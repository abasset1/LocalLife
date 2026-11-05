import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "leaflet/dist/leaflet.css";
import "leaflet.markercluster/dist/MarkerCluster.css";
import "leaflet.markercluster/dist/MarkerCluster.Default.css";
import "./styles.css";
import AppRoutes from "./AppRoutes";
import { LaunchFeaturesProvider } from "./features/launchFeatures";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <BrowserRouter>
            <LaunchFeaturesProvider>
                <AppRoutes />
            </LaunchFeaturesProvider>
        </BrowserRouter>
    </StrictMode>
);
