# Domaine neiri.fr

Domaine acheté chez OVHcloud le 19/11/2026. Le site et l'API restent sur le serveur Hetzner (Docker, Caddy) :
l'hébergement web de 100 Mo fourni avec le domaine n'est **pas** utilisé par l'application. La boîte e-mail incluse
(MX Plan) peut servir à **recevoir** (`contact@neiri.fr`) ; l'**envoi** des e-mails de l'application passe par un
prestataire transactionnel (Brevo).

## 1. Zone DNS (OVHcloud → Domaines → neiri.fr → Zone DNS)

| Type | Sous-domaine | Cible | Rôle |
|---|---|---|---|
| A | *(vide)* | IPv4 du serveur Hetzner | site et API |
| AAAA | *(vide)* | IPv6 du serveur Hetzner, **ou supprimer** | voir ci-dessous |
| CNAME | `www` | `neiri.fr.` | redirection www → neiri.fr (Caddy) |

⚠️ OVH crée par défaut des enregistrements `A` et `AAAA` (et parfois `www`) vers son hébergement 100 Mo :
**les remplacer ou les supprimer**. Un `AAAA` restant vers OVH fait échouer le certificat HTTPS (Let's Encrypt
essaie l'IPv6 en premier) et envoie une partie des visiteurs sur la page d'attente OVH.

Vérifier (propagation : de quelques minutes à quelques heures) :

```sh
dig +short neiri.fr A
dig +short neiri.fr AAAA      # vide, ou l'IPv6 du serveur Hetzner
dig +short www.neiri.fr
```

## 2. Serveur (`infra/.env.beta`)

```
LOCALLIFE_DOMAIN=neiri.fr
LOCALLIFE_OLD_DOMAIN=locallife-beta.duckdns.org
```

puis `docker compose --env-file .env.beta -f docker-compose.beta.yml up -d --build`. Caddy obtient les certificats
de `neiri.fr` et `www.neiri.fr` ; l'ancien domaine redirige vers le nouveau (liens d'e-mails déjà envoyés,
applications déjà installées). `FRONTEND_BASE_URL` (liens des e-mails) suit `LOCALLIFE_DOMAIN`.

Contrôle : `curl -i https://neiri.fr/actuator/health` → `{"status":"UP"}`.

## 3. Services à mettre à jour

* **Google Cloud** (connexion Google), client OAuth « Web » : ajouter `https://neiri.fr` aux origines JavaScript
  autorisées.
* **EAS** (application) : `eas env:create --environment production --name EXPO_PUBLIC_API_URL --value https://neiri.fr --visibility plaintext`.
* **Backblaze / sauvegardes** : inchangé.

## 4. E-mails

**Réception** (`contact@neiri.fr`) : OVHcloud → Web Cloud → E-mails → créer l'adresse sur l'offre MX Plan incluse.
Les enregistrements `MX` d'OVH sont déjà dans la zone : ne pas y toucher.

**Envoi** (Brevo, gratuit jusqu'à 300 e-mails par jour) :

1. Compte Brevo → Paramètres → Expéditeurs, domaines et IP dédiées → Domaines → ajouter `neiri.fr`.
2. Brevo affiche les enregistrements à créer dans la zone OVH (code de vérification `brevo-code`, DKIM, DMARC) :
   les recopier tels quels.
3. **SPF : un seul enregistrement TXT `v=spf1` par domaine.** OVH en crée déjà un ; le modifier pour y ajouter
   Brevo plutôt que d'en créer un second :
   `v=spf1 include:mx.ovh.com include:spf.brevo.com ~all`
4. **DMARC** (si Brevo ne le fournit pas) : TXT sur `_dmarc` : `v=DMARC1; p=none; rua=mailto:contact@neiri.fr`.
5. Une fois le domaine « authentifié » chez Brevo : Paramètres → SMTP et API → identifiant et clé SMTP, puis dans
   `infra/.env.beta` :

```
SMTP_HOST=smtp-relay.brevo.com
SMTP_PORT=587
SMTP_USERNAME=<identifiant SMTP Brevo>
SMTP_PASSWORD=<clé SMTP Brevo>
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_SSL=false
MAIL_FROM=Neiri <no-reply@neiri.fr>
MAIL_REPLY_TO=contact@neiri.fr
```

`docker compose --env-file .env.beta -f docker-compose.beta.yml up -d backend`, puis Administration → E-mail →
envoyer un test. Ensuite, révoquer le mot de passe d'application Gmail utilisé jusqu'ici.
