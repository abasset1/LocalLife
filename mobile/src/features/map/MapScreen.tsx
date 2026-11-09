import { useLocalSearchParams, useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
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
import { FollowNewsBanner, FollowNewsPopup, PremiumOfferPopup } from '@/features/news';
import { ChipGroup, RADIUS_OPTIONS_KM } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

import { activitiesAtSamePlace, clusterTapAction, sortByStartDate } from './activityGroups';
import {
  Region,
  pointInBounds,
  regionForRadius,
  regionForZoom,
  regionToBounds,
  regionToFit,
  zoomForRegion,
} from './geo';
import { MapChip, MapChipRow, type ChipIcon } from './MapChip';
import { applyMapLayerPreference, chooseMapLayer, shownLayer as layerShown, useMapLayer } from './layerStore';
import {
  categoriesChipLabel,
  hasActiveFilters,
  layerContents,
  layerTabs,
  listButtonLabel,
  periodChipLabel,
  radiusChipLabel,
} from './mapControls';
import { mapStyleFor } from './nightMapStyle';
import { ActivityMarker, ClusterMarker, FoodTruckMarker, HighlightedActivityMarker, PlaceMarker } from './markers';
import { CuisineFilterBar } from '@/features/foodtruck/CuisineFilterBar';
import { markerOf } from '@/features/foodtruck/cuisines';
import { keepFreeActivities } from './priceFilter';
import { PreviewCard, type Selection } from './PreviewCard';
import { useMapData, type RadiusSearch } from './useMapData';
import { type MapLayer, preferredMapLayer } from './placesPreference';
import { usePlaces } from './usePlaces';
import { useLaunchFeatures } from '@/features/launch';
import { useSearchPreferences } from './useSearchPreferences';
import { LOCATION_MESSAGES, useUserLocation } from './useUserLocation';

/** Pictogrammes des contenus du bouton « Affichage » (Night garde son logo, voir `MapChip`). */
const LAYER_ICONS: Record<Exclude<MapLayer, 'night'>, ChipIcon> = {
  activities: { ios: 'calendar', android: 'event', web: 'event' },
  places: { ios: 'mappin.and.ellipse', android: 'location_on', web: 'location_on' },
  foodTrucks: { ios: 'box.truck', android: 'local_shipping', web: 'local_shipping' },
};

const ICONS = {
  period: { ios: 'calendar', android: 'calendar_today', web: 'calendar_today' },
  categories: { ios: 'tag', android: 'sell', web: 'sell' },
  radius: { ios: 'scope', android: 'radar', web: 'radar' },
  list: { ios: 'list.bullet', android: 'view_list', web: 'view_list' },
  locate: { ios: 'location', android: 'my_location', web: 'my_location' },
  display: { ios: 'square.2.layers.3d', android: 'layers', web: 'layers' },
} satisfies Record<string, ChipIcon>;

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
 * publiées (regroupées en clusters), lieux ou food trucks — un seul de ces
 * contenus à la fois —, aperçu rapide
 * d'un élément, états chargement / erreur / aucun résultat.
 */
export default function MapScreen() {
  // Lancement progressif (05/11/2026) : lieux, food trucks et Premium n'apparaissent qu'une fois ouverts.
  const features = useLaunchFeatures();
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
  /** Bouton flottant « Affichage » : choix du contenu de la carte (activités, food trucks, Night, lieux). */
  const [displayPanelOpen, setDisplayPanelOpen] = useState(false);
  /** Un seul panneau ouvert à la fois. */
  const closePanels = useCallback(() => {
    setRadiusPanelOpen(false);
    setPeriodPanelOpen(false);
    setCategoryPanelOpen(false);
    setDisplayPanelOpen(false);
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
    // L'activité demandée doit apparaître, même hors de la période choisie (un lieu, lui, n'a pas de date).
    if (focus.focusId) setPeriod('all');
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

  // Contenu (issue #226), choisi par le bouton « Affichage » : « Activités », « Food trucks », « Night » (activités entre
  // 20 h et 6 h, thème nuit) ou « Lieux ». Partagé avec la liste (`layerStore`) ; choix mémorisé sur l'appareil,
  // sauf carte choisie dans les paramètres (utilisateur connecté), affichée à l'arrivée et à chacun de ses changements.
  const mapLayer = useMapLayer();
  const preferredLayer = preferredMapLayer(preferences?.defaultMapLayer);
  useEffect(() => {
    applyMapLayerPreference(preferredLayer);
  }, [preferredLayer]);
  /** Contenu choisi : les marqueurs changent sur place, sans changer d'écran, et le panneau se referme. */
  const selectMapLayer = useCallback(
    (layer: MapLayer) => {
      closePanels();
      chooseMapLayer(layer);
    },
    [closePanels],
  );
  // Contenu mémorisé mais pas (ou plus) ouvert au lancement : retour aux activités, sans effacer le choix enregistré.
  const shownLayer: MapLayer = layerShown(mapLayer, features);
  // Univers changé (ici ou depuis la liste) : l'aperçu d'un élément qui n'est plus affiché se ferme.
  const [selectionLayer, setSelectionLayer] = useState<MapLayer>(shownLayer);
  if (selectionLayer !== shownLayer) {
    setSelectionLayer(shownLayer);
    setSelection(null);
  }
  const contents = layerContents(shownLayer, features);
  const showNight = contents.night;
  const showActivities = contents.activities;
  const showPlaces = contents.places;
  const showFoodTrucks = contents.foodTrucks;
  const { activities: loadedActivities, foodTrucks, loading, error, tooWide, retry } = useMapData(
    region,
    categoryCodes,
    radiusSearch,
    period,
    features.foodTrucks,
    showNight,
  );
  // Filtre « Gratuit » : appliqué ici sur `priceType`, la carte recevant toutes les activités de la zone.
  const [freeOnly, setFreeOnly] = useState(false);
  const activities = useMemo(() => keepFreeActivities(loadedActivities, freeOnly), [loadedActivities, freeOnly]);
  // Activité demandée par « Voir sur la carte » : son aperçu s'ouvre dès qu'elle est chargée.
  useEffect(() => {
    if (pendingFocusId.current === null) return;
    const activity = loadedActivities.find((a) => a.id === pendingFocusId.current);
    if (!activity) return;
    pendingFocusId.current = null;
    select({ kind: 'activity', activity });
  }, [loadedActivities, select]);
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
      bounds && showActivities
        ? index.getClusters(
            [bounds.swLongitude, bounds.swLatitude, bounds.neLongitude, bounds.neLatitude],
            zoom,
          )
        : [],
    [index, bounds, zoom, showActivities],
  );

  // Filtre « type de cuisine » de la couche food trucks (`null` = toutes).
  const [cuisine, setCuisine] = useState<string | null>(null);
  const visibleFoodTrucks = useMemo(
    () =>
      bounds && showFoodTrucks
        ? foodTrucks.filter(
            (t) => pointInBounds(bounds, t.latitude, t.longitude) && (cuisine === null || t.cuisineType === cuisine),
          )
        : [],
    [foodTrucks, bounds, showFoodTrucks, cuisine],
  );

  const visiblePlaces = useMemo(
    () => (bounds && showPlaces ? places.filter((p) => pointInBounds(bounds, p.latitude, p.longitude)) : []),
    [places, bounds, showPlaces],
  );

  /**
   * Appui sur une pastille, comme sur le site web : l'aperçu fait défiler ses activités une à une, de la plus
   * proche dans le temps à la plus lointaine (« ‹ 2 / 4 › »). Une pastille de plus de 10 activités à des endroits
   * différents cadre plutôt toutes ses activités d'un coup, tant que le zoom peut encore les séparer.
   */
  const openCluster = useCallback(
    (clusterId: number) => {
      const leaves = sortByStartDate(
        index.getLeaves(clusterId, Infinity).map((feature) => (feature.properties as { activity: Activity }).activity),
      );
      if (leaves.length === 0) return;
      const target = regionToFit(leaves, region);
      const canZoom = target !== null && index.getClusterExpansionZoom(clusterId) <= CLUSTER_MAX_ZOOM;
      closePanels();
      if (target && clusterTapAction(leaves, canZoom) === 'zoom') {
        setSelection(null);
        mapRef.current?.animateToRegion(target, 300);
        return;
      }
      select({ kind: 'activity', activity: leaves[0], group: leaves });
    },
    [index, region, closePanels, select],
  );

  /** Activités de la zone affichée : avec les lieux et food trucks visibles, le nombre du bouton « Voir la liste ». */
  const zoneActivityCount = useMemo(
    () => (bounds && showActivities ? activities.filter((a) => pointInBounds(bounds, a.latitude, a.longitude)).length : 0),
    [activities, bounds, showActivities],
  );
  const resultCount = zoneActivityCount + visiblePlaces.length + visibleFoodTrucks.length;
  const highlighted = selection?.kind === 'activity' && showActivities ? selection.activity : null;

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

  // Rien à montrer dans la zone : message, avec de quoi changer les filtres s'il y en a.
  const isEmpty =
    showActivities &&
    !loading &&
    !error &&
    !tooWide &&
    clusters.length === 0 &&
    visiblePlaces.length === 0 &&
    visibleFoodTrucks.length === 0;
  const filtersActive = hasActiveFilters({
    period,
    categoryCount: categoryCodes.length,
    radiusKm: activeRadiusKm,
    freeOnly,
  });
  const resetFilters = useCallback(() => {
    closePanels();
    setPeriod('all');
    clearCategories();
    setRadius(null);
    setFreeOnly(false);
  }, [closePanels, setPeriod]);
  /** Liste dans le même univers, avec la même ville et les mêmes filtres (état partagé, rien à transmettre). */
  const openList = useCallback(() => {
    closePanels();
    router.navigate('/liste');
  }, [closePanels, router]);

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
        }}
        showsUserLocation={showUser}
        showsMyLocationButton={false}
        toolbarEnabled={false}
        moveOnMarkerPress={false}
        rotateEnabled={false}
        pitchEnabled={false}
        mapType={USE_OSM_TILES ? 'none' : 'standard'}
        // Fond de carte violet nuit (Android, Google Maps) ou sombre (iOS, Apple Plans) pour la carte Night
        // seulement : un style vide explicite le retire dès qu'on passe sur un autre onglet.
        customMapStyle={USE_OSM_TILES ? undefined : mapStyleFor(showNight)}
        userInterfaceStyle={showNight ? 'dark' : undefined}>
        {USE_OSM_TILES && <UrlTile urlTemplate={OSM_TILE_URL} maximumZ={19} zIndex={-1} />}
        {radiusSearch && (
          // Rayon favori, matérialisé comme sur le site web.
          <Circle
            center={{ latitude: radiusSearch.latitude, longitude: radiusSearch.longitude }}
            radius={radiusSearch.radiusKm * 1000}
            strokeColor={Brand.navy2}
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
                // `night` dans la clé : sur Android, un marqueur n'est redessiné qu'à sa création.
                key={`c-${clusterId}-${count}-${showNight}`}
                coordinate={coordinate}
                count={count}
                night={showNight}
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
              key={`a-${activity.id}-${showNight}`}
              coordinate={coordinate}
              title={activity.title}
              night={showNight}
              approximate={isApproximateLocation(activity)}
              onPress={() => {
                closePanels();
                // Même aperçu que les pastilles, avec les autres activités au même endroit (à 100 m près).
                select({ kind: 'activity', activity, group: activitiesAtSamePlace(activity, activities) });
              }}
            />
          );
        })}
        {highlighted && (
          <HighlightedActivityMarker
            key={`h-${highlighted.id}-${showNight}`}
            coordinate={{ latitude: highlighted.latitude, longitude: highlighted.longitude }}
            title={highlighted.title}
            night={showNight}
            onPress={() => {
              markerPressedAt.current = Date.now();
            }}
          />
        )}

        {visibleFoodTrucks.map((foodTruck) => (
          <FoodTruckMarker
            key={`t-${foodTruck.presenceId}`}
            coordinate={{ latitude: foodTruck.latitude, longitude: foodTruck.longitude }}
            name={foodTruck.name}
            emoji={markerOf(foodTruck).emoji}
            color={markerOf(foodTruck).color}
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

      {/* La barre d'onglets du bas réserve déjà la barre de gestes : seuls le haut et les côtés sont protégés ici. */}
      <SafeAreaView style={styles.overlay} edges={['top', 'left', 'right']} pointerEvents="box-none">
        <View style={styles.top} pointerEvents="box-none">
          {/* En haut : seulement le menu ☰ et la recherche, alignés. */}
          <View style={styles.headerRow} pointerEvents="box-none">
            <MenuButton floating />
            <View style={styles.search}>
              <CitySearch floating />
            </View>
          </View>
          {/* Filtres des activités (Activités, Night) : chacun affiche sa valeur et ouvre son panneau en bas de l'écran. */}
          {showActivities && (
            <MapChipRow accessibilityLabel="Filtres des activités">
              <MapChip
                small
                icon={ICONS.period}
                label={periodChipLabel(period)}
                accessibilityLabel={`Période : ${periodLabel}, modifier`}
                active={period !== 'all' || periodPanelOpen}
                onPress={() => {
                  const open = periodPanelOpen;
                  closePanels();
                  setPeriodPanelOpen(!open);
                }}
              />
              <MapChip
                small
                icon={ICONS.categories}
                label={categoriesChipLabel(categoryCodes.length)}
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
              <MapChip
                small
                icon={ICONS.radius}
                label={radiusChipLabel(activeRadiusKm)}
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
              <MapChip
                small
                label="Gratuit"
                accessibilityLabel={
                  freeOnly ? 'Activités gratuites seulement, tout afficher' : 'Afficher seulement les activités gratuites'
                }
                active={freeOnly}
                onPress={() => {
                  closePanels();
                  setFreeOnly(!freeOnly);
                }}
              />
              {filtersActive && (
                <MapChip small label="Réinitialiser" accessibilityLabel="Retirer tous les filtres" onPress={resetFilters} />
              )}
            </MapChipRow>
          )}
          {shownLayer === 'foodTrucks' && (
            <CuisineFilterBar
              floating
              foodTrucks={foodTrucks}
              selected={cuisine}
              onChange={(code) => {
                setCuisine(code);
                setSelection(null);
              }}
            />
          )}
          {features.premium && (
            <>
              {!selection && <FollowNewsBanner />}
              <FollowNewsPopup />
              <PremiumOfferPopup />
            </>
          )}
          {!place && !loading && !error && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="small">Vue d’ensemble : cherchez une ville ou touchez ◎ pour votre position.</ThemedText>
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
          {showActivities && tooWide && !loading && !error && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">🔍 Zoomez pour voir les activités.</ThemedText>
            </View>
          )}
          {isEmpty && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.banner, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">
                {freeOnly
                  ? 'Aucune activité gratuite dans cette zone.'
                  : period === 'all'
                    ? 'Aucune activité dans cette zone.'
                    : `Aucune activité ici pour « ${periodLabel} ».`}
              </ThemedText>
              {filtersActive && (
                <View style={styles.actions}>
                  <Button
                    label="Modifier les filtres"
                    onPress={() => {
                      closePanels();
                      if (period !== 'all') setPeriodPanelOpen(true);
                      else if (categoryCodes.length > 0) setCategoryPanelOpen(true);
                      else if (activeRadiusKm !== null) setRadiusPanelOpen(true);
                      else setFreeOnly(false);
                    }}
                  />
                  <Button label="Réinitialiser" variant="secondary" onPress={resetFilters} />
                </View>
              )}
            </View>
          )}
        </View>

        <View style={styles.bottom} pointerEvents="box-none">
          {/* Contrôles flottants, au-dessus de l'aperçu : se recentrer, changer l'affichage. */}
          <View style={styles.fabs} pointerEvents="box-none">
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Me recentrer sur ma position"
              accessibilityState={{ busy: location.status === 'locating' }}
              disabled={location.status === 'locating'}
              hitSlop={4}
              onPress={centerOnUser}
              style={({ pressed }) => [
                styles.roundButton,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                Shadows.floating,
                pressed && styles.pressed,
              ]}>
              {location.status === 'locating' ? (
                <ActivityIndicator size="small" color={theme.primary} />
              ) : (
                <SymbolView name={ICONS.locate} tintColor={theme.primary} size={22} />
              )}
            </Pressable>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="Changer l’affichage"
              accessibilityState={{ expanded: displayPanelOpen }}
              hitSlop={4}
              onPress={() => {
                const open = displayPanelOpen;
                closePanels();
                setDisplayPanelOpen(!open);
              }}
              style={({ pressed }) => [
                styles.roundButton,
                displayPanelOpen
                  ? { backgroundColor: theme.primary, borderColor: theme.primary }
                  : { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                Shadows.floating,
                pressed && styles.pressed,
              ]}>
              <SymbolView name={ICONS.display} tintColor={displayPanelOpen ? theme.onPrimary : theme.primary} size={22} />
            </Pressable>
          </View>
          {displayPanelOpen && (
            <View style={[styles.panel, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">Afficher sur la carte</ThemedText>
              {/* Un contenu à la fois : un appui change les marqueurs sur place et referme le panneau. */}
              <View style={styles.actions} accessibilityRole="tablist" accessibilityLabel="Contenu de la carte">
                {layerTabs(features).map((tab) => (
                  <MapChip
                    key={tab.value}
                    role="tab"
                    label={tab.label}
                    accessibilityLabel={tab.description}
                    icon={tab.value === 'night' ? undefined : LAYER_ICONS[tab.value]}
                    night={tab.value === 'night'}
                    active={shownLayer === tab.value}
                    onPress={() => selectMapLayer(tab.value)}
                  />
                ))}
              </View>
            </View>
          )}
          {!selection && categoryPanelOpen && (
            <View style={[styles.panel, { backgroundColor: theme.backgroundElement }]}>
              <CategoryPanel />
            </View>
          )}
          {!selection && radiusPanelOpen && (
            <View style={[styles.panel, { backgroundColor: theme.backgroundElement }]}>
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
            <View style={[styles.panel, { backgroundColor: theme.backgroundElement }]}>
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
          {selection && (
            <PreviewCard
              selection={selection}
              origin={place?.source === 'position' ? place : null}
              onClose={() => setSelection(null)}
              onChange={setSelection}
            />
          )}
          {/* La liste complète, dans le même univers, avec la même recherche et les mêmes filtres. */}
          <MapChip
            icon={ICONS.list}
            label={listButtonLabel(resultCount)}
            accessibilityLabel={`Voir la liste des résultats (${resultCount})`}
            onPress={openList}
            style={styles.listButton}
          />
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
  headerRow: {
    alignSelf: 'stretch',
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  roundButton: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: Radius.pill,
    borderWidth: StyleSheet.hairlineWidth,
  },
  pressed: {
    opacity: 0.85,
  },
  search: {
    flex: 1,
  },
  bottom: {
    justifyContent: 'flex-end',
    gap: Spacing.two,
    margin: Spacing.three,
    marginBottom: Spacing.two,
  },
  fabs: {
    alignSelf: 'flex-end',
    gap: Spacing.two,
  },
  listButton: {
    alignSelf: 'center',
  },
  panel: {
    gap: Spacing.two,
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
  banner: {
    alignSelf: 'stretch',
    gap: Spacing.three,
    padding: Spacing.three,
    borderRadius: Radius.lg,
    ...Shadows.floating,
  },
});
