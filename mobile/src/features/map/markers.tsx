import { useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Marker } from 'react-native-maps';

import { Brand, NightNeon } from '@/constants/theme';

const ACTIVITY_COLOR = Brand.navy2;
const FOOD_TRUCK_COLOR = Brand.pink;
const CLUSTER_COLOR = Brand.navy;
const PLACE_COLOR = '#6B5CA5';
const HIGHLIGHT_COLOR = Brand.pink;

/** Halo néon des marqueurs du thème « Night », comme sur le site (`drop-shadow` magenta). */
const NIGHT_GLOW = {
  shadowColor: NightNeon.magenta,
  shadowOpacity: 0.9,
  shadowRadius: 8,
  shadowOffset: { width: 0, height: 0 },
  elevation: 6,
} as const;

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
  night = false,
  onPress,
}: {
  coordinate: Coordinate;
  title: string;
  approximate?: boolean;
  /** Thème « Night » : point magenta néon. */
  night?: boolean;
  onPress: () => void;
}) {
  const tracks = useTracksViewChanges();
  return (
    <Marker
      coordinate={coordinate}
      onPress={(event) => {
        // Évite que la carte reçoive aussi ce toucher et ferme l’aperçu (iOS).
        event.stopPropagation?.();
        onPress();
      }}
      tracksViewChanges={tracks}
      accessibilityLabel={`Activité : ${title}${approximate ? ' (emplacement approximatif)' : ''}`}>
      <View style={styles.markerPadding}>
        <View
          style={[
            styles.activity,
            { backgroundColor: night ? NightNeon.magenta : ACTIVITY_COLOR },
            night && NIGHT_GLOW,
            approximate && styles.approximate,
          ]}>
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
      onPress={(event) => {
        // Évite que la carte reçoive aussi ce toucher et ferme l’aperçu (iOS).
        event.stopPropagation?.();
        onPress();
      }}
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

/**
 * Carré avec pictogramme : forme ET pictogramme distincts des activités (pas seulement la couleur). Emoji et couleur
 * choisis par le propriétaire, sinon l'emoji du type de cuisine sur fond orange.
 */
export function FoodTruckMarker({
  coordinate,
  name,
  emoji = '🚚',
  color = FOOD_TRUCK_COLOR,
  onPress,
}: {
  coordinate: Coordinate;
  name: string;
  emoji?: string;
  color?: string;
  onPress: () => void;
}) {
  const tracks = useTracksViewChanges();
  return (
    <Marker
      coordinate={coordinate}
      onPress={(event) => {
        // Évite que la carte reçoive aussi ce toucher et ferme l’aperçu (iOS).
        event.stopPropagation?.();
        onPress();
      }}
      tracksViewChanges={tracks}
      accessibilityLabel={`Food truck : ${name}`}>
      <View style={styles.markerPadding}>
        <View style={[styles.foodTruck, { backgroundColor: color }]}>
          <Text style={styles.foodTruckIcon}>{emoji}</Text>
        </View>
      </View>
    </Marker>
  );
}

/**
 * Activité affichée dans l'aperçu (touchée, choisie dans la liste de la zone ou atteinte avec « ‹ › ») : point
 * agrandi en rose par-dessus les autres marqueurs, visible même quand elle est regroupée dans une pastille.
 */
export function HighlightedActivityMarker({
  coordinate,
  title,
  night = false,
  onPress,
}: {
  coordinate: Coordinate;
  title: string;
  /** Thème « Night » : point cyan néon. */
  night?: boolean;
  /** Appui sur le point : l'aperçu reste ouvert (la carte ne doit pas recevoir ce toucher). */
  onPress: () => void;
}) {
  const tracks = useTracksViewChanges();
  return (
    <Marker
      coordinate={coordinate}
      zIndex={1000}
      onPress={(event) => {
        event.stopPropagation?.();
        onPress();
      }}
      tracksViewChanges={tracks}
      accessibilityLabel={`Activité affichée : ${title}`}>
      <View style={styles.markerPadding}>
        <View
          style={[styles.highlighted, { backgroundColor: night ? NightNeon.cyan : HIGHLIGHT_COLOR }, night && NIGHT_GLOW]}>
          <View style={styles.highlightedDot} />
        </View>
      </View>
    </Marker>
  );
}

export function ClusterMarker({
  coordinate,
  count,
  night = false,
  onPress,
}: {
  coordinate: Coordinate;
  count: number;
  /** Thème « Night » : pastille magenta néon, nombre en violet nuit. */
  night?: boolean;
  onPress: () => void;
}) {
  const tracks = useTracksViewChanges();
  const size = count < 10 ? 40 : count < 100 ? 48 : 56;
  return (
    <Marker
      coordinate={coordinate}
      onPress={(event) => {
        // Évite que la carte reçoive aussi ce toucher et ferme l’aperçu (iOS).
        event.stopPropagation?.();
        onPress();
      }}
      tracksViewChanges={tracks}
      accessibilityLabel={`Groupe de ${count} activités. Toucher pour les parcourir ou zoomer.`}>
      <View style={styles.markerPadding}>
        <View
          style={[
            styles.cluster,
            { width: size, height: size, borderRadius: size / 2, backgroundColor: CLUSTER_COLOR },
            night && [styles.clusterNight, NIGHT_GLOW],
          ]}>
          <Text style={[styles.clusterText, night && { color: NightNeon.ink }]}>{count}</Text>
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
  highlighted: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 4,
    borderColor: '#ffffff',
    alignItems: 'center',
    justifyContent: 'center',
    ...MARKER_SHADOW,
  },
  highlightedDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
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
  clusterNight: {
    backgroundColor: NightNeon.magenta,
    borderColor: 'rgba(255, 63, 209, 0.35)',
  },
  clusterText: {
    color: '#ffffff',
    fontWeight: '800',
    fontSize: 15,
  },
});
