package org.di.digital.util;

import lombok.experimental.UtilityClass;
import org.di.digital.model.enums.TreeModuleType;

@UtilityClass
public class TreeUrlBuilder {

    private static final String HTTP_PROTOCOL = "http://";

    public static String buildModuleUrl(String host, String port, String caseNumber, TreeModuleType moduleType) {
        String endpoint = moduleType.getEndpoint(caseNumber);
        return String.format(HTTP_PROTOCOL + "%s:%s%s", host, port, endpoint);
    }

    public static String buildBaseUrl(String host, String port) {
        return String.format(HTTP_PROTOCOL + "%s:%s", host, port);
    }
}
