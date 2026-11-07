/**
 * Rôles Neiri (décision du 02/11/2026), distincts du type de compte (offre Gratuit, Premium ou Pro) :
 * - `USER` : utilisateur ;
 * - `LOCALLIFE` : équipe Neiri, accès à la modération de l'administration ;
 * - `ADMIN` : administrateur, tous les droits.
 * Le serveur reste seul juge : ces aides ne servent qu'à l'affichage.
 */
export type Role = "USER" | "LOCALLIFE" | "ADMIN";

export const ROLE_LABELS: Record<Role, string> = {
    USER: "Utilisateur",
    LOCALLIFE: "Neiri",
    ADMIN: "Administrateur",
};

/** Libellé d'un rôle reçu du serveur, y compris une valeur inattendue (affichée telle quelle). */
export function roleLabel(role: string | null | undefined): string {
    return role && role in ROLE_LABELS ? ROLE_LABELS[role as Role] : role ?? "";
}

/** Accès à l'administration : modération pour l'équipe Neiri, tout pour un administrateur. */
export function canModerate(role: string | null | undefined): boolean {
    return role === "ADMIN" || role === "LOCALLIFE";
}
