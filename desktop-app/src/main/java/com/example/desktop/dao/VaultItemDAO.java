package com.example.desktop.dao;

import com.example.desktop.model.StoredImageRecord;
import com.example.desktop.model.VaultItemFx;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * DAO contract for desktop vault items.
 */
public interface VaultItemDAO {

    List<VaultItemFx> findAllByUserId(long userId) throws SQLException;

    VaultItemFx insert(long userId, VaultItemFx item, StoredImageRecord imageRecord) throws SQLException;

    boolean update(long userId, VaultItemFx item, StoredImageRecord imageRecord) throws SQLException;

    boolean deleteById(long userId, long itemId) throws SQLException;

    boolean restoreById(long userId, long itemId) throws SQLException;

    Optional<StoredImageRecord> findStoredImageByItemId(long userId, long itemId) throws SQLException;
}
