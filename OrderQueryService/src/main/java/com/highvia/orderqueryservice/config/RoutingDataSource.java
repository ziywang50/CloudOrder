package com.highvia.orderqueryservice.config;

import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoutingDataSource extends AbstractDataSource {
    private final List<DataSource> dataSources;
    private final AtomicInteger counter = new AtomicInteger(0);

    public RoutingDataSource(List<DataSource> dataSources) {
        this.dataSources = dataSources;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getNext().getConnection();
    }

    @Override
    public Connection getConnection(String u, String p) throws SQLException {
        return getNext().getConnection(u, p);
    }

    private DataSource getNext() {
        int idx = Math.abs(counter.getAndIncrement() % dataSources.size());
        return dataSources.get(idx);
    }
}
