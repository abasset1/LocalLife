package com.locallife.backend.activityclassification.application;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Données réelles de test : extraction OpenAgenda du 29/09/2026 ({@code legacy/categorie_source_type.csv},
 * 47 338 activités par catégorie d'origine) et instantané des correspondances de l'ancienne liste Java
 * {@code LegacyCategoryMapper} ({@code legacy/legacy_mapper_snapshot.csv}, figé avant sa suppression par
 * l'issue #50 : 130 valeurs, 3 162 activités).
 */
final class RealExtraction {

    private RealExtraction() {
    }

    /** Lignes {category, nb} de l'extraction (colonne source_type ignorée). */
    static List<String[]> readExtraction() throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = RealExtraction.class
                .getResourceAsStream("/legacy/categorie_source_type.csv");
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.readLine(); // en-tête
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = parseCsvLine(line);
                rows.add(new String[] {cells[1], cells[2]});
            }
        }
        return rows;
    }

    static String[] parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells.toArray(new String[0]);
    }


    /** Lignes {catégorie d'origine, nb, codes « A+B »} de l'instantané de l'ancienne liste Java. */
    static List<String[]> readLegacySnapshot() throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = RealExtraction.class.getResourceAsStream("/legacy/legacy_mapper_snapshot.csv");
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.readLine(); // en-tête
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(parseCsvLine(line));
                }
            }
        }
        return rows;
    }
}
