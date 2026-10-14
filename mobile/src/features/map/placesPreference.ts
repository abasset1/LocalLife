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

/** Mémorise le choix ; un échec d'écriture est sans conséquence (le choix vaut pour la session). */
export async function saveShowPlaces(shown: boolean): Promise<void> {
  try {
    await AsyncStorage.setItem(SHOW_PLACES_KEY, String(shown));
  } catch {
    // Stockage indisponible : le choix n'est pas mémorisé.
  }
}
