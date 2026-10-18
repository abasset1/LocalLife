import { Link } from "react-router-dom";
import type { ExplorerCurrentUser } from "./types";

interface ExplorerMenuProps {
    isOpen: boolean;
    onClose: () => void;
    currentUser: ExplorerCurrentUser | null;
    onOpenContributionForm: () => void;
    onLogout: () => void;
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
function ExplorerMenu({ isOpen, onClose, currentUser, onOpenContributionForm, onLogout }: ExplorerMenuProps) {
    return (
        <>
            {/* Rétro-plan cliquable, mobile uniquement (voir styles.css) : ferme le tiroir. */}
            <div
                className={isOpen ? "explorer-menu-backdrop explorer-menu-backdrop--visible" : "explorer-menu-backdrop"}
                onClick={onClose}
                role="presentation"
            />
            <nav
                aria-label="Menu LocalLife"
                className={isOpen ? "explorer-menu explorer-menu--open" : "explorer-menu"}
            >
                <div className="explorer-menu-brand">
                    <span className="explorer-menu-brand-mark">L</span>
                    LocalLife
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
                            {currentUser.role === "PRO" && (
                                <Link className="explorer-menu-link" onClick={onClose} to="/pro">
                                    Espace professionnel
                                </Link>
                            )}
                            {currentUser.role === "ADMIN" && (
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
            </nav>
        </>
    );
}

export default ExplorerMenu;
