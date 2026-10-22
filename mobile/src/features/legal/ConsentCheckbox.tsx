import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { CONSENT_LABEL } from '@/auth/consent';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { LEGAL_PAGES, type LegalPage } from './legalLinks';
import { openLegalPage } from './openLegalPage';

/**
 * Case de consentement de l'inscription (contrat RGPD) : non cochée par défaut, zone tactile ≥ 48 px, suivie des
 * liens vers les deux textes (ouverts sur le site). Les liens sont des boutons distincts de la case, pour rester
 * accessibles séparément au lecteur d'écran.
 */
export function ConsentCheckbox({
  checked,
  onChange,
  disabled = false,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  disabled?: boolean;
}) {
  const theme = useTheme();
  const [linkError, setLinkError] = useState<string | null>(null);

  async function open(page: LegalPage) {
    setLinkError(await openLegalPage(page));
  }

  return (
    <View style={styles.group}>
      <Pressable
        accessibilityRole="checkbox"
        accessibilityLabel={CONSENT_LABEL}
        accessibilityState={{ checked, disabled }}
        disabled={disabled}
        onPress={() => onChange(!checked)}
        style={({ pressed }) => [styles.row, pressed && styles.pressed, disabled && styles.disabled]}>
        <View
          importantForAccessibility="no"
          style={[
            styles.box,
            { borderColor: checked ? theme.primary : theme.textSecondary },
            checked && { backgroundColor: theme.primary },
          ]}>
          {checked && <ThemedText style={[styles.check, { color: theme.onPrimary }]}>✓</ThemedText>}
        </View>
        <ThemedText type="small" style={styles.label}>
          {CONSENT_LABEL}
        </ThemedText>
      </Pressable>
      <View style={styles.links}>
        {(['terms', 'privacy'] as const).map((page) => (
          <Pressable
            key={page}
            accessibilityRole="link"
            accessibilityHint="Ouvre la page sur le site LocalLife"
            onPress={() => void open(page)}
            hitSlop={8}
            style={({ pressed }) => [styles.link, pressed && styles.pressed]}>
            <ThemedText type="linkPrimary" style={styles.linkText}>
              {LEGAL_PAGES[page].label}
            </ThemedText>
          </Pressable>
        ))}
      </View>
      {linkError && <Notice tone="danger">{linkError}</Notice>}
    </View>
  );
}

const styles = StyleSheet.create({
  group: {
    gap: Spacing.one,
  },
  row: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  box: {
    width: 24,
    height: 24,
    borderWidth: 2,
    borderRadius: 6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  check: {
    fontSize: 15,
    lineHeight: 18,
    fontWeight: 800,
  },
  label: {
    flex: 1,
  },
  links: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    columnGap: Spacing.four,
    rowGap: Spacing.one,
    paddingLeft: 24 + Spacing.three,
  },
  link: {
    minHeight: 32,
    justifyContent: 'center',
  },
  linkText: {
    fontSize: 14,
    lineHeight: 20,
  },
  pressed: {
    opacity: 0.7,
  },
  disabled: {
    opacity: 0.5,
  },
});
