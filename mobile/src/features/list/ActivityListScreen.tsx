import { useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, RefreshControl, StyleSheet, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import type { Activity, FoodTruck, Place } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Spacing } from '@/constants/theme';
import { CategoryPanel, categoriesButtonLabel } from '@/features/explorer/CategoryPanel';
import { CitySearch } from '@/features/explorer/CitySearch';
import { applyPreferences, clearCategories, setFreeOnly, setPlace, setRadius, useExplorer } from '@/features/explorer/explorerStore';
import { keepFreeActivities } from '@/features/map/priceFilter';
import { useSearchPreferences } from '@/features/map/useSearchPreferences';
import { LOCATION_MESSAGES, useUserLocation } from '@/features/map/useUserLocation';
import { MenuButton } from '@/features/navigation/AppMenu';
import { RADIUS_OPTIONS_KM } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

import { ActivityCard } from './ActivityCard';
import { FilterChips } from './FilterChips';
import { PERIOD_OPTIONS, distanceKm, formatDistance, matchesQuery } from './filters';
import { CuisineFilterBar } from '@/features/foodtruck/CuisineFilterBar';
import { chooseMapLayer, shownLayer, useMapLayer } from '@/features/map/layerStore';
import { layerContents, layerTabs } from '@/features/map/mapControls';

import { FoodTruckCard } from './FoodTruckCard';
import { foodTrucksNear } from './foodTruckList';
import { usePeriod } from './periodStore';
import { useActivityList } from './useActivityList';
import { useFoodTruckList } from './useFoodTruckList';
import { PlaceCard } from './PlaceCard';
import { listItemKey, listSummary, mergeByDistance, placesNear, type ListItem } from './placeList';
import { usePlaceList } from './usePlaceList';
import { useLaunchFeatures } from '@/features/launch';

/** Rayon de la liste tant qu'aucun rayon n'est choisi (la carte cherche alors dans la zone visible). */
const DEFAULT_RADIUS_KM = 20;

/**
 * Vue « Liste » : les activités publiées autour du lieu de recherche (ville choisie, « Autour de moi »
 * ou ville des paramètres), triées par distance. Lieu, catégories et rayon sont partagés avec la carte
 * (`explorerStore`, FRONT-REV-02) ; la période aussi (`periodStore`). Sans lieu choisi, la liste invite
 * à en choisir un : aucune ville n'est supposée. Filtre texte local en plus. Un appui ouvre la fiche.
 *
 * Contenus (issue #226) : les mêmes que la carte, partagés avec elle (`layerStore`). « Activités » liste les activités
 * par distance ; « Lieux » les lieux du rayon ; « Food trucks » les présences du jour (période
 * et catégories ne s'y appliquent pas) ; « Night » les activités de 20 h à 6 h, dans le thème nuit.
 */
export default function ActivityListScreen() {
  const theme = useTheme();
  const router = useRouter();
  const preferences = useSearchPreferences();
  useEffect(() => {
    applyPreferences(preferences);
  }, [preferences]);
  const location = useUserLocation();

  const { place, categoryCodes, radiusKm: chosenRadiusKm, freeOnly } = useExplorer();
  const [period, setPeriod] = usePeriod();
  const [query, setQuery] = useState('');
  const [categoryPanelOpen, setCategoryPanelOpen] = useState(false);
  // Univers partagé avec la carte : en changer ici le change aussi là-bas, et inversement.
  const features = useLaunchFeatures();
  const layer = shownLayer(useMapLayer(), features);
  const contents = layerContents(layer, features);
  const showFoodTrucks = layer === 'foodTrucks';
  // Filtre « type de cuisine » de l'univers food trucks (`null` = toutes).
  const [cuisine, setCuisine] = useState<string | null>(null);

  const radiusKm = chosenRadiusKm ?? DEFAULT_RADIUS_KM;

  const { state, refreshing, refresh } = useActivityList(
    place && contents.activities
      ? { latitude: place.latitude, longitude: place.longitude, radiusKm, period, categoryCodes, night: contents.night }
      : null,
  );

  // « Gratuit » choisi sur la carte (état partagé) : appliqué ici aussi, comme sur le site.
  const activities = useMemo(
    () =>
      state.status === 'success'
        ? keepFreeActivities(state.activities, freeOnly).filter((a) => matchesQuery(a, query))
        : [],
    [state, query, freeOnly],
  );

  const foodTruckList = useFoodTruckList(contents.foodTrucks && place !== null);
  const foodTrucks = useMemo(
    () =>
      place && foodTruckList.state.status === 'success'
        ? foodTrucksNear(foodTruckList.state.foodTrucks, place, radiusKm, query, showFoodTrucks ? cuisine : null)
        : [],
    [foodTruckList.state, place, radiusKm, query, cuisine, showFoodTrucks],
  );

  const placeList = usePlaceList(place, radiusKm, contents.places);
  const places = useMemo(
    () =>
      place && placeList.state.status === 'success' ? placesNear(placeList.state.places, place, radiusKm, query) : [],
    [placeList.state, place, radiusKm, query],
  );

  const items: ListItem[] = mergeByDistance(
    contents.activities
      ? activities.map((activity) => ({ kind: 'activity', activity, km: place ? distanceKm(place, activity) : 0 }))
      : [],
    contents.places ? places.map(({ place: item, km }) => ({ kind: 'place', place: item, km })) : [],
    contents.foodTrucks ? foodTrucks.map(({ foodTruck, distanceKm: km }) => ({ kind: 'foodTruck', foodTruck, km })) : [],
  );
  // État affiché (chargement, erreur, vide) : celui du contenu choisi.
  const primary = contents.activities ? state : showFoodTrucks ? foodTruckList.state : placeList.state;
  const listStatus = place === null ? 'idle' : primary.status;
  const listError = primary.status === 'error' ? primary.message : '';
  const listRefreshing =
    (contents.activities && refreshing) ||
    (contents.foodTrucks && foodTruckList.refreshing) ||
    (contents.places && placeList.refreshing);
  function refreshList() {
    if (contents.activities) refresh();
    if (contents.foodTrucks) foodTruckList.refresh();
    if (contents.places) placeList.refresh();
  }

  async function aroundMe() {
    const position = await location.locate();
    if (position) {
      setPlace({ label: 'votre position', latitude: position.latitude, longitude: position.longitude, source: 'position' });
    }
  }

  function openFoodTruck(foodTruck: FoodTruck) {
    router.push({ pathname: '/foodtruck/[id]', params: { id: String(foodTruck.id) } });
  }

  // Lieu : pas de fiche dédiée, la carte se cadre dessus (son aperçu s'ouvre d'un appui sur le marqueur).
  function showPlace(item: Place) {
    router.navigate({ pathname: '/', params: { focusLat: String(item.latitude), focusLon: String(item.longitude) } });
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

      <FilterChips
        label="Univers"
        options={layerTabs(features).map((tab) => ({ value: tab.value, label: tab.label }))}
        value={layer}
        onChange={chooseMapLayer}
      />

      <View style={styles.padded}>
        <View style={[styles.search, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <ThemedText importantForAccessibility="no">⌕</ThemedText>
          <TextInput
            accessibilityLabel="Filtrer la liste"
            placeholder={
              showFoodTrucks
                ? 'Filtrer : tacos, crêpes…'
                : layer === 'places'
                  ? 'Filtrer : musée, théâtre…'
                  : 'Filtrer : concert, marché…'
            }
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

      {showFoodTrucks && foodTruckList.state.status === 'success' && (
        <View style={styles.padded}>
          <CuisineFilterBar foodTrucks={foodTruckList.state.foodTrucks} selected={cuisine} onChange={setCuisine} />
        </View>
      )}
      {contents.activities && (
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
          {contents.activities && (
            <View style={styles.tool}>
              <Button
                label={categoriesButtonLabel(categoryCodes.length)}
                variant={categoryCodes.length > 0 ? 'primary' : 'secondary'}
                icon={{ ios: 'square.grid.2x2', android: 'category', web: 'category' }}
                onPress={() => setCategoryPanelOpen((open) => !open)}
              />
            </View>
          )}
          {contents.activities && (
            <View style={styles.tool}>
              <Button
                label="Gratuit"
                variant={freeOnly ? 'primary' : 'secondary'}
                icon={{ ios: 'eurosign.circle', android: 'money_off', web: 'money_off' }}
                onPress={() => setFreeOnly(!freeOnly)}
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
        {categoryPanelOpen && contents.activities && (
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

      {listStatus === 'success' && (
        <ThemedText type="small" themeColor="textSecondary" style={styles.padded} accessibilityLiveRegion="polite">
          {listSummary(layer, items.length, radiusKm)}
          {contents.activities && categoryCodes.length > 0
            ? ` · ${categoryCodes.length} catégorie${categoryCodes.length > 1 ? 's' : ''}`
            : ''}
          {contents.activities && freeOnly ? ' · gratuites' : ''}
        </ThemedText>
      )}
    </View>
  );

  return (
    <ThemedView style={styles.container}>
      {/* La barre d'onglets du bas réserve déjà la barre de gestes. */}
      <SafeAreaView style={styles.container} edges={['top', 'left', 'right']}>
        <FlatList
          data={items}
          keyExtractor={listItemKey}
          ListHeaderComponent={header}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
          contentContainerStyle={styles.content}
          ItemSeparatorComponent={Separator}
          refreshControl={
            <RefreshControl
              refreshing={listRefreshing}
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
                  distance={place ? formatDistance(item.km) : undefined}
                  onPress={() => open(item.activity)}
                />
              ) : item.kind === 'place' ? (
                <PlaceCard place={item.place} distance={formatDistance(item.km)} onPress={() => showPlace(item.place)} />
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
                    accessibilityLabel={
                      showFoodTrucks
                        ? 'Chargement des food trucks'
                        : layer === 'places'
                          ? 'Chargement des lieux'
                          : 'Chargement des activités'
                    }
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
              {listStatus === 'success' && layer === 'places' && (
                <Card style={styles.empty}>
                  <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
                    🏛
                  </ThemedText>
                  <ThemedText type="heading" style={styles.centerText}>
                    Aucun lieu ici
                  </ThemedText>
                  <ThemedText themeColor="textSecondary" style={styles.centerText}>
                    {query ? 'Aucun lieu ne correspond à votre filtre.' : 'Élargissez le rayon pour voir plus de lieux.'}
                  </ThemedText>
                </Card>
              )}
              {listStatus === 'success' && contents.activities && (
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
