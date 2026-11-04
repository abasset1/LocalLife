import { request } from './http';

/** Type de message (`kind` backend) : une idée ou un problème. */
export type FeedbackKind = 'SUGGESTION' | 'BUG';

/**
 * Envoie une suggestion ou un signalement d'anomalie (`POST /api/v1/feedback`, connexion exigée), avec l'écran
 * d'où il est envoyé. Les administrateurs sont prévenus par e-mail ; au plus 10 messages par heure et par compte.
 */
export function submitFeedback(kind: FeedbackKind, message: string, pageUrl?: string): Promise<{ id: number }> {
  return request<{ id: number }>('/api/v1/feedback', {
    method: 'POST',
    body: { kind, message, pageUrl },
    authenticated: true,
  });
}
