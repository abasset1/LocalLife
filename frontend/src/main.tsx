import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import "leaflet/dist/leaflet.css";
import "leaflet.markercluster/dist/MarkerCluster.css";
import "leaflet.markercluster/dist/MarkerCluster.Default.css";
import "./styles.css";
import App from "./App";
import AdminPage from "./pages/AdminPage";
import FavoritesPage from "./pages/FavoritesPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import LoginPage from "./pages/LoginPage";
import MyContributionsPage from "./pages/MyContributionsPage";
import ProfilePage from "./pages/ProfilePage";
import ProPage from "./pages/ProPage";
import RegisterPage from "./pages/RegisterPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import UnsubscribeAlertPage from "./pages/UnsubscribeAlertPage";
import ConfirmEmailChangePage from "./pages/ConfirmEmailChangePage";
import VerifyEmailPage from "./pages/VerifyEmailPage";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <BrowserRouter>
            <Routes>
                <Route element={<App />} path="/" />
                <Route element={<AdminPage />} path="/admin" />
                <Route element={<ProfilePage />} path="/profile" />
                <Route element={<ProPage />} path="/pro" />
                <Route element={<FavoritesPage />} path="/favorites" />
                <Route element={<MyContributionsPage />} path="/contributions" />
                <Route element={<LoginPage />} path="/login" />
                <Route element={<RegisterPage />} path="/register" />
                <Route element={<ForgotPasswordPage />} path="/forgot-password" />
                <Route element={<ResetPasswordPage />} path="/reset-password" />
                <Route element={<VerifyEmailPage />} path="/verify-email" />
                <Route element={<ConfirmEmailChangePage />} path="/confirm-email-change" />
                <Route element={<UnsubscribeAlertPage />} path="/alerts/unsubscribe" />
            </Routes>
        </BrowserRouter>
    </StrictMode>
);
