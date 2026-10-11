import { buildDirectionsUrl } from "../../utils/directions";
import { buildGoogleCalendarUrl } from "../../utils/googleCalendar";
import { formatDateOfDay, formatRecurrenceRule, formatTimeRange } from "../../utils/recurrence";
import { formatActivityLocation, formatCategoryLabels } from "./activityUtils";
import { hasCoordinates } from "./globalSearch";
import type { Activity, ActivityDetail, AccessibilitySummary, Category, ExplorerCurrentUser } from "./types";

/**
 * LL-11015 : les cinq catégories d'accessibilité (LL-11009) traduites en
 * labels affichables. Seuls les champs non-`null` sont affichés (voir plus
 * bas) — "non renseigné" reste distinct de "non accessible".
 */
const ACCESSIBILITY_LABELS: Record<keyof AccessibilitySummary, string> = {
    motorImpairment: "Accessible aux personnes à mobilité réduite",
    hearingImpairment: "Accessible aux personnes malentendantes",
    visualImpairment: "Accessible aux personnes malvoyantes",
    psychicImpairment: "Accessible aux personnes avec un handicap psychique",
    intellectualImpairment: "Accessible aux personnes avec un handicap intellectuel",
};

interface ActivityDetailModalProps {
    activity: Activity;
    categories: Category[];
    detail: ActivityDetail | null;
    isLoadingDetail: boolean;
    detailError: string | null;
    currentUser: ExplorerCurrentUser | null;
    isTogglingLike: boolean;
    onToggleLike: (activity: Activity) => void;
    isCancelling: boolean;
    cancelError: string | null;
    onCancel: (activity: Activity) => void;
    /** Ouvre le formulaire de modification (créateur ou administrateur). */
    onEdit: (activity: Activity) => void;
    isDeleting: boolean;
    onDelete: (activity: Activity) => void;
    onClose: () => void;
}

/**
 * LL-11015, critère "absence de données correctement gérée" : chaque
 * section (galerie, description longue, horaires, lieux, liens,
 * accessibilité, tags, informations complémentaires) ne s'affiche que si
 * elle a effectivement un contenu.
 */
function renderDetailSections(detail: ActivityDetail) {
    const locationsById = new Map(detail.locations.map((location) => [location.id, location]));

    return (
        <>
            {detail.media.length > 0 && (
                <div className="explorer-detail-gallery">
                    {detail.media.map((mediaItem) => (
                        <img alt={mediaItem.altText ?? ""} key={mediaItem.id} loading="lazy" src={mediaItem.url} />
                    ))}
                </div>
            )}
            {detail.longDescription && (
                <p className="explorer-detail-row explorer-detail-long-description">{detail.longDescription}</p>
            )}
            {detail.schedules.length > 0 && (
                <div className="explorer-detail-row">
                    <strong>Horaires :</strong>
                    <ul className="explorer-detail-schedules">
                        {detail.schedules.map((schedule) => {
                            const location = schedule.locationId !== null
                                ? locationsById.get(schedule.locationId)
                                : undefined;
                            const recurrenceLabel = formatRecurrenceRule(schedule.recurrenceRule);
                            const timeRangeLabel = formatTimeRange(schedule.startTime, schedule.endTime);
                            const locationLabel = location?.city ?? location?.name ?? null;
                            const exceptions = detail.occurrences.filter((occurrence) =>
                                occurrence.scheduleId === schedule.id
                                && (occurrence.status === "CANCELLED"
                                    || (occurrence.locationId !== null
                                        && occurrence.locationId !== schedule.locationId)));
                            return (
                                <li key={schedule.id}>
                                    {[recurrenceLabel, timeRangeLabel, locationLabel]
                                        .filter((part): part is string => Boolean(part))
                                        .join(" · ")}
                                    {exceptions.length > 0 && (
                                        <ul className="explorer-detail-exceptions">
                                            {exceptions.map((occurrence) => (
                                                <li key={occurrence.id}>
                                                    {formatDateOfDay(occurrence.startAt)} →{" "}
                                                    {occurrence.status === "CANCELLED"
                                                        ? "annulé"
                                                        : locationsById.get(occurrence.locationId ?? -1)?.city
                                                            ?? "lieu modifié"}
                                                </li>
                                            ))}
                                        </ul>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                </div>
            )}
            {detail.locations.length > 0 && (
                <div className="explorer-detail-row">
                    <strong>{detail.locations.length > 1 ? "Lieux :" : "Lieu :"}</strong>
                    <ul>
                        {detail.locations.map((location) => (
                            <li key={location.id}>
                                {[location.name, location.address, location.city, location.postalCode]
                                    .filter((part): part is string => Boolean(part))
                                    .join(", ")}
                            </li>
                        ))}
                    </ul>
                </div>
            )}
            {detail.links.length > 0 && (
                <div className="explorer-detail-row">
                    <strong>Liens et contacts :</strong>
                    <ul>
                        {detail.links.map((link, index) => (
                            <li key={`${link.type}-${index}`}>
                                {link.type === "EMAIL" ? (
                                    <a href={`mailto:${link.value}`}>{link.label ?? link.value}</a>
                                ) : link.type === "PHONE" ? (
                                    <a href={`tel:${link.value}`}>{link.label ?? link.value}</a>
                                ) : (
                                    <a href={link.value} rel="noreferrer" target="_blank">
                                        {link.label ?? link.value}
                                    </a>
                                )}
                            </li>
                        ))}
                    </ul>
                </div>
            )}
            {detail.accessibility && (
                <div className="explorer-detail-row">
                    <strong>Accessibilité :</strong>
                    <ul>
                        {(Object.keys(ACCESSIBILITY_LABELS) as (keyof AccessibilitySummary)[])
                            .filter((key) => detail.accessibility?.[key] === true)
                            .map((key) => (
                                <li key={key}>{ACCESSIBILITY_LABELS[key]}</li>
                            ))}
                    </ul>
                </div>
            )}
            {detail.tags.length > 0 && (
                <div className="explorer-detail-row explorer-detail-tags">
                    {detail.tags.map((tag) => (
                        <span className="explorer-detail-tag" key={tag}>
                            {tag}
                        </span>
                    ))}
                </div>
            )}
            {detail.additionalInfo && (
                <p className="explorer-detail-row">
                    <strong>Informations complémentaires :</strong> {detail.additionalInfo}
                </p>
            )}
        </>
    );
}

/**
 * LL-EF-017 : fiche complète d'une activité, extraite de App.tsx et
 * restylée avec l'identité visuelle LocalLife (classes `explorer-*` dédiées
 * — voir styles.css — distinctes de `.modal-*`/`.activity-detail-*`
 * partagées avec AdminPage/FavoritesPage, pour ne pas modifier leur rendu).
 * Logique inchangée : like (LS-002), annulation (LL-EF-010), export Google
 * Agenda (LL-EF-013), itinéraire (LL-EF-015).
 */
function ActivityDetailModal({
    activity,
    categories,
    detail,
    isLoadingDetail,
    detailError,
    currentUser,
    isTogglingLike,
    onToggleLike,
    isCancelling,
    cancelError,
    onCancel,
    onEdit,
    isDeleting,
    onDelete,
    onClose,
}: ActivityDetailModalProps) {
    const categoryLabels = formatCategoryLabels(activity, categories);
    const calendarOccurrence = detail?.occurrences.find((occurrence) => occurrence.status !== "CANCELLED");
    // Un résultat de recherche (#92) peut n'avoir ni date ni lieu connus : les liens qui en dépendent sont masqués.
    const calendarStart = calendarOccurrence?.startAt ?? activity.startDate;
    const hasDestination = hasCoordinates(activity) || Boolean(activity.address || activity.city);

    return (
        <div className="explorer-modal-overlay" onClick={onClose} role="presentation">
            <div
                aria-labelledby="explorer-activity-detail-title"
                aria-modal="true"
                className="explorer-modal-dialog"
                onClick={(event) => event.stopPropagation()}
                role="dialog"
            >
                <div className="explorer-modal-header">
                    <div className="explorer-modal-header-title">
                        <h2 id="explorer-activity-detail-title">{activity.title}</h2>
                        {currentUser && (
                            <button
                                aria-label={activity.likedByCurrentUser ? "Retirer des favoris" : "Ajouter aux favoris"}
                                aria-pressed={activity.likedByCurrentUser}
                                className="explorer-like-button"
                                disabled={isTogglingLike}
                                onClick={() => onToggleLike(activity)}
                                type="button"
                            >
                                {activity.likedByCurrentUser ? "♥" : "♡"}
                            </button>
                        )}
                    </div>
                    <button aria-label="Fermer" className="explorer-modal-close-button" onClick={onClose} type="button">
                        ✕
                    </button>
                </div>
                <div className="explorer-detail-body">
                    <p className="explorer-detail-row">
                        <strong>Lieu :</strong> {formatActivityLocation(activity)}
                    </p>
                    <p className="explorer-detail-row">
                        <strong>Date :</strong>{" "}
                        {activity.startDate
                            ? new Date(activity.startDate).toLocaleString("fr-FR", {
                                  dateStyle: "long",
                                  timeStyle: "short",
                              })
                            : "non précisée"}
                    </p>
                    {categoryLabels && (
                        <p className="explorer-detail-row">
                            <strong>{categoryLabels.includes(",") ? "Catégories" : "Catégorie"} :</strong>{" "}
                            {categoryLabels}
                        </p>
                    )}
                    {/* Un résultat de recherche (#92) ne porte pas sa source : rien plutôt qu'une ligne vide. */}
                    {activity.sourceName && (
                        <p className="explorer-detail-row">
                            <strong>Source :</strong> {activity.sourceName}
                        </p>
                    )}
                    {activity.publishedAt && (
                        <p className="explorer-detail-row">
                            <strong>Publiée le :</strong>{" "}
                            {new Date(activity.publishedAt).toLocaleDateString("fr-FR", { dateStyle: "long" })}
                        </p>
                    )}
                    {activity.createdByUsername && (
                        <p className="explorer-detail-row">
                            <strong>Créée par :</strong> {activity.createdByUsername}
                        </p>
                    )}
                    {isLoadingDetail && <p className="explorer-detail-row explorer-detail-loading">Chargement du détail…</p>}
                    {detailError && (
                        <p className="explorer-detail-row explorer-detail-error" role="alert">
                            {detailError}
                        </p>
                    )}
                    {detail && renderDetailSections(detail)}
                    <div className="explorer-detail-actions">
                        {calendarStart && (
                            <a
                                className="explorer-detail-action-button"
                                href={buildGoogleCalendarUrl({
                                    title: activity.title,
                                    description: detail?.description ?? null,
                                    address: activity.address,
                                    city: activity.city,
                                    latitude: activity.latitude,
                                    longitude: activity.longitude,
                                    occurrenceStartAt: calendarOccurrence?.startAt ?? null,
                                    occurrenceEndAt: calendarOccurrence?.endAt ?? null,
                                    fallbackStartDate: activity.startDate,
                                })}
                                rel="noreferrer"
                                target="_blank"
                            >
                                Ajouter à Google Agenda
                            </a>
                        )}
                        {hasDestination && (
                            <a
                                className="explorer-detail-action-button explorer-detail-action-button-accent"
                                href={buildDirectionsUrl({
                                    latitude: activity.latitude,
                                    longitude: activity.longitude,
                                    address: activity.address,
                                    city: activity.city,
                                })}
                                rel="noreferrer"
                                target="_blank"
                            >
                                Y aller
                            </a>
                        )}
                    </div>
                    {currentUser && activity.createdBy !== null
                        && (currentUser.userId === activity.createdBy || currentUser.role === "ADMIN") && (
                        <div className="explorer-detail-actions">
                            {cancelError && (
                                <p className="explorer-detail-error" role="alert">
                                    {cancelError}
                                </p>
                            )}
                            <button
                                className="explorer-detail-action-button"
                                disabled={isCancelling || isDeleting}
                                onClick={() => onEdit(activity)}
                                type="button"
                            >
                                Modifier
                            </button>
                            <button
                                className="explorer-cancel-button"
                                disabled={isCancelling || isDeleting}
                                onClick={() => onCancel(activity)}
                                type="button"
                            >
                                {isCancelling ? "Annulation…" : "Annuler cette activité"}
                            </button>
                            <button
                                className="explorer-cancel-button"
                                disabled={isCancelling || isDeleting}
                                onClick={() => onDelete(activity)}
                                type="button"
                            >
                                {isDeleting ? "Suppression…" : "Supprimer"}
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export default ActivityDetailModal;
