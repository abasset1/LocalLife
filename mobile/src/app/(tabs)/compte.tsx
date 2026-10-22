import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import * as WebBrowser from 'expo-web-browser';

import { ApiError, HttpError, getCurrentAccount, resendVerification } from '@/api';
import { BrandMark } from '@/components/brand-mark';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { MenuRow } from '@/components/menu-row';
import { Notice } from '@/components/notice';
import { ScreenHeader } from '@/components/screen-header';
import { adminWebUrl, shouldOfferAdminLink } from '@/features/admin/adminLink';
import { MenuButton } from '@/features/navigation/AppMenu';
import { useOwnsFoodTrucks } from '@/features/pro/useProAccess';
import { apiUrl } from '@/config/env';
import { TextField } from '@/components/text-field';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import {
  GoogleSignInError,
  USERNAME_MAX_LENGTH,
  USERNAME_RULES,
  googleSignInAvailable,
  requestGoogleIdToken,
  useAuth,
  useUsernameAvailability,
} from '@/auth';
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
  const { state, login, loginWithGoogle, register, logout } = useAuth();
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
  const [googleLoading, setGoogleLoading] = useState(false);
  const usernameStatus = useUsernameAvailability(mode === 'register' ? username : '');
  /** Rôle relu par le serveur (`GET /users/me`), jamais celui du jeton stocké : décide seul du lien d'administration. */
  const [serverAccount, setServerAccount] = useState<{ userId: number; role: string; hasProAccess: boolean } | null>(
    null,
  );
  const [adminLinkError, setAdminLinkError] = useState<string | null>(null);
  const authenticatedUserId = state.status === 'authenticated' ? state.user.userId : null;
  // Rôle valable seulement pour le compte connecté qui l'a demandé (changement de compte : plus de lien).
  const serverRole = serverAccount && serverAccount.userId === authenticatedUserId ? serverAccount.role : null;
  // Seul `hasProAccess` ouvre l'espace professionnel (plus le rôle) ; sinon, l'offre Pro est proposée.
  const accountKnown = serverAccount !== null && serverAccount.userId === authenticatedUserId;
  const hasProAccess = accountKnown && serverAccount.hasProAccess;
  // Ancien compte Pro : ses food trucks restent consultables et archivables.
  const ownsFoodTrucks = useOwnsFoodTrucks(accountKnown && !hasProAccess);

  useEffect(() => {
    if (authenticatedUserId === null) return;
    const controller = new AbortController();
    getCurrentAccount({ signal: controller.signal })
      .then((account) =>
        setServerAccount({
          userId: authenticatedUserId,
          role: account.role,
          hasProAccess: account.hasProAccess === true,
        }),
      )
      .catch(() => setServerAccount(null));
    return () => controller.abort();
  }, [authenticatedUserId]);

  async function openAdministration() {
    const url = adminWebUrl(apiUrl);
    if (!url) {
      setAdminLinkError("Adresse du site introuvable dans la configuration de l'application.");
      return;
    }
    setAdminLinkError(null);
    try {
      await WebBrowser.openBrowserAsync(url);
    } catch {
      setAdminLinkError("Impossible d'ouvrir le site d'administration.");
    }
  }

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
    setInfo(null);
    setUnverified(false);
    setPassword('');
  }

  /** « Continuer avec Google » : connexion, ou création du compte s'il n'existe pas encore. */
  async function continueWithGoogle() {
    setError(null);
    setInfo(null);
    setUnverified(false);
    setGoogleLoading(true);
    try {
      const idToken = await requestGoogleIdToken();
      if (idToken) await loginWithGoogle(idToken);
    } catch (e) {
      setError(e instanceof GoogleSignInError ? e.message : messageFor(e));
    } finally {
      setGoogleLoading(false);
    }
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
        <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
          <ScrollView contentContainerStyle={styles.content}>
            <MenuButton />
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
              />
              <MenuRow
                icon={{ ios: 'lock', android: 'lock', web: 'lock' }}
                label="Mot de passe et compte"
                description="Changer de mot de passe, supprimer le compte"
                onPress={() => router.push('/securite')}
              />
              <MenuRow
                icon={{ ios: 'star', android: 'star', web: 'star' }}
                label="Mon abonnement"
                description="Gratuit, Premium ou Pro"
                onPress={() => router.push('/abonnement')}
              />
              {hasProAccess ? (
                <MenuRow
                  icon={{ ios: 'briefcase', android: 'storefront', web: 'storefront' }}
                  label="Espace professionnel"
                  description="Vos lieux et vos food trucks"
                  onPress={() => router.push('/pro')}
                  last={!shouldOfferAdminLink(serverRole)}
                />
              ) : (
                <>
                {ownsFoodTrucks && (
                  <MenuRow
                    icon={{ ios: 'truck.box', android: 'local_shipping', web: 'local_shipping' }}
                    label="Mes food trucks"
                    description="Accès professionnel terminé : consulter ou archiver"
                    onPress={() => router.push('/pro/foodtrucks')}
                  />
                )}
                <MenuRow
                  icon={{ ios: 'briefcase', android: 'storefront', web: 'storefront' }}
                  label="Vous êtes professionnel ?"
                  description="Gérez votre lieu ou votre food truck avec l’offre Pro"
                  onPress={() => router.push('/abonnement')}
                  last={!shouldOfferAdminLink(serverRole)}
                />
                </>
              )}
              {shouldOfferAdminLink(serverRole) && (
                <MenuRow
                  icon={{ ios: 'lock.shield', android: 'admin_panel_settings', web: 'admin_panel_settings' }}
                  label="Administration (site web)"
                  description="Comptes, modération et imports, sur le site"
                  onPress={() => void openAdministration()}
                  last
                />
              )}
            </Card>
            {adminLinkError && <Notice tone="danger">{adminLinkError}</Notice>}

            <Button label="Se déconnecter" variant="danger" onPress={() => void logout()} />
          </ScrollView>
        </SafeAreaView>
      </ThemedView>
    );
  }

  const isLogin = mode === 'login';

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.container} edges={['top', 'bottom']}>
        <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
            <MenuButton />
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
                disabled={googleLoading}
                onPress={() => void submit()}
              />
              {googleSignInAvailable && (
                <>
                  <View style={styles.separator} importantForAccessibility="no-hide-descendants">
                    <View style={[styles.separatorLine, { backgroundColor: theme.border }]} />
                    <ThemedText type="small" themeColor="textSecondary">
                      ou
                    </ThemedText>
                    <View style={[styles.separatorLine, { backgroundColor: theme.border }]} />
                  </View>
                  <Button
                    label="Continuer avec Google"
                    variant="secondary"
                    loading={googleLoading}
                    disabled={submitting}
                    onPress={() => void continueWithGoogle()}
                  />
                </>
              )}
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
  separator: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  separatorLine: {
    flex: 1,
    height: StyleSheet.hairlineWidth,
  },
  usernameGroup: {
    gap: Spacing.two,
  },
});
