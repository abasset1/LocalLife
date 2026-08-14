import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import type { LatLngExpression } from "leaflet";
import { MapContainer, Marker, Popup, TileLayer, useMapEvents } from "react-leaflet";
import { Link } from "react-router-dom";
import { apiFetch } from "./api/apiClient";
import { clearToken, getPayload } from "./auth/authStorage";

interface Activity {
    id: number;
    title: string;
    category: string;
    latitude: number;
    longitude: number;
    startDate: string;
}

interface ApiErrorBody {
    message: string;
}

/**
 * États de la géolocalisation navigateur (LL-4010), désormais utilisés
 * pour piloter la recherche `/nearby` (LL-4011, voir plus bas) : tant que
 * la position n'est pas `granted`, la recherche reste centrée sur
 * `MARSEILLE_LATITUDE`/`MARSEILLE_LONGITUDE` (comportement par défaut
 * introduit en LL-4008). `idle` : géolocalisation jamais demandée.
 * `loading` : demande de permission/position en cours. `granted` :
 * position obtenue. `denied` : permission refusée par l'utilisateur.
 * `error` : géolocalisation indisponible ou autre échec (timeout,
 * position indisponible).
 */
type GeolocationStatus = "idle" | "loading" | "granted" | "denied" | "error";

interface UserPosition {
    latitude: number;
    longitude: number;
}

/**
 * Zone actuellement visible sur la carte (LL-4012), au format attendu par
 * le contrat LL-4006 (`docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md`).
 * `null` tant que l'utilisateur n'a pas encore déplacé/zoomé la carte —
 * dans ce cas la recherche reste pilotée par `/nearby` (LL-4008/LL-4011).
 */
interface MapBounds {
    swLatitude: number;
    swLongitude: number;
    neLatitude: number;
    neLongitude: number;
}

/** Délai de neutralisation (ms) entre la fin d'un geste sur la carte et le déclenchement
 * effectif d'une nouvelle recherche — absorbe une rafale de `moveend` rapprochés (ex.
 * glisser/relâcher/glisser à nouveau rapidement), en plus du fait que `moveend`/`zoomend`
 * ne se déclenchent déjà qu'une fois à la fin du geste (pas en continu pendant le
 * déplacement/zoom, contrairement à `move`/`zoom`). */
const MAP_BOUNDS_DEBOUNCE_MS = 400;

/**
 * Composant enfant sans rendu visuel, monté à l'intérieur de
 * `<MapContainer>` : c'est la seule façon d'écouter les événements de la
 * carte avec react-leaflet — `useMapEvents` doit être appelé depuis un
 * descendant de `MapContainer`, qui n'expose pas de props
 * `onMoveEnd`/`onZoomEnd` directement.
 */
function MapBoundsWatcher({ onBoundsChange }: { onBoundsChange: (bounds: MapBounds) => void }) {
    const debounceTimer = useRef<number | null>(null);

    const map = useMapEvents({
        moveend: () => scheduleBoundsUpdate(),
        zoomend: () => scheduleBoundsUpdate(),
    });

    function scheduleBoundsUpdate() {
        if (debounceTimer.current !== null) {
            window.clearTimeout(debounceTimer.current);
        }
        debounceTimer.current = window.setTimeout(() => {
            const bounds = map.getBounds();
            onBoundsChange({
                swLatitude: bounds.getSouthWest().lat,
                swLongitude: bounds.getSouthWest().lng,
                neLatitude: bounds.getNorthEast().lat,
                neLongitude: bounds.getNorthEast().lng,
            });
        }, MAP_BOUNDS_DEBOUNCE_MS);
    }

    useEffect(() => {
        return () => {
            if (debounceTimer.current !== null) {
                window.clearTimeout(debounceTimer.current);
            }
        };
    }, []);

    return null;
}

const MARSEILLE_LATITUDE = 43.2965;
const MARSEILLE_LONGITUDE = 5.3698;
const MARSEILLE_COORDINATES: LatLngExpression = [MARSEILLE_LATITUDE, MARSEILLE_LONGITUDE];

/**
 * Rayon de recherche (LL-4001 : en kilomètres, max 50), utilisé pour la
 * recherche `/nearby` — repli par défaut tant que l'utilisateur n'a pas
 * interagi avec la carte (`mapBounds === null`, voir `MapBounds`
 * ci-dessus). Une fois que la carte a été déplacée/zoomée (LL-4012), ce
 * rayon fixe n'entre plus en jeu : la recherche passe sur
 * `/within-bounds`, pilotée par la zone réellement visible.
 */
const DEFAULT_SEARCH_RADIUS_KM = 50;

/** Valeur du filtre catégorie représentant « pas de filtre ». */
const ALL_CATEGORIES = "";

/**
 * Valeur du filtre date représentant « pas de filtre ». Un `<input
 * type="date">` HTML renvoie nativement une chaîne vide quand il est
 * effacé, et sinon déjà au format ISO-8601 `yyyy-MM-dd` attendu par le
 * contrat LL-4005 — aucune conversion nécessaire avant de la passer telle
 * quelle en paramètre `date`.
 */
const NO_DATE_FILTER = "";

function buildCategoryOptions(items: Activity[]): string[] {
    return Array.from(new Set(items.map((item) => item.category))).sort((a, b) => a.localeCompare(b, "fr"));
}

function App() {
    const [activities, setActivities] = useState<Activity[]>([]);
    const [availableCategories, setAvailableCategories] = useState<string[]>([]);
    const [selectedCategory, setSelectedCategory] = useState(ALL_CATEGORIES);
    const [selectedDate, setSelectedDate] = useState(NO_DATE_FILTER);
    const [currentUser, setCurrentUser] = useState(() => getPayload());
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [category, setCategory] = useState("");
    const [address, setAddress] = useState("");
    const [submitStatus, setSubmitStatus] = useState<"idle" | "success" | "error">("idle");
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const [isLoadingActivities, setIsLoadingActivities] = useState(true);
    const [searchError, setSearchError] = useState<string | null>(null);
    const [geolocationStatus, setGeolocationStatus] = useState<GeolocationStatus>("idle");
    const [userPosition, setUserPosition] = useState<UserPosition | null>(null);
    const [geolocationErrorMessage, setGeolocationErrorMessage] = useState<string | null>(null);
    const [mapBounds, setMapBounds] = useState<MapBounds | null>(null);

    useEffect(() => {
        const abortController = new AbortController();
        setIsLoadingActivities(true);
        setSearchError(null);
        // Supprime immédiatement les anciens marqueurs plutôt que d'attendre la réponse :
        // critère d'acceptation explicite de LL-4012 (« suppression des anciens marqueurs
        // avant affichage des nouveaux résultats »), appliqué ici à toute nouvelle
        // recherche (changement de filtre, de position, ou de zone de carte) par simplicité
        // et cohérence, pas uniquement au cas du déplacement de carte.
        setActivities([]);

        async function loadActivities() {
            const params = new URLSearchParams();
            let endpoint: string;

            if (mapBounds) {
                // LL-4012 : une fois que l'utilisateur a déplacé/zoomé la carte, la zone
                // réellement visible devient la source de vérité pour la recherche —
                // remplace le rayon fixe de LL-4008/LL-4011 — conformément au contrat
                // LL-4006 (docs/02_Architecture/BOUNDING_BOX_SEARCH_CONTRACT.md).
                endpoint = "/api/v1/activities/within-bounds";
                params.set("swLatitude", String(mapBounds.swLatitude));
                params.set("swLongitude", String(mapBounds.swLongitude));
                params.set("neLatitude", String(mapBounds.neLatitude));
                params.set("neLongitude", String(mapBounds.neLongitude));
            } else {
                // Avant toute interaction avec la carte : comportement LL-4008/LL-4011
                // inchangé (rayon fixe autour de la position utilisateur ou de Marseille).
                endpoint = "/api/v1/activities/nearby";
                const latitude = userPosition?.latitude ?? MARSEILLE_LATITUDE;
                const longitude = userPosition?.longitude ?? MARSEILLE_LONGITUDE;
                params.set("latitude", String(latitude));
                params.set("longitude", String(longitude));
                params.set("radius", String(DEFAULT_SEARCH_RADIUS_KM));
            }

            if (selectedCategory !== ALL_CATEGORIES) {
                params.set("category", selectedCategory);
            }
            if (selectedDate !== NO_DATE_FILTER) {
                params.set("date", selectedDate);
            }

            try {
                const response = await fetch(`${endpoint}?${params.toString()}`, {
                    signal: abortController.signal,
                });

                if (response.ok) {
                    const data: Activity[] = await response.json();
                    setActivities(data);
                    // La liste des catégories disponibles n'est reconstruite que sur la
                    // réponse non filtrée (ni catégorie ni date) : sinon elle se
                    // réduirait au fil des sélections (une fois qu'un filtre est actif,
                    // la réponse ne contient plus que ce qui correspond) et l'utilisateur
                    // ne pourrait plus revenir en arrière.
                    if (selectedCategory === ALL_CATEGORIES && selectedDate === NO_DATE_FILTER) {
                        setAvailableCategories(buildCategoryOptions(data));
                    }
                } else {
                    // LL-4013 : état « erreur » distinct de l'état « aucun résultat » —
                    // un échec de la requête (ex. 400/500) ne doit pas être présenté comme
                    // une recherche qui a simplement abouti à zéro activité.
                    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                    setSearchError(body?.message ?? "Impossible de charger les activités, réessaie.");
                    setActivities([]);
                }
            } catch {
                if (!abortController.signal.aborted) {
                    setSearchError("Impossible de contacter le serveur, réessaie plus tard.");
                    setActivities([]);
                }
            } finally {
                // Ne pas repasser `isLoadingActivities` à false pour une requête déjà
                // annulée : la requête suivante (déclenchée par le même changement de
                // dépendance) l'a déjà remis à true, on ne veut pas l'écraser.
                if (!abortController.signal.aborted) {
                    setIsLoadingActivities(false);
                }
            }
        }

        void loadActivities();

        return () => abortController.abort();
    }, [selectedCategory, selectedDate, refreshKey, userPosition, mapBounds]);

    function handleLogout() {
        clearToken();
        setCurrentUser(null);
    }

    /**
     * Déclenchée par un clic explicite sur le bouton « Utiliser ma
     * position » (pas automatiquement au chargement de la page) : demande
     * de permission plus prévisible pour l'utilisateur, et conforme au
     * critère d'acceptation « demande explicite de permission » de
     * LL-4010 (le clic est la demande explicite, avant même que le
     * navigateur affiche sa propre invite de permission).
     *
     * ⚠️ Aucune position utilisateur n'est envoyée au backend ni stockée
     * ailleurs qu'en état React local (`userPosition`) — perdue à chaque
     * rechargement de page, conformément au critère d'acceptation
     * « aucune position utilisateur persistée en base » de LL-4010.
     */
    function handleUseMyLocation() {
        if (!("geolocation" in navigator)) {
            setGeolocationStatus("error");
            setGeolocationErrorMessage("La géolocalisation n'est pas disponible sur ce navigateur.");
            return;
        }

        setGeolocationStatus("loading");
        setGeolocationErrorMessage(null);

        navigator.geolocation.getCurrentPosition(
            (position) => {
                setUserPosition({
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                });
                setGeolocationStatus("granted");
            },
            (error) => {
                if (error.code === error.PERMISSION_DENIED) {
                    setGeolocationStatus("denied");
                    setGeolocationErrorMessage(
                        "Autorisation refusée : impossible d'utiliser ta position pour le moment.");
                } else {
                    // POSITION_UNAVAILABLE ou TIMEOUT.
                    setGeolocationStatus("error");
                    setGeolocationErrorMessage("Impossible de récupérer ta position, réessaie plus tard.");
                }
            },
        );
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSubmitStatus("idle");
        setSubmitError(null);

        try {
            const response = await apiFetch("/api/v1/activities", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ title, description, category, address }),
            });

            if (!response.ok) {
                const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
                setSubmitError(body?.message ?? "Impossible de proposer cette activité, réessaie.");
                setSubmitStatus("error");
                return;
            }

            // Ne pas se contenter d'ajouter `created` localement (comme avant LL-4008) :
            // la liste affichée est désormais filtrée par zone/catégorie via /nearby, et
            // l'activité créée pourrait ne pas correspondre au filtre actif (catégorie
            // différente, hors du rayon de recherche) — un simple refetch reste la
            // source de vérité la plus simple et la plus sûre ici.
            setRefreshKey((current) => current + 1);
            setSubmitStatus("success");
            setTitle("");
            setDescription("");
            setCategory("");
            setAddress("");
        } catch {
            setSubmitError("Impossible de contacter le serveur, réessaie plus tard.");
            setSubmitStatus("error");
        }
    }

    return (
        <main className="application-shell">
            <header className="application-header">
                <h1>LocalLife</h1>
                {currentUser ? (
                    <div className="header-user">
                        <span>Bonjour, {currentUser.email}</span>
                        <button className="header-logout-button" onClick={handleLogout} type="button">
                            Déconnexion
                        </button>
                    </div>
                ) : (
                    <Link className="header-login-link" to="/login">
                        Se connecter
                    </Link>
                )}
            </header>
            <div className="geolocation-bar">
                <button
                    disabled={geolocationStatus === "loading"}
                    onClick={handleUseMyLocation}
                    type="button"
                >
                    {geolocationStatus === "loading" ? "Localisation en cours…" : "Utiliser ma position"}
                </button>
                {geolocationStatus === "granted" && userPosition && (
                    <span className="geolocation-message geolocation-message-success">
                        Position récupérée ({userPosition.latitude.toFixed(4)}, {userPosition.longitude.toFixed(4)})
                    </span>
                )}
                {(geolocationStatus === "denied" || geolocationStatus === "error") && geolocationErrorMessage && (
                    <span className="geolocation-message geolocation-message-error">
                        {geolocationErrorMessage}
                    </span>
                )}
            </div>
            <div className="activity-filters">
                <label htmlFor="category-filter">Filtrer par catégorie</label>
                <select
                    id="category-filter"
                    onChange={(event) => setSelectedCategory(event.target.value)}
                    value={selectedCategory}
                >
                    <option value={ALL_CATEGORIES}>Toutes les catégories</option>
                    {availableCategories.map((availableCategory) => (
                        <option key={availableCategory} value={availableCategory}>
                            {availableCategory}
                        </option>
                    ))}
                </select>
                <label htmlFor="date-filter">Filtrer par date</label>
                <input
                    id="date-filter"
                    onChange={(event) => setSelectedDate(event.target.value)}
                    type="date"
                    value={selectedDate}
                />
                {selectedDate !== NO_DATE_FILTER && (
                    <button
                        aria-label="Effacer le filtre par date"
                        onClick={() => setSelectedDate(NO_DATE_FILTER)}
                        type="button"
                    >
                        ✕
                    </button>
                )}
            </div>
            <form className="contribution-form" onSubmit={(event) => void handleSubmit(event)}>
                <input
                    aria-label="Titre"
                    onChange={(event) => setTitle(event.target.value)}
                    placeholder="Titre"
                    required
                    type="text"
                    value={title}
                />
                <input
                    aria-label="Description"
                    onChange={(event) => setDescription(event.target.value)}
                    placeholder="Description"
                    required
                    type="text"
                    value={description}
                />
                <input
                    aria-label="Catégorie"
                    onChange={(event) => setCategory(event.target.value)}
                    placeholder="Catégorie"
                    required
                    type="text"
                    value={category}
                />
                <input
                    aria-label="Adresse"
                    onChange={(event) => setAddress(event.target.value)}
                    placeholder="Adresse (ex : 10 rue de la République, Marseille)"
                    required
                    type="text"
                    value={address}
                />
                <button type="submit">Proposer une activité</button>
                {submitStatus === "success" && <span className="form-message form-message-success">Activité proposée !</span>}
                {submitStatus === "error" && <span className="form-message form-message-error">{submitError}</span>}
            </form>
            <div className="map-area">
                {/*
                  LL-4013 : 4 états distincts, chacun visible et compréhensible séparément
                  (chargement / résultats / aucun résultat / erreur) — l'état « résultats »
                  n'a pas besoin de message dédié : les marqueurs sur la carte en tiennent
                  lieu.
                */}
                {isLoadingActivities && <p className="activities-status">Chargement des activités…</p>}
                {!isLoadingActivities && searchError && (
                    <p className="activities-status activities-status-error" role="alert">
                        {searchError}
                    </p>
                )}
                {!isLoadingActivities && !searchError && activities.length === 0 && (
                    <p className="activities-status">Aucune activité trouvée dans cette zone.</p>
                )}
                <MapContainer
                    center={MARSEILLE_COORDINATES}
                    className="map"
                    zoom={13}
                    zoomControl
                >
                    <TileLayer
                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    />
                    <MapBoundsWatcher onBoundsChange={setMapBounds} />
                    {activities.map((activity) => (
                        <Marker
                            key={activity.id}
                            position={[activity.latitude, activity.longitude]}
                        >
                            <Popup>
                                <strong>{activity.title}</strong>
                                <br />
                                {activity.category}
                                <br />
                                {new Date(activity.startDate).toLocaleDateString("fr-FR")}
                            </Popup>
                        </Marker>
                    ))}
                </MapContainer>
            </div>
        </main>
    );
}

export default App;
