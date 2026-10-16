import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { ActivityIndicator, FlatList, RefreshControl, StyleSheet, View } from 'react-native';

import type { Activity } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useFavorites } from '@/features/favorites';
import { ActivityCard } from '@/features/list/ActivityCard';
import { useTheme } from '@/hooks/use-theme';

/** « Mes favoris » (onglet Compte) : les activités aimées, rechargées à chaque ouverture de l'écran. */
export default function FavorisScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const { favorites, status, error, refresh } = useFavorites();
  const authenticated = state.status === 'authenticated';

  useFocusEffect(
    useCallback(() => {
      if (authenticated) void refresh();
    }, [authenticated, refresh]),
  );

  function open(activity: Activity) {
    router.push({
      pathname: '/activite/[id]',
      params: { id: String(activity.id), ...(activity.sourceName ? { source: activity.sourceName } : {}) },
    });
  }

  if (!authenticated) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText style={styles.centerText}>Connectez-vous depuis l’onglet Compte pour retrouver vos favoris.</ThemedText>
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <FlatList
        data={favorites}
        keyExtractor={(activity) => String(activity.id)}
        contentContainerStyle={styles.content}
        ItemSeparatorComponent={Separator}
        refreshControl={
          <RefreshControl
            refreshing={status === 'loading' && favorites.length > 0}
            onRefresh={() => void refresh()}
            tintColor={theme.primary}
            colors={[theme.primary]}
          />
        }
        ListHeaderComponent={
          error ? (
            <View style={styles.header}>
              <Notice tone="danger">Erreur : {error}</Notice>
              <Button label="Réessayer" onPress={() => void refresh()} />
            </View>
          ) : null
        }
        renderItem={({ item }) => <ActivityCard activity={item} onPress={() => open(item)} />}
        ListEmptyComponent={
          status === 'loading' ? (
            <View style={styles.loading}>
              <ActivityIndicator size="large" color={theme.primary} accessibilityLabel="Chargement de vos favoris" />
            </View>
          ) : error ? null : (
            <Card style={styles.empty}>
              <ThemedText style={styles.emptyIcon} importantForAccessibility="no">
                ♡
              </ThemedText>
              <ThemedText type="heading" style={styles.centerText}>
                Aucun favori pour l’instant
              </ThemedText>
              <ThemedText themeColor="textSecondary" style={styles.centerText}>
                Touchez le cœur d’une activité, sur la liste ou sur sa fiche, pour la retrouver ici.
              </ThemedText>
              <Button label="Parcourir la liste" onPress={() => router.navigate('/liste')} />
            </Card>
          )
        }
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
    padding: Spacing.three,
    paddingBottom: Spacing.five,
  },
  header: {
    gap: Spacing.two,
    marginBottom: Spacing.three,
  },
  separator: {
    height: Spacing.three,
  },
  loading: {
    paddingVertical: Spacing.six,
  },
  empty: {
    padding: Spacing.four,
    gap: Spacing.three,
  },
  emptyIcon: {
    fontSize: 40,
    lineHeight: 48,
    textAlign: 'center',
  },
});
