import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, RefreshControl, StyleSheet, View } from 'react-native';

import type { FollowedItem } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { followStatusLabel, useFollows } from '@/features/follow';
import { useCurrentAccount } from '@/features/pro/useProAccess';
import { useTheme } from '@/hooks/use-theme';

/**
 * « Activités et lieux suivis » (rubrique Compte) : ce que l'utilisateur suit avec la cloche, rechargé à chaque
 * ouverture. Toucher une activité ouvre sa fiche ; « Ne plus suivre » arrête les notifications. Réservé aux offres
 * Premium et Pro : un compte revenu au gratuit garde sa liste mais n'est plus prévenu.
 */
export default function SuivisScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const { follows, status, error, refresh, setFollowing } = useFollows();
  const [busyKey, setBusyKey] = useState<string | null>(null);
  const authenticated = state.status === 'authenticated';
  const canFollow = useCurrentAccount()?.limits?.canFollow;

  useFocusEffect(
    useCallback(() => {
      if (authenticated) void refresh();
    }, [authenticated, refresh]),
  );

  async function stop(item: FollowedItem) {
    const key = `${item.type}-${item.id}`;
    setBusyKey(key);
    const problem = await setFollowing(item, false);
    setBusyKey(null);
    if (problem) Alert.alert('Suivi non retiré', problem.message);
  }

  if (!authenticated) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText style={styles.centerText}>
          Connectez-vous depuis la rubrique Compte du menu pour retrouver vos suivis.
        </ThemedText>
      </ThemedView>
    );
  }

  const header = (
    <View style={styles.header}>
      <ThemedText themeColor="textSecondary">
        Vous êtes prévenu de leurs mises à jour : dates, lieu, annulation, nouvelles activités d’un lieu.
      </ThemedText>
      {canFollow === false && (
        <Notice tone="warning">
          Suivre une activité ou un lieu est réservé aux offres Premium et Pro. Vos suivis restent listés, mais vous
          n’êtes plus prévenu.
        </Notice>
      )}
      {canFollow === false && (
        <Button label="Voir les offres" variant="secondary" onPress={() => router.push('/abonnement')} />
      )}
      {status === 'error' && error && <Notice tone="danger">Erreur : {error}</Notice>}
    </View>
  );

  return (
    <ThemedView style={styles.container}>
      <FlatList
        data={follows}
        keyExtractor={(item) => `${item.type}-${item.id}`}
        contentContainerStyle={styles.content}
        ListHeaderComponent={header}
        refreshControl={
          <RefreshControl refreshing={status === 'loading' && follows.length > 0} onRefresh={() => void refresh()} />
        }
        ListEmptyComponent={
          status === 'loading' ? (
            <ActivityIndicator accessibilityLabel="Chargement des suivis" style={styles.loader} />
          ) : status === 'success' ? (
            <ThemedText themeColor="textSecondary" style={styles.centerText}>
              Rien pour l’instant : touchez 🔔 « Suivre » sur la fiche d’une activité ou l’aperçu d’un lieu.
            </ThemedText>
          ) : null
        }
        renderItem={({ item }) => {
          const label = followStatusLabel(item);
          const key = `${item.type}-${item.id}`;
          const openable = item.type === 'activity' && item.status !== 'DELETED';
          return (
            <Card style={styles.row}>
              <Pressable
                accessibilityRole={openable ? 'button' : undefined}
                accessibilityLabel={item.title ?? 'Sans titre'}
                disabled={!openable}
                onPress={() => router.push({ pathname: '/activite/[id]', params: { id: String(item.id) } })}
                style={styles.rowText}>
                <ThemedText type="smallBold" numberOfLines={2}>
                  {item.type === 'activity' ? '📅' : '🏛'} {item.title ?? 'Sans titre'}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {item.type === 'activity' ? 'Activité' : 'Lieu'}
                  {item.city ? ` · ${item.city}` : ''}
                  {label ? ` · ${label}` : ''}
                </ThemedText>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`Ne plus suivre « ${item.title ?? 'Sans titre'} »`}
                disabled={busyKey === key}
                hitSlop={6}
                onPress={() => void stop(item)}
                style={[styles.stop, { borderColor: theme.border }, busyKey === key && styles.busy]}>
                <ThemedText type="small" themeColor="primary">
                  Ne plus suivre
                </ThemedText>
              </Pressable>
            </Card>
          );
        }}
        ItemSeparatorComponent={Separator}
      />
    </ThemedView>
  );
}

function Separator() {
  return <View style={styles.separator} />;
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
  },
  centerText: {
    textAlign: 'center',
  },
  content: {
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  header: {
    gap: Spacing.three,
    marginBottom: Spacing.three,
  },
  loader: {
    marginTop: Spacing.four,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    padding: Spacing.three,
  },
  rowText: {
    flex: 1,
    gap: Spacing.half,
  },
  stop: {
    minHeight: 40,
    justifyContent: 'center',
    paddingHorizontal: Spacing.three,
    borderRadius: 999,
    borderWidth: 1,
  },
  busy: {
    opacity: 0.5,
  },
  separator: {
    height: Spacing.two,
  },
});
