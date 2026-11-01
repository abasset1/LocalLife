import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

/** Durée d'affichage d'une étape en lecture automatique (ms). */
export const GUIDE_STEP_DURATION_MS = 9000;

function prefersReducedMotion(): boolean {
    return typeof window !== "undefined"
        && typeof window.matchMedia === "function"
        && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

/** Texte tapé lettre par lettre (texte complet d'emblée si les animations sont réduites). */
function TypingText({ text, delayMs = 600 }: { text: string; delayMs?: number }) {
    const [length, setLength] = useState(() => (prefersReducedMotion() ? text.length : 0));

    useEffect(() => {
        if (prefersReducedMotion()) {
            return;
        }
        let interval: number | undefined;
        const start = window.setTimeout(() => {
            interval = window.setInterval(() => {
                setLength((current) => {
                    if (current >= text.length) {
                        window.clearInterval(interval);
                        return current;
                    }
                    return current + 1;
                });
            }, 90);
        }, delayMs);
        return () => {
            window.clearTimeout(start);
            window.clearInterval(interval);
        };
    }, [text, delayMs]);

    return (
        <span className="guide-typing">
            {text.slice(0, length)}
            <span aria-hidden="true" className="guide-caret" />
        </span>
    );
}

/* --- Scènes animées : maquettes simplifiées de l'interface réelle. ------------------------------- */

function MiniMap({ children }: { children?: ReactNode }) {
    return (
        <div className="guide-map">
            <span className="guide-map-road guide-map-road-1" />
            <span className="guide-map-road guide-map-road-2" />
            <span className="guide-map-road guide-map-road-3" />
            <span className="guide-map-water" />
            {children}
        </div>
    );
}

function Pin({ x, y, delay, emoji = "📍", className = "" }: {
    x: number; y: number; delay: number; emoji?: string; className?: string;
}) {
    return (
        <span
            className={`guide-pin ${className}`}
            style={{ left: `${x}%`, top: `${y}%`, animationDelay: `${delay}s` }}
        >
            {emoji}
        </span>
    );
}

function WelcomeScene() {
    return (
        <div className="guide-scene guide-scene-welcome">
            <div className="guide-logo">L</div>
            <div className="guide-orbit">
                {["🎭", "🎵", "🏃", "🍽️", "🎨", "🚚"].map((emoji, index) => (
                    <span className="guide-orbit-item" key={emoji} style={{ animationDelay: `${0.2 + index * 0.15}s`, ["--i" as string]: index }}>
                        {emoji}
                    </span>
                ))}
            </div>
        </div>
    );
}

function MapScene() {
    return (
        <div className="guide-scene">
            <MiniMap>
                <Pin delay={0.3} x={22} y={30} />
                <Pin delay={0.5} x={48} y={55} />
                <Pin delay={0.7} x={70} y={28} />
                <Pin className="guide-pin-selected" delay={0.9} x={60} y={66} />
                <span className="guide-cluster" style={{ left: "84%", top: "52%" }}>12</span>
                <span className="guide-cursor guide-cursor-map" aria-hidden="true">👆</span>
                <div className="guide-preview-card">
                    <span className="guide-thumb">🎵</span>
                    <div>
                        <strong>Concert au port</strong>
                        <small>Sam. 20:30 · Marseille</small>
                        <span className="guide-mini-button">Voir l'activité</span>
                    </div>
                </div>
            </MiniMap>
        </div>
    );
}

function SearchScene() {
    return (
        <div className="guide-scene guide-scene-column">
            <div className="guide-searchbar">
                <span aria-hidden="true">🔍</span>
                <TypingText text="marché de Noël Aix" />
            </div>
            <ul className="guide-results">
                {[
                    ["🎄", "Marché de Noël", "Aix-en-Provence · 1er déc."],
                    ["🎁", "Village des créateurs", "Aix-en-Provence · 7 déc."],
                    ["🏙️", "Aix-en-Provence", "Ville — recentrer la carte"],
                ].map(([emoji, title, meta], index) => (
                    <li className="guide-result" key={title} style={{ animationDelay: `${2.6 + index * 0.25}s` }}>
                        <span className="guide-thumb">{emoji}</span>
                        <div>
                            <strong>{title}</strong>
                            <small>{meta}</small>
                        </div>
                    </li>
                ))}
            </ul>
        </div>
    );
}

function FiltersScene() {
    return (
        <div className="guide-scene guide-scene-column">
            <div className="guide-chip-row">
                {["Culture", "Musique", "Sport", "Famille", "Gastronomie"].map((label, index) => (
                    <span
                        className={index === 1 || index === 3 ? "guide-chip guide-chip-toggle" : "guide-chip"}
                        key={label}
                        style={{ animationDelay: `${0.8 + index * 0.4}s` }}
                    >
                        {label}
                    </span>
                ))}
            </div>
            <div className="guide-chip-row">
                <span className="guide-chip guide-chip-toggle" style={{ animationDelay: "2.8s" }}>📅 Ce week-end</span>
                <span className="guide-chip">Cette semaine</span>
                <span className="guide-chip">Ce mois-ci</span>
            </div>
            <div className="guide-radius">
                <span>Rayon</span>
                <div className="guide-radius-track"><span className="guide-radius-fill" /></div>
                <strong>10 km</strong>
            </div>
            <div className="guide-chip-row">
                <span className="guide-chip guide-chip-toggle" style={{ animationDelay: "4.4s" }}>📍 Autour de moi</span>
                <span className="guide-segmented">
                    <span className="guide-segment guide-segment-a">🗺 Carte</span>
                    <span className="guide-segment guide-segment-b">☰ Liste</span>
                </span>
            </div>
        </div>
    );
}

function LayersScene() {
    return (
        <div className="guide-scene">
            <MiniMap>
                <div className="guide-layer-set guide-layer-activities">
                    <Pin delay={0.2} emoji="📅" x={25} y={35} />
                    <Pin delay={0.3} emoji="📅" x={55} y={60} />
                    <Pin delay={0.4} emoji="📅" x={72} y={30} />
                </div>
                <div className="guide-layer-set guide-layer-places">
                    <Pin delay={3.2} emoji="🏛" x={35} y={50} />
                    <Pin delay={3.3} emoji="🏰" x={65} y={40} />
                    <Pin delay={3.4} emoji="🏟" x={45} y={25} />
                </div>
                <div className="guide-layer-set guide-layer-trucks">
                    <Pin delay={6.1} emoji="🚚" x={30} y={62} />
                    <Pin delay={6.2} emoji="🚚" x={68} y={55} />
                </div>
                <div className="guide-layer-switch">
                    <span className="guide-layer-button guide-layer-button-1">📅 Activités</span>
                    <span className="guide-layer-button guide-layer-button-2">🏛 Lieux</span>
                    <span className="guide-layer-button guide-layer-button-3">🚚 Food trucks</span>
                </div>
            </MiniMap>
        </div>
    );
}

function FavoritesScene() {
    return (
        <div className="guide-scene">
            <div className="guide-detail-card">
                <div className="guide-detail-image">🎭</div>
                <div className="guide-detail-body">
                    <strong>Festival de théâtre de rue</strong>
                    <small>📅 Du 12 au 14 juillet · 📍 Avignon</small>
                    <span className="guide-line" />
                    <span className="guide-line guide-line-short" />
                    <div className="guide-detail-actions">
                        <span className="guide-heart" aria-hidden="true">
                            <span className="guide-heart-empty">♡</span>
                            <span className="guide-heart-full">❤️</span>
                        </span>
                        <span className="guide-mini-button">Site de l'organisateur</span>
                    </div>
                </div>
                <span className="guide-toast">Ajoutée à « Mes favoris »</span>
            </div>
        </div>
    );
}

function AlertsScene() {
    return (
        <div className="guide-scene guide-scene-alerts">
            <div className="guide-sun" aria-hidden="true">☀️</div>
            <div className="guide-envelope" aria-hidden="true">✉️</div>
            <div className="guide-mail">
                <small>Chaque matin · LocalLife</small>
                <strong>3 nouvelles activités près de Marseille</strong>
                {["🎵 Jazz sur le Vieux-Port", "🧒 Atelier famille au musée", "🚚 Food truck place Castellane"].map(
                    (line, index) => (
                        <span className="guide-mail-line" key={line} style={{ animationDelay: `${2.4 + index * 0.4}s` }}>
                            {line}
                        </span>
                    ),
                )}
            </div>
        </div>
    );
}

function ContributeScene() {
    return (
        <div className="guide-scene guide-scene-column">
            <div className="guide-form">
                <label>Titre</label>
                <div className="guide-input"><TypingText delayMs={400} text="Vide-grenier du quartier" /></div>
                <label>Catégorie</label>
                <div className="guide-chip-row">
                    <span className="guide-chip guide-chip-toggle" style={{ animationDelay: "2.8s" }}>Famille</span>
                    <span className="guide-chip">Culture</span>
                </div>
                <label>Date et lieu</label>
                <div className="guide-input guide-input-fill" style={{ animationDelay: "3.4s" }}>
                    Dim. 9:00 · Place Jean-Jaurès
                </div>
                <span className="guide-submit">Envoyer</span>
            </div>
            <div className="guide-status-flow">
                <span className="guide-status guide-status-pending">⏳ En attente de modération</span>
                <span className="guide-arrow">→</span>
                <span className="guide-status guide-status-published">✅ Publiée sur la carte</span>
            </div>
        </div>
    );
}

function ProfileScene() {
    return (
        <div className="guide-scene guide-scene-cards">
            {[
                ["🧭", "Préférences de recherche", "Ville, rayon et catégories par défaut"],
                ["🔔", "Mes alertes", "Gérer, modifier, suspendre"],
                ["⭐", "Mon abonnement", "Gratuit, Premium ou Pro"],
                ["🔒", "Sécurité", "Mot de passe, e-mail"],
                ["📦", "Mes données", "Exporter ou supprimer"],
            ].map(([icon, title, text], index) => (
                <div className="guide-profile-card" key={title} style={{ animationDelay: `${0.3 + index * 0.35}s` }}>
                    <span className="guide-profile-icon">{icon}</span>
                    <div>
                        <strong>{title}</strong>
                        <small>{text}</small>
                    </div>
                </div>
            ))}
        </div>
    );
}

function PlansScene() {
    const plans = [
        { name: "Gratuit", icon: "🌱", items: ["Carte, liste et fiches", "5 favoris", "1 alerte"] },
        { name: "Premium", icon: "⭐", items: ["Favoris illimités", "10 alertes", "2 contributions / mois"] },
        { name: "Pro", icon: "🏢", items: ["Contributions illimitées", "Gérer tes lieux", "Food trucks et planning"] },
    ];
    return (
        <div className="guide-scene guide-scene-plans">
            {plans.map((plan, index) => (
                <div className={index === 2 ? "guide-plan guide-plan-highlight" : "guide-plan"} key={plan.name}
                    style={{ animationDelay: `${0.3 + index * 0.45}s` }}>
                    <span className="guide-plan-icon">{plan.icon}</span>
                    <strong>{plan.name}</strong>
                    <ul>
                        {plan.items.map((item) => <li key={item}>✓ {item}</li>)}
                    </ul>
                </div>
            ))}
        </div>
    );
}

function FinishScene() {
    return (
        <div className="guide-scene guide-scene-welcome">
            <div className="guide-confetti" aria-hidden="true">
                {Array.from({ length: 14 }, (_, index) => (
                    <span key={index} style={{ left: `${5 + index * 6.5}%`, animationDelay: `${(index % 5) * 0.25}s` }} />
                ))}
            </div>
            <div className="guide-logo guide-logo-big">🚀</div>
        </div>
    );
}

/* --- Étapes du didacticiel. ---------------------------------------------------------------------- */

interface GuideStep {
    id: string;
    title: string;
    text: ReactNode;
    tips: string[];
    scene: () => ReactNode;
}

export const GUIDE_STEPS: GuideStep[] = [
    {
        id: "bienvenue",
        title: "Bienvenue sur LocalLife",
        text: "LocalLife rassemble sur une seule carte les sorties près de chez toi : concerts, expositions, "
            + "marchés, sport, activités en famille, lieux à visiter et food trucks.",
        tips: ["Découvrir est gratuit, sans compte", "Un compte débloque favoris, alertes et contributions"],
        scene: WelcomeScene,
    },
    {
        id: "carte",
        title: "Explore la carte",
        text: "Déplace-toi et zoome : la carte charge les activités de la zone affichée. Les nombres regroupent "
            + "plusieurs activités proches ; touche un marqueur pour afficher son aperçu.",
        tips: ["Zoome sur un groupe pour le déplier", "« Voir l'activité » ouvre la fiche complète"],
        scene: MapScene,
    },
    {
        id: "recherche",
        title: "Recherche une sortie ou une ville",
        text: "Tape un mot-clé dans la barre de recherche : un titre, un thème, un lieu. Choisis une ville dans "
            + "les suggestions pour recentrer la carte dessus.",
        tips: ["Les résultats s'affichent sur la carte et dans la liste", "✕ efface la recherche"],
        scene: SearchScene,
    },
    {
        id: "filtres",
        title: "Affine avec les filtres",
        text: "Garde seulement ce qui t'intéresse : catégories, période (ce week-end, cette semaine…), date de "
            + "début, rayon de recherche. « Autour de moi » utilise ta position.",
        tips: ["Sur téléphone, bascule entre la vue Carte et la vue Liste", "Tes filtres se combinent"],
        scene: FiltersScene,
    },
    {
        id: "couches",
        title: "Activités, lieux et food trucks",
        text: "Les boutons au-dessus de la carte changent ce qui est affiché : les activités datées, les lieux "
            + "(musées, monuments, équipements…) ou les food trucks présents aujourd'hui.",
        tips: ["La fiche d'un lieu liste ses activités à venir", "Ton choix est retenu pour ta prochaine visite"],
        scene: LayersScene,
    },
    {
        id: "favoris",
        title: "Garde tes coups de cœur",
        text: "Sur la fiche d'une activité, retrouve les dates, l'adresse, la description et le lien de "
            + "l'organisateur. Le cœur l'ajoute à « Mes favoris », accessibles depuis le menu.",
        tips: ["Connexion requise pour les favoris", "5 favoris avec l'offre gratuite, illimités ensuite"],
        scene: FavoritesScene,
    },
    {
        id: "alertes",
        title: "Reçois des alertes par e-mail",
        text: "Crée une alerte sur tes catégories préférées (et les food trucks) autour d'une ville : chaque "
            + "matin, LocalLife t'envoie les nouveautés. Un lien dans l'e-mail suffit pour se désabonner.",
        tips: ["Proposée dès ta première connexion", "Gère tes alertes depuis « Mon profil »"],
        scene: AlertsScene,
    },
    {
        id: "contribuer",
        title: "Propose une activité",
        text: "Tu organises un événement ? « Proposer une activité » dans le menu : titre, catégories, dates, "
            + "adresse, récurrence. Elle est relue par l'équipe avant d'apparaître sur la carte.",
        tips: ["Suis-les dans « Mes contributions »", "Tu peux modifier ou annuler tes propositions"],
        scene: ContributeScene,
    },
    {
        id: "profil",
        title: "Personnalise ton espace",
        text: "Dans « Mon profil », règle la ville, le rayon et les catégories sur lesquels la carte s'ouvre, "
            + "gère tes alertes, ton abonnement, ta sécurité et tes données personnelles.",
        tips: ["Exporte tes données à tout moment", "Supprime ton compte quand tu veux"],
        scene: ProfileScene,
    },
    {
        id: "offres",
        title: "Choisis ton offre",
        text: "La carte reste gratuite pour tous. Premium élargit favoris, alertes et contributions. Pro ouvre "
            + "l'Espace professionnel : revendiquer ou créer un lieu, gérer ses food trucks et leur planning.",
        tips: ["Change d'offre depuis « Mon profil » → « Mon abonnement »", "L'offre Pro est validée par l'équipe"],
        scene: PlansScene,
    },
    {
        id: "fin",
        title: "À toi de jouer !",
        text: "Tu connais l'essentiel de LocalLife. Retrouve ce guide à tout moment depuis le menu, rubrique "
            + "« Aide ».",
        tips: [],
        scene: FinishScene,
    },
];

/**
 * Didacticiel animé (`/guide`) : présentation pas à pas des possibilités de l'application, avec des
 * scènes animées qui reproduisent l'interface. Lecture automatique (mise en pause au survol, au focus
 * ou via le bouton), navigation au clavier (← →, Espace) et animations désactivées si l'utilisateur
 * préfère réduire les animations.
 */
function GuidePage() {
    const [index, setIndex] = useState(0);
    const [isPlaying, setIsPlaying] = useState(() => !prefersReducedMotion());
    const [isHovered, setIsHovered] = useState(false);
    const step = GUIDE_STEPS[index];
    const isLast = index === GUIDE_STEPS.length - 1;
    const isRunning = isPlaying && !isHovered && !isLast;

    const goTo = useCallback((next: number) => {
        setIndex(Math.max(0, Math.min(GUIDE_STEPS.length - 1, next)));
    }, []);

    useEffect(() => {
        if (!isRunning) {
            return;
        }
        const timer = window.setTimeout(() => goTo(index + 1), GUIDE_STEP_DURATION_MS);
        return () => window.clearTimeout(timer);
    }, [index, isRunning, goTo]);

    useEffect(() => {
        function handleKeyDown(event: KeyboardEvent) {
            const target = event.target as HTMLElement | null;
            if (target && ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName)) {
                return;
            }
            if (event.key === "ArrowRight") {
                goTo(index + 1);
            } else if (event.key === "ArrowLeft") {
                goTo(index - 1);
            } else if (event.key === " " && target?.tagName !== "BUTTON" && target?.tagName !== "A") {
                event.preventDefault();
                setIsPlaying((value) => !value);
            }
        }
        window.addEventListener("keydown", handleKeyDown);
        return () => window.removeEventListener("keydown", handleKeyDown);
    }, [index, goTo]);

    const Scene = step.scene;

    return (
        <div className="guide-page">
            <header className="guide-topbar">
                <Link className="guide-brand" to="/">
                    <span className="explorer-menu-brand-mark">L</span>
                    LocalLife
                </Link>
                <span className="guide-topbar-title">Guide de l'application</span>
                <Link className="guide-skip" to="/">Passer le guide</Link>
            </header>

            <main
                className="guide-main"
                onMouseEnter={() => setIsHovered(true)}
                onMouseLeave={() => setIsHovered(false)}
            >
                <div className="guide-progress" aria-hidden="true">
                    {GUIDE_STEPS.map((item, itemIndex) => (
                        <span className="guide-progress-segment" key={item.id}>
                            <span
                                className={[
                                    "guide-progress-fill",
                                    itemIndex < index ? "guide-progress-done" : "",
                                    itemIndex === index ? "guide-progress-current" : "",
                                    itemIndex === index && !isRunning ? "guide-progress-paused" : "",
                                ].join(" ")}
                                key={itemIndex === index ? `current-${index}` : item.id}
                                style={{ animationDuration: `${GUIDE_STEP_DURATION_MS}ms` }}
                            />
                        </span>
                    ))}
                </div>

                <section aria-labelledby="guide-step-title" aria-live="polite" className="guide-step" key={step.id}>
                    <div className="guide-stage">
                        <Scene />
                    </div>
                    <div className="guide-copy">
                        <p className="guide-counter">Étape {index + 1} sur {GUIDE_STEPS.length}</p>
                        <h1 id="guide-step-title">{step.title}</h1>
                        <p className="guide-text">{step.text}</p>
                        {step.tips.length > 0 && (
                            <ul className="guide-tips">
                                {step.tips.map((tip) => <li key={tip}>{tip}</li>)}
                            </ul>
                        )}
                        {isLast && (
                            <div className="guide-cta">
                                <Link className="pp-button pp-button-primary" to="/">Explorer la carte</Link>
                                <Link className="pp-button pp-button-secondary" to="/register">Créer un compte</Link>
                                <button className="pp-button pp-button-secondary" onClick={() => goTo(0)} type="button">
                                    Revoir le guide
                                </button>
                            </div>
                        )}
                    </div>
                </section>

                <nav aria-label="Navigation du guide" className="guide-controls">
                    <button
                        className="guide-nav-button"
                        disabled={index === 0}
                        onClick={() => goTo(index - 1)}
                        type="button"
                    >
                        ← Précédent
                    </button>
                    <div className="guide-dots">
                        {GUIDE_STEPS.map((item, itemIndex) => (
                            <button
                                aria-current={itemIndex === index ? "step" : undefined}
                                aria-label={`Étape ${itemIndex + 1} : ${item.title}`}
                                className={itemIndex === index ? "guide-dot guide-dot-active" : "guide-dot"}
                                key={item.id}
                                onClick={() => goTo(itemIndex)}
                                type="button"
                            />
                        ))}
                    </div>
                    <div className="guide-controls-right">
                        <button
                            aria-label={isPlaying ? "Mettre en pause" : "Lecture automatique"}
                            className="guide-nav-button guide-play-button"
                            disabled={isLast}
                            onClick={() => setIsPlaying((value) => !value)}
                            type="button"
                        >
                            {isPlaying ? "❚❚" : "▶"}
                        </button>
                        <button
                            className="guide-nav-button guide-nav-primary"
                            disabled={isLast}
                            onClick={() => goTo(index + 1)}
                            type="button"
                        >
                            Suivant →
                        </button>
                    </div>
                </nav>
            </main>
        </div>
    );
}

export default GuidePage;
