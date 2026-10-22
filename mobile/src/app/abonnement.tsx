import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView } from 'react-native';

import { cancelSubscription, getSubscription, subscribePlan, withdrawPlanRequest } from '@/api';
import type { Plan, SubscriptionOverview } from '@/api';
import { useAuth } from '@/auth';
import { Button } from '@/components/button';
import { Card } from '@/components/card';
import { Notice } from '@/components/notice';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { PLAN_DESCRIPTIONS, PLAN_LABELS, describeSubscription, formatDay } from '@/features/pro/format';
import { messageFor, proStyles as styles } from '@/features/pro/ProGate';

const CHOICES: Exclude<Plan, 'FREE'>[] = ['PREMIUM', 'PRO'];

/**
 * « Mon abonnement » (BILLING-01, ADR-0014), partagé avec le site web : offre actuelle et fin d'accès, passage à
 * Premium (activé tout de suite : pas de paiement branché pour l'instant) ou demande d'offre Pro (examinée par
 * l'équipe, décision du 21/10/2026), retrait d'une demande, résiliation. La carte et la liste restent gratuites.
 */
export default function AbonnementScreen() {
  const { state } = useAuth();
  const authenticated = state.status === 'authenticated';
  const [overview, setOverview] = useState<SubscriptionOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!authenticated) return;
    const controller = new AbortController();
    getSubscription({ signal: controller.signal })
      .then(setOverview)
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setError(messageFor(e));
      });
    return () => controller.abort();
  }, [authenticated]);

  const run = useCallback(async (action: () => Promise<SubscriptionOverview>, success: string) => {
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      setOverview(await action());
      setInfo(success);
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusy(false);
    }
  }, []);

  if (!authenticated) {
    return (
      <ThemedView style={styles.centered}>
        <ThemedText style={styles.centerText}>
          Connectez-vous depuis la rubrique Compte du menu pour gérer votre abonnement.
        </ThemedText>
      </ThemedView>
    );
  }
  if (!overview) {
    return (
      <ThemedView style={styles.centered}>
        {error ? <Notice tone="danger">{error}</Notice> : <ActivityIndicator accessibilityLabel="Chargement" />}
      </ThemedView>
    );
  }

  const { plan, current, pending } = overview;
  const lastRejected = overview.history.find((item) => item.status === 'REJECTED');

  function confirmCancel() {
    const end = current?.accessEndsAt;
    Alert.alert(
      'Résilier ?',
      end
        ? `Votre accès ${PLAN_LABELS[plan]} durera jusqu’au ${formatDay(end)}.`
        : plan === 'PRO'
          ? // Offre Pro sans échéance : l'accès dure jusqu'au 1er du mois suivant (décision du 22/10/2026).
            'Votre accès Pro durera jusqu’à la fin du mois ; ensuite, vos food trucks publiés seront masqués.'
          : `Votre accès ${PLAN_LABELS[plan]} s’arrêtera tout de suite.`,
      [
        { text: 'Annuler', style: 'cancel' },
        { text: 'Résilier', style: 'destructive', onPress: () => void run(cancelSubscription, 'Abonnement résilié.') },
      ],
    );
  }

  return (
    <ThemedView style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <Card style={styles.card}>
          <ThemedText type="eyebrow">Offre actuelle</ThemedText>
          <ThemedText type="subtitle" accessibilityRole="header">
            {PLAN_LABELS[plan]}
          </ThemedText>
          <ThemedText themeColor="textSecondary">{describeSubscription(overview)}</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {PLAN_DESCRIPTIONS[plan]}
          </ThemedText>
        </Card>

        {pending && (
          <Card style={styles.card}>
            <Notice tone="info">
              Demande de passage à {PLAN_LABELS[pending.plan]} envoyée le {formatDay(pending.requestedAt)} : l’équipe
              LocalLife va l’examiner.
            </Notice>
            <Button
              label="Retirer ma demande"
              variant="secondary"
              loading={busy}
              onPress={() => void run(withdrawPlanRequest, 'Demande retirée.')}
            />
          </Card>
        )}
        {CHOICES.filter((choice) => !(choice === plan && current?.status === 'ACTIVE'))
          .filter((choice) => pending?.plan !== choice)
          .map((choice) => (
          <Card key={choice} style={styles.card}>
            <ThemedText type="heading">{PLAN_LABELS[choice]}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {PLAN_DESCRIPTIONS[choice]}
            </ThemedText>
            {choice === 'PREMIUM' && plan === 'PRO' ? (
              <ThemedText type="small" themeColor="textSecondary">
                Votre offre Pro inclut déjà Premium.
              </ThemedText>
            ) : null}
            <Button
              label={choice === 'PRO' ? 'Demander l’offre Pro' : `Passer à ${PLAN_LABELS[choice]}`}
              loading={busy}
              onPress={() =>
                void run(
                  () => subscribePlan(choice),
                  choice === 'PRO'
                    ? 'Demande d’offre Pro envoyée : l’équipe LocalLife va l’examiner.'
                    : `Offre ${PLAN_LABELS[choice]} activée.`,
                )
              }
            />
          </Card>
        ))}

        {current?.status === 'ACTIVE' && (
          <Button label="Résilier mon abonnement" variant="danger" loading={busy} onPress={confirmCancel} />
        )}
        {lastRejected?.decisionReason && !pending ? (
          <ThemedText type="small" themeColor="textSecondary">
            Dernière demande refusée : {lastRejected.decisionReason}
          </ThemedText>
        ) : null}
        <ThemedText type="small" themeColor="textSecondary">
          Paiement en ligne bientôt disponible ; pour l’instant, Premium est activé sans paiement et une demande Pro
          est validée par l’équipe. La carte et la liste restent gratuites quelle que soit l’offre.
        </ThemedText>
        {info && <Notice tone="success">{info}</Notice>}
        {error && <Notice tone="danger">{error}</Notice>}
      </ScrollView>
    </ThemedView>
  );
}
