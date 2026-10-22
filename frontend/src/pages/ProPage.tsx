import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { getPayload } from "../auth/authStorage";
import { placeTypeLabel } from "../components/places/placeTypes";
import ProClaimsSection from "../components/pro/ProClaimsSection";
import type { ClaimTarget } from "../components/pro/ProClaimsSection";
import ProFoodTrucksSection from "../components/pro/ProFoodTrucksSection";
import { ProPlaceChangesPanel, ProPlaceEditForm } from "../components/pro/ProManagedPlacePanel";
import ProNewPlaceSection from "../components/pro/ProNewPlaceSection";
import { proRequest } from "../components/pro/proApi";
import { PLACE_STATUS_LABELS } from "../components/pro/types";
import type { ManagedPlace } from "../components/pro/types";

const SECTIONS = [
    { value: "places", label: "Mes lieux" },
    { value: "claim", label: "Demander la gestion d'un lieu" },
    { value: "create", label: "Créer un lieu" },
    { value: "foodtruck", label: "Mes food trucks" },
] as const;

type Section = (typeof SECTIONS)[number]["value"];

/**
 * Espace professionnel (ADR-0012, PRO-01) : trois parcours distincts — demander la gestion d'un lieu existant, créer
 * un lieu, créer et gérer un food truck (profil, emplacements, planning) — et la liste des lieux gérés. Les droits sont vérifiés par
 * le backend : un compte sans rôle professionnel reçoit un 403, affiché ici comme tel — sauf s'il possède encore des
 * food trucks (accès pro terminé) : il les consulte et peut les archiver.
 */
function ProPage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const claimPlaceId = Number(searchParams.get("claim"));
    const requestedSection = SECTIONS.find((item) => item.value === searchParams.get("section"))?.value;
    const [section, setSection] = useState<Section>(claimPlaceId ? "claim" : requestedSection ?? "places");
    const [claimTarget, setClaimTarget] = useState<ClaimTarget | null>(null);
    const [places, setPlaces] = useState<ManagedPlace[]>([]);
    const [refreshKey, setRefreshKey] = useState(0);
    const [denied, setDenied] = useState<string | null>(null);
    /** Accès pro terminé mais food trucks encore possédés : consultation et archivage seulement. */
    const [ownsFoodTrucks, setOwnsFoodTrucks] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    /** Lieu dont le formulaire est ouvert : modification directe (`edit`) ou proposition (`change`). */
    const [openPanel, setOpenPanel] = useState<{ id: number; mode: "edit" | "change" } | null>(null);

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
                // Sans accès, la liste des food trucks reste permise à leur propriétaire (403 s'il n'en a aucun).
                void proRequest<unknown[]>("/api/v1/pro/foodtrucks").then((trucks) => {
                    setOwnsFoodTrucks(trucks.ok && trucks.data.length > 0);
                });
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

    async function renounce(place: ManagedPlace) {
        if (!window.confirm(`Ne plus gérer « ${place.name} » ? Il te faudra refaire une demande pour le gérer à nouveau.`)) {
            return;
        }
        const result = await proRequest<void>(`/api/v1/pro/places/${place.id}/renounce`, "POST");
        setMessage(result.ok ? `Tu ne gères plus « ${place.name} ».` : result.message);
        setOpenPanel(null);
        setRefreshKey((key) => key + 1);
    }

    return (
        <div className="admin-shell">
            <header className="admin-header">
                <h1>Espace professionnel</h1>
                <Link to="/">← Retour à la carte</Link>
            </header>

            {denied && ownsFoodTrucks ? (
                <main className="admin-content">
                    <p className="activities-status" role="status">
                        Ton accès professionnel a pris fin : tes food trucks publiés sont masqués de la carte. Tu peux
                        encore les consulter et les archiver ; ils seront republiés si ton accès revient.
                    </p>
                    <ProFoodTrucksSection readOnly />
                    <Link className="admin-approve-button" to="/profile">Retrouver l'offre Pro</Link>
                </main>
            ) : denied ? (
                <main className="admin-content">
                    <p className="activities-status activities-status-error">{denied}</p>
                    <p className="activities-status">
                        L'espace professionnel est inclus dans l'offre Pro : gérer vos lieux et vos food trucks, leurs
                        emplacements et leur planning.
                    </p>
                    <Link className="admin-approve-button" to="/profile">Passer à l'offre Pro</Link>
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
                                            <li className="favorites-list-item contributions-list-item pro-places-item" key={place.id}>
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
                                                    {place.editable && (
                                                        <button className="contribution-action" type="button"
                                                            onClick={() => setOpenPanel({ id: place.id, mode: "edit" })}>
                                                            Modifier
                                                        </button>
                                                    )}
                                                    {place.changeProposable && (
                                                        <button className="contribution-action" type="button"
                                                            onClick={() => setOpenPanel({ id: place.id, mode: "change" })}>
                                                            Proposer une modification
                                                        </button>
                                                    )}
                                                    <button className="contribution-action contribution-action-danger"
                                                        onClick={() => void renounce(place)} type="button">
                                                        Ne plus gérer ce lieu
                                                    </button>
                                                </div>
                                                {openPanel?.id === place.id && openPanel.mode === "edit" && (
                                                    <ProPlaceEditForm
                                                        onCancel={() => setOpenPanel(null)}
                                                        onDone={(text) => {
                                                            setMessage(text);
                                                            setOpenPanel(null);
                                                            setRefreshKey((key) => key + 1);
                                                        }}
                                                        place={place}
                                                    />
                                                )}
                                                {openPanel?.id === place.id && openPanel.mode === "change" && (
                                                    <ProPlaceChangesPanel onClose={() => setOpenPanel(null)} place={place} />
                                                )}
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

                        {section === "foodtruck" && <ProFoodTrucksSection />}
                    </main>
                </>
            )}
        </div>
    );
}

export default ProPage;
