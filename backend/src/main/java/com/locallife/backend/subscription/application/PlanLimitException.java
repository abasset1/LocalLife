package com.locallife.backend.subscription.application;

/** Action refusée parce que le compte a atteint une limite de son offre ({@link PlanLimits}) : 403. */
public class PlanLimitException extends RuntimeException {

    public PlanLimitException(String message) {
        super(message);
    }
}
