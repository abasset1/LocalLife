package com.locallife.backend.user.application;

import org.springframework.http.HttpStatus;

/** Action de gestion de compte refusée, avec le statut HTTP du contrat (voir {@link AccountAdministrationService}). */
public class AccountActionException extends RuntimeException {

    private final HttpStatus status;

    private AccountActionException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static AccountActionException badRequest(String message) {
        return new AccountActionException(HttpStatus.BAD_REQUEST, message);
    }

    static AccountActionException forbidden(String message) {
        return new AccountActionException(HttpStatus.FORBIDDEN, message);
    }

    static AccountActionException notFound(String message) {
        return new AccountActionException(HttpStatus.NOT_FOUND, message);
    }

    static AccountActionException conflict(String message) {
        return new AccountActionException(HttpStatus.CONFLICT, message);
    }
}
