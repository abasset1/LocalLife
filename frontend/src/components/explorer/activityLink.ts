import type { Activity } from "./types";

/**
 * Lien direct vers la fiche d'une activité : `/?activity=<id>` (e-mail d'alerte, lien copié). L'Explorer ouvre la
 * fiche à l'arrivée ; `lat`, `lng` et `zoom`, s'ils sont dans le lien, placent la carte sur l'activité
 * (`mapUrlView.ts`).
 */
export const ACTIVITY_LINK_PARAM = "activity";

/** Identifiant de l'activité demandée par l'adresse ; `null` si absent ou invalide. */
export function readActivityLinkId(search: string): number | null {
    const raw = new URLSearchParams(search).get(ACTIVITY_LINK_PARAM);
    if (raw === null || !/^\d+$/.test(raw.trim())) {
        return null;
    }
    const id = Number(raw.trim());
    return Number.isSafeInteger(id) && id > 0 ? id : null;
}

/**
 * Activité à afficher dans la fiche, d'après la réponse de `GET /api/v1/activities/{id}` : fiche détaillée
 * (`startDate`, `media`) ou résumé (`date`, `mainImageUrl`) selon l'offre de l'appelant. `null` si la réponse
 * n'est pas une activité.
 */
export function activityFromLinkResponse(body: unknown): Activity | null {
    if (!body || typeof body !== "object") {
        return null;
    }
    const value = body as Record<string, unknown>;
    if (typeof value.id !== "number" || typeof value.title !== "string") {
        return null;
    }
    const text = (field: unknown): string | null => (typeof field === "string" ? field : null);
    const media = Array.isArray(value.media) ? value.media : [];
    const firstMedia = media[0] as { url?: unknown } | undefined;
    return {
        id: value.id,
        title: value.title,
        category: text(value.category) ?? "",
        categoryCodes: Array.isArray(value.categoryCodes) ? (value.categoryCodes as string[]) : [],
        latitude: typeof value.latitude === "number" ? value.latitude : 0,
        longitude: typeof value.longitude === "number" ? value.longitude : 0,
        startDate: text(value.startDate) ?? text(value.date) ?? "",
        status: text(value.status) ?? "PUBLISHED",
        publishedAt: text(value.publishedAt),
        sourceName: "",
        address: text(value.address),
        city: text(value.city),
        createdBy: typeof value.createdBy === "number" ? value.createdBy : null,
        createdByUsername: null,
        likedByCurrentUser: false,
        mainImageUrl: text(value.mainImageUrl) ?? text(firstMedia?.url),
        coordinatePrecision: (text(value.coordinatePrecision) as Activity["coordinatePrecision"]) ?? null,
        hostPlace: (value.hostPlace as Activity["hostPlace"]) ?? null,
        priceType: (text(value.priceType) as Activity["priceType"]) ?? null,
    };
}

/**
 * Retire `activity` de l'adresse une fois la fiche refermée, sans nouvelle entrée d'historique : un rechargement ne
 * la rouvre pas, et la position de la carte reste dans l'adresse.
 */
export function clearActivityLink(): void {
    const url = new URL(window.location.href);
    if (!url.searchParams.has(ACTIVITY_LINK_PARAM)) {
        return;
    }
    url.searchParams.delete(ACTIVITY_LINK_PARAM);
    window.history.replaceState(window.history.state, "", url);
}
