import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type NoticeTone = 'info' | 'success' | 'warning' | 'danger';

const ICONS: Record<NoticeTone, string> = {
  info: 'ℹ︎',
  success: '✓',
  warning: '!',
  danger: '!',
};

/**
 * Message encadré (information, succès, avertissement, erreur). Les erreurs sont annoncées comme
 * alertes, les autres messages comme région « polite ».
 */
export function Notice({ tone = 'info', children }: { tone?: NoticeTone; children: ReactNode }) {
  const theme = useTheme();
  const colors = {
    info: { background: theme.primarySoft, foreground: theme.primary },
    success: { background: theme.successSoft, foreground: theme.success },
    warning: { background: theme.warningSoft, foreground: theme.warning },
    danger: { background: theme.dangerSoft, foreground: theme.danger },
  }[tone];

  return (
    <View
      accessibilityRole={tone === 'danger' ? 'alert' : undefined}
      accessibilityLiveRegion={tone === 'danger' ? undefined : 'polite'}
      style={[styles.notice, { backgroundColor: colors.background }]}>
      <View style={[styles.badge, { backgroundColor: colors.foreground }]} importantForAccessibility="no">
        <ThemedText style={[styles.badgeText, { color: theme.backgroundElement }]}>{ICONS[tone]}</ThemedText>
      </View>
      <ThemedText type="small" style={[styles.text, tone === 'danger' && { color: theme.danger }]}>
        {children}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  notice: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two + Spacing.one,
    padding: Spacing.three,
    borderRadius: Radius.md,
  },
  badge: {
    width: 20,
    height: 20,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 1,
  },
  badgeText: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: 800,
  },
  text: {
    flex: 1,
  },
});
