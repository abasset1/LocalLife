import type { FoodTruck } from "../explorer/types";

export const SPOT_KIND_LABELS: Record<FoodTruck["spotKind"], string> = {
    MARKET: "Marché",
    PRIVATE: "Espace privé",
    EVENT: "Événement",
    OTHER: "Emplacement",
};

/** Horaires du jour d'une présence (ADR-0013), ou `null` s'ils ne sont pas précisés. */
export function formatHours(foodTruck: Pick<FoodTruck, "startTime" | "endTime">): string | null {
    if (!foodTruck.startTime || !foodTruck.endTime) {
        return null;
    }
    const hour = (value: string) => value.slice(0, 5).replace(":", "h");
    return `${hour(foodTruck.startTime)} – ${hour(foodTruck.endTime)}`;
}

/** Une ligne lisible : type d'emplacement, nom de l'emplacement, horaires. */
export function describePresence(foodTruck: FoodTruck): string {
    return [SPOT_KIND_LABELS[foodTruck.spotKind] ?? null, foodTruck.spotName, formatHours(foodTruck)]
        .filter(Boolean).join(" · ");
}
