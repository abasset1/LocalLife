import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ScrollView } from 'react-native';

import { listMyPlaces, submitPlace } from '@/api';
import type { ManagedPlace } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { MenuRow } from '@/components/menu-row';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { placeTypeLabel } from '@/features/map/placeTypes';
import { PLACE_STATUS_LABELS } from '@/features/pro/format';
import { ProGate, isProDenied, messageFor, proStyles as styles } from '@/features/pro/ProGate';

/**
 * Espace professionnel (ADR-0012), partagé avec le site web : trois parcours distincts — demander la gestion d'un
 * lieu existant, créer un lieu, gérer ses food trucks — et la liste des lieux gérés (chacun ouvre sa fiche : proposer
 * une modification, ne plus le gérer). Le backend vérifie les droits ;
 * sans offre Pro, l'écran invite à la demander.
 */
export default function ProHomeScreen() {
  const router = useRouter();
  const [places, setPlaces] = useState<ManagedPlace[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(() => {
    const controller = new AbortController();
    listMyPlaces({ signal: controller.signal })
      .then((found) => {
        setPlaces(found);
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

  async function submit(place: ManagedPlace) {
    setBusyId(place.id);
    setError(null);
    try {
      const updated = await submitPlace(place.id);
      setPlaces((current) => current.map((p) => (p.id === updated.id ? updated : p)));
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <ProGate loading={loading} denied={denied}>
      <ThemedView style={styles.container}>
        <ScrollView contentContainerStyle={styles.content}>
          <Card>
            <MenuRow
              icon={{ ios: 'building.2', android: 'store', web: 'store' }}
              label="Demander la gestion d’un lieu"
              description="Votre lieu est déjà sur LocalLife"
              onPress={() => router.push('/pro/revendiquer')}
            />
            <MenuRow
              icon={{ ios: 'plus.circle', android: 'add_business', web: 'add_business' }}
              label="Créer un lieu"
              description="Votre lieu n’existe pas encore"
              onPress={() => router.push('/pro/nouveau-lieu')}
            />
            <MenuRow
              icon={{ ios: 'truck.box', android: 'local_shipping', web: 'local_shipping' }}
              label="Mes food trucks"
              description="Profil, emplacements et planning"
              onPress={() => router.push('/pro/foodtrucks')}
              last
            />
          </Card>

          <ThemedText type="subtitle" accessibilityRole="header">
            Mes lieux
          </ThemedText>
          {places.length === 0 && (
            <ThemedText themeColor="textSecondary">
              Vous ne gérez encore aucun lieu : demandez la gestion d’un lieu existant ou créez le vôtre.
            </ThemedText>
          )}
          {places.map((place) => (
            <Card key={place.id} style={styles.card}>
              <ThemedText type="heading">{place.name}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {PLACE_STATUS_LABELS[place.status]} · {placeTypeLabel(place.placeType)}
                {place.city ? ` · ${place.city}` : ''}
                {place.origin === 'IMPORTED' ? ' · fiche importée' : ''}
              </ThemedText>
              {place.status === 'DRAFT' && (
                <Button
                  label="Envoyer à la modération"
                  loading={busyId === place.id}
                  onPress={() => void submit(place)}
                />
              )}
              <Button
                label={place.changeProposable ? 'Modifier la fiche, gérer le lieu' : 'Gérer ce lieu'}
                variant="secondary"
                onPress={() => router.push({ pathname: '/pro/lieu/[id]', params: { id: String(place.id) } })}
              />
            </Card>
          ))}
          {error && <Notice tone="danger">{error}</Notice>}
        </ScrollView>
      </ThemedView>
    </ProGate>
  );
}
