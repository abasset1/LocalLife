import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useActiveCategories } from '@/features/contribution/useActiveCategories';
import { ChipGroup } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

import { clearCategories, toggleCategory, useExplorer } from './explorerStore';

/**
 * Choix des catégories de l'explorateur (FRONT-REV-02, point B) : une ou plusieurs catégories du
 * référentiel, cochées ou décochées d'un appui, et « Toutes les catégories » pour retirer le filtre.
 * Le choix est partagé par la carte et la liste (`explorerStore`).
 */
export function CategoryPanel() {
  const theme = useTheme();
  const { categoryCodes } = useExplorer();
  const { categories, error } = useActiveCategories();

  if (error) {
    return (
      <ThemedText type="small" accessibilityRole="alert" style={{ color: theme.danger }}>
        Catégories indisponibles : {error}
      </ThemedText>
    );
  }
  if (categories.length === 0) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator size="small" color={theme.primary} />
        <ThemedText type="small">Chargement des catégories…</ThemedText>
      </View>
    );
  }
  return (
    <View style={styles.wrapper}>
      <ChipGroup
        label="Catégories (une ou plusieurs)"
        options={categories.map((category) => ({ value: category.code, label: category.label }))}
        selected={categoryCodes}
        onToggle={toggleCategory}
      />
      {categoryCodes.length > 0 && (
        <Button label="Toutes les catégories" variant="ghost" onPress={clearCategories} />
      )}
    </View>
  );
}

/** Libellé du bouton qui ouvre le choix : « Catégories » ou « Catégories (2) ». */
export function categoriesButtonLabel(count: number): string {
  return count === 0 ? 'Catégories' : `Catégories (${count})`;
}

const styles = StyleSheet.create({
  wrapper: {
    gap: Spacing.two,
  },
  loading: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
});
