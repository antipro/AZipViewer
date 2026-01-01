package com.bitifyware.zipviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;

/**
 * Utility class for generating thumbnails from images
 */
public class ThumbnailGenerator {

    // Configurable threshold for generating thumbnails (default 1MB)
    public static final int THUMBNAIL_SIZE_THRESHOLD = 1024 * 1024; // 1MB in bytes
    
    // Target thumbnail dimensions
    private static final int THUMBNAIL_WIDTH = 300;
    private static final int THUMBNAIL_HEIGHT = 300;

    /**
     * Generate a thumbnail from a bitmap
     * Uses efficient matrix-based scaling for better memory efficiency
     * 
     * @param source The source bitmap
     * @return The thumbnail bitmap
     */
    public static Bitmap generateThumbnail(Bitmap source) {
        if (source == null) {
            return null;
        }

        int width = source.getWidth();
        int height = source.getHeight();

        // Calculate the scaling factor
        float scaleWidth = (float) THUMBNAIL_WIDTH / width;
        float scaleHeight = (float) THUMBNAIL_HEIGHT / height;
        float scaleFactor = Math.min(scaleWidth, scaleHeight);

        // Calculate new dimensions
        int newWidth = Math.round(width * scaleFactor);
        int newHeight = Math.round(height * scaleFactor);

        // Use Matrix for more efficient scaling
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postScale(scaleFactor, scaleFactor);
        
        return Bitmap.createBitmap(source, 0, 0, width, height, matrix, true);
    }

    /**
     * Estimate the size of a bitmap in bytes
     * 
     * @param bitmap The bitmap to estimate
     * @return The estimated size in bytes
     */
    public static int estimateBitmapSize(Bitmap bitmap) {
        if (bitmap == null) {
            return 0;
        }
        // Width * Height * Bytes per pixel (4 for ARGB_8888)
        return bitmap.getWidth() * bitmap.getHeight() * 4;
    }

    /**
     * Check if an image needs a thumbnail based on its size
     * 
     * @param bitmap The bitmap to check
     * @return true if the bitmap is larger than the threshold
     */
    public static boolean needsThumbnail(Bitmap bitmap) {
        return estimateBitmapSize(bitmap) > THUMBNAIL_SIZE_THRESHOLD;
    }

    /**
     * Decode a bitmap with sample size to reduce memory usage
     * 
     * @param data The image data
     * @param reqWidth The required width
     * @param reqHeight The required height
     * @return The decoded bitmap
     */
    public static Bitmap decodeSampledBitmap(byte[] data, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    /**
     * Calculate sample size for bitmap decoding
     * 
     * @param options The bitmap options
     * @param reqWidth The required width
     * @param reqHeight The required height
     * @return The sample size
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, 
                                            int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
