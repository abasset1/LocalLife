import { useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { Activity, FoodTruck } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Spacing } from '@/constants/theme';
import { CategoryPanel, categoriesButtonLabel } from '@/features/explorer/CategoryPanel';
import { CitySearch } from '@/features/explorer/CitySearch';
import { applyPreferences, clearCategories, setPlace, setRadius, useExplorer } from '@/features/explorer/explorerStore';
import { useSearchPreferences } from '@/features/map/useSearchPreferences';
import { LOCATION_MESSAGES, useUserLocation } from '@/features/map/useUserLocation';
import { MenuButton } from '@/features/navigation/AppMenu';
import { RADIUS_OPTIONS_KM } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

import { ActivityCard } from './ActivityCard';
import { FilterChips } from './FilterChips';
import { PERIOD_OPTIONS, distanceKm, formatDistance, matchesQuery } from './filters';
import { FoodTruckCard } from './FoodTruckCard';
import { foodTrucksNear } from './foodTruckList';
import { usePeriod } from './periodStore';
import { useActivityList } from './useActivityList';
import { useFoodTruckList } from './useFoodTruckList';

/** Rayon de la liste tant qu'aucun rayon n'est choisi (la carte cherche alors dans la zone visible). */
const DEFAULT_RADIUS_KM = 20;

type ListTab = 'activities' | 'foodTrucks';

/** Contenu de la liste : les food trucks (ADR-0013) ont leur onglet, distinct des activités. */
const TAB_OPTIONS: readonly { value: ListTab; label: string }[] = [
  { value: 'activities', label: 'Activités' },
  { value: 'foodTrucks', label: '🚚 Food trucks' },
];

type ListItem = { kind: 'activity'; activity: Activity } | { kind: 'foodTruck'; foodTruck: FoodTruck; km: number };

/**
 * Vue « Liste » : les activités publiées autour du lieu de recherche (ville choisie, « Autour de moi »
 * ou ville des paramètres), triées par distance. Lieu, catégories et rayon sont partagés avec la carte
 * (`explorerStore`, FRONT-REV-02) ; la période aussi (`periodStore`). Sans lieu choisi, la liste invite
 * à en choisir un : aucune ville n'est supposée. Filtre texte local en plus. Un appui ouvre la fiche.
 *
 * Onglet « Food trucks » (ADR-0013) : les food trucks présents aujourd'hui dans le même rayon, une carte par
 * présence, triés par distance. Période et catégories ne s'y appliquent pas (catégories d'activités ; une
 * présence est celle du jour).
 */
export default function ActivityListScreen() {
  const theme = useTheme();
  const router = useRouter();
  const preferences = useSearchPreferences();
  useEffect(() => {
    applyPreferences(preferences);
  }, [preferences]);
  const location = useUserLocation();

  const { place, categoryCodes, radiusKm: chosenRadiusKm } = useExplorer();
  const [period, setPeriod] = usePeriod();
  const [query, setQuery] = useState('');
  const [categoryPanelOpen, setCategoryPanelOpen] = useState(false);
  const [tab, setTab] = useState<ListTab>('activities');
  const showFoodTrucks = tab === 'foodTrucks';

  const radiusKm = chosenRadiusKm ?? DEFAULT_RADIUS_KM;

  const { state, refreshing, refresh } = useActivityList(
    place
      ? { latitude: place.latitude, longitude: place.longitude, radiusKm, period, categoryCodes }
      : null,
  );

  const activities = useMemo(
    () => (state.status === 'success' ? state.activities.filter((a) => matchesQuery(a, query)) : []),
    [state, query],
  );

  const foodTruckList = useFoodTruckList(showFoodTrucks && place !== null);
  const foodTrucks = useMemo(
    () =>
      place && foodTruckList.state.status === 'success'
        ? foodTrucksNear(foodTruckList.state.foodTrucks, place, radiusKm, query)
        : [],
    [foodTruckList.state, place, radiusKm, query],
  );

  const items: ListItem[] = showFoodTrucks
    ? foodTrucks.map(({ foodTruck, distanceKm: km }) => ({ kind: 'foodTruck', foodTruck, km }))
    : activities.map((activity) => ({ kind: 'activity', activity }));
  // L'état affiché (chargement, erreur, vide) est celui de l'onglet ouvert.
  const listStatus = place === null ? 'idle' : showFoodTrucks ? foodTruckList.state.status : state.status;
  const listError =
    showFoodTrucks && foodTruckList.state.status === 'error'
      ? foodTruckList.state.message
      : state.status === 'error'
        ? state.message
        : '';
  const refreshList = showFoodTrucks ? foodTruckList.refresh : refresh;

  async function aroundMe() {
    const position = await location.locate();
    if (position) {
      setPlace({ label: 'votre position', latitude: position.latitude, longitude: position.longitude, source: 'position' });
    }
  }

  function openFoodTruck(foodTruck: FoodTruck) {
    router.push({ pathname: '/foodtruck/[id]', params: { id: String(foodTruck.id) } });
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
      <View style={[styles.padded, styles.topRow]}>
        <MenuButton />
        <Button
          label="Carte"
          variant="secondary"
          icon={{ ios: 'map', android: 'map', web: 'map' }}
          onPress={() => router.navigate('/')}
        />
      </View>
      <View style={[styles.padded, styles.titleBlock]}>
        <ThemedText type="eyebrow">À découvrir</ThemedText>
        <ThemedText type="title" accessibilityRole="header">
          {place ? `Près de ${place.label}` : 'Choisissez un lieu'}
        </ThemedText>
      </View>

      <View style={styles.padded}>
        <CitySearch />
      </View>

      <FilterChips label="Contenu de la liste" options={TAB_OPTIONS} value={tab} onChange={setTab} />

      <View style={styles.padded}>
        <View style={[styles.search, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <ThemedText importantForAccessibility="no">⌕</ThemedText>
          <TextInput
            accessibilityLabel={showFoodTrucks ? 'Filtrer les food trucks de la liste' : 'Filtrer les activités de la liste'}
            placeholder={showFoodTrucks ? 'Filtrer : tacos, crêpes…' : 'Filtrer : concert, marché…'}
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

      {!showFoodTrucks && (
        <FilterChips label="Période" options={PERIOD_OPTIONS} value={period} onChange={setPeriod} />
      )}
      <FilterChips
        label="Rayon de recherche"
        options={RADIUS_OPTIONS_KM.map((km) => ({ value: km as number, label: `${km} km` }))}
        value={radiusKm}
        onChange={setRadius}
      />

      <View style={[styles.padded, styles.tools]}>
        <View style={styles.toolsRow}>
          {!showFoodTrucks && (
            <View style={styles.tool}>
              <Button
                label={categoriesButtonLabel(categoryCodes.length)}
                variant={categoryCodes.length > 0 ? 'primary' : 'secondary'}
                icon={{ ios: 'square.grid.2x2', android: 'category', web: 'category' }}
                onPress={() => setCategoryPanelOpen((open) => !open)}
              />
            </View>
          )}
          <View style={styles.tool}>
            <Button
              label="Autour de moi"
              variant="secondary"
              icon={{ ios: 'location.fill', android: 'my_location', web: 'my_location' }}
              loading={location.status === 'locating'}
              onPress={() => void aroundMe()}
            />
          </View>
        </View>
        {categoryPanelOpen && !showFoodTrucks && (
          <Card style={styles.panel}>
            <CategoryPanel />
          </Card>
        )}
      </View>

      {locationProblem && (
        <View style={styles.padded}>
          <Notice tone="warning">{LOCATION_MESSAGES[locationProblem]}</Notice>
        </View>
      )}

      {showFoodTrucks && listStatus === 'success' && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.padded} accessibilityLiveRegion="polite">
          {foodTrucks.length === 0
            ? 'Aucun food truck présent aujourd’hui'
            : `${foodTrucks.length} présence${foodTrucks.length > 1 ? 's' : ''} aujourd’hui à moins de ${radiusKm} km`}
        </ThemedText>
      )}

      {!showFoodTrucks && state.status === 'success' && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.padded} accessibilityLiveRegion="polite">
          {activities.length === 0
            ? 'Aucun résultat'
            : `${activities.length} activité${activities.length > 1 ? 's' : ''} à moins de ${radiusKm} km`}
          {categoryCodes.length > 0
            ? ` · ${categoryCodes.length} catégorie${categoryCodes.length > 1 ? 's' : ''}`
            : ''}
        </ThemedText>
      )}
    </View>
  );

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.container} edges={['top', 'bottom', 'left', 'right']}>
        <FlatList
          data={items}
          keyExtractor={(item) => (item.kind === 'activity' ? `a-${item.activity.id}` : `t-${item.foodTruck.presenceId}`)}
          ListHeaderComponent={header}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          contentContainerStyle={styles.content}
          ItemSeparatorComponent={Separator}
          refreshControl={
            <RefreshControl
              refreshing={showFoodTrucks ? foodTruckList.refreshing : refreshing}
              onRefresh={refreshList}
              tintColor={theme.primary}
              colors={[theme.primary]}
            />
          }
          renderItem={({ item }) => (
            <View style={styles.padded}>
              {item.kind === 'activity' ? (
                <ActivityCard
                  activity={item.activity}
                  distance={place ? formatDistance(distanceKm(place, item.activity)) : undefined}
                  onPress={() => open(item.activity)}
                />
              ) : (
                <FoodTruckCard
                  foodTruck={item.foodTruck}
                  distance={formatDistance(item.km)}
                  onPress={() => openFoodTruck(item.foodTruck)}
                />
              )}
            </View>
          )}
          ListEmptyComponent={
            <View style={styles.padded}>
              {listStatus === 'idle' && (
                <Card style={styles.empty}>
                  <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
                    📍
                  </ThemedText>
                  <ThemedText type="heading" style={styles.centerText}>
                    Où cherchez-vous ?
                  </ThemedText>
                  <ThemedText themeColor="textSecondary" style={styles.centerText}>
                    Recherchez une ville ci-dessus, ou touchez « Autour de moi ».
                  </ThemedText>
                </Card>
              )}
              {listStatus === 'loading' && (
                <View style={styles.loading} accessibilityLiveRegion="polite">
                  <ActivityIndicator
                    size="large"
                    color={theme.primary}
                    accessibilityLabel={showFoodTrucks ? 'Chargement des food trucks' : 'Chargement des activités'}
                  />
                </View>
              )}
              {listStatus === 'error' && (
                <View style={styles.errorBlock}>
                  <Notice tone="danger">Erreur : {listError}</Notice>
                  <Button label="Réessayer" onPress={refreshList} />
                </View>
              )}
              {listStatus === 'success' && showFoodTrucks && (
                <Card style={styles.empty}>
                  <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
                    🚚
                  </ThemedText>
                  <ThemedText type="heading" style={styles.centerText}>
                    Aucun food truck ici aujourd’hui
                  </ThemedText>
                  <ThemedText themeColor="textSecondary" style={styles.centerText}>
                    {query
                      ? 'Aucun food truck ne correspond à votre filtre.'
                      : 'Élargissez le rayon, ou revenez un autre jour.'}
                  </ThemedText>
                </Card>
              )}
              {listStatus === 'success' && !showFoodTrucks && (
                <Card style={styles.empty}>
                  <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
                    🧭
                  </ThemedText>
                  <ThemedText type="heading" style={styles.centerText}>
                    Rien trouvé ici
                  </ThemedText>
                  <ThemedText themeColor="textSecondary" style={styles.centerText}>
                    {query
                      ? 'Aucune activité ne correspond à votre filtre.'
                      : categoryCodes.length > 0
                        ? 'Aucune activité de ces catégories ici. Retirez des catégories, ou élargissez le rayon ou la période.'
                        : 'Élargissez le rayon ou la période pour voir plus d’activités.'}
                  </ThemedText>
                  {!query && categoryCodes.length > 0 && (
                    <Button label="Toutes les catégories" variant="secondary" onPress={clearCategories} />
                  )}
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
    paddingTop: Spacing.two,
    paddingBottom: Spacing.three,
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  titleBlock: {
    gap: Spacing.one,
  },
  panel: {
    padding: Spacing.three,
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
    gap: Spacing.two,
  },
  toolsRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  tool: {
    flex: 1,
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
