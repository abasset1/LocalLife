/**
 * Message affichable quand l'enregistrement de l'appareil échoue, selon l'erreur levée (sans dépendance à React
 * Native pour rester testable).
 */
export function pushRegistrationFailure(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error);
  // Build Android sans google-services.json : FCM, donc Expo Push, est indisponible.
  if (/FirebaseApp|Firebase|FCM|google-services/i.test(message)) {
    return "Les notifications ne sont pas configurées pour Android dans cette version de l'application (Firebase).";
  }
  return "L'appareil n'a pas pu être enregistré. Réessayez plus tard.";
}
