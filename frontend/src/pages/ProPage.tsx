import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { getPayload } from "../auth/authStorage";
import { placeTypeLabel } from "../components/places/placeTypes";
import ProClaimsSection from "../components/pro/ProClaimsSection";
import type { ClaimTarget } from "../components/pro/ProClaimsSection";
import ProNewPlaceSection from "../components/pro/ProNewPlaceSection";
import { proRequest } from "../components/pro/proApi";
import { PLACE_STATUS_LABELS } from "../components/pro/types";
import type { ManagedPlace } from "../components/pro/types";

const SECTIONS = [
    { value: "places", label: "Mes lieux" },
    { value: "claim", label: "Demander la gestion d'un lieu" },
    { value: "create", label: "Créer un lieu" },
    { value: "foodtruck", label: "Créer un food truck" },
] as const;

type Section = (typeof SECTIONS)[number]["value"];

/**
 * Espace professionnel (ADR-0012, PRO-01) : trois parcours distincts — demander la gestion d'un lieu existant, créer
 * un lieu, créer un food truck (à venir avec ARCHI-01) — et la liste des lieux gérés. Les droits sont vérifiés par
 * le backend : un compte sans rôle professionnel reçoit un 403, affiché ici comme tel.
 */
function ProPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const claimPlaceId = Number(searchParams.get("claim"));
    const [section, setSection] = useState<Section>(claimPlaceId ? "claim" : "places");
    const [claimTarget, setClaimTarget] = useState<ClaimTarget | null>(null);
    const [places, setPlaces] = useState<ManagedPlace[]>([]);
    const [refreshKey, setRefreshKey] = useState(0);
    const [denied, setDenied] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);

    useEffect(() => {
        if (!getPayload()) {
            navigate("/login");
        }
    }, [navigate]);

    useEffect(() => {
        if (!claimPlaceId) {
            return;
        }
        // Lien « Demander la gestion de ce lieu » depuis une fiche publique.
        void fetch(`/api/v1/places/${claimPlaceId}`).then(async (response) => {
            if (response.ok) {
                const place = (await response.json()) as { id: number; name: string; city: string | null };
                setClaimTarget({ id: place.id, name: place.name, city: place.city });
            }
        }).catch(() => undefined);
    }, [claimPlaceId]);

    useEffect(() => {
        void proRequest<ManagedPlace[]>("/api/v1/pro/places").then((result) => {
            if (result.ok) {
                setPlaces(result.data);
                setDenied(null);
            } else if (result.status === 403) {
                setDenied(result.message);
            } else {
                setMessage(result.message);
            }
        });
    }, [refreshKey]);

    async function submit(place: ManagedPlace) {
        const result = await proRequest<ManagedPlace>(`/api/v1/pro/places/${place.id}/submit`, "POST");
        setMessage(result.ok ? `« ${place.name} » envoyé à la modération.` : result.message);
        setRefreshKey((key) => key + 1);
    }

    return (
        <div className="admin-shell">
            <header className="admin-header">
                <h1>Espace professionnel</h1>
                <Link to="/">← Retour à la carte</Link>
            </header>

            {denied ? (
                <main className="admin-content">
                    <p className="activities-status activities-status-error">{denied}</p>
                    <p className="activities-status">
                        L'espace professionnel est réservé aux comptes professionnels. Contacte l'équipe LocalLife pour
                        faire activer le tien.
                    </p>
                </main>
            ) : (
                <>
                    <nav className="admin-tabs admin-section-tabs">
                        {SECTIONS.map((item) => (
                            <button
                                className={item.value === section ? "admin-tab admin-tab-active" : "admin-tab"}
                                key={item.value}
                                onClick={() => setSection(item.value)}
                                type="button"
                            >
                                {item.label}
                            </button>
                        ))}
                    </nav>

                    <main className="admin-content">
                        {section === "places" && (
                            <section aria-labelledby="pro-places-title">
                                <h2 id="pro-places-title">Mes lieux</h2>
                                {message && <p className="activities-status" role="status">{message}</p>}
                                {places.length === 0 ? (
                                    <p className="activities-status">
                                        Tu ne gères encore aucun lieu : demande la gestion d'un lieu existant ou crée le tien.
                                    </p>
                                ) : (
                                    <ul className="favorites-list">
                                        {places.map((place) => (
                                            <li className="favorites-list-item contributions-list-item" key={place.id}>
                                                <div className="favorites-list-item-info">
                                                    <span className="favorites-list-item-title">{place.name}</span>
                                                    <span className="favorites-list-item-meta">
                                                        {PLACE_STATUS_LABELS[place.status]} · {placeTypeLabel(place.placeType)}
                                                        {place.city ? ` · ${place.city}` : ""}
                                                        {place.origin === "IMPORTED" ? " · fiche importée" : ""}
                                                    </span>
                                                </div>
                                                <div className="contributions-list-item-actions">
                                                    {place.status === "DRAFT" && (
                                                        <button className="contribution-action" onClick={() => void submit(place)}
                                                            type="button">
                                                            Envoyer à la modération
                                                        </button>
                                                    )}
                                                </div>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </section>
                        )}

                        {section === "claim" && <ProClaimsSection initialTarget={claimTarget} />}

                        {section === "create" && (
                            <ProNewPlaceSection
                                onClaim={(target) => {
                                    setClaimTarget(target);
                                    setSection("claim");
                                }}
                                onCreated={() => setRefreshKey((key) => key + 1)}
                            />
                        )}

                        {section === "foodtruck" && (
                            <section aria-labelledby="pro-foodtruck-title">
                                <h2 id="pro-foodtruck-title">Créer un food truck</h2>
                                <p className="activities-status">
                                    Bientôt disponible. Un food truck aura sa propre fiche, distincte des lieux, avec ses
                                    emplacements et son planning de présence.
                                </p>
                            </section>
                        )}
                    </main>
                </>
            )}
        </div>
    );
}

export default ProPage;
