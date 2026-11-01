import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getPayload } from "../auth/authStorage";
import { proRequest } from "../components/pro/proApi";
import type { Plan, SubscriptionOverview } from "../components/subscription/types";

type PlanState =
    | { kind: "visitor" }
    | { kind: "loading" }
    | { kind: "loaded"; plan: Plan }
    | { kind: "error" };

const FEATURES = [
    {
        icon: "📅",
        title: "Une activité",
        text: "Les dates changent, l'adresse bouge, l'événement est annulé ou ses informations sont mises à jour : tu le "
            + "sais tout de suite, sans avoir à revenir vérifier.",
    },
    {
        icon: "🏛",
        title: "Un lieu",
        text: "Ton musée, ta salle de concert ou ton parc préféré programme une nouvelle activité, change d'adresse ou "
            + "de coordonnées : tu es prévenu.",
    },
    {
        icon: "📲",
        title: "Au bon moment",
        text: "Une notification sur ton téléphone si tu as l'application LocalLife, sinon un e-mail. Un seul message "
            + "qui regroupe tout, au plus une fois par heure, et jamais la nuit.",
    },
];

const STEPS = [
    "Ouvre la fiche d'une activité, ou la fiche d'un lieu dans la liste « Lieux ».",
    "Touche 🔔 « Suivre ».",
    "C'est tout : LocalLife te prévient dès qu'il y a du nouveau.",
];

/**
 * Page « Nouveautés » (`/nouveautes`) : présente aux membres la cloche « Suivre » (activités et lieux, PR #188) et les
 * alertes par mots-clés (PR #191, #192), des fonctionnalités Premium et Pro, et propose de passer à Premium — gratuit pour le moment, aucun paiement n'étant
 * branché (BILLING-01). Le bouton active Premium tout de suite (`POST /users/me/subscription/subscribe`) ; un visiteur
 * est invité à créer un compte, un membre Premium ou Pro voit qu'il en profite déjà.
 */
function WhatsNewPage() {
    const [planState, setPlanState] = useState<PlanState>(() => (getPayload() ? { kind: "loading" } : { kind: "visitor" }));
    const [isSubscribing, setIsSubscribing] = useState(false);
    const [subscribeError, setSubscribeError] = useState<string | null>(null);
    const [justSubscribed, setJustSubscribed] = useState(false);

    useEffect(() => {
        if (!getPayload()) {
            return;
        }
        let isCurrent = true;
        void proRequest<SubscriptionOverview>("/api/v1/users/me/subscription").then((result) => {
            if (isCurrent) {
                setPlanState(result.ok ? { kind: "loaded", plan: result.data.plan } : { kind: "error" });
            }
        });
        return () => {
            isCurrent = false;
        };
    }, []);

    async function subscribe() {
        setIsSubscribing(true);
        setSubscribeError(null);
        const result = await proRequest<SubscriptionOverview>("/api/v1/users/me/subscription/subscribe", "POST",
            { plan: "PREMIUM" });
        setIsSubscribing(false);
        if (result.ok) {
            setPlanState({ kind: "loaded", plan: result.data.plan });
            setJustSubscribed(true);
        } else {
            setSubscribeError(result.message);
        }
    }

    const hasAccess = planState.kind === "loaded" && planState.plan !== "FREE";

    return (
        <div className="news">
            <header className="news-topbar">
                <Link className="guide-brand" to="/">
                    <span className="explorer-menu-brand-mark">L</span>
                    LocalLife
                </Link>
                <Link className="news-back" to="/">← Retour à la carte</Link>
            </header>

            <main className="news-main">
                <section className="news-hero">
                    <p className="news-badge">Nouveau</p>
                    <div aria-hidden="true" className="news-bell">🔔</div>
                    <h1>Suis tes activités et tes lieux préférés</h1>
                    <p className="news-lead">
                        Avec la cloche <strong>« Suivre »</strong>, LocalLife te prévient dès qu'une activité ou un lieu
                        qui t'intéresse change. Plus besoin de revenir vérifier : les nouvelles viennent à toi.
                    </p>
                </section>

                <section aria-labelledby="news-what-title" className="news-section">
                    <h2 id="news-what-title">Ce que tu peux suivre</h2>
                    <div className="news-cards">
                        {FEATURES.map((feature) => (
                            <article className="news-card" key={feature.title}>
                                <span aria-hidden="true" className="news-card-icon">{feature.icon}</span>
                                <h3>{feature.title}</h3>
                                <p>{feature.text}</p>
                            </article>
                        ))}
                    </div>
                </section>

                <section aria-labelledby="news-how-title" className="news-section">
                    <h2 id="news-how-title">Comment ça marche</h2>
                    <ol className="news-steps">
                        {STEPS.map((step) => <li key={step}>{step}</li>)}
                    </ol>
                    <p className="news-hint">
                        Tu retrouves tout ce que tu suis dans « Mon profil » → « Activités et lieux suivis », et tu
                        arrêtes un suivi en un clic.
                    </p>
                </section>

                <section aria-labelledby="news-keywords-title" className="news-section news-keywords">
                    <p className="news-badge">Nouveau aussi</p>
                    <h2 id="news-keywords-title">🔎 Des alertes sur mesure, avec tes mots-clés</h2>
                    <p>
                        Ajoute des mots-clés ou des expressions à tes alertes : tu ne reçois que les nouveautés qui en
                        parlent vraiment. Combine-les avec des catégories (Musique <strong>et</strong> « jazz »), ou
                        utilise-les seuls pour chercher dans toutes les catégories.
                    </p>
                    <ul aria-label="Exemples de mots-clés" className="news-keyword-examples">
                        <li>jazz</li>
                        <li>marché de Noël</li>
                        <li>randonnée</li>
                        <li>atelier enfants</li>
                    </ul>
                    <p className="news-hint">
                        Jusqu'à 10 mots-clés par alerte, trouvés dans le titre ou la description d'une activité, sans
                        tenir compte des majuscules ni des accents : « noel » trouve aussi « Noël ». À régler dans
                        « Mon profil » → « Mes alertes », au moment de créer une alerte ou avec le bouton « Mots-clés ».
                    </p>
                    <p className="news-hint">
                        Réservé à Premium : si ton offre se termine, une alerte à mots-clés est mise en pause. Retire
                        ses mots-clés et elle repart aussitôt.
                    </p>
                </section>

                <section aria-labelledby="news-premium-title" className="news-premium">
                    <p className="news-free">🎁 Gratuit pour le moment</p>
                    <h2 id="news-premium-title">
                        {hasAccess ? "Tu en profites déjà" : "Deux fonctionnalités Premium, offertes en ce moment"}
                    </h2>

                    {hasAccess ? (
                        <>
                            <p>
                                {justSubscribed
                                    ? "C'est fait : tu es Premium ! "
                                    : `Ton offre ${planState.kind === "loaded" && planState.plan === "PRO" ? "Pro" : "Premium"} `
                                        + "comprend le suivi des activités et des lieux, et les alertes par mots-clés. "}
                                Ouvre une fiche et touche 🔔 « Suivre », ou ajoute des mots-clés à tes alertes.
                            </p>
                            <div className="news-actions">
                                <Link className="pp-button pp-button-primary" to="/">Explorer la carte</Link>
                                <Link className="pp-button pp-button-secondary" to="/profile">Mes suivis</Link>
                            </div>
                        </>
                    ) : (
                        <>
                            <p>
                                Suivre des activités et des lieux et personnaliser ses alertes avec des mots-clés sont
                                réservés à l'offre Premium. Pour l'instant, Premium
                                est <strong>gratuit</strong> : aucun paiement n'est demandé, il s'active tout de suite et
                                tu peux le résilier à tout moment depuis « Mon profil ».
                            </p>
                            <ul className="news-perks">
                                <li>🔔 Suivi des activités et des lieux</li>
                                <li>❤️ Favoris illimités (5 avec le compte gratuit)</li>
                                <li>✉️ Jusqu'à 10 alertes (1 avec le compte gratuit)</li>
                                <li>🔎 Alertes personnalisées par mots-clés</li>
                                <li>✍️ 2 propositions d'activités par mois</li>
                            </ul>
                            {planState.kind === "visitor" && (
                                <div className="news-actions">
                                    <Link className="pp-button pp-button-primary" to="/register">Créer mon compte</Link>
                                    <Link className="pp-button pp-button-secondary" to="/login">Me connecter</Link>
                                </div>
                            )}
                            {planState.kind === "visitor" && (
                                <p className="news-hint">
                                    Une fois connecté, reviens sur cette page pour passer à Premium en un clic.
                                </p>
                            )}
                            {planState.kind === "loading" && <p className="news-hint">Chargement de ton offre…</p>}
                            {(planState.kind === "loaded" || planState.kind === "error") && (
                                <div className="news-actions">
                                    <button
                                        className="pp-button pp-button-primary news-cta"
                                        disabled={isSubscribing}
                                        onClick={() => void subscribe()}
                                        type="button"
                                    >
                                        {isSubscribing ? "Activation…" : "Passer à Premium gratuitement"}
                                    </button>
                                    <Link className="pp-button pp-button-secondary" to="/profile">Voir les offres</Link>
                                </div>
                            )}
                            {subscribeError && (
                                <p className="news-error" role="alert">
                                    {subscribeError} Tu peux aussi passer à Premium depuis « Mon profil ».
                                </p>
                            )}
                        </>
                    )}
                </section>
            </main>
        </div>
    );
}

export default WhatsNewPage;
