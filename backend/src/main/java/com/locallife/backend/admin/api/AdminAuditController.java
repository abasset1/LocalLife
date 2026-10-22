package com.locallife.backend.admin.api;

import com.locallife.backend.admin.application.AdminActionLog;
import com.locallife.backend.admin.application.AdminActionLog.Page;
import com.locallife.backend.admin.infrastructure.AdminActionLogRepository.Filter;
import com.locallife.backend.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consultation du journal des actions d'administration ({@code /api/v1/admin/**} : rôle ADMIN). */
@RestController
@RequestMapping("/api/v1/admin/action-log")
public class AdminAuditController {

    private static final int MAX_SIZE = 100;

    private final AdminActionLog actionLog;

    public AdminAuditController(AdminActionLog actionLog) {
        this.actionLog = actionLog;
    }

    @Operation(summary = "Journal des actions d'administration",
            description = "Les plus récentes d'abord ; filtres facultatifs : action, type et identifiant de cible, "
                    + "auteur. En ajout seul : rien ne s'y modifie ni ne s'y supprime.")
    @GetMapping
    public ResponseEntity<Object> find(
            @RequestParam(required = false) String action, @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId, @RequestParam(required = false) Long actorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            ErrorResponse body = new ErrorResponse(Instant.now(), 400, "Bad Request",
                    "'page' doit être positif et 'size' entre 1 et " + MAX_SIZE + ".", request.getRequestURI());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
        Page result = actionLog.find(new Filter(blank(action), blank(targetType), blank(targetId), actorId), page,
                size);
        return ResponseEntity.ok(result);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
