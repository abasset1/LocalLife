package com.locallife.backend.place.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.locallife.backend.place.domain.ClaimTransitions.Action;
import org.junit.jupiter.api.Test;

class ClaimTransitionsTest {

    @Test
    void requester_ShouldSubmitDraftsAndCompletedClaims_AndWithdrawUntilReview() {
        assertThat(ClaimTransitions.isAllowed(ClaimStatus.DRAFT, Action.SUBMIT)).isTrue();
        assertThat(ClaimTransitions.isAllowed(ClaimStatus.INFO_REQUESTED, Action.SUBMIT)).isTrue();
        assertThat(ClaimTransitions.isAllowed(ClaimStatus.SUBMITTED, Action.SUBMIT)).isFalse();
        assertThat(ClaimTransitions.isAllowed(ClaimStatus.SUBMITTED, Action.WITHDRAW)).isTrue();
        assertThat(ClaimTransitions.isAllowed(ClaimStatus.IN_REVIEW, Action.WITHDRAW)).isFalse();
        assertThat(ClaimTransitions.isEditable(ClaimStatus.DRAFT)).isTrue();
        assertThat(ClaimTransitions.isEditable(ClaimStatus.SUBMITTED)).isFalse();
    }

    @Test
    void moderator_ShouldDecideOnlySubmittedClaims_WithAReasonForRefusals() {
        for (Action decision : new Action[] {Action.APPROVE, Action.REJECT, Action.REQUEST_INFO}) {
            assertThat(decision.isModeration()).isTrue();
            assertThat(ClaimTransitions.isAllowed(ClaimStatus.SUBMITTED, decision)).isTrue();
            assertThat(ClaimTransitions.isAllowed(ClaimStatus.IN_REVIEW, decision)).isTrue();
            assertThat(ClaimTransitions.isAllowed(ClaimStatus.DRAFT, decision)).isFalse();
            assertThat(ClaimTransitions.isAllowed(ClaimStatus.INFO_REQUESTED, decision)).isFalse();
        }
        assertThat(ClaimTransitions.requiresReason(Action.APPROVE)).isFalse();
        assertThat(ClaimTransitions.requiresReason(Action.REJECT)).isTrue();
        assertThat(ClaimTransitions.requiresReason(Action.REQUEST_INFO)).isTrue();
    }

    @Test
    void closedClaims_ShouldAcceptNoAction() {
        for (ClaimStatus closed : new ClaimStatus[] {ClaimStatus.APPROVED, ClaimStatus.REJECTED,
                ClaimStatus.WITHDRAWN}) {
            assertThat(closed.isActive()).isFalse();
            for (Action action : Action.values()) {
                assertThat(ClaimTransitions.isAllowed(closed, action)).as(closed + " " + action).isFalse();
            }
        }
    }
}
