import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import "leaflet/dist/leaflet.css";
import "./styles.css";
import App from "./App";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <BrowserRouter>
            <Routes>
                <Route element={<App />} path="/" />
                <Route element={<LoginPage />} path="/login" />
                <Route element={<RegisterPage />} path="/register" />
            </Routes>
        </BrowserRouter>
    </StrictMode>
);
