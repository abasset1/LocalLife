# --- Étape de build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copié séparément pour profiter du cache Docker sur les dépendances
COPY pom.xml checkstyle.xml ./
RUN mvn -q dependency:go-offline

COPY src ./src
# Les tests nécessitent une base PostgreSQL non disponible pendant le build de l'image
RUN mvn -q clean package -DskipTests

# --- Étape d'exécution ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/target/backend-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
