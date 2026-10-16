import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApiError, HttpError, resendVerification } from '@/api';
import { BrandMark } from '@/components/brand-mark';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { MenuRow } from '@/components/menu-row';
import { Notice } from '@/components/notice';
import { ScreenHeader } from '@/components/screen-header';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Shadows, Spacing } from '@/constants/theme';
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
  /** Connexion refusée car l'adresse n'est pas confirmée (403) : propose de renvoyer le lien. */
  const [unverified, setUnverified] = useState(false);
  const [resending, setResending] = useState(false);
  const usernameStatus = useUsernameAvailability(mode === 'register' ? username : '');

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
    setInfo(null);
    setUnverified(false);
    setPassword('');
  }

  async function resend() {
    setResending(true);
    setError(null);
    try {
      const { message } = await resendVerification(email.trim());
      setUnverified(false);
      setInfo(message);
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setResending(false);
    }
  }

  async function submit() {
    setError(null);
    setInfo(null);
    setUnverified(false);

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
      if (mode === 'login' && e instanceof HttpError && e.isForbidden) setUnverified(true);
    } finally {
      setSubmitting(false);
    }
  }

  if (state.status === 'loading') {
    return (
      <ThemedView style={styles.centered}>
        <ActivityIndicator color={theme.primary} accessibilityLabel="Chargement de la session" />
      </ThemedView>
    );
  }

  if (state.status === 'authenticated') {
    const initial = state.user.email.trim().charAt(0).toUpperCase() || '?';
    return (
      <ThemedView style={styles.container}>
        <SafeAreaView style={styles.container} edges={['top']}>
          <ScrollView contentContainerStyle={styles.content}>
            <ScreenHeader eyebrow="Mon espace" title="Compte" />

            <Card style={styles.profile}>
              <View style={[styles.avatar, { backgroundColor: theme.primary }]} importantForAccessibility="no">
                <ThemedText style={[styles.avatarLetter, { color: theme.onPrimary }]}>{initial}</ThemedText>
              </View>
              <View style={styles.profileText}>
                <ThemedText type="small" themeColor="textSecondary">
                  Connecté en tant que
                </ThemedText>
                <ThemedText type="smallBold" numberOfLines={1} style={styles.email}>
                  {state.user.email}
                </ThemedText>
              </View>
            </Card>

            <Card style={styles.menu}>
              <MenuRow
                icon={{ ios: 'heart', android: 'favorite', web: 'favorite' }}
                label="Mes favoris"
                description="Les activités que vous aimez"
                onPress={() => router.push('/favoris')}
              />
              <MenuRow
                icon={{ ios: 'slider.horizontal.3', android: 'tune', web: 'tune' }}
                label="Paramètres de recherche"
                description="Ville, rayon et catégories de la carte"
                onPress={() => router.push('/parametres')}
              />
              <MenuRow
                icon={{ ios: 'bell', android: 'notifications', web: 'notifications' }}
                label="Mes alertes"
                description="Être prévenu des nouveautés"
                onPress={() => router.push('/alertes')}
              />
              <MenuRow
                icon={{ ios: 'square.and.pencil', android: 'edit_note', web: 'edit_note' }}
                label="Mes contributions"
                description="Suivre et modifier vos propositions"
                onPress={() => router.push('/contributions')}
                last
              />
            </Card>

            <Button label="Se déconnecter" variant="danger" onPress={() => void logout()} />
          </ScrollView>
        </SafeAreaView>
      </ThemedView>
    );
  }

  const isLogin = mode === 'login';

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.container} edges={['top']}>
        <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            <View style={[styles.hero, { backgroundColor: theme.primary }]}>
              <BrandMark inverted />
              <ThemedText style={styles.heroTitle}>
                {isLogin ? 'Bon retour parmi nous.' : 'Rejoignez la vie locale.'}
              </ThemedText>
              <ThemedText type="small" style={styles.heroLead}>
                Activités, événements et food trucks près de chez vous, au même endroit.
              </ThemedText>
            </View>

            <View
              accessibilityRole="tablist"
              style={[styles.segmented, { backgroundColor: theme.backgroundSelected }]}>
              {(['login', 'register'] as const).map((value) => {
                const active = mode === value;
                return (
                  <Pressable
                    key={value}
                    accessibilityRole="tab"
                    accessibilityState={{ selected: active, disabled: submitting }}
                    disabled={submitting}
                    onPress={() => !active && switchMode(value)}
                    style={[styles.segment, active && [styles.segmentActive, { backgroundColor: theme.backgroundElement }]]}>
                    <ThemedText type="smallBold" themeColor={active ? 'text' : 'textSecondary'}>
                      {value === 'login' ? 'Connexion' : 'Inscription'}
                    </ThemedText>
                  </Pressable>
                );
              })}
            </View>

            {info && <Notice tone="success">{info}</Notice>}

            <Card style={styles.form}>
              {!isLogin && (
                <View style={styles.usernameGroup}>
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
                    themeColor={usernameStatus.kind === 'unavailable' || usernameStatus.kind === 'available' ? undefined : 'textSecondary'}
                    style={
                      usernameStatus.kind === 'unavailable'
                        ? { color: theme.danger }
                        : usernameStatus.kind === 'available'
                          ? { color: theme.success }
                          : undefined
                    }
                  >
                    {usernameStatus.kind === 'idle' && USERNAME_RULES}
                    {usernameStatus.kind === 'checking' && 'Vérification…'}
                    {usernameStatus.kind === 'available' && '✓ Ce nom est disponible.'}
                    {usernameStatus.kind === 'unavailable' && usernameStatus.message}
                  </ThemedText>
                </View>
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
                placeholder="vous@exemple.fr"
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

              {error && <Notice tone="danger">Erreur : {error}</Notice>}
              {unverified && (
                <Button
                  label="Renvoyer l'e-mail de confirmation"
                  variant="secondary"
                  loading={resending}
                  onPress={() => void resend()}
                />
              )}

              <Button
                label={isLogin ? 'Se connecter' : "S'inscrire"}
                loading={submitting}
                onPress={() => void submit()}
              />
              {isLogin && (
                <Button
                  label="Mot de passe oublié ?"
                  variant="ghost"
                  disabled={submitting}
                  onPress={() =>
                    router.push({ pathname: '/mot-de-passe-oublie', params: email.trim() ? { email: email.trim() } : {} })
                  }
                />
              )}
            </Card>

            <Button
              label={isLogin ? 'Pas de compte ? Créer un compte' : 'Déjà inscrit ? Se connecter'}
              variant="ghost"
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
  content: {
    gap: Spacing.three,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  profile: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  avatar: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarLetter: {
    fontSize: 22,
    lineHeight: 28,
    fontWeight: 800,
  },
  profileText: {
    flex: 1,
  },
  email: {
    fontSize: 16,
  },
  menu: {
    padding: 0,
    gap: 0,
    overflow: 'hidden',
  },
  hero: {
    borderRadius: Radius.xl,
    padding: Spacing.four,
    gap: Spacing.three,
    ...Shadows.card,
  },
  heroTitle: {
    color: '#FFFFFF',
    fontSize: 30,
    lineHeight: 34,
    fontWeight: 800,
    letterSpacing: -1,
    marginTop: Spacing.two,
  },
  heroLead: {
    color: '#C8D7D1',
  },
  segmented: {
    flexDirection: 'row',
    padding: Spacing.one,
    borderRadius: Radius.md,
  },
  segment: {
    flex: 1,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: Radius.md - 4,
  },
  segmentActive: {
    ...Shadows.card,
  },
  form: {
    gap: Spacing.three,
    padding: Spacing.four - Spacing.one,
  },
  usernameGroup: {
    gap: Spacing.two,
  },
});
