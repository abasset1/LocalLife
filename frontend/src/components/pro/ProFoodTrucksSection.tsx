import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { SPOT_KIND_LABELS, formatHours, spotAddressLine } from "../foodtrucks/foodTruckFormat";
import { CUISINE_TYPES, DEFAULT_MARKER_COLOR, MARKER_COLORS, MARKER_EMOJIS, cuisineLabel, markerOf } from "../foodtrucks/cuisines";
import { proRequest } from "./proApi";
import { resizeImage } from "./resizeImage";
import type { SimilarResult } from "./types";

type SpotKind = "MARKET" | "PRIVATE" | "EVENT" | "OTHER";
type TruckStatus = "DRAFT" | "PENDING" | "PUBLISHED" | "SUSPENDED" | "ARCHIVED" | "REJECTED";
type PresenceStatus = "DRAFT" | "PUBLISHED" | "SUSPENDED" | "ARCHIVED";

/** Food truck vu par son propriétaire (`OwnedFoodTruckResponse`, backend). */
interface OwnedFoodTruck {
    id: number;
    name: string;
    description: string | null;
    category: string;
    contact: string | null;
    status: TruckStatus;
    suspendedByLocalLife: boolean;
    /** Validé une fois par un modérateur : la publication suivante est directe. */
    approved: boolean;
    /** Motif du dernier refus de validation. */
    reviewReason: string | null;
    /** Masqué automatiquement à la fin de l'accès professionnel (republié à son retour). */
    suspendedForAccess: boolean;
    /** Type de cuisine (filtre de la carte), pastille et photo. */
    cuisineType: string | null;
    markerEmoji: string | null;
    markerColor: string | null;
    photoUrl: string | null;
    /** Pages Instagram et Facebook, adresses complètes. */
    instagramUrl: string | null;
    facebookUrl: string | null;
}

/** Présence vue par le propriétaire (`ManagedPresenceResponse`, backend). */
interface ManagedPresence {
    id: number;
    status: PresenceStatus;
    spotName: string | null;
    /** Adresse exacte de l'emplacement, `null` si inconnue. */
    spotAddress: string | null;
    spotKind: SpotKind;
    daysOfWeek: string | null;
    startTime: string | null;
    endTime: string | null;
    validFrom: string | null;
    validUntil: string | null;
    placeId: number | null;
}

const TRUCK_STATUS_LABELS: Record<TruckStatus, string> = {
    DRAFT: "Brouillon",
    PENDING: "En validation",
    PUBLISHED: "Publié",
    SUSPENDED: "Suspendu",
    ARCHIVED: "Archivé",
    REJECTED: "Refusé",
};

const PRESENCE_STATUS_LABELS: Record<PresenceStatus, string> = {
    DRAFT: "Brouillon",
    PUBLISHED: "Publié",
    SUSPENDED: "Désactivé",
    ARCHIVED: "Archivé",
};

const DAYS = [
    { code: "MO", label: "Lun" }, { code: "TU", label: "Mar" }, { code: "WE", label: "Mer" },
    { code: "TH", label: "Jeu" }, { code: "FR", label: "Ven" }, { code: "SA", label: "Sam" },
    { code: "SU", label: "Dim" },
];

const EMPTY_PROFILE = {
    name: "", category: "", description: "", contact: "", cuisineType: "", markerEmoji: "", markerColor: "",
    instagram: "", facebook: "",
};

/** Photo du formulaire : inchangée, nouvelle (à envoyer après l'enregistrement du profil) ou à retirer. */
type PhotoChange = { kind: "keep" } | { kind: "new"; data: string } | { kind: "remove" };
const EMPTY_PRESENCE = {
    spotMode: "place" as "place" | "new",
    placeId: null as number | null,
    placeLabel: "",
    spotName: "", address: "", postalCode: "", city: "", latitude: "", longitude: "",
    spotKind: "MARKET" as SpotKind,
    days: [] as string[],
    startTime: "", endTime: "", validFrom: "", validUntil: "",
};

/** Statuts depuis lesquels le propriétaire peut archiver son food truck. */
const ARCHIVABLE: TruckStatus[] = ["DRAFT", "PENDING", "PUBLISHED", "SUSPENDED", "REJECTED"];

/**
 * Publication possible : un food truck jamais validé part en validation (brouillon ou refusé) ; une fois validé, un
 * brouillon ou un food truck suspendu par son propriétaire se publie directement. Un food truck masqué par Neiri ou
 * par la fin de l'accès professionnel ne se republie pas d'ici.
 */
function canPublish(truck: OwnedFoodTruck): boolean {
    if (truck.suspendedByLocalLife || truck.suspendedForAccess) {
        return false;
    }
    return truck.approved
        ? truck.status === "DRAFT" || truck.status === "SUSPENDED"
        : truck.status === "DRAFT" || truck.status === "REJECTED";
}

function describeDays(days: string | null): string {
    if (!days) {
        return "Tous les jours";
    }
    return days.split(",").map((code) => DAYS.find((day) => day.code === code)?.label ?? code).join(", ");
}

function describePeriod(presence: ManagedPresence): string | null {
    const format = (value: string) => new Date(`${value}T12:00:00`).toLocaleDateString("fr-FR");
    if (presence.validFrom && presence.validUntil) {
        return `du ${format(presence.validFrom)} au ${format(presence.validUntil)}`;
    }
    if (presence.validFrom) {
        return `à partir du ${format(presence.validFrom)}`;
    }
    return presence.validUntil ? `jusqu'au ${format(presence.validUntil)}` : null;
}

/**
 * Food trucks du professionnel (PRO-01 lot C, FOODTRUCK-01) : profil distinct des lieux, publié par son propriétaire,
 * puis emplacements et planning de présence (lieu Neiri existant ou nouvelle adresse, jours, horaires, période).
 * Brouillon d'abord, validation par un modérateur avant la première publication, publication directe ensuite ; un
 * food truck en validation peut être retiré (retour en brouillon) ; un emplacement peut être désactivé, réactivé ou archivé. Le backend vérifie
 * la propriété, les chevauchements et les dates.
 */
function ProFoodTrucksSection({ readOnly = false }: {
    /** Accès professionnel terminé : consultation et archivage seulement (le reste est refusé par le serveur). */
    readOnly?: boolean;
} = {}) {
    const [trucks, setTrucks] = useState<OwnedFoodTruck[]>([]);
    const [refreshKey, setRefreshKey] = useState(0);
    const [editing, setEditing] = useState<OwnedFoodTruck | "new" | null>(null);
    const [profile, setProfile] = useState(EMPTY_PROFILE);
    const [photoChange, setPhotoChange] = useState<PhotoChange>({ kind: "keep" });
    const [selected, setSelected] = useState<OwnedFoodTruck | null>(null);
    const [presences, setPresences] = useState<ManagedPresence[]>([]);
    const [presenceForm, setPresenceForm] = useState(EMPTY_PRESENCE);
    const [editingPresence, setEditingPresence] = useState<ManagedPresence | "new" | null>(null);
    const [placeSearch, setPlaceSearch] = useState({ name: "", city: "" });
    const [placeResults, setPlaceResults] = useState<SimilarResult | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        void proRequest<OwnedFoodTruck[]>("/api/v1/pro/foodtrucks").then((result) => {
            if (result.ok) {
                setTrucks(result.data);
                setSelected((current) => result.data.find((truck) => truck.id === current?.id) ?? null);
            } else {
                setMessage(result.message);
            }
        });
    }, [refreshKey]);

    useEffect(() => {
        if (!selected) {
            setPresences([]);
            return;
        }
        void proRequest<ManagedPresence[]>(`/api/v1/pro/foodtrucks/${selected.id}/presences`).then((result) => {
            if (result.ok) {
                setPresences(result.data);
            }
        });
    }, [selected, refreshKey]);

    function startProfile(truck: OwnedFoodTruck | "new") {
        setEditing(truck);
        setProfile(truck === "new" ? EMPTY_PROFILE : {
            name: truck.name, category: truck.category, description: truck.description ?? "", contact: truck.contact ?? "",
            cuisineType: truck.cuisineType ?? "", markerEmoji: truck.markerEmoji ?? "", markerColor: truck.markerColor ?? "",
            instagram: truck.instagramUrl ?? "", facebook: truck.facebookUrl ?? "",
        });
        setPhotoChange({ kind: "keep" });
        setMessage(null);
    }

    async function saveProfile(event: FormEvent) {
        event.preventDefault();
        setBusy(true);
        const body = {
            ...profile,
            cuisineType: profile.cuisineType || null,
            markerEmoji: profile.markerEmoji || null,
            markerColor: profile.markerColor || null,
            instagram: profile.instagram.trim() || null,
            facebook: profile.facebook.trim() || null,
        };
        let result = editing === "new"
            ? await proRequest<OwnedFoodTruck>("/api/v1/pro/foodtrucks", "POST", body)
            : await proRequest<OwnedFoodTruck>(`/api/v1/pro/foodtrucks/${(editing as OwnedFoodTruck).id}`, "PUT", body);
        // La photo s'enregistre une fois le profil enregistré (un nouveau food truck n'a pas d'identifiant avant).
        if (result.ok && photoChange.kind !== "keep") {
            const photoResult = photoChange.kind === "new"
                ? await proRequest<OwnedFoodTruck>(`/api/v1/pro/foodtrucks/${result.data.id}/photo`, "PUT", { data: photoChange.data })
                : await proRequest<OwnedFoodTruck>(`/api/v1/pro/foodtrucks/${result.data.id}/photo`, "DELETE");
            if (!photoResult.ok) {
                setBusy(false);
                setMessage(`Profil enregistré, mais pas la photo : ${photoResult.message}`);
                setEditing(null);
                setSelected(result.data);
                setRefreshKey((key) => key + 1);
                return;
            }
            result = photoResult;
        }
        setBusy(false);
        if (result.ok) {
            setMessage(editing === "new"
                ? `« ${result.data.name} » créé en brouillon. Ajoute ses emplacements, puis envoie-le en validation.`
                : "Profil enregistré.");
            setEditing(null);
            setSelected(result.data);
            setRefreshKey((key) => key + 1);
        } else {
            setMessage(result.message);
        }
    }

    async function actOnTruck(truck: OwnedFoodTruck, action: "publish" | "suspend" | "archive") {
        if (action === "archive" && !window.confirm(`Archiver « ${truck.name} » ? C'est définitif.`)) {
            return;
        }
        const result = await proRequest<OwnedFoodTruck>(`/api/v1/pro/foodtrucks/${truck.id}/${action}`, "POST");
        if (!result.ok) {
            setMessage(result.message);
        } else if (action === "publish" && result.data.status === "PENDING") {
            setMessage(`« ${truck.name} » est envoyé en validation : un modérateur le vérifie avant sa première publication.`);
        } else if (action === "suspend" && truck.status === "PENDING") {
            setMessage(`Demande retirée : « ${truck.name} » repasse en brouillon.`);
        } else {
            setMessage(null);
        }
        setRefreshKey((key) => key + 1);
    }

    function startPresence(presence: ManagedPresence | "new") {
        setEditingPresence(presence);
        setPlaceResults(null);
        setPresenceForm(presence === "new" ? EMPTY_PRESENCE : {
            ...EMPTY_PRESENCE,
            spotMode: "place",
            placeLabel: presence.spotName ?? "",
            spotKind: presence.spotKind,
            days: presence.daysOfWeek ? presence.daysOfWeek.split(",") : [],
            startTime: presence.startTime?.slice(0, 5) ?? "",
            endTime: presence.endTime?.slice(0, 5) ?? "",
            validFrom: presence.validFrom ?? "",
            validUntil: presence.validUntil ?? "",
        });
        setMessage(null);
    }

    async function searchPlaces(event: FormEvent) {
        event.preventDefault();
        const params = new URLSearchParams(placeSearch);
        const result = await proRequest<SimilarResult>(`/api/v1/pro/places/similar?${params}`);
        if (result.ok) {
            setPlaceResults(result.data);
        } else {
            setMessage(result.message);
        }
    }

    async function savePresence(event: FormEvent) {
        event.preventDefault();
        if (!selected) {
            return;
        }
        const form = presenceForm;
        const isNew = editingPresence === "new";
        const spot = form.spotMode === "new"
            ? {
                newSpot: {
                    name: form.spotName, address: form.address, postalCode: form.postalCode, city: form.city,
                    latitude: form.latitude === "" ? null : Number(form.latitude),
                    longitude: form.longitude === "" ? null : Number(form.longitude),
                },
            }
            : form.placeId !== null ? { placeId: form.placeId } : {};
        const body = {
            ...spot, spotKind: form.spotKind, days: form.days, startTime: form.startTime || null,
            endTime: form.endTime || null, validFrom: form.validFrom || null, validUntil: form.validUntil || null,
        };
        setBusy(true);
        const result = isNew
            ? await proRequest<ManagedPresence>(`/api/v1/pro/foodtrucks/${selected.id}/presences`, "POST", body)
            : await proRequest<ManagedPresence>(`/api/v1/pro/presences/${(editingPresence as ManagedPresence).id}`, "PUT", body);
        setBusy(false);
        if (result.ok) {
            setMessage(isNew ? "Emplacement enregistré en brouillon : vérifie-le, puis publie-le." : "Emplacement modifié.");
            setEditingPresence(null);
            setRefreshKey((key) => key + 1);
        } else {
            setMessage(result.message);
        }
    }

    async function actOnPresence(presence: ManagedPresence, action: "publish" | "suspend" | "archive") {
        const result = await proRequest<ManagedPresence>(`/api/v1/pro/presences/${presence.id}/${action}`, "POST");
        setMessage(result.ok ? null : result.message);
        setRefreshKey((key) => key + 1);
    }

    function setForm<K extends keyof typeof EMPTY_PRESENCE>(key: K, value: (typeof EMPTY_PRESENCE)[K]) {
        setPresenceForm((current) => ({ ...current, [key]: value }));
    }

    async function choosePhoto(file: File | undefined) {
        if (!file) {
            return;
        }
        try {
            setPhotoChange({ kind: "new", data: await resizeImage(file) });
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "Image illisible.");
        }
    }

    const currentPhoto = photoChange.kind === "new"
        ? photoChange.data
        : photoChange.kind === "remove" || editing === "new" || !editing ? null : editing.photoUrl;
    const marker = markerOf(profile);

    const publishedPlaces = placeResults?.candidates.filter((candidate) => candidate.status === "PUBLISHED") ?? [];

    return (
        <section aria-labelledby="pro-foodtruck-title">
            <h2 id="pro-foodtruck-title">Mes food trucks</h2>
            <p className="activities-status">
                Un food truck a sa propre fiche, distincte des lieux. Ses emplacements et son planning disent où et quand
                le trouver ; seuls les emplacements publiés et valables le jour même apparaissent sur la carte.
            </p>
            {message && <p className="activities-status" role="status">{message}</p>}

            {editing ? (
                <form className="contribution-form" onSubmit={(event) => void saveProfile(event)}>
                    <h3>{editing === "new" ? "Créer un food truck" : `Modifier « ${editing.name} »`}</h3>
                    <div className="form-field">
                        <label htmlFor="truck-name">Nom commercial</label>
                        <input id="truck-name" maxLength={255} required type="text" value={profile.name}
                            onChange={(event) => setProfile({ ...profile, name: event.target.value })} />
                    </div>
                    <div className="form-field">
                        <label htmlFor="truck-cuisine-type">Type de cuisine</label>
                        <select id="truck-cuisine-type" required value={profile.cuisineType}
                            onChange={(event) => setProfile({ ...profile, cuisineType: event.target.value })}>
                            <option disabled value="">Choisir…</option>
                            {CUISINE_TYPES.map((cuisine) => (
                                <option key={cuisine.code} value={cuisine.code}>{cuisine.emoji} {cuisine.label}</option>
                            ))}
                        </select>
                        <small>Les visiteurs filtrent la carte et la liste des food trucks sur ce type.</small>
                    </div>
                    <div className="form-field">
                        <label htmlFor="truck-category">Spécialité</label>
                        <input id="truck-category" maxLength={255} placeholder="Tacos maison, crêpes bretonnes…" required
                            type="text" value={profile.category}
                            onChange={(event) => setProfile({ ...profile, category: event.target.value })} />
                    </div>
                    <div className="form-field">
                        <label htmlFor="truck-description">Description</label>
                        <textarea id="truck-description" maxLength={2000} rows={5} value={profile.description}
                            placeholder="Ton histoire, ta carte, tes produits, tes formules…"
                            onChange={(event) => setProfile({ ...profile, description: event.target.value })} />
                        <small>{profile.description.length} / 2000</small>
                    </div>
                    <div className="form-field">
                        <label htmlFor="truck-photo">Photo</label>
                        {currentPhoto && <img alt="Photo du food truck" className="food-truck-photo-preview" src={currentPhoto} />}
                        <input accept="image/jpeg,image/png,image/webp" id="truck-photo" type="file"
                            onChange={(event) => void choosePhoto(event.target.files?.[0])} />
                        {currentPhoto && (
                            <button className="contribution-action" onClick={() => setPhotoChange({ kind: "remove" })} type="button">
                                Retirer la photo
                            </button>
                        )}
                    </div>
                    <fieldset className="form-field">
                        <legend>Pastille sur la carte</legend>
                        <p className="food-truck-marker-current">
                            <span aria-hidden="true" className="food-truck-marker-preview" style={{ backgroundColor: marker.color }}>
                                {marker.emoji}
                            </span>{" "}
                            Aperçu{profile.markerEmoji ? "" : " (emoji du type de cuisine par défaut)"}
                        </p>
                        <div aria-label="Emoji de la pastille" className="food-truck-marker-picker" role="group">
                            <button aria-label="Emoji par défaut" aria-pressed={profile.markerEmoji === ""} type="button"
                                onClick={() => setProfile({ ...profile, markerEmoji: "" })}>
                                ∅
                            </button>
                            {MARKER_EMOJIS.map((emoji) => (
                                <button aria-label={`Emoji ${emoji}`} aria-pressed={profile.markerEmoji === emoji} key={emoji}
                                    onClick={() => setProfile({ ...profile, markerEmoji: emoji })} type="button">
                                    {emoji}
                                </button>
                            ))}
                        </div>
                        <div aria-label="Couleur de la pastille" className="food-truck-marker-picker" role="group">
                            {MARKER_COLORS.map((color) => (
                                <button aria-label={`Couleur ${color}`} key={color} style={{ backgroundColor: color }} type="button"
                                    aria-pressed={(profile.markerColor || DEFAULT_MARKER_COLOR) === color}
                                    onClick={() => setProfile({ ...profile, markerColor: color === DEFAULT_MARKER_COLOR ? "" : color })} />
                            ))}
                            <input aria-label="Autre couleur" type="color" value={profile.markerColor || DEFAULT_MARKER_COLOR}
                                onChange={(event) => setProfile({ ...profile, markerColor: event.target.value })} />
                        </div>
                    </fieldset>
                    <div className="form-field">
                        <label htmlFor="truck-contact">Contact ou lien utile</label>
                        <input id="truck-contact" maxLength={512} placeholder="Téléphone, site, réseau social…"
                            type="text" value={profile.contact}
                            onChange={(event) => setProfile({ ...profile, contact: event.target.value })} />
                    </div>
                    <div className="form-field">
                        <label htmlFor="truck-instagram">Instagram</label>
                        <input id="truck-instagram" maxLength={300} placeholder="https://www.instagram.com/moncamion ou @moncamion"
                            type="text" value={profile.instagram}
                            onChange={(event) => setProfile({ ...profile, instagram: event.target.value })} />
                    </div>
                    <div className="form-field">
                        <label htmlFor="truck-facebook">Facebook</label>
                        <input id="truck-facebook" maxLength={300} placeholder="https://www.facebook.com/moncamion"
                            type="text" value={profile.facebook}
                            onChange={(event) => setProfile({ ...profile, facebook: event.target.value })} />
                        <small>Facultatifs : les liens s'affichent sur la fiche et la popup du food truck.</small>
                    </div>
                    <div className="modal-actions">
                        <button className="admin-approve-button" disabled={busy} type="submit">Enregistrer</button>
                        <button className="contribution-action" onClick={() => setEditing(null)} type="button">
                            Annuler
                        </button>
                    </div>
                </form>
            ) : !readOnly && (
                <button className="admin-approve-button" onClick={() => startProfile("new")} type="button">
                    Créer un food truck
                </button>
            )}

            {trucks.length === 0 ? (
                <p className="activities-status">Tu n'as pas encore de food truck.</p>
            ) : (
                <ul className="favorites-list">
                    {trucks.map((truck) => (
                        <li className="favorites-list-item contributions-list-item" key={truck.id}>
                            <div className="favorites-list-item-info">
                                <span className="favorites-list-item-title">{`${markerOf(truck).emoji} ${truck.name}`}</span>
                                <span className="favorites-list-item-meta">
                                    {TRUCK_STATUS_LABELS[truck.status]} · {[cuisineLabel(truck.cuisineType), truck.category].filter(Boolean).join(" · ")}
                                    {truck.suspendedByLocalLife ? " · suspendu par Neiri" : ""}
                                </span>
                                {truck.status === "PENDING" && (
                                    <span className="favorites-list-item-meta">
                                        Un modérateur vérifie ta fiche avant sa première publication.
                                    </span>
                                )}
                                {truck.status === "REJECTED" && truck.reviewReason && (
                                    <span className="favorites-list-item-meta">Motif du refus : {truck.reviewReason}</span>
                                )}
                                {truck.suspendedForAccess && (
                                    <span className="favorites-list-item-meta">
                                        Masqué : ton accès professionnel a pris fin. Il sera republié si ton accès revient.
                                    </span>
                                )}
                            </div>
                            <div className="contributions-list-item-actions">
                                <button className="contribution-action" onClick={() => setSelected(truck)} type="button">
                                    Emplacements
                                </button>
                                {!readOnly && truck.status !== "ARCHIVED" && (
                                    <button className="contribution-action" onClick={() => startProfile(truck)} type="button">
                                        Modifier
                                    </button>
                                )}
                                {!readOnly && canPublish(truck) && (
                                    <button className="contribution-action" onClick={() => void actOnTruck(truck, "publish")}
                                        type="button">
                                        {truck.approved ? "Publier" : "Envoyer en validation"}
                                    </button>
                                )}
                                {!readOnly && truck.status === "PENDING" && (
                                    <button className="contribution-action" onClick={() => void actOnTruck(truck, "suspend")}
                                        type="button">
                                        Retirer la demande
                                    </button>
                                )}
                                {!readOnly && truck.status === "PUBLISHED" && (
                                    <button className="contribution-action" onClick={() => void actOnTruck(truck, "suspend")}
                                        type="button">
                                        Suspendre
                                    </button>
                                )}
                                {ARCHIVABLE.includes(truck.status) && (
                                    <button className="contribution-action contribution-action-danger" type="button"
                                        onClick={() => void actOnTruck(truck, "archive")}>
                                        Archiver
                                    </button>
                                )}
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            {selected && (
                <section aria-labelledby="pro-presences-title">
                    <h3 id="pro-presences-title">Emplacements et planning de « {selected.name} »</h3>
                    <p className="activities-status">
                        Neiri n'accorde aucune autorisation de stationnement : tu restes responsable des autorisations
                        nécessaires pour chaque emplacement.
                    </p>
                    {presences.length === 0 ? (
                        <p className="activities-status">Aucun emplacement pour l'instant.</p>
                    ) : (
                        <ul className="favorites-list">
                            {presences.map((presence) => (
                                <li className="favorites-list-item contributions-list-item" key={presence.id}>
                                    <div className="favorites-list-item-info">
                                        <span className="favorites-list-item-title">
                                            {presence.spotName ?? "Emplacement"} · {SPOT_KIND_LABELS[presence.spotKind]}
                                        </span>
                                        {spotAddressLine(presence) && (
                                            <span className="favorites-list-item-meta">🏠 {spotAddressLine(presence)}</span>
                                        )}
                                        <span className="favorites-list-item-meta">
                                            {PRESENCE_STATUS_LABELS[presence.status]} · {describeDays(presence.daysOfWeek)}
                                            {formatHours(presence) ? ` · ${formatHours(presence)}` : ""}
                                            {describePeriod(presence) ? ` · ${describePeriod(presence)}` : ""}
                                        </span>
                                    </div>
                                    {presence.status !== "ARCHIVED" && (
                                        <div className="contributions-list-item-actions">
                                            {!readOnly && (
                                                <button className="contribution-action" onClick={() => startPresence(presence)}
                                                    type="button">
                                                    Modifier
                                                </button>
                                            )}
                                            {!readOnly && presence.status !== "PUBLISHED" && (
                                                <button className="contribution-action" type="button"
                                                    onClick={() => void actOnPresence(presence, "publish")}>
                                                    {presence.status === "SUSPENDED" ? "Réactiver" : "Publier"}
                                                </button>
                                            )}
                                            {!readOnly && presence.status === "PUBLISHED" && (
                                                <button className="contribution-action" type="button"
                                                    onClick={() => void actOnPresence(presence, "suspend")}>
                                                    Désactiver
                                                </button>
                                            )}
                                            <button className="contribution-action contribution-action-danger" type="button"
                                                onClick={() => void actOnPresence(presence, "archive")}>
                                                Archiver
                                            </button>
                                        </div>
                                    )}
                                </li>
                            ))}
                        </ul>
                    )}

                    {editingPresence === null ? (
                        !readOnly && selected.status !== "ARCHIVED" && (
                            <button className="admin-approve-button" onClick={() => startPresence("new")} type="button">
                                Ajouter un emplacement
                            </button>
                        )
                    ) : (
                        <form className="contribution-form" onSubmit={(event) => void savePresence(event)}>
                            <h4>{editingPresence === "new" ? "Nouvel emplacement" : "Modifier l'emplacement"}</h4>
                            <fieldset className="form-field">
                                <legend>Où</legend>
                                <label>
                                    <input checked={presenceForm.spotMode === "place"} name="spot-mode" type="radio"
                                        onChange={() => setForm("spotMode", "place")} />
                                    {" "}{editingPresence === "new" ? "Un lieu Neiri existant" : "Garder ou choisir un lieu existant"}
                                </label>
                                <label>
                                    <input checked={presenceForm.spotMode === "new"} name="spot-mode" type="radio"
                                        onChange={() => setForm("spotMode", "new")} />
                                    {" "}Une autre adresse
                                </label>
                            </fieldset>

                            {presenceForm.spotMode === "place" ? (
                                <div className="form-field">
                                    {presenceForm.placeLabel && <p>Emplacement choisi : {presenceForm.placeLabel}</p>}
                                    <div className="pro-place-search">
                                        <input aria-label="Nom du lieu" placeholder="Nom du lieu (marché, halle…)" type="text"
                                            value={placeSearch.name}
                                            onChange={(event) => setPlaceSearch({ ...placeSearch, name: event.target.value })} />
                                        <input aria-label="Commune du lieu" placeholder="Commune" type="text"
                                            value={placeSearch.city}
                                            onChange={(event) => setPlaceSearch({ ...placeSearch, city: event.target.value })} />
                                        <button className="contribution-action" onClick={(event) => void searchPlaces(event)}
                                            type="button">
                                            Rechercher
                                        </button>
                                    </div>
                                    {placeResults && publishedPlaces.length === 0 && (
                                        <p className="activities-status">Aucun lieu publié trouvé : saisis plutôt l'adresse.</p>
                                    )}
                                    {publishedPlaces.map(({ place }) => (
                                        <label key={place.id}>
                                            <input checked={presenceForm.placeId === place.id} name="spot-place" type="radio"
                                                onChange={() => setPresenceForm((current) => ({
                                                    ...current, placeId: place.id, placeLabel: place.name,
                                                }))} />
                                            {" "}{place.name}{place.city ? ` (${place.city})` : ""}
                                        </label>
                                    ))}
                                </div>
                            ) : (
                                <>
                                    <div className="form-field">
                                        <label htmlFor="spot-name">Nom de l'emplacement</label>
                                        <input id="spot-name" placeholder="Parking de la zone d'activité" type="text"
                                            value={presenceForm.spotName} onChange={(event) => setForm("spotName", event.target.value)} />
                                    </div>
                                    <div className="form-field">
                                        <label htmlFor="spot-address">Adresse</label>
                                        <input id="spot-address" type="text" value={presenceForm.address}
                                            onChange={(event) => setForm("address", event.target.value)} />
                                    </div>
                                    <div className="form-field">
                                        <label htmlFor="spot-postal-code">Code postal</label>
                                        <input id="spot-postal-code" type="text" value={presenceForm.postalCode}
                                            onChange={(event) => setForm("postalCode", event.target.value)} />
                                    </div>
                                    <div className="form-field">
                                        <label htmlFor="spot-city">Commune</label>
                                        <input id="spot-city" required type="text" value={presenceForm.city}
                                            onChange={(event) => setForm("city", event.target.value)} />
                                    </div>
                                    <div className="form-field">
                                        <label htmlFor="spot-latitude">Latitude (facultatif, déduite de l'adresse)</label>
                                        <input id="spot-latitude" step="any" type="number" value={presenceForm.latitude}
                                            onChange={(event) => setForm("latitude", event.target.value)} />
                                    </div>
                                    <div className="form-field">
                                        <label htmlFor="spot-longitude">Longitude</label>
                                        <input id="spot-longitude" step="any" type="number" value={presenceForm.longitude}
                                            onChange={(event) => setForm("longitude", event.target.value)} />
                                    </div>
                                </>
                            )}

                            <div className="form-field">
                                <label htmlFor="spot-kind">Type d'emplacement</label>
                                <select id="spot-kind" value={presenceForm.spotKind}
                                    onChange={(event) => setForm("spotKind", event.target.value as SpotKind)}>
                                    {(Object.keys(SPOT_KIND_LABELS) as SpotKind[]).map((kind) => (
                                        <option key={kind} value={kind}>{kind === "OTHER" ? "Autre" : SPOT_KIND_LABELS[kind]}</option>
                                    ))}
                                </select>
                            </div>
                            <fieldset className="form-field">
                                <legend>Jours (aucun coché : tous les jours)</legend>
                                {DAYS.map((day) => (
                                    <label key={day.code}>
                                        <input checked={presenceForm.days.includes(day.code)} type="checkbox"
                                            onChange={(event) => setForm("days", event.target.checked
                                                ? [...presenceForm.days, day.code]
                                                : presenceForm.days.filter((code) => code !== day.code))} />
                                        {" "}{day.label}
                                    </label>
                                ))}
                            </fieldset>
                            <div className="form-field">
                                <label htmlFor="spot-start">Arrivée</label>
                                <input id="spot-start" type="time" value={presenceForm.startTime}
                                    onChange={(event) => setForm("startTime", event.target.value)} />
                            </div>
                            <div className="form-field">
                                <label htmlFor="spot-end">Départ</label>
                                <input id="spot-end" type="time" value={presenceForm.endTime}
                                    onChange={(event) => setForm("endTime", event.target.value)} />
                            </div>
                            <div className="form-field">
                                <label htmlFor="spot-from">À partir du (facultatif)</label>
                                <input id="spot-from" type="date" value={presenceForm.validFrom}
                                    onChange={(event) => setForm("validFrom", event.target.value)} />
                            </div>
                            <div className="form-field">
                                <label htmlFor="spot-until">Jusqu'au (facultatif)</label>
                                <input id="spot-until" type="date" value={presenceForm.validUntil}
                                    onChange={(event) => setForm("validUntil", event.target.value)} />
                            </div>
                            <div className="modal-actions">
                                <button className="admin-approve-button" disabled={busy} type="submit">
                                    Enregistrer en brouillon
                                </button>
                                <button className="contribution-action" onClick={() => setEditingPresence(null)} type="button">
                                    Annuler
                                </button>
                            </div>
                        </form>
                    )}
                </section>
            )}
        </section>
    );
}

export default ProFoodTrucksSection;
