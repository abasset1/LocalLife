package com.locallife.backend.place.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Le rattrapage SQL de la phase A (ADR-0011, § 7.1) classe les lieux comme
 * {@link CoordinatePrecisionPolicy#inferFromText} : même règle des textes génériques, sur PostgreSQL réel.
 * Le bloc de classification est lu dans {@code infra/verification/phase_a_precision_mesure.sql}, script remis
 * à l'administrateur ; les lieux de test sont insérés dans une transaction annulée.
 */
@SpringBootTest
class GenericPlaceSqlParityIntegrationTest {

    private static final Path SCRIPT = Path.of("..", "infra", "verification", "phase_a_precision_mesure.sql");

    private static final Path MIGRATION = Path.of("src", "main", "resources", "db", "migration",
            "V61__add_coordinate_precision.sql");

    private static final Pattern BLOCK = Pattern.compile(
            "-- classification:début\\n(.*?)\\n-- classification:fin", Pattern.DOTALL);

    /** Nom, adresse, commune. */
    private static final String[][] CASES = {
        {"Divers lieux à Aix-en-Provence", null, "Aix-en-Provence"},
        {"Différents lieux de la commune", null, "Arles"},
        {"Centre ville et divers lieux", null, "Hyères"},
        {"Centre ville Saint-Rémy-de-Provence", null, "Saint-Rémy-de-Provence"},
        {"Dans les rues du village", null, "Saint-Paul-de-Vence"},
        {"CENTRE-VILLE", null, "Pertuis"},
        {"ÉGLISE — centre-ville", null, "Pertuis"},
        {"En centre-ville", null, null},
        {"Le Village", null, "Gordes"},
        {"Plusieurs lieux", null, null},
        {"Centre ville", "   ", "Apt"},
        {"Fête du livre", "Divers lieux", "Apt"},
        {"Centre ville Cours Hyacinthe Bellon", null, "Ollioules"},
        {"Place du village", null, "Bonnieux"},
        {"90 Boulevard Victor Hugo Théâtre de Pertuis", null, "Pertuis"},
        {"Lieux divers", null, null},
        {"Divers lieux", "Place de l'Horloge", "Avignon"},
        {"Dans la ville d'Avignon", null, "Avignon"},
        {"Église Saint-Pierre", null, "Avignon"},
        {"Cœur de ville", null, "Cavaillon"},
        {null, null, null},
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void sqlClassification_ShouldMatchJavaRule() throws IOException {
        String block = classificationBlocks().get(0);
        Map<Long, String[]> inserted = new HashMap<>();
        Map<Long, Boolean> sqlApproximate = new HashMap<>();

        transactionTemplate.executeWithoutResult(status -> {
            for (String[] place : CASES) {
                Long id = jdbcTemplate.queryForObject(
                        "INSERT INTO location (name, address, city) VALUES (?, ?, ?) RETURNING id",
                        Long.class, place[0], place[1], place[2]);
                inserted.put(id, place);
            }
            String ids = String.join(",", inserted.keySet().stream().map(String::valueOf).toList());
            jdbcTemplate.query("WITH " + block + " SELECT id, approximate FROM precision_candidate WHERE id IN ("
                    + ids + ")", row -> {
                        sqlApproximate.put(row.getLong("id"), row.getBoolean("approximate"));
                    });
            status.setRollbackOnly();
        });

        assertEquals(CASES.length, sqlApproximate.size());
        for (Map.Entry<Long, String[]> entry : inserted.entrySet()) {
            String[] place = entry.getValue();
            boolean javaApproximate = CoordinatePrecisionPolicy.inferFromText(place[0], place[1], place[2])
                    == CoordinatePrecision.APPROXIMATE;
            assertEquals(javaApproximate, sqlApproximate.get(entry.getKey()),
                    "Classement divergent pour " + String.join(" | ", String.valueOf(place[0]),
                            String.valueOf(place[1]), String.valueOf(place[2])));
        }
    }

    @Test
    void script_ShouldRepeatTheSameClassificationBlock() throws IOException {
        List<String> blocks = classificationBlocks();
        assertEquals(2, blocks.size());
        assertEquals(blocks.get(0), blocks.get(1));
    }

    @Test
    void migration_ShouldUseTheSameClassificationBlock() throws IOException {
        Matcher matcher = BLOCK.matcher(Files.readString(MIGRATION, StandardCharsets.UTF_8));
        assertEquals(true, matcher.find(), "bloc de classification absent de V61");
        assertEquals(classificationBlocks().get(0), matcher.group(1));
    }

    @Test
    void script_ShouldOnlyRead() throws IOException {
        Pattern write = Pattern.compile("\\b(insert|update|delete|alter|create|drop|truncate|grant)\\b",
                Pattern.CASE_INSENSITIVE);
        for (String line : Files.readAllLines(SCRIPT, StandardCharsets.UTF_8)) {
            if (!line.strip().startsWith("--")) {
                assertFalse(write.matcher(line).find(), "Écriture dans le script de mesure : " + line);
            }
        }
    }

    private static List<String> classificationBlocks() throws IOException {
        Matcher matcher = BLOCK.matcher(Files.readString(SCRIPT, StandardCharsets.UTF_8));
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }
}
