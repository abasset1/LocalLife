import { useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { Linking, Pressable, StyleSheet, View } from 'react-native';

import type { Activity, FoodTruck, Place } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { isSafeWebUrl } from '@/features/activity/format';
import { buildDirectionsUrl, withApproximateMention } from '@/features/activity/location';
import { useTheme } from '@/hooks/use-theme';

import { placeTypeLabel } from './placeTypes';
import { usePlaceActivities } from './usePlaceActivities';

/** Activités affichées dans l'aperçu d'un lieu ; la suite est résumée par un nombre. */
const PLACE_ACTIVITIES_SHOWN = 3;

export type Selection =
  | { kind: 'activity'; activity: Activity }
  | { kind: 'foodTruck'; foodTruck: FoodTruck }
  | { kind: 'place'; place: Place };

function formatDate(iso: string | null): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

/** Consultation rapide d'un élément touché sur la carte. */
export function PreviewCard({ selection, onClose }: { selection: Selection; onClose: () => void }) {
  if (selection.kind === 'place') return <PlacePreview place={selection.place} onClose={onClose} />;
  return <ItemPreview selection={selection} onClose={onClose} />;
}

function CardHeader({ title, onClose }: { title: string; onClose: () => void }) {
  return (
    <View style={styles.header}>
      <ThemedText type="smallBold" style={styles.title} numberOfLines={2}>
        {title}
      </ThemedText>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Fermer l'aperçu"
        hitSlop={12}
        onPress={onClose}
        style={styles.close}>
        <ThemedText>✕</ThemedText>
      </Pressable>
    </View>
  );
}

/** Lieu permanent (couche « Lieux ») : pas de fiche dédiée, l'aperçu suffit. */
function PlacePreview({ place, onClose }: { place: Place; onClose: () => void }) {
  const theme = useTheme();
  const router = useRouter();
  const activities = usePlaceActivities(place.id);
  const location = withApproximateMention(
    [place.address, [place.postalCode, place.city].filter(Boolean).join(' ')].filter(Boolean).join(', ') || null,
    place,
  );
  const directions = buildDirectionsUrl(place);
  return (
    <View style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
      <CardHeader title={place.name} onClose={onClose} />
      <ThemedText type="small" themeColor="textSecondary">
        {placeTypeLabel(place.placeType)}
      </ThemedText>
      {location ? <ThemedText type="small">{location}</ThemedText> : null}
      {place.description ? (
        <ThemedText type="small" numberOfLines={3}>
          {place.description}
        </ThemedText>
      ) : null}
      {activities && activities.length === 0 ? (
        <ThemedText type="small" themeColor="textSecondary">
          Aucune activité à venir connue ici.
        </ThemedText>
      ) : null}
      {activities && activities.length > 0 ? (
        <View style={styles.activities}>
          <ThemedText type="smallBold">À venir</ThemedText>
          {activities.slice(0, PLACE_ACTIVITIES_SHOWN).map((activity) => (
            <Pressable
              key={activity.id}
              accessibilityRole="link"
              accessibilityLabel={`Voir l'activité ${activity.title}`}
              hitSlop={6}
              onPress={() =>
                router.push({
                  pathname: '/activite/[id]',
                  params: {
                    id: String(activity.id),
                    ...(activity.sourceName ? { source: activity.sourceName } : {}),
                  },
                })
              }>
              <ThemedText type="small" numberOfLines={1}>
                • {activity.title}
                {formatDate(activity.startDate) ? ` · ${formatDate(activity.startDate)}` : ''}
              </ThemedText>
            </Pressable>
          ))}
          {activities.length > PLACE_ACTIVITIES_SHOWN ? (
            <ThemedText type="small" themeColor="textSecondary">
              et {activities.length - PLACE_ACTIVITIES_SHOWN} autre
              {activities.length - PLACE_ACTIVITIES_SHOWN > 1 ? 's' : ''}
            </ThemedText>
          ) : null}
        </View>
      ) : null}
      <View style={styles.actions}>
        {directions ? (
          <Button
            label="Itinéraire"
            variant="secondary"
            onPress={() => Linking.openURL(directions).catch(() => undefined)}
          />
        ) : null}
        {isSafeWebUrl(place.website) ? (
          <Button
            label="Site du lieu"
            variant="secondary"
            onPress={() => WebBrowser.openBrowserAsync(place.website as string).catch(() => undefined)}
          />
        ) : null}
      </View>
    </View>
  );
}

function ItemPreview({
  selection,
  onClose,
}: {
  selection: Exclude<Selection, { kind: 'place' }>;
  onClose: () => void;
}) {
  const theme = useTheme();
  const router = useRouter();

  const isActivity = selection.kind === 'activity';
  const title = isActivity ? selection.activity.title : selection.foodTruck.name;
  const category = isActivity ? selection.activity.category : selection.foodTruck.category;
  const place = isActivity ? withApproximateMention(selection.activity.city, selection.activity) : null;
  const date = isActivity ? formatDate(selection.activity.startDate) : null;
  const description = isActivity ? null : selection.foodTruck.description;
  const contact = isActivity ? null : selection.foodTruck.contact;

  const meta = [isActivity ? category : `Food truck · ${category}`, place, date]
    .filter(Boolean)
    .join(' · ');

  return (
    <View style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
      <CardHeader title={title} onClose={onClose} />
      <ThemedText type="small" themeColor="textSecondary">
        {meta}
      </ThemedText>
      {description ? (
        <ThemedText type="small" numberOfLines={3}>
          {description}
        </ThemedText>
      ) : null}
      {contact ? (
        <ThemedText type="small" themeColor="textSecondary">
          Contact : {contact}
        </ThemedText>
      ) : null}
      <Button
        label={isActivity ? 'Voir la fiche' : 'Voir la fiche du food truck'}
        onPress={() =>
          isActivity
            ? router.push({
                pathname: '/activite/[id]',
                params: {
                  id: String(selection.activity.id),
                  ...(selection.activity.sourceName ? { source: selection.activity.sourceName } : {}),
                },
              })
            : router.push({
                pathname: '/foodtruck/[id]',
                params: { id: String(selection.foodTruck.id) },
              })
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two,
  },
  title: {
    flex: 1,
  },
  activities: {
    gap: Spacing.one,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  close: {
    minWidth: 32,
    minHeight: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
