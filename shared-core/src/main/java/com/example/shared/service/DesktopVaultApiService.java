package com.example.shared.service;

import com.example.shared.api.ApiStoredImageDto;
import com.example.shared.api.ApiVaultItemDto;
import com.example.shared.api.ApiVaultItemImageDto;
import com.example.shared.api.ApiVaultItemMutationRequest;
import com.example.shared.model.VaultItem;
import com.example.shared.model.VaultItemImage;
import com.example.shared.model.VaultUser;
import com.example.shared.repository.VaultItemRepository;
import com.example.shared.repository.VaultUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * User-scoped backend service that mirrors the current desktop DAO surface.
 */
@Service
@Transactional
public class DesktopVaultApiService {

    private final VaultItemRepository vaultItemRepository;
    private final VaultUserRepository vaultUserRepository;

    @Autowired
    public DesktopVaultApiService(VaultItemRepository vaultItemRepository, VaultUserRepository vaultUserRepository) {
        this.vaultItemRepository = vaultItemRepository;
        this.vaultUserRepository = vaultUserRepository;
    }

    @Transactional(readOnly = true)
    public List<ApiVaultItemDto> findAllItems(long userId) {
        requireUser(userId);
        return vaultItemRepository.findAllByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toItemDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ApiVaultItemDto> findItem(long userId, long itemId) {
        return vaultItemRepository.findByIdAndOwnerId(itemId, userId).map(this::toItemDto);
    }

    public ApiVaultItemDto createItem(long userId, ApiVaultItemMutationRequest request) {
        VaultItem item = new VaultItem();
        item.setOwner(requireUser(userId));
        item.setUserId(userId);
        applyMutation(item, request, true);
        return toItemDto(vaultItemRepository.save(item));
    }

    public Optional<ApiVaultItemDto> updateItem(long userId, long itemId, ApiVaultItemMutationRequest request) {
        return vaultItemRepository.findByIdAndOwnerId(itemId, userId)
                .map(item -> {
                    applyMutation(item, request, false);
                    return toItemDto(vaultItemRepository.save(item));
                });
    }

    public boolean deleteItem(long userId, long itemId) {
        return vaultItemRepository.softDeleteByIdAndOwnerId(itemId, userId) > 0;
    }

    public boolean restoreItem(long userId, long itemId) {
        return vaultItemRepository.restoreByIdAndOwnerId(itemId, userId) > 0;
    }

    @Transactional(readOnly = true)
    public List<ApiStoredImageDto> findStoredImages(long userId, long itemId) {
        return vaultItemRepository.findByIdAndOwnerId(itemId, userId)
                .map(VaultItem::getImages)
                .orElseGet(List::of)
                .stream()
                .sorted(imageComparator())
                .map(this::toStoredImageDto)
                .toList();
    }

    private VaultUser requireUser(long userId) {
        return vaultUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
    }

    private void applyMutation(VaultItem item, ApiVaultItemMutationRequest request, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("Item payload is required.");
        }

        String title = trimToNull(request.title());
        if (title == null) {
            throw new IllegalArgumentException("Item title is required.");
        }

        LocalDateTime createdAt = request.createdAt() != null
                ? request.createdAt()
                : (creating ? LocalDateTime.now() : item.getCreatedAt());
        LocalDateTime updatedAt = request.updatedAt() != null
                ? request.updatedAt()
                : (creating ? createdAt : LocalDateTime.now());

        item.setTitle(title);
        item.setContent(trimToNull(request.content()));
        item.setAiContext(trimToNull(request.aiContext()));
        item.setItemType(normalizeItemType(request.itemType()));
        item.setTags(trimToNull(request.tags()));
        item.setSourceUrl(trimToNull(request.sourceUrl()));
        item.setCreatedAt(createdAt);
        item.setUpdatedAt(updatedAt);
        item.setDeletedAt(request.deletedAt());
        item.setLocked(request.locked());
        item.setLockPasswordHash(emptyIfNull(request.lockPasswordHash()));
        item.setLockSalt(emptyIfNull(request.lockSalt()));
        item.setLockPayload(emptyIfNull(request.lockPayload()));

        applyStoredImages(item, request.storedImages(), createdAt, updatedAt);
    }

    private void applyStoredImages(VaultItem item,
                                   List<ApiStoredImageDto> storedImages,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
        item.clearImages();
        if (storedImages == null || storedImages.isEmpty()) {
            return;
        }

        storedImages.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt(ApiStoredImageDto::displayOrder)
                        .thenComparing(image -> image.id() == null ? Long.MAX_VALUE : image.id()))
                .forEach(storedImage -> {
                    byte[] imageData = decodeBase64(storedImage.imageDataBase64());
                    byte[] protectedImageData = decodeBase64(storedImage.protectedImageDataBase64());
                    long resolvedByteCount = Math.max(storedImage.byteCount(), Math.max(imageData.length, protectedImageData.length));
                    if (resolvedByteCount <= 0L) {
                        return;
                    }

                    VaultItemImage image = new VaultItemImage();
                    if (storedImage.id() != null && storedImage.id() > 0L) {
                        image.setId(storedImage.id());
                    }
                    image.setItem(item);
                    image.setFileName(emptyIfNull(storedImage.fileName()));
                    image.setAiContext(trimToNull(storedImage.aiContext()));
                    image.setMimeType(emptyIfNull(storedImage.mimeType()));
                    image.setByteCount(resolvedByteCount);
                    image.setDisplayOrder(storedImage.displayOrder());
                    image.setProtectedMetadata(emptyIfNull(storedImage.protectedMetadata()));
                    image.setImageData(imageData);
                    image.setProtectedImageData(protectedImageData);
                    image.setCreatedAt(createdAt);
                    image.setUpdatedAt(updatedAt);
                    item.addImage(image);
                });
    }

    private ApiVaultItemDto toItemDto(VaultItem item) {
        return new ApiVaultItemDto(
                item.getId(),
                item.getUserId(),
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
                emptyIfNull(item.getLockPasswordHash()),
                emptyIfNull(item.getLockSalt()),
                emptyIfNull(item.getLockPayload()),
                item.getImages().stream()
                        .sorted(imageComparator())
                        .map(this::toItemImageDto)
                        .toList());
    }

    private ApiVaultItemImageDto toItemImageDto(VaultItemImage image) {
        return new ApiVaultItemImageDto(
                image.getId(),
                emptyIfNull(image.getFileName()),
                emptyIfNull(image.getAiContext()),
                emptyIfNull(image.getMimeType()),
                image.getByteCount(),
                image.getDisplayOrder());
    }

    private ApiStoredImageDto toStoredImageDto(VaultItemImage image) {
        return new ApiStoredImageDto(
                image.getId(),
                emptyIfNull(image.getFileName()),
                emptyIfNull(image.getAiContext()),
                emptyIfNull(image.getMimeType()),
                image.getByteCount(),
                image.getDisplayOrder(),
                emptyIfNull(image.getProtectedMetadata()),
                encodeBase64(image.getImageData()),
                encodeBase64(image.getProtectedImageData()));
    }

    private Comparator<VaultItemImage> imageComparator() {
        return Comparator.comparingInt(VaultItemImage::getDisplayOrder)
                .thenComparing(image -> image.getId() == null ? Long.MAX_VALUE : image.getId());
    }

    private String normalizeItemType(String itemType) {
        String normalized = trimToNull(itemType);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private byte[] decodeBase64(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Stored image payload is not valid Base64.");
        }
    }

    private String encodeBase64(byte[] value) {
        if (value == null || value.length == 0) {
            return "";
        }
        return Base64.getEncoder().encodeToString(value);
    }
}
