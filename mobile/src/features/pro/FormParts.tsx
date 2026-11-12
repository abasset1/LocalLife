import type { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * Briques des formulaires de l'espace professionnel (food trucks, emplacements, lieux), comme sur le site : en-tête
 * avec pictogramme, sections titrées séparées, champs courts côte à côte, choix en cartes, étapes et boutons en bas.
 */

/** En-tête d'un formulaire : pictogramme sur pastille rose, titre et sous-titre. */
export function FormHeader({ icon, title, subtitle }: { icon: string; title: string; subtitle?: string }) {
  const theme = useTheme();
  return (
    <View style={[styles.header, { borderBottomColor: theme.border }]}>
      <View style={styles.headerIcon} importantForAccessibility="no">
        <ThemedText style={styles.headerEmoji}>{icon}</ThemedText>
      </View>
      <View style={styles.headerText}>
        <ThemedText type="heading" accessibilityRole="header">
          {title}
        </ThemedText>
        {subtitle ? (
          <ThemedText type="small" themeColor="textSecondary">
            {subtitle}
          </ThemedText>
        ) : null}
      </View>
    </View>
  );
}

/** Section titrée (« IDENTITÉ », « ADRESSE »…), séparée de la précédente par un filet. */
export function FormSection({ title, hint, children, first = false }: {
  title: string;
  hint?: string;
  children: ReactNode;
  /** Première section du formulaire : pas de filet au-dessus. */
  first?: boolean;
}) {
  const theme = useTheme();
  return (
    <View style={[styles.section, !first && [styles.sectionSeparated, { borderTopColor: theme.border }]]}>
      <View style={styles.sectionTitle}>
        <View style={[styles.sectionBar, { backgroundColor: theme.accent }]} />
        <ThemedText type="eyebrow" accessibilityRole="header">
          {title}
        </ThemedText>
      </View>
      {hint ? <Hint>{hint}</Hint> : null}
      {children}
    </View>
  );
}

/** Champs courts côte à côte (code postal et commune, arrivée et départ…). */
export function FieldRow({ children }: { children: ReactNode }) {
  return (
    <View style={styles.fieldRow}>
      {(Array.isArray(children) ? children : [children]).map((child, index) => (
        <View key={index} style={styles.fieldRowItem}>
          {child}
        </View>
      ))}
    </View>
  );
}

/** Texte d'aide discret sous un champ ou une section. */
export function Hint({ children }: { children: ReactNode }) {
  return (
    <ThemedText type="small" themeColor="textSecondary" style={styles.hint}>
      {children}
    </ThemedText>
  );
}

/** Boutons du bas d'un formulaire : action secondaire à gauche, principale à droite, au-dessus d'un filet. */
export function FormActions({ children }: { children: ReactNode }) {
  const theme = useTheme();
  return (
    <View style={[styles.actions, { borderTopColor: theme.border }]}>
      {(Array.isArray(children) ? children : [children]).filter(Boolean).map((child, index) => (
        <View key={index} style={styles.actionItem}>
          {child}
        </View>
      ))}
    </View>
  );
}

/** Choix exclusif présenté en cartes avec pastille radio (lieu existant / autre adresse, lieu trouvé). */
export function ChoiceCards<T extends string>({ label, options, selected, onSelect, columns = 2 }: {
  label: string;
  options: { value: T; label: string; description?: string }[];
  selected: T | null;
  onSelect: (value: T) => void;
  /** 1 : une carte par ligne (listes de résultats). */
  columns?: 1 | 2;
}) {
  const theme = useTheme();
  return (
    <View accessibilityRole="radiogroup" accessibilityLabel={label} style={[styles.choices, columns === 2 && styles.choicesRow]}>
      {options.map((option) => {
        const active = option.value === selected;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="radio"
            accessibilityState={{ selected: active }}
            accessibilityLabel={option.label}
            onPress={() => onSelect(option.value)}
            style={({ pressed }) => [
              styles.choice,
              columns === 2 && styles.choiceHalf,
              {
                borderColor: active ? theme.primary : theme.border,
                backgroundColor: active ? theme.backgroundSelected : theme.backgroundElement,
              },
              pressed && styles.pressed,
            ]}>
            <View style={[styles.radio, { borderColor: active ? theme.primary : theme.textSecondary }]}>
              {active ? <View style={[styles.radioDot, { backgroundColor: theme.primary }]} /> : null}
            </View>
            <View style={styles.choiceText}>
              <ThemedText type="smallBold">{option.label}</ThemedText>
              {option.description ? (
                <ThemedText type="small" themeColor="textSecondary">
                  {option.description}
                </ThemedText>
              ) : null}
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}

/** Étapes numérotées d'un parcours (créer un lieu) ; l'étape en cours est mise en avant. */
export function Steps({ steps, current }: { steps: string[]; current: number }) {
  const theme = useTheme();
  return (
    <View style={styles.steps} accessibilityLabel={`Étape ${current + 1} sur ${steps.length} : ${steps[current]}`}>
      {steps.map((step, index) => {
        const state = index === current ? 'current' : index < current ? 'done' : 'todo';
        return (
          <View key={step} style={styles.step} importantForAccessibility="no-hide-descendants">
            <View
              style={[
                styles.stepDot,
                state === 'current' && { backgroundColor: Brand.pink, borderColor: Brand.pink },
                state === 'done' && { backgroundColor: theme.backgroundSelected, borderColor: theme.primary },
                state === 'todo' && { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              ]}>
              <ThemedText
                type="smallBold"
                style={{ color: state === 'current' ? '#FFFFFF' : state === 'done' ? theme.primary : theme.textSecondary }}>
                {state === 'done' ? '✓' : index + 1}
              </ThemedText>
            </View>
            <ThemedText type="smallBold" themeColor={state === 'todo' ? 'textSecondary' : 'text'}>
              {step}
            </ThemedText>
            {index < steps.length - 1 ? <View style={[styles.stepLine, { backgroundColor: theme.border }]} /> : null}
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    paddingBottom: Spacing.three,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  headerIcon: {
    width: 46,
    height: 46,
    borderRadius: Radius.md,
    backgroundColor: Brand.pink,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerEmoji: {
    fontSize: 22,
    lineHeight: 28,
  },
  headerText: {
    flex: 1,
    gap: Spacing.half,
  },
  section: {
    gap: Spacing.three,
  },
  sectionSeparated: {
    paddingTop: Spacing.three + Spacing.one,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  sectionTitle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  sectionBar: {
    width: 16,
    height: 3,
    borderRadius: 2,
  },
  fieldRow: {
    flexDirection: 'row',
    gap: Spacing.two + Spacing.one,
  },
  fieldRowItem: {
    flex: 1,
    minWidth: 0,
  },
  hint: {
    marginTop: -Spacing.two,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.two + Spacing.one,
    paddingTop: Spacing.three,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  actionItem: {
    flex: 1,
  },
  choices: {
    gap: Spacing.two,
  },
  choicesRow: {
    flexDirection: 'row',
  },
  choice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two + Spacing.one,
    minHeight: 54,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
    borderWidth: 1.5,
    borderRadius: Radius.md,
  },
  choiceHalf: {
    flex: 1,
  },
  choiceText: {
    flex: 1,
    gap: Spacing.half,
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  pressed: {
    opacity: 0.85,
  },
  steps: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingBottom: Spacing.one,
  },
  step: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  stepDot: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepLine: {
    flex: 1,
    height: 1.5,
    marginRight: Spacing.two,
  },
});
