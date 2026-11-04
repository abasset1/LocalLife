import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView } from 'react-native';

import { createFoodTruck, listMyFoodTrucks } from '@/api';
import type { OwnedFoodTruck } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { cuisineLabel, markerOf } from '@/features/foodtruck/cuisines';
import { truckStatusLabel } from '@/features/pro/format';
import {
  EMPTY_PROFILE,
  FoodTruckProfileFields,
  profileInputOf,
  savePhoto,
  type PhotoChange,
} from '@/features/pro/FoodTruckProfileFields';
import { ProGate, isProDenied, messageFor, proStyles as styles } from '@/features/pro/ProGate';
import { useProAccessEnded } from '@/features/pro/useProAccess';


/**
 * Mes food trucks (PRO-01 lot C), comme sur le site : profil distinct des lieux, créé en brouillon ; ses emplacements
 * et son planning se gèrent sur la fiche de chaque food truck, qui l'envoie en validation (première publication,
 * examinée par LocalLife) puis le publie. Sans accès pro, la liste reste visible pour archiver.
 */
export default function MyFoodTrucksScreen() {
  const router = useRouter();
  const [trucks, setTrucks] = useState<OwnedFoodTruck[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [creating, setCreating] = useState(false);
  const [profile, setProfile] = useState(EMPTY_PROFILE);
  const [photo, setPhoto] = useState<PhotoChange>({ kind: 'keep' });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Sans accès pro (ancien compte Pro), les food trucks restent consultables et archivables, rien de plus.
  const accessEnded = useProAccessEnded();

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
      const created = await createFoodTruck(profileInputOf(profile));
      setProfile(EMPTY_PROFILE);
      setCreating(false);
      try {
        await savePhoto(created.id, photo);
      } catch (e) {
        setError(`Food truck créé, mais pas la photo : ${messageFor(e)}`);
      }
      setPhoto({ kind: 'keep' });
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

            {accessEnded && (
              <Notice tone="warning">
                Votre accès professionnel a pris fin : vous pouvez consulter et archiver vos food trucks. Pour les
                modifier ou les publier à nouveau, reprenez l’offre Pro.
              </Notice>
            )}

            {accessEnded ? null : creating ? (
              <Card style={styles.form}>
                <ThemedText type="heading">Créer un food truck</ThemedText>
                <FoodTruckProfileFields
                  profile={profile}
                  onChange={setProfile}
                  currentPhotoUrl={null}
                  photo={photo}
                  onPhotoChange={setPhoto}
                  onError={setError}
                />
                <Button
                  label="Créer le brouillon"
                  loading={busy}
                  disabled={profile.name.trim() === '' || profile.category.trim() === '' || profile.cuisineType === ''}
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
                <ThemedText type="heading">
                  {markerOf(truck).emoji} {truck.name}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {truckStatusLabel(truck)} · {[cuisineLabel(truck.cuisineType), truck.category].filter(Boolean).join(' · ')}
                  {truck.suspendedByLocalLife ? ' · suspendu par LocalLife' : ''}
                </ThemedText>
                {truck.status === 'REJECTED' && truck.reviewReason ? (
                  <ThemedText type="small">Motif du refus : {truck.reviewReason}</ThemedText>
                ) : null}
                <Button
                  label={accessEnded ? 'Voir le food truck' : 'Gérer le profil et les emplacements'}
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
