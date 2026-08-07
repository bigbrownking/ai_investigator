package org.di.digital.exception;

import lombok.Getter;

@Getter
public class FaceAuthUnavailableException extends RuntimeException {

    private final String code;
    private final String detail;

    private FaceAuthUnavailableException(String message, String code, String detail) {
        super(message);
        this.code = code;
        this.detail = detail;
    }
    public static FaceAuthUnavailableException of(String message, String detail) {
        return new FaceAuthUnavailableException(message, null, detail);
    }

    public static FaceAuthUnavailableException fromCode(String code, String detail) {
        String message = FaceErrorMessages.map(code, "Не удалось удалить Face ID");
        return new FaceAuthUnavailableException(message, code, detail);
    }
}