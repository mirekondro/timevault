package com.example.desktop.dao;

import com.example.desktop.model.VaultItemFx;

import java.sql.SQLException;
import java.util.List;

/**
 * DAO contract for desktop vault items.
 */
public interface VaultItemDAO {

    List<VaultItemFx> findAll() throws SQLException;

    VaultItemFx insert(VaultItemFx item) throws SQLException;

    void deleteById(long itemId) throws SQLException;
}
