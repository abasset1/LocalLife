import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import type { LatLngExpression } from "leaflet";
import { MapContainer, Marker, Popup, TileLayer } from "react-leaflet";
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

const MARSEILLE_LATITUDE = 43.2965;
const MARSEILLE_LONGITUDE = 5.3698;
const MARSEILLE_COORDINATES: LatLngExpression = [MARSEILLE_LATITUDE, MARSEILLE_LONGITUDE];

/**
 * Rayon de recherche (LL-4001 : en kilomètres, max 50) utilisé en attendant
 * la géolocalisation utilisateur (LL-4010) et le chargement dynamique
 * selon la zone de la carte (LL-4012). ⚠️ Décision à valider avec Alex :
 * on utilise le rayon maximum autorisé par le contrat autour du point de
 * référence Marseille (déjà utilisé pour centrer la carte), pour se
 * rapprocher du comportement actuel (afficher toutes les activités,
 * qui sont toutes situées autour de Marseille dans les données de démo)
 * en attendant que LL-4010/LL-4012 remplacent ce point fixe par la
 * position réelle de l'utilisateur / la zone visible sur la carte.
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

    useEffect(() => {
        const abortController = new AbortController();

        async function loadActivities() {
            const params = new URLSearchParams({
                latitude: String(MARSEILLE_LATITUDE),
                longitude: String(MARSEILLE_LONGITUDE),
                radius: String(DEFAULT_SEARCH_RADIUS_KM),
            });
            if (selectedCategory !== ALL_CATEGORIES) {
                params.set("category", selectedCategory);
            }
            if (selectedDate !== NO_DATE_FILTER) {
                params.set("date", selectedDate);
            }

            const response = await fetch(`/api/v1/activities/nearby?${params.toString()}`, {
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
            }
        }

        void loadActivities().catch(() => setActivities([]));

        return () => abortController.abort();
    }, [selectedCategory, selectedDate, refreshKey]);

    function handleLogout() {
        clearToken();
        setCurrentUser(null);
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
        </main>
    );
}

export default App;
