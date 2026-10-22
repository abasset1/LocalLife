import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { apiFetch } from "../api/apiClient";
import { clearToken, getPayload } from "../auth/authStorage";
import { USERNAME_MAX_LENGTH, useUsernameAvailability } from "../auth/useUsernameAvailability";
import UsernameStatusMessage from "../components/UsernameStatusMessage";
import { DeleteAccountCard, PasswordCard } from "../components/profile/AccountSecurityCards";
import ActivityAlertsSection from "../components/profile/ActivityAlertsSection";
import { proRequest } from "../components/pro/proApi";
import { Notice, ProfileCard } from "../components/profile/ProfileUi";
import SearchPreferencesForm from "../components/profile/SearchPreferencesForm";
import SubscriptionCard from "../components/subscription/SubscriptionCard";

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
    /** Nouvelle adresse en attente de confirmation (lien envoyé à cette adresse), sinon `null`. */
    pendingEmail: string | null;
    /** Faux pour un compte créé avec Google, sans mot de passe LocalLife. */
    hasPassword: boolean;
    /** Droit de proposer ou modifier une activité (offre Premium ou Pro). */
    canContribute: boolean;
    /** Accès à l'espace professionnel (offre Pro en cours ou rôle attribué) : seul indicateur à utiliser. */
    hasProAccess: boolean;
}

interface ApiErrorBody {
    message: string;
}

function formatDate(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
        ? value
        : date.toLocaleDateString("fr-FR", { month: "long", year: "numeric" });
}

/** Initiales de l'avatar : premières lettres du nom d'utilisateur (ou de l'email). */
function initialsOf(profile: UserProfile): string {
    const source = profile.username.trim() || profile.email;
    const words = source.split(/[\s._-]+/).filter(Boolean);
    const letters = words.length > 1 ? words[0][0] + words[1][0] : source.slice(0, 2);
    return letters.toUpperCase();
}

function ProfilePage() {
    const navigate = useNavigate();
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [searchParams] = useSearchParams();
    const [isLoading, setIsLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);

    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [isSaving, setIsSaving] = useState(false);
    const [saveError, setSaveError] = useState<string | null>(null);
    const [saveSuccess, setSaveSuccess] = useState(false);
    const usernameStatus = useUsernameAvailability(username, profile?.username);

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

    // Accès pro terminé : le propriétaire de food trucks garde un lien pour les consulter et les archiver.
    const [ownsFoodTrucks, setOwnsFoodTrucks] = useState(false);
    const hasProAccess = profile?.hasProAccess;
    useEffect(() => {
        if (hasProAccess !== false) {
            setOwnsFoodTrucks(false);
            return;
        }
        let isCurrent = true;
        void proRequest<unknown[]>("/api/v1/pro/foodtrucks").then((result) => {
            if (isCurrent) {
                setOwnsFoodTrucks(result.ok && result.data.length > 0);
            }
        });
        return () => {
            isCurrent = false;
        };
    }, [hasProAccess]);

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();
        setSaveError(null);
        setSaveSuccess(false);
        if (usernameStatus.kind === "unavailable") {
            setSaveError(usernameStatus.message);
            return;
        }
        setIsSaving(true);

        try {
            const response = await apiFetch("/api/v1/users/me", {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username: username.trim(), email }),
            });

            if (response.ok) {
                const data: UserProfile = await response.json();
                setProfile(data);
                // Une nouvelle adresse n'est appliquée qu'après confirmation : le champ revient à l'adresse actuelle.
                setEmail(data.email);
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
        <div className="pp">
            <header className="pp-topbar">
                <Link aria-label="LocalLife — retour à la carte" className="pp-brand" to="/">
                    <span aria-hidden="true" className="pp-brand-mark">
                        L
                    </span>
                    LocalLife
                </Link>
                <Link className="pp-button pp-button-secondary" to="/">
                    ← Retour à la carte
                </Link>
            </header>

            <main className="pp-main">
                {isLoading && <p className="pp-hint">Chargement de ton profil…</p>}
                {!isLoading && loadError && <Notice kind="error">{loadError}</Notice>}
                {!isLoading && !loadError && profile && (
                    <>
                        {/*
                          Critère d'acceptation « les informations de son compte sont
                          affichées » : role/date d'inscription ne sont pas modifiables
                          (voir UserService#updateProfile côté backend), affichées en
                          lecture seule dans le bandeau, séparément du formulaire.
                        */}
                        <section aria-label="Mon profil" className="pp-hero">
                            <span aria-hidden="true" className="pp-avatar">
                                {initialsOf(profile)}
                            </span>
                            <div className="pp-hero-text">
                                <h1 className="pp-hero-name">{profile.username}</h1>
                                <p className="pp-hero-email">{profile.email}</p>
                                <div className="pp-hero-badges">
                                    <span className="pp-hero-badge">
                                        {profile.role === "ADMIN" ? "Administrateur"
                                            : profile.role === "PRO" ? "Professionnel" : "Utilisateur"}
                                    </span>
                                    {/* Accès relu en base : le lien apparaît dès l'attribution, sans reconnexion. */}
                                    {profile.hasProAccess && (
                                        <Link className="pp-hero-badge" to="/pro">Espace professionnel →</Link>
                                    )}
                                    {!profile.hasProAccess && ownsFoodTrucks && (
                                        <Link className="pp-hero-badge" to="/pro?section=foodtruck">Mes food trucks →</Link>
                                    )}
                                    <span className="pp-hero-badge">Membre depuis {formatDate(profile.createdAt)}</span>
                                </div>
                            </div>
                        </section>

                        <div className="pp-grid">
                            <div className="pp-column">
                                <ProfileCard icon="👤" title="Mon compte" titleId="profile-account-title">
                                    <form className="pp-form" onSubmit={(event) => void handleSubmit(event)}>
                                        <div className="pp-field">
                                            <label className="pp-label" htmlFor="profile-username">
                                                Nom d'utilisateur
                                            </label>
                                            <input
                                                aria-describedby="profile-username-status"
                                                aria-invalid={usernameStatus.kind === "unavailable"}
                                                autoComplete="username"
                                                className="pp-input"
                                                id="profile-username"
                                                maxLength={USERNAME_MAX_LENGTH}
                                                onChange={(event) => setUsername(event.target.value)}
                                                required
                                                type="text"
                                                value={username}
                                            />
                                            <UsernameStatusMessage
                                                id="profile-username-status"
                                                status={usernameStatus}
                                            />
                                        </div>
                                        <div className="pp-field">
                                            <label className="pp-label" htmlFor="profile-email">
                                                Email
                                            </label>
                                            <input
                                                className="pp-input"
                                                id="profile-email"
                                                onChange={(event) => setEmail(event.target.value)}
                                                required
                                                type="email"
                                                value={email}
                                            />
                                        </div>
                                        <div className="pp-actions">
                                            <button
                                                className="pp-button pp-button-primary"
                                                disabled={isSaving}
                                                type="submit"
                                            >
                                                {isSaving ? "Enregistrement…" : "Enregistrer"}
                                            </button>
                                            {saveSuccess && <Notice kind="success">Profil mis à jour.</Notice>}
                                            {saveError && <Notice kind="error">{saveError}</Notice>}
                                        </div>
                                        {profile.pendingEmail && (
                                            <Notice kind="info">
                                                Changement d'adresse en attente : clique sur le lien envoyé à{" "}
                                                <strong>{profile.pendingEmail}</strong> pour le confirmer. D'ici là,
                                                ton adresse reste {profile.email}.
                                            </Notice>
                                        )}
                                    </form>
                                </ProfileCard>

                                {searchParams.get("premium") === "1" && !profile.canContribute && (
                                    <Notice kind="info">
                                        Proposer une activité est réservé aux offres Premium et Pro. Choisis une offre
                                        ci-dessous pour contribuer.
                                    </Notice>
                                )}
                                <SubscriptionCard />

                                <PasswordCard hasPassword={profile.hasPassword} />

                                {/* Critère d'acceptation « la déconnexion est accessible ». */}
                                <button className="pp-button pp-button-danger pp-logout" onClick={handleLogout} type="button">
                                    Se déconnecter
                                </button>

                                <DeleteAccountCard
                                    hasPassword={profile.hasPassword}
                                    isAdmin={profile.role === "ADMIN"}
                                    onDeleted={() => {
                                        clearToken();
                                        navigate("/");
                                    }}
                                />
                            </div>

                            <div className="pp-column pp-column-wide">
                                <SearchPreferencesForm />
                                <ActivityAlertsSection />
                            </div>
                        </div>
                    </>
                )}
            </main>
        </div>
    );
}

export default ProfilePage;
