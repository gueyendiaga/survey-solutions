package com.surveysolutions.analytics.questions;

/** Les données de la version demandée ne sont pas encore importées dans l'entrepôt. */
public class DataNotSynchronizedException extends RuntimeException {
    public DataNotSynchronizedException(String message) {
        super(message);
    }
}
