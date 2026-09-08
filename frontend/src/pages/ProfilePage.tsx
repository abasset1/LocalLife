import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { apiFetch } from "../api/apiClient";
import { clearToken, getPayload } from "../auth/authStorage";

/**
 * Miroir de `UserResponse` côté backend (jamais `passwordHash`, voir sa
 * javadoc) — même principe que `AdminActivity`/`Source` dans AdminPage.tsx :
 * représentation dédiée à cette page, distincte du payload JWT décodé
 * (`getPayload`, qui ne contient que `userId`/`email`/`role`).
 */
interface UserProfile {
    id: number;
    username: string;
    email: string;
    role: string;
    createdAt: string;
}

interface ApiErrorBody {
    message: string;
}

function formatDate(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString("fr-FR");
}

function ProfilePage() {
    const navigate = useNavigate();
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);

    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [isSaving, setIsSaving] = useState(false);
    const [saveError, setSaveError] = useState<string | null>(null);
    const [saveSuccess, setSaveSuccess] = useState(false);

    /**
     * Garde d'accès (critère d'acceptation « un utilisateur connecté peut
     * accéder à son interface utilisateur », qui suppose l'inverse pour
     * un utilisateur non connecté) — même patron que AdminPage.tsx : une
     * question d'UX, la protection réelle est côté backend
     * (`SecurityConfig`, `GET`/`PATCH /api/v1/users/me` réservés à un JWT
     * valide).
     */
    useEffect(() => {
        if (!getPayload()) {
            navigate("/login");
        }
    }, [navigate]);

    useEffect(() => {
        const abortController = new AbortController();
        setIsLoading(true);
        setLoadError(null);

        async function loadProfile() {
            try {
                const response = await apiFetch("/api/v1/users/me", { signal: abortController.signal });
                if (response.ok) {
                    const data: UserProfile = await response.json();
                    setProfile(data);
                    setUsername(data.username);
                    setEmail(data.email);
                } else {
                    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                    setLoadError(body?.message ?? "Impossible de charger ton profil, réessaie.");
                }
            } catch {
                if (!abortController.signal.aborted) {
                    setLoadError("Impossible de contacter le serveur, réessaie plus tard.");
                }
            } finally {
                if (!abortController.signal.aborted) {
                    setIsLoading(false);
                }
            }
        }

        void loadProfile();

        return () => abortController.abort();
    }, []);

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();
        setIsSaving(true);
        setSaveError(null);
        setSaveSuccess(false);

        try {
            const response = await apiFetch("/api/v1/users/me", {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username, email }),
            });

            if (response.ok) {
                const data: UserProfile = await response.json();
                setProfile(data);
                setSaveSuccess(true);
            } else {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setSaveError(body?.message ?? "Impossible d'enregistrer ces modifications, réessaie.");
            }
        } catch {
            setSaveError("Impossible de contacter le serveur, réessaie plus tard.");
        } finally {
            setIsSaving(false);
        }
    }

    function handleLogout() {
        clearToken();
        navigate("/");
    }

    return (
        <div className="admin-shell">
            <header className="admin-header">
                <h1>Mon profil</h1>
                <Link to="/">← Retour à la carte</Link>
            </header>

            <main className="profile-content">
                {isLoading && <p className="activities-status">Chargement de ton profil…</p>}
                {!isLoading && loadError && (
                    <p className="activities-status activities-status-error">{loadError}</p>
                )}
                {!isLoading && !loadError && profile && (
                    <>
                        {/*
                          Critère d'acceptation « les informations de son compte sont
                          affichées » : role/date d'inscription ne sont pas modifiables
                          (voir UserService#updateProfile côté backend), affichées en
                          lecture seule séparément du formulaire d'édition ci-dessous.
                        */}
                        <dl className="profile-meta">
                            <div>
                                <dt>Rôle</dt>
                                <dd>{profile.role === "ADMIN" ? "Administrateur" : "Utilisateur"}</dd>
                            </div>
                            <div>
                                <dt>Membre depuis</dt>
                                <dd>{formatDate(profile.createdAt)}</dd>
                            </div>
                        </dl>

                        <form className="profile-form" onSubmit={(event) => void handleSubmit(event)}>
                            <label htmlFor="profile-username">Nom d'utilisateur</label>
                            <input
                                id="profile-username"
                                onChange={(event) => setUsername(event.target.value)}
                                required
                                type="text"
                                value={username}
                            />
                            <label htmlFor="profile-email">Email</label>
                            <input
                                id="profile-email"
                                onChange={(event) => setEmail(event.target.value)}
                                required
                                type="email"
                                value={email}
                            />
                            <button disabled={isSaving} type="submit">
                                {isSaving ? "Enregistrement…" : "Enregistrer"}
                            </button>
                            {saveSuccess && (
                                <span className="form-message form-message-success">Profil mis à jour.</span>
                            )}
                            {saveError && <span className="form-message form-message-error">{saveError}</span>}
                        </form>

                        {/* Critère d'acceptation « la déconnexion est accessible ». */}
                        <button className="profile-logout-button" onClick={handleLogout} type="button">
                            Déconnexion
                        </button>
                    </>
                )}
            </main>
        </div>
    );
}

export default ProfilePage;
