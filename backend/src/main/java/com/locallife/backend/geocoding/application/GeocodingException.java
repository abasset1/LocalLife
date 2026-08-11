package com.locallife.backend.geocoding.application;

/**
 * Exception de base pour un échec de géocodage (LL-3012).
 */
public class GeocodingException extends RuntimeException {

    public GeocodingException(String message) {
        super(message);
    }

    public GeocodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
