import { useEffect, useState } from "react";
import type { LatLngExpression } from "leaflet";
import { MapContainer, Marker, Popup, TileLayer } from "react-leaflet";

interface Activity {
    id: number;
    title: string;
    category: string;
    latitude: number;
    longitude: number;
    startDate: string;
}

const MARSEILLE_COORDINATES: LatLngExpression = [43.2965, 5.3698];

function App() {
    const [activities, setActivities] = useState<Activity[]>([]);

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

    return (
        <main className="application-shell">
            <header className="application-header">
                <h1>LocalLife</h1>
            </header>
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
