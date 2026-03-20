package com.example.desktop.dao;

import java.sql.SQLException;

/**
 * Startup hook for whichever backend the desktop app is currently using.
 */
public interface AppInitializer {

    void initialize() throws SQLException;
}
