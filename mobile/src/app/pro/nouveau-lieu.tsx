import { useRouter } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView } from 'react-native';

import { createPlace, findSimilarPlaces } from '@/api';
import type { SimilarResult } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { PLACE_TYPE_LABELS, placeTypeLabel } from '@/features/map/placeTypes';
import { ProGate, isProDenied, messageFor, proStyles as styles } from '@/features/pro/ProGate';
import { ChipGroup } from '@/features/settings';

const EMPTY = { name: '', placeType: '', address: '', postalCode: '', city: '', description: '', website: '', phone: '', email: '' };

const TYPE_OPTIONS = Object.entries(PLACE_TYPE_LABELS)
  .map(([value, label]) => ({ value, label }))
  .sort((a, b) => a.label.localeCompare(b.label, 'fr'));

/**
 * Créer un lieu absent de LocalLife (ADR-0012, parcours B), comme sur le site : LocalLife cherche d'abord les lieux
 * proches ou de même nom dans la commune ; si l'un est le vôtre, demandez plutôt à le gérer. Le lieu est enregistré en
 * brouillon, à envoyer à la modération depuis l'espace professionnel.
 */
export default function NewPlaceScreen() {
  const router = useRouter();
  const [fields, setFields] = useState(EMPTY);
  const [check, setCheck] = useState<SimilarResult | null>(null);
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [reportedSimilarTo, setReportedSimilarTo] = useState<number | null>(null);
  const [denied, setDenied] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  function set(key: keyof typeof EMPTY, value: string) {
    setFields((current) => ({ ...current, [key]: value }));
    setCheck(null);
  }

  async function verify() {
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      const result = await findSimilarPlaces({
        name: fields.name, address: fields.address, postalCode: fields.postalCode, city: fields.city,
      });
      setCheck(result);
      setLatitude(result.latitude?.toString() ?? '');
      setLongitude(result.longitude?.toString() ?? '');
      setReportedSimilarTo(null);
    } catch (e) {
      if (isProDenied(e)) setDenied(true);
      else setError(messageFor(e));
    } finally {
      setBusy(false);
    }
  }

  async function save() {
    setBusy(true);
    setError(null);
    try {
      const created = await createPlace({
        ...fields,
        latitude: latitude.trim() === '' ? null : Number(latitude.replace(',', '.')),
        longitude: longitude.trim() === '' ? null : Number(longitude.replace(',', '.')),
        confirmNotDuplicate: true,
        reportedSimilarTo,
      });
      setFields(EMPTY);
      setCheck(null);
      setInfo(`« ${created.name} » enregistré en brouillon. Envoyez-le à la modération depuis l’espace professionnel.`);
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusy(false);
    }
  }

  const complete = fields.name.trim() !== '' && fields.placeType !== '' && fields.address.trim() !== ''
    && fields.city.trim() !== '';
  const hasPosition = latitude.trim() !== '' && longitude.trim() !== '';

  return (
    <ProGate loading={false} denied={denied}>
      <ThemedView style={styles.container}>
        <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            <Card style={styles.form}>
              <TextField label="Nom" value={fields.name} onChangeText={(v) => set('name', v)} maxLength={255} />
              <ChipGroup
                label="Catégorie"
                options={TYPE_OPTIONS}
                selected={fields.placeType ? [fields.placeType] : []}
                onToggle={(value) => set('placeType', value)}
                single
              />
              <TextField label="Adresse" value={fields.address} onChangeText={(v) => set('address', v)} />
              <TextField
                label="Code postal"
                value={fields.postalCode}
                onChangeText={(v) => set('postalCode', v)}
                keyboardType="number-pad"
                maxLength={5}
              />
              <TextField label="Commune" value={fields.city} onChangeText={(v) => set('city', v)} />
              <TextField
                label="Description"
                value={fields.description}
                onChangeText={(v) => set('description', v)}
                multiline
                maxLength={5000}
              />
              <TextField
                label="Site web"
                placeholder="https://"
                value={fields.website}
                onChangeText={(v) => set('website', v)}
                autoCapitalize="none"
                keyboardType="url"
              />
              <TextField label="Téléphone" value={fields.phone} onChangeText={(v) => set('phone', v)} keyboardType="phone-pad" />
              <TextField
                label="E-mail professionnel"
                value={fields.email}
                onChangeText={(v) => set('email', v)}
                autoCapitalize="none"
                keyboardType="email-address"
              />
              <Button label="Vérifier et continuer" loading={busy} disabled={!complete} onPress={() => void verify()} />
            </Card>

            {check && (
              <Card style={styles.form}>
                {check.candidates.length === 0 ? (
                  <Notice tone="success">Aucun lieu proche ou de même nom n’existe déjà.</Notice>
                ) : (
                  <>
                    <Notice tone="warning">
                      Ces lieux existent déjà. Si l’un d’eux est le vôtre, demandez plutôt à le gérer.
                    </Notice>
                    {check.candidates.map(({ place, distanceMeters }) => (
                      <Card key={place.id} style={styles.card}>
                        <ThemedText type="heading">{place.name}</ThemedText>
                        <ThemedText type="small" themeColor="textSecondary">
                          {placeTypeLabel(place.placeType)}
                          {place.city ? ` · ${place.city}` : ''}
                          {distanceMeters !== null ? ` · à ${distanceMeters} m` : ''}
                        </ThemedText>
                        <Button
                          label="C’est mon lieu"
                          variant="secondary"
                          onPress={() =>
                            router.push({
                              pathname: '/pro/revendiquer',
                              params: { placeId: String(place.id), placeName: place.name },
                            })
                          }
                        />
                        <Button
                          label={reportedSimilarTo === place.id ? '✓ Signalé comme similaire' : 'Différent mais similaire : le signaler'}
                          variant="ghost"
                          onPress={() => setReportedSimilarTo(reportedSimilarTo === place.id ? null : place.id)}
                        />
                      </Card>
                    ))}
                  </>
                )}
                {!hasPosition && (
                  <Notice tone="warning">L’adresse n’a pas pu être localisée : indiquez la position du lieu.</Notice>
                )}
                <TextField label="Latitude" value={latitude} onChangeText={setLatitude} keyboardType="numbers-and-punctuation" />
                <TextField label="Longitude" value={longitude} onChangeText={setLongitude} keyboardType="numbers-and-punctuation" />
                <Button
                  label={check.candidates.length === 0 ? 'Enregistrer le brouillon' : 'Mon lieu n’est pas dans la liste : enregistrer'}
                  loading={busy}
                  disabled={!hasPosition}
                  onPress={() => void save()}
                />
              </Card>
            )}

            {info && <Notice tone="success">{info}</Notice>}
            {error && <Notice tone="danger">{error}</Notice>}
          </ScrollView>
        </KeyboardAvoidingView>
      </ThemedView>
    </ProGate>
  );
}
