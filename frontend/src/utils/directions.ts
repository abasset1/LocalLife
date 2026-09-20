/**
 * LL-EF-015 : construit l'url « Y aller » vers une activité — l'« url
 * universelle » Google Maps (`google.com/maps/dir/?api=1&destination=...`,
 * mécanisme public documenté par Google, sans clé API ni authentification),
 * ouverte dans un nouvel onglet/l'application de navigation compatible de
 * l'utilisateur (sur mobile, cette url déclenche le sélecteur d'application
 * de navigation du système si plusieurs sont installées — Google Maps,
 * Waze, Plans, etc.) — LocalLife n'implémente aucun système de navigation
 * propre (critère d'acceptation explicite du ticket).
 *
 * Priorité explicite du ticket : coordonnées GPS d'abord, adresse en
 * secours — `destination` accepte aussi bien `latitude,longitude` qu'une
 * adresse en texte libre.
 */

export interface DirectionsDestinationInput {
    latitude: number | null;
    longitude: number | null;
    address: string | null;
    city: string | null;
}

export function buildDirectionsUrl(destination: DirectionsDestinationInput): string {
    const hasCoordinates = destination.latitude !== null && destination.longitude !== null
        && !Number.isNaN(destination.latitude) && !Number.isNaN(destination.longitude);

    const destinationParam = hasCoordinates
        ? `${destination.latitude},${destination.longitude}`
        : [destination.address, destination.city].filter(Boolean).join(", ");

    const params = new URLSearchParams({
        api: "1",
        destination: destinationParam,
    });
    return `https://www.google.com/maps/dir/?${params.toString()}`;
}
