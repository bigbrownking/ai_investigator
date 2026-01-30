package org.di.digital.util;

import jakarta.servlet.http.HttpServletRequest;
import org.di.digital.model.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.StringTokenizer;

public class UserUtil {
    public static String getClientIpAddress(HttpServletRequest request) {
        String forwardHeader= request.getHeader("X-Forwarded-For");
        if (forwardHeader == null) {
            return request.getRemoteAddr();
        } else {
            return new StringTokenizer(forwardHeader, ",").nextToken().trim();
        }
    }
    public static User getCurrentUser(){
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    public static HttpServletRequest getCurrentHttpRequest(){
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}
