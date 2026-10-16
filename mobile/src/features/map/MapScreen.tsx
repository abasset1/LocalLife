import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import MapView, { Circle, UrlTile } from 'react-native-maps';
import { SafeAreaView } from 'react-native-safe-area-context';
import Supercluster from 'supercluster';

import type { Activity } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { isExpoGoAndroid } from '@/config/expoGo';
import { Spacing } from '@/constants/theme';
import { isApproximateLocation } from '@/features/activity/location';
import { ChipGroup, RADIUS_OPTIONS_KM } from '@/features/settings';
import { useTheme } from '@/hooks/use-theme';

import { Region, pointInBounds, regionForRadius, regionForZoom, regionToBounds, zoomForRegion } from './geo';
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
  const [selection, setSelection] = useState<Selection | null>(null);

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

  const centeredPreferences = useRef<string | null>(null);
  useEffect(() => {
    if (!preferences || centeredPreferences.current === preferencesKey) return;
    centeredPreferences.current = preferencesKey;
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
  const [radiusPanelOpen, setRadiusPanelOpen] = useState(false);
  const [placeListOpen, setPlaceListOpen] = useState(false);

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
        onPress={() => setSelection(null)}
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
            strokeColor="rgba(26, 95, 200, 0.8)"
            strokeWidth={1.5}
            lineDashPattern={[8, 8]}
            fillColor="rgba(26, 95, 200, 0.05)"
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
                onPress={() => zoomIntoCluster(clusterId, longitude, latitude)}
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
              onPress={() => setSelection({ kind: 'activity', activity })}
            />
          );
        })}

        {visibleFoodTrucks.map((foodTruck) => (
          <FoodTruckMarker
            key={`t-${foodTruck.id}`}
            coordinate={{ latitude: foodTruck.latitude, longitude: foodTruck.longitude }}
            name={foodTruck.name}
            onPress={() => setSelection({ kind: 'foodTruck', foodTruck })}
          />
        ))}

        {visiblePlaces.map((place) => (
          <PlaceMarker
            key={`p-${place.id}`}
            coordinate={{ latitude: place.latitude, longitude: place.longitude }}
            name={place.name}
            onPress={() => setSelection({ kind: 'place', place })}
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

      <SafeAreaView style={styles.overlay} pointerEvents="box-none">
        <View style={styles.top} pointerEvents="box-none">
          {loading && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ActivityIndicator size="small" />
              <ThemedText type="small">Chargement…</ThemedText>
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
            <View style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="small">
                {categoryCodes.length === 1
                  ? 'Filtré sur 1 catégorie'
                  : `Filtré sur ${categoryCodes.length} catégories`}
              </ThemedText>
              <Button label="Tout afficher" variant="secondary" onPress={() => setClearedFilterKey(preferencesKey)} />
            </View>
          )}
          {tooWide && !loading && !error && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="small">Zoomez pour voir les activités.</ThemedText>
            </View>
          )}
          {isEmpty && (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.chip, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="small">Aucune activité dans cette zone.</ThemedText>
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
            <View style={[styles.radiusPanel, { backgroundColor: theme.background }]}>
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
              {showPlaces && visiblePlaces.length > 0 && (
                <View style={[styles.locateWrapper, { backgroundColor: theme.background }]}>
                  <Button
                    label={placeListOpen ? 'Fermer la liste' : `Liste (${visiblePlaces.length})`}
                    variant="secondary"
                    onPress={() => {
                      setRadiusPanelOpen(false);
                      setPlaceListOpen((open) => !open);
                    }}
                  />
                </View>
              )}
              <View style={[styles.locateWrapper, { backgroundColor: theme.background }]}>
                <Button
                  label={showPlaces ? '🏛 Masquer les lieux' : '🏛 Lieux'}
                  variant="secondary"
                  onPress={toggleShowPlaces}
                />
              </View>
              <View style={[styles.locateWrapper, { backgroundColor: theme.background }]}>
                <Button
                  label={activeRadiusKm === null ? '◎ Rayon' : `◎ ${activeRadiusKm} km`}
                  variant="secondary"
                  onPress={() => {
                    setPlaceListOpen(false);
                    setRadiusPanelOpen((open) => !open);
                  }}
                />
              </View>
              <View style={[styles.locateWrapper, { backgroundColor: theme.background }]}>
                <Button
                  label="Ma position"
                  variant="secondary"
                  loading={location.status === 'locating'}
                  onPress={centerOnUser}
                />
              </View>
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
  bottom: {
    justifyContent: 'flex-end',
    margin: Spacing.three,
  },
  bottomActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'flex-end',
    gap: Spacing.two,
  },
  radiusPanel: {
    marginBottom: Spacing.two,
    padding: Spacing.three,
    borderRadius: 12,
  },
  locateWrapper: {
    alignSelf: 'flex-end',
    borderRadius: 8,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    borderRadius: 20,
  },
  banner: {
    alignSelf: 'stretch',
    gap: Spacing.two,
    padding: Spacing.three,
    borderRadius: 12,
  },
});
