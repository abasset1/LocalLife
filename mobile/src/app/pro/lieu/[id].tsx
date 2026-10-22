import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, ScrollView } from 'react-native';

import { getMyPlace, listPlaceChanges, proposePlaceChange, renouncePlace, withdrawPlaceChange } from '@/api';
import type { ManagedPlace, PlaceChange, PlaceChangeInput } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { placeTypeLabel } from '@/features/map/placeTypes';
import { PLACE_CHANGE_STATUS_LABELS, PLACE_STATUS_LABELS, formatDay } from '@/features/pro/format';
import { ProGate, isProDenied, messageFor, proStyles as styles } from '@/features/pro/ProGate';

/** « 10 rue X, 13001 Marseille », vide sans adresse ni commune. */
function formatAddress(value: { address: string | null; postalCode: string | null; city: string | null }): string {
  return [value.address, [value.postalCode, value.city].filter(Boolean).join(' ')].filter(Boolean).join(', ');
}

/** Formulaire pré-rempli avec la fiche actuelle du lieu. */
function formFrom(place: ManagedPlace): PlaceChangeInput {
  return {
    address: place.address ?? '',
    postalCode: place.postalCode ?? '',
    city: place.city ?? '',
    description: place.description ?? '',
    website: place.website ?? '',
    phone: place.phone ?? '',
    email: place.email ?? '',
  };
}

/**
 * Un lieu géré de l'espace professionnel (décisions du 22/10/2026), comme sur le site : proposer une modification
 * de sa fiche publiée (adresse, description, contacts — ni le nom ni la catégorie), examinée par LocalLife avant
 * d'apparaître ; suivre et retirer ses propositions ; ne plus gérer le lieu.
 */
export default function ManagedPlaceScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const placeId = Number(id);
  const router = useRouter();
  const [place, setPlace] = useState<ManagedPlace | null>(null);
  const [changes, setChanges] = useState<PlaceChange[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [form, setForm] = useState<PlaceChangeInput | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const found = await getMyPlace(placeId);
      setPlace(found);
      setChanges(found.changeProposable ? await listPlaceChanges(placeId) : []);
      setDenied(false);
    } catch (e) {
      if (isProDenied(e)) setDenied(true);
      else setError(messageFor(e));
    } finally {
      setLoading(false);
    }
  }, [placeId]);
  // Rechargé à chaque retour sur l'écran.
  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    setError(null);
    try {
      await action();
      setInfo(success);
      await load();
      return true;
    } catch (e) {
      setError(messageFor(e));
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function propose() {
    if (!form) return;
    const sent = await run(
      () => proposePlaceChange(placeId, form),
      'Proposition envoyée : LocalLife va l’examiner avant de mettre la fiche à jour.',
    );
    if (sent) setForm(null);
  }

  function confirmRenounce() {
    if (!place) return;
    Alert.alert(
      'Ne plus gérer ce lieu ?',
      `Vous ne pourrez plus proposer de modification de « ${place.name} » ; vos propositions en attente seront retirées. ` +
        'Pour le gérer à nouveau, il faudra refaire une demande.',
      [
        { text: 'Annuler', style: 'cancel' },
        {
          text: 'Ne plus gérer',
          style: 'destructive',
          onPress: () => {
            setBusy(true);
            setError(null);
            renouncePlace(place.id)
              .then(() => router.back())
              .catch((e: unknown) => setError(messageFor(e)))
              .finally(() => setBusy(false));
          },
        },
      ],
    );
  }

  const hasPending = changes.some((change) => change.status === 'PENDING');

  return (
    <ProGate loading={loading} denied={denied}>
      <ThemedView style={styles.container}>
        <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            {place && (
              <Card style={styles.card}>
                <ThemedText type="subtitle" accessibilityRole="header">
                  {place.name}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {PLACE_STATUS_LABELS[place.status]} · {placeTypeLabel(place.placeType)}
                  {place.origin === 'IMPORTED' ? ' · fiche importée' : ''}
                </ThemedText>
                {formatAddress(place) ? <ThemedText type="small">📍 {formatAddress(place)}</ThemedText> : null}
                {place.description ? <ThemedText type="small">{place.description}</ThemedText> : null}
              </Card>
            )}

            {place?.changeProposable && (
              <>
                {form ? (
                  <Card style={styles.form}>
                    <ThemedText type="heading">Proposer une modification</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      Le nom et la catégorie ne changent pas. LocalLife examine votre proposition avant de mettre la fiche à
                      jour{hasPending ? ' ; elle remplace celle déjà en attente' : ''}.
                    </ThemedText>
                    <TextField label="Adresse" value={form.address} onChangeText={(v) => setForm({ ...form, address: v })} />
                    <TextField
                      label="Code postal"
                      value={form.postalCode}
                      onChangeText={(v) => setForm({ ...form, postalCode: v })}
                      keyboardType="number-pad"
                      maxLength={5}
                    />
                    <TextField label="Commune" value={form.city} onChangeText={(v) => setForm({ ...form, city: v })} />
                    <TextField
                      label="Description"
                      value={form.description}
                      onChangeText={(v) => setForm({ ...form, description: v })}
                      multiline
                      maxLength={4000}
                    />
                    <TextField
                      label="Site web"
                      value={form.website}
                      onChangeText={(v) => setForm({ ...form, website: v })}
                      autoCapitalize="none"
                      keyboardType="url"
                    />
                    <TextField
                      label="Téléphone"
                      value={form.phone}
                      onChangeText={(v) => setForm({ ...form, phone: v })}
                      keyboardType="phone-pad"
                    />
                    <TextField
                      label="E-mail"
                      value={form.email}
                      onChangeText={(v) => setForm({ ...form, email: v })}
                      autoCapitalize="none"
                      keyboardType="email-address"
                    />
                    <Button
                      label="Envoyer la proposition"
                      loading={busy}
                      disabled={form.city.trim() === ''}
                      onPress={() => void propose()}
                    />
                    <Button label="Annuler" variant="ghost" onPress={() => setForm(null)} />
                  </Card>
                ) : (
                  place && (
                    <Button
                      label="Proposer une modification"
                      onPress={() => {
                        setForm(formFrom(place));
                        setInfo(null);
                        setError(null);
                      }}
                    />
                  )
                )}

                <ThemedText type="subtitle" accessibilityRole="header">
                  Mes propositions
                </ThemedText>
                {changes.length === 0 && (
                  <ThemedText themeColor="textSecondary">Aucune proposition pour l’instant.</ThemedText>
                )}
                {changes.map((change) => (
                  <Card key={change.id} style={styles.card}>
                    <ThemedText type="heading">{PLACE_CHANGE_STATUS_LABELS[change.status]}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      Envoyée le {formatDay(change.createdAt)}
                      {change.decidedAt ? ` · décision le ${formatDay(change.decidedAt)}` : ''}
                    </ThemedText>
                    {formatAddress(change) ? <ThemedText type="small">{formatAddress(change)}</ThemedText> : null}
                    {change.decisionReason ? (
                      <ThemedText type="small">Motif : {change.decisionReason}</ThemedText>
                    ) : null}
                    {change.status === 'PENDING' && (
                      <Button
                        label="Retirer"
                        variant="danger"
                        loading={busy}
                        onPress={() => void run(() => withdrawPlaceChange(change.id), 'Proposition retirée.')}
                      />
                    )}
                  </Card>
                ))}
              </>
            )}

            {info && <Notice tone="success">{info}</Notice>}
            {error && <Notice tone="danger">{error}</Notice>}

            {place && (
              <Button label="Ne plus gérer ce lieu" variant="danger" loading={busy} onPress={confirmRenounce} />
            )}
          </ScrollView>
        </KeyboardAvoidingView>
      </ThemedView>
    </ProGate>
  );
}
