import type { ConfigContext, ExpoConfig } from "expo/config";

/**
 * Configuration Expo de l'application mobile LocalLife.
 *
 * L'URL de l'API est fournie via la variable d'environnement
 * EXPO_PUBLIC_API_URL (voir .env.example). Elle est lue au build/démarrage
 * et exposée à l'application via `expo-constants` (extra.apiUrl), pour
 * rester cohérent avec la contrainte "aucun secret dans le dépôt" et
 * "URL de l'API configurable par environnement" de LL-MOB-0001.
 *
 * Aucune valeur par défaut de production n'est codée en dur ici : en
 * l'absence de EXPO_PUBLIC_API_URL, on retombe sur une URL de
 * développement local explicite.
 */
const DEV_DEFAULT_API_URL = "http://localhost:8080";

/**
 * Clé Google Maps (Android uniquement, LL-MOB-0005). Fournie par
 * l'environnement, jamais commitée. Inutile avec Expo Go ; requise pour
 * un build autonome (APK/AAB) afin que la carte s'affiche sur Android.
 */
const googleMapsApiKey = process.env.GOOGLE_MAPS_API_KEY;

/**
 * Identifiant du projet EAS (Expo Application Services), requis pour
 * obtenir un jeton de notification push Expo. Fourni par l'environnement :
 * sans lui, l'application fonctionne mais les alertes par notification sont
 * indisponibles (message affiché à l'utilisateur).
 */
const easProjectId = process.env.EAS_PROJECT_ID;

/**
 * Connexion Google (identifiants OAuth publics, pas des secrets). Le client
 * « web » est celui du site et du backend (`GOOGLE_CLIENT_ID`). Sur iOS, il
 * faut aussi le client iOS : son schéma d'URL (identifiant inversé) est
 * déclaré par le plugin. Sans ces valeurs, le bouton Google est masqué.
 */
const googleWebClientId = process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID;
const googleIosClientId = process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID;
/** `123-abc.apps.googleusercontent.com` → `com.googleusercontent.apps.123-abc`. */
const googleIosUrlScheme = googleIosClientId
  ? `com.googleusercontent.apps.${googleIosClientId.replace(/\.apps\.googleusercontent\.com$/, "")}`
  : undefined;

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: "LocalLife",
  slug: "locallife-mobile",
  version: "1.0.0",
  orientation: "portrait",
  icon: "./assets/images/icon.png",
  scheme: "locallife",
  userInterfaceStyle: "automatic",
  ios: {
    // Identifiant de l'application iOS (définitif une fois publiée sur l'App Store).
    bundleIdentifier: "com.locallife.mobile",
    icon: "./assets/images/icon.png",
  },
  android: {
    // Identifiant de l'application Android (définitif une fois publiée sur le Play Store).
    package: "com.locallife.mobile",
    adaptiveIcon: {
      backgroundColor: "#173F35",
      foregroundImage: "./assets/images/android-icon-foreground.png",
      backgroundImage: "./assets/images/android-icon-background.png",
      monochromeImage: "./assets/images/android-icon-monochrome.png",
    },
    predictiveBackGestureEnabled: false,
    ...(googleMapsApiKey ? { config: { googleMaps: { apiKey: googleMapsApiKey } } } : {}),
  },
  web: {
    output: "static",
    favicon: "./assets/images/favicon.png",
  },
  plugins: [
    "expo-router",
    ...(googleIosUrlScheme
      ? [["@react-native-google-signin/google-signin", { iosUrlScheme: googleIosUrlScheme }] as [string, object]]
      : []),
    [
      // Alertes en notification push (voir src/notifications/push.ts).
      "expo-notifications",
      {
        color: "#173F35",
      },
    ],
    [
      // LL-MOB-0006 : position au premier plan uniquement, demandée à la
      // demande de l'utilisateur. Le message explique clairement l'usage.
      "expo-location",
      {
        locationWhenInUsePermission:
          "LocalLife utilise votre position pour centrer la carte sur vous et afficher les activités proches. Elle n'est pas enregistrée.",
        isAndroidBackgroundLocationEnabled: false,
        isIosBackgroundLocationEnabled: false,
      },
    ],
    [
      "expo-splash-screen",
      {
        // Vert forêt de la charte ; logo LocalLife (« L » et point corail).
        backgroundColor: "#173F35",
        image: "./assets/images/splash-icon.png",
        imageWidth: 120,
      },
    ],
  ],
  experiments: {
    typedRoutes: true,
    reactCompiler: true,
  },
  extra: {
    apiUrl: process.env.EXPO_PUBLIC_API_URL ?? DEV_DEFAULT_API_URL,
    googleWebClientId,
    googleIosClientId,
    ...(easProjectId ? { eas: { projectId: easProjectId } } : {}),
  },
});
