import { MapContainer, TileLayer } from "react-leaflet";

const MARSEILLE_COORDINATES = [43.2965, 5.3698];

function App() {
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
            </MapContainer>
        </main>
    );
}

export default App;
