package org.di.digital.model.enums.cases;

import lombok.Getter;
import org.di.digital.model.enums.settings.UserSettingsLanguage;

@Getter
public enum CaseRejectionReason {
    DEADLINE_INTERRUPTED("Прерванные сроки", "Мерзімдері үзілген", "Interrupted deadlines"),
    TERMINATED("Прекращенные", "Тоқтатылған", "Terminated"),
    SENT_186("Передано в порядке ст. 186 УПК РК", "Қазақстан Республикасы Қылмыстық-процестік кодексінің 186-бабына сәйкес берілді.",
            "Transferred pursuant to Article 186 of the Code of Criminal Procedure of the Republic of Kazakhstan."),
    SENT_TO_COURT("Направленные в суд", "Сотқа жіберілген", "Sent to court");

    private final String label;
    private final String kzName;
    private final String enName;

    CaseRejectionReason(String label, String kzName, String enName) {
        this.label = label;
        this.kzName = kzName;
        this.enName = enName;
    }

    public String localized(UserSettingsLanguage lang) {
        return switch (lang) {
            case KZ -> kzName;
            case EN -> enName;
            case RU -> label;
        };
    }

    public static CaseRejectionReason fromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        for (CaseRejectionReason reason : values()) {
            if (reason.getLabel().equalsIgnoreCase(displayName.trim()) ||
                    reason.getKzName().equalsIgnoreCase(displayName.trim()) ||
                    reason.getEnName().equalsIgnoreCase(displayName.trim())) {
                return reason;
            }
        }

        try {
            return valueOf(displayName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid rejection reason: " + displayName +
                    ". Valid values: Прерванные сроки, Прекращенные, Направленные в суд (or DEADLINE_INTERRUPTED, TERMINATED, SENT_TO_COURT)");
        }
    }
}