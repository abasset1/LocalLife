import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import MapView, { Circle, UrlTile } from 'react-native-maps';
import { SafeAreaView } from 'react-native-safe-area-context';
import Supercluster from 'supercluster';

import type { Activity } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { isExpoGoAndroid } from '@/config/expoGo';
import { Brand, Radius, Shadows, Spacing } from '@/constants/theme';
import { isApproximateLocation } from '@/features/activity/location';
import { CategoryPanel } from '@/features/explorer/CategoryPanel';
import { CitySearch } from '@/features/explorer/CitySearch';
import {
  applyPreferences,
  clearCategories,
  setPlace,
  setRadius,
  useExplorer,
  type ExplorerPlace,
} from '@/features/explorer/explorerStore';
import { PERIOD_OPTIONS } from '@/features/list/filters';
import { usePeriod } from '@/features/list/periodStore';
import { MenuButton } from '@/features/navigation/AppMenu';
import { ChipGroup, RADIUS_OPTIONS_KM } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

import { ActivityGroupPanel } from './ActivityGroupPanel';
import {
  Region,
  pointInBounds,
  regionForRadius,
  regionForZoom,
  regionToBounds,
  regionToFit,
  zoomForRegion,
} from './geo';
import { MapControl } from './MapControl';
import { ActivityMarker, ClusterMarker, FoodTruckMarker, PlaceMarker } from './markers';
import { PlaceListPanel } from './PlaceListPanel';
import { PreviewCard, type Selection } from './PreviewCard';
import { useMapData, type RadiusSearch } from './useMapData';
import { loadShowPlaces, saveShowPlaces } from './placesPreference';
import { usePlaces } from './usePlaces';
import { useSearchPreferences } from './useSearchPreferences';
import { LOCATION_MESSAGES, useUserLocation } from './useUserLocation';

/**
 * Vue de départ tant qu'aucun lieu n'est choisi : vue d'ensemble de la zone couverte par les sources
 * (Provence), comme le site web — les activités s'y affichent sans avoir à zoomer. Ce n'est jamais
 * présenté comme la ville de l'utilisateur : la carte invite alors à choisir une ville ou « Ma
 * position » (FRONT-REV-02).
 */
const INITIAL_REGION: Region = {
  latitude: 43.65,
  longitude: 5.2,
  latitudeDelta: 3,
  longitudeDelta: 2.2,
};

/** Niveau de zoom après recentrage sur l'utilisateur (~ quartier, quelques centaines de mètres). */
const USER_ZOOM = 14;

/** Niveau de zoom sur une ville choisie sans rayon (~ commune entière). */
const CITY_ZOOM = 12;

/** Rayon de cadrage d'une ville par défaut sans rayon choisi (même valeur que le site web). */
const DEFAULT_PREFERENCE_RADIUS_KM = 50;

/** Valeur de la puce « Sans rayon » du sélecteur de rayon. */
const NO_RADIUS = 'none';

/** Zoom après « Voir sur la carte » depuis une fiche (~ rue). */
const FOCUS_ZOOM = 16;

/** Au-delà de ce zoom, plus de regroupement : chaque activité a son marqueur. */
const CLUSTER_MAX_ZOOM = 17;

/**
 * Dans Expo Go sur Android, le fond Google Maps peut rester vide (la clé est
 * celle d'Expo Go, pas la nôtre) : tuiles OpenStreetMap à la place, pour le
 * développement uniquement. Les builds (APK, iOS) gardent le fond natif ; la
 * politique d'usage d'OpenStreetMap exclut un usage intensif en production.
 */
const USE_OSM_TILES = isExpoGoAndroid;
const OSM_TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';

/**
 * Carte interactive de l'onglet « Découvrir » (LL-MOB-0005) : activités
 * publiées (regroupées en clusters), food trucks distincts, aperçu rapide
 * d'un élément, états chargement / erreur / aucun résultat.
 */
export default function MapScreen() {
  const theme = useTheme();
  const mapRef = useRef<MapView>(null);

  const [region, setRegion] = useState<Region>(INITIAL_REGION);
  const router = useRouter();
  /** « Voir sur la carte » depuis une fiche : centre et activité à sélectionner (paramètres de route). */
  const focus = useLocalSearchParams<{ focusId?: string; focusLat?: string; focusLon?: string }>();
  const [selection, setSelection] = useState<Selection | null>(null);
  /** Heure du dernier appui sur un marqueur (iOS n'indique pas toujours `marker-press`). */
  const markerPressedAt = useRef(0);
  const select = useCallback((next: Selection) => {
    markerPressedAt.current = Date.now();
    setSelection(next);
  }, []);

  // Critères partagés avec la liste (FRONT-REV-02) : lieu, catégories, rayon. Les paramètres de
  // recherche (utilisateur connecté) les initialisent à chaque changement de ces paramètres, pas à
  // chaque retour sur la carte : un choix fait par l'utilisateur n'est pas écrasé.
  const preferences = useSearchPreferences();
  useEffect(() => {
    applyPreferences(preferences);
  }, [preferences]);
  const { place, categoryCodes, radiusKm: activeRadiusKm } = useExplorer();

  // Centre courant, lu sans relancer le cadrage à chaque déplacement de la carte.
  const regionRef = useRef(region);
  useEffect(() => {
    regionRef.current = region;
  }, [region]);

  const [radiusPanelOpen, setRadiusPanelOpen] = useState(false);
  const [periodPanelOpen, setPeriodPanelOpen] = useState(false);
  const [categoryPanelOpen, setCategoryPanelOpen] = useState(false);
  const [placeListOpen, setPlaceListOpen] = useState(false);
  /** Activités d'un groupe de marqueurs au même endroit, listées après un appui sur ce groupe. */
  const [activityGroup, setActivityGroup] = useState<Activity[] | null>(null);
  /** Un seul panneau ouvert à la fois. */
  const closePanels = useCallback(() => {
    setRadiusPanelOpen(false);
    setPeriodPanelOpen(false);
    setCategoryPanelOpen(false);
    setPlaceListOpen(false);
    setActivityGroup(null);
  }, []);
  const [period, setPeriod] = usePeriod();
  const periodLabel = PERIOD_OPTIONS.find((option) => option.value === period)?.label ?? 'Tout';

  // Carte pas encore prête (premier affichage de l'onglet) : le cadrage demandé attend `onMapReady`.
  const mapReady = useRef(false);
  const pendingRegion = useRef<Region | null>(null);
  const pendingFocusId = useRef<number | null>(null);
  /** Carte cadrée sur une activité : le cadrage d'après les préférences, s'il arrive ensuite, est ignoré. */
  const focusedOnActivity = useRef(false);
  const moveTo = useCallback((target: Region) => {
    if (mapReady.current) mapRef.current?.animateToRegion(target, 500);
    else pendingRegion.current = target;
  }, []);

  useEffect(() => {
    const latitude = Number(focus.focusLat);
    const longitude = Number(focus.focusLon);
    if (!focus.focusLat || !focus.focusLon || !Number.isFinite(latitude) || !Number.isFinite(longitude)) return;
    pendingFocusId.current = focus.focusId ? Number(focus.focusId) : null;
    focusedOnActivity.current = true;
    closePanels();
    // L'activité demandée doit apparaître, même hors de la période choisie.
    setPeriod('all');
    moveTo(regionForZoom(latitude, longitude, FOCUS_ZOOM, regionRef.current));
    // Paramètres consommés : revenir sur l'onglet ne recentre pas une seconde fois.
    router.setParams({ focusId: undefined, focusLat: undefined, focusLon: undefined });
  }, [focus.focusId, focus.focusLat, focus.focusLon, moveTo, router, setPeriod, closePanels]);

  // Nouveau lieu de recherche (ville choisie, « Ma position », ville des paramètres) : la carte s'y
  // cadre, sur le rayon s'il y en a un. Un lieu venant des paramètres n'écrase pas un cadrage déjà fait
  // sur une activité (« Voir sur la carte »).
  const framedPlace = useRef<ExplorerPlace | null>(null);
  // Point bleu de l'appareil : seulement quand la recherche part de sa position.
  const showUser = place?.source === 'position';
  useEffect(() => {
    // Lieu retiré (« ✕ ») : la carte reste où elle est.
    if (!place || framedPlace.current === place) return;
    framedPlace.current = place;
    if (place.source === 'map') return;
    if (place.source === 'preferences' && focusedOnActivity.current) return;
    setSelection(null);
    const radius = activeRadiusKm ?? (place.source === 'preferences' ? DEFAULT_PREFERENCE_RADIUS_KM : null);
    moveTo(
      radius !== null
        ? regionForRadius(place.latitude, place.longitude, radius)
        : regionForZoom(
            place.latitude,
            place.longitude,
            place.source === 'position' ? USER_ZOOM : CITY_ZOOM,
            regionRef.current,
          ),
    );
  }, [place, activeRadiusKm, moveTo]);

  // Rayon actif : seules les activités dans ce rayon autour du lieu de recherche sont affichées.
  const radiusSearch = useMemo<RadiusSearch | null>(
    () =>
      activeRadiusKm !== null && place
        ? { latitude: place.latitude, longitude: place.longitude, radiusKm: activeRadiusKm }
        : null,
    [activeRadiusKm, place],
  );

  /**
   * Rayon choisi depuis la carte : recherche et cadrage dans ce rayon autour du lieu de recherche (ou,
   * sans lieu, du centre actuel de la carte) ; « Sans rayon » revient à la recherche par zone visible.
   */
  const changeRadius = useCallback(
    (radiusKm: number | null) => {
      setRadiusPanelOpen(false);
      setRadius(radiusKm);
      if (radiusKm === null) return;
      const center = place ?? { latitude: region.latitude, longitude: region.longitude };
      if (!place) {
        setPlace({ label: 'la zone de la carte', latitude: center.latitude, longitude: center.longitude, source: 'map' });
      }
      mapRef.current?.animateToRegion(regionForRadius(center.latitude, center.longitude, radiusKm), 500);
    },
    [place, region],
  );

  const { activities, foodTrucks, loading, error, tooWide, retry } = useMapData(
    region,
    categoryCodes,
    radiusSearch,
    period,
  );
  // Activité demandée par « Voir sur la carte » : son aperçu s'ouvre dès qu'elle est chargée.
  useEffect(() => {
    if (pendingFocusId.current === null) return;
    const activity = activities.find((a) => a.id === pendingFocusId.current);
    if (!activity) return;
    pendingFocusId.current = null;
    select({ kind: 'activity', activity });
  }, [activities, select]);
  // Couche « Lieux » (ADR-0011, phase B) : désactivée par défaut, choix mémorisé sur l'appareil.
  const [showPlaces, setShowPlaces] = useState(false);
  useEffect(() => {
    let active = true;
    loadShowPlaces().then((shown) => {
      if (active && shown) setShowPlaces(true);
    });
    return () => {
      active = false;
    };
  }, []);
  const toggleShowPlaces = useCallback(() => {
    setShowPlaces(!showPlaces);
    saveShowPlaces(!showPlaces);
  }, [showPlaces]);
  const places = usePlaces(region, showPlaces);
  const location = useUserLocation();

  const bounds = useMemo(() => regionToBounds(region), [region]);
  const zoom = zoomForRegion(region);

  const index = useMemo(() => {
    const cluster = new Supercluster<{ activity: Activity }>({ radius: 50, maxZoom: CLUSTER_MAX_ZOOM });
    cluster.load(
      activities.map((activity) => ({
        type: 'Feature' as const,
        geometry: { type: 'Point' as const, coordinates: [activity.longitude, activity.latitude] },
        properties: { activity },
      })),
    );
    return cluster;
  }, [activities]);

  const clusters = useMemo(
    () =>
      bounds
        ? index.getClusters(
            [bounds.swLongitude, bounds.swLatitude, bounds.neLongitude, bounds.neLatitude],
            zoom,
          )
        : [],
    [index, bounds, zoom],
  );

  const visibleFoodTrucks = useMemo(
    () =>
      bounds ? foodTrucks.filter((t) => pointInBounds(bounds, t.latitude, t.longitude)) : [],
    [foodTrucks, bounds],
  );

  const visiblePlaces = useMemo(
    () => (bounds ? places.filter((p) => pointInBounds(bounds, p.latitude, p.longitude)) : []),
    [places, bounds],
  );

  /**
   * Appui sur un groupe de marqueurs, comme sur le site web : la carte cadre toutes ses activités d'un
   * coup (au lieu d'avancer d'un niveau de zoom à la fois). Si elles sont au même endroit, ou si le
   * regroupement ne s'arrêterait qu'au-delà du zoom maximal, elles sont listées.
   */
  const openCluster = useCallback(
    (clusterId: number) => {
      const leaves = index
        .getLeaves(clusterId, Infinity)
        .map((feature) => (feature.properties as { activity: Activity }).activity);
      const target = regionToFit(leaves, region);
      if (!target || index.getClusterExpansionZoom(clusterId) > CLUSTER_MAX_ZOOM) {
        closePanels();
        setActivityGroup(leaves);
        return;
      }
      mapRef.current?.animateToRegion(target, 300);
    },
    [index, region, closePanels],
  );

  // « Ma position » : la position devient le lieu de recherche (carte et liste) ; la carte s'y cadre
  // (effet ci-dessus) et `useMapData` recharge les activités autour.
  const centerOnUser = useCallback(async () => {
    const position = await location.locate();
    if (!position) return;
    setPlace({ label: 'votre position', latitude: position.latitude, longitude: position.longitude, source: 'position' });
  }, [location]);

  const locationProblem =
    location.status === 'denied' || location.status === 'unavailable' || location.status === 'error'
      ? location.status
      : null;

  const isEmpty = !loading && !error && !tooWide && clusters.length === 0 && visibleFoodTrucks.length === 0;

  return (
    <View style={styles.container}>
      <MapView
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        initialRegion={INITIAL_REGION}
        onRegionChangeComplete={setRegion}
        onMapReady={() => {
          mapReady.current = true;
          if (pendingRegion.current) {
            mapRef.current?.animateToRegion(pendingRegion.current, 500);
            pendingRegion.current = null;
          }
        }}
        // Toucher un marqueur déclenche aussi `onPress` de la carte (`action: 'marker-press'`) : sans ce
        // filtre, la sélection était aussitôt effacée et l'aperçu (donc la fiche) restait inaccessible.
        onPress={(event) => {
          if (event.nativeEvent.action === 'marker-press' || Date.now() - markerPressedAt.current < 400) return;
          setSelection(null);
          setActivityGroup(null);
        }}
        showsUserLocation={showUser}
        showsMyLocationButton={false}
        toolbarEnabled={false}
        moveOnMarkerPress={false}
        rotateEnabled={false}
        pitchEnabled={false}
        mapType={USE_OSM_TILES ? 'none' : 'standard'}>
        {USE_OSM_TILES && <UrlTile urlTemplate={OSM_TILE_URL} maximumZ={19} zIndex={-1} />}
        {radiusSearch && (
          // Rayon favori, matérialisé comme sur le site web.
          <Circle
            center={{ latitude: radiusSearch.latitude, longitude: radiusSearch.longitude }}
            radius={radiusSearch.radiusKm * 1000}
            strokeColor={Brand.green2}
            strokeWidth={2}
            lineDashPattern={[8, 8]}
            fillColor="rgba(36, 84, 71, 0.07)"
          />
        )}
        {clusters.map((feature) => {
          const [longitude, latitude] = feature.geometry.coordinates;
          const coordinate = { latitude, longitude };

          if ('cluster' in feature.properties && feature.properties.cluster) {
            const clusterId = feature.properties.cluster_id;
            const count = feature.properties.point_count;
            return (
              <ClusterMarker
                key={`c-${clusterId}-${count}`}
                coordinate={coordinate}
                count={count}
                onPress={() => {
                  markerPressedAt.current = Date.now();
                  openCluster(clusterId);
                }}
              />
            );
          }

          const { activity } = feature.properties as { activity: Activity };
          return (
            <ActivityMarker
              key={`a-${activity.id}`}
              coordinate={coordinate}
              title={activity.title}
              approximate={isApproximateLocation(activity)}
              onPress={() => select({ kind: 'activity', activity })}
            />
          );
        })}

        {visibleFoodTrucks.map((foodTruck) => (
          <FoodTruckMarker
            key={`t-${foodTruck.id}`}
            coordinate={{ latitude: foodTruck.latitude, longitude: foodTruck.longitude }}
            name={foodTruck.name}
            onPress={() => select({ kind: 'foodTruck', foodTruck })}
          />
        ))}

        {visiblePlaces.map((place) => (
          <PlaceMarker
            key={`p-${place.id}`}
            coordinate={{ latitude: place.latitude, longitude: place.longitude }}
            name={place.name}
            onPress={() => select({ kind: 'place', place })}
          />
        ))}
      </MapView>

      {USE_OSM_TILES && (
        // Attribution exigée par OpenStreetMap.
        <View style={styles.attribution} pointerEvents="none">
          <ThemedText type="small" style={styles.attributionText}>
            © OpenStreetMap
          </ThemedText>
        </View>
      )}

      {/* Plus de barre d'onglets (menu rétractable) : le bas (barre de gestes) est aussi réservé. */}
      <SafeAreaView style={styles.overlay} edges={['top', 'bottom', 'left', 'right']} pointerEvents="box-none">
        <View style={styles.top} pointerEvents="box-none">
          <View style={styles.searchRow} pointerEvents="box-none">
            <MenuButton floating />
            <View style={styles.search}>
              <CitySearch floating />
            </View>
          </View>
          {!place && !loading && !error && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="small">Vue d’ensemble : cherchez une ville ou touchez « Ma position ».</ThemedText>
            </View>
          )}
          {loading && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ActivityIndicator size="small" color={theme.primary} />
              <ThemedText type="smallBold">Chargement…</ThemedText>
            </View>
          )}
          {error && (
            <View
              accessibilityRole="alert"
              style={[styles.banner, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="small" style={{ color: theme.danger }}>
                Erreur : {error}
              </ThemedText>
              <Button label="Réessayer" onPress={retry} />
            </View>
          )}
          {locationProblem && (
            <View
              accessibilityRole="alert"
              style={[styles.banner, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="small">{LOCATION_MESSAGES[locationProblem]}</ThemedText>
              <View style={styles.actions}>
                {locationProblem === 'denied' && !location.canAskAgain && (
                  <Button label="Ouvrir les réglages" onPress={location.openSettings} />
                )}
                {locationProblem === 'error' && (
                  <Button label="Réessayer" onPress={centerOnUser} />
                )}
                <Button label="Fermer" variant="secondary" onPress={location.dismiss} />
              </View>
            </View>
          )}
          {categoryCodes.length > 0 && (
            <View style={[styles.chip, styles.filterChip, { backgroundColor: theme.backgroundElement }]}>
              <View style={[styles.filterDot, { backgroundColor: theme.accent }]} />
              <ThemedText type="smallBold">
                {categoryCodes.length === 1
                  ? 'Filtré sur 1 catégorie'
                  : `Filtré sur ${categoryCodes.length} catégories`}
              </ThemedText>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Tout afficher"
                hitSlop={8}
                onPress={clearCategories}
                style={[styles.chipAction, { backgroundColor: theme.primarySoft }]}>
                <ThemedText type="smallBold" themeColor="primary">
                  Tout afficher
                </ThemedText>
              </Pressable>
            </View>
          )}
          {tooWide && !loading && !error && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">🔍 Zoomez pour voir les activités.</ThemedText>
            </View>
          )}
          {isEmpty && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">
                {period === 'all'
                  ? 'Aucune activité dans cette zone.'
                  : `Aucune activité ici pour « ${periodLabel} ».`}
              </ThemedText>
            </View>
          )}
        </View>

        <View style={styles.bottom} pointerEvents="box-none">
          {!selection && activityGroup && (
            <ActivityGroupPanel
              activities={activityGroup}
              onClose={() => setActivityGroup(null)}
              onSelect={(activity) => {
                setActivityGroup(null);
                select({ kind: 'activity', activity });
              }}
            />
          )}
          {!selection && placeListOpen && showPlaces && visiblePlaces.length > 0 && (
            <PlaceListPanel
              places={visiblePlaces}
              onSelect={(place) => {
                setPlaceListOpen(false);
                setSelection({ kind: 'place', place });
              }}
            />
          )}
          {!selection && categoryPanelOpen && (
            <View style={[styles.radiusPanel, { backgroundColor: theme.backgroundElement }]}>
              <CategoryPanel />
            </View>
          )}
          {!selection && radiusPanelOpen && (
            <View style={[styles.radiusPanel, { backgroundColor: theme.backgroundElement }]}>
              <ChipGroup
                label="Afficher les activités dans un rayon de"
                single
                options={[
                  { value: NO_RADIUS, label: 'Sans rayon' },
                  ...RADIUS_OPTIONS_KM.map((km) => ({ value: String(km), label: `${km} km` })),
                ]}
                selected={[activeRadiusKm === null ? NO_RADIUS : String(activeRadiusKm)]}
                onToggle={(value) => changeRadius(value === NO_RADIUS ? null : Number(value))}
              />
            </View>
          )}
          {!selection && periodPanelOpen && (
            <View style={[styles.radiusPanel, { backgroundColor: theme.backgroundElement }]}>
              <ChipGroup
                label="Afficher les activités"
                single
                options={PERIOD_OPTIONS}
                selected={[period]}
                onToggle={(value) => {
                  setPeriod(value as typeof period);
                  setPeriodPanelOpen(false);
                }}
              />
            </View>
          )}
          {!selection && (
            // Une seule rangée d'icônes (FRONT-REV-02) : l'état choisi se lit à la couleur, le détail est
            // annoncé par le libellé accessible.
            <View style={styles.bottomActions} pointerEvents="box-none">
              <MapControl
                icon={{ ios: 'list.bullet.rectangle', android: 'view_list', web: 'view_list' }}
                accessibilityLabel="Afficher la liste des activités"
                onPress={() => {
                  closePanels();
                  router.navigate('/liste');
                }}
              />
              <MapControl
                icon={{ ios: 'square.grid.2x2', android: 'category', web: 'category' }}
                accessibilityLabel={
                  categoryCodes.length === 0
                    ? 'Choisir des catégories'
                    : `${categoryCodes.length} catégorie${categoryCodes.length > 1 ? 's' : ''} choisie${categoryCodes.length > 1 ? 's' : ''}, modifier`
                }
                active={categoryCodes.length > 0 || categoryPanelOpen}
                onPress={() => {
                  const open = categoryPanelOpen;
                  closePanels();
                  setCategoryPanelOpen(!open);
                }}
              />
              <MapControl
                icon={{ ios: 'building.columns', android: 'account_balance', web: 'account_balance' }}
                accessibilityLabel={showPlaces ? 'Masquer les lieux' : 'Afficher les lieux'}
                active={showPlaces}
                onPress={toggleShowPlaces}
              />
              <MapControl
                icon={{ ios: 'calendar', android: 'calendar_month', web: 'calendar_month' }}
                accessibilityLabel={
                  period === 'all' ? 'Choisir une période' : `Période : ${periodLabel}, modifier`
                }
                active={period !== 'all' || periodPanelOpen}
                onPress={() => {
                  const open = periodPanelOpen;
                  closePanels();
                  setPeriodPanelOpen(!open);
                }}
              />
              <MapControl
                icon={{ ios: 'scope', android: 'radar', web: 'radar' }}
                accessibilityLabel={
                  activeRadiusKm === null ? 'Choisir un rayon' : `Rayon de ${activeRadiusKm} km, modifier`
                }
                active={activeRadiusKm !== null || radiusPanelOpen}
                onPress={() => {
                  const open = radiusPanelOpen;
                  closePanels();
                  setRadiusPanelOpen(!open);
                }}
              />
              {showPlaces && visiblePlaces.length > 0 && (
                <MapControl
                  icon={{ ios: 'list.bullet', android: 'list', web: 'list' }}
                  accessibilityLabel={
                    placeListOpen ? 'Fermer la liste' : `Liste des lieux (${visiblePlaces.length})`
                  }
                  active={placeListOpen}
                  onPress={() => {
                    const open = placeListOpen;
                    closePanels();
                    setPlaceListOpen(!open);
                  }}
                />
              )}
              <MapControl
              icon={{ ios: 'location.fill', android: 'my_location', web: 'my_location' }}
              accessibilityLabel="Ma position"
              loading={location.status === 'locating'}
              onPress={centerOnUser}
              />
            </View>
          )}
          {selection && <PreviewCard selection={selection} onClose={() => setSelection(null)} />}
        </View>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  attribution: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    paddingHorizontal: Spacing.one,
    backgroundColor: 'rgba(255, 255, 255, 0.7)',
  },
  attributionText: {
    fontSize: 10,
    color: '#333',
  },
  overlay: {
    flex: 1,
    justifyContent: 'space-between',
  },
  top: {
    alignItems: 'center',
    gap: Spacing.two,
    margin: Spacing.three,
  },
  searchRow: {
    alignSelf: 'stretch',
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two,
  },
  search: {
    flex: 1,
  },
  bottom: {
    justifyContent: 'flex-end',
    margin: Spacing.three,
  },
  bottomActions: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  radiusPanel: {
    marginBottom: Spacing.three,
    padding: Spacing.three,
    borderRadius: Radius.xl,
    ...Shadows.floating,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    minHeight: 40,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    borderRadius: Radius.pill,
    ...Shadows.floating,
  },
  filterChip: {
    paddingRight: Spacing.one + Spacing.half,
  },
  filterDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  chipAction: {
    minHeight: 32,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
    borderRadius: Radius.pill,
  },
  banner: {
    alignSelf: 'stretch',
    gap: Spacing.three,
    padding: Spacing.three,
    borderRadius: Radius.lg,
    ...Shadows.floating,
  },
});
