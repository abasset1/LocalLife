import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { Button } from '@/components/button';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { MAX_ALERT_KEYWORDS, addKeywords } from './keywords';

/**
 * Saisie des mots-clés d'une alerte : un champ (« Ajouter », la touche Entrée ou une virgule pour valider) et les
 * mots-clés en pastilles, chacune avec ✕ pour la retirer.
 */
export function KeywordsField({
  label,
  keywords,
  onChange,
  disabled = false,
}: {
  label: string;
  keywords: string[];
  onChange: (keywords: string[]) => void;
  disabled?: boolean;
}) {
  const theme = useTheme();
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);

  function commit(value: string = text) {
    if (value.trim() === '') return;
    const result = addKeywords(keywords, value);
    if ('error' in result) {
      setError(result.error);
      return;
    }
    setError(null);
    setText('');
    onChange(result.keywords);
  }

  return (
    <View style={styles.wrapper}>
      {keywords.length > 0 && (
        <View style={styles.chips}>
          {keywords.map((keyword) => (
            <View key={keyword} style={[styles.chip, { backgroundColor: theme.primary }]}>
              <ThemedText type="small" style={{ color: theme.onPrimary }}>
                {keyword}
              </ThemedText>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`Retirer le mot-clé ${keyword}`}
                disabled={disabled}
                hitSlop={8}
                onPress={() => onChange(keywords.filter((value) => value !== keyword))}>
                <ThemedText type="smallBold" style={{ color: theme.onPrimary }}>
                  ✕
                </ThemedText>
              </Pressable>
            </View>
          ))}
        </View>
      )}
      <TextField
        label={label}
        value={text}
        editable={!disabled && keywords.length < MAX_ALERT_KEYWORDS}
        placeholder="jazz, marché de Noël, randonnée…"
        autoCapitalize="none"
        returnKeyType="done"
        submitBehavior="submit"
        onChangeText={(value) => {
          setError(null);
          // Une virgule valide le mot-clé en cours.
          if (value.endsWith(',')) commit(value);
          else setText(value);
        }}
        onSubmitEditing={() => commit()}
      />
      <Button
        label="Ajouter le mot-clé"
        variant="secondary"
        disabled={disabled || text.trim() === ''}
        onPress={() => commit()}
      />
      <ThemedText type="small" themeColor="textSecondary">
        Une activité est retenue si l’un de ces mots apparaît dans son titre ou sa description (majuscules et accents
        ignorés). {keywords.length} / {MAX_ALERT_KEYWORDS}
      </ThemedText>
      {error && (
        <ThemedText type="small" accessibilityRole="alert" style={{ color: theme.danger }}>
          {error}
        </ThemedText>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    gap: Spacing.two,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingLeft: Spacing.three,
    paddingRight: Spacing.two,
    paddingVertical: Spacing.one,
    borderRadius: 999,
  },
});
