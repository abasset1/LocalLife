import type { MapViewProps } from 'react-native-maps';

type MapStyleElement = NonNullable<MapViewProps['customMapStyle']>[number];

/**
 * Fond de carte du thème « Night » (Google Maps, Android) : violet nuit, comme les tuiles inversées et teintées du
 * site (`.leaflet-tile-pane` en thème Night). Sur iOS (Apple Plans), la carte passe en mode sombre
 * (`userInterfaceStyle`), qui n'accepte pas de style personnalisé.
 */
export const NIGHT_MAP_STYLE: MapStyleElement[] = [
  { elementType: 'geometry', stylers: [{ color: '#0b0518' }] },
  { elementType: 'labels.text.fill', stylers: [{ color: '#ab9fd0' }] },
  { elementType: 'labels.text.stroke', stylers: [{ color: '#0e061d' }] },
  { elementType: 'labels.icon', stylers: [{ visibility: 'off' }] },
  { featureType: 'administrative', elementType: 'geometry', stylers: [{ color: '#34245a' }] },
  { featureType: 'administrative.locality', elementType: 'labels.text.fill', stylers: [{ color: '#f3ecff' }] },
  { featureType: 'poi', stylers: [{ visibility: 'off' }] },
  { featureType: 'poi.park', elementType: 'geometry', stylers: [{ visibility: 'on' }, { color: '#150a2c' }] },
  { featureType: 'road', elementType: 'geometry', stylers: [{ color: '#2a1150' }] },
  { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#1d1038' }] },
  { featureType: 'road.highway', elementType: 'geometry', stylers: [{ color: '#4a1d7a' }] },
  { featureType: 'road.highway', elementType: 'labels.text.fill', stylers: [{ color: '#2de2ff' }] },
  { featureType: 'transit', stylers: [{ visibility: 'off' }] },
  { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#120a3a' }] },
  { featureType: 'water', elementType: 'labels.text.fill', stylers: [{ color: '#6f67d8' }] },
];

/**
 * Style à donner à la carte : le violet nuit pour la carte Night, sinon un style vide **explicite**. Sur Android,
 * `react-native-maps` ignore un style absent (`undefined`) et garde le dernier appliqué : sans ce `[]`, la carte
 * restait violette après être passé de Night à Activités, Lieux ou Food trucks.
 */
export function mapStyleFor(night: boolean): MapStyleElement[] {
  return night ? NIGHT_MAP_STYLE : [];
}
