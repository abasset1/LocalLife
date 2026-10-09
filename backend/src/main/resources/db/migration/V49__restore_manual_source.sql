-- Recrée la source réservée « Saisie manuelle » (type MANUAL) si elle a disparu.
--
-- Elle n'est créée que par V8, jamais rejouée : une base dont la table `source` a été vidée après coup
-- (constaté le 09/10/2026 sur une base de développement nettoyée à la main) n'a plus de source MANUAL, et
-- toute proposition d'activité depuis le site échoue, la contribution manuelle étant rattachée à cette
-- source.
--
-- Sans effet sur une base saine : l'insertion n'a lieu que si aucune source MANUAL n'existe (au plus une
-- est autorisée, index unique partiel de V30).

INSERT INTO source (name, type, url, status, last_sync_at)
SELECT 'Saisie manuelle', 'MANUAL', NULL, 'ACTIVE', NULL
WHERE NOT EXISTS (SELECT 1 FROM source WHERE type = 'MANUAL');
