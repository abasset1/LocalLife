import { usePathname, useRouter, type Href } from 'expo-router';
import { SymbolView, type SymbolViewProps } from 'expo-symbols';
import { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useAuth } from '@/auth';
import { useCurrentAccount, useOwnsFoodTrucks } from '@/features/pro/useProAccess';
import { BrandMark } from '@/components/brand-mark';
import { ThemedText } from '@/components/themed-text';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

import { isCurrentEntry, visibleSections, type MenuEntry } from './menuEntries';

const ICONS: Record<MenuEntry['icon'], SymbolViewProps['name']> = {
  map: { ios: 'map', android: 'map', web: 'map' },
  list: { ios: 'list.bullet.rectangle', android: 'view_list', web: 'view_list' },
  add: { ios: 'plus.circle', android: 'add_circle', web: 'add_circle' },
  account: { ios: 'person.crop.circle', android: 'account_circle', web: 'account_circle' },
  heart: { ios: 'heart', android: 'favorite', web: 'favorite' },
  edit: { ios: 'square.and.pencil', android: 'edit_note', web: 'edit_note' },
  bell: { ios: 'bell', android: 'notifications', web: 'notifications' },
  settings: { ios: 'slider.horizontal.3', android: 'tune', web: 'tune' },
  pro: { ios: 'briefcase', android: 'storefront', web: 'storefront' },
  star: { ios: 'star', android: 'star', web: 'star' },
};

/**
 * Menu rétractable (FRONT-REV-02, point C), à la place de la barre d'onglets : bouton « ☰ » en haut
 * des écrans principaux, qui ouvre un panneau latéral avec toutes les rubriques. Il se ferme par
 * « Fermer », un appui hors du panneau, le bouton retour d'Android ou le choix d'une rubrique ; il
 * ne reste donc jamais au-dessus de la recherche ou des résultats.
 */
export function MenuButton({ floating = false }: { /** Posé sur la carte : pastille ombrée. */ floating?: boolean }) {
  const theme = useTheme();
  const router = useRouter();
  const pathname = usePathname();
  const { state } = useAuth();
  const authenticated = state.status === 'authenticated';
  const [open, setOpen] = useState(false);
  // Relu à l'ouverture du menu : l'espace professionnel n'apparaît qu'avec un accès pro ; sans, un ancien compte Pro
  // garde l'accès à ses food trucks (consultation, archivage).
  const account = useCurrentAccount(open && authenticated);
  const proAccess = account?.hasProAccess === true;
  const ownsFoodTrucks = useOwnsFoodTrucks(open && account !== null && !proAccess);

  function go(entry: MenuEntry) {
    setOpen(false);
    if (isCurrentEntry(entry, pathname)) return;
    // Rubriques principales : changement de vue (l'état de chacune est conservé) ; autres : écran empilé.
    if (entry.main) router.navigate(entry.href as Href);
    else router.push(entry.href as Href);
  }

  return (
    <>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="Ouvrir le menu"
        accessibilityState={{ expanded: open }}
        hitSlop={4}
        onPress={() => setOpen(true)}
        style={({ pressed }) => [
          styles.button,
          { backgroundColor: theme.backgroundElement, borderColor: theme.border },
          floating && Shadows.floating,
          pressed && styles.pressed,
        ]}>
        <SymbolView
          name={{ ios: 'line.3.horizontal', android: 'menu', web: 'menu' }}
          tintColor={theme.primary}
          size={22}
        />
      </Pressable>

      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <View style={styles.overlay}>
          <SafeAreaView
            edges={['top', 'bottom', 'left']}
            style={[styles.panel, { backgroundColor: theme.background }]}
            accessibilityViewIsModal>
            <View style={styles.header}>
              <BrandMark />
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Fermer le menu"
                onPress={() => setOpen(false)}
                style={styles.close}>
                <ThemedText type="heading" themeColor="textSecondary">
                  ✕
                </ThemedText>
              </Pressable>
            </View>
            <ScrollView contentContainerStyle={styles.sections}>
              {visibleSections({ authenticated, proAccess, ownsFoodTrucks }).map((section) => (
                <View key={section.title} style={styles.section}>
                  <ThemedText type="eyebrow">{section.title}</ThemedText>
                  {section.entries.map((entry) => {
                    const current = isCurrentEntry(entry, pathname);
                    return (
                      <Pressable
                        key={entry.href}
                        accessibilityRole="button"
                        accessibilityLabel={entry.label}
                        accessibilityState={{ selected: current }}
                        onPress={() => go(entry)}
                        style={({ pressed }) => [
                          styles.entry,
                          current && { backgroundColor: theme.primarySoft },
                          pressed && { backgroundColor: theme.backgroundSelected },
                        ]}>
                        <SymbolView name={ICONS[entry.icon]} tintColor={theme.primary} size={22} />
                        <ThemedText type="smallBold" style={current ? { color: theme.primary } : undefined}>
                          {entry.label}
                        </ThemedText>
                      </Pressable>
                    );
                  })}
                </View>
              ))}
            </ScrollView>
          </SafeAreaView>
          {/* Appui hors du panneau : fermeture. */}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Fermer le menu"
            style={styles.backdrop}
            onPress={() => setOpen(false)}
          />
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  button: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: Radius.pill,
    borderWidth: StyleSheet.hairlineWidth,
  },
  pressed: {
    opacity: 0.85,
  },
  overlay: {
    flex: 1,
    flexDirection: 'row',
  },
  panel: {
    width: '82%',
    maxWidth: 340,
    ...Shadows.floating,
  },
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(23, 32, 29, 0.45)',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingLeft: Spacing.three,
    paddingVertical: Spacing.two,
  },
  close: {
    minWidth: 48,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sections: {
    gap: Spacing.four,
    padding: Spacing.three,
  },
  section: {
    gap: Spacing.one,
  },
  entry: {
    minHeight: 48,
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.two,
    borderRadius: Radius.md,
  },
});
