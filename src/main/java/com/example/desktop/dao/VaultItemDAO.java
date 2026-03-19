package com.example.desktop.dao;

import com.example.desktop.model.VaultItemFx;

import java.sql.SQLException;
import java.util.List;

/**
 * DAO contract for desktop vault items.
 */
public interface VaultItemDAO {

    List<VaultItemFx> findAllByUserId(long userId) throws SQLException;

    VaultItemFx insert(long userId, VaultItemFx item) throws SQLException;

    boolean deleteById(long userId, long itemId) throws SQLException;
}
