package com.locallife.backend.collector.api;

import com.locallife.backend.collector.application.ImportResult;
import com.locallife.backend.collector.application.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Déclencheur contrôlé du pipeline d'import (LL-7002, Sprint 7).
 *
 * <b>Décision MVP</b> (voir {@code SPRINT_7.md}) : déclenchement manuel,
 * protégé par le rôle {@code ADMIN} — pas de scheduler, conformément à la
 * règle du sprint « aucun scheduler complexe ». Contrôleur distinct
 * plutôt qu'une méthode ajoutée à {@code AdminActivityController} : chemin
 * différent ({@code /api/v1/admin/import}), et objectif différent
 * (déclencher le pipeline de collecte, pas gérer des activités déjà
 * persistées) — même raisonnement que celui documenté dans la javadoc
 * d'{@code AdminActivityController} pour justifier sa séparation
 * d'{@code ActivityController}.
 *
 * Protection effective au niveau {@code SecurityConfig}
 * ({@code .requestMatchers(HttpMethod.POST, "/api/v1/admin/import")
 * .hasRole("ADMIN")}), pas ici : même convention que tous les autres
 * endpoints d'administration du projet (LL-6005/LL-6006/LL-3008).
 *
 * <b>Réutilisation du pipeline existant sans duplication</b> (critère
 * d'acceptation du ticket) : ce contrôleur ne fait qu'invoquer
 * {@link ImportService#importAll()}, déjà responsable de l'orchestration
 * complète {@code Collector → Normalisation → Validation → Persistance}
 * depuis LL-5008 — aucune logique d'import dupliquée ici.
 *
 * <b>Journalisation</b> (critère d'acceptation du ticket) : déjà assurée
 * par {@link ImportService} lui-même (LL-5009, un log {@code INFO} par
 * source traitée) — ce contrôleur ne journalise rien de plus, pour ne pas
 * dupliquer une responsabilité déjà couverte. Le résultat détaillé
 * ({@link ImportResult}, un par {@code Collector} enregistré) est en plus
 * renvoyé dans la réponse HTTP, pour un retour immédiat à l'administrateur
 * sans avoir à consulter les logs.
 *
 * Un échec de collecte (ex. configuration OpenAgenda manquante, panne
 * réseau) ne fait pas échouer cet appel : {@link ImportService} le
 * capture déjà et le traduit en un {@code ImportResult} dégradé
 * ({@code errors = 1}), voir sa javadoc — cet endpoint répond donc
 * toujours {@code 200} avec le détail du résultat, y compris en cas
 * d'échec de collecte, plutôt que de renvoyer une erreur HTTP générique
 * qui masquerait ce détail.
 */
@RestController
@RequestMapping("/api/v1/admin/import")
public class AdminImportController {

    private final ImportService importService;

    public AdminImportController(ImportService importService) {
        this.importService = importService;
    }

    @Operation(
            summary = "Déclenche un import réel (réservé aux administrateurs)",
            description = "Exécute le pipeline Collector → Normalisation → Validation → Persistance pour "
                    + "chaque collecteur enregistré, et retourne le résultat détaillé par source. "
                    + "Déclenchement manuel uniquement (aucune planification automatique). Réservé au rôle ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "Import exécuté ; voir le détail par source dans la réponse "
                        + "(un échec de collecte pour une source se traduit par un résultat dégradé, pas "
                        + "par une erreur HTTP)."),
        @ApiResponse(responseCode = "401", description = "Authentification requise (JWT manquant ou invalide)."),
        @ApiResponse(responseCode = "403", description = "Rôle ADMIN requis.")
    })
    @PostMapping
    public ResponseEntity<List<ImportResult>> triggerImport() {
        List<ImportResult> results = importService.importAll();
        return ResponseEntity.ok(results);
    }

}
