package com.bitifyware.zipviewer;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.chrisbanes.photoview.PhotoView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for ViewPager2 to display full-screen zoomable images
 */
public class ImageViewerAdapter extends RecyclerView.Adapter<ImageViewerAdapter.ImageViewerViewHolder> {

    private List<Bitmap> images;
    private Map<Integer, Float> rotationMap = new HashMap<>();
    private Map<Integer, Bitmap> rotatedBitmaps = new HashMap<>();

    public ImageViewerAdapter(List<Bitmap> images) {
        this.images = images;
    }

    @NonNull
    @Override
    public ImageViewerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image_viewer, parent, false);
        return new ImageViewerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewerViewHolder holder, int position) {
        Bitmap bitmap = images.get(position);
        Float savedRotation = rotationMap.get(position);
        
        if (savedRotation != null && savedRotation != 0f) {
            // Use cached rotated bitmap if available
            Bitmap rotatedBitmap = rotatedBitmaps.get(position);
            if (rotatedBitmap == null || isBitmapRecycled(rotatedBitmap)) {
                rotatedBitmap = rotateBitmap(bitmap, savedRotation);
                rotatedBitmaps.put(position, rotatedBitmap);
            }
            holder.photoView.setImageBitmap(rotatedBitmap);
        } else {
            holder.photoView.setImageBitmap(bitmap);
        }
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    /**
     * Rotate bitmap by the specified angle
     */
    private Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    /**
     * Save rotation state for a specific position
     */
    public void saveRotation(int position, float rotation) {
        rotationMap.put(position, rotation);
        // Clear cached rotated bitmap to force recreation
        Bitmap oldRotated = rotatedBitmaps.remove(position);
        if (oldRotated != null && !isBitmapRecycled(oldRotated)) {
            // Check if this bitmap is one of the original images (don't recycle those)
            if (position >= 0 && position < images.size() && oldRotated != images.get(position)) {
                oldRotated.recycle();
            }
        }
    }
    
    /**
     * Safely check if a bitmap is recycled
     */
    private boolean isBitmapRecycled(Bitmap bitmap) {
        if (bitmap == null) {
            return true;
        }
        try {
            return bitmap.isRecycled();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /**
     * Get rotation state for a specific position
     */
    public float getRotation(int position) {
        Float rotation = rotationMap.get(position);
        return rotation != null ? rotation : 0f;
    }
    
    /**
     * Clean up cached rotated bitmaps to prevent memory leaks
     * Call this when the adapter is no longer needed
     */
    public void cleanup() {
        for (Map.Entry<Integer, Bitmap> entry : rotatedBitmaps.entrySet()) {
            Bitmap bitmap = entry.getValue();
            int position = entry.getKey();
            // Only recycle if it's not null, not recycled, and not one of the original images
            if (bitmap != null && !isBitmapRecycled(bitmap)) {
                if (position < 0 || position >= images.size() || bitmap != images.get(position)) {
                    bitmap.recycle();
                }
            }
        }
        rotatedBitmaps.clear();
        rotationMap.clear();
    }

    static class ImageViewerViewHolder extends RecyclerView.ViewHolder {
        PhotoView photoView;

        public ImageViewerViewHolder(@NonNull View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.photoView);
        }
    }
}
