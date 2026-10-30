package org.di.digital.reporting.controller;

import org.di.digital.reporting.service.ConsolidationService;
import org.di.digital.reporting.service.SubmissionService;
import org.di.digital.reporting.service.TemplateService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

/**
 * Calls every public endpoint method of the reporting controllers through Spring method security
 * (the same @EnableMethodSecurity the application uses) with different roles.
 */
class ReportingControllersSecurityTest {

    private static AnnotationConfigApplicationContext context;

    @Configuration
    @EnableMethodSecurity
    static class SecuredControllers {
        @Bean
        TemplateService templateService() {
            return mock(TemplateService.class);
        }

        @Bean
        SubmissionService submissionService() {
            return mock(SubmissionService.class);
        }

        @Bean
        ConsolidationService consolidationService() {
            return mock(ConsolidationService.class);
        }

        @Bean
        ConsolidatedReportController consolidatedReportController(ConsolidationService service) {
            return new ConsolidatedReportController(service);
        }

        @Bean
        TemplateAdminController templateAdminController(TemplateService service) {
            return new TemplateAdminController(service);
        }

        @Bean
        OperatorReportController operatorReportController(TemplateService templates, SubmissionService submissions) {
            return new OperatorReportController(templates, submissions);
        }
    }

    @BeforeAll
    static void startContext() {
        context = new AnnotationConfigApplicationContext(SecuredControllers.class);
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    static Stream<Arguments> adminEndpoints() {
        return Stream.of(ConsolidatedReportController.class, TemplateAdminController.class)
                .flatMap(ReportingControllersSecurityTest::endpoints);
    }

    static Stream<Arguments> operatorEndpoints() {
        return endpoints(OperatorReportController.class);
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("adminEndpoints")
    void adminEndpointsAllowAdmin(String controller, String method, Class<?> type, Method endpoint) {
        authenticateAs("ADMIN");
        assertThatCode(() -> invoke(type, endpoint)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("adminEndpoints")
    void adminEndpointsDenyRegionalAdmin(String controller, String method, Class<?> type, Method endpoint) {
        authenticateAs("REG_ADMIN");
        assertThat(catchThrowable(() -> invoke(type, endpoint))).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("adminEndpoints")
    void adminEndpointsDenyOrdinaryUser(String controller, String method, Class<?> type, Method endpoint) {
        authenticateAs("USER", "ADVANCED_USER");
        assertThat(catchThrowable(() -> invoke(type, endpoint))).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("adminEndpoints")
    void adminEndpointsDenyUnauthenticated(String controller, String method, Class<?> type, Method endpoint) {
        assertThat(catchThrowable(() -> invoke(type, endpoint)))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("operatorEndpoints")
    void operatorEndpointsAllowRegionalAdminOnly(String controller, String method, Class<?> type, Method endpoint) {
        authenticateAs("REG_ADMIN");
        assertThatCode(() -> invoke(type, endpoint)).doesNotThrowAnyException();

        authenticateAs("ADMIN");
        assertThat(catchThrowable(() -> invoke(type, endpoint))).isInstanceOf(AccessDeniedException.class);
    }

    private static Stream<Arguments> endpoints(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
                .map(m -> Arguments.of(type.getSimpleName(), m.getName(), type, m));
    }

    private static void authenticateAs(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private static void invoke(Class<?> type, Method endpoint) throws Throwable {
        Object controller = context.getBean(type);
        // The proxy exposes the same public methods; arguments are irrelevant because services are mocks
        Method proxied = controller.getClass().getMethod(endpoint.getName(), endpoint.getParameterTypes());
        try {
            proxied.invoke(controller, new Object[endpoint.getParameterCount()]);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
