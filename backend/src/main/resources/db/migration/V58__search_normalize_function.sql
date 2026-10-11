-- #91 — Recherche globale : normalisation du texte cherché, sans extension.
--
-- La recherche ignore la casse, les accents, la ponctuation et les espaces superflus. La même fonction
-- est appliquée en SQL à la requête et aux champs (titre, description, catégories, ville, adresse) :
-- la normalisation est donc identique des deux côtés par construction.
--
-- Pourquoi pas l'extension `unaccent` : elle est disponible mais non installée, et l'installer
-- (CREATE EXTENSION) dépend des droits du rôle de migration sur chaque base (locale, CI, production).
-- Cette fonction en SQL pur n'a aucune dépendance. Elle couvre les lettres latines accentuées des
-- blocs Latin-1 et Latin étendu A/B (table générée depuis la décomposition Unicode NFD), plus les
-- ligatures œ/æ et ß. Les lettres non latines sont conservées telles quelles (seule la ponctuation
-- est retirée).
--
-- IMMUTABLE : utilisable dans un index d'expression si une mesure le justifie plus tard ; aucun index
-- n'est créé ici (voir docs/02_Architecture/GLOBAL_SEARCH_CONTRACT.md, section Performance).

CREATE OR REPLACE FUNCTION ll_search_normalize(input text) RETURNS text
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
AS $$
    SELECT btrim(regexp_replace(
        lower(translate(
            replace(replace(replace(replace(replace(input, 'Œ', 'oe'), 'œ', 'oe'), 'Æ', 'ae'), 'æ', 'ae'), 'ß', 'ss'),
            'ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝàáâãäåçèéêëìíîïñòóôõöùúûüýÿĀāĂăĄąĆćĈĉĊċČčĎďĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĨĩĪīĬĭĮįİĴĵĶķĹĺĻļĽľŃńŅņŇňŌōŎŏŐőŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽžƠơƯưǍǎǏǐǑǒǓǔǕǖǗǘǙǚǛǜǞǟǠǡǦǧǨǩǪǫǬǭǰǴǵǸǹǺǻȀȁȂȃȄȅȆȇȈȉȊȋȌȍȎȏȐȑȒȓȔȕȖȗȘșȚțȞȟȦȧȨȩȪȫȬȭȮȯȰȱȲȳĐđĦħıŁłĿŀØøŦŧ',
            'aaaaaaceeeeiiiinooooouuuuyaaaaaaceeeeiiiinooooouuuuyyaaaaaaccccccccddeeeeeeeeeegggggggghhiiiiiiiiijjkkllllllnnnnnnoooooorrrrrrssssssssttttuuuuuuuuuuuuwwyyyzzzzzzoouuaaiioouuuuuuuuuuaaaaggkkoooojggnnaaaaaaeeeeiiiioooorrrruuuusstthhaaeeooooooooyyddhhilllloott')),
        '[^[:alnum:]]+', ' ', 'g'))
$$;
