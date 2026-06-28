package org.example.API;

import org.example.ApplicationLayer.dto.ApiResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLTransientConnectionException;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger =
            Logger.getLogger(GlobalExceptionHandler.class.getName());

    @ExceptionHandler({
            DataAccessException.class,
            CannotCreateTransactionException.class,
            SQLTransientConnectionException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleDatabaseUnavailable(Exception error) {
        logger.log(Level.SEVERE, "[GlobalExceptionHandler] Database is unavailable", error);

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(
                        "The database is currently unavailable. Please try again in a few moments."
                ));
    }
}