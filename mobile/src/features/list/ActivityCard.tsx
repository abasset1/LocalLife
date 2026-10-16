import { Pressable, StyleSheet, View } from 'react-native';

import type { Activity } from '@/api';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { formatDateRange } from '@/features/activity/format';
import { withApproximateMention } from '@/features/activity/location';
import { useTheme } from '@/hooks/use-theme';

/** Carte d'une activité dans l'onglet « Liste » : date, titre, catégorie, commune et distance. */
export function ActivityCard({
  activity,
  distance,
  onPress,
}: {
  activity: Activity;
  /** Distance déjà mise en forme (« 2,4 km »), si le centre de recherche est connu. */
  distance: string | null;
  onPress: () => void;
}) {
  const theme = useTheme();
  const date = formatDateRange(activity.startDate, activity.endDate);
  const place = withApproximateMention(activity.city, activity);
  const meta = [place, distance].filter(Boolean).join(' · ');

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={[activity.title, activity.category, date, meta].filter(Boolean).join(', ')}
      accessibilityHint="Ouvre la fiche de l'activité"
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        pressed && styles.pressed,
      ]}>
      <View style={[styles.accent, { backgroundColor: theme.primarySoft }]}>
        <ThemedText type="eyebrow" themeColor="primary" numberOfLines={1}>
          {activity.category}
        </ThemedText>
      </View>
      <View style={styles.body}>
        {date ? (
          <ThemedText type="eyebrow" numberOfLines={1}>
            {date}
          </ThemedText>
        ) : null}
        <ThemedText type="heading" numberOfLines={2}>
          {activity.title}
        </ThemedText>
        {activity.description ? (
          <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
            {activity.description}
          </ThemedText>
        ) : null}
        <View style={styles.footer}>
          <ThemedText type="small" themeColor="textSecondary" numberOfLines={1} style={styles.meta}>
            {meta ? `📍 ${meta}` : ''}
          </ThemedText>
          <ThemedText type="smallBold" themeColor="primary">
            Voir →
          </ThemedText>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: Radius.lg,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
    ...Shadows.card,
  },
  pressed: {
    opacity: 0.9,
    transform: [{ scale: 0.99 }],
  },
  accent: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  body: {
    padding: Spacing.three,
    gap: Spacing.one + Spacing.half,
  },
  footer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    marginTop: Spacing.one,
  },
  meta: {
    flex: 1,
  },
});
