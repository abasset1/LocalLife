Docker & CI.

## Bêta (LL-9002)

* `docker-compose.beta.yml` — composants de l'environnement bêta
  (postgres, backend, frontend, reverse proxy Caddy).
* `Caddyfile.beta` — configuration du reverse proxy HTTPS.
* `.env.beta.example` — template des variables d'environnement bêta,
  sans valeur réelle. Le fichier `.env.beta` réel n'existe que sur le
  VPS bêta, jamais commité (voir `.gitignore`).

Détail complet des décisions et de la procédure de déploiement :
`docs/02_Architecture/BETA_DEPLOYMENT.md`.
