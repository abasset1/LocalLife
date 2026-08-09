# LocalLife

Plateforme locale d'activités, avec une API Spring Boot et une carte web React.

## Fonctionnalités (Sprint 1)

* Carte interactive (affichage, zoom, déplacement) centrée sur Marseille.
* Affichage des activités de démonstration sous forme de marqueurs.
* Popup au clic sur un marqueur : titre, catégorie et date de l'activité.

## Fonctionnalités (Sprint 2)

* Création d'un compte utilisateur (`POST /api/v1/users`) et consultation
  par id (`GET /api/v1/users/{id}`).
* Consultation des catégories (`GET /api/v1/categories`).
* Formulaire de contribution : proposer une activité directement depuis la
  carte (titre, description, catégorie, localisation), envoyée à
  `POST /api/v1/activities` (statut `PENDING` par défaut — pas de
  modération à ce stade).

## Démarrage

La base de données doit être démarrée avant le backend :

```powershell
cd infra
docker compose up -d

cd ..\backend
mvn spring-boot:run
```

Dans un second terminal, démarrer le frontend :

```powershell
cd frontend
npm install
npm run dev
```

Le frontend est accessible sur `http://localhost:5173` et le backend sur
`http://localhost:8080`.
