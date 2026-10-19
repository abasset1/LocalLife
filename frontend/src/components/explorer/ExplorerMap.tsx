import { useEffect, useRef } from "react";
import type { LatLngExpression } from "leaflet";
import L from "leaflet";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import { Circle, MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from "react-leaflet";
import MarkerClusterGroup from "react-leaflet-cluster";
import { DEFAULT_MAP_CENTER, DEFAULT_MAP_ZOOM, MAP_BOUNDS_DEBOUNCE_MS } from "./constants";
import { APPROXIMATE_LOCATION_LABEL, isApproximateLocation } from "./activityUtils";
import { hasCoordinates } from "./globalSearch";
import PlacesLayer from "../places/PlacesLayer";
import type { Place } from "../places/types";
import { describePresence } from "../foodtrucks/foodTruckFormat";
import type { Activity, FoodTruck, MapBounds, UserPosition } from "./types";

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
    /** Couche « Food trucks » (ADR-0013), distincte des activités et des lieux. */
    showFoodTrucks?: boolean;
    /** Couche « Lieux » (ADR-0011, phase B) : lieux permanents publiés de la zone affichée. */
    showPlaces?: boolean;
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
}

function ExplorerMap({
    activities,
    foodTrucks,
    showFoodTrucks = true,
    showPlaces = false,
    onPlacesChange,
    userPosition,
    radiusKm,
    selectedActivity,
    onSelectActivity,
    onBoundsChange,
    fitKey = null,
}: ExplorerMapProps) {
    const initialCenter: LatLngExpression = userPosition
        ? [userPosition.latitude, userPosition.longitude]
        : DEFAULT_MAP_CENTER;

    return (
        // zoomSnap fractionnaire : le cadrage sur le rayon favori (fitBounds) colle au rayon au lieu
        // d'arrondir au niveau de zoom entier inférieur (jusqu'à deux fois trop large).
        <MapContainer
            center={initialCenter}
            className="explorer-map"
            zoom={userPosition ? 13 : DEFAULT_MAP_ZOOM}
            zoomControl={false}
            zoomSnap={0.25}
        >
            <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapBoundsWatcher onBoundsChange={onBoundsChange} />
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
            <MarkerClusterGroup>
                {activities.filter(hasCoordinates).map((activity) => (
                    <Marker
                        eventHandlers={{ click: () => onSelectActivity(activity) }}
                        key={activity.id}
                        // ADR-0011, phase A : position approximative signalée (marqueur atténué, info-bulle).
                        opacity={isApproximateLocation(activity) ? 0.55 : 1}
                        position={[activity.latitude, activity.longitude]}
                        title={isApproximateLocation(activity) ? APPROXIMATE_LOCATION_LABEL : undefined}
                    />
                ))}
            </MarkerClusterGroup>
            <PlacesLayer enabled={showPlaces} onPlacesChange={onPlacesChange} onSelectActivity={onSelectActivity} />
            {showFoodTrucks && foodTrucks.map((foodTruck) => (
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
