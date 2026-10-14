import { useEffect, useState } from "react";
import L from "leaflet";
import { Marker, Popup, useMap } from "react-leaflet";
import { APPROXIMATE_LOCATION_LABEL } from "../explorer/activityUtils";
import { MAP_BOUNDS_DEBOUNCE_MS } from "../explorer/constants";
import { placeTypeLabel } from "./placeTypes";
import type { Place } from "./types";

/** Icône des lieux permanents publiés — voir styles.css `.explorer-place-marker`. */
const PLACE_MARKER_ICON = L.divIcon({
    className: "explorer-place-marker",
    html: "🏛",
    iconSize: [28, 28],
    iconAnchor: [14, 14],
});

export interface Bounds {
    south: number;
    west: number;
    north: number;
    east: number;
}

/** URL des lieux publiés d'une zone ; longitudes ramenées dans [-180, 180] (carte dézoomée). */
export function placesWithinBoundsUrl(bounds: Bounds): string {
    const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));
    const params = new URLSearchParams({
        minLat: String(clamp(bounds.south, -90, 90)),
        maxLat: String(clamp(bounds.north, -90, 90)),
        minLng: String(clamp(bounds.west, -180, 180)),
        maxLng: String(clamp(bounds.east, -180, 180)),
    });
    return `/api/v1/places/within-bounds?${params.toString()}`;
}

/**
 * Couche « Lieux » (ADR-0011, phase B) : lieux permanents publiés de la zone affichée. Lit la zone de la carte dès
 * son activation puis à chaque déplacement, indépendamment du chargement des activités. Carte utilisable sans
 * lieux si la requête échoue.
 */
function PlacesLayer({ enabled }: { enabled: boolean }) {
    const map = useMap();
    const [places, setPlaces] = useState<Place[]>([]);

    useEffect(() => {
        if (!enabled) {
            setPlaces([]);
            return;
        }
        let abortController: AbortController | null = null;
        let timer: number | null = null;

        function load() {
            const bounds = map.getBounds();
            const south = bounds.getSouth();
            const north = bounds.getNorth();
            const west = bounds.getWest();
            const east = bounds.getEast();
            if (![south, north, west, east].every(Number.isFinite) || south >= north || west >= east) {
                return;
            }
            abortController?.abort();
            const controller = new AbortController();
            abortController = controller;
            fetch(placesWithinBoundsUrl({ south, west, north, east }), { signal: controller.signal })
                .then(async (response) => {
                    if (response.ok) {
                        setPlaces((await response.json()) as Place[]);
                    }
                })
                .catch(() => {
                    // Annulé ou serveur injoignable : la carte reste utilisable sans lieux.
                });
        }

        function scheduleLoad() {
            if (timer !== null) {
                window.clearTimeout(timer);
            }
            timer = window.setTimeout(load, MAP_BOUNDS_DEBOUNCE_MS);
        }

        load();
        map.on("moveend", scheduleLoad);
        return () => {
            map.off("moveend", scheduleLoad);
            if (timer !== null) {
                window.clearTimeout(timer);
            }
            abortController?.abort();
        };
    }, [enabled, map]);

    return (
        <>
            {places.map((place) => (
                <Marker
                    icon={PLACE_MARKER_ICON}
                    key={`place-${place.id}`}
                    position={[place.latitude, place.longitude]}
                    title={place.name}
                >
                    <Popup>
                        <strong>{place.name}</strong>
                        <br />
                        {placeTypeLabel(place.placeType)}
                        {(place.address || place.city) && (
                            <>
                                <br />
                                {[place.address, place.city].filter(Boolean).join(", ")}
                            </>
                        )}
                        {place.coordinatePrecision === "APPROXIMATE" && (
                            <>
                                <br />
                                <em>{APPROXIMATE_LOCATION_LABEL}</em>
                            </>
                        )}
                        {place.website && (
                            <>
                                <br />
                                <a href={place.website} rel="noreferrer" target="_blank">
                                    Site du lieu
                                </a>
                            </>
                        )}
                    </Popup>
                </Marker>
            ))}
        </>
    );
}

export default PlacesLayer;
