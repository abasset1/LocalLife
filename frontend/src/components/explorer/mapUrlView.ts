/**
 * Position de la carte Explorer dans l'adresse de la page (`?lat=…&lng=…&zoom=…`), comme sur la carte
 * Finishers : un lien copié rouvre la carte au même endroit, et un rechargement ne la ramène pas à la
 * vue d'ensemble.
 */
export interface MapUrlView {
    latitude: number;
    longitude: number;
    zoom: number;
}

const MIN_ZOOM = 1;
const MAX_ZOOM = 19;

/** Lit la position de la carte dans une chaîne de requête ; `null` si absente ou invalide. */
export function readMapUrlView(search: string): MapUrlView | null {
    const params = new URLSearchParams(search);
    const raw = [params.get("lat"), params.get("lng"), params.get("zoom")];
    if (raw.some((value) => value === null || value.trim() === "")) {
        return null;
    }
    const [latitude, longitude, zoom] = raw.map(Number);
    if (
        !Number.isFinite(latitude) || latitude < -90 || latitude > 90
        || !Number.isFinite(longitude) || longitude < -180 || longitude > 180
        || !Number.isFinite(zoom) || zoom < MIN_ZOOM || zoom > MAX_ZOOM
    ) {
        return null;
    }
    return { latitude, longitude, zoom };
}

/**
 * Écrit la position dans l'adresse sans nouvelle entrée d'historique (le bouton « Précédent » ne
 * rejoue pas chaque déplacement) et sans toucher aux autres paramètres. `history.state` est conservé :
 * le routeur y garde sa clé de navigation.
 */
export function writeMapUrlView(view: MapUrlView): void {
    const url = new URL(window.location.href);
    url.searchParams.set("lat", view.latitude.toFixed(5));
    url.searchParams.set("lng", view.longitude.toFixed(5));
    url.searchParams.set("zoom", String(Math.round(view.zoom * 100) / 100));
    if (url.href !== window.location.href) {
        window.history.replaceState(window.history.state, "", url);
    }
}
