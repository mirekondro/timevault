package com.example.desktop.dao.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.example.desktop.dao.VaultItemDAO;
import com.example.desktop.model.GalleryImageFx;
import com.example.desktop.model.StoredImageRecord;
import com.example.desktop.model.UnlockedItemSession;
import com.example.desktop.model.VaultItemFx;
import com.example.shared.api.ApiMessageResponse;
import com.example.shared.api.ApiStoredImageDto;
import com.example.shared.api.ApiVaultItemDto;
import com.example.shared.api.ApiVaultItemImageDto;
import com.example.shared.api.ApiVaultItemMutationRequest;

import java.sql.SQLException;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

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
    public VaultItemFx insert(long userId, VaultItemFx item, List<StoredImageRecord> imageRecords) throws SQLException {
        ApiVaultItemDto savedItem = apiClient.post(
                "/api/client/vault/items",
                toMutationRequest(item, imageRecords),
                ApiVaultItemDto.class,
                true);
        VaultItemFx mappedItem = toFxItem(savedItem);
        preserveTransientState(item, mappedItem, imageRecords);
        return mappedItem;
    }

    @Override
    public boolean update(long userId, VaultItemFx item, List<StoredImageRecord> imageRecords) throws SQLException {
        try {
            ApiVaultItemDto updatedItem = apiClient.put(
                    "/api/client/vault/items/" + item.getId(),
                    toMutationRequest(item, imageRecords),
                    ApiVaultItemDto.class,
                    true);
            syncItem(item, updatedItem);
            preserveTransientState(item, item, imageRecords);
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
    public List<StoredImageRecord> findStoredImagesByItemId(long userId, long itemId) throws SQLException {
        try {
            List<ApiStoredImageDto> storedImages = apiClient.get(
                    "/api/client/vault/items/" + itemId + "/images",
                    new TypeReference<List<ApiStoredImageDto>>() {},
                    true);
            return storedImages.stream()
                    .map(this::toStoredImageRecord)
                    .sorted(Comparator.comparingInt(StoredImageRecord::displayOrder)
                            .thenComparing(record -> record.id() == null ? Long.MAX_VALUE : record.id()))
                    .toList();
        } catch (ApiNotFoundException exception) {
            return List.of();
        }
    }

    private ApiVaultItemMutationRequest toMutationRequest(VaultItemFx item, List<StoredImageRecord> imageRecords) {
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
                imageRecords == null ? List.of() : imageRecords.stream()
                        .map(this::toStoredImageDto)
                        .toList());
    }

    private ApiStoredImageDto toStoredImageDto(StoredImageRecord imageRecord) {
        if (imageRecord == null) {
            return null;
        }
        return new ApiStoredImageDto(
                imageRecord.id(),
                imageRecord.fileName(),
                imageRecord.aiContext(),
                imageRecord.mimeType(),
                imageRecord.byteCount(),
                imageRecord.displayOrder(),
                imageRecord.protectedMetadata(),
                encodeBase64(imageRecord.imageData()),
                encodeBase64(imageRecord.protectedImageData()));
    }

    private StoredImageRecord toStoredImageRecord(ApiStoredImageDto storedImage) {
        return new StoredImageRecord(
                storedImage.id(),
                storedImage.fileName(),
                storedImage.aiContext(),
                storedImage.mimeType(),
                storedImage.byteCount(),
                storedImage.displayOrder(),
                storedImage.protectedMetadata(),
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
        target.setGalleryImages(mapGalleryImages(source.images()));
    }

    private void preserveTransientState(VaultItemFx source, VaultItemFx target, List<StoredImageRecord> imageRecords) {
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

        target.setGalleryImages(mergeGalleryImages(target.getGalleryImages(), source.getGalleryImages(), imageRecords));
    }

    private List<GalleryImageFx> mapGalleryImages(List<ApiVaultItemImageDto> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .filter(java.util.Objects::nonNull)
                .map(source -> {
                    GalleryImageFx image = new GalleryImageFx();
                    image.setId(source.id() == null ? 0L : source.id());
                    image.setFileName(source.fileName());
                    image.setAiContext(source.aiContext());
                    image.setMimeType(source.mimeType());
                    image.setByteCount(source.byteCount());
                    image.setDisplayOrder(source.displayOrder());
                    return image;
                })
                .sorted(Comparator.comparingInt(GalleryImageFx::getDisplayOrder)
                        .thenComparingLong(GalleryImageFx::getId))
                .toList();
    }

    private List<GalleryImageFx> mergeGalleryImages(List<GalleryImageFx> targetImages,
                                                    List<GalleryImageFx> sourceImages,
                                                    List<StoredImageRecord> imageRecords) {
        if (targetImages == null || targetImages.isEmpty()) {
            return List.of();
        }

        List<StoredImageRecord> safeImageRecords = imageRecords == null ? List.of() : imageRecords;
        return targetImages.stream()
                .map(targetImage -> {
                    GalleryImageFx mergedImage = targetImage.copy();
                    findMatchingSourceImage(targetImage, sourceImages).ifPresent(sourceImage ->
                            mergedImage.setCachedImageBytes(sourceImage.getCachedImageBytes()));
                    if (!mergedImage.hasCachedBytes()) {
                        findMatchingRecord(targetImage, safeImageRecords).ifPresent(record ->
                                mergedImage.setCachedImageBytes(record.imageData()));
                    }
                    return mergedImage;
                })
                .toList();
    }

    private java.util.Optional<GalleryImageFx> findMatchingSourceImage(GalleryImageFx targetImage, List<GalleryImageFx> sourceImages) {
        if (targetImage == null || sourceImages == null || sourceImages.isEmpty()) {
            return java.util.Optional.empty();
        }
        return sourceImages.stream()
                .filter(sourceImage -> matchesImage(targetImage, sourceImage.getId(), sourceImage.getDisplayOrder(), sourceImage.getFileName()))
                .findFirst();
    }

    private java.util.Optional<StoredImageRecord> findMatchingRecord(GalleryImageFx targetImage, List<StoredImageRecord> imageRecords) {
        if (targetImage == null || imageRecords == null || imageRecords.isEmpty()) {
            return java.util.Optional.empty();
        }
        return imageRecords.stream()
                .filter(record -> matchesImage(targetImage, record.id() == null ? 0L : record.id(), record.displayOrder(), record.fileName()))
                .findFirst();
    }

    private boolean matchesImage(GalleryImageFx targetImage, long candidateId, int candidateDisplayOrder, String candidateFileName) {
        if (targetImage.getId() > 0L && candidateId > 0L) {
            return targetImage.getId() == candidateId;
        }
        return targetImage.getDisplayOrder() == candidateDisplayOrder
                && java.util.Objects.equals(targetImage.getFileName(), candidateFileName);
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
