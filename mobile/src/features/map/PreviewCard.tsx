import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';

import type { Activity, FoodTruck } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type Selection =
  | { kind: 'activity'; activity: Activity }
  | { kind: 'foodTruck'; foodTruck: FoodTruck };

function formatDate(iso: string | null): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

/** Consultation rapide d'un élément touché sur la carte. */
export function PreviewCard({ selection, onClose }: { selection: Selection; onClose: () => void }) {
  const theme = useTheme();
  const router = useRouter();

  const isActivity = selection.kind === 'activity';
  const title = isActivity ? selection.activity.title : selection.foodTruck.name;
  const category = isActivity ? selection.activity.category : selection.foodTruck.category;
  const place = isActivity ? selection.activity.city : null;
  const date = isActivity ? formatDate(selection.activity.startDate) : null;
  const description = isActivity ? null : selection.foodTruck.description;
  const contact = isActivity ? null : selection.foodTruck.contact;

  const meta = [isActivity ? category : `Food truck · ${category}`, place, date]
    .filter(Boolean)
    .join(' · ');

  return (
    <View style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.header}>
        <ThemedText type="smallBold" style={styles.title} numberOfLines={2}>
          {title}
        </ThemedText>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Fermer l'aperçu"
          hitSlop={12}
          onPress={onClose}
          style={styles.close}>
          <ThemedText>✕</ThemedText>
        </Pressable>
      </View>
      <ThemedText type="small" themeColor="textSecondary">
        {meta}
      </ThemedText>
      {description ? (
        <ThemedText type="small" numberOfLines={3}>
          {description}
        </ThemedText>
      ) : null}
      {contact ? (
        <ThemedText type="small" themeColor="textSecondary">
          Contact : {contact}
        </ThemedText>
      ) : null}
      {isActivity && (
        <Button
          label="Voir la fiche"
          onPress={() =>
            router.push({ pathname: '/activite/[id]', params: { id: String(selection.activity.id) } })
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two,
  },
  title: {
    flex: 1,
  },
  close: {
    minWidth: 32,
    minHeight: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
