import { useEffect, useState } from "react";
import type { Activity } from "../explorer/types";

/** Nombre d'activités affichées dans la fiche courte d'un lieu ; l'API en renvoie au plus 50. */
export const PLACE_ACTIVITIES_SHOWN = 5;

function formatDay(iso: string | null): string | null {
    if (!iso) {
        return null;
    }
    const date = new Date(iso);
    return Number.isNaN(date.getTime())
        ? null
        : date.toLocaleDateString("fr-FR", { weekday: "short", day: "numeric", month: "short" });
}

/**
 * Activités à venir d'un lieu permanent (ADR-0011, § 7.2.10) : celles du lieu et des lieux d'événement reliés par
 * un administrateur. Chargées à l'ouverture de la fiche du lieu ; un clic ouvre l'aperçu de l'activité, comme un
 * marqueur. Rien n'est affiché si la requête échoue : la fiche du lieu reste utilisable.
 */
function PlaceActivities({ placeId, onSelectActivity }: {
    placeId: number;
    onSelectActivity?: (activity: Activity) => void;
}) {
    const [activities, setActivities] = useState<Activity[] | null>(null);

    useEffect(() => {
        const controller = new AbortController();
        fetch(`/api/v1/activities/at-place/${placeId}`, { signal: controller.signal })
            .then(async (response) => {
                if (response.ok) {
                    setActivities((await response.json()) as Activity[]);
                }
            })
            .catch(() => {
                // Annulé ou serveur injoignable : la fiche du lieu reste affichée sans activités.
            });
        return () => controller.abort();
    }, [placeId]);

    if (activities === null) {
        return null;
    }
    if (activities.length === 0) {
        return <p className="place-activities-empty">Aucune activité à venir connue ici.</p>;
    }
    const shown = activities.slice(0, PLACE_ACTIVITIES_SHOWN);
    return (
        <div className="place-activities">
            <strong>À venir</strong>
            <ul>
                {shown.map((activity) => {
                    const day = formatDay(activity.startDate);
                    return (
                        <li key={activity.id}>
                            {onSelectActivity ? (
                                <button className="place-activity-link" onClick={() => onSelectActivity(activity)} type="button">
                                    {activity.title}
                                </button>
                            ) : (
                                activity.title
                            )}
                            {day && <span className="place-activity-date"> · {day}</span>}
                        </li>
                    );
                })}
            </ul>
            {activities.length > shown.length && (
                <p className="place-activities-more">
                    et {activities.length - shown.length} autre{activities.length - shown.length > 1 ? "s" : ""}
                </p>
            )}
        </div>
    );
}

export default PlaceActivities;
