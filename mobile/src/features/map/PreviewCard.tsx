import { useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { Linking, Pressable, StyleSheet, View } from 'react-native';

import type { Activity, FoodTruck, Place } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { isSafeWebUrl } from '@/features/activity/format';
import { buildDirectionsUrl, withApproximateMention } from '@/features/activity/location';
import { useTheme } from '@/hooks/use-theme';

import { RemoteImage } from '@/components/remote-image';
import { cuisineLabel, markerOf } from '@/features/foodtruck/cuisines';
import { photoUri } from '@/features/foodtruck/photo';
import { describeToday, spotAddressLine } from '@/features/foodtruck/presence';
import { useProAccess } from '@/features/pro/useProAccess';
import { FollowBell } from '@/features/follow';

import { positionInGroup, stepInGroup } from './activityGroups';
import { placeTypeLabel } from './placeTypes';
import { usePlaceActivities } from './usePlaceActivities';

/** Activités affichées dans l'aperçu d'un lieu ; la suite est résumée par un nombre. */
const PLACE_ACTIVITIES_SHOWN = 3;

export type Selection =
  /**
   * `group` : activités de la pastille ou du même endroit (à 100 m près), triées par date, que l'aperçu fait
   * défiler (« 2 / 4 ») comme la popup du site web. Absent ou d'une seule activité : pas de flèches.
   */
  | { kind: 'activity'; activity: Activity; group?: readonly Activity[] }
  | { kind: 'foodTruck'; foodTruck: FoodTruck }
  | { kind: 'place'; place: Place };

function formatDate(iso: string | null): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
}

/**
 * Consultation rapide d'un élément touché sur la carte. `onChange` reçoit l'activité affichée après un appui sur
 * « ‹ » ou « › » dans un groupe (la carte la met en évidence).
 */
export function PreviewCard({
  selection,
  onClose,
  onChange,
}: {
  selection: Selection;
  onClose: () => void;
  onChange?: (selection: Selection) => void;
}) {
  if (selection.kind === 'place') return <PlacePreview place={selection.place} onClose={onClose} />;
  return <ItemPreview selection={selection} onClose={onClose} onChange={onChange} />;
}

/** « ‹ 2 / 4 › » : passe d'une activité du groupe à l'autre, en boucle. */
function GroupNav({
  group,
  activity,
  onChange,
}: {
  group: readonly Activity[];
  activity: Activity;
  onChange: (selection: Selection) => void;
}) {
  const theme = useTheme();
  const move = (step: number) => onChange({ kind: 'activity', activity: stepInGroup(group, activity, step), group });
  return (
    <View style={styles.nav}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Activité précédente"
        hitSlop={8}
        onPress={() => move(-1)}
        style={({ pressed }) => [styles.navButton, { borderColor: theme.border }, pressed && styles.pressed]}>
        <ThemedText type="heading" themeColor="textSecondary">
          ‹
        </ThemedText>
      </Pressable>
      <ThemedText type="smallBold" accessibilityLiveRegion="polite">
        {positionInGroup(group, activity)}
      </ThemedText>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Activité suivante"
        hitSlop={8}
        onPress={() => move(1)}
        style={({ pressed }) => [styles.navButton, { borderColor: theme.border }, pressed && styles.pressed]}>
        <ThemedText type="heading" themeColor="textSecondary">
          ›
        </ThemedText>
      </Pressable>
    </View>
  );
}

/** Poignée, sur-titre (type d'élément), titre et bouton de fermeture de l'aperçu. */
function CardHeader({ eyebrow, title, onClose }: { eyebrow: string; title: string; onClose: () => void }) {
  const theme = useTheme();
  return (
    <>
      <View style={[styles.handle, { backgroundColor: theme.border }]} importantForAccessibility="no" />
      <View style={styles.header}>
        <View style={styles.title}>
          <ThemedText type="eyebrow" numberOfLines={1}>
            {eyebrow}
          </ThemedText>
          <ThemedText type="heading" numberOfLines={2}>
            {title}
          </ThemedText>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Fermer l'aperçu"
          hitSlop={12}
          onPress={onClose}
          style={[styles.close, { backgroundColor: theme.backgroundSelected }]}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            ✕
          </ThemedText>
        </Pressable>
      </View>
    </>
  );
}

/** Lieu permanent (couche « Lieux ») : pas de fiche dédiée, l'aperçu suffit. */
function PlacePreview({ place, onClose }: { place: Place; onClose: () => void }) {
  const theme = useTheme();
  const router = useRouter();
  const activities = usePlaceActivities(place.id);
  // Comptes avec accès pro seulement : demande de gestion de ce lieu, pré-sélectionné.
  const proAccess = useProAccess();
  const location = withApproximateMention(
    [place.address, [place.postalCode, place.city].filter(Boolean).join(' ')].filter(Boolean).join(', ') || null,
    place,
  );
  const directions = buildDirectionsUrl(place);
  return (
    <View style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
      <CardHeader eyebrow={`🏛 ${placeTypeLabel(place.placeType)}`} title={place.name} onClose={onClose} />
      {location ? (
        <ThemedText type="small" themeColor="textSecondary">
          📍 {location}
        </ThemedText>
      ) : null}
      {place.description ? (
        <ThemedText type="small" numberOfLines={3}>
          {place.description}
        </ThemedText>
      ) : null}
      <FollowBell type="place" id={place.id} title={place.name} city={place.city} />
      {activities && activities.length === 0 ? (
        <ThemedText type="small" themeColor="textSecondary">
          Aucune activité à venir connue ici.
        </ThemedText>
      ) : null}
      {activities && activities.length > 0 ? (
        <View style={[styles.activities, { backgroundColor: theme.background }]}>
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
                <ThemedText type="smallBold" themeColor="accent">
                  ›{' '}
                </ThemedText>
                {activity.title}
                {formatDate(activity.startDate) ? (
                  <ThemedText type="small" themeColor="textSecondary">
                    {` · ${formatDate(activity.startDate)}`}
                  </ThemedText>
                ) : null}
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
            icon={{ ios: 'arrow.triangle.turn.up.right.diamond', android: 'directions', web: 'directions' }}
            onPress={() => Linking.openURL(directions).catch(() => undefined)}
          />
        ) : null}
        {isSafeWebUrl(place.website) ? (
          <Button
            label="Site du lieu"
            variant="secondary"
            icon={{ ios: 'safari', android: 'language', web: 'language' }}
            onPress={() => WebBrowser.openBrowserAsync(place.website as string).catch(() => undefined)}
          />
        ) : null}
      </View>
      {proAccess ? (
        <Button
          label="Demander la gestion de ce lieu"
          variant="ghost"
          icon={{ ios: 'briefcase', android: 'storefront', web: 'storefront' }}
          onPress={() =>
            router.push({ pathname: '/pro/revendiquer', params: { placeId: String(place.id), placeName: place.name } })
          }
        />
      ) : null}
    </View>
  );
}

function ItemPreview({
  selection,
  onClose,
  onChange,
}: {
  selection: Exclude<Selection, { kind: 'place' }>;
  onClose: () => void;
  onChange?: (selection: Selection) => void;
}) {
  const theme = useTheme();
  const router = useRouter();

  const isActivity = selection.kind === 'activity';
  const title = isActivity ? selection.activity.title : selection.foodTruck.name;
  const category = isActivity ? selection.activity.category : selection.foodTruck.category;
  const place = isActivity ? withApproximateMention(selection.activity.city, selection.activity) : null;
  const date = isActivity ? formatDate(selection.activity.startDate) : null;
  const description = isActivity ? null : selection.foodTruck.description;
  // ADR-0013 : où et quand ce food truck est présent aujourd'hui.
  const presence = isActivity ? null : describeToday(selection.foodTruck);
  const contact = isActivity ? null : selection.foodTruck.contact;
  const address = isActivity ? null : spotAddressLine(selection.foodTruck);

  const cuisine = isActivity ? null : cuisineLabel(selection.foodTruck.cuisineType);
  const eyebrow = isActivity
    ? category
    : `${markerOf(selection.foodTruck).emoji} Food truck · ${[cuisine, category].filter(Boolean).join(' · ')}`;
  const photo = isActivity ? selection.activity.mainImageUrl : photoUri(selection.foodTruck.photoUrl);
  const meta = [place ? `📍 ${place}` : null, date ? `📅 ${date}` : null, presence ? `📍 ${presence}` : null]
    .filter(Boolean)
    .join('   ');
  const group = isActivity && selection.group && selection.group.length > 1 ? selection.group : null;

  return (
    <View style={[styles.card, { backgroundColor: theme.backgroundElement }]}>
      <CardHeader eyebrow={eyebrow} title={title} onClose={onClose} />
      <RemoteImage url={photo} style={styles.photo} />
      {meta ? (
        <ThemedText type="small" themeColor="textSecondary">
          {meta}
        </ThemedText>
      ) : null}
      {address ? (
        <ThemedText type="small" themeColor="textSecondary">
          🏠 {address}
        </ThemedText>
      ) : null}
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
        icon={{ ios: 'arrow.right', android: 'arrow_forward', web: 'arrow_forward' }}
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
      {group && isActivity && onChange ? (
        <GroupNav group={group} activity={selection.activity} onChange={onChange} />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: Radius.xl,
    padding: Spacing.three,
    paddingTop: Spacing.two,
    gap: Spacing.three - Spacing.one,
    ...Shadows.floating,
  },
  handle: {
    alignSelf: 'center',
    width: 36,
    height: 4,
    borderRadius: 2,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two,
  },
  title: {
    flex: 1,
    gap: Spacing.one,
  },
  photo: {
    height: 120,
    borderRadius: Radius.md,
  },
  activities: {
    gap: Spacing.one,
    padding: Spacing.three - Spacing.one,
    borderRadius: Radius.md,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  nav: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.three,
  },
  navButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
  close: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
