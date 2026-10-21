import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, KeyboardAvoidingView, Platform, ScrollView } from 'react-native';

import { changePassword, deleteAccount, getCurrentAccount } from '@/api';
import type { CurrentAccount } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { messageFor, proStyles as styles } from '@/features/pro/ProGate';

const MIN_PASSWORD_LENGTH = 8;
const DELETION_WORD = 'SUPPRIMER';

/**
 * « Mot de passe et compte », partagé avec le site : changer son mot de passe (ou en définir un pour un compte créé
 * avec Google) et supprimer son compte. Le serveur décide ; l'écran affiche ses messages.
 */
export default function SecuriteScreen() {
  const router = useRouter();
  const { state, replaceToken, endDeletedSession } = useAuth();
  const authenticated = state.status === 'authenticated';
  const [account, setAccount] = useState<CurrentAccount | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [secret, setSecret] = useState('');
  const [busy, setBusy] = useState(false);
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!authenticated) return;
    const controller = new AbortController();
    getCurrentAccount({ signal: controller.signal })
      .then(setAccount)
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setLoadError(messageFor(e));
      });
    return () => controller.abort();
  }, [authenticated]);

  if (!authenticated) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText style={styles.centerText}>Connectez-vous depuis la rubrique Compte du menu.</ThemedText>
      </ThemedView>
    );
  }
  if (!account) {
    return (
      <ThemedView style={styles.centered}>
        {loadError ? <Notice tone="danger">{loadError}</Notice> : <ActivityIndicator accessibilityLabel="Chargement" />}
      </ThemedView>
    );
  }

  const hasPassword = account.hasPassword !== false;
  const isAdmin = account.role === 'ADMIN';

  async function submitPassword() {
    setError(null);
    setInfo(null);
    if (next.length < MIN_PASSWORD_LENGTH) {
      setError(`Le mot de passe doit contenir au moins ${MIN_PASSWORD_LENGTH} caractères.`);
      return;
    }
    setBusy(true);
    try {
      const { token } = await changePassword(hasPassword ? current : null, next);
      await replaceToken(token);
      setCurrent('');
      setNext('');
      setAccount({ ...account!, hasPassword: true });
      setInfo('Mot de passe enregistré. Vos autres sessions ont été déconnectées.');
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusy(false);
    }
  }

  function confirmDeletion() {
    Alert.alert(
      'Supprimer votre compte ?',
      'Définitif. Vos activités publiées restent visibles sans votre nom ; le reste est supprimé.',
      [
        { text: 'Annuler', style: 'cancel' },
        { text: 'Supprimer', style: 'destructive', onPress: () => void submitDeletion() },
      ],
    );
  }

  async function submitDeletion() {
    setError(null);
    setBusy(true);
    try {
      await deleteAccount(hasPassword ? { password: secret } : { confirmation: secret.trim() });
      await endDeletedSession();
      router.replace('/compte');
    } catch (e) {
      setError(messageFor(e));
      setBusy(false);
    }
  }

  return (
    <ThemedView style={styles.container}>
      <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
          <Card style={styles.form}>
            <ThemedText type="heading" accessibilityRole="header">
              {hasPassword ? 'Changer de mot de passe' : 'Définir un mot de passe'}
            </ThemedText>
            {!hasPassword && (
              <ThemedText type="small" themeColor="textSecondary">
                Vous vous connectez avec Google. Un mot de passe vous permettra aussi de vous connecter par e-mail.
              </ThemedText>
            )}
            {hasPassword && (
              <TextField
                label="Mot de passe actuel"
                value={current}
                onChangeText={setCurrent}
                secureTextEntry
                autoComplete="current-password"
                textContentType="password"
              />
            )}
            <TextField
              label={`Nouveau mot de passe (${MIN_PASSWORD_LENGTH} caractères au moins)`}
              value={next}
              onChangeText={setNext}
              secureTextEntry
              autoComplete="new-password"
              textContentType="newPassword"
            />
            <Button label="Enregistrer le mot de passe" loading={busy} onPress={() => void submitPassword()} />
          </Card>

          {!isAdmin && (
            <Card style={styles.form}>
              <ThemedText type="heading" accessibilityRole="header">
                Supprimer mon compte
              </ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                Définitif. Vos activités déjà publiées restent visibles, sans votre nom ; vos contributions non publiées,
                favoris, alertes et préférences sont supprimés, et votre abonnement prend fin.
              </ThemedText>
              <TextField
                label={hasPassword ? 'Votre mot de passe' : `Saisissez ${DELETION_WORD} pour confirmer`}
                value={secret}
                onChangeText={setSecret}
                secureTextEntry={hasPassword}
                autoCapitalize={hasPassword ? 'none' : 'characters'}
              />
              <Button label="Supprimer mon compte" variant="danger" loading={busy} onPress={confirmDeletion} />
            </Card>
          )}
          {info && <Notice tone="success">{info}</Notice>}
          {error && <Notice tone="danger">{error}</Notice>}
        </ScrollView>
      </KeyboardAvoidingView>
    </ThemedView>
  );
}
