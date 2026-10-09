package com.locallife.backend.push.application;

/** Le service Expo Push n'a pas pu être joint ou a refusé la requête dans son ensemble. */
public class PushUnavailableException extends RuntimeException {

    public PushUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
