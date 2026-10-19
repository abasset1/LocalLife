import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, ScrollView } from 'react-native';

import {
  createPresence,
  findSimilarPlaces,
  foodTruckAction,
  getMyFoodTruck,
  listPresences,
  presenceAction,
  updateFoodTruck,
  updatePresence,
} from '@/api';
import type { ManagedPresence, OwnedFoodTruck, OwnerAction, PresenceInput, SimilarPlace, SpotKind } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { SPOT_KIND_LABELS, describeSchedule } from '@/features/foodtruck/presence';
import {
  DAY_OPTIONS,
  PRESENCE_STATUS_LABELS,
  TRUCK_STATUS_LABELS,
  formatDay,
  normalizeDate,
  normalizeTime,
  presenceActions,
  truckActions,
} from '@/features/pro/format';
import { ProGate, isProDenied, messageFor, proStyles as styles } from '@/features/pro/ProGate';
import { ChipGroup } from '@/features/settings';

const ACTION_LABELS: Record<OwnerAction, string> = { publish: 'Publier', suspend: 'Suspendre', archive: 'Archiver' };
const KIND_OPTIONS = (Object.keys(SPOT_KIND_LABELS) as SpotKind[]).map((value) => ({
  value,
  label: value === 'OTHER' ? 'Autre' : SPOT_KIND_LABELS[value],
}));

const EMPTY_FORM = {
  mode: 'place' as 'place' | 'new',
  placeId: null as number | null,
  placeLabel: '',
  spotName: '',
  address: '',
  postalCode: '',
  city: '',
  spotKind: 'MARKET' as SpotKind,
  days: [] as string[],
  startTime: '',
  endTime: '',
  validFrom: '',
  validUntil: '',
};

/**
 * Un food truck de l'espace professionnel (PRO-01 lot C, FOODTRUCK-01), comme sur le site : profil (publier,
 * suspendre, archiver), puis emplacements et planning — un lieu LocalLife existant ou une adresse, un type, des
 * jours, des horaires et une période. Brouillon d'abord, publication ensuite. Le backend refuse les chevauchements.
 */
export default function MyFoodTruckScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const truckId = Number(id);
  const [truck, setTruck] = useState<OwnedFoodTruck | null>(null);
  const [presences, setPresences] = useState<ManagedPresence[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [profile, setProfile] = useState({ name: '', category: '', description: '', contact: '' });
  const [editingProfile, setEditingProfile] = useState(false);
  const [editing, setEditing] = useState<ManagedPresence | 'new' | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [placeQuery, setPlaceQuery] = useState({ name: '', city: '' });
  const [places, setPlaces] = useState<SimilarPlace[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [found, foundPresences] = await Promise.all([getMyFoodTruck(truckId), listPresences(truckId)]);
      setTruck(found);
      setPresences(foundPresences);
      setDenied(false);
    } catch (e) {
      if (isProDenied(e)) setDenied(true);
      else setError(messageFor(e));
    } finally {
      setLoading(false);
    }
  }, [truckId]);
  // Rechargé à chaque retour sur l'écran.
  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  async function run(action: () => Promise<unknown>, success: string | null) {
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

  function actOnTruck(action: OwnerAction) {
    if (!truck) return;
    const go = () => void run(() => foodTruckAction(truck.id, action), null);
    if (action === 'archive') {
      Alert.alert('Archiver ?', `« ${truck.name} » ne pourra plus être republié.`, [
        { text: 'Annuler', style: 'cancel' },
        { text: 'Archiver', style: 'destructive', onPress: go },
      ]);
    } else {
      go();
    }
  }

  function startPresence(presence: ManagedPresence | 'new') {
    setEditing(presence);
    setPlaces(null);
    setForm(
      presence === 'new'
        ? EMPTY_FORM
        : {
            ...EMPTY_FORM,
            placeLabel: presence.spotName ?? '',
            spotKind: presence.spotKind,
            days: presence.daysOfWeek ? presence.daysOfWeek.split(',') : [],
            startTime: presence.startTime?.slice(0, 5) ?? '',
            endTime: presence.endTime?.slice(0, 5) ?? '',
            validFrom: presence.validFrom ?? '',
            validUntil: presence.validUntil ?? '',
          },
    );
    setInfo(null);
    setError(null);
  }

  async function searchPlaces() {
    try {
      const result = await findSimilarPlaces(placeQuery);
      setPlaces(result.candidates.filter((candidate) => candidate.status === 'PUBLISHED'));
    } catch (e) {
      setError(messageFor(e));
    }
  }

  async function savePresence() {
    const startTime = normalizeTime(form.startTime);
    const endTime = normalizeTime(form.endTime);
    const validFrom = normalizeDate(form.validFrom);
    const validUntil = normalizeDate(form.validUntil);
    if (startTime === undefined || endTime === undefined) {
      setError('Horaires au format HH:MM (ex. 08:00).');
      return;
    }
    if (validFrom === undefined || validUntil === undefined) {
      setError('Dates au format JJ/MM/AAAA.');
      return;
    }
    const input: PresenceInput = {
      spotKind: form.spotKind,
      days: form.days,
      startTime,
      endTime,
      validFrom,
      validUntil,
      ...(form.mode === 'new'
        ? {
            newSpot: {
              name: form.spotName,
              address: form.address,
              postalCode: form.postalCode,
              city: form.city,
              latitude: null,
              longitude: null,
            },
          }
        : form.placeId !== null
          ? { placeId: form.placeId }
          : {}),
    };
    const isNew = editing === 'new';
    const saved = await run(
      () => (isNew ? createPresence(truckId, input) : updatePresence((editing as ManagedPresence).id, input)),
      isNew ? 'Emplacement enregistré en brouillon : vérifiez-le, puis publiez-le.' : 'Emplacement modifié.',
    );
    if (saved) setEditing(null);
  }

  return (
    <ProGate loading={loading} denied={denied}>
      <ThemedView style={styles.container}>
        <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            {truck && (
              <Card style={styles.form}>
                <ThemedText type="subtitle" accessibilityRole="header">
                  🚚 {truck.name}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {TRUCK_STATUS_LABELS[truck.status]} · {truck.category}
                  {truck.suspendedByLocalLife ? ' · suspendu par LocalLife' : ''}
                </ThemedText>
                {editingProfile ? (
                  <>
                    <TextField label="Nom commercial" value={profile.name} onChangeText={(v) => setProfile({ ...profile, name: v })} />
                    <TextField label="Cuisine" value={profile.category} onChangeText={(v) => setProfile({ ...profile, category: v })} />
                    <TextField
                      label="Description"
                      value={profile.description}
                      onChangeText={(v) => setProfile({ ...profile, description: v })}
                      multiline
                    />
                    <TextField
                      label="Contact ou lien utile"
                      value={profile.contact}
                      onChangeText={(v) => setProfile({ ...profile, contact: v })}
                      autoCapitalize="none"
                    />
                    <Button
                      label="Enregistrer"
                      loading={busy}
                      onPress={() =>
                        void run(() => updateFoodTruck(truck.id, profile), 'Profil enregistré.').then((ok) => {
                          if (ok) setEditingProfile(false);
                        })
                      }
                    />
                    <Button label="Annuler" variant="ghost" onPress={() => setEditingProfile(false)} />
                  </>
                ) : (
                  <>
                    {truck.description ? <ThemedText>{truck.description}</ThemedText> : null}
                    {truck.status !== 'ARCHIVED' && (
                      <Button
                        label="Modifier le profil"
                        variant="secondary"
                        onPress={() => {
                          setProfile({
                            name: truck.name,
                            category: truck.category,
                            description: truck.description ?? '',
                            contact: truck.contact ?? '',
                          });
                          setEditingProfile(true);
                        }}
                      />
                    )}
                    {truckActions(truck.status, truck.suspendedByLocalLife).map((action) => (
                      <Button
                        key={action}
                        label={ACTION_LABELS[action]}
                        variant={action === 'archive' ? 'danger' : action === 'publish' ? 'primary' : 'secondary'}
                        loading={busy}
                        onPress={() => actOnTruck(action)}
                      />
                    ))}
                  </>
                )}
              </Card>
            )}

            <ThemedText type="subtitle" accessibilityRole="header">
              Emplacements et planning
            </ThemedText>
            <Notice tone="info">
              LocalLife n’accorde aucune autorisation de stationnement : vous restez responsable des autorisations
              nécessaires pour chaque emplacement.
            </Notice>
            {presences.length === 0 && <ThemedText themeColor="textSecondary">Aucun emplacement pour l’instant.</ThemedText>}
            {presences.map((presence) => (
              <Card key={presence.id} style={styles.card}>
                <ThemedText type="heading">{presence.spotName ?? 'Emplacement'}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {PRESENCE_STATUS_LABELS[presence.status]} · {describeSchedule(presence)}
                  {presence.validFrom ? ` · à partir du ${formatDay(presence.validFrom)}` : ''}
                  {presence.validUntil ? ` · jusqu’au ${formatDay(presence.validUntil)}` : ''}
                </ThemedText>
                {presence.status !== 'ARCHIVED' && (
                  <Button label="Modifier" variant="secondary" onPress={() => startPresence(presence)} />
                )}
                {presenceActions(presence.status).map((action) => (
                  <Button
                    key={action}
                    label={action === 'publish' ? (presence.status === 'SUSPENDED' ? 'Réactiver' : 'Publier')
                      : action === 'suspend' ? 'Désactiver' : 'Archiver'}
                    variant={action === 'archive' ? 'danger' : 'secondary'}
                    loading={busy}
                    onPress={() => void run(() => presenceAction(presence.id, action), null)}
                  />
                ))}
              </Card>
            ))}

            {editing ? (
              <Card style={styles.form}>
                <ThemedText type="heading">{editing === 'new' ? 'Nouvel emplacement' : 'Modifier l’emplacement'}</ThemedText>
                <ChipGroup
                  label="Où"
                  options={[
                    { value: 'place', label: editing === 'new' ? 'Un lieu LocalLife' : 'Garder ou choisir un lieu' },
                    { value: 'new', label: 'Une autre adresse' },
                  ]}
                  selected={[form.mode]}
                  onToggle={(value) => setForm({ ...form, mode: value as 'place' | 'new' })}
                  single
                />
                {form.mode === 'place' ? (
                  <>
                    {form.placeLabel ? <ThemedText>Emplacement choisi : {form.placeLabel}</ThemedText> : null}
                    <TextField
                      label="Nom du lieu"
                      placeholder="Marché, halle…"
                      value={placeQuery.name}
                      onChangeText={(v) => setPlaceQuery({ ...placeQuery, name: v })}
                    />
                    <TextField label="Commune" value={placeQuery.city} onChangeText={(v) => setPlaceQuery({ ...placeQuery, city: v })} />
                    <Button label="Rechercher un lieu" variant="secondary" onPress={() => void searchPlaces()} />
                    {places?.length === 0 && (
                      <ThemedText themeColor="textSecondary">Aucun lieu publié trouvé : saisissez plutôt l’adresse.</ThemedText>
                    )}
                    {places && places.length > 0 && (
                      <ChipGroup
                        label="Lieux trouvés"
                        options={places.map(({ place }) => ({
                          value: String(place.id),
                          label: place.city ? `${place.name} (${place.city})` : place.name,
                        }))}
                        selected={form.placeId === null ? [] : [String(form.placeId)]}
                        onToggle={(value) => {
                          const chosen = places.find(({ place }) => String(place.id) === value);
                          setForm({ ...form, placeId: Number(value), placeLabel: chosen?.place.name ?? '' });
                        }}
                        single
                      />
                    )}
                  </>
                ) : (
                  <>
                    <TextField label="Nom de l’emplacement" value={form.spotName} onChangeText={(v) => setForm({ ...form, spotName: v })} />
                    <TextField label="Adresse" value={form.address} onChangeText={(v) => setForm({ ...form, address: v })} />
                    <TextField
                      label="Code postal"
                      value={form.postalCode}
                      onChangeText={(v) => setForm({ ...form, postalCode: v })}
                      keyboardType="number-pad"
                      maxLength={5}
                    />
                    <TextField label="Commune" value={form.city} onChangeText={(v) => setForm({ ...form, city: v })} />
                  </>
                )}
                <ChipGroup
                  label="Type d’emplacement"
                  options={KIND_OPTIONS}
                  selected={[form.spotKind]}
                  onToggle={(value) => setForm({ ...form, spotKind: value as SpotKind })}
                  single
                />
                <ChipGroup
                  label="Jours (aucun : tous les jours)"
                  options={DAY_OPTIONS}
                  selected={form.days}
                  onToggle={(value) =>
                    setForm({
                      ...form,
                      days: form.days.includes(value) ? form.days.filter((d) => d !== value) : [...form.days, value],
                    })
                  }
                />
                <TextField
                  label="Arrivée (HH:MM)"
                  placeholder="08:00"
                  value={form.startTime}
                  onChangeText={(v) => setForm({ ...form, startTime: v })}
                  keyboardType="numbers-and-punctuation"
                />
                <TextField
                  label="Départ (HH:MM)"
                  placeholder="13:00"
                  value={form.endTime}
                  onChangeText={(v) => setForm({ ...form, endTime: v })}
                  keyboardType="numbers-and-punctuation"
                />
                <TextField
                  label="À partir du (facultatif, JJ/MM/AAAA)"
                  value={form.validFrom}
                  onChangeText={(v) => setForm({ ...form, validFrom: v })}
                  keyboardType="numbers-and-punctuation"
                />
                <TextField
                  label="Jusqu’au (facultatif, JJ/MM/AAAA)"
                  value={form.validUntil}
                  onChangeText={(v) => setForm({ ...form, validUntil: v })}
                  keyboardType="numbers-and-punctuation"
                />
                <Button label="Enregistrer en brouillon" loading={busy} onPress={() => void savePresence()} />
                <Button label="Annuler" variant="ghost" onPress={() => setEditing(null)} />
              </Card>
            ) : (
              truck?.status !== 'ARCHIVED' && <Button label="Ajouter un emplacement" onPress={() => startPresence('new')} />
            )}

            {info && <Notice tone="success">{info}</Notice>}
            {error && <Notice tone="danger">{error}</Notice>}
          </ScrollView>
        </KeyboardAvoidingView>
      </ThemedView>
    </ProGate>
  );
}
