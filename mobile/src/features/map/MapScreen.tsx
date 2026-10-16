import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, View } from 'react-native';
import MapView, { Circle } from 'react-native-maps';
import { SafeAreaView } from 'react-native-safe-area-context';
import Supercluster from 'supercluster';

import type { Activity } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Shadows, Spacing } from '@/constants/theme';
import { isApproximateLocation } from '@/features/activity/location';
import { ChipGroup, RADIUS_OPTIONS_KM } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

import { Region, pointInBounds, regionForRadius, regionForZoom, regionToBounds, zoomForRegion } from './geo';
import { MapControl } from './MapControl';
import { ActivityMarker, ClusterMarker, FoodTruckMarker, PlaceMarker } from './markers';
import { PlaceListPanel } from './PlaceListPanel';
import { PreviewCard, type Selection } from './PreviewCard';
import { useMapData, type RadiusSearch } from './useMapData';
import { loadShowPlaces, saveShowPlaces } from './placesPreference';
import { usePlaces } from './usePlaces';
import { useSearchPreferences } from './useSearchPreferences';
import { LOCATION_MESSAGES, useUserLocation } from './useUserLocation';

/** Zone de départ : Marseille, périmètre du MVP (voir `BOUNDING_BOX_SEARCH_CONTRACT.md`). */
const INITIAL_REGION: Region = {
  latitude: 43.2965,
  longitude: 5.3698,
  latitudeDelta: 0.12,
  longitudeDelta: 0.08,
};

/** Niveau de zoom après recentrage sur l'utilisateur (~ quartier, quelques centaines de mètres). */
const USER_ZOOM = 14;

/** Rayon de cadrage d'une ville par défaut sans rayon choisi (même valeur que le site web). */
const DEFAULT_PREFERENCE_RADIUS_KM = 50;

const NO_CATEGORIES: string[] = [];

/** Valeur de la puce « Sans rayon » du sélecteur de rayon. */
const NO_RADIUS = 'none';

/** Zoom après « Voir sur la carte » depuis une fiche (~ rue). */
const FOCUS_ZOOM = 16;

/** Au-delà de ce zoom, plus de regroupement : chaque activité a son marqueur. */
const CLUSTER_MAX_ZOOM = 17;

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

  // Paramètres de recherche (utilisateur connecté) : cadrage sur la ville et
  // le rayon par défaut, et filtre sur les catégories par défaut. Appliqués à
  // chaque changement de ces paramètres, pas à chaque retour sur la carte :
  // un déplacement fait par l'utilisateur n'est pas écrasé.
  const preferences = useSearchPreferences();
  const preferencesKey = preferences ? JSON.stringify(preferences) : null;
  // « Tout afficher » lève le filtre jusqu'au prochain changement des paramètres.
  const [clearedFilterKey, setClearedFilterKey] = useState<string | null>(null);
  const categoryCodes =
    preferences && clearedFilterKey !== preferencesKey ? preferences.defaultCategoryCodes : NO_CATEGORIES;

  // Centre courant, lu sans relancer le cadrage à chaque déplacement de la carte.
  const regionRef = useRef(region);
  useEffect(() => {
    regionRef.current = region;
  }, [region]);

  const [radiusPanelOpen, setRadiusPanelOpen] = useState(false);
  const [placeListOpen, setPlaceListOpen] = useState(false);

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
    setPlaceListOpen(false);
    setRadiusPanelOpen(false);
    moveTo(regionForZoom(latitude, longitude, FOCUS_ZOOM, regionRef.current));
    // Paramètres consommés : revenir sur l'onglet ne recentre pas une seconde fois.
    router.setParams({ focusId: undefined, focusLat: undefined, focusLon: undefined });
  }, [focus.focusId, focus.focusLat, focus.focusLon, moveTo, router]);

  const centeredPreferences = useRef<string | null>(null);
  useEffect(() => {
    if (!preferences || centeredPreferences.current === preferencesKey) return;
    const firstPreferences = centeredPreferences.current === null;
    centeredPreferences.current = preferencesKey;
    if (firstPreferences && focusedOnActivity.current) return;
    const { defaultLatitude, defaultLongitude, defaultRadiusKm } = preferences;
    if (defaultLatitude !== null && defaultLongitude !== null) {
      // Ville favorite : cadrée sur le rayon favori (ou 50 km).
      mapRef.current?.animateToRegion(
        regionForRadius(defaultLatitude, defaultLongitude, defaultRadiusKm ?? DEFAULT_PREFERENCE_RADIUS_KM),
        500,
      );
    } else if (defaultRadiusKm !== null) {
      // Rayon favori sans ville : cadré autour du centre actuel de la carte.
      const { latitude, longitude } = regionRef.current;
      mapRef.current?.animateToRegion(regionForRadius(latitude, longitude, defaultRadiusKm), 500);
    }
  }, [preferences, preferencesKey]);

  // Rayon favori : seules les activités dans ce rayon sont affichées, autour de
  // la ville favorite ou, après « Ma position », de la position de l'utilisateur
  // (jusqu'au prochain changement des paramètres).
  const [userCenter, setUserCenter] = useState<{ key: string | null; latitude: number; longitude: number } | null>(
    null,
  );
  // Rayon choisi depuis la carte (bouton « Rayon »), pour la session : remplace
  // le rayon favori jusqu'au prochain changement des paramètres ; `null` = sans rayon.
  const [radiusOverride, setRadiusOverride] = useState<{ key: string | null; radiusKm: number | null } | null>(
    null,
  );
  const activeRadiusKm =
    radiusOverride && radiusOverride.key === preferencesKey
      ? radiusOverride.radiusKm
      : (preferences?.defaultRadiusKm ?? null);

  const radiusSearch = useMemo<RadiusSearch | null>(() => {
    if (activeRadiusKm === null) return null;
    const center =
      userCenter && userCenter.key === preferencesKey
        ? userCenter
        : preferences && preferences.defaultLatitude !== null && preferences.defaultLongitude !== null
          ? { latitude: preferences.defaultLatitude, longitude: preferences.defaultLongitude }
          : null;
    return center ? { latitude: center.latitude, longitude: center.longitude, radiusKm: activeRadiusKm } : null;
  }, [activeRadiusKm, preferences, preferencesKey, userCenter]);

  /**
   * Rayon choisi depuis la carte : recherche et cadrage dans ce rayon autour de
   * la ville favorite ou de la position (ou, à défaut, du centre actuel de la
   * carte) ; « Sans rayon » revient à la recherche par zone visible.
   */
  const changeRadius = useCallback(
    (radiusKm: number | null) => {
      setRadiusPanelOpen(false);
      setRadiusOverride({ key: preferencesKey, radiusKm });
      if (radiusKm === null) return;
      const center = radiusSearch ?? { latitude: region.latitude, longitude: region.longitude };
      if (!radiusSearch) {
        setUserCenter({ key: preferencesKey, latitude: center.latitude, longitude: center.longitude });
      }
      mapRef.current?.animateToRegion(regionForRadius(center.latitude, center.longitude, radiusKm), 500);
    },
    [preferencesKey, radiusSearch, region],
  );

  const { activities, foodTrucks, loading, error, tooWide, retry } = useMapData(region, categoryCodes, radiusSearch);
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
  const [showUser, setShowUser] = useState(false);

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

  const zoomIntoCluster = useCallback(
    (clusterId: number, longitude: number, latitude: number) => {
      const target = index.getClusterExpansionZoom(clusterId);
      mapRef.current?.animateToRegion(regionForZoom(latitude, longitude, target, region), 300);
    },
    [index, region],
  );

  // Recentre la carte sur l'utilisateur : la zone visible change, et
  // `useMapData` recharge alors les activités autour de lui (within-bounds).
  const centerOnUser = useCallback(async () => {
    const position = await location.locate();
    if (!position) return;
    setShowUser(true);
    setSelection(null);
    if (activeRadiusKm !== null) {
      // Rayon actif : il suit l'utilisateur, et la carte est cadrée dessus.
      setUserCenter({ key: preferencesKey, latitude: position.latitude, longitude: position.longitude });
      mapRef.current?.animateToRegion(regionForRadius(position.latitude, position.longitude, activeRadiusKm), 500);
      return;
    }
    mapRef.current?.animateToRegion(
      regionForZoom(position.latitude, position.longitude, USER_ZOOM, region),
      500,
    );
  }, [location, region, activeRadiusKm, preferencesKey]);

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
        }}
        showsUserLocation={showUser}
        showsMyLocationButton={false}
        toolbarEnabled={false}
        moveOnMarkerPress={false}
        rotateEnabled={false}
        pitchEnabled={false}>
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
                  zoomIntoCluster(clusterId, longitude, latitude);
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

      {/* Le bas est déjà protégé par la barre d'onglets : seul le haut (encoche) est réservé. */}
      <SafeAreaView style={styles.overlay} edges={['top', 'left', 'right']} pointerEvents="box-none">
        <View style={styles.top} pointerEvents="box-none">
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
                onPress={() => setClearedFilterKey(preferencesKey)}
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
              <ThemedText type="smallBold">Aucune activité dans cette zone.</ThemedText>
            </View>
          )}
        </View>

        <View style={styles.bottom} pointerEvents="box-none">
          {!selection && placeListOpen && showPlaces && visiblePlaces.length > 0 && (
            <PlaceListPanel
              places={visiblePlaces}
              onSelect={(place) => {
                setPlaceListOpen(false);
                setSelection({ kind: 'place', place });
              }}
            />
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
          {!selection && (
            <View style={styles.bottomActions} pointerEvents="box-none">
              <View style={styles.pills} pointerEvents="box-none">
                <MapControl
                  icon={{ ios: 'building.columns', android: 'account_balance', web: 'account_balance' }}
                  label="Lieux"
                  accessibilityLabel={showPlaces ? 'Masquer les lieux' : 'Afficher les lieux'}
                  active={showPlaces}
                  onPress={toggleShowPlaces}
                />
                <MapControl
                  icon={{ ios: 'scope', android: 'radar', web: 'radar' }}
                  label={activeRadiusKm === null ? 'Rayon' : `${activeRadiusKm} km`}
                  accessibilityLabel={
                    activeRadiusKm === null ? 'Choisir un rayon' : `Rayon de ${activeRadiusKm} km, modifier`
                  }
                  active={activeRadiusKm !== null || radiusPanelOpen}
                  onPress={() => {
                    setPlaceListOpen(false);
                    setRadiusPanelOpen((open) => !open);
                  }}
                />
                {showPlaces && visiblePlaces.length > 0 && (
                  <MapControl
                    icon={{ ios: 'list.bullet', android: 'list', web: 'list' }}
                    label={placeListOpen ? 'Fermer' : `Liste (${visiblePlaces.length})`}
                    accessibilityLabel={
                      placeListOpen ? 'Fermer la liste' : `Liste des lieux (${visiblePlaces.length})`
                    }
                    active={placeListOpen}
                    onPress={() => {
                      setRadiusPanelOpen(false);
                      setPlaceListOpen((open) => !open);
                    }}
                  />
                )}
              </View>
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
  overlay: {
    flex: 1,
    justifyContent: 'space-between',
  },
  top: {
    alignItems: 'center',
    gap: Spacing.two,
    margin: Spacing.three,
  },
  bottom: {
    justifyContent: 'flex-end',
    margin: Spacing.three,
  },
  bottomActions: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  pills: {
    flex: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: Spacing.two,
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
