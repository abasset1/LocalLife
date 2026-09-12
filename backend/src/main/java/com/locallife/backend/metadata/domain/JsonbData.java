package com.locallife.backend.metadata.domain;

/**
 * Valeur JSON destinée à une colonne PostgreSQL {@code jsonb}.
 *
 * <p>Ce type dédié évite qu'un convertisseur JDBC {@code String -> PGobject}
 * s'applique aux autres colonnes texte de l'application.</p>
 */
public record JsonbData(String value) {
}
