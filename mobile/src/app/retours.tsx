import { useRouter } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';

import { ApiError, submitFeedback, type FeedbackKind } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ScreenHeader } from '@/components/screen-header';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { FEEDBACK_KINDS, FEEDBACK_MAX_LENGTH, feedbackMessageError, feedbackOrigin } from '@/features/feedback';
import { ChipGroup } from '@/features/settings';

/**
 * « Une idée, un problème ? » (utilisateur connecté) : type de message et texte libre, envoyés à
 * `POST /api/v1/feedback` comme depuis le site web. Les administrateurs les retrouvent dans la section
 * « Retours » de l'administration et sont prévenus par e-mail.
 */
export default function RetoursScreen() {
  const router = useRouter();
  const { state } = useAuth();
  const [kind, setKind] = useState<FeedbackKind>('SUGGESTION');
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  async function submit() {
    const invalid = feedbackMessageError(message);
    if (invalid) {
      setError(invalid);
      return;
    }
    setSending(true);
    setError(null);
    try {
      await submitFeedback(kind, message.trim(), feedbackOrigin(Platform.OS, Platform.Version));
      setSent(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Une erreur inattendue est survenue. Réessayez.');
    } finally {
      setSending(false);
    }
  }

  if (state.status !== 'authenticated') {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText>
          Connectez-vous depuis la rubrique Compte du menu pour nous envoyer une idée ou signaler un problème.
        </ThemedText>
      </ThemedView>
    );
  }

  const placeholder = FEEDBACK_KINDS.find((option) => option.value === kind)?.placeholder;

  return (
    <ThemedView style={styles.container}>
      <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
          <ScreenHeader
            title="Une idée, un problème ?"
            lead="Une suggestion pour améliorer LocalLife, ou quelque chose qui ne marche pas : dites-le-nous."
          />
          {sent ? (
            <Card style={styles.form}>
              <ThemedText type="heading">🙏 Merci !</ThemedText>
              <ThemedText>Votre message a bien été envoyé à l’équipe LocalLife. Nous le lisons avec attention.</ThemedText>
              <Button
                label="Envoyer un autre message"
                variant="secondary"
                onPress={() => {
                  setMessage('');
                  setSent(false);
                }}
              />
              <Button label="Fermer" onPress={() => router.back()} />
            </Card>
          ) : (
            <Card style={styles.form}>
              <ChipGroup
                label="Type de message"
                options={FEEDBACK_KINDS}
                selected={[kind]}
                onToggle={(value) => setKind(value as FeedbackKind)}
                single
              />
              <TextField
                label="Votre message"
                value={message}
                onChangeText={(value) => {
                  setMessage(value);
                  setError(null);
                }}
                placeholder={placeholder}
                maxLength={FEEDBACK_MAX_LENGTH}
                multiline
                numberOfLines={6}
              />
              <ThemedText type="small" themeColor="textSecondary" style={styles.counter}>
                {message.length} / {FEEDBACK_MAX_LENGTH}
              </ThemedText>
              {error && <Notice tone="danger">Erreur : {error}</Notice>}
              <Button label="Envoyer" loading={sending} onPress={() => void submit()} />
            </Card>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </ThemedView>
  );
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
  content: {
    gap: Spacing.four,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  form: {
    gap: Spacing.three,
    padding: Spacing.four - Spacing.one,
  },
  counter: {
    alignSelf: 'flex-end',
  },
});
