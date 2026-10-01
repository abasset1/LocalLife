-- CAT-003 (SPRINT_CATEG) : référentiel canonique des 7 publics V1.
--
-- Même schéma que CAT-001 (catégories) et CAT-002 (types) : séparation
-- ID technique / CODE métier stable / LABEL traduit. Un public n'est
-- jamais une catégorie (ex. FAMILY est un public, la catégorie associée
-- à l'activité peut être GASTRONOMY) : cette table ne porte donc aucune
-- colonne category_id ni activity_type_id.
--
-- Différence avec CATEGORY/ACTIVITY_TYPE (à retenir pour les tickets
-- suivants) : les exemples du sprint ("AUDIENCE = CHILDREN, FAMILY")
-- montrent qu'une activité peut porter PLUSIEURS publics simultanément,
-- contrairement à la catégorie et au type qui restent uniques par
-- activité. Cette table ne modélise que le référentiel lui-même ; la
-- relation many-to-many activité ↔ publics est hors périmètre CAT-003.

CREATE TABLE audience (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE audience_translation (
    id          BIGSERIAL PRIMARY KEY,
    audience_id BIGINT       NOT NULL REFERENCES audience(id) ON DELETE CASCADE,
    locale      VARCHAR(10)  NOT NULL,
    label       VARCHAR(255) NOT NULL,
    description TEXT,
    UNIQUE (audience_id, locale)
);

INSERT INTO audience (code) VALUES
    ('ALL_PUBLIC'),
    ('FAMILY'),
    ('CHILDREN'),
    ('TEENAGERS'),
    ('ADULTS'),
    ('SENIORS'),
    ('PROFESSIONALS');

INSERT INTO audience_translation (audience_id, locale, label, description)
SELECT id, 'fr-FR', v.label, v.description
FROM audience
JOIN (VALUES
    ('ALL_PUBLIC',    'Tout public',       'Activité ouverte à tous, sans restriction d''âge.'),
    ('FAMILY',        'Famille',           'Activité pensée pour être vécue en famille, parents et enfants ensemble.'),
    ('CHILDREN',      'Enfants',           'Activité destinée aux enfants.'),
    ('TEENAGERS',     'Adolescents',       'Activité destinée aux adolescents.'),
    ('ADULTS',        'Adultes',           'Activité destinée aux adultes.'),
    ('SENIORS',       'Seniors',           'Activité destinée aux seniors.'),
    ('PROFESSIONALS', 'Professionnels',    'Activité destinée à un public professionnel.')
) AS v(code, label, description) ON v.code = audience.code;
