import { Pressable, StyleSheet, View } from 'react-native';

import type { Activity } from '@/api';
import { RemoteImage } from '@/components/remote-image';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { formatDateRange } from '@/features/activity/format';
import { withApproximateMention } from '@/features/activity/location';
import { LikeButton } from '@/features/favorites';
import { useTheme } from '@/hooks/use-theme';

/**
 * Carte d'une activité (onglet « Liste », « Mes favoris ») : photo éventuelle, catégorie, date, titre,
 * commune, distance et cœur « J'aime ».
 */
export function ActivityCard({
  activity,
  distance,
  onPress,
}: {
  activity: Activity;
  /** Distance déjà mise en forme (« 2,4 km »), si le centre de recherche est connu. */
  distance?: string | null;
  onPress: () => void;
}) {
  const theme = useTheme();
  const date = formatDateRange(activity.startDate, activity.endDate);
  const place = withApproximateMention(activity.city, activity);
  const meta = [place, distance].filter(Boolean).join(' · ');

  return (
    // Le cœur est un frère de la zone cliquable, pas un enfant : sinon les lecteurs d'écran, qui lisent la
    // carte d'un bloc, ne pourraient pas l'atteindre.
    <View style={styles.wrapper}>
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
        <RemoteImage url={activity.mainImageUrl} style={styles.photo} />
        <View style={[styles.accent, { backgroundColor: theme.primarySoft }]}>
          <ThemedText type="eyebrow" themeColor="primary" numberOfLines={1} style={styles.category}>
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
      <View style={styles.like} pointerEvents="box-none">
        <LikeButton activityId={activity.id} title={activity.title} floating />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    position: 'relative',
  },
  like: {
    position: 'absolute',
    top: Spacing.two,
    right: Spacing.two,
  },
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
  photo: {
    height: 150,
  },
  accent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingLeft: Spacing.three,
    // Place laissée au cœur posé en haut à droite (quand il n'y a pas de photo).
    paddingRight: 56,
    minHeight: 56,
  },
  category: {
    flex: 1,
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
