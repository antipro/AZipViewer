package com.bitifyware.zipviewer;

import android.graphics.Bitmap;

/**
 * Represents an image entry from a zip file with both full and thumbnail versions
 */
public class ImageEntry {
    private String fileName;
    private Bitmap fullBitmap;
    private Bitmap thumbnail;
    private boolean thumbnailLoading;
    private long fileSize;

    public ImageEntry(String fileName) {
        this.fileName = fileName;
        this.thumbnailLoading = false;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Bitmap getFullBitmap() {
        return fullBitmap;
    }

    public void setFullBitmap(Bitmap fullBitmap) {
        this.fullBitmap = fullBitmap;
    }

    public Bitmap getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Bitmap thumbnail) {
        this.thumbnail = thumbnail;
    }

    public boolean isThumbnailLoading() {
        return thumbnailLoading;
    }

    public void setThumbnailLoading(boolean thumbnailLoading) {
        this.thumbnailLoading = thumbnailLoading;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public boolean hasThumbnail() {
        return thumbnail != null;
    }

    public boolean hasFullBitmap() {
        return fullBitmap != null;
    }

    /**
     * Clean up bitmaps to prevent memory leaks
     * Note: While bitmap recycling is less critical in modern Android,
     * it's still beneficial for apps handling many large images (5MB each)
     * to explicitly recycle bitmaps to avoid OutOfMemoryError.
     * The code checks isRecycled() before any access to prevent crashes.
     */
    public void recycle() {
        if (thumbnail != null && !thumbnail.isRecycled()) {
            thumbnail.recycle();
            thumbnail = null;
        }
        if (fullBitmap != null && !fullBitmap.isRecycled()) {
            fullBitmap.recycle();
            fullBitmap = null;
        }
    }
}
