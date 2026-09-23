/**
 * Erreurs du client API LocalLife (LL-MOB-0003).
 *
 * Toutes héritent de `ApiError` ; l'interface utilisateur peut donc soit
 * les traiter globalement (`instanceof ApiError`), soit distinguer :
 * - `NetworkError` : le serveur n'a pas été joint (hors ligne, DNS, refus
 *   de connexion…) ;
 * - `TimeoutError` : pas de réponse dans le délai imparti ;
 * - `RequestAbortedError` : requête annulée par l'appelant (ex. l'utilisateur
 *   a déplacé la carte avant la fin du chargement) — à ignorer en général ;
 * - `HttpError` : le serveur a répondu avec un statut d'erreur ;
 * - `ResponseFormatError` : réponse 2xx dont le corps n'est pas du JSON valide.
 */
export class ApiError extends Error {
  constructor(message: string) {
    super(message);
    this.name = new.target.name;
  }
}

export class NetworkError extends ApiError {
  constructor(message = 'Impossible de joindre le serveur. Vérifiez votre connexion.') {
    super(message);
  }
}

export class TimeoutError extends ApiError {
  constructor(message = 'Le serveur met trop de temps à répondre.') {
    super(message);
  }
}

export class RequestAbortedError extends ApiError {
  constructor(message = 'Requête annulée.') {
    super(message);
  }
}

export class ResponseFormatError extends ApiError {
  constructor(message = 'Réponse inattendue du serveur.') {
    super(message);
  }
}

/** Corps d'erreur renvoyé par le backend (`ErrorResponse`), quand présent. */
export interface ApiErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
}

export class HttpError extends ApiError {
  readonly status: number;
  readonly body: ApiErrorBody | null;

  constructor(status: number, message: string, body: ApiErrorBody | null = null) {
    super(message);
    this.status = status;
    this.body = body;
  }

  /** JWT manquant, invalide ou expiré (ou identifiants refusés au login). */
  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  /** Erreur de validation côté serveur (paramètres ou formulaire). */
  get isBadRequest(): boolean {
    return this.status === 400;
  }

  get isServerError(): boolean {
    return this.status >= 500;
  }
}

/** Message de repli lorsque le backend ne fournit pas de `message`. */
export function defaultHttpMessage(status: number): string {
  switch (status) {
    case 400:
      return 'Requête invalide.';
    case 401:
      return 'Authentification requise ou session expirée.';
    case 403:
      return 'Accès refusé.';
    case 404:
      return 'Ressource introuvable.';
    case 503:
      return 'Service momentanément indisponible.';
    default:
      return status >= 500
        ? 'Erreur du serveur. Réessayez plus tard.'
        : `Erreur inattendue (${status}).`;
  }
}
