import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import type { Activity } from '@/api';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { formatDateRange } from '@/features/activity/format';
import { useTheme } from '@/hooks/use-theme';

import { zoneCountLabel } from './activityGroups';

/**
 * Liste des activités de la zone affichée, liée à la carte comme sur le site web (page Explorer) : en tête le
 * nombre d'activités de la zone, mis à jour à chaque déplacement ou zoom ; un appui ouvre l'aperçu de l'activité
 * et met son point en évidence sur la carte, même quand il est regroupé avec ses voisins. « Recherche autour d'un
 * lieu » ouvre la rubrique Liste (recherche par rayon).
 */
export function ZoneActivityList({
  activities,
  onSelect,
  onClose,
  onOpenFullList,
}: {
  /** Activités de la zone, déjà triées. */
  activities: readonly Activity[];
  onSelect: (activity: Activity) => void;
  onClose: () => void;
  onOpenFullList: () => void;
}) {
  const theme = useTheme();
  return (
    <View style={[styles.panel, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.header}>
        <ThemedText type="heading" style={styles.title}>
          {zoneCountLabel(activities.length)}
        </ThemedText>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Fermer la liste"
          hitSlop={8}
          onPress={onClose}
          style={styles.close}>
          <ThemedText themeColor="textSecondary">✕</ThemedText>
        </Pressable>
      </View>
      <FlatList
        data={activities}
        keyExtractor={(activity) => String(activity.id)}
        renderItem={({ item }) => {
          const when = formatDateRange(item.startDate, item.endDate);
          return (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={[item.title, when].filter(Boolean).join(', ')}
              onPress={() => onSelect(item)}
              style={({ pressed }) => [
                styles.item,
                { borderColor: theme.border },
                pressed && { backgroundColor: theme.backgroundSelected },
              ]}>
              <ThemedText type="smallBold" numberOfLines={1}>
                {item.title}
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
                {[when, item.address ?? item.city].filter(Boolean).join(' · ')}
              </ThemedText>
            </Pressable>
          );
        }}
      />
      <Pressable accessibilityRole="link" hitSlop={6} onPress={onOpenFullList} style={styles.fullList}>
        <ThemedText type="smallBold" themeColor="primary">
          Recherche autour d’un lieu ›
        </ThemedText>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    maxHeight: 360,
    marginBottom: Spacing.two,
    padding: Spacing.three,
    borderRadius: Radius.xl,
    gap: Spacing.two,
    ...Shadows.floating,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  title: {
    flex: 1,
  },
  close: {
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fullList: {
    alignSelf: 'flex-end',
    minHeight: 44,
    justifyContent: 'center',
  },
  item: {
    paddingVertical: Spacing.two,
    paddingHorizontal: Spacing.one,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderRadius: Radius.sm,
    minHeight: 48,
    justifyContent: 'center',
  },
});
