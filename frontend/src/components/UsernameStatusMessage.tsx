import { USERNAME_RULES } from "../auth/useUsernameAvailability";
import type { UsernameStatus } from "../auth/useUsernameAvailability";

interface UsernameStatusMessageProps {
    id: string;
    status: UsernameStatus;
}

/** Règles du nom d'utilisateur, puis le résultat de la vérification en direct (LS-000). */
function UsernameStatusMessage({ id, status }: UsernameStatusMessageProps) {
    return (
        <span className="username-status" id={id} role="status">
            {status.kind === "idle" && <span className="username-status-hint">{USERNAME_RULES}</span>}
            {status.kind === "checking" && <span className="username-status-hint">Vérification…</span>}
            {status.kind === "available" && <span className="username-status-ok">✓ Ce nom est disponible.</span>}
            {status.kind === "unavailable" && <span className="username-status-error">{status.message}</span>}
        </span>
    );
}

export default UsernameStatusMessage;
