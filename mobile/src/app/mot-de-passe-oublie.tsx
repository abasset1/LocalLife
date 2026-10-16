import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';

import { ApiError, forgotPassword } from '@/api';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ScreenHeader } from '@/components/screen-header';
import { TextField } from '@/components/text-field';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';

/**
 * Mot de passe oublié : demande l'envoi d'un lien de réinitialisation (`POST /auth/forgot-password`).
 * Le lien reçu par e-mail ouvre la page du site où choisir le nouveau mot de passe ; on se reconnecte
 * ensuite dans l'application. La réponse ne dit pas si un compte existe pour cette adresse.
 */
export default function MotDePasseOublieScreen() {
  const params = useLocalSearchParams<{ email?: string }>();
  const [email, setEmail] = useState(params.email ?? '');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState<string | null>(null);

  async function submit() {
    setError(null);
    setSent(null);
    if (!email.trim()) {
      setError('Renseignez votre adresse e-mail.');
      return;
    }
    setSending(true);
    try {
      const { message } = await forgotPassword(email.trim());
      setSent(message);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Une erreur inattendue est survenue. Réessayez.');
    } finally {
      setSending(false);
    }
  }

  return (
    <ThemedView style={styles.container}>
      <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
          <ScreenHeader
            title="Réinitialiser"
            lead="Indiquez l’adresse de votre compte : nous vous envoyons un lien pour choisir un nouveau mot de passe."
          />
          <Card style={styles.form}>
            <TextField
              label="Adresse e-mail"
              value={email}
              onChangeText={(value) => {
                setEmail(value);
                setSent(null);
              }}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="email-address"
              autoComplete="email"
              textContentType="emailAddress"
              placeholder="vous@exemple.fr"
              onSubmitEditing={() => void submit()}
            />
            {error && <Notice tone="danger">Erreur : {error}</Notice>}
            {sent && (
              <Notice tone="success">
                {sent} Ouvrez le lien reçu pour choisir votre nouveau mot de passe, puis reconnectez-vous ici.
              </Notice>
            )}
            <Button label="Envoyer le lien" loading={sending} onPress={() => void submit()} />
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    gap: Spacing.four,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  form: {
    gap: Spacing.three,
    padding: Spacing.four - Spacing.one,
  },
});
