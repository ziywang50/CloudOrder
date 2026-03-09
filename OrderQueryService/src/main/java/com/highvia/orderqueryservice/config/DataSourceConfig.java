package com.highvia.orderqueryservice.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Arrays;

@Configuration
public class DataSourceConfig {
    @Value("${spring.datasource.read1.url}")
    private String url1;

    @Value("${spring.datasource.read2.url}")
    private String url2;

    @Value("${spring.datasource.read3.url}")
    private String url3;

    @Value("${spring.datasource.read1.username}")
    private String username;

    @Value("${spring.datasource.read1.password}")
    private String password;

    @Primary
    @Bean
    public DataSource dataSource() {
        return new RoutingDataSource(Arrays.asList(
                buildDs(url1),
                buildDs(url2),
                buildDs(url3)
        ));
    }

    private HikariDataSource buildDs(String url) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(50);
        ds.setMinimumIdle(20);
        ds.setConnectionTimeout(30000);
        return ds;
    }
}
