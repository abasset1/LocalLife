import { Alert, Platform } from 'react-native';

/**
 * Demande confirmation avant une action irréversible. `Alert.alert` n'affiche pas de boutons sur le
 * web (`react-native-web`) : `window.confirm` y prend le relais.
 */
export function confirmDestructive(title: string, message: string, confirmLabel: string): Promise<boolean> {
  if (Platform.OS === 'web') {
    return Promise.resolve(window.confirm(`${title}\n\n${message}`));
  }
  return new Promise((resolve) => {
    Alert.alert(title, message, [
      { text: 'Annuler', style: 'cancel', onPress: () => resolve(false) },
      { text: confirmLabel, style: 'destructive', onPress: () => resolve(true) },
    ]);
  });
}

/** Message d'information (une fois l'action faite). */
export function inform(title: string, message: string): void {
  if (Platform.OS === 'web') {
    window.alert(`${title}\n\n${message}`);
    return;
  }
  Alert.alert(title, message);
}
