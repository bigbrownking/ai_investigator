package org.di.digital.config;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public TomcatServletWebServerFactory tomcatFactory() {
        TomcatServletWebServerFactory factory =
                new TomcatServletWebServerFactory();

        factory.addContextCustomizers(context ->
                context.setAllowCasualMultipartParsing(true)
        );

        return factory;
    }
}
