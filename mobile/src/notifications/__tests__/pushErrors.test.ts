import { pushRegistrationFailure } from '../pushErrors';

describe("échec de l'enregistrement de l'appareil", () => {
  it('signale un build Android sans Firebase', () => {
    const error = new Error(
      "Default FirebaseApp is not initialized in this process com.neiri.app. Make sure to call FirebaseApp.initializeApp(Context) first.",
    );
    expect(pushRegistrationFailure(error)).toContain('Firebase');
  });

  it('reste générique pour les autres erreurs', () => {
    expect(pushRegistrationFailure(new Error('Network request failed'))).toBe(
      "L'appareil n'a pas pu être enregistré. Réessayez plus tard.",
    );
    expect(pushRegistrationFailure('oups')).toContain('Réessayez');
  });
});
