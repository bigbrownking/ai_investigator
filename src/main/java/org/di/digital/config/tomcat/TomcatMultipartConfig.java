package org.di.digital.config.tomcat;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatMultipartConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatMultipartCustomizer() {
        return factory -> factory.addConnectorCustomizers(connector -> {
            connector.setMaxPartCount(300);
            connector.setMaxParameterCount(10000);
        });
    }
}