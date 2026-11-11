import { afterEach, beforeEach } from "vitest";

/**
 * Visiteur arrivé par un lien de carte (`?lat=…&lng=…&zoom=…`) : un lieu est connu, la fenêtre de choix
 * de ville (CityPromptModal) ne s'ouvre pas et les activités de la zone affichée se chargent aussitôt.
 */
export function useSharedMapLink() {
    beforeEach(() => {
        window.history.replaceState(null, "", "/?lat=43.9&lng=4.8&zoom=10");
    });
    afterEach(() => {
        window.history.replaceState(null, "", "/");
    });
}
