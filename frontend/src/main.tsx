import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "leaflet/dist/leaflet.css";
import "leaflet.markercluster/dist/MarkerCluster.css";
import "leaflet.markercluster/dist/MarkerCluster.Default.css";
import "./styles.css";
import AppRoutes from "./AppRoutes";
import { refreshSession } from "./auth/authStorage";
import { LaunchFeaturesProvider } from "./features/launchFeatures";

function render(): void {
    createRoot(document.getElementById("root")!).render(
        <StrictMode>
            <BrowserRouter>
                <LaunchFeaturesProvider>
                    <AppRoutes />
                </LaunchFeaturesProvider>
            </BrowserRouter>
        </StrictMode>
    );
}

// La session vit dans un cookie HttpOnly illisible par le JS : on lit l'identité auprès du serveur
// (GET /users/me) AVANT le premier rendu, pour que le cache de session soit déjà rempli lorsqu'un écran
// protégé décide, de façon synchrone, s'il affiche le contenu connecté ou redirige le visiteur. Ce premier
// appel amorce aussi le cookie anti-CSRF XSRF-TOKEN. Le rendu a lieu quoi qu'il arrive (échec = visiteur).
void refreshSession().finally(render);
