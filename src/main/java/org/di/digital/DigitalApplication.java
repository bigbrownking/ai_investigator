package org.di.digital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class DigitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitalApplication.class, args);
    }

}
