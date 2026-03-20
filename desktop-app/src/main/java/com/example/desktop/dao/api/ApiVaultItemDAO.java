package com.example.desktop.dao.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.example.desktop.dao.VaultItemDAO;
import com.example.desktop.model.StoredImageRecord;
import com.example.desktop.model.UnlockedItemSession;
import com.example.desktop.model.VaultItemFx;
import com.example.shared.api.ApiMessageResponse;
import com.example.shared.api.ApiStoredImageDto;
import com.example.shared.api.ApiVaultItemDto;
import com.example.shared.api.ApiVaultItemMutationRequest;

import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * API-backed desktop vault item DAO.
 */
public class ApiVaultItemDAO implements VaultItemDAO {

    private static final TypeReference<List<ApiVaultItemDto>> ITEM_LIST_TYPE = new TypeReference<>() {
    };

    private final TimeVaultApiClient apiClient;

    public ApiVaultItemDAO(TimeVaultApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<VaultItemFx> findAllByUserId(long userId) throws SQLException {
        List<ApiVaultItemDto> items = apiClient.get("/api/client/vault/items", ITEM_LIST_TYPE, true);
        return items.stream()
                .map(this::toFxItem)
                .toList();
    }

    @Override
    public VaultItemFx insert(long userId, VaultItemFx item, StoredImageRecord imageRecord) throws SQLException {
        ApiVaultItemDto savedItem = apiClient.post(
                "/api/client/vault/items",
                toMutationRequest(item, imageRecord),
                ApiVaultItemDto.class,
                true);
        VaultItemFx mappedItem = toFxItem(savedItem);
        preserveTransientState(item, mappedItem, imageRecord);
        return mappedItem;
    }

    @Override
    public boolean update(long userId, VaultItemFx item, StoredImageRecord imageRecord) throws SQLException {
        try {
            ApiVaultItemDto updatedItem = apiClient.put(
                    "/api/client/vault/items/" + item.getId(),
                    toMutationRequest(item, imageRecord),
                    ApiVaultItemDto.class,
                    true);
            syncItem(item, updatedItem);
            preserveTransientState(item, item, imageRecord);
            return true;
        } catch (ApiNotFoundException exception) {
            return false;
        }
    }

    @Override
    public boolean deleteById(long userId, long itemId) throws SQLException {
        try {
            apiClient.delete("/api/client/vault/items/" + itemId, ApiMessageResponse.class, true);
            return true;
        } catch (ApiNotFoundException exception) {
            return false;
        }
    }

    @Override
    public boolean restoreById(long userId, long itemId) throws SQLException {
        try {
            apiClient.post("/api/client/vault/items/" + itemId + "/restore", null, ApiMessageResponse.class, true);
            return true;
        } catch (ApiNotFoundException exception) {
            return false;
        }
    }

    @Override
    public Optional<StoredImageRecord> findStoredImageByItemId(long userId, long itemId) throws SQLException {
        try {
            ApiStoredImageDto storedImage = apiClient.get(
                    "/api/client/vault/items/" + itemId + "/image",
                    ApiStoredImageDto.class,
                    true);
            return Optional.of(toStoredImageRecord(storedImage));
        } catch (ApiNotFoundException exception) {
            return Optional.empty();
        }
    }

    private ApiVaultItemMutationRequest toMutationRequest(VaultItemFx item, StoredImageRecord imageRecord) {
        return new ApiVaultItemMutationRequest(
                item.getTitle(),
                item.getContent(),
                item.getAiContext(),
                item.getItemType(),
                item.getTags(),
                item.getSourceUrl(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getDeletedAt(),
                item.isLocked(),
                item.getLockPasswordHash(),
                item.getLockSalt(),
                item.getLockPayload(),
                toStoredImageDto(imageRecord));
    }

    private ApiStoredImageDto toStoredImageDto(StoredImageRecord imageRecord) {
        if (imageRecord == null) {
            return null;
        }
        return new ApiStoredImageDto(
                imageRecord.mimeType(),
                imageRecord.byteCount(),
                encodeBase64(imageRecord.imageData()),
                encodeBase64(imageRecord.protectedImageData()));
    }

    private StoredImageRecord toStoredImageRecord(ApiStoredImageDto storedImage) {
        return new StoredImageRecord(
                storedImage.mimeType(),
                storedImage.byteCount(),
                decodeBase64(storedImage.imageDataBase64()),
                decodeBase64(storedImage.protectedImageDataBase64()));
    }

    private VaultItemFx toFxItem(ApiVaultItemDto item) {
        VaultItemFx fxItem = new VaultItemFx();
        syncItem(fxItem, item);
        return fxItem;
    }

    private void syncItem(VaultItemFx target, ApiVaultItemDto source) {
        target.setId(source.id() == null ? 0L : source.id());
        target.setOwnerId(source.ownerId() == null ? 0L : source.ownerId());
        target.setTitle(source.title());
        target.setContent(source.content());
        target.setAiContext(source.aiContext());
        target.setItemType(source.itemType());
        target.setTags(source.tags());
        target.setSourceUrl(source.sourceUrl());
        target.setCreatedAt(source.createdAt());
        target.setUpdatedAt(source.updatedAt());
        target.setDeletedAt(source.deletedAt());
        target.setLocked(source.locked());
        target.setLockPasswordHash(source.lockPasswordHash());
        target.setLockSalt(source.lockSalt());
        target.setLockPayload(source.lockPayload());
        target.setImageMimeType(source.imageMimeType());
        target.setImageByteCount(source.imageByteCount());
    }

    private void preserveTransientState(VaultItemFx source, VaultItemFx target, StoredImageRecord imageRecord) {
        if (source == null || target == null) {
            return;
        }

        if (target.isLocked()) {
            target.clearUnlockedSession();
            target.clearCachedImageBytes();
            return;
        }

        UnlockedItemSession unlockedSession = source.getUnlockedSession();
        if (unlockedSession != null) {
            target.setUnlockedSession(unlockedSession.copy());
        } else {
            target.clearUnlockedSession();
        }

        byte[] cachedBytes = source.getCachedImageBytes();
        if (cachedBytes.length > 0) {
            target.setCachedImageBytes(cachedBytes);
            return;
        }

        if (imageRecord != null && imageRecord.imageData().length > 0) {
            target.setCachedImageBytes(imageRecord.imageData());
            return;
        }

        target.clearCachedImageBytes();
    }

    private String encodeBase64(byte[] value) {
        if (value == null || value.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(value);
    }

    private byte[] decodeBase64(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        return Base64.getDecoder().decode(value);
    }
}
