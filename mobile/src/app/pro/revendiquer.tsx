import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView } from 'react-native';

import { claimAction, createClaim, findSimilarPlaces, listMyClaims, updateClaim } from '@/api';
import type { Claim, SimilarPlace } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { placeTypeLabel } from '@/features/map/placeTypes';
import { CLAIM_STATUS_LABELS, formatDay, isClaimEditable, isClaimWithdrawable } from '@/features/pro/format';
import { ProGate, isProDenied, messageFor, proStyles as styles } from '@/features/pro/ProGate';

/** Lieu visé par la demande en cours de rédaction, ou demande existante à compléter. */
type Target = { kind: 'place'; id: number; name: string } | { kind: 'claim'; claim: Claim };

/**
 * Demander la gestion d'un lieu existant (ADR-0012, parcours A), comme sur le site : retrouver le lieu, expliquer
 * son lien avec lui, joindre des justificatifs (texte ou liens), envoyer. La fiche du lieu ne change pas avant
 * l'approbation de LocalLife. Paramètres facultatifs `placeId`/`placeName` : lieu déjà choisi (depuis « Créer un
 * lieu »).
 */
export default function ClaimScreen() {
  const params = useLocalSearchParams<{ placeId?: string; placeName?: string }>();
  const [claims, setClaims] = useState<Claim[]>([]);
  const [loading, setLoading] = useState(true);
  const [denied, setDenied] = useState(false);
  const [name, setName] = useState('');
  const [city, setCity] = useState('');
  const [results, setResults] = useState<SimilarPlace[] | null>(null);
  const [target, setTarget] = useState<Target | null>(
    params.placeId ? { kind: 'place', id: Number(params.placeId), name: params.placeName ?? 'Lieu choisi' } : null,
  );
  const [relationship, setRelationship] = useState('');
  const [evidence, setEvidence] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setClaims(await listMyClaims());
      setDenied(false);
    } catch (e) {
      if (isProDenied(e)) setDenied(true);
      else setError(messageFor(e));
    } finally {
      setLoading(false);
    }
  }, []);
  // Rechargé à chaque retour sur l'écran.
  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  async function search() {
    setError(null);
    try {
      setResults((await findSimilarPlaces({ name, city })).candidates);
    } catch (e) {
      setError(messageFor(e));
    }
  }

  function choose(next: Target) {
    setTarget(next);
    setRelationship(next.kind === 'claim' ? (next.claim.relationship ?? '') : '');
    setEvidence(next.kind === 'claim' ? (next.claim.evidence ?? '') : '');
    setInfo(null);
    setError(null);
  }

  /** Enregistre la demande (brouillon), puis l'envoie si demandé. */
  async function save(send: boolean) {
    if (!target) return;
    setBusy(true);
    setError(null);
    try {
      const text = { relationship, evidence };
      const saved = target.kind === 'claim' ? await updateClaim(target.claim.id, text) : await createClaim(target.id, text);
      if (send) await claimAction(saved.id, 'submit');
      setInfo(send ? 'Demande envoyée : LocalLife va l’examiner.' : 'Brouillon enregistré.');
      setTarget(null);
      setResults(null);
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusy(false);
      void load();
    }
  }

  async function act(claim: Claim, action: 'submit' | 'withdraw') {
    setError(null);
    try {
      await claimAction(claim.id, action);
      setInfo(action === 'submit' ? 'Demande envoyée.' : 'Demande retirée.');
    } catch (e) {
      setError(messageFor(e));
    } finally {
      void load();
    }
  }

  return (
    <ProGate loading={loading} denied={denied}>
      <ThemedView style={styles.container}>
        <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            <ThemedText themeColor="textSecondary">
              Votre lieu est déjà sur LocalLife ? Retrouvez-le et demandez à le gérer. La fiche reste inchangée tant que
              LocalLife n’a pas approuvé votre demande.
            </ThemedText>

            {target ? (
              <Card style={styles.form}>
                <ThemedText type="heading">
                  Demande pour « {target.kind === 'claim' ? target.claim.placeName : target.name} »
                </ThemedText>
                {target.kind === 'claim' && target.claim.status === 'INFO_REQUESTED' && target.claim.decisionReason ? (
                  <Notice tone="warning">Demande de LocalLife : {target.claim.decisionReason}</Notice>
                ) : null}
                <TextField
                  label="Votre lien avec le lieu"
                  placeholder="Ex. : je suis le gérant de l’établissement."
                  value={relationship}
                  onChangeText={setRelationship}
                  multiline
                  maxLength={2000}
                />
                <TextField
                  label="Justificatifs"
                  placeholder="SIRET, site officiel, adresse e-mail professionnelle…"
                  value={evidence}
                  onChangeText={setEvidence}
                  multiline
                  maxLength={4000}
                />
                <ThemedText type="small" themeColor="textSecondary">
                  Visibles de vous et de l’équipe LocalLife seulement, effacés 12 mois après la décision.
                </ThemedText>
                <Button
                  label="Envoyer la demande"
                  loading={busy}
                  disabled={relationship.trim() === ''}
                  onPress={() => void save(true)}
                />
                <Button label="Enregistrer le brouillon" variant="secondary" loading={busy} onPress={() => void save(false)} />
                <Button label="Annuler" variant="ghost" onPress={() => setTarget(null)} />
              </Card>
            ) : (
              <Card style={styles.form}>
                <TextField label="Nom du lieu" value={name} onChangeText={setName} returnKeyType="next" />
                <TextField label="Commune" value={city} onChangeText={setCity} returnKeyType="search" />
                <Button
                  label="Rechercher"
                  disabled={name.trim() === '' || city.trim() === ''}
                  onPress={() => void search()}
                />
                {results?.length === 0 && (
                  <ThemedText themeColor="textSecondary">
                    Aucun lieu trouvé : vous pouvez le créer depuis « Créer un lieu ».
                  </ThemedText>
                )}
                {results?.map(({ place }) => (
                  <Card key={place.id} style={styles.card}>
                    <ThemedText type="heading">{place.name}</ThemedText>
                    <ThemedText type="small" themeColor="textSecondary">
                      {placeTypeLabel(place.placeType)} · {[place.address, place.city].filter(Boolean).join(', ')}
                    </ThemedText>
                    <Button
                      label="Demander la gestion de ce lieu"
                      variant="secondary"
                      onPress={() => choose({ kind: 'place', id: place.id, name: place.name })}
                    />
                  </Card>
                ))}
              </Card>
            )}

            {info && <Notice tone="success">{info}</Notice>}
            {error && <Notice tone="danger">{error}</Notice>}

            <ThemedText type="subtitle" accessibilityRole="header">
              Mes demandes
            </ThemedText>
            {claims.length === 0 && <ThemedText themeColor="textSecondary">Aucune demande pour l’instant.</ThemedText>}
            {claims.map((claim) => (
              <Card key={claim.id} style={styles.card}>
                <ThemedText type="heading">{claim.placeName}</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {CLAIM_STATUS_LABELS[claim.status]}
                  {claim.placeCity ? ` · ${claim.placeCity}` : ''} · mise à jour le {formatDay(claim.updatedAt)}
                </ThemedText>
                {claim.decisionReason ? (
                  <ThemedText type="small">Motif : {claim.decisionReason}</ThemedText>
                ) : null}
                {isClaimEditable(claim.status) && (
                  <Button label="Compléter" variant="secondary" onPress={() => choose({ kind: 'claim', claim })} />
                )}
                {isClaimEditable(claim.status) && claim.relationship ? (
                  <Button label="Envoyer" onPress={() => void act(claim, 'submit')} />
                ) : null}
                {isClaimWithdrawable(claim.status) && (
                  <Button label="Retirer" variant="danger" onPress={() => void act(claim, 'withdraw')} />
                )}
              </Card>
            ))}
          </ScrollView>
        </KeyboardAvoidingView>
      </ThemedView>
    </ProGate>
  );
}
