import { useEffect, useRef } from "react";
import type { LatLngExpression } from "leaflet";
import L from "leaflet";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import { MapContainer, Marker, Popup, TileLayer, useMap, useMapEvents } from "react-leaflet";
import MarkerClusterGroup from "react-leaflet-cluster";
import { MAP_BOUNDS_DEBOUNCE_MS, MARSEILLE_COORDINATES } from "./constants";
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

            onBoundsChange({
                swLatitude: southWest.lat,
                swLongitude: southWest.lng,
                neLatitude: northEast.lat,
                neLongitude: northEast.lng,
            });
        }, MAP_BOUNDS_DEBOUNCE_MS);
    }

    useEffect(() => {
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
 */
function MapRecenterOnUserPosition({ position }: { position: UserPosition | null }) {
    const map = useMap();

    useEffect(() => {
        if (position) {
            map.setView([position.latitude, position.longitude], map.getZoom());
        }
    }, [position, map]);

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
        if (activity) {
            map.setView([activity.latitude, activity.longitude], Math.max(map.getZoom(), 15), {
                animate: true,
            });
        }
    }, [activity, map]);

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
    userPosition: UserPosition | null;
    selectedActivity: Activity | null;
    onSelectActivity: (activity: Activity) => void;
    onBoundsChange: (bounds: MapBounds) => void;
}

function ExplorerMap({
    activities,
    foodTrucks,
    userPosition,
    selectedActivity,
    onSelectActivity,
    onBoundsChange,
}: ExplorerMapProps) {
    const initialCenter: LatLngExpression = userPosition
        ? [userPosition.latitude, userPosition.longitude]
        : MARSEILLE_COORDINATES;

    return (
        <MapContainer center={initialCenter} className="explorer-map" zoom={13} zoomControl={false}>
            <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapBoundsWatcher onBoundsChange={onBoundsChange} />
            <MapRecenterOnUserPosition position={userPosition} />
            <MapFlyToSelection activity={selectedActivity} />
            <MapInvalidateOnResize />
            {/*
              Clustering (demande Alex, hors ticket de sprint), conservé à l'identique.
              Les marqueurs ouvrent désormais l'aperçu Explorer (`ActivityPreview`) au
              lieu d'un Popup Leaflet (LL-EF-017, section 8 : "afficher son aperçu").
            */}
            <MarkerClusterGroup>
                {activities.map((activity) => (
                    <Marker
                        eventHandlers={{ click: () => onSelectActivity(activity) }}
                        key={activity.id}
                        position={[activity.latitude, activity.longitude]}
                    />
                ))}
            </MarkerClusterGroup>
            {foodTrucks.map((foodTruck) => (
                <Marker
                    icon={FOOD_TRUCK_MARKER_ICON}
                    key={`food-truck-${foodTruck.id}`}
                    position={[foodTruck.latitude, foodTruck.longitude]}
                >
                    <Popup>
                        <strong>{foodTruck.name}</strong>
                        <br />
                        {foodTruck.category}
                    </Popup>
                </Marker>
            ))}
        </MapContainer>
    );
}

export default ExplorerMap;
