package org.di.digital.config.db;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class FLDatasourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.fl")
    public DataSourceProperties flDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "flDataSource")
    public DataSource flDataSource() {
        return flDataSourceProperties()
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "flJdbcTemplate")
    public JdbcTemplate flJdbcTemplate(@Qualifier("flDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "flTransactionManager")
    public PlatformTransactionManager flTransactionManager(@Qualifier("flDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

}
