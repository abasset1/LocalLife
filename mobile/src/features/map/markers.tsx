import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Marker } from 'react-native-maps';

import { Brand } from '@/constants/theme';

const ACTIVITY_COLOR = Brand.green2;
const FOOD_TRUCK_COLOR = Brand.coral;
const CLUSTER_COLOR = Brand.green;
const PLACE_COLOR = '#6B5CA5';

/** Ombre portée commune aux marqueurs, pour les détacher du fond de carte. */
const MARKER_SHADOW = {
  shadowColor: '#000000',
  shadowOpacity: 0.25,
  shadowRadius: 4,
  shadowOffset: { width: 0, height: 2 },
  elevation: 4,
} as const;

/**
 * Sur Android, un marqueur personnalisé doit être « suivi » le temps de
 * son premier rendu pour que sa vue soit rasterisée ; on coupe ensuite le
 * suivi pour ne pas re-rasteriser des dizaines de marqueurs à chaque frame.
 */
function useTracksViewChanges(): boolean {
  const [tracks, setTracks] = useState(true);
  useEffect(() => {
    const timer = setTimeout(() => setTracks(false), 400);
    return () => clearTimeout(timer);
  }, []);
  return tracks;
}

type Coordinate = { latitude: number; longitude: number };

/** Position approximative (une zone, pas le lieu) : marqueur atténué, comme sur le site web. */
export function ActivityMarker({
  coordinate,
  title,
  approximate = false,
  onPress,
}: {
  coordinate: Coordinate;
  title: string;
  approximate?: boolean;
  onPress: () => void;
}) {
  const tracks = useTracksViewChanges();
  return (
    <Marker
      coordinate={coordinate}
      onPress={onPress}
      tracksViewChanges={tracks}
      accessibilityLabel={`Activité : ${title}${approximate ? ' (emplacement approximatif)' : ''}`}>
      <View style={styles.markerPadding}>
        <View
          style={[styles.activity, { backgroundColor: ACTIVITY_COLOR }, approximate && styles.approximate]}>
          <View style={styles.activityDot} />
        </View>
      </View>
    </Marker>
  );
}

/** Losange violet avec pictogramme : forme distincte des activités et des food trucks. */
export function PlaceMarker({
  coordinate,
  name,
  onPress,
}: {
  coordinate: Coordinate;
  name: string;
  onPress: () => void;
}) {
  const tracks = useTracksViewChanges();
  return (
    <Marker
      coordinate={coordinate}
      onPress={onPress}
      tracksViewChanges={tracks}
      accessibilityLabel={`Lieu : ${name}`}>
      <View style={styles.markerPadding}>
        <View style={[styles.place, { backgroundColor: PLACE_COLOR }]}>
          <Text style={styles.placeIcon}>🏛</Text>
        </View>
      </View>
    </Marker>
  );
}

/** Carré orange avec camion : forme ET pictogramme distincts des activités (pas seulement la couleur). */
export function FoodTruckMarker({
  coordinate,
  name,
  onPress,
}: {
  coordinate: Coordinate;
  name: string;
  onPress: () => void;
}) {
  const tracks = useTracksViewChanges();
  return (
    <Marker
      coordinate={coordinate}
      onPress={onPress}
      tracksViewChanges={tracks}
      accessibilityLabel={`Food truck : ${name}`}>
      <View style={styles.markerPadding}>
        <View style={[styles.foodTruck, { backgroundColor: FOOD_TRUCK_COLOR }]}>
          <Text style={styles.foodTruckIcon}>🚚</Text>
        </View>
      </View>
    </Marker>
  );
}

export function ClusterMarker({
  coordinate,
  count,
  onPress,
}: {
  coordinate: Coordinate;
  count: number;
  onPress: () => void;
}) {
  const tracks = useTracksViewChanges();
  const size = count < 10 ? 40 : count < 100 ? 48 : 56;
  return (
    <Marker
      coordinate={coordinate}
      onPress={onPress}
      tracksViewChanges={tracks}
      accessibilityLabel={`Groupe de ${count} activités. Toucher pour zoomer.`}>
      <View style={styles.markerPadding}>
        <View
          style={[
            styles.cluster,
            { width: size, height: size, borderRadius: size / 2, backgroundColor: CLUSTER_COLOR },
          ]}>
          <Text style={styles.clusterText}>{count}</Text>
        </View>
      </View>
    </Marker>
  );
}

const styles = StyleSheet.create({
  // Marge autour du marqueur : sur Android, la vue rasterisée couperait sinon l'ombre.
  markerPadding: {
    padding: 4,
  },
  activity: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 3,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    ...MARKER_SHADOW,
  },
  activityDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#ffffff',
  },
  approximate: {
    opacity: 0.5,
    borderStyle: 'dashed',
  },
  place: {
    width: 26,
    height: 26,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    transform: [{ rotate: '45deg' }],
    ...MARKER_SHADOW,
  },
  placeIcon: {
    fontSize: 13,
    transform: [{ rotate: '-45deg' }],
  },
  foodTruck: {
    width: 36,
    height: 36,
    borderRadius: 10,
    borderWidth: 2.5,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    ...MARKER_SHADOW,
  },
  foodTruckIcon: {
    fontSize: 18,
  },
  cluster: {
    borderWidth: 4,
    borderColor: 'rgba(255, 255, 255, 0.9)',
    alignItems: 'center',
    justifyContent: 'center',
    ...MARKER_SHADOW,
  },
  clusterText: {
    color: '#ffffff',
    fontWeight: '800',
    fontSize: 15,
  },
});
