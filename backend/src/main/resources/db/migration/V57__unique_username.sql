-- LS-000 : le nom d'utilisateur devient l'identifiant public d'un compte,
-- unique sans tenir compte de la casse.
--
-- Règle de format (identique à UsernamePolicy côté backend) : 3 à 30
-- caractères parmi lettres ASCII, chiffres, « . », « _ » et « - », le
-- premier étant une lettre ou un chiffre.
--
-- Les comptes existants sont mis en conformité avant la pose des
-- contraintes, en parcourant les comptes du plus ancien au plus récent :
--   1. un nom hors format est nettoyé (accents retirés, caractères interdits
--      remplacés par « - », tronqué à 30 caractères) ; s'il reste trop
--      court, il devient « membre-<id> » ;
--   2. le plus ancien compte garde son nom ; un compte plus récent qui
--      reprend le même nom (casse ignorée) reçoit un suffixe « -2 », « -3 »…
-- Chaque renommage est signalé par un NOTICE dans les journaux de migration.
-- Les noms réservés (admin, support…) ne sont refusés qu'aux nouveaux choix :
-- un compte existant qui en porte un le garde.

DO $$
DECLARE
    account RECORD;
    base TEXT;
    candidate TEXT;
    suffix INT;
BEGIN
    FOR account IN SELECT id, username FROM users ORDER BY id LOOP
        base := coalesce(account.username, '');
        IF base !~ '^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$' THEN
            base := translate(base,
                    'àâäáãåçèéêëìíîïñòóôöõùúûüýÿÀÂÄÁÃÅÇÈÉÊËÌÍÎÏÑÒÓÔÖÕÙÚÛÜÝ',
                    'aaaaaaceeeeiiiinooooouuuuyyAAAAAACEEEEIIIINOOOOOUUUUY');
            base := regexp_replace(base, '[^A-Za-z0-9._-]+', '-', 'g');
            base := regexp_replace(base, '^[^A-Za-z0-9]+', '');
            base := regexp_replace(left(base, 30), '[._-]+$', '');
            IF length(base) < 3 THEN
                base := 'membre-' || account.id;
            END IF;
        END IF;

        candidate := base;
        suffix := 1;
        WHILE EXISTS (
                SELECT 1 FROM users other
                WHERE other.id < account.id AND lower(other.username) = lower(candidate)) LOOP
            suffix := suffix + 1;
            candidate := left(base, 30 - length('-' || suffix)) || '-' || suffix;
        END LOOP;

        IF account.username IS DISTINCT FROM candidate THEN
            RAISE NOTICE 'LS-000 : compte % renommé de « % » en « % »', account.id, account.username, candidate;
            UPDATE users SET username = candidate WHERE id = account.id;
        END IF;
    END LOOP;
END $$;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(30);
ALTER TABLE users ADD CONSTRAINT users_username_format
    CHECK (username ~ '^[A-Za-z0-9][A-Za-z0-9._-]{2,29}$');
CREATE UNIQUE INDEX users_username_lower_key ON users (lower(username));
