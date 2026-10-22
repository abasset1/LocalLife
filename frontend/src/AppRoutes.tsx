import { Route, Routes } from "react-router-dom";
import App from "./App";
import AdminPage from "./pages/AdminPage";
import FavoritesPage from "./pages/FavoritesPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import LegalPage from "./pages/LegalPage";
import LoginPage from "./pages/LoginPage";
import MyContributionsPage from "./pages/MyContributionsPage";
import ProfilePage from "./pages/ProfilePage";
import ProPage from "./pages/ProPage";
import RegisterPage from "./pages/RegisterPage";
import ResetPasswordPage from "./pages/ResetPasswordPage";
import UnsubscribeAlertPage from "./pages/UnsubscribeAlertPage";
import ConfirmEmailChangePage from "./pages/ConfirmEmailChangePage";
import VerifyEmailPage from "./pages/VerifyEmailPage";

/** Routes de l'application, séparées de main.tsx pour pouvoir être rendues dans les tests (MemoryRouter). */
function AppRoutes() {
    return (
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
            <Route element={<LegalPage document="privacy" />} path="/confidentialite" />
            <Route element={<LegalPage document="terms" />} path="/conditions" />
            <Route element={<LegalPage document="legalNotice" />} path="/mentions-legales" />
        </Routes>
    );
}

export default AppRoutes;
