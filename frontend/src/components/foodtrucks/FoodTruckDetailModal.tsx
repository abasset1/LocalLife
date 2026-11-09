import { useEffect, useState } from "react";
import type { FoodTruck } from "../explorer/types";
import FollowBell from "../follow/FollowBell";
import { useLaunchFeatures } from "../../features/launchFeatures";
import { buildDirectionsUrl } from "../../utils/directions";
import { cuisineLabel, markerOf } from "./cuisines";
import { SPOT_KIND_LABELS, contactUrl, describeDays, formatHours, socialUrl, spotAddressLine } from "./foodTruckFormat";

/** Présence du planning public (`PresenceResponse`, backend). */
interface PresenceDetail {
    id: number;
    spotName: string | null;
    spotAddress: string | null;
    latitude: number | null;
    longitude: number | null;
    spotKind: FoodTruck["spotKind"];
    daysOfWeek: string | null;
    startTime: string | null;
    endTime: string | null;
    validFrom: string | null;
    validUntil: string | null;
}

/** Fiche publique d'un food truck (`FoodTruckDetailResponse`, backend). */
interface FoodTruckDetail {
    id: number;
    name: string;
    description: string | null;
    category: string;
    contact: string | null;
    cuisineType: string | null;
    markerEmoji: string | null;
    markerColor: string | null;
    photoUrl: string | null;
    instagramUrl: string | null;
    facebookUrl: string | null;
    presences: PresenceDetail[];
}

function formatPeriod(presence: PresenceDetail): string | null {
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
 * Fiche d'un food truck, ouverte depuis la popup de sa pastille ou la liste : même fenêtre que la fiche d'une
 * activité. Le profil vient de la carte (affiché tout de suite), le planning complet de `GET /foodtrucks/{id}`.
 */
function FoodTruckDetailModal({ foodTruck, onClose }: { foodTruck: FoodTruck; onClose: () => void }) {
    const [detail, setDetail] = useState<FoodTruckDetail | null>(null);
    const [failed, setFailed] = useState(false);
    const { premium: premiumOpen } = useLaunchFeatures();

    useEffect(() => {
        const controller = new AbortController();
        fetch(`/api/v1/foodtrucks/${foodTruck.id}`, { signal: controller.signal })
            .then(async (response) => {
                if (response.ok) {
                    setDetail((await response.json()) as FoodTruckDetail);
                } else {
                    setFailed(true);
                }
            })
            .catch(() => {
                if (!controller.signal.aborted) {
                    setFailed(true);
                }
            });
        return () => controller.abort();
    }, [foodTruck.id]);

    useEffect(() => {
        const onKey = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                onClose();
            }
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, [onClose]);

    const photoUrl = detail?.photoUrl ?? foodTruck.photoUrl ?? null;
    const description = detail?.description ?? foodTruck.description ?? null;
    const contact = detail?.contact ?? foodTruck.contact ?? null;
    const cuisine = cuisineLabel(detail?.cuisineType ?? foodTruck.cuisineType);
    const link = contactUrl(contact);
    const marker = markerOf(detail ?? foodTruck);
    const hours = formatHours(foodTruck);
    const directions = buildDirectionsUrl({
        latitude: foodTruck.latitude, longitude: foodTruck.longitude, address: foodTruck.spotAddress ?? null, city: null,
    });
    const instagram = socialUrl(detail?.instagramUrl ?? foodTruck.instagramUrl, "instagram");
    const facebook = socialUrl(detail?.facebookUrl ?? foodTruck.facebookUrl, "facebook");

    return (
        <div className="explorer-modal-overlay" onClick={onClose} role="presentation">
            <div
                aria-labelledby="explorer-food-truck-detail-title"
                aria-modal="true"
                className="explorer-modal-dialog"
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className="explorer-modal-header">
                    <div className="explorer-modal-header-title">
                        <span aria-hidden="true" className="food-truck-marker-preview" style={{ backgroundColor: marker.color }}>
                            {marker.emoji}
                        </span>
                        <h2 id="explorer-food-truck-detail-title">{foodTruck.name}</h2>
                    </div>
                    <button aria-label="Fermer" className="explorer-modal-close-button" onClick={onClose} type="button">
                        ✕
                    </button>
                </div>
                <div className="explorer-detail-body">
                    {premiumOpen && <FollowBell id={foodTruck.id} title={foodTruck.name} type="foodtruck" />}
                    {photoUrl && (
                        <div className="explorer-detail-gallery">
                            <img alt={`Photo de ${foodTruck.name}`} src={photoUrl} />
                        </div>
                    )}
                    <p className="explorer-detail-row">
                        <strong>Cuisine :</strong> {[cuisine, detail?.category ?? foodTruck.category].filter(Boolean).join(" · ")}
                    </p>
                    <p className="explorer-detail-row">
                        <strong>Aujourd'hui :</strong>{" "}
                        {[SPOT_KIND_LABELS[foodTruck.spotKind], foodTruck.spotName, hours].filter(Boolean).join(" · ")}
                    </p>
                    {foodTruck.spotAddress && (
                        <p className="explorer-detail-row">
                            <strong>Adresse :</strong> {foodTruck.spotAddress} ·{" "}
                            <a href={directions} rel="noopener noreferrer" target="_blank">Y aller</a>
                        </p>
                    )}
                    {description && <p className="explorer-detail-row explorer-detail-long-description">{description}</p>}
                    {contact && (
                        <p className="explorer-detail-row">
                            <strong>Contact :</strong>{" "}
                            {link ? <a href={link} rel="noopener noreferrer" target="_blank">{contact}</a> : contact}
                        </p>
                    )}
                    {(instagram || facebook) && (
                        <p className="explorer-detail-row food-truck-social-links">
                            <strong>Réseaux :</strong>
                            {instagram && <a href={instagram} rel="noopener noreferrer" target="_blank">Instagram</a>}
                            {facebook && <a href={facebook} rel="noopener noreferrer" target="_blank">Facebook</a>}
                        </p>
                    )}
                    {detail && detail.presences.length > 0 && (
                        <div className="explorer-detail-row">
                            <strong>Où le trouver :</strong>
                            <ul className="explorer-detail-schedules">
                                {detail.presences.map((presence) => (
                                    <li key={presence.id}>
                                        {[describeDays(presence.daysOfWeek), formatHours(presence),
                                            [SPOT_KIND_LABELS[presence.spotKind], presence.spotName].filter(Boolean).join(" "),
                                            spotAddressLine(presence), formatPeriod(presence)].filter(Boolean).join(" · ")}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}
                    {failed && (
                        <p className="explorer-detail-row explorer-detail-error" role="alert">
                            Le planning de ce food truck n'a pas pu être chargé.
                        </p>
                    )}
                </div>
            </div>
        </div>
    );
}

export default FoodTruckDetailModal;
