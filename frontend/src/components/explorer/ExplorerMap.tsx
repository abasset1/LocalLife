import { useEffect, useRef, useState } from "react";
import type { LatLngExpression, LatLngLiteral, LeafletMouseEvent } from "leaflet";
import L from "leaflet";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import { Circle, MapContainer, Marker, Popup, TileLayer, Tooltip, useMap, useMapEvents } from "react-leaflet";
import MarkerClusterGroup from "react-leaflet-cluster";
import { DEFAULT_MAP_CENTER, DEFAULT_MAP_ZOOM, MAP_BOUNDS_DEBOUNCE_MS } from "./constants";
import { APPROXIMATE_LOCATION_LABEL, isApproximateLocation } from "./activityUtils";
import { hasCoordinates } from "./globalSearch";
import ClusterActivitiesPopup from "./ClusterActivitiesPopup";
import { type MapUrlView, writeMapUrlView } from "./mapUrlView";
import PlacesLayer from "../places/PlacesLayer";
import type { Place } from "../places/types";
import { describePresence } from "../foodtrucks/foodTruckFormat";
import type { Activity, Category, FoodTruck, MapBounds, UserPosition } from "./types";

/**
 * Correctif prod : icône par défaut de Leaflet pour les marqueurs
 * d'activité — voir la javadoc d'origine (App.tsx avant LL-EF-017) pour le
 * détail du problème Vite/`_getIconUrl`. Reconfiguré une fois au chargement
 * du module, ces icônes ne changeant jamais pendant la vie de l'app.
 */
delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: markerIcon2x,
    iconUrl: markerIcon,
    shadowUrl: markerShadow,
});

/** Icône dédiée au marqueur food truck (LL-6009) — voir styles.css `.explorer-food-truck-marker`. */
const FOOD_TRUCK_MARKER_ICON = L.divIcon({
    className: "explorer-food-truck-marker",
    html: "🚚",
    iconSize: [32, 32],
    iconAnchor: [16, 16],
});

/**
 * Activité survolée dans la liste (comme sur la carte Finishers) : marqueur dédié, hors des groupes,
 * pour rester visible même quand l'activité est regroupée avec ses voisines.
 */
const HIGHLIGHT_MARKER_ICON = L.divIcon({
    className: "explorer-highlight-marker",
    html: "<span></span>",
    iconSize: [26, 26],
    iconAnchor: [13, 13],
});

/** Zoom minimal quand la carte est centrée sur une ville ou une position. */
const CITY_ZOOM = 12;

/**
 * Composant enfant sans rendu visuel : seule façon d'écouter les événements
 * de la carte avec react-leaflet (`useMapEvents` doit être appelé depuis un
 * descendant de `MapContainer`).
 */
function MapBoundsWatcher({ onBoundsChange }: { onBoundsChange: (bounds: MapBounds) => void }) {
    const debounceTimer = useRef<number | null>(null);

    const map = useMapEvents({
        moveend: () => scheduleBoundsUpdate(),
        zoomend: () => scheduleBoundsUpdate(),
    });

    function scheduleBoundsUpdate() {
        if (debounceTimer.current !== null) {
            window.clearTimeout(debounceTimer.current);
        }
        debounceTimer.current = window.setTimeout(() => {
            const bounds = map.getBounds();
            const southWest = bounds.getSouthWest();
            const northEast = bounds.getNorthEast();

            // LL-EF-017 : un conteneur de taille nulle (carte masquée en
            // display: none, bascule mobile Carte/Liste) produit des bounds
            // dégénérés (SW = NE, voire inversés) — le backend rejette alors
            // la requête ("swLatitude doit être strictement inférieur à
            // neLatitude"). On ignore silencieusement ces bounds invalides
            // plutôt que de les envoyer.
            if (
                !Number.isFinite(southWest.lat)
                || !Number.isFinite(southWest.lng)
                || !Number.isFinite(northEast.lat)
                || !Number.isFinite(northEast.lng)
                || southWest.lat >= northEast.lat
                || southWest.lng >= northEast.lng
            ) {
                return;
            }

            // Carte dézoomée au-delà d'un tour du monde : Leaflet renvoie des
            // longitudes hors de [-180, 180] (le monde est répété), refusées par
            // le backend (400). Ramenées aux bornes valides, comme sur mobile.
            const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
            onBoundsChange({
                swLatitude: clamp(southWest.lat, -90, 90),
                swLongitude: clamp(southWest.lng, -180, 180),
                neLatitude: clamp(northEast.lat, -90, 90),
                neLongitude: clamp(northEast.lng, -180, 180),
            });
        }, MAP_BOUNDS_DEBOUNCE_MS);
    }

    useEffect(() => {
        // Zone affichée transmise dès l'affichage, sans attendre un geste : sans lieu choisi, c'est elle
        // qui fait la recherche (spec P0-A, pas de recherche autour d'une position inventée).
        map.whenReady(() => scheduleBoundsUpdate());
        return () => {
            if (debounceTimer.current !== null) {
                window.clearTimeout(debounceTimer.current);
            }
        };
    }, []);

    return null;
}

/**
 * Recopie le centre et le zoom de la carte dans l'adresse de la page après chaque geste (voir
 * `mapUrlView.ts`). Une carte masquée (bascule mobile Carte/Liste) n'écrit rien.
 */
function MapUrlViewSync() {
    const map = useMapEvents({
        moveend: () => {
            const size = map.getSize();
            if (size.x === 0 || size.y === 0) {
                return;
            }
            const center = map.getCenter();
            writeMapUrlView({ latitude: center.lat, longitude: L.Util.wrapNum(center.lng, [-180, 180], true), zoom: map.getZoom() });
        },
    });
    return null;
}

/**
 * `MapContainer` ne respecte sa prop `center` qu'au montage initial — un
 * changement ultérieur de `userPosition` (LL-4010) ne recentre donc jamais
 * visuellement la carte sans ce composant.
 *
 * Avec un rayon favori (paramètres de l'utilisateur connecté), la carte est
 * cadrée sur ce rayon autour du point : la zone visible, qui devient ensuite
 * la zone de recherche (`within-bounds`), correspond alors au rayon choisi.
 * Sans rayon favori, le zoom courant est conservé (comportement d'origine).
 */
function MapRecenterOnUserPosition({ position, radiusKm }: { position: UserPosition | null; radiusKm: number | null }) {
    const map = useMap();

    useEffect(() => {
        if (!position) {
            return;
        }
        if (radiusKm) {
            map.fitBounds(L.latLng(position.latitude, position.longitude).toBounds(radiusKm * 2 * 1000));
        } else {
            // Au moins l'échelle d'une ville (recherche de ville depuis une carte dézoomée).
            map.setView([position.latitude, position.longitude], Math.max(map.getZoom(), CITY_ZOOM));
        }
    }, [position, radiusKm, map]);

    return null;
}

/**
 * LL-EF-017 : recentre/zoome sur l'activité sélectionnée (marqueur ou ligne
 * de liste), pour matérialiser le lien carte ↔ activité demandé en section 8
 * du ticket ("Clic sur une activité : positionner la carte sur celle-ci").
 */
function MapFlyToSelection({ activity }: { activity: Activity | null }) {
    const map = useMap();

    useEffect(() => {
        // Résultat de recherche sans lieu connu (#92) : la carte ne bouge pas.
        if (activity && hasCoordinates(activity)) {
            map.setView([activity.latitude, activity.longitude], Math.max(map.getZoom(), 15), {
                animate: true,
            });
        }
    }, [activity, map]);

    return null;
}

/**
 * Recherche globale (#92) : à chaque nouvelle page de résultats (`fitKey`), la carte est cadrée sur les
 * résultats situés, où qu'ils soient — ils ne sont pas limités à la zone affichée. Sans résultat situé,
 * la carte ne bouge pas.
 */
function MapFitToResults({ activities, fitKey }: { activities: Activity[]; fitKey: string | null }) {
    const map = useMap();

    useEffect(() => {
        if (fitKey === null) {
            return;
        }
        const located = activities.filter(hasCoordinates);
        if (located.length === 0) {
            return;
        }
        const bounds = L.latLngBounds(located.map((activity) => [activity.latitude, activity.longitude]));
        map.fitBounds(bounds, { padding: [40, 40], maxZoom: 14 });
        // Seul un changement de page ou de recherche recadre, pas un simple nouveau tableau.
    }, [fitKey, map]);

    return null;
}

/**
 * LL-EF-017 : la nouvelle mise en page permet que la carte reste montée
 * alors que son conteneur change de taille (bascule Carte/Liste en mobile,
 * ouverture/fermeture du panneau liste en desktop lors d'un redimensionnement
 * de fenêtre) — Leaflet ne détecte pas seul ces changements de taille de son
 * conteneur (contrairement à un redimensionnement de la fenêtre elle-même,
 * déjà géré nativement) et affiche alors des tuiles tronquées tant qu'aucune
 * interaction ne force un recalcul. `ResizeObserver` couvre les deux cas
 * (bascule mobile et passage d'un point de rupture CSS à l'autre) sans avoir
 * à dupliquer la logique de largeur d'écran déjà définie en CSS.
 */
function MapInvalidateOnResize() {
    const map = useMap();

    useEffect(() => {
        const container = map.getContainer();
        const observer = new ResizeObserver((entries) => {
            const { width, height } = entries[0]?.contentRect ?? { width: 0, height: 0 };
            // Le conteneur passe à une taille nulle quand la carte est masquée
            // (display: none, bascule mobile Carte/Liste) — appeler
            // invalidateSize() dans cet état produit des bounds dégénérés
            // (voir MapBoundsWatcher.scheduleBoundsUpdate). On ne recalcule
            // que sur une taille réelle, c'est-à-dire quand la carte redevient
            // visible ou change effectivement de taille.
            if (width > 0 && height > 0) {
                map.invalidateSize();
            }
        });
        observer.observe(container);
        return () => observer.disconnect();
    }, [map]);

    return null;
}

interface ExplorerMapProps {
    activities: Activity[];
    foodTrucks: FoodTruck[];
    /**
     * Seul contenu affiché : activités, lieux permanents publiés de la zone (ADR-0011, phase B) ou food trucks
     * (ADR-0013) — jamais plusieurs à la fois.
     */
    layer?: "activities" | "places" | "foodTrucks";
    /** Lieux de la couche « Lieux » chargés pour la zone affichée. */
    onPlacesChange?: (places: Place[]) => void;
    userPosition: UserPosition | null;
    /** Rayon actif (km) : la carte est cadrée dessus autour de `userPosition`, et il est tracé. */
    radiusKm: number | null;
    selectedActivity: Activity | null;
    onSelectActivity: (activity: Activity) => void;
    onBoundsChange: (bounds: MapBounds) => void;
    /** Recherche globale : identifiant de la page de résultats affichée, `null` hors recherche. */
    fitKey?: string | null;
    /** Position lue dans l'adresse au chargement (lien partagé, rechargement) : vue initiale de la carte. */
    initialView?: MapUrlView | null;
    /** Activité survolée dans la liste : mise en évidence sur la carte. */
    highlightedActivity?: Activity | null;
    /** Libellés des catégories, pour la popup d'une pastille. */
    categories?: Category[];
    /** « Voir l'activité » depuis la popup d'une pastille : fiche complète. */
    onOpenDetail?: (activity: Activity) => void;
}

/** Pastille cliquée : sa position et ses activités, de la plus proche dans le temps à la plus lointaine. */
interface ClusterSelection {
    position: LatLngLiteral;
    activities: Activity[];
}

/**
 * Au-delà de ce nombre d'activités, une pastille dont les activités sont à des endroits différents
 * zoome sur son groupe au lieu d'ouvrir la popup (comme la carte Finishers) : on ne fait pas défiler
 * des dizaines d'activités une à une.
 */
const CLUSTER_POPUP_MAX_ACTIVITIES = 10;

/** Positions arrondies à environ 100 m : en dessous, deux activités sont considérées au même endroit. */
function placeKey(activity: Activity): string {
    return `${activity.latitude.toFixed(3)},${activity.longitude.toFixed(3)}`;
}

function compareByStartDate(a: Activity, b: Activity): number {
    if (!a.startDate || !b.startDate) {
        return a.startDate ? -1 : b.startDate ? 1 : 0;
    }
    return a.startDate.localeCompare(b.startDate);
}

function ExplorerMap({
    activities,
    foodTrucks,
    layer = "activities",
    onPlacesChange,
    userPosition,
    radiusKm,
    selectedActivity,
    onSelectActivity,
    onBoundsChange,
    fitKey = null,
    initialView = null,
    highlightedActivity = null,
    categories = [],
    onOpenDetail,
}: ExplorerMapProps) {
    /** Activité de chaque marqueur, pour retrouver celles d'une pastille cliquée. */
    const activityByMarker = useRef(new WeakMap<L.Marker, Activity>());
    const [clusterSelection, setClusterSelection] = useState<ClusterSelection | null>(null);

    // Le gestionnaire de clic de la pastille est enregistré une seule fois par la bibliothèque : il
    // passe par une référence pour toujours lire l'état courant.
    const handleClusterClickRef = useRef<(event: LeafletMouseEvent) => void>(() => undefined);
    handleClusterClickRef.current = (event) => {
        /** Pastille cliquée (`L.MarkerCluster` de leaflet.markercluster). */
        const cluster = (event as LeafletMouseEvent & {
            layer: {
                getAllChildMarkers: () => L.Marker[];
                getLatLng: () => LatLngLiteral;
                zoomToBounds: (options?: L.FitBoundsOptions) => void;
            };
        }).layer;
        const clusterActivities = cluster.getAllChildMarkers()
            .map((marker: L.Marker) => activityByMarker.current.get(marker))
            .filter((activity: Activity | undefined): activity is Activity => activity !== undefined)
            .sort(compareByStartDate);
        if (clusterActivities.length === 0) {
            return;
        }
        // Carte du groupe de marqueurs (propriété interne de Leaflet, présente tant qu'il est affiché).
        const map = (event.target as { _map?: L.Map })._map;
        const isSpreadOut = new Set(clusterActivities.map(placeKey)).size > 1;
        const canZoom = map ? map.getZoom() < map.getMaxZoom() : false;
        if (clusterActivities.length > CLUSTER_POPUP_MAX_ACTIVITIES && isSpreadOut && canZoom) {
            setClusterSelection(null);
            cluster.zoomToBounds({ padding: [40, 40] });
            return;
        }
        setClusterSelection({ position: cluster.getLatLng(), activities: clusterActivities });
    };

    /** Marqueur seul : même popup, avec les activités situées au même endroit. */
    function handleMarkerClick(activity: Activity) {
        const key = placeKey(activity);
        const samePlace = activities.filter((other) => other.id !== activity.id && hasCoordinates(other)
            && placeKey(other) === key);
        setClusterSelection({
            position: { lat: activity.latitude, lng: activity.longitude },
            activities: [activity, ...samePlace.sort(compareByStartDate)],
        });
    }

    // Autre contenu affiché ou nouvelle recherche : la popup d'une pastille n'a plus lieu d'être.
    useEffect(() => setClusterSelection(null), [layer, fitKey]);

    const initialCenter: LatLngExpression = initialView
        ? [initialView.latitude, initialView.longitude]
        : userPosition
            ? [userPosition.latitude, userPosition.longitude]
            : DEFAULT_MAP_CENTER;
    const initialZoom = initialView ? initialView.zoom : userPosition ? 13 : DEFAULT_MAP_ZOOM;

    return (
        // zoomSnap fractionnaire : le cadrage sur le rayon favori (fitBounds) colle au rayon au lieu
        // d'arrondir au niveau de zoom entier inférieur (jusqu'à deux fois trop large).
        <MapContainer
            center={initialCenter}
            className="explorer-map"
            zoom={initialZoom}
            zoomControl={false}
            zoomSnap={0.25}
        >
            <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapBoundsWatcher onBoundsChange={onBoundsChange} />
            <MapUrlViewSync />
            <MapRecenterOnUserPosition position={userPosition} radiusKm={radiusKm} />
            {userPosition && radiusKm && (
                // Rayon actif, matérialisé autour de la ville (ou de la position) de recherche.
                <Circle
                    center={[userPosition.latitude, userPosition.longitude]}
                    interactive={false}
                    pathOptions={{ color: "#2563eb", weight: 1.5, dashArray: "6 6", fillOpacity: 0.04 }}
                    radius={radiusKm * 1000}
                />
            )}
            <MapFlyToSelection activity={selectedActivity} />
            <MapFitToResults activities={activities} fitKey={fitKey} />
            <MapInvalidateOnResize />
            {/*
              Clustering (demande Alex, hors ticket de sprint), conservé à l'identique.
              Les marqueurs ouvrent désormais l'aperçu Explorer (`ActivityPreview`) au
              lieu d'un Popup Leaflet (LL-EF-017, section 8 : "afficher son aperçu").
            */}
            <MarkerClusterGroup
                // Clic sur une pastille : popup qui fait défiler ses activités, sans zoom sur la zone.
                onClick={(event: LeafletMouseEvent) => handleClusterClickRef.current(event)}
                spiderfyOnMaxZoom={false}
                zoomToBoundsOnClick={false}
            >
                {layer === "activities" && activities.filter(hasCoordinates).map((activity) => (
                    <Marker
                        eventHandlers={{ click: () => handleMarkerClick(activity) }}
                        key={activity.id}
                        // ADR-0011, phase A : position approximative signalée (marqueur atténué, info-bulle).
                        opacity={isApproximateLocation(activity) ? 0.55 : 1}
                        position={[activity.latitude, activity.longitude]}
                        ref={(marker) => {
                            if (marker) {
                                activityByMarker.current.set(marker, activity);
                            }
                        }}
                        title={isApproximateLocation(activity) ? APPROXIMATE_LOCATION_LABEL : undefined}
                    />
                ))}
            </MarkerClusterGroup>
            {layer === "activities" && clusterSelection && (
                <Popup
                    className="explorer-cluster-leaflet-popup"
                    eventHandlers={{ remove: () => setClusterSelection(null) }}
                    key={`${clusterSelection.position.lat},${clusterSelection.position.lng},${clusterSelection.activities.map((activity) => activity.id).join("-")}`}
                    maxWidth={300}
                    minWidth={240}
                    position={clusterSelection.position}
                >
                    <ClusterActivitiesPopup
                        activities={clusterSelection.activities}
                        categories={categories}
                        onOpenDetail={(activity) => {
                            setClusterSelection(null);
                            onOpenDetail?.(activity);
                        }}
                    />
                </Popup>
            )}
            {layer === "activities" && highlightedActivity && hasCoordinates(highlightedActivity) && (
                <Marker
                    icon={HIGHLIGHT_MARKER_ICON}
                    interactive={false}
                    key={`highlight-${highlightedActivity.id}`}
                    keyboard={false}
                    position={[highlightedActivity.latitude, highlightedActivity.longitude]}
                    zIndexOffset={1000}
                >
                    <Tooltip className="explorer-highlight-tooltip" direction="top" offset={[0, -14]} permanent>
                        {highlightedActivity.title}
                    </Tooltip>
                </Marker>
            )}
            <PlacesLayer enabled={layer === "places"} onPlacesChange={onPlacesChange} onSelectActivity={onSelectActivity} />
            {layer === "foodTrucks" && foodTrucks.map((foodTruck) => (
                <Marker
                    icon={FOOD_TRUCK_MARKER_ICON}
                    key={`food-truck-presence-${foodTruck.presenceId}`}
                    position={[foodTruck.latitude, foodTruck.longitude]}
                >
                    <Popup>
                        <strong>{foodTruck.name}</strong>
                        <br />
                        {foodTruck.category}
                        {describePresence(foodTruck) && (
                            <>
                                <br />
                                {describePresence(foodTruck)}
                            </>
                        )}
                    </Popup>
                </Marker>
            ))}
        </MapContainer>
    );
}

export default ExplorerMap;
