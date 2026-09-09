package org.di.digital.util.mapper;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Component
public class FileUrlResolver {

    private String getToken() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        String queryToken = request.getParameter("token");
        return StringUtils.hasText(queryToken) ? queryToken : null;
    }

    private String appendToken(String url) {
        String token = getToken();
        return token != null ? url + "&token=" + UriUtils.encode(token, StandardCharsets.UTF_8) : url;
    }

    public String preview(String fileUrl) {
        if (fileUrl == null) return null;
        String url = "/api/files/preview?path=" + UriUtils.encode(fileUrl, StandardCharsets.UTF_8);
        return appendToken(url);
    }

    public String download(String fileUrl, String originalFileName) {
        if (fileUrl == null) return null;
        String url = "/api/files/download?path=" + UriUtils.encode(fileUrl, StandardCharsets.UTF_8)
                + "&name=" + UriUtils.encode(originalFileName, StandardCharsets.UTF_8);
        return appendToken(url);
    }
}
