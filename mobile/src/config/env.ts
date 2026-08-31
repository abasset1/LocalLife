import Constants from "expo-constants";

/**
 * URL de base de l'API LocalLife, définie via app.config.ts (extra.apiUrl)
 * à partir de la variable d'environnement EXPO_PUBLIC_API_URL.
 *
 * Ce module ne fait que centraliser la lecture de la config ; la
 * construction du client API (appels, typage des réponses, gestion des
 * erreurs) est du ressort de LL-MOB-0003.
 */
export const apiUrl: string = Constants.expoConfig?.extra?.apiUrl;

if (!apiUrl) {
  // Ne devrait pas arriver : app.config.ts fournit toujours une valeur de
  // repli en développement. On échoue tôt et bruyamment plutôt que de
  // laisser l'app démarrer avec une URL API indéfinie.
  throw new Error(
    "EXPO_PUBLIC_API_URL introuvable : vérifiez app.config.ts et votre fichier .env (voir .env.example).",
  );
}
