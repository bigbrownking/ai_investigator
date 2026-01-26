package org.di.digital.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UrlBuilder {

    private static final String HTTP_PROTOCOL = "http://";

    public String buildUrl(String host, String port, String endpoint) {
        return String.format(HTTP_PROTOCOL+"%s:%s%s", host, port, endpoint);
    }
    public String buildUrl(String host, String port, String endpoint, String... params) {
        String baseUrl = buildUrl(host, port, endpoint);
        return String.format(baseUrl, (Object[]) params);
    }

}