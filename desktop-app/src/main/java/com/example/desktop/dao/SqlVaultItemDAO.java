package com.example.desktop.dao;

import com.example.desktop.model.GalleryImageFx;
import com.example.desktop.model.StoredImageRecord;
import com.example.desktop.model.VaultItemFx;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL Server implementation of the desktop DAO.
 */
public class SqlVaultItemDAO implements VaultItemDAO {

    private final ConnectionManager connectionManager;

    public SqlVaultItemDAO(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public List<VaultItemFx> findAllByUserId(long userId) throws SQLException {
        String sql = """
                SELECT i.id, i.user_id, i.title, i.content, i.ai_context, i.item_type, i.tags, i.source_url,
                       i.is_locked, i.lock_password_hash, i.lock_salt, i.lock_payload,
                       i.created_at, i.updated_at, i.deleted_at,
                       img.id AS image_id,
                       img.file_name AS image_file_name,
                       img.ai_context AS image_ai_context,
                       img.mime_type AS image_mime_type,
                       img.byte_count AS image_byte_count,
                       img.display_order AS image_display_order
                FROM dbo.vault_items i
                LEFT JOIN dbo.vault_item_images img ON img.item_id = i.id
                WHERE i.user_id = ?
                ORDER BY i.created_at DESC, i.id DESC, img.display_order ASC, img.id ASC
                """;

        Map<Long, VaultItemFx> itemsById = new LinkedHashMap<>();
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long itemId = resultSet.getLong("id");
                    VaultItemFx item = itemsById.computeIfAbsent(itemId, ignored -> createItem(resultSet));
                    GalleryImageFx galleryImage = mapGalleryImageMetadata(resultSet);
                    if (galleryImage != null) {
                        List<GalleryImageFx> galleryImages = item.getGalleryImages();
                        galleryImages.add(galleryImage);
                        item.setGalleryImages(galleryImages);
                    }
                }
            }
        }
        return new ArrayList<>(itemsById.values());
    }

    @Override
    public VaultItemFx insert(long userId, VaultItemFx item, List<StoredImageRecord> imageRecords) throws SQLException {
        String sql = """
                INSERT INTO dbo.vault_items (
                    user_id, title, content, ai_context, item_type, tags, source_url,
                    is_locked, lock_password_hash, lock_salt, lock_payload, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        LocalDateTime createdAt = item.getCreatedAt() == null ? LocalDateTime.now() : item.getCreatedAt();
        LocalDateTime updatedAt = item.getUpdatedAt() == null ? createdAt : item.getUpdatedAt();

        try (Connection connection = connectionManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, userId);
                    statement.setString(2, item.getTitle());
                    statement.setString(3, item.getContent());
                    statement.setString(4, item.getAiContext());
                    statement.setString(5, item.getItemType());
                    statement.setString(6, item.getTags());
                    statement.setString(7, item.getSourceUrl());
                    statement.setBoolean(8, item.isLocked());
                    statement.setString(9, item.getLockPasswordHash());
                    statement.setString(10, item.getLockSalt());
                    statement.setString(11, item.getLockPayload());
                    statement.setTimestamp(12, Timestamp.valueOf(createdAt));
                    statement.setTimestamp(13, Timestamp.valueOf(updatedAt));
                    statement.executeUpdate();

                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            item.setId(generatedKeys.getLong(1));
                        }
                    }
                }

                List<StoredImageRecord> persistedImages = syncImageRecords(connection, item.getId(), imageRecords, createdAt, updatedAt);
                item.setGalleryImages(mapGalleryImages(persistedImages, true));
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }

        item.setOwnerId(userId);
        item.setCreatedAt(createdAt);
        item.setUpdatedAt(updatedAt);
        return item;
    }

    @Override
    public boolean update(long userId, VaultItemFx item, List<StoredImageRecord> imageRecords) throws SQLException {
        String sql = """
                UPDATE dbo.vault_items
                SET title = ?, content = ?, ai_context = ?, item_type = ?, tags = ?, source_url = ?,
                    is_locked = ?, lock_password_hash = ?, lock_salt = ?, lock_payload = ?, updated_at = ?
                WHERE id = ? AND user_id = ?
                """;

        LocalDateTime updatedAt = item.getUpdatedAt() == null ? LocalDateTime.now() : item.getUpdatedAt();

        try (Connection connection = connectionManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean updated;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, item.getTitle());
                    statement.setString(2, item.getContent());
                    statement.setString(3, item.getAiContext());
                    statement.setString(4, item.getItemType());
                    statement.setString(5, item.getTags());
                    statement.setString(6, item.getSourceUrl());
                    statement.setBoolean(7, item.isLocked());
                    statement.setString(8, item.getLockPasswordHash());
                    statement.setString(9, item.getLockSalt());
                    statement.setString(10, item.getLockPayload());
                    statement.setTimestamp(11, Timestamp.valueOf(updatedAt));
                    statement.setLong(12, item.getId());
                    statement.setLong(13, userId);
                    updated = statement.executeUpdate() > 0;
                }

                if (updated) {
                    List<StoredImageRecord> persistedImages = syncImageRecords(connection, item.getId(), imageRecords, item.getCreatedAt(), updatedAt);
                    connection.commit();
                    item.setOwnerId(userId);
                    item.setUpdatedAt(updatedAt);
                    item.setGalleryImages(mapGalleryImages(persistedImages, true));
                } else {
                    connection.rollback();
                }
                return updated;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    @Override
    public boolean deleteById(long userId, long itemId) throws SQLException {
        String sql = """
                UPDATE dbo.vault_items
                SET deleted_at = SYSDATETIME(), updated_at = SYSDATETIME()
                WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """;
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            statement.setLong(2, userId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public boolean restoreById(long userId, long itemId) throws SQLException {
        String sql = """
                UPDATE dbo.vault_items
                SET deleted_at = NULL, updated_at = SYSDATETIME()
                WHERE id = ? AND user_id = ? AND deleted_at IS NOT NULL
                """;
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            statement.setLong(2, userId);
            return statement.executeUpdate() > 0;
        }
    }

    @Override
    public List<StoredImageRecord> findStoredImagesByItemId(long userId, long itemId) throws SQLException {
        String sql = """
                SELECT img.id, img.file_name, img.ai_context, img.mime_type, img.byte_count,
                       img.display_order, img.protected_metadata, img.image_data, img.protected_image_data
                FROM dbo.vault_item_images img
                INNER JOIN dbo.vault_items i ON i.id = img.item_id
                WHERE i.user_id = ? AND img.item_id = ?
                ORDER BY img.display_order ASC, img.id ASC
                """;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, itemId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<StoredImageRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(mapStoredImageRecord(resultSet, true));
                }
                return records;
            }
        }
    }

    private List<StoredImageRecord> syncImageRecords(Connection connection,
                                                     long itemId,
                                                     List<StoredImageRecord> imageRecords,
                                                     LocalDateTime createdAt,
                                                     LocalDateTime updatedAt) throws SQLException {
        List<StoredImageRecord> normalizedRecords = normalizeImageRecords(imageRecords);
        deleteMissingImageRecords(connection, itemId, normalizedRecords);

        for (StoredImageRecord record : normalizedRecords) {
            if (record.id() != null && record.id() > 0L && updateImageRecord(connection, itemId, record, updatedAt)) {
                continue;
            }
            insertImageRecord(connection, itemId, record, createdAt == null ? updatedAt : createdAt, updatedAt);
        }

        return loadImageRecords(connection, itemId, true);
    }

    private boolean updateImageRecord(Connection connection,
                                      long itemId,
                                      StoredImageRecord imageRecord,
                                      LocalDateTime updatedAt) throws SQLException {
        String sql = """
                UPDATE dbo.vault_item_images
                SET file_name = ?, ai_context = ?, mime_type = ?, byte_count = ?, display_order = ?,
                    protected_metadata = ?, image_data = ?, protected_image_data = ?, updated_at = ?
                WHERE item_id = ? AND id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, emptyToNull(imageRecord.fileName()));
            statement.setString(2, emptyToNull(imageRecord.aiContext()));
            statement.setString(3, imageRecord.mimeType());
            statement.setLong(4, imageRecord.byteCount());
            statement.setInt(5, imageRecord.displayOrder());
            statement.setString(6, emptyToNull(imageRecord.protectedMetadata()));
            setNullableBytes(statement, 7, imageRecord.imageData());
            setNullableBytes(statement, 8, imageRecord.protectedImageData());
            statement.setTimestamp(9, Timestamp.valueOf(updatedAt));
            statement.setLong(10, itemId);
            statement.setLong(11, imageRecord.id());
            return statement.executeUpdate() > 0;
        }
    }

    private void insertImageRecord(Connection connection,
                                   long itemId,
                                   StoredImageRecord imageRecord,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) throws SQLException {
        String sql = """
                INSERT INTO dbo.vault_item_images (
                    item_id, file_name, ai_context, mime_type, byte_count, display_order,
                    protected_metadata, image_data, protected_image_data, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            statement.setString(2, emptyToNull(imageRecord.fileName()));
            statement.setString(3, emptyToNull(imageRecord.aiContext()));
            statement.setString(4, imageRecord.mimeType());
            statement.setLong(5, imageRecord.byteCount());
            statement.setInt(6, imageRecord.displayOrder());
            statement.setString(7, emptyToNull(imageRecord.protectedMetadata()));
            setNullableBytes(statement, 8, imageRecord.imageData());
            setNullableBytes(statement, 9, imageRecord.protectedImageData());
            statement.setTimestamp(10, Timestamp.valueOf(createdAt));
            statement.setTimestamp(11, Timestamp.valueOf(updatedAt));
            statement.executeUpdate();
        }
    }

    private void deleteMissingImageRecords(Connection connection,
                                           long itemId,
                                           List<StoredImageRecord> imageRecords) throws SQLException {
        List<Long> keptIds = imageRecords.stream()
                .map(StoredImageRecord::id)
                .filter(id -> id != null && id > 0L)
                .toList();

        StringBuilder sql = new StringBuilder("DELETE FROM dbo.vault_item_images WHERE item_id = ?");
        if (!keptIds.isEmpty()) {
            sql.append(" AND id NOT IN (");
            for (int index = 0; index < keptIds.size(); index++) {
                if (index > 0) {
                    sql.append(", ");
                }
                sql.append('?');
            }
            sql.append(')');
        }

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setLong(1, itemId);
            for (int index = 0; index < keptIds.size(); index++) {
                statement.setLong(index + 2, keptIds.get(index));
            }
            statement.executeUpdate();
        }
    }

    private List<StoredImageRecord> loadImageRecords(Connection connection, long itemId, boolean includeBinary) throws SQLException {
        String sql = includeBinary
                ? """
                SELECT id, file_name, ai_context, mime_type, byte_count, display_order, protected_metadata, image_data, protected_image_data
                FROM dbo.vault_item_images
                WHERE item_id = ?
                ORDER BY display_order ASC, id ASC
                """
                : """
                SELECT id, file_name, ai_context, mime_type, byte_count, display_order, protected_metadata
                FROM dbo.vault_item_images
                WHERE item_id = ?
                ORDER BY display_order ASC, id ASC
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, itemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<StoredImageRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(mapStoredImageRecord(resultSet, includeBinary));
                }
                return records;
            }
        }
    }

    private void setNullableBytes(PreparedStatement statement, int parameterIndex, byte[] value) throws SQLException {
        if (value == null || value.length == 0) {
            statement.setNull(parameterIndex, Types.VARBINARY);
            return;
        }
        statement.setBytes(parameterIndex, value);
    }

    private List<StoredImageRecord> normalizeImageRecords(List<StoredImageRecord> imageRecords) {
        if (imageRecords == null || imageRecords.isEmpty()) {
            return List.of();
        }
        List<StoredImageRecord> normalizedRecords = new ArrayList<>();
        int index = 0;
        for (StoredImageRecord imageRecord : imageRecords) {
            if (imageRecord == null || imageRecord.byteCount() <= 0L) {
                continue;
            }
            normalizedRecords.add(new StoredImageRecord(
                    imageRecord.id(),
                    imageRecord.fileName(),
                    imageRecord.aiContext(),
                    imageRecord.mimeType(),
                    imageRecord.byteCount(),
                    index++,
                    imageRecord.protectedMetadata(),
                    imageRecord.imageData(),
                    imageRecord.protectedImageData()));
        }
        return normalizedRecords;
    }

    private List<GalleryImageFx> mapGalleryImages(List<StoredImageRecord> imageRecords, boolean includeBytes) {
        if (imageRecords == null || imageRecords.isEmpty()) {
            return List.of();
        }
        List<GalleryImageFx> galleryImages = new ArrayList<>(imageRecords.size());
        for (StoredImageRecord imageRecord : imageRecords) {
            GalleryImageFx image = new GalleryImageFx();
            image.setId(imageRecord.id() == null ? 0L : imageRecord.id());
            image.setFileName(imageRecord.fileName());
            image.setAiContext(imageRecord.aiContext());
            image.setMimeType(imageRecord.mimeType());
            image.setByteCount(imageRecord.byteCount());
            image.setDisplayOrder(imageRecord.displayOrder());
            if (includeBytes && imageRecord.imageData().length > 0) {
                image.setCachedImageBytes(imageRecord.imageData());
            }
            galleryImages.add(image);
        }
        return galleryImages;
    }

    private VaultItemFx createItem(ResultSet resultSet) {
        VaultItemFx item = new VaultItemFx();
        try {
            item.setId(resultSet.getLong("id"));
            item.setOwnerId(resultSet.getLong("user_id"));
            item.setTitle(resultSet.getString("title"));
            item.setContent(resultSet.getString("content"));
            item.setAiContext(resultSet.getString("ai_context"));
            item.setItemType(resultSet.getString("item_type"));
            item.setTags(resultSet.getString("tags"));
            item.setSourceUrl(resultSet.getString("source_url"));
            item.setLocked(resultSet.getBoolean("is_locked"));
            item.setLockPasswordHash(resultSet.getString("lock_password_hash"));
            item.setLockSalt(resultSet.getString("lock_salt"));
            item.setLockPayload(resultSet.getString("lock_payload"));

            Timestamp createdAt = resultSet.getTimestamp("created_at");
            Timestamp updatedAt = resultSet.getTimestamp("updated_at");
            Timestamp deletedAt = resultSet.getTimestamp("deleted_at");
            item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
            item.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
            item.setDeletedAt(deletedAt == null ? null : deletedAt.toLocalDateTime());
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not map vault item row.", exception);
        }
        return item;
    }

    private GalleryImageFx mapGalleryImageMetadata(ResultSet resultSet) throws SQLException {
        long imageId = resultSet.getLong("image_id");
        if (resultSet.wasNull()) {
            return null;
        }

        GalleryImageFx image = new GalleryImageFx();
        image.setId(imageId);
        image.setFileName(resultSet.getString("image_file_name"));
        image.setAiContext(resultSet.getString("image_ai_context"));
        image.setMimeType(resultSet.getString("image_mime_type"));
        image.setByteCount(resultSet.getLong("image_byte_count"));
        image.setDisplayOrder(resultSet.getInt("image_display_order"));
        return image;
    }

    private StoredImageRecord mapStoredImageRecord(ResultSet resultSet, boolean includeBinary) throws SQLException {
        return new StoredImageRecord(
                resultSet.getLong("id"),
                resultSet.getString("file_name"),
                resultSet.getString("ai_context"),
                resultSet.getString("mime_type"),
                resultSet.getLong("byte_count"),
                resultSet.getInt("display_order"),
                resultSet.getString("protected_metadata"),
                includeBinary ? resultSet.getBytes("image_data") : new byte[0],
                includeBinary ? resultSet.getBytes("protected_image_data") : new byte[0]);
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
