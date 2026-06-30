package org.example.API;

import org.example.ApplicationLayer.dto.ApiResponse;
import org.hibernate.JDBCException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger =
            Logger.getLogger(GlobalExceptionHandler.class.getName());

    private static final String DATABASE_UNAVAILABLE_MESSAGE =
            "The database is currently unavailable. Please try again in a few moments.";

    @ExceptionHandler({
            CannotCreateTransactionException.class,
            DataAccessException.class,
            JpaSystemException.class,
            JDBCException.class,
            SQLException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleDatabaseUnavailable(Exception error) {
        logger.log(Level.SEVERE, "[GlobalExceptionHandler] Database is unavailable", error);
        return databaseUnavailable();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException error) {
        if (isDatabaseUnavailable(error)) {
            logger.log(Level.SEVERE, "[GlobalExceptionHandler] Database is unavailable", error);
            return databaseUnavailable();
        }

        // Log the real cause with its stack trace. Without this, unexpected runtime
        // failures surface to the client only as a generic 500 with no server-side
        // record, making them impossible to diagnose.
        logger.log(Level.SEVERE, "[GlobalExceptionHandler] Unexpected error", error);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("A system error occurred. Please try again later."));
    }

    public static boolean isDatabaseUnavailable(Throwable error) {
        Throwable current = error;

        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();

            if (className.contains("CannotCreateTransactionException")
                    || className.contains("JDBCConnectionException")
                    || className.contains("PSQLException")
                    || className.contains("DataAccessResourceFailureException")
                    || className.contains("JpaSystemException")
                    || className.contains("SQLTransientConnectionException")) {
                return true;
            }

            if (message != null &&
                    (message.contains("Could not open JPA EntityManager")
                            || message.contains("Unable to acquire JDBC Connection")
                            || message.contains("Connection refused")
                            || message.contains("Connection timed out")
                            || message.contains("The connection attempt failed")
                            || message.contains("Communications link failure"))) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    public static <T> ResponseEntity<ApiResponse<T>> databaseUnavailable() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(DATABASE_UNAVAILABLE_MESSAGE));
    }
}