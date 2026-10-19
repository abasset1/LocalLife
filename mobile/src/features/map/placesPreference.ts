import AsyncStorage from '@react-native-async-storage/async-storage';

/** Clé du choix « couche Lieux affichée », comme `locallife.showPlaces` sur le site web. */
export const SHOW_PLACES_KEY = 'locallife.showPlaces';

/** Choix mémorisé sur l'appareil ; `false` s'il est absent ou illisible (la carte reste utilisable). */
export async function loadShowPlaces(): Promise<boolean> {
  try {
    return (await AsyncStorage.getItem(SHOW_PLACES_KEY)) === 'true';
  } catch {
    return false;
  }
}

/** Clé du choix « couche Food trucks affichée » (ADR-0013), comme `locallife.showFoodTrucks` sur le site. */
export const SHOW_FOOD_TRUCKS_KEY = 'locallife.showFoodTrucks';

/** Couche « Food trucks » : affichée sauf si l'utilisateur l'a masquée (affichage d'avant sa bascule). */
export async function loadShowFoodTrucks(): Promise<boolean> {
  try {
    return (await AsyncStorage.getItem(SHOW_FOOD_TRUCKS_KEY)) !== 'false';
  } catch {
    return true;
  }
}

export async function saveShowFoodTrucks(shown: boolean): Promise<void> {
  try {
    await AsyncStorage.setItem(SHOW_FOOD_TRUCKS_KEY, String(shown));
  } catch {
    // Stockage indisponible : le choix n'est pas mémorisé.
  }
}

/** Mémorise le choix ; un échec d'écriture est sans conséquence (le choix vaut pour la session). */
export async function saveShowPlaces(shown: boolean): Promise<void> {
  try {
    await AsyncStorage.setItem(SHOW_PLACES_KEY, String(shown));
  } catch {
    // Stockage indisponible : le choix n'est pas mémorisé.
  }
}
