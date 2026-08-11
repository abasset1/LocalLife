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

const MARSEILLE_COORDINATES: LatLngExpression = [43.2965, 5.3698];

function App() {
    const [activities, setActivities] = useState<Activity[]>([]);
    const [currentUser, setCurrentUser] = useState(() => getPayload());
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [category, setCategory] = useState("");
    const [address, setAddress] = useState("");
    const [submitStatus, setSubmitStatus] = useState<"idle" | "success" | "error">("idle");
    const [submitError, setSubmitError] = useState<string | null>(null);

    useEffect(() => {
        const abortController = new AbortController();

        async function loadActivities() {
            const response = await fetch("/api/v1/activities", {
                signal: abortController.signal,
            });

            if (response.ok) {
                setActivities(await response.json());
            }
        }

        void loadActivities().catch(() => setActivities([]));

        return () => abortController.abort();
    }, []);

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

            const created: Activity = await response.json();
            setActivities((current) => [...current, created]);
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
