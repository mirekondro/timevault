package com.example.desktop.dao;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;

import java.sql.Connection;

/**
 * Creates SQL Server connections for the desktop DAO layer.
 */
public class ConnectionManager {

    private final DatabaseConfig databaseConfig;

    public ConnectionManager(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }

    public Connection getConnection() throws SQLServerException {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(databaseConfig.server());
        dataSource.setPortNumber(databaseConfig.port());
        dataSource.setDatabaseName(databaseConfig.database());
        dataSource.setUser(databaseConfig.user());
        dataSource.setPassword(databaseConfig.password());
        dataSource.setEncrypt("true");
        dataSource.setTrustServerCertificate(databaseConfig.trustServerCertificate());
        return dataSource.getConnection();
    }
}
