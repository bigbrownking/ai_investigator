package org.di.digital.exception;

public final class FaceErrorMessages {
    private FaceErrorMessages() {}

    public static String map(String code) {
        if (code == null) return "Ошибка проверки лица";
        return switch (code) {
            case "IU011"              -> "Требуется эталонное фото лица. Пройдите регистрацию Face ID заново.";
            case "LIVENESS_FAILED"    -> "Обнаружено фото или экран. Используйте живое лицо.";
            case "MATCH_FAILED"       -> "Лицо не совпадает с сохранённым эталоном.";
            case "POSE_NOT_CONFIRMED" -> "Поза не распознана. Следуйте инструкциям на экране.";
            case "NO_FACE"            -> "Лицо не обнаружено. Убедитесь, что лицо в кадре.";
            case "INCONSISTENT_FACES" -> "На кадрах разные лица. Повторите попытку.";
            case "INTERNAL"           -> "Внутренняя ошибка обработки лица. Повторите попытку.";
            default                   -> "Ошибка проверки лица";
        };
    }
    public static String map(String code, String fallback) {
        if (code == null) return fallback != null ? fallback : "Ошибка проверки лица";
        String mapped = map(code);
        return mapped.equals("Ошибка проверки лица") && fallback != null ? fallback : mapped;
    }
}