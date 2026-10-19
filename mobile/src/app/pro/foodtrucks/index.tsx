import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView } from 'react-native';

import { createFoodTruck, listMyFoodTrucks } from '@/api';
import type { OwnedFoodTruck } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { TRUCK_STATUS_LABELS } from '@/features/pro/format';
import { ProGate, isProDenied, messageFor, proStyles as styles } from '@/features/pro/ProGate';

const EMPTY = { name: '', category: '', description: '', contact: '' };

/**
 * Mes food trucks (PRO-01 lot C), comme sur le site : profil distinct des lieux, créé en brouillon ; ses emplacements
 * et son planning se gèrent sur la fiche de chaque food truck, qui le publie ensuite.
 */
export default function MyFoodTrucksScreen() {
  const router = useRouter();
  const [trucks, setTrucks] = useState<OwnedFoodTruck[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [creating, setCreating] = useState(false);
  const [profile, setProfile] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    const controller = new AbortController();
    listMyFoodTrucks({ signal: controller.signal })
      .then((found) => {
        setTrucks(found);
        setDenied(false);
      })
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        if (isProDenied(e)) setDenied(true);
        else setError(messageFor(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);
  useFocusEffect(load);

  async function create() {
    setBusy(true);
    setError(null);
    try {
      const created = await createFoodTruck(profile);
      setProfile(EMPTY);
      setCreating(false);
      router.push({ pathname: '/pro/foodtrucks/[id]', params: { id: String(created.id) } });
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <ProGate loading={loading} denied={denied}>
      <ThemedView style={styles.container}>
        <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            <ThemedText themeColor="textSecondary">
              Un food truck a sa propre fiche, distincte des lieux. Ses emplacements et son planning disent où et quand le
              trouver ; seuls les emplacements publiés et valables le jour même apparaissent sur la carte.
            </ThemedText>

            {creating ? (
              <Card style={styles.form}>
                <ThemedText type="heading">Créer un food truck</ThemedText>
                <TextField label="Nom commercial" value={profile.name} onChangeText={(v) => setProfile({ ...profile, name: v })} maxLength={255} />
                <TextField
                  label="Cuisine"
                  placeholder="Tacos, crêpes, burgers…"
                  value={profile.category}
                  onChangeText={(v) => setProfile({ ...profile, category: v })}
                  maxLength={255}
                />
                <TextField
                  label="Description"
                  value={profile.description}
                  onChangeText={(v) => setProfile({ ...profile, description: v })}
                  multiline
                  maxLength={2000}
                />
                <TextField
                  label="Contact ou lien utile"
                  placeholder="Téléphone, site, réseau social…"
                  value={profile.contact}
                  onChangeText={(v) => setProfile({ ...profile, contact: v })}
                  autoCapitalize="none"
                  maxLength={512}
                />
                <Button
                  label="Créer le brouillon"
                  loading={busy}
                  disabled={profile.name.trim() === '' || profile.category.trim() === ''}
                  onPress={() => void create()}
                />
                <Button label="Annuler" variant="ghost" onPress={() => setCreating(false)} />
              </Card>
            ) : (
              <Button label="Créer un food truck" onPress={() => setCreating(true)} />
            )}

            {trucks.length === 0 && !creating && (
              <ThemedText themeColor="textSecondary">Vous n’avez pas encore de food truck.</ThemedText>
            )}
            {trucks.map((truck) => (
              <Card key={truck.id} style={styles.card}>
                <ThemedText type="heading">🚚 {truck.name}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {TRUCK_STATUS_LABELS[truck.status]} · {truck.category}
                  {truck.suspendedByLocalLife ? ' · suspendu par LocalLife' : ''}
                </ThemedText>
                <Button
                  label="Gérer le profil et les emplacements"
                  variant="secondary"
                  onPress={() => router.push({ pathname: '/pro/foodtrucks/[id]', params: { id: String(truck.id) } })}
                />
              </Card>
            ))}
            {error && <Notice tone="danger">{error}</Notice>}
          </ScrollView>
        </KeyboardAvoidingView>
      </ThemedView>
    </ProGate>
  );
}
