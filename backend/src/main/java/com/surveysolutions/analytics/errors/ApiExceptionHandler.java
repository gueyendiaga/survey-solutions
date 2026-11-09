package com.surveysolutions.analytics.errors;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import com.surveysolutions.analytics.questions.DataNotSynchronizedException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** 409 + syncPending : le frontend affiche l'état de synchronisation et réessaie. */
    @ExceptionHandler(DataNotSynchronizedException.class)
    ProblemDetail handleDataNotSynchronized(DataNotSynchronizedException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("syncPending", true);
        return problem;
    }

    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail handleHeadquartersResponse(RestClientResponseException exception) {
        logger.warn("Survey Solutions HQ a répondu avec le statut HTTP {}", exception.getStatusCode());
        return problem(
                HttpStatus.BAD_GATEWAY,
                "Survey Solutions HQ a refusé ou échoué la requête (HTTP "
                        + exception.getStatusCode().value() + "). Vérifiez le compte API et ses permissions.");
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail handleHeadquartersConnection(ResourceAccessException exception) {
        logger.warn("Survey Solutions HQ est inaccessible: {}", exception.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Connexion impossible à Survey Solutions HQ.");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
