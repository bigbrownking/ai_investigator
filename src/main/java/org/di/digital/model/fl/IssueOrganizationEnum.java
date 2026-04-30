package org.di.digital.model.fl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IssueOrganizationEnum {
    MINISTRY_OF_JUSTICE(1, "МИНИСТЕРСТВО ЮСТИЦИИ РК", "ҚР ӘДІЛЕТ МИНИСТРЛІГІ"),
    MINISTRY_OF_INTERNAL_AFFAIRS(2, "МИНИСТЕРСТВО ВНУТРЕННИХ ДЕЛ РК", "ҚР ІШКІ ІСТЕР МИНИСТРЛІГІ"),
    FOREIGN_AUTHORITY(3, "Уполномоченный орган иностранного государства", "Шет мемлекеттің уәкілетті органы");

    private final int id;
    private final String ruName;
    private final String kzName;

    public static String getNameById(String id, String language) {
        if (id == null) return null;
        for (IssueOrganizationEnum org : values()) {
            if (String.valueOf(org.id).equals(id)) {
                return language.equals("казахском") ? org.kzName : org.ruName;
            }
        }
        return id;
    }
}