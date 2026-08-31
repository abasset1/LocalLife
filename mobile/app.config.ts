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
    icon: "./assets/expo.icon",
  },
  android: {
    adaptiveIcon: {
      backgroundColor: "#E6F4FE",
      foregroundImage: "./assets/images/android-icon-foreground.png",
      backgroundImage: "./assets/images/android-icon-background.png",
      monochromeImage: "./assets/images/android-icon-monochrome.png",
    },
    predictiveBackGestureEnabled: false,
  },
  web: {
    output: "static",
    favicon: "./assets/images/favicon.png",
  },
  plugins: [
    "expo-router",
    [
      "expo-splash-screen",
      {
        backgroundColor: "#208AEF",
        image: "./assets/images/splash-icon.png",
        imageWidth: 76,
      },
    ],
  ],
  experiments: {
    typedRoutes: true,
    reactCompiler: true,
  },
  extra: {
    apiUrl: process.env.EXPO_PUBLIC_API_URL ?? DEV_DEFAULT_API_URL,
  },
});
