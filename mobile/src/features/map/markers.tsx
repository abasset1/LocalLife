import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Marker } from 'react-native-maps';

import { Colors } from '@/constants/theme';

const ACTIVITY_COLOR = Colors.light.primary;
const FOOD_TRUCK_COLOR = '#C2410C';
const CLUSTER_COLOR = '#0F3D82';
const PLACE_COLOR = '#6D28D9';

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
      <View
        style={[styles.activity, { backgroundColor: ACTIVITY_COLOR }, approximate && styles.approximate]}
      />
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
      <View style={[styles.place, { backgroundColor: PLACE_COLOR }]}>
        <Text style={styles.placeIcon}>🏛</Text>
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
      <View style={[styles.foodTruck, { backgroundColor: FOOD_TRUCK_COLOR }]}>
        <Text style={styles.foodTruckIcon}>🚚</Text>
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
      <View
        style={[
          styles.cluster,
          { width: size, height: size, borderRadius: size / 2, backgroundColor: CLUSTER_COLOR },
        ]}>
        <Text style={styles.clusterText}>{count}</Text>
      </View>
    </Marker>
  );
}

const styles = StyleSheet.create({
  activity: {
    width: 26,
    height: 26,
    borderRadius: 13,
    borderWidth: 3,
    borderColor: '#ffffff',
  },
  approximate: {
    opacity: 0.5,
    borderStyle: 'dashed',
  },
  place: {
    width: 26,
    height: 26,
    borderRadius: 4,
    borderWidth: 2,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    transform: [{ rotate: '45deg' }],
  },
  placeIcon: {
    fontSize: 13,
    transform: [{ rotate: '-45deg' }],
  },
  foodTruck: {
    width: 34,
    height: 34,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  foodTruckIcon: {
    fontSize: 18,
  },
  cluster: {
    borderWidth: 3,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  clusterText: {
    color: '#ffffff',
    fontWeight: '700',
    fontSize: 15,
  },
});
