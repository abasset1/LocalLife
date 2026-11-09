/**
 * Contenu des trois textes légaux (mentions légales, conditions d'utilisation, politique de confidentialité).
 *
 * Rédigé le 05/11/2026 à partir de ce que fait réellement le code (`docs/02_Architecture/RGPD.md`,
 * `RetentionService`, `PlanLimits`, ADR-0012 à 0014). **Projet à faire relire par un avocat avant publication
 * définitive.**
 *
 * Les informations que seul l'éditeur peut fournir sont regroupées dans {@link LEGAL_EDITOR} : tant qu'une valeur
 * commence par « [À compléter », elle s'affiche surlignée sur le site. Tout changement de fond de ces textes doit
 * s'accompagner d'une nouvelle {@link LEGAL_VERSION} (ici et dans `LegalTerms.VERSION` côté serveur).
 */
import type { LegalDocumentKey } from "./legalDocuments";

/** Identité de l'éditeur et des prestataires : à renseigner par l'éditeur. */
export const LEGAL_EDITOR = {
    /** Personne physique (nom, prénom) ou dénomination sociale. */
    name: "[À compléter : nom et prénom de l'éditeur, ou dénomination sociale]",
    /** Statut : particulier, entrepreneur individuel (micro-entreprise), SAS, SARL… et capital social le cas échéant. */
    legalForm: "[À compléter : statut juridique et, pour une société, capital social]",
    address: "[À compléter : adresse postale du domicile ou du siège]",
    /** SIREN/SIRET et RCS ou RNE ; « Non applicable » pour un particulier sans activité professionnelle. */
    registration: "[À compléter : numéro SIREN/SIRET et immatriculation (RCS ou RNE)]",
    vat: "[À compléter : numéro de TVA intracommunautaire, ou « Non applicable »]",
    phone: "[À compléter : numéro de téléphone]",
    contactEmail: "[À compléter : adresse e-mail de contact]",
    /** Adresse dédiée aux demandes relatives aux données personnelles (peut être la même que le contact). */
    privacyEmail: "[À compléter : adresse e-mail pour les données personnelles]",
    publicationDirector: "[À compléter : nom et prénom du directeur de la publication]",
    siteUrl: "[À compléter : adresse du site]",
    /** Prestataire d'envoi des e-mails (SMTP) et pays d'hébergement de ses serveurs. */
    emailProvider: "[À compléter : prestataire d'envoi des e-mails et pays]",
    /** Région Backblaze B2 retenue pour les sauvegardes (une région européenne est recommandée). */
    backupRegion: "[À compléter : région des sauvegardes Backblaze B2, par exemple « Amsterdam (Pays-Bas) »]",
} as const;

/** Un paragraphe (texte) ou une liste à puces (tableau de textes). */
export type LegalBlock = string | string[];

export interface LegalSection {
    title: string;
    blocks: LegalBlock[];
}

const E = LEGAL_EDITOR;

const LEGAL_NOTICE: LegalSection[] = [
    {
        title: "1. Éditeur du service",
        blocks: [
            "Le site " + E.siteUrl + " et l'application mobile Neiri (ensemble, « Neiri » ou « le Service ») "
                + "sont édités par :",
            [
                "Éditeur : " + E.name,
                "Statut : " + E.legalForm,
                "Adresse : " + E.address,
                "Immatriculation : " + E.registration,
                "TVA intracommunautaire : " + E.vat,
                "Téléphone : " + E.phone,
                "E-mail : " + E.contactEmail,
            ],
            "Directeur de la publication : " + E.publicationDirector + ".",
        ],
    },
    {
        title: "2. Hébergement",
        blocks: [
            "Le Service est hébergé par Hetzner Online GmbH, Industriestr. 25, 91710 Gunzenhausen, Allemagne "
                + "(+49 9831 505-0, www.hetzner.com). Les serveurs sont situés dans l'Union européenne.",
            "Les sauvegardes de la base de données sont conservées par Backblaze, Inc., "
                + "201 Baldwin Ave., San Mateo, CA 94401, États-Unis, dans la région suivante : " + E.backupRegion + ".",
        ],
    },
    {
        title: "3. Nature du service",
        blocks: [
            "Neiri est une carte des activités et événements locaux. Une grande partie des informations publiées "
                + "provient de sources publiques ou ouvertes (agendas OpenAgenda, DATAtourisme, jeux de données publiés "
                + "sur data.gouv.fr) ; d'autres sont proposées par les utilisateurs et les professionnels. Neiri "
                + "n'organise pas les événements qu'il référence et n'en est pas le vendeur.",
            "Pour les contenus mis en ligne par les utilisateurs, l'éditeur agit en qualité d'hébergeur au sens de "
                + "l'article 6 de la loi n° 2004-575 du 21 juin 2004 pour la confiance dans l'économie numérique (LCEN) "
                + "et du règlement (UE) 2022/2065 sur les services numériques (DSA).",
        ],
    },
    {
        title: "4. Sources des données et licences",
        blocks: [
            "La source de chaque activité importée est indiquée sur sa fiche. Les données réutilisées le sont dans le "
                + "respect de leur licence :",
            [
                "Données publiées sur data.gouv.fr et données DATAtourisme : Licence Ouverte / Open Licence (Etalab), "
                    + "versions 1.0 ou 2.0 selon le jeu de données. Ces données ont pu être filtrées, classées par "
                    + "catégorie, dédoublonnées et reformatées par Neiri ; leurs producteurs ne sauraient être "
                    + "tenus pour responsables de ces traitements.",
                "Événements OpenAgenda : conditions d'utilisation d'OpenAgenda et licence choisie par chaque agenda "
                    + "source ; le nom de l'agenda d'origine est mentionné sur la fiche.",
                "Fonds de carte : © les contributeurs d'OpenStreetMap, données disponibles sous licence Open Database "
                    + "License (ODbL), www.openstreetmap.org/copyright. Sur l'application Android, la carte peut être "
                    + "fournie par Google Maps, sous les conditions de Google.",
            ],
            "Un producteur de données qui souhaite qu'une information ne soit plus reprise, ou qu'elle soit corrigée, "
                + "peut écrire à " + E.contactEmail + ".",
        ],
    },
    {
        title: "5. Propriété intellectuelle",
        blocks: [
            "La marque et le nom Neiri, la structure du Service, ses textes, sa charte graphique, ses logiciels et "
                + "sa base de données sont protégés par le Code de la propriété intellectuelle. Sauf mention contraire, "
                + "ils sont la propriété de l'éditeur.",
            "Toute reproduction, représentation ou extraction substantielle, par quelque procédé que ce soit, sans "
                + "autorisation écrite préalable de l'éditeur, est interdite et constitue une contrefaçon (articles "
                + "L. 335-2 et suivants et L. 342-1 et suivants du Code de la propriété intellectuelle). Les données "
                + "publiées sous licence ouverte restent réutilisables aux conditions de leur licence d'origine.",
            "Les photographies, logos et textes fournis par les organisateurs, les professionnels ou les sources "
                + "restent la propriété de leurs auteurs.",
        ],
    },
    {
        title: "6. Signaler un contenu",
        blocks: [
            "Toute personne peut signaler un contenu qu'elle estime illicite ou inexact en écrivant à "
                + E.contactEmail + ", ou, si elle dispose d'un compte, au moyen du formulaire « Une idée, un "
                + "problème ? ». Pour être traité rapidement, le signalement précise l'adresse ou le titre du "
                + "contenu, les raisons pour lesquelles il est jugé illicite et les coordonnées de son auteur.",
            "Est puni d'un an d'emprisonnement et de 15 000 euros d'amende le fait de présenter un contenu comme "
                + "illicite dans le but d'en obtenir le retrait en sachant cette information inexacte (article 6 de "
                + "la LCEN).",
        ],
    },
    {
        title: "7. Données personnelles",
        blocks: [
            "Le traitement des données personnelles est décrit dans la politique de confidentialité. Les conditions "
                + "d'accès et d'utilisation du Service figurent dans les conditions d'utilisation.",
        ],
    },
];

const TERMS: LegalSection[] = [
    {
        title: "1. Objet",
        blocks: [
            "Les présentes conditions d'utilisation (les « Conditions ») régissent l'accès au site " + E.siteUrl
                + " et à l'application mobile Neiri (le « Service »), édités par " + E.name + " (l'« Éditeur »), "
                + "ainsi que leur utilisation par toute personne qui les consulte (le « Visiteur ») ou qui y crée un "
                + "compte (l'« Utilisateur »).",
            "Le Service permet de découvrir sur une carte les activités, événements, lieux et food trucks proches "
                + "d'un endroit, de les enregistrer en favoris, de recevoir des alertes et, selon l'offre du compte, de "
                + "proposer des activités ou de gérer la présence d'un établissement.",
        ],
    },
    {
        title: "2. Acceptation des Conditions",
        blocks: [
            "La consultation du Service suppose l'acceptation des présentes Conditions. La création d'un compte, "
                + "par formulaire ou avec un compte Google, nécessite de cocher la case par laquelle l'Utilisateur "
                + "déclare avoir au moins 15 ans et accepter les présentes Conditions ainsi que la politique de "
                + "confidentialité. La date de cette acceptation et la version des textes acceptée sont enregistrées.",
            "Les Conditions applicables sont celles en vigueur à la date de la connexion. Leur version figure en bas "
                + "de cette page.",
        ],
    },
    {
        title: "3. Accès au Service",
        blocks: [
            "La consultation de la carte et des fiches est gratuite et ne nécessite pas de compte. Les frais "
                + "d'accès à internet et d'équipement restent à la charge de l'Utilisateur.",
            "L'Éditeur s'efforce d'assurer l'accès au Service en continu, sans y être tenu : le Service peut être "
                + "interrompu, notamment pour maintenance, mise à jour, ou en cas de panne ou de force majeure. Le "
                + "Service est actuellement proposé en version bêta et ses fonctionnalités peuvent évoluer.",
        ],
    },
    {
        title: "4. Compte utilisateur",
        blocks: [
            [
                "Le compte est réservé aux personnes âgées d'au moins 15 ans. Une personne de moins de 15 ans ne "
                    + "peut pas créer de compte.",
                "L'Utilisateur fournit une adresse e-mail valide, qu'il confirme. Un compte jamais confirmé est "
                    + "supprimé 30 jours après sa création.",
                "Le nom d'utilisateur ne doit pas porter atteinte aux droits d'un tiers (nom, marque), usurper "
                    + "l'identité d'autrui ni être injurieux. Il est affiché publiquement avec les contributions.",
                "L'Utilisateur garde ses identifiants secrets et est responsable de l'utilisation de son compte. Il "
                    + "prévient l'Éditeur sans délai de toute utilisation non autorisée.",
                "L'Utilisateur peut supprimer son compte à tout moment depuis son profil. Un compte resté sans "
                    + "activité pendant 3 ans est supprimé, après un avertissement par e-mail 30 jours avant.",
            ],
        ],
    },
    {
        title: "5. Informations publiées sur le Service",
        blocks: [
            "Les informations relatives aux activités et événements (dates, horaires, lieux, tarifs, conditions "
                + "d'accès, accessibilité) proviennent pour l'essentiel de sources tierces, d'organisateurs ou "
                + "d'utilisateurs. Elles sont reprises et classées automatiquement, et l'Éditeur ne peut en garantir "
                + "ni l'exactitude, ni l'exhaustivité, ni la mise à jour.",
            "L'Éditeur n'organise pas les événements référencés et n'est pas partie aux relations entre "
                + "l'Utilisateur et les organisateurs, lieux ou commerçants. Avant de se déplacer, l'Utilisateur "
                + "vérifie les informations auprès de l'organisateur. Une annulation, un report, une modification ou "
                + "un incident survenu lors d'un événement relève de la seule responsabilité de son organisateur.",
            "Les liens vers des sites tiers (billetterie, site de l'organisateur, réseaux sociaux) sont fournis pour "
                + "information ; l'Éditeur n'exerce aucun contrôle sur ces sites.",
        ],
    },
    {
        title: "6. Contributions des utilisateurs",
        blocks: [
            "Selon son offre, l'Utilisateur peut proposer des activités (textes, horaires, lieu, liens, images). "
                + "Chaque proposition est vérifiée par l'équipe Neiri avant publication, qui peut la refuser, la "
                + "corriger dans sa forme ou la retirer ensuite, sans avoir à se justifier au-delà de l'information "
                + "prévue à l'article 9.",
            "L'Utilisateur garantit qu'il a le droit de publier sa contribution, qu'elle est exacte et qu'elle ne "
                + "porte atteinte à aucun droit de tiers (droit d'auteur, droit à l'image, marque, vie privée). Il "
                + "garantit l'Éditeur contre toute réclamation d'un tiers à ce titre.",
            "L'Utilisateur concède à l'Éditeur, à titre gratuit et non exclusif, pour le monde entier et pour la "
                + "durée des droits, le droit de reproduire, représenter, adapter (mise en forme, traduction, "
                + "recadrage, réduction des images) et diffuser sa contribution sur le Service et dans les "
                + "communications qui en font la promotion. Après la suppression du compte, une activité déjà "
                + "publiée reste en ligne sans mention de son auteur.",
        ],
    },
    {
        title: "7. Comportements interdits",
        blocks: [
            "Il est interdit, sur ou au moyen du Service :",
            [
                "de publier un contenu illicite, notamment diffamatoire, injurieux, discriminatoire, haineux, "
                    + "violent, pornographique, contrefaisant, ou faisant l'apologie de crimes ;",
                "de publier un événement fictif, trompeur, ou une publicité déguisée sans rapport avec une activité "
                    + "réelle ;",
                "d'usurper l'identité d'une personne ou de se prétendre gestionnaire d'un lieu sans en avoir le "
                    + "droit ;",
                "de collecter les données du Service de manière automatisée (aspiration, extraction), en dehors des "
                    + "réutilisations permises par les licences des données d'origine ;",
                "de porter atteinte à la sécurité ou au fonctionnement du Service (intrusion, surcharge, contournement "
                    + "des limites ou des contrôles d'accès).",
            ],
        ],
    },
    {
        title: "8. Espace professionnel",
        blocks: [
            "Un compte professionnel permet de demander la gestion de la fiche d'un lieu, de proposer des "
                + "modifications et de publier les emplacements et horaires d'un food truck. La demande de gestion "
                + "d'un lieu est accompagnée des justificatifs demandés et examinée par l'équipe Neiri, qui peut "
                + "l'accepter ou la refuser ; les justificatifs sont effacés 12 mois après la décision.",
            "Le professionnel déclare agir pour son propre compte ou avec l'autorisation de l'établissement qu'il "
                + "représente, et s'engage à tenir à jour les informations qu'il publie. Les informations publiées "
                + "par un professionnel (nom commercial, adresse, horaires, liens, photos) sont visibles de tous.",
        ],
    },
    {
        title: "9. Modération, suspension et suppression",
        blocks: [
            "L'Éditeur peut retirer un contenu, ou suspendre ou supprimer un compte, en cas de manquement aux "
                + "présentes Conditions, de contenu illicite signalé ou d'atteinte à la sécurité du Service. Sauf "
                + "urgence ou obligation légale contraire, l'Utilisateur concerné est informé de la décision et de "
                + "ses motifs, et peut la contester en écrivant à " + E.contactEmail + ".",
            "Les signalements de contenus sont reçus et traités dans les conditions prévues aux mentions légales.",
        ],
    },
    {
        title: "10. Offres Premium et Pro",
        blocks: [
            "Le compte gratuit comporte des limites (nombre de favoris et d'alertes, absence de contribution et de "
                + "suivi d'activités ou de lieux). Les offres Premium et Pro lèvent tout ou partie de ces limites ; "
                + "leur contenu est présenté dans le Service au moment de la souscription.",
            "Pendant la phase actuelle, ces offres sont proposées gratuitement et activées immédiatement, sans "
                + "engagement ni moyen de paiement. Elles ne deviendront jamais payantes sans une démarche expresse "
                + "de l'Utilisateur : si une offre payante est proposée à l'avenir, elle fera l'objet de conditions "
                + "générales de vente distinctes (prix, durée, renouvellement, droit de rétractation, médiation), à "
                + "accepter séparément avant tout paiement.",
            "Lorsqu'une offre prend fin, le compte revient aux limites du compte gratuit : il conserve ce qu'il a "
                + "déjà enregistré, mais ne peut plus en ajouter au-delà de ces limites.",
        ],
    },
    {
        title: "11. Propriété intellectuelle",
        blocks: [
            "Le Service et ses éléments sont protégés dans les conditions décrites aux mentions légales. "
                + "L'Utilisateur bénéficie d'un droit personnel, non exclusif et non transférable d'utiliser le "
                + "Service pour ses besoins propres, conformément aux présentes Conditions.",
        ],
    },
    {
        title: "12. Responsabilité",
        blocks: [
            "L'Éditeur est tenu d'une obligation de moyens. Il ne saurait être tenu responsable des dommages "
                + "résultant de l'inexactitude d'une information provenant d'un tiers, de l'annulation ou du "
                + "déroulement d'un événement, d'une interruption du Service, ou d'une utilisation du Service non "
                + "conforme aux présentes Conditions.",
            "Ces limitations ne s'appliquent pas en cas de faute lourde ou intentionnelle de l'Éditeur, ni aux "
                + "dommages corporels, et ne privent pas l'Utilisateur agissant en qualité de consommateur des "
                + "droits que la loi lui reconnaît et auxquels il ne peut être dérogé.",
        ],
    },
    {
        title: "13. Données personnelles",
        blocks: [
            "Les données personnelles sont traitées conformément à la politique de confidentialité, qui fait partie "
                + "intégrante des présentes Conditions.",
        ],
    },
    {
        title: "14. Modification des Conditions",
        blocks: [
            "L'Éditeur peut modifier les présentes Conditions, notamment pour suivre l'évolution du Service ou de la "
                + "réglementation. En cas de modification substantielle, l'Utilisateur en est informé dans le "
                + "Service ou par e-mail avant son entrée en vigueur et, le cas échéant, invité à accepter la nouvelle "
                + "version. L'Utilisateur qui la refuse peut supprimer son compte à tout moment.",
        ],
    },
    {
        title: "15. Droit applicable et litiges",
        blocks: [
            "Les présentes Conditions sont soumises au droit français. En cas de difficulté, l'Utilisateur est invité "
                + "à écrire d'abord à " + E.contactEmail + " pour rechercher une solution amiable.",
            "L'Utilisateur consommateur peut saisir, à son choix, la juridiction du lieu où il demeurait au moment de "
                + "la conclusion du contrat ou de la survenance du fait dommageable, ou toute autre juridiction "
                + "compétente en application de la loi. Pour les Utilisateurs professionnels, compétence est "
                + "attribuée aux tribunaux du ressort du domicile ou du siège de l'Éditeur.",
        ],
    },
    {
        title: "16. Contact",
        blocks: ["Pour toute question relative aux présentes Conditions : " + E.contactEmail + "."],
    },
];

const PRIVACY: LegalSection[] = [
    {
        title: "1. Responsable du traitement",
        blocks: [
            "Le responsable du traitement des données personnelles collectées sur Neiri est " + E.name + ", "
                + E.address + " (l'« Éditeur »). Pour toute question ou demande relative à vos données : "
                + E.privacyEmail + ".",
            "Cette politique s'applique au site et à l'application mobile Neiri. Elle est établie conformément au "
                + "règlement (UE) 2016/679 (« RGPD ») et à la loi n° 78-17 du 6 janvier 1978 relative à "
                + "l'informatique, aux fichiers et aux libertés.",
        ],
    },
    {
        title: "2. Ce que nous ne collectons pas",
        blocks: [
            [
                "Aucun outil de mesure d'audience, aucun traceur publicitaire, aucune revente de données.",
                "Aucun journal des adresses IP n'est conservé par Neiri.",
                "La position de votre appareil, si vous l'autorisez, sert uniquement à afficher et rechercher les "
                    + "activités autour de vous ; elle n'est jamais enregistrée. Seule la ville par défaut que vous "
                    + "choisissez dans vos préférences est conservée.",
            ],
        ],
    },
    {
        title: "3. Données traitées, finalités et bases légales",
        blocks: [
            [
                "Compte : nom d'utilisateur, adresse e-mail, mot de passe (conservé uniquement sous forme d'empreinte "
                    + "irréversible), type de compte, date d'inscription, date et version des textes acceptés, date de "
                    + "dernière activité, nombre et date des connexions. Finalité : créer et gérer votre compte. Base "
                    + "légale : exécution du contrat (article 6.1.b du RGPD).",
                "Connexion avec Google : identifiant de votre compte Google et adresse e-mail transmis par Google, si "
                    + "vous choisissez ce mode de connexion. Base légale : exécution du contrat.",
                "Préférences, favoris, suivis et alertes : ville par défaut et ses coordonnées, catégories préférées, "
                    + "favoris, activités et lieux suivis, alertes (ville, rayon, catégories, mots-clés, canaux) et "
                    + "historique des alertes envoyées, jetons de notification de vos appareils. Finalité : "
                    + "personnaliser le Service et vous prévenir. Base légale : exécution du contrat.",
                "Contributions : activités que vous proposez, avec votre nom d'utilisateur, affiché publiquement une "
                    + "fois l'activité publiée. Base légale : exécution du contrat.",
                "Espace professionnel : demandes de gestion d'un lieu (lien avec l'établissement, justificatifs), "
                    + "droits de gestion, propositions de modification, informations des food trucks (nom, contact, "
                    + "liens, photo, emplacements, horaires). Finalité : vérifier votre droit à gérer une fiche et "
                    + "publier vos informations. Base légale : exécution du contrat et intérêt légitime de l'Éditeur "
                    + "à prévenir les usurpations (article 6.1.f).",
                "Offres et abonnements : offre souscrite, dates et historique. Base légale : exécution du contrat et, "
                    + "pour l'historique, obligation légale de conservation comptable (article 6.1.c).",
                "Messages « Une idée, un problème ? » : type et texte du message, page d'origine, navigateur ou "
                    + "système de l'appareil. Finalité : traiter vos suggestions et corriger les anomalies. Base "
                    + "légale : intérêt légitime de l'Éditeur à améliorer le Service.",
                "Sécurité : journaux des opérations sur les comptes et des actions d'administration, échecs de "
                    + "connexion (enregistrés sous forme d'empreinte de l'adresse, jamais l'adresse elle-même). "
                    + "Finalité : protéger les comptes, prévenir les fraudes, assurer la traçabilité. Base légale : "
                    + "intérêt légitime de l'Éditeur.",
            ],
        ],
    },
    {
        title: "4. Données des organisateurs d'événements",
        blocks: [
            "Les activités importées depuis des sources publiques (OpenAgenda, DATAtourisme, data.gouv.fr) peuvent "
                + "comporter les coordonnées publiées par leur organisateur (nom, téléphone, adresse e-mail, site). "
                + "Ces données ne sont pas collectées auprès de vous mais auprès de ces sources (article 14 du RGPD). "
                + "Elles sont affichées sur la fiche de l'activité pour permettre au public de contacter "
                + "l'organisateur, et supprimées avec l'activité.",
            "L'adresse e-mail de contact peut être utilisée pour informer l'organisateur, au plus une fois par "
                + "événement et au plus tous les 30 jours, que son événement est référencé sur Neiri. La date de "
                + "cet envoi est conservée pour éviter les doublons. Base légale : intérêt légitime de l'Éditeur à "
                + "informer les organisateurs de la réutilisation de leurs informations.",
            "Un organisateur peut à tout moment s'opposer à ce traitement, demander la correction ou le retrait de "
                + "ses coordonnées ou de son événement en écrivant à " + E.privacyEmail + ".",
        ],
    },
    {
        title: "5. Durées de conservation",
        blocks: [
            [
                "Compte et données associées : jusqu'à la suppression du compte. Un compte sans activité pendant "
                    + "3 ans est supprimé, après un avertissement par e-mail 30 jours avant (sauf compte administrateur).",
                "Compte jamais confirmé : 30 jours après l'inscription.",
                "Liens de confirmation d'adresse et de réinitialisation du mot de passe : 7 jours après leur "
                    + "expiration ou leur utilisation.",
                "Jetons de notification d'un appareil inactif et historique des alertes envoyées : 6 mois.",
                "Justificatifs des demandes de gestion d'un lieu : 12 mois après la décision ; demandes et "
                    + "propositions de modification closes : 3 ans.",
                "Journaux des comptes et des actions d'administration : 3 ans ; échecs de connexion : 1 jour au plus.",
                "Historique des abonnements terminés : 6 ans (obligations comptables).",
                "Activités supprimées : 30 jours dans la corbeille avant leur effacement définitif.",
                "Sauvegardes : 7 jours sur le serveur et 30 jours chez le prestataire de sauvegarde.",
            ],
            "À la suppression de votre compte, vos favoris, alertes, préférences, identités Google, contributions non "
                + "publiées, demandes et propositions sont supprimés ; votre adresse est effacée des journaux ; les "
                + "activités déjà publiées restent en ligne sans mention de leur auteur ; les coordonnées de vos "
                + "food trucks sont effacées.",
        ],
    },
    {
        title: "6. Destinataires",
        blocks: [
            "Vos données sont accessibles aux seules personnes habilitées de l'équipe Neiri, dans la limite de "
                + "leurs fonctions (modération, assistance, administration). Votre nom d'utilisateur, vos "
                + "contributions publiées et les informations publiques de vos établissements sont visibles de tous.",
            "Elles sont également traitées, pour le compte de l'Éditeur, par les sous-traitants suivants :",
            [
                "Hetzner Online GmbH (Allemagne) : hébergement du Service et de la base de données.",
                "Backblaze, Inc. : stockage des sauvegardes, région " + E.backupRegion + ".",
                "Envoi des e-mails : " + E.emailProvider + ".",
                "650 Industries, Inc. (Expo, États-Unis), avec les services de notification d'Apple et de Google : "
                    + "acheminement des notifications sur l'application mobile.",
                "Google LLC / Google Ireland Ltd : connexion avec Google, si vous la choisissez, et affichage de la "
                    + "carte sur Android.",
            ],
            "L'affichage de la carte sur le site, et dans certains cas sur l'application, charge les fonds de carte "
                + "depuis les serveurs de la fondation OpenStreetMap (Royaume-Uni) : votre adresse IP et la zone "
                + "affichée leur sont alors transmises par votre navigateur, conformément à leur politique de "
                + "confidentialité. Vos données ne sont communiquées à aucun autre tiers, sauf obligation légale ou "
                + "réquisition d'une autorité.",
        ],
    },
    {
        title: "7. Transferts hors de l'Union européenne",
        blocks: [
            "Certains prestataires sont établis aux États-Unis (Google, Expo, Apple, et Backblaze si une région "
                + "américaine est utilisée). Ces transferts sont encadrés par la décision d'adéquation de la "
                + "Commission européenne du 10 juillet 2023 (EU-US Data Privacy Framework) pour les entreprises "
                + "certifiées, ou à défaut par les clauses contractuelles types adoptées par la Commission "
                + "européenne. Le Royaume-Uni bénéficie d'une décision d'adéquation. Une copie des garanties peut être "
                + "obtenue sur demande à " + E.privacyEmail + ".",
        ],
    },
    {
        title: "8. Cookies et stockage sur votre appareil",
        blocks: [
            "Neiri n'utilise ni cookie publicitaire ni outil de mesure d'audience. Le site et l'application "
                + "enregistrent seulement sur votre appareil les informations nécessaires à leur fonctionnement : "
                + "votre session de connexion, le fond de carte choisi et vos choix d'affichage (par exemple le fait "
                + "d'avoir fermé un message). Ce stockage, strictement nécessaire au service que vous demandez, est "
                + "dispensé de consentement (article 82 de la loi Informatique et Libertés). La session est effacée à "
                + "la déconnexion ; le reste peut l'être en vidant les données du site dans votre navigateur ou celles "
                + "de l'application sur votre téléphone.",
            "Sur les pages de connexion et d'inscription, le bouton « Se connecter avec Google » est fourni par "
                + "Google, qui peut alors déposer ses propres cookies selon sa politique de confidentialité.",
        ],
    },
    {
        title: "9. Sécurité",
        blocks: [
            "L'Éditeur met en œuvre des mesures adaptées pour protéger vos données : connexion chiffrée (HTTPS), "
                + "mots de passe stockés sous forme d'empreinte, limitation des tentatives de connexion, sessions "
                + "révocables, accès d'administration limités et tracés, journaux de l'application sans adresse "
                + "e-mail complète. En cas de violation de données présentant un risque pour vous, vous en serez "
                + "informé dans les conditions prévues par le RGPD.",
        ],
    },
    {
        title: "10. Vos droits",
        blocks: [
            "Vous disposez d'un droit d'accès, de rectification, d'effacement, de limitation, de portabilité de vos "
                + "données, et d'un droit d'opposition aux traitements fondés sur l'intérêt légitime. Vous pouvez "
                + "aussi définir des directives relatives au sort de vos données après votre décès.",
            [
                "Accès et portabilité : bouton « Télécharger mes données » de votre profil (fichier JSON).",
                "Rectification : modification de votre profil et de votre adresse e-mail.",
                "Effacement : suppression de votre compte depuis votre profil.",
                "Opposition aux alertes : lien de désinscription dans chaque e-mail d'alerte et réglage des "
                    + "notifications de chaque alerte.",
                "Toute autre demande : " + E.privacyEmail + ". Une réponse vous est apportée dans un délai d'un "
                    + "mois, qui peut être prolongé de deux mois en cas de demande complexe ; un justificatif "
                    + "d'identité ne peut être demandé qu'en cas de doute raisonnable.",
            ],
            "Si vous estimez que vos droits ne sont pas respectés, vous pouvez introduire une réclamation auprès de "
                + "la Commission nationale de l'informatique et des libertés (CNIL), 3 place de Fontenoy, TSA 80715, "
                + "75334 Paris Cedex 07, www.cnil.fr.",
        ],
    },
    {
        title: "11. Mineurs",
        blocks: [
            "La création d'un compte est réservée aux personnes d'au moins 15 ans, âge à partir duquel un mineur peut "
                + "consentir seul au traitement de ses données en France (article 45 de la loi Informatique et "
                + "Libertés). Si vous constatez qu'un enfant de moins de 15 ans a créé un compte, écrivez à "
                + E.privacyEmail + " pour qu'il soit supprimé.",
        ],
    },
    {
        title: "12. Modification de cette politique",
        blocks: [
            "Cette politique peut évoluer avec le Service ou la réglementation. Sa version figure en bas de cette "
                + "page ; toute modification substantielle vous sera signalée dans le Service ou par e-mail.",
        ],
    },
];

export const LEGAL_CONTENT: Record<LegalDocumentKey, LegalSection[]> = {
    legalNotice: LEGAL_NOTICE,
    terms: TERMS,
    privacy: PRIVACY,
};

/** Découpe un texte en morceaux, en isolant les champs « [À compléter …] » pour les surligner. */
export function splitPlaceholders(text: string): { text: string; placeholder: boolean }[] {
    return text
        .split(/(\[À compléter[^\]]*\])/)
        .filter((part) => part !== "")
        .map((part) => ({ text: part, placeholder: part.startsWith("[À compléter") }));
}
