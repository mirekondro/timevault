package com.example.desktop.model;

import java.util.Arrays;

/**
 * Desktop model for one image inside an image gallery item.
 */
public final class GalleryImageFx {

    private long id;
    private String fileName = "";
    private String aiContext = "";
    private String mimeType = "application/octet-stream";
    private long byteCount;
    private int displayOrder;
    private byte[] cachedImageBytes = new byte[0];

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = Math.max(id, 0L);
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName == null ? "" : fileName.trim();
    }

    public String getAiContext() {
        return aiContext;
    }

    public void setAiContext(String aiContext) {
        this.aiContext = aiContext == null ? "" : aiContext.trim();
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType;
    }

    public long getByteCount() {
        return byteCount;
    }

    public void setByteCount(long byteCount) {
        this.byteCount = Math.max(byteCount, 0L);
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = Math.max(displayOrder, 0);
    }

    public byte[] getCachedImageBytes() {
        return Arrays.copyOf(cachedImageBytes, cachedImageBytes.length);
    }

    public void setCachedImageBytes(byte[] cachedImageBytes) {
        this.cachedImageBytes = cachedImageBytes == null ? new byte[0] : Arrays.copyOf(cachedImageBytes, cachedImageBytes.length);
        if (byteCount == 0L) {
            byteCount = this.cachedImageBytes.length;
        }
    }

    public boolean hasCachedBytes() {
        return cachedImageBytes.length > 0;
    }

    public void clearCachedImageBytes() {
        cachedImageBytes = new byte[0];
    }

    public GalleryImageFx copy() {
        GalleryImageFx copy = new GalleryImageFx();
        copy.setId(id);
        copy.setFileName(fileName);
        copy.setAiContext(aiContext);
        copy.setMimeType(mimeType);
        copy.setByteCount(byteCount);
        copy.setDisplayOrder(displayOrder);
        copy.setCachedImageBytes(cachedImageBytes);
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GalleryImageFx that)) {
            return false;
        }
        return id == that.id
                && byteCount == that.byteCount
                && displayOrder == that.displayOrder
                && java.util.Objects.equals(fileName, that.fileName)
                && java.util.Objects.equals(aiContext, that.aiContext)
                && java.util.Objects.equals(mimeType, that.mimeType)
                && Arrays.equals(cachedImageBytes, that.cachedImageBytes);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(id, fileName, aiContext, mimeType, byteCount, displayOrder);
        result = 31 * result + Arrays.hashCode(cachedImageBytes);
        return result;
    }
}
