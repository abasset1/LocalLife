import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';
import MapView from 'react-native-maps';
import { SafeAreaView } from 'react-native-safe-area-context';
import Supercluster from 'supercluster';

import type { Activity } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { Region, pointInBounds, regionForRadius, regionForZoom, regionToBounds, zoomForRegion } from './geo';
import { ActivityMarker, ClusterMarker, FoodTruckMarker } from './markers';
import { PreviewCard, type Selection } from './PreviewCard';
import { useMapData } from './useMapData';
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

  const centeredPreferences = useRef<string | null>(null);
  useEffect(() => {
    if (!preferences || centeredPreferences.current === preferencesKey) return;
    centeredPreferences.current = preferencesKey;
    const { defaultLatitude, defaultLongitude, defaultRadiusKm } = preferences;
    if (defaultLatitude !== null && defaultLongitude !== null) {
      mapRef.current?.animateToRegion(
        regionForRadius(defaultLatitude, defaultLongitude, defaultRadiusKm ?? DEFAULT_PREFERENCE_RADIUS_KM),
        500,
      );
    }
  }, [preferences, preferencesKey]);

  const { activities, foodTrucks, loading, error, tooWide, retry } = useMapData(region, categoryCodes);
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
    mapRef.current?.animateToRegion(
      regionForZoom(position.latitude, position.longitude, USER_ZOOM, region),
      500,
    );
  }, [location, region]);

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
        pitchEnabled={false}>
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
      </MapView>

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
          {!selection && (
            <View
              style={[styles.locateWrapper, { backgroundColor: theme.background }]}
              pointerEvents="box-none">
              <Button
                label="Ma position"
                variant="secondary"
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
