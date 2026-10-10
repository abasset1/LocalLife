import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApiError } from '@/api';
import { Button } from '@/components/button';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { USERNAME_MAX_LENGTH, USERNAME_RULES, useAuth, useUsernameAvailability } from '@/auth';
import { useTheme } from '@/hooks/use-theme';

type Mode = 'login' | 'register';

/** Message affichable pour une erreur : celui de l'API, sinon un message générique. */
function messageFor(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Une erreur inattendue est survenue. Réessayez.';
}

/**
 * Onglet "Compte" (LL-MOB-0004) : inscription, connexion, session
 * restaurée et déconnexion. Les règles de validation (mot de passe,
 * format d'e-mail…) restent côté backend : l'écran n'affiche que ses
 * messages.
 */
export default function CompteScreen() {
  const { state, login, register, logout } = useAuth();
  const theme = useTheme();
  const router = useRouter();

  const [mode, setMode] = useState<Mode>('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const usernameStatus = useUsernameAvailability(mode === 'register' ? username : '');

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
    setInfo(null);
    setPassword('');
  }

  async function submit() {
    setError(null);
    setInfo(null);

    if (!email.trim() || !password || (mode === 'register' && !username.trim())) {
      setError('Renseignez tous les champs.');
      return;
    }
    if (mode === 'register' && usernameStatus.kind === 'unavailable') {
      setError(usernameStatus.message);
      return;
    }

    setSubmitting(true);
    try {
      if (mode === 'login') {
        await login(email, password);
        setPassword('');
      } else {
        await register({ username, email, password });
        setMode('login');
        setPassword('');
        setInfo(
          'Compte créé. Un e-mail de confirmation vous a été envoyé : confirmez votre adresse, puis connectez-vous.',
        );
      }
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setSubmitting(false);
    }
  }

  if (state.status === 'loading') {
    return (
      <ThemedView style={styles.centered}>
        <ActivityIndicator accessibilityLabel="Chargement de la session" />
      </ThemedView>
    );
  }

  if (state.status === 'authenticated') {
    return (
      <ThemedView style={styles.container}>
        <SafeAreaView style={styles.safeArea}>
          <ThemedText type="title" style={styles.title}>
            Compte
          </ThemedText>
          <ThemedText>Connecté en tant que</ThemedText>
          <ThemedText type="smallBold">{state.user.email}</ThemedText>
          <Button label="Paramètres de recherche" onPress={() => router.push('/parametres')} />
          <Button label="Mes alertes" onPress={() => router.push('/alertes')} />
          <Button label="Se déconnecter" variant="secondary" onPress={() => void logout()} />
        </SafeAreaView>
      </ThemedView>
    );
  }

  const isLogin = mode === 'login';

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea}>
        <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.form}>
            <ThemedText type="title" style={styles.title}>
              Compte
            </ThemedText>
            <ThemedText type="subtitle" style={styles.subtitle}>
              {isLogin ? 'Connexion' : 'Inscription'}
            </ThemedText>

            {info && (
              <ThemedText accessibilityRole="alert" themeColor="textSecondary">
                {info}
              </ThemedText>
            )}

            {!isLogin && (
              <>
                <TextField
                  label="Nom d'utilisateur"
                  value={username}
                  onChangeText={setUsername}
                  autoCapitalize="none"
                  autoCorrect={false}
                  autoComplete="username-new"
                  textContentType="username"
                  maxLength={USERNAME_MAX_LENGTH}
                />
                <ThemedText
                  type="small"
                  accessibilityLiveRegion="polite"
                  themeColor={usernameStatus.kind === 'unavailable' ? undefined : 'textSecondary'}
                  style={usernameStatus.kind === 'unavailable' ? { color: theme.danger } : undefined}
                >
                  {usernameStatus.kind === 'idle' && USERNAME_RULES}
                  {usernameStatus.kind === 'checking' && 'Vérification…'}
                  {usernameStatus.kind === 'available' && '✓ Ce nom est disponible.'}
                  {usernameStatus.kind === 'unavailable' && usernameStatus.message}
                </ThemedText>
              </>
            )}
            <TextField
              label="Adresse e-mail"
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              autoCorrect={false}
              keyboardType="email-address"
              autoComplete="email"
              textContentType="emailAddress"
            />
            <TextField
              label="Mot de passe"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete={isLogin ? 'current-password' : 'new-password'}
              textContentType={isLogin ? 'password' : 'newPassword'}
              onSubmitEditing={() => void submit()}
            />

            {error && (
              <ThemedText accessibilityRole="alert" style={{ color: theme.danger }}>
                Erreur : {error}
              </ThemedText>
            )}

            <Button
              label={isLogin ? 'Se connecter' : "S'inscrire"}
              loading={submitting}
              onPress={() => void submit()}
            />
            <Button
              label={isLogin ? 'Pas de compte ? Créer un compte' : 'Déjà inscrit ? Se connecter'}
              variant="secondary"
              disabled={submitting}
              onPress={() => switchMode(isLogin ? 'register' : 'login')}
            />
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  flex: {
    flex: 1,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  safeArea: {
    flex: 1,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.six,
    gap: Spacing.three,
  },
  form: {
    gap: Spacing.three,
    paddingBottom: Spacing.five,
  },
  title: {
    marginBottom: Spacing.two,
  },
  subtitle: {
    fontSize: 24,
    lineHeight: 32,
  },
});
