import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import * as WebBrowser from 'expo-web-browser';
import { useState } from 'react';
import { Linking, Pressable, StyleSheet, View } from 'react-native';

import type { ActivityDetail } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import {
  accessibilityLabels,
  formatAgeRange,
  formatOccurrence,
  formatSchedule,
  linkAction,
  upcomingOccurrences,
} from './details';
import { formatPlace } from './format';

/** Dates affichées avant « Voir toutes les dates ». */
const OCCURRENCES_SHOWN = 4;

/** Action rapide de la fiche : pastille ronde avec icône, libellé dessous. */
export function QuickAction({
  icon,
  label,
  onPress,
}: {
  icon: SymbolViewProps['name'];
  label: string;
  onPress: () => void;
}) {
  const theme = useTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      style={({ pressed }) => [styles.quick, pressed && styles.pressed]}>
      <View style={[styles.quickIcon, { backgroundColor: theme.primarySoft }]}>
        <SymbolView name={icon} tintColor={theme.primary} size={22} />
      </View>
      <ThemedText type="small" style={styles.quickLabel} numberOfLines={2}>
        {label}
      </ThemedText>
    </Pressable>
  );
}

/** « Quand ? » : prochaines dates réelles et horaires réguliers. Rien si la fiche n'en a pas. */
export function WhenSection({ detail }: { detail: ActivityDetail }) {
  const [showAll, setShowAll] = useState(false);
  const occurrences = upcomingOccurrences(detail.occurrences);
  const schedules = detail.schedules.map(formatSchedule).filter((line): line is string => Boolean(line));
  if (occurrences.length === 0 && schedules.length === 0) return null;
  const shown = showAll ? occurrences : occurrences.slice(0, OCCURRENCES_SHOWN);

  return (
    <Card style={styles.section}>
      <ThemedText type="heading">Quand ?</ThemedText>
      {schedules.map((line) => (
        <Line key={line} icon="↻" text={line} />
      ))}
      {shown.map((occurrence) => (
        <Line key={occurrence.id} icon="•" text={formatOccurrence(occurrence)} />
      ))}
      {occurrences.length > OCCURRENCES_SHOWN && (
        <Button
          label={showAll ? 'Moins de dates' : `Voir les ${occurrences.length} dates`}
          variant="ghost"
          onPress={() => setShowAll((all) => !all)}
        />
      )}
    </Card>
  );
}

/** Infos pratiques : tarifs et conditions, âge, accessibilité, autres lieux, mots-clés, compléments. */
export function PracticalSection({ detail }: { detail: ActivityDetail }) {
  const theme = useTheme();
  const age = formatAgeRange(detail.ageMin, detail.ageMax);
  const accessibility = accessibilityLabels(detail.accessibility);
  const otherPlaces =
    detail.locations.length > 1
      ? detail.locations
          .map((l) => [l.name, formatPlace(l.address, l.postalCode, l.city)].filter(Boolean).join(' — '))
          .filter(Boolean)
      : [];
  const hasContent =
    detail.conditions || age || accessibility.length > 0 || otherPlaces.length > 0 || detail.tags.length > 0 || detail.additionalInfo;
  if (!hasContent) return null;

  return (
    <Card style={styles.section}>
      <ThemedText type="heading">Infos pratiques</ThemedText>
      {detail.conditions ? <Block title="Tarifs et conditions" text={detail.conditions} /> : null}
      {age ? <Block title="Âge" text={age} /> : null}
      {otherPlaces.length > 0 ? (
        <View style={styles.block}>
          <ThemedText type="eyebrow" themeColor="textSecondary">
            Lieux
          </ThemedText>
          {otherPlaces.map((place) => (
            <Line key={place} icon="📍" text={place} />
          ))}
        </View>
      ) : null}
      {accessibility.length > 0 ? (
        <View style={styles.block}>
          <ThemedText type="eyebrow" themeColor="textSecondary">
            Accessible
          </ThemedText>
          <View style={styles.chips}>
            {accessibility.map((label) => (
              <View key={label} style={[styles.chip, { backgroundColor: theme.successSoft }]}>
                <ThemedText type="smallBold" style={{ color: theme.success }}>
                  ♿︎ {label}
                </ThemedText>
              </View>
            ))}
          </View>
        </View>
      ) : null}
      {detail.additionalInfo ? <Block title="À savoir" text={detail.additionalInfo} /> : null}
      {detail.tags.length > 0 ? (
        <View style={styles.chips}>
          {detail.tags.map((tag) => (
            <View key={tag} style={[styles.chip, { backgroundColor: theme.backgroundSelected }]}>
              <ThemedText type="small" themeColor="textSecondary">
                #{tag}
              </ThemedText>
            </View>
          ))}
        </View>
      ) : null}
    </Card>
  );
}

/** Liens de la fiche (billetterie, téléphone, e-mail), hors site principal déjà proposé. */
export function LinksSection({ detail, mainUrl }: { detail: ActivityDetail; mainUrl: string | null }) {
  const actions = detail.links
    .map(linkAction)
    .filter((action): action is NonNullable<typeof action> => action !== null && action.url !== mainUrl);
  if (actions.length === 0) return null;
  return (
    <View style={styles.links}>
      {actions.map((action) => (
        <Button
          key={action.url}
          label={action.label}
          variant="secondary"
          icon={
            action.kind === 'phone'
              ? { ios: 'phone', android: 'call', web: 'call' }
              : action.kind === 'email'
                ? { ios: 'envelope', android: 'mail', web: 'mail' }
                : { ios: 'safari', android: 'language', web: 'language' }
          }
          onPress={() =>
            (action.kind === 'web' ? WebBrowser.openBrowserAsync(action.url) : Linking.openURL(action.url)).catch(
              () => undefined,
            )
          }
        />
      ))}
    </View>
  );
}

function Line({ icon, text }: { icon: string; text: string }) {
  return (
    <View style={styles.line}>
      <ThemedText themeColor="accent" style={styles.lineIcon} importantForAccessibility="no">
        {icon}
      </ThemedText>
      <ThemedText style={styles.lineText}>{text}</ThemedText>
    </View>
  );
}

function Block({ title, text }: { title: string; text: string }) {
  return (
    <View style={styles.block} accessible accessibilityLabel={`${title} : ${text}`}>
      <ThemedText type="eyebrow" themeColor="textSecondary">
        {title}
      </ThemedText>
      <ThemedText>{text}</ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  quick: {
    flex: 1,
    alignItems: 'center',
    gap: Spacing.one + Spacing.half,
    minHeight: 72,
  },
  quickIcon: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
  },
  quickLabel: {
    textAlign: 'center',
    fontSize: 12,
    lineHeight: 16,
  },
  pressed: {
    opacity: 0.7,
  },
  section: {
    gap: Spacing.three,
    padding: Spacing.four - Spacing.one,
  },
  line: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  lineIcon: {
    width: 18,
    textAlign: 'center',
  },
  lineText: {
    flex: 1,
  },
  block: {
    gap: Spacing.one,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  chip: {
    paddingHorizontal: Spacing.two + Spacing.one,
    paddingVertical: Spacing.one,
    borderRadius: Radius.pill,
  },
  links: {
    gap: Spacing.two,
  },
});
