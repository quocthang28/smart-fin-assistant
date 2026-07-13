package com.example.smartfinassistant.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class ReadonlyDataSourceConfig {

    @Bean(name = "readonlyDataSourceProperties", defaultCandidate = false)
    @ConfigurationProperties("app.datasource.readonly")
    DataSourceProperties readonlyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "readonlyDataSource", defaultCandidate = false)
    DataSource readonlyDataSource(
            @Qualifier("readonlyDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "readonlyJdbcTemplate", defaultCandidate = false)
    JdbcTemplate readonlyJdbcTemplate(
            @Qualifier("readonlyDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "readonlyTransactionManager", defaultCandidate = false)
    PlatformTransactionManager readonlyTransactionManager(
            @Qualifier("readonlyDataSource") DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
