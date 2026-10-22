import { useState, type ComponentProps } from 'react';
import { StyleSheet } from 'react-native';

import { Card } from '@/components/card';
import { MenuRow } from '@/components/menu-row';
import { Notice } from '@/components/notice';

import { LEGAL_PAGES, type LegalPage } from './legalLinks';
import { openLegalPage } from './openLegalPage';

const ROWS: { page: LegalPage; icon: ComponentProps<typeof MenuRow>['icon'] }[] = [
  { page: 'privacy', icon: { ios: 'hand.raised', android: 'privacy_tip', web: 'privacy_tip' } },
  { page: 'terms', icon: { ios: 'doc.text', android: 'description', web: 'description' } },
  { page: 'legalNotice', icon: { ios: 'info.circle', android: 'info', web: 'info' } },
];

/** Liens vers les textes légaux, ouverts sur le site (même domaine que l'API). */
export function LegalLinks() {
  const [error, setError] = useState<string | null>(null);

  async function open(page: LegalPage) {
    setError(await openLegalPage(page));
  }

  return (
    <>
      <Card style={styles.menu}>
        {ROWS.map(({ page, icon }, index) => (
          <MenuRow
            key={page}
            icon={icon}
            label={LEGAL_PAGES[page].label}
            description="Sur le site LocalLife"
            onPress={() => void open(page)}
            last={index === ROWS.length - 1}
          />
        ))}
      </Card>
      {error && <Notice tone="danger">{error}</Notice>}
    </>
  );
}

const styles = StyleSheet.create({
  menu: {
    padding: 0,
    gap: 0,
    overflow: 'hidden',
  },
});
