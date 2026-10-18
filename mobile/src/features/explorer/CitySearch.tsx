import { useState } from 'react';
import { ActivityIndicator, Keyboard, Pressable, StyleSheet, TextInput, View } from 'react-native';

import type { CitySuggestion } from '@/api';
import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { cityLabel, formatPostalCodes } from './citySearch';
import { setPlace, useExplorer } from './explorerStore';
import { useCitySuggestions } from './useCitySuggestions';

/**
 * Recherche de ville de l'explorateur (FRONT-REV-02, point A), sur la carte comme dans la liste :
 * communes proposées pendant la saisie (nom, codes postaux, département) ; en choisir une en fait le
 * lieu de recherche des deux vues. Chargement, panne (avec « Réessayer ») et absence de commune sont
 * annoncés. Le lieu choisi s'affiche dans le champ vide, et « ✕ » le retire.
 */
export function CitySearch({ floating = false }: { /** Posé sur la carte : fond opaque et ombre. */ floating?: boolean }) {
  const theme = useTheme();
  const { place } = useExplorer();
  const [query, setQuery] = useState('');
  const [focused, setFocused] = useState(false);
  // Liste liée à la saisie, pas au focus : sur certains appareils, toucher une commune retire le
  // focus du champ avant la fin de l'appui, ce qui refermait la liste et annulait le choix.
  const suggestions = useCitySuggestions(query);

  function choose(city: CitySuggestion) {
    setPlace({ label: cityLabel(city), latitude: city.latitude, longitude: city.longitude, source: 'city' });
    setQuery('');
    Keyboard.dismiss();
  }

  const placeholder = place ? `📍 ${place.label}` : 'Ville ou code postal';

  return (
    <View style={styles.wrapper}>
      <View
        style={[
          styles.field,
          { backgroundColor: theme.backgroundElement, borderColor: focused ? theme.primary : theme.border },
          floating && Shadows.floating,
        ]}>
        <ThemedText importantForAccessibility="no">🔍</ThemedText>
        <TextInput
          accessibilityLabel="Rechercher une ville"
          accessibilityHint={place ? `Lieu actuel : ${place.label}` : undefined}
          placeholder={placeholder}
          placeholderTextColor={place ? theme.text : theme.textSecondary}
          selectionColor={theme.primary}
          value={query}
          onChangeText={setQuery}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          onSubmitEditing={() => {
            if (suggestions.status === 'success' && suggestions.suggestions.length > 0) {
              choose(suggestions.suggestions[0]);
            }
          }}
          returnKeyType="search"
          autoCorrect={false}
          autoCapitalize="words"
          textContentType="addressCity"
          style={[styles.input, { color: theme.text }]}
        />
        {query ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Effacer la saisie"
            hitSlop={10}
            style={styles.clear}
            onPress={() => setQuery('')}>
            <ThemedText themeColor="textSecondary">✕</ThemedText>
          </Pressable>
        ) : place ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Retirer le lieu ${place.label}`}
            hitSlop={10}
            style={styles.clear}
            onPress={() => setPlace(null)}>
            <ThemedText themeColor="textSecondary">✕</ThemedText>
          </Pressable>
        ) : null}
      </View>

      {suggestions.status !== 'idle' && (
        <View
          accessibilityLiveRegion="polite"
          style={[styles.panel, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          {suggestions.status === 'loading' && (
            <View style={styles.row}>
              <ActivityIndicator size="small" color={theme.primary} />
              <ThemedText type="small">Recherche des communes…</ThemedText>
            </View>
          )}
          {suggestions.status === 'error' && (
            <View style={styles.message}>
              <ThemedText type="small" accessibilityRole="alert" style={{ color: theme.danger }}>
                {suggestions.message}
              </ThemedText>
              <Button label="Réessayer" variant="secondary" onPress={suggestions.retry} />
            </View>
          )}
          {suggestions.status === 'success' && suggestions.suggestions.length === 0 && (
            <ThemedText type="small" style={styles.message}>
              Aucune commune ne correspond à « {query.trim()} ». Vérifiez l’orthographe ou essayez un code postal.
            </ThemedText>
          )}
          {suggestions.status === 'success' &&
            suggestions.suggestions.map((city, index) => (
              <Pressable
                key={`${city.name}-${city.postalCodes[0] ?? index}`}
                accessibilityRole="button"
                accessibilityLabel={`${city.name}, ${formatPostalCodes(city.postalCodes, query)}`}
                onPress={() => choose(city)}
                style={({ pressed }) => [
                  styles.row,
                  styles.suggestion,
                  pressed && { backgroundColor: theme.backgroundSelected },
                ]}>
                <ThemedText importantForAccessibility="no">📍</ThemedText>
                <ThemedText type="smallBold" style={styles.name}>
                  {city.name}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {formatPostalCodes(city.postalCodes, query)}
                  {city.department ? ` · ${city.department}` : ''}
                </ThemedText>
              </Pressable>
            ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    gap: Spacing.one,
  },
  field: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingLeft: Spacing.three,
    paddingRight: Spacing.one,
    borderWidth: 1.5,
    borderRadius: Radius.pill,
  },
  input: {
    flex: 1,
    minHeight: 46,
    fontSize: 16,
  },
  clear: {
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  panel: {
    borderWidth: 1,
    borderRadius: Radius.lg,
    paddingVertical: Spacing.one,
    overflow: 'hidden',
    ...Shadows.floating,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.three,
    minHeight: 48,
  },
  suggestion: {
    paddingVertical: Spacing.two,
  },
  name: {
    flex: 1,
  },
  message: {
    gap: Spacing.two,
    padding: Spacing.three,
  },
});
