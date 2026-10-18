import { FlatList, Pressable, StyleSheet, View } from 'react-native';

import type { Activity } from '@/api';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { formatDateRange } from '@/features/activity/format';
import { useTheme } from '@/hooks/use-theme';

/**
 * Activités d'un groupe de marqueurs au même endroit (même adresse, ou position approximative au centre
 * d'une commune) : zoomer ne les séparerait jamais. Comme le site web, qui les écarte en éventail, la
 * carte les liste ; un appui ouvre l'aperçu de l'activité, comme son marqueur.
 */
export function ActivityGroupPanel({
  activities,
  onSelect,
  onClose,
}: {
  activities: readonly Activity[];
  onSelect: (activity: Activity) => void;
  onClose: () => void;
}) {
  const theme = useTheme();
  return (
    <View style={[styles.panel, { backgroundColor: theme.backgroundElement }]}>
      <View style={styles.header}>
        <ThemedText type="heading" style={styles.title}>
          {activities.length} activités à cet endroit
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
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    maxHeight: 320,
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
  item: {
    paddingVertical: Spacing.two,
    paddingHorizontal: Spacing.one,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderRadius: Radius.sm,
    minHeight: 48,
    justifyContent: 'center',
  },
});
