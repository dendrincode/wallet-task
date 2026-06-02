package com.wallet.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(
            DataSourceProperties properties,
            @Value("${spring.datasource.hikari.maximum-pool-size:20}") int maximumPoolSize,
            @Value("${spring.datasource.hikari.minimum-idle:5}") int minimumIdle,
            @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeout,
            @Value("${spring.datasource.hikari.idle-timeout:600000}") long idleTimeout) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setIdleTimeout(idleTimeout);

        return dataSource;
    }
}
