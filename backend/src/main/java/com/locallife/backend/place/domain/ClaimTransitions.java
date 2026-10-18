package com.locallife.backend.place.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Transitions d'une demande d'administration (ADR-0012, § 3.1), en fonctions pures. Le demandeur rédige, soumet,
 * complète ou retire ; le modérateur prend en charge et décide. Une demande approuvée, refusée ou retirée est close.
 */
public final class ClaimTransitions {

    /** Action sur une demande, et qui peut la faire. */
    public enum Action {
        SUBMIT(false), WITHDRAW(false), START_REVIEW(true), APPROVE(true), REJECT(true), REQUEST_INFO(true);

        private final boolean moderation;

        Action(boolean moderation) {
            this.moderation = moderation;
        }

        /** Action réservée au modérateur. */
        public boolean isModeration() {
            return moderation;
        }
    }

    private static final Map<Action, Set<ClaimStatus>> FROM = Map.of(
            Action.SUBMIT, EnumSet.of(ClaimStatus.DRAFT, ClaimStatus.INFO_REQUESTED),
            Action.WITHDRAW, EnumSet.of(ClaimStatus.DRAFT, ClaimStatus.SUBMITTED, ClaimStatus.INFO_REQUESTED),
            Action.START_REVIEW, EnumSet.of(ClaimStatus.SUBMITTED),
            // Décider sans prise en charge préalable est permis : la prise en charge sert à signaler aux autres
            // modérateurs qu'une demande est en cours d'examen.
            Action.APPROVE, EnumSet.of(ClaimStatus.SUBMITTED, ClaimStatus.IN_REVIEW),
            Action.REJECT, EnumSet.of(ClaimStatus.SUBMITTED, ClaimStatus.IN_REVIEW),
            Action.REQUEST_INFO, EnumSet.of(ClaimStatus.SUBMITTED, ClaimStatus.IN_REVIEW));

    private static final Map<Action, ClaimStatus> TO = Map.of(
            Action.SUBMIT, ClaimStatus.SUBMITTED,
            Action.WITHDRAW, ClaimStatus.WITHDRAWN,
            Action.START_REVIEW, ClaimStatus.IN_REVIEW,
            Action.APPROVE, ClaimStatus.APPROVED,
            Action.REJECT, ClaimStatus.REJECTED,
            Action.REQUEST_INFO, ClaimStatus.INFO_REQUESTED);

    private ClaimTransitions() {
    }

    public static boolean isAllowed(ClaimStatus current, Action action) {
        return FROM.get(action).contains(current);
    }

    public static ClaimStatus target(Action action) {
        return TO.get(action);
    }

    /** Un motif est exigé pour refuser ou demander des informations : c'est ce que lit le professionnel. */
    public static boolean requiresReason(Action action) {
        return action == Action.REJECT || action == Action.REQUEST_INFO;
    }

    /** Le demandeur peut modifier le texte de sa demande tant qu'elle n'est pas chez le modérateur. */
    public static boolean isEditable(ClaimStatus status) {
        return status == ClaimStatus.DRAFT || status == ClaimStatus.INFO_REQUESTED;
    }
}
