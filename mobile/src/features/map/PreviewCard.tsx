import { useRouter } from 'expo-router';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import * as WebBrowser from 'expo-web-browser';
import { useMemo, useState, type ReactNode } from 'react';
import { Linking, PanResponder, Pressable, ScrollView, Share, StyleSheet, View } from 'react-native';

import type { Activity, FoodTruck, Place } from '@/api';
import { Button, type ButtonProps } from '@/components/button';
import { RemoteImage } from '@/components/remote-image';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { buildCalendarUrl, shareMessage } from '@/features/activity/calendar';
import { isSafeWebUrl } from '@/features/activity/format';
import { buildDirectionsUrl, withApproximateMention } from '@/features/activity/location';
import { LikeButton } from '@/features/favorites';
import { FollowBell } from '@/features/follow';
import { cuisineLabel, markerOf } from '@/features/foodtruck/cuisines';
import { photoUri } from '@/features/foodtruck/photo';
import { describeToday, spotAddressLine } from '@/features/foodtruck/presence';
import { useProAccess } from '@/features/pro/useProAccess';
import { useTheme } from '@/hooks/use-theme';

import { positionInGroup, stepInGroup } from './activityGroups';
import { placeTypeLabel } from './placeTypes';
import { formatWhen, placeWithDistance, sheetMoveFor } from './previewSheet';
import { usePlaceActivities } from './usePlaceActivities';

/** Activités affichées dans l'aperçu développé d'un lieu ; la suite est résumée par un nombre. */
const PLACE_ACTIVITIES_SHOWN = 3;

export type Selection =
  /**
   * `group` : activités de la pastille ou du même endroit (à 100 m près), triées par date, que l'aperçu fait
   * défiler (« 2 / 4 ») comme la popup du site web. Absent ou d'une seule activité : pas de flèches.
   */
  | { kind: 'activity'; activity: Activity; group?: readonly Activity[] }
  | { kind: 'foodTruck'; foodTruck: FoodTruck }
  | { kind: 'place'; place: Place };

type Icon = SymbolViewProps['name'];
const ICONS = {
  when: { ios: 'calendar', android: 'calendar_today', web: 'calendar_today' },
  where: { ios: 'mappin', android: 'location_on', web: 'location_on' },
  directions: { ios: 'arrow.triangle.turn.up.right.diamond', android: 'directions', web: 'directions' },
  calendar: { ios: 'calendar.badge.plus', android: 'event', web: 'event' },
  share: { ios: 'square.and.arrow.up', android: 'share', web: 'share' },
  open: { ios: 'arrow.right', android: 'arrow_forward', web: 'arrow_forward' },
  website: { ios: 'safari', android: 'language', web: 'language' },
  pro: { ios: 'briefcase', android: 'storefront', web: 'storefront' },
} satisfies Record<string, Icon>;

function openUrl(url: string | null) {
  if (url) Linking.openURL(url).catch(() => undefined);
}

/**
 * Aperçu de l'élément touché sur la carte (issue #226) : panneau du bas à trois positions. Réduit : vignette, nature,
 * nom, date, lieu et distance, favori, deux actions. Développé (poignée tirée vers le haut, ou touchée) : description
 * et le reste des actions. Poignée tirée vers le bas : réduit, puis fermé ; un appui sur la carte ferme aussi.
 *
 * `origin` : position de l'utilisateur quand la recherche part de là, pour la distance (jamais demandée ici).
 * `onChange` reçoit l'activité affichée après un appui sur « ‹ » ou « › » dans un groupe (la carte la met en
 * évidence).
 */
export function PreviewCard({
  selection,
  origin,
  onClose,
  onChange,
}: {
  selection: Selection;
  origin: { latitude: number; longitude: number } | null;
  onClose: () => void;
  onChange?: (selection: Selection) => void;
}) {
  const theme = useTheme();
  const [expanded, setExpanded] = useState(false);

  // Le geste est lu à la fin du glissement : un seul changement de position par geste.
  const pan = useMemo(
    () =>
      PanResponder.create({
        onMoveShouldSetPanResponder: (_event, gesture) => Math.abs(gesture.dy) > 8 && Math.abs(gesture.dy) > Math.abs(gesture.dx),
        onPanResponderRelease: (_event, gesture) => {
          const move = sheetMoveFor(gesture.dy, expanded);
          if (move === 'expand') setExpanded(true);
          else if (move === 'collapse') setExpanded(false);
          else if (move === 'close') onClose();
        },
      }),
    [expanded, onClose],
  );

  const content =
    selection.kind === 'place' ? (
      <PlaceContent place={selection.place} origin={origin} expanded={expanded} onExpand={() => setExpanded(true)} />
    ) : selection.kind === 'foodTruck' ? (
      <FoodTruckContent foodTruck={selection.foodTruck} origin={origin} expanded={expanded} />
    ) : (
      <ActivityContent selection={selection} origin={origin} expanded={expanded} onChange={onChange} />
    );

  return (
    <View
      style={[styles.sheet, expanded && styles.sheetExpanded, { backgroundColor: theme.backgroundElement }]}
      accessibilityLabel="Aperçu">
      <View {...pan.panHandlers} style={styles.grip}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={expanded ? 'Réduire l’aperçu' : 'Développer l’aperçu'}
          accessibilityState={{ expanded }}
          hitSlop={12}
          onPress={() => setExpanded(!expanded)}
          style={styles.handleArea}>
          <View style={[styles.handle, { backgroundColor: theme.border }]} />
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Fermer l’aperçu"
          hitSlop={12}
          onPress={onClose}
          style={[styles.close, { backgroundColor: theme.backgroundSelected }]}>
          <ThemedText type="smallBold" themeColor="textSecondary">
            ✕
          </ThemedText>
        </Pressable>
      </View>
      {expanded ? (
        <ScrollView contentContainerStyle={styles.body} showsVerticalScrollIndicator={false}>
          {content}
        </ScrollView>
      ) : (
        <View style={styles.body}>{content}</View>
      )}
    </View>
  );
}

/** Ligne vignette + texte + bouton à droite (favori, suivi), commune aux trois types d'élément. */
function Summary({
  photo,
  eyebrow,
  title,
  when,
  where,
  side,
  onOpen,
  openLabel,
}: {
  photo: string | null;
  /** Pastille au-dessus du titre ; absente si `null` (activité importée sans libellé de catégorie). */
  eyebrow: string | null;
  title: string;
  when: string | null;
  where: string | null;
  side?: ReactNode;
  onOpen: () => void;
  openLabel: string;
}) {
  const theme = useTheme();
  return (
    <View style={styles.summary}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={openLabel}
        onPress={onOpen}
        style={({ pressed }) => [styles.summaryMain, pressed && styles.pressed]}>
        <RemoteImage
          url={photo}
          style={[styles.thumb, { backgroundColor: theme.sand }]}
          fallback={<ThemedText style={styles.thumbFallback}>{(eyebrow || title).slice(0, 2)}</ThemedText>}
        />
        <View style={styles.summaryText}>
          {eyebrow ? (
            <View style={[styles.pill, { backgroundColor: theme.sand }]}>
              <ThemedText type="small" numberOfLines={1} style={[styles.pillText, { color: theme.primary }]}>
                {eyebrow}
              </ThemedText>
            </View>
          ) : null}
          <ThemedText type="heading" numberOfLines={2}>
            {title}
          </ThemedText>
          {when ? <InfoLine icon={ICONS.when} text={when} /> : null}
          {where ? <InfoLine icon={ICONS.where} text={where} /> : null}
        </View>
      </Pressable>
      {side ? <View style={styles.side}>{side}</View> : null}
    </View>
  );
}

function InfoLine({ icon, text }: { icon: Icon; text: string }) {
  const theme = useTheme();
  return (
    <View style={styles.infoLine}>
      <SymbolView name={icon} tintColor={theme.textSecondary} size={14} />
      <ThemedText type="small" themeColor="textSecondary" numberOfLines={1} style={styles.infoText}>
        {text}
      </ThemedText>
    </View>
  );
}

/** Rangée d'actions de même largeur. */
function Actions({ actions }: { actions: (ButtonProps | null)[] }) {
  const shown = actions.filter((action): action is ButtonProps => action !== null);
  if (shown.length === 0) return null;
  return (
    <View style={styles.actions}>
      {shown.map((action) => (
        <View key={action.label} style={styles.action}>
          <Button {...action} />
        </View>
      ))}
    </View>
  );
}

function ActivityContent({
  selection,
  origin,
  expanded,
  onChange,
}: {
  selection: Extract<Selection, { kind: 'activity' }>;
  origin: { latitude: number; longitude: number } | null;
  expanded: boolean;
  onChange?: (selection: Selection) => void;
}) {
  const router = useRouter();
  const { activity } = selection;
  const group = selection.group && selection.group.length > 1 ? selection.group : null;
  const when = formatWhen(activity.startDate, activity.endDate);
  const city = withApproximateMention(activity.city, activity);
  const address = [activity.address, [activity.postalCode, activity.city].filter(Boolean).join(' ')]
    .filter(Boolean)
    .join(', ');
  const directions = buildDirectionsUrl(activity);
  const calendarUrl = buildCalendarUrl({
    title: activity.title,
    description: activity.description,
    location: address || null,
    startDate: activity.startDate,
  });
  const open = () =>
    router.push({
      pathname: '/activite/[id]',
      params: { id: String(activity.id), ...(activity.sourceName ? { source: activity.sourceName } : {}) },
    });
  const share = () =>
    Share.share({
      title: activity.title,
      message: shareMessage({
        title: activity.title,
        date: when,
        place: city,
        url: isSafeWebUrl(activity.url) ? activity.url : null,
      }),
    }).catch(() => undefined);

  return (
    <>
      <Summary
        photo={activity.mainImageUrl}
        eyebrow={activity.category}
        title={activity.title}
        when={when}
        where={placeWithDistance(city, activity, origin)}
        side={<LikeButton activityId={activity.id} title={activity.title} />}
        onOpen={open}
        openLabel={`Voir la fiche de ${activity.title}`}
      />
      {expanded && activity.description ? (
        <ThemedText type="small" numberOfLines={6}>
          {activity.description}
        </ThemedText>
      ) : null}
      <Actions
        actions={[
          directions
            ? { label: 'Itinéraire', variant: 'secondary', icon: ICONS.directions, onPress: () => openUrl(directions) }
            : null,
          calendarUrl
            ? { label: 'Ajouter à mon agenda', icon: ICONS.calendar, onPress: () => openUrl(calendarUrl) }
            : { label: 'Voir la fiche', icon: ICONS.open, onPress: open },
        ]}
      />
      {expanded ? (
        <Actions
          actions={[
            { label: 'Partager', variant: 'secondary', icon: ICONS.share, onPress: share },
            calendarUrl ? { label: 'Voir la fiche complète', variant: 'secondary', icon: ICONS.open, onPress: open } : null,
          ]}
        />
      ) : null}
      {group && onChange ? <GroupNav group={group} activity={activity} onChange={onChange} /> : null}
    </>
  );
}

function FoodTruckContent({
  foodTruck,
  origin,
  expanded,
}: {
  foodTruck: FoodTruck;
  origin: { latitude: number; longitude: number } | null;
  expanded: boolean;
}) {
  const router = useRouter();
  const cuisine = cuisineLabel(foodTruck.cuisineType);
  const address = spotAddressLine(foodTruck);
  const directions = buildDirectionsUrl({
    latitude: foodTruck.latitude,
    longitude: foodTruck.longitude,
    address: foodTruck.spotAddress ?? null,
    city: null,
  });
  const open = () => router.push({ pathname: '/foodtruck/[id]', params: { id: String(foodTruck.id) } });
  return (
    <>
      <Summary
        photo={photoUri(foodTruck.photoUrl)}
        eyebrow={`${markerOf(foodTruck).emoji} Food truck${cuisine ? ` · ${cuisine}` : ''}`}
        title={foodTruck.name}
        when={describeToday(foodTruck)}
        where={placeWithDistance(address, foodTruck, origin)}
        onOpen={open}
        openLabel={`Voir la fiche du food truck ${foodTruck.name}`}
      />
      {expanded && foodTruck.description ? (
        <ThemedText type="small" numberOfLines={6}>
          {foodTruck.description}
        </ThemedText>
      ) : null}
      {expanded && foodTruck.contact ? (
        <ThemedText type="small" themeColor="textSecondary">
          Contact : {foodTruck.contact}
        </ThemedText>
      ) : null}
      <Actions
        actions={[
          directions
            ? { label: 'Itinéraire', variant: 'secondary', icon: ICONS.directions, onPress: () => openUrl(directions) }
            : null,
          { label: 'Voir la fiche', icon: ICONS.open, onPress: open },
        ]}
      />
    </>
  );
}

/** Lieu permanent (« Lieux ») : pas de fiche dédiée, l'aperçu développé en tient lieu. */
function PlaceContent({
  place,
  origin,
  expanded,
  onExpand,
}: {
  place: Place;
  origin: { latitude: number; longitude: number } | null;
  expanded: boolean;
  onExpand: () => void;
}) {
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
    <>
      <Summary
        photo={null}
        eyebrow={`🏛 ${placeTypeLabel(place.placeType)}`}
        title={place.name}
        when={null}
        where={placeWithDistance(expanded ? location : place.city, place, origin)}
        onOpen={onExpand}
        openLabel={`Plus d’informations sur ${place.name}`}
      />
      {expanded && place.description ? (
        <ThemedText type="small" numberOfLines={6}>
          {place.description}
        </ThemedText>
      ) : null}
      <Actions
        actions={[
          directions
            ? { label: 'Itinéraire', variant: 'secondary', icon: ICONS.directions, onPress: () => openUrl(directions) }
            : null,
          isSafeWebUrl(place.website)
            ? {
                label: 'Site du lieu',
                variant: 'secondary',
                icon: ICONS.website,
                onPress: () => WebBrowser.openBrowserAsync(place.website as string).catch(() => undefined),
              }
            : null,
        ]}
      />
      {expanded ? (
        <>
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
                    {formatWhen(activity.startDate, null) ? (
                      <ThemedText type="small" themeColor="textSecondary">
                        {` · ${formatWhen(activity.startDate, null)}`}
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
          {proAccess ? (
            <Button
              label="Demander la gestion de ce lieu"
              variant="ghost"
              icon={ICONS.pro}
              onPress={() =>
                router.push({ pathname: '/pro/revendiquer', params: { placeId: String(place.id), placeName: place.name } })
              }
            />
          ) : null}
        </>
      ) : null}
    </>
  );
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
        {positionInGroup(group, activity)} au même endroit
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

const styles = StyleSheet.create({
  sheet: {
    borderRadius: Radius.xl,
    paddingHorizontal: Spacing.three,
    paddingBottom: Spacing.three,
    ...Shadows.floating,
  },
  // Développé : il monte jusqu'à environ les deux tiers de l'écran et défile au-delà.
  sheetExpanded: {
    maxHeight: 460,
  },
  grip: {
    height: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  handleArea: {
    paddingHorizontal: Spacing.five,
    paddingVertical: Spacing.two,
  },
  handle: {
    width: 40,
    height: 5,
    borderRadius: 3,
  },
  close: {
    position: 'absolute',
    right: -Spacing.one,
    top: Spacing.two,
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  body: {
    gap: Spacing.three - Spacing.one,
  },
  summary: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two,
  },
  summaryMain: {
    flex: 1,
    flexDirection: 'row',
    gap: Spacing.three - Spacing.one,
  },
  thumb: {
    width: 84,
    height: 84,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  thumbFallback: {
    fontSize: 28,
    lineHeight: 34,
  },
  summaryText: {
    flex: 1,
    minWidth: 0,
    gap: Spacing.half + 1,
  },
  pill: {
    alignSelf: 'flex-start',
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.half,
    borderRadius: Radius.pill,
    maxWidth: '100%',
  },
  pillText: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: '700',
  },
  infoLine: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  infoText: {
    flexShrink: 1,
  },
  side: {
    paddingTop: Spacing.one,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  action: {
    flex: 1,
  },
  activities: {
    gap: Spacing.one,
    padding: Spacing.three - Spacing.one,
    borderRadius: Radius.md,
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
});
