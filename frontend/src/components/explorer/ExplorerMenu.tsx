import { BrandLogo } from "../BrandLogo";
import { Link } from "react-router-dom";
import { LEGAL_DOCUMENTS } from "../../legal/legalDocuments";
import { canModerate } from "../../auth/roles";
import type { ExplorerCurrentUser } from "./types";
import { useLaunchFeatures } from "../../features/launchFeatures";

interface ExplorerMenuProps {
    isOpen: boolean;
    onClose: () => void;
    currentUser: ExplorerCurrentUser | null;
    /** `hasProAccess` de `GET /api/v1/users/me` : affiche l'entrée « Espace professionnel ». */
    hasProAccess?: boolean;
    onOpenContributionForm: () => void;
    onLogout: () => void;
    /** « Une idée, un problème ? » : formulaire de suggestion ou de signalement d'anomalie. */
    onOpenFeedback?: () => void;
}

/**
 * LL-EF-017, section 5 : les fonctionnalités qui ne sont pas nécessaires à
 * la découverte immédiate (carte/filtres/recherche) sortent de l'écran
 * principal et vivent ici — colonne fixe en desktop (voir `.explorer-shell`
 * dans styles.css), tiroir hors-écran en mobile (ouvert via `isOpen`).
 *
 * Seules les entrées correspondant à une fonctionnalité déjà existante du
 * produit sont listées (Explorer, Mes favoris, Proposer une activité, Mon
 * profil, Administration, Connexion/Déconnexion) — "Amis" et "Mon agenda"
 * de la maquette du ticket ne correspondent à aucune page/route existante
 * et ne sont donc pas ajoutés ici (hors périmètre de ce ticket).
 */
function ExplorerMenu({
    isOpen, onClose, currentUser, hasProAccess = false, onOpenContributionForm, onLogout, onOpenFeedback,
}: ExplorerMenuProps) {
    // Lancement (05/11/2026) : proposer une activité demande une offre Premium ou Pro, la page « Nouveautés »
    // présente Premium ; tant que ces offres sont fermées, ces entrées sont masquées.
    const features = useLaunchFeatures();
    const canPropose = features.premium || features.pro;
    return (
        <>
            {/* Rétro-plan cliquable, mobile uniquement (voir styles.css) : ferme le tiroir. */}
            <div
                className={isOpen ? "explorer-menu-backdrop explorer-menu-backdrop--visible" : "explorer-menu-backdrop"}
                onClick={onClose}
                role="presentation"
            />
            <nav
                aria-label="Menu Neiri"
                className={isOpen ? "explorer-menu explorer-menu--open" : "explorer-menu"}
            >
                <div className="explorer-menu-brand">
                    <BrandLogo tone="light" />
                </div>

                <div className="explorer-menu-section">
                    <span className="explorer-menu-section-title">Découvrir</span>
                    <Link className="explorer-menu-link explorer-menu-link-active" onClick={onClose} to="/">
                        Explorer
                    </Link>
                    {currentUser && (
                        <Link className="explorer-menu-link" onClick={onClose} to="/favorites">
                            Mes favoris
                        </Link>
                    )}
                </div>

                <div className="explorer-menu-section">
                    <span className="explorer-menu-section-title">Communauté</span>
                    {canPropose && (
                        <button
                            className="explorer-menu-link explorer-menu-link-button"
                            onClick={() => {
                                onClose();
                                onOpenContributionForm();
                            }}
                            type="button"
                        >
                            Proposer une activité
                        </button>
                    )}
                    {currentUser && (
                        <Link className="explorer-menu-link" onClick={onClose} to="/contributions">
                            Mes contributions
                        </Link>
                    )}
                </div>

                <div className="explorer-menu-section">
                    <span className="explorer-menu-section-title">Compte</span>
                    {currentUser ? (
                        <>
                            <span className="explorer-menu-user">{currentUser.email}</span>
                            <Link className="explorer-menu-link" onClick={onClose} to="/profile">
                                Mon profil
                            </Link>
                            {/* Accès pro relu sur le serveur ; sans accès, l'offre Pro se demande depuis « Mon profil ». */}
                            {hasProAccess && (
                                <Link className="explorer-menu-link" onClick={onClose} to="/pro">
                                    Espace professionnel
                                </Link>
                            )}
                            {canModerate(currentUser.role) && (
                                <Link className="explorer-menu-link" onClick={onClose} to="/admin">
                                    Administration
                                </Link>
                            )}
                            <button
                                className="explorer-menu-link explorer-menu-link-button"
                                onClick={() => {
                                    onClose();
                                    onLogout();
                                }}
                                type="button"
                            >
                                Déconnexion
                            </button>
                        </>
                    ) : (
                        <Link className="explorer-menu-link" onClick={onClose} to="/login">
                            Se connecter
                        </Link>
                    )}
                </div>

                <div className="explorer-menu-section">
                    <span className="explorer-menu-section-title">Aide</span>
                    {features.premium && (
                        <Link className="explorer-menu-link" onClick={onClose} to="/nouveautes">
                            Nouveautés <span className="explorer-menu-new">Nouveau</span>
                        </Link>
                    )}
                    <Link className="explorer-menu-link" onClick={onClose} to="/guide">
                        Guide de l'application
                    </Link>
                    {onOpenFeedback && (
                        <button
                            className="explorer-menu-link explorer-menu-link-button"
                            onClick={() => {
                                onClose();
                                onOpenFeedback();
                            }}
                            type="button"
                        >
                            Une idée, un problème ?
                        </button>
                    )}
                </div>

                {/* Pied du menu : textes légaux (contrat RGPD). */}
                <div className="explorer-menu-legal">
                    {Object.values(LEGAL_DOCUMENTS).map((legalDocument) => (
                        <Link key={legalDocument.path} onClick={onClose} to={legalDocument.path}>
                            {legalDocument.title}
                        </Link>
                    ))}
                </div>
            </nav>
        </>
    );
}

export default ExplorerMenu;
