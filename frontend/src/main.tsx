import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import "leaflet/dist/leaflet.css";
import "leaflet.markercluster/dist/MarkerCluster.css";
import "leaflet.markercluster/dist/MarkerCluster.Default.css";
import "./styles.css";
import App from "./App";
import AdminPage from "./pages/AdminPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import LoginPage from "./pages/LoginPage";
import ProfilePage from "./pages/ProfilePage";
import RegisterPage from "./pages/RegisterPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <BrowserRouter>
            <Routes>
                <Route element={<App />} path="/" />
                <Route element={<AdminPage />} path="/admin" />
                <Route element={<ProfilePage />} path="/profile" />
                <Route element={<LoginPage />} path="/login" />
                <Route element={<RegisterPage />} path="/register" />
                <Route element={<ForgotPasswordPage />} path="/forgot-password" />
                <Route element={<ResetPasswordPage />} path="/reset-password" />
            </Routes>
        </BrowserRouter>
    </StrictMode>
);
