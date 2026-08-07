# LocalLife

Plateforme locale d'activités, avec une API Spring Boot et une carte web React.

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
