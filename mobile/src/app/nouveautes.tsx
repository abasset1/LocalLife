import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { ApiError, getSubscription, subscribePlan } from '@/api';
import type { Plan } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const FEATURES = [
  {
    icon: '📅',
    title: 'Une activité',
    text: 'Les dates changent, l’adresse bouge, l’événement est annulé ou ses informations sont mises à jour : vous le savez tout de suite.',
  },
  {
    icon: '🏛',
    title: 'Un lieu',
    text: 'Votre musée, votre salle ou votre parc préféré programme une nouvelle activité ou change d’adresse : vous êtes prévenu.',
  },
  {
    icon: '📲',
    title: 'Au bon moment',
    text: 'Une notification sur ce téléphone, sinon un e-mail. Un seul message qui regroupe tout, au plus une fois par heure, jamais la nuit.',
  },
];

const STEPS = [
  'Ouvrez la fiche d’une activité ou l’aperçu d’un lieu sur la carte.',
  'Touchez 🔔 « Suivre ».',
  'C’est tout : Neiri vous prévient dès qu’il y a du nouveau.',
];

const PERKS = [
  '🔔 Suivi des activités et des lieux',
  '❤️ Favoris illimités (5 avec le compte gratuit)',
  '✉️ Jusqu’à 10 alertes (1 avec le compte gratuit)',
  '🔎 Alertes personnalisées par mots-clés',
  '✍️ 2 propositions d’activités par mois',
];

const KEYWORD_EXAMPLES = ['jazz', 'marché de Noël', 'randonnée', 'atelier enfants'];

type PlanState = { kind: 'loading' } | { kind: 'loaded'; plan: Plan } | { kind: 'error' };

/**
 * « Nouveautés » : présente la cloche « Suivre » (activités et lieux), réservée à Premium et Pro, et propose de passer à
 * Premium — gratuit pour le moment, aucun paiement n'étant branché. Même contenu que la page `/nouveautes` du site.
 */
export default function NouveautesScreen() {
  const theme = useTheme();
  const router = useRouter();
  const { state } = useAuth();
  const authenticated = state.status === 'authenticated';
  const [planState, setPlanState] = useState<PlanState>({ kind: 'loading' });
  const [subscribing, setSubscribing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [justSubscribed, setJustSubscribed] = useState(false);

  useEffect(() => {
    if (!authenticated) return;
    const controller = new AbortController();
    getSubscription({ signal: controller.signal })
      .then((overview) => setPlanState({ kind: 'loaded', plan: overview.plan }))
      .catch(() => {
        if (!controller.signal.aborted) setPlanState({ kind: 'error' });
      });
    return () => controller.abort();
  }, [authenticated]);

  async function subscribe() {
    setSubscribing(true);
    setError(null);
    try {
      const overview = await subscribePlan('PREMIUM');
      setPlanState({ kind: 'loaded', plan: overview.plan });
      setJustSubscribed(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Une erreur inattendue est survenue. Réessayez.');
    } finally {
      setSubscribing(false);
    }
  }

  const hasAccess = authenticated && planState.kind === 'loaded' && planState.plan !== 'FREE';

  return (
    <ThemedView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.hero}>
          <View style={[styles.badge, { backgroundColor: theme.accent }]}>
            <ThemedText type="smallBold" style={styles.badgeText}>
              NOUVEAU
            </ThemedText>
          </View>
          <ThemedText style={styles.bell}>🔔</ThemedText>
          <ThemedText type="subtitle" accessibilityRole="header" style={styles.center}>
            Suivez vos activités et vos lieux préférés
          </ThemedText>
          <ThemedText themeColor="textSecondary" style={styles.center}>
            Avec la cloche « Suivre », Neiri vous prévient dès qu’une activité ou un lieu qui vous intéresse change.
          </ThemedText>
        </View>

        <ThemedText type="heading">Ce que vous pouvez suivre</ThemedText>
        {FEATURES.map((feature) => (
          <Card key={feature.title} style={styles.feature}>
            <View style={[styles.icon, { backgroundColor: theme.sand }]}>
              <ThemedText style={styles.iconText}>{feature.icon}</ThemedText>
            </View>
            <View style={styles.featureText}>
              <ThemedText type="smallBold">{feature.title}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {feature.text}
              </ThemedText>
            </View>
          </Card>
        ))}

        <ThemedText type="heading">Comment ça marche</ThemedText>
        {STEPS.map((step, index) => (
          <View key={step} style={styles.step}>
            <View style={[styles.stepNumber, { backgroundColor: theme.primary }]}>
              <ThemedText type="smallBold" style={styles.stepNumberText}>
                {index + 1}
              </ThemedText>
            </View>
            <ThemedText style={styles.stepText}>{step}</ThemedText>
          </View>
        ))}
        <ThemedText type="small" themeColor="textSecondary">
          Retrouvez tout ce que vous suivez dans Compte → « Activités et lieux suivis ».
        </ThemedText>

        <Card style={styles.keywords}>
          <View style={[styles.badge, styles.badgeStart, { backgroundColor: theme.accent }]}>
            <ThemedText type="smallBold" style={styles.badgeText}>
              NOUVEAU AUSSI
            </ThemedText>
          </View>
          <ThemedText type="heading" accessibilityRole="header">
            🔎 Des alertes sur mesure, avec vos mots-clés
          </ThemedText>
          <ThemedText>
            Ajoutez des mots-clés ou des expressions à vos alertes : vous ne recevez que les nouveautés qui en parlent
            vraiment. Combinez-les avec des catégories (Musique et « jazz »), ou utilisez-les seuls pour chercher dans
            toutes les catégories.
          </ThemedText>
          <View style={styles.examples}>
            {KEYWORD_EXAMPLES.map((example) => (
              <View key={example} style={[styles.example, { backgroundColor: theme.primary }]}>
                <ThemedText type="small" style={{ color: theme.onPrimary }}>
                  {example}
                </ThemedText>
              </View>
            ))}
          </View>
          <ThemedText type="small" themeColor="textSecondary">
            Jusqu’à 10 mots-clés par alerte, trouvés dans le titre ou la description d’une activité, sans tenir compte
            des majuscules ni des accents. À régler dans « Mes alertes », à la création ou avec le bouton « Mots-clés ».
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Réservé à Premium : si votre offre se termine, une alerte à mots-clés est mise en pause. Retirez ses
            mots-clés et elle repart aussitôt.
          </ThemedText>
        </Card>

        <View style={[styles.premium, { backgroundColor: theme.accentSoft, borderColor: theme.accent }]}>
          <View style={styles.free}>
            <ThemedText type="smallBold" style={styles.freeText}>
              🎁 Gratuit pour le moment
            </ThemedText>
          </View>
          <ThemedText type="heading">
            {hasAccess ? 'Vous en profitez déjà' : 'Deux fonctionnalités Premium, offertes en ce moment'}
          </ThemedText>
          {hasAccess ? (
            <>
              <ThemedText>
                {justSubscribed ? 'C’est fait : vous êtes Premium ! ' : ''}
                Ouvrez une fiche et touchez 🔔 « Suivre », ou ajoutez des mots-clés à vos alertes.
              </ThemedText>
              <Button label="Explorer la carte" onPress={() => router.navigate('/')} />
              <Button label="Mes suivis" variant="secondary" onPress={() => router.push('/suivis')} />
            </>
          ) : (
            <>
              <ThemedText>
                Suivre des activités et des lieux et personnaliser ses alertes avec des mots-clés sont réservés à l’offre
                Premium. Pour l’instant, Premium est gratuit :
                aucun paiement n’est demandé, il s’active tout de suite et vous pouvez le résilier à tout moment dans
                « Mon abonnement ».
              </ThemedText>
              {PERKS.map((perk) => (
                <View key={perk} style={[styles.perk, { backgroundColor: theme.backgroundElement }]}>
                  <ThemedText type="small">
                    {perk}
                  </ThemedText>
                </View>
              ))}
              {!authenticated ? (
                <>
                  <Button label="Créer un compte ou se connecter" onPress={() => router.navigate('/compte')} />
                  <ThemedText type="small" themeColor="textSecondary">
                    Une fois connecté, revenez ici pour passer à Premium en un geste.
                  </ThemedText>
                </>
              ) : planState.kind === 'loading' ? (
                <ThemedText type="small" themeColor="textSecondary">
                  Chargement de votre offre…
                </ThemedText>
              ) : (
                <>
                  <Button label="Passer à Premium gratuitement" loading={subscribing} onPress={() => void subscribe()} />
                  <Button label="Voir les offres" variant="secondary" onPress={() => router.push('/abonnement')} />
                </>
              )}
              {error && <Notice tone="danger">{error}</Notice>}
            </>
          )}
        </View>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  keywords: {
    gap: Spacing.three,
    padding: Spacing.four,
    marginTop: Spacing.three,
  },
  badgeStart: {
    alignSelf: 'flex-start',
  },
  examples: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  example: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: 999,
  },
  container: {
    flex: 1,
  },
  content: {
    gap: Spacing.three,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  hero: {
    alignItems: 'center',
    gap: Spacing.two,
    marginBottom: Spacing.two,
  },
  center: {
    textAlign: 'center',
  },
  badge: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.half,
    borderRadius: 999,
  },
  badgeText: {
    color: '#FFFFFF',
    letterSpacing: 1,
  },
  bell: {
    fontSize: 52,
    lineHeight: 64,
  },
  feature: {
    flexDirection: 'row',
    gap: Spacing.three,
    padding: Spacing.three,
  },
  icon: {
    width: 44,
    height: 44,
    borderRadius: Radius.md,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconText: {
    fontSize: 22,
    lineHeight: 28,
  },
  featureText: {
    flex: 1,
    gap: Spacing.half,
  },
  step: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  stepNumber: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepNumberText: {
    color: '#FFFFFF',
  },
  stepText: {
    flex: 1,
  },
  premium: {
    gap: Spacing.three,
    marginTop: Spacing.three,
    padding: Spacing.four,
    borderRadius: Radius.xl,
    borderWidth: 1.5,
  },
  free: {
    alignSelf: 'flex-start',
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: 999,
    backgroundColor: '#FBA91E',
  },
  freeText: {
    color: Brand.ink,
  },
  perk: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    borderRadius: Radius.md,
  },
});
