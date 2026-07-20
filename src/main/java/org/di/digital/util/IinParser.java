package org.di.digital.util;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

@Slf4j
public class IinParser {

    private IinParser() {}

    public static LocalDate parseBirthDate(String iin) {
        if (iin == null || iin.length() != 12 || !iin.matches("\\d{12}")) {
            log.warn("Invalid IIN for birthDate: {}", iin);
            return null;
        }

        int yy = Integer.parseInt(iin.substring(0, 2));
        int mm = Integer.parseInt(iin.substring(2, 4));
        int dd = Integer.parseInt(iin.substring(4, 6));
        int centuryDigit = Character.getNumericValue(iin.charAt(6));

        int century;
        switch (centuryDigit) {
            case 1, 2 -> century = 1800;
            case 3, 4 -> century = 1900;
            case 5, 6 -> century = 2000;
            default -> {
                log.warn("Invalid century digit in IIN: {}", iin);
                return null;
            }
        }

        try {
            return LocalDate.of(century + yy, mm, dd);
        } catch (Exception e) {
            log.warn("Invalid date components in IIN {}: {}", iin, e.getMessage());
            return null;
        }
    }
}