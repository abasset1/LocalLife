import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { Activity } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Spacing } from '@/constants/theme';
import { useSearchPreferences } from '@/features/map/useSearchPreferences';
import { LOCATION_MESSAGES, useUserLocation } from '@/features/map/useUserLocation';
import { RADIUS_OPTIONS_KM } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

import { ActivityCard } from './ActivityCard';
import { FilterChips } from './FilterChips';
import { PERIOD_OPTIONS, distanceKm, formatDistance, matchesQuery } from './filters';
import { usePeriod } from './periodStore';
import { useActivityList } from './useActivityList';

/** Centre par défaut, comme la carte : Marseille, périmètre du MVP. */
const DEFAULT_CENTER = { latitude: 43.2965, longitude: 5.3698, label: 'Marseille' };
const DEFAULT_RADIUS_KM = 20;
const NO_CATEGORIES: string[] = [];

/**
 * Onglet « Liste » : les activités publiées autour de l'utilisateur (après « Autour de moi »), de sa
 * ville par défaut (paramètres de recherche) ou de Marseille, triées par distance. Filtres : période,
 * rayon, recherche texte ; les catégories par défaut des paramètres s'appliquent comme sur la carte.
 * Un appui ouvre la fiche de l'activité.
 */
export default function ActivityListScreen() {
  const theme = useTheme();
  const router = useRouter();
  const preferences = useSearchPreferences();
  const location = useUserLocation();

  const [userPosition, setUserPosition] = useState<{ latitude: number; longitude: number } | null>(null);
  const [period, setPeriod] = usePeriod();
  const [radiusOverride, setRadiusOverride] = useState<number | null>(null);
  const [query, setQuery] = useState('');
  const [showAllCategories, setShowAllCategories] = useState(false);

  const center = userPosition
    ? { ...userPosition, label: 'vous' }
    : preferences && preferences.defaultLatitude !== null && preferences.defaultLongitude !== null
      ? {
          latitude: preferences.defaultLatitude,
          longitude: preferences.defaultLongitude,
          label: preferences.defaultCity ?? 'votre ville',
        }
      : DEFAULT_CENTER;
  const radiusKm = radiusOverride ?? preferences?.defaultRadiusKm ?? DEFAULT_RADIUS_KM;
  const preferredCategories = preferences?.defaultCategoryCodes ?? NO_CATEGORIES;
  const categoryCodes = showAllCategories ? NO_CATEGORIES : preferredCategories;

  const { state, refreshing, refresh } = useActivityList({
    latitude: center.latitude,
    longitude: center.longitude,
    radiusKm,
    period,
    categoryCodes,
  });

  const activities = useMemo(
    () => (state.status === 'success' ? state.activities.filter((a) => matchesQuery(a, query)) : []),
    [state, query],
  );

  async function aroundMe() {
    const position = await location.locate();
    if (position) setUserPosition({ latitude: position.latitude, longitude: position.longitude });
  }

  function open(activity: Activity) {
    router.push({
      pathname: '/activite/[id]',
      params: { id: String(activity.id), ...(activity.sourceName ? { source: activity.sourceName } : {}) },
    });
  }

  const locationProblem =
    location.status === 'denied' || location.status === 'unavailable' || location.status === 'error'
      ? location.status
      : null;

  const header = (
    <View style={styles.header}>
      <View style={styles.titleBlock}>
        <ThemedText type="eyebrow">À découvrir</ThemedText>
        <ThemedText type="title" accessibilityRole="header">
          Près de {center.label}
        </ThemedText>
      </View>

      <View style={styles.padded}>
        <View style={[styles.search, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <ThemedText importantForAccessibility="no">🔍</ThemedText>
          <TextInput
            accessibilityLabel="Rechercher une activité"
            placeholder="Concert, marché, commune…"
            placeholderTextColor={theme.textSecondary}
            selectionColor={theme.primary}
            value={query}
            onChangeText={setQuery}
            returnKeyType="search"
            autoCorrect={false}
            style={[styles.searchInput, { color: theme.text }]}
          />
          {query ? (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Effacer la recherche"
              hitSlop={10}
              onPress={() => setQuery('')}>
              <ThemedText themeColor="textSecondary">✕</ThemedText>
            </Pressable>
          ) : null}
        </View>
      </View>

      <FilterChips label="Période" options={PERIOD_OPTIONS} value={period} onChange={setPeriod} />
      <FilterChips
        label="Rayon de recherche"
        options={RADIUS_OPTIONS_KM.map((km) => ({ value: km as number, label: `${km} km` }))}
        value={radiusKm}
        onChange={setRadiusOverride}
      />

      <View style={[styles.padded, styles.tools]}>
        <Button
          label={userPosition ? 'Actualiser ma position' : 'Autour de moi'}
          variant="secondary"
          icon={{ ios: 'location.fill', android: 'my_location', web: 'my_location' }}
          loading={location.status === 'locating'}
          onPress={() => void aroundMe()}
        />
        {preferredCategories.length > 0 && (
          <Button
            label={showAllCategories ? 'Mes catégories seulement' : 'Toutes les catégories'}
            variant="ghost"
            onPress={() => setShowAllCategories((all) => !all)}
          />
        )}
      </View>

      {locationProblem && (
        <View style={styles.padded}>
          <Notice tone="warning">{LOCATION_MESSAGES[locationProblem]}</Notice>
        </View>
      )}

      {state.status === 'success' && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.padded} accessibilityLiveRegion="polite">
          {activities.length === 0
            ? 'Aucun résultat'
            : `${activities.length} activité${activities.length > 1 ? 's' : ''} à moins de ${radiusKm} km`}
          {categoryCodes.length > 0 ? ' · filtrées sur vos catégories' : ''}
        </ThemedText>
      )}
    </View>
  );

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.container} edges={['top', 'left', 'right']}>
        <FlatList
          data={activities}
          keyExtractor={(activity) => String(activity.id)}
          ListHeaderComponent={header}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          contentContainerStyle={styles.content}
          ItemSeparatorComponent={Separator}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={theme.primary} colors={[theme.primary]} />
          }
          renderItem={({ item }) => (
            <View style={styles.padded}>
              <ActivityCard
                activity={item}
                distance={formatDistance(distanceKm(center, item))}
                onPress={() => open(item)}
              />
            </View>
          )}
          ListEmptyComponent={
            <View style={styles.padded}>
              {state.status === 'loading' && (
                <View style={styles.loading} accessibilityLiveRegion="polite">
                  <ActivityIndicator size="large" color={theme.primary} accessibilityLabel="Chargement des activités" />
                </View>
              )}
              {state.status === 'error' && (
                <View style={styles.errorBlock}>
                  <Notice tone="danger">Erreur : {state.message}</Notice>
                  <Button label="Réessayer" onPress={refresh} />
                </View>
              )}
              {state.status === 'success' && (
                <Card style={styles.empty}>
                  <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
                    🧭
                  </ThemedText>
                  <ThemedText type="heading" style={styles.centerText}>
                    Rien trouvé ici
                  </ThemedText>
                  <ThemedText themeColor="textSecondary" style={styles.centerText}>
                    {query
                      ? 'Aucune activité ne correspond à votre recherche.'
                      : 'Élargissez le rayon ou la période pour voir plus d’activités.'}
                  </ThemedText>
                </Card>
              )}
            </View>
          }
        />
      </SafeAreaView>
    </ThemedView>
  );
}

function Separator() {
  return <View style={styles.separator} />;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    paddingBottom: Spacing.five,
  },
  header: {
    gap: Spacing.three,
    paddingTop: Spacing.four,
    paddingBottom: Spacing.three,
  },
  titleBlock: {
    gap: Spacing.one,
    paddingHorizontal: Spacing.three,
  },
  padded: {
    paddingHorizontal: Spacing.three,
  },
  search: {
    minHeight: 50,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.three,
    borderWidth: 1.5,
    borderRadius: Radius.pill,
  },
  searchInput: {
    flex: 1,
    minHeight: 48,
    fontSize: 16,
  },
  tools: {
    gap: Spacing.one,
  },
  separator: {
    height: Spacing.three,
  },
  loading: {
    paddingVertical: Spacing.six,
  },
  errorBlock: {
    gap: Spacing.three,
  },
  empty: {
    padding: Spacing.four,
    gap: Spacing.two,
  },
  emptyIcon: {
    fontSize: 40,
    lineHeight: 48,
    textAlign: 'center',
  },
  centerText: {
    textAlign: 'center',
  },
});
