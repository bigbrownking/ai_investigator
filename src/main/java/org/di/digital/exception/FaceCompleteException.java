package org.di.digital.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class FaceCompleteException extends RuntimeException {

    private final HttpStatus status;
    private final String propertyKey;
    private final String propertyValue;

    public FaceCompleteException(HttpStatus status, String message,
                                 String propertyKey, String propertyValue) {
        super(message);
        this.status = status;
        this.propertyKey = propertyKey;
        this.propertyValue = propertyValue;
    }

    public FaceCompleteException(HttpStatus status, String message) {
        this(status, message, null, null);
    }
}