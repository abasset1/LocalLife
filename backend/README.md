# Backend — LocalLife

API REST du projet LocalLife. Spring Boot 4.1.0, Java 21, PostgreSQL/PostGIS.

## Prérequis

* Java 21
* Maven
* Docker Desktop (pour la base de données et/ou le conteneur backend)

## Démarrage

**1. Démarrer la base de données** (PostgreSQL/PostGIS via Docker Compose) :

```bash
cd infra
docker compose up -d
```

**2. Démarrer l'application** :

```bash
cd backend
mvn spring-boot:run
```

Le profil `local` est actif par défaut. L'application est accessible sur `http://localhost:8080`.

* Health check : `http://localhost:8080/actuator/health`
* Documentation API (Swagger) : `http://localhost:8080/swagger-ui.html`

## Profils Spring

| Profil  | Usage                                    | Datasource configurée |
| ------- | ----------------------------------------- | ---------------------- |
| `local` | Développement local (actif par défaut)    | ✅ (Docker Compose)     |
| `dev`   | Environnement de développement partagé    | ⏳ pas encore            |
| `test`  | Exécution des tests                       | ⏳ pas encore            |
| `prod`  | Production                                | ⏳ pas encore            |

Pour forcer un profil différent de `local` :

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Docker

**Construire l'image :**

```bash
cd backend
docker build -t locallife-backend .
```

**Lancer le conteneur** — le backend doit rejoindre le même réseau Docker que la base de données, et cibler le conteneur Postgres par son nom (`localhost` ne fonctionne pas entre deux conteneurs) :

```bash
docker network ls   # repérer le réseau créé par docker compose (ex: infra_default)

docker run --rm -p 8080:8080 \
  --network infra_default \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://locallife-postgres:5432/locallife \
  locallife-backend
```

## Commandes Maven utiles

| Commande                  | Effet                                                          |
| -------------------------- | --------------------------------------------------------------- |
| `mvn spring-boot:run`      | Démarre l'application (profil `local` par défaut)              |
| `mvn verify`                | Compile, lance les tests, vérifie le formatage (Spotless) et la qualité de code (Checkstyle) — échoue en cas de non-conformité |
| `mvn spotless:apply`       | Corrige automatiquement le formatage du code                    |
| `mvn clean package`         | Construit le jar exécutable (`target/backend-*.jar`)            |

> `mvn verify` et les tests nécessitent que PostgreSQL soit démarré (`docker compose up -d` dans `infra/`).
