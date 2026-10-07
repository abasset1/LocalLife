-- Issue #47 : enrichissement et nettoyage du référentiel catégories + mots-clés, à partir de l'audit
-- documenté dans docs/AUDIT_REFERENTIEL_CATEGORIES.md (vocabulaire réel de l'extraction OpenAgenda du
-- 29/09/2026, docs/data/categorie_source_type.csv).
--
-- * Aucune nouvelle catégorie : les 9 catégories V1 couvrent les domaines observés hors emploi/formation
--   (décision de fond toujours en attente, voir l'audit §4).
-- * Aucune modification du moteur : uniquement des données (category_keyword).
-- * Mots-clés métier précis ; « artisanat » exclu (exposition, activité culturelle ou marché selon le
--   contexte, trop ambigu pour COMMERCE) ; variantes singulier/pluriel et graphies observées dans les données
--   réelles (« theatre », « théatre », « cinema ») ; expressions multi-mots quand le mot seul est trop
--   générique (« visite » seul reste exclu : c'est un format, pas un domaine).
-- * Un même mot-clé peut désigner plusieurs catégories (modèle 1..N, issue #45) : « patrimoine » →
--   CULTURE + TOURISM.
--
-- Rejouable : insertions ON CONFLICT DO NOTHING, désactivation idempotente.

INSERT INTO category_keyword (category_id, keyword)
SELECT c.id, v.keyword
FROM category c
JOIN (
    VALUES
        -- CULTURE : spectacle vivant, musique, cinéma, arts, lecture, histoire/patrimoine
        ('CULTURE', 'theatre'),
        ('CULTURE', 'théatre'),
        ('CULTURE', 'théâtres'),
        ('CULTURE', 'spectacle'),
        ('CULTURE', 'spectacles'),
        ('CULTURE', 'musique'),
        ('CULTURE', 'musiques'),
        ('CULTURE', 'musical'),
        ('CULTURE', 'musicale'),
        ('CULTURE', 'musicaux'),
        ('CULTURE', 'concerts'),
        ('CULTURE', 'jazz'),
        ('CULTURE', 'opéra'),
        ('CULTURE', 'orchestre'),
        ('CULTURE', 'chorale'),
        ('CULTURE', 'récital'),
        ('CULTURE', 'orgue'),
        ('CULTURE', 'cinéma'),
        ('CULTURE', 'cinema'),
        ('CULTURE', 'ciné'),
        ('CULTURE', 'film'),
        ('CULTURE', 'films'),
        ('CULTURE', 'court métrage'),
        ('CULTURE', 'courts métrages'),
        ('CULTURE', 'documentaire'),
        ('CULTURE', 'expositions'),
        ('CULTURE', 'marionnette'),
        ('CULTURE', 'marionnettes'),
        ('CULTURE', 'conte'),
        ('CULTURE', 'contes'),
        ('CULTURE', 'conteur'),
        ('CULTURE', 'conteuse'),
        ('CULTURE', 'clown'),
        ('CULTURE', 'clowns'),
        ('CULTURE', 'magie'),
        ('CULTURE', 'magicien'),
        ('CULTURE', 'humour'),
        ('CULTURE', 'humoriste'),
        ('CULTURE', 'one man show'),
        ('CULTURE', 'seul en scène'),
        ('CULTURE', 'improvisation'),
        ('CULTURE', 'arts de la rue'),
        ('CULTURE', 'art de rue'),
        ('CULTURE', 'street art'),
        ('CULTURE', 'art contemporain'),
        ('CULTURE', 'arts plastiques'),
        ('CULTURE', 'sculpture'),
        ('CULTURE', 'galerie'),
        ('CULTURE', 'poème'),
        ('CULTURE', 'poèmes'),
        ('CULTURE', 'slam'),
        ('CULTURE', 'écriture'),
        ('CULTURE', 'auteur'),
        ('CULTURE', 'autrice'),
        ('CULTURE', 'dédicace'),
        ('CULTURE', 'bande dessinée'),
        ('CULTURE', 'polar'),
        ('CULTURE', 'arts du récit'),
        ('CULTURE', 'fiction sonore'),
        ('CULTURE', 'archives'),
        ('CULTURE', 'archéologie'),
        ('CULTURE', 'archéologique'),
        ('CULTURE', 'patrimoine'),
        ('CULTURE', 'jep'),
        ('CULTURE', 'pays d''art et d''histoire'),
        ('CULTURE', 'ville d''art et d''histoire'),
        ('CULTURE', 'nuit des musées'),

        -- TOURISM : aucun mot-clé jusqu'ici ; patrimoine et découverte touristique d'un lieu
        ('TOURISM', 'tourisme'),
        ('TOURISM', 'touristique'),
        ('TOURISM', 'office de tourisme'),
        ('TOURISM', 'patrimoine'),
        ('TOURISM', 'jep'),
        ('TOURISM', 'pays d''art et d''histoire'),
        ('TOURISM', 'ville d''art et d''histoire'),
        ('TOURISM', 'monument'),
        ('TOURISM', 'monuments'),
        ('TOURISM', 'abbaye'),
        ('TOURISM', 'cathédrale'),
        ('TOURISM', 'citadelle'),
        ('TOURISM', 'remparts'),
        ('TOURISM', 'croisière'),

        -- GASTRONOMY
        ('GASTRONOMY', 'dégustations'),
        ('GASTRONOMY', 'gastronomie'),
        ('GASTRONOMY', 'gastronomique'),
        ('GASTRONOMY', 'food truck'),
        ('GASTRONOMY', 'food trucks'),
        ('GASTRONOMY', 'cuisine'),
        ('GASTRONOMY', 'culinaire'),
        ('GASTRONOMY', 'repas'),
        ('GASTRONOMY', 'brunch'),
        ('GASTRONOMY', 'pique-nique'),
        ('GASTRONOMY', 'vin'),
        ('GASTRONOMY', 'vins'),
        ('GASTRONOMY', 'œnologie'),
        ('GASTRONOMY', 'oenologie'),
        ('GASTRONOMY', 'bière'),
        ('GASTRONOMY', 'bières'),
        ('GASTRONOMY', 'chocolat'),
        ('GASTRONOMY', 'pâtisserie'),
        ('GASTRONOMY', 'fromage'),
        ('GASTRONOMY', 'fromages'),
        ('GASTRONOMY', 'terroir'),
        ('GASTRONOMY', 'gourmand'),
        ('GASTRONOMY', 'gourmande'),
        ('GASTRONOMY', 'gourmands'),

        -- MARKETS
        ('MARKETS', 'marchés'),
        ('MARKETS', 'brocantes'),
        ('MARKETS', 'vide-greniers'),
        ('MARKETS', 'vide-dressing'),
        ('MARKETS', 'puces'),
        ('MARKETS', 'foire'),
        ('MARKETS', 'foires'),
        -- « bourse aux » seul est trop générique (« bourse aux emplois », « bourse aux formations ») :
        -- uniquement des expressions explicitement liées aux marchés d'objets.
        ('MARKETS', 'bourse aux jouets'),
        ('MARKETS', 'bourse aux livres'),
        ('MARKETS', 'bourse aux vêtements'),
        ('MARKETS', 'bourse aux collections'),
        ('MARKETS', 'troc'),

        -- COMMERCE
        ('COMMERCE', 'braderies'),
        ('COMMERCE', 'soldes'),
        ('COMMERCE', 'commerçants'),
        ('COMMERCE', 'commerces'),
        ('COMMERCE', 'boutique éphémère'),
        ('COMMERCE', 'artisans'),

        -- SPORT
        ('SPORT', 'sport'),
        ('SPORT', 'sports'),
        ('SPORT', 'sportif'),
        ('SPORT', 'sportive'),
        ('SPORT', 'course à pied'),
        ('SPORT', 'trail'),
        ('SPORT', 'marathon'),
        ('SPORT', 'semi-marathon'),
        ('SPORT', 'triathlon'),
        ('SPORT', 'cyclisme'),
        ('SPORT', 'cyclo'),
        ('SPORT', 'vtt'),
        ('SPORT', 'marche nordique'),
        ('SPORT', 'natation'),
        ('SPORT', 'basket'),
        ('SPORT', 'basketball'),
        ('SPORT', 'handball'),
        ('SPORT', 'volley'),
        ('SPORT', 'volley-ball'),
        ('SPORT', 'tennis'),
        ('SPORT', 'judo'),
        ('SPORT', 'karaté'),
        ('SPORT', 'boxe'),
        ('SPORT', 'escalade'),
        ('SPORT', 'yoga'),
        ('SPORT', 'pilates'),
        ('SPORT', 'gymnastique'),
        ('SPORT', 'fitness'),
        ('SPORT', 'zumba'),
        ('SPORT', 'pétanque'),
        ('SPORT', 'tir à l''arc'),
        ('SPORT', 'kayak'),
        ('SPORT', 'canoë'),
        ('SPORT', 'paddle'),
        ('SPORT', 'surf'),
        ('SPORT', 'ski'),
        ('SPORT', 'spéléo'),
        ('SPORT', 'spéléologie'),
        ('SPORT', 'équitation'),
        ('SPORT', 'roller'),
        ('SPORT', 'athlétisme'),

        -- NATURE
        ('NATURE', 'sorties nature'),
        ('NATURE', 'jardin'),
        ('NATURE', 'jardins'),
        ('NATURE', 'jardinage'),
        ('NATURE', 'jardiner'),
        ('NATURE', 'potager'),
        ('NATURE', 'oiseaux'),
        ('NATURE', 'ornithologie'),
        ('NATURE', 'lpo'),
        ('NATURE', 'faune'),
        ('NATURE', 'flore'),
        ('NATURE', 'forêt'),
        ('NATURE', 'arbres'),
        ('NATURE', 'botanique'),
        ('NATURE', 'naturaliste'),
        ('NATURE', 'naturalistes'),
        ('NATURE', 'écologie'),
        ('NATURE', 'transition écologique'),
        ('NATURE', 'réserve naturelle'),
        ('NATURE', 'compostage'),

        -- LEISURE
        ('LEISURE', 'jeux vidéo'),
        ('LEISURE', 'quiz'),
        ('LEISURE', 'blind test'),
        ('LEISURE', 'loto'),
        ('LEISURE', 'belote'),
        ('LEISURE', 'karaoké'),
        ('LEISURE', 'murder party'),
        ('LEISURE', 'laser game'),
        ('LEISURE', 'bowling'),
        ('LEISURE', 'carnaval'),
        ('LEISURE', 'feu d''artifice'),
        ('LEISURE', 'feux d''artifice'),
        ('LEISURE', 'ludothèque'),

        -- CITIZEN
        ('CITIZEN', 'réunions publiques'),
        ('CITIZEN', 'conseil municipal'),
        ('CITIZEN', 'conseil de quartier'),
        ('CITIZEN', 'concertation'),
        ('CITIZEN', 'consultation publique'),
        ('CITIZEN', 'enquête publique'),
        ('CITIZEN', 'débat public'),
        ('CITIZEN', 'budget participatif'),
        ('CITIZEN', 'commémoration'),
        ('CITIZEN', 'cérémonie commémorative'),
        ('CITIZEN', 'don du sang'),
        ('CITIZEN', 'collecte de sang'),
        ('CITIZEN', 'bénévolat'),
        ('CITIZEN', 'citoyen'),
        ('CITIZEN', 'citoyenne'),
        ('CITIZEN', 'citoyens'),
        ('CITIZEN', 'citoyennes'),
        ('CITIZEN', 'citoyenneté')
) AS v(category_code, keyword)
    ON c.code = v.category_code
ON CONFLICT (category_id, keyword) DO NOTHING;

-- Nettoyage : « escape » seul est trop générique (titre de film, de spectacle...). « escape game »
-- reste actif. Désactivé, pas supprimé (traçabilité des matched_keywords existants).
UPDATE category_keyword ck
SET active = false, updated_at = now()
FROM category c
WHERE ck.category_id = c.id
  AND c.code = 'LEISURE'
  AND ck.keyword = 'escape'
  AND ck.active;
