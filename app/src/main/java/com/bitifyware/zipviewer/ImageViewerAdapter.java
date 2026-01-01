package com.bitifyware.zipviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.chrisbanes.photoview.PhotoView;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adapter for ViewPager2 to display full-screen zoomable images
 * Images are loaded on-demand from the archive to prevent memory exhaustion
 */
public class ImageViewerAdapter extends RecyclerView.Adapter<ImageViewerAdapter.ImageViewerViewHolder> {

    private Context context;
    private List<ImageEntry> imageEntries;
    private String archivePath;
    private String password;
    private Map<Integer, Float> rotationMap = new HashMap<>();
    private Map<Integer, Bitmap> loadedBitmaps = new HashMap<>();
    private Map<Integer, Bitmap> rotatedBitmaps = new HashMap<>();
    private ExecutorService executorService = Executors.newFixedThreadPool(2);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public ImageViewerAdapter(Context context, List<ImageEntry> imageEntries, String archivePath, String password) {
        this.context = context;
        this.imageEntries = imageEntries;
        this.archivePath = archivePath;
        this.password = password;
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
        // First, show thumbnail if available
        ImageEntry entry = imageEntries.get(position);
        if (entry.hasThumbnail()) {
            holder.photoView.setImageBitmap(entry.getThumbnail());
        }
        
        // Check if full bitmap is already loaded
        Bitmap loadedBitmap = loadedBitmaps.get(position);
        if (loadedBitmap != null && !isBitmapRecycled(loadedBitmap)) {
            // Use cached bitmap
            displayBitmap(holder, position, loadedBitmap);
        } else {
            // Load bitmap from archive in background
            loadBitmapAsync(holder, position);
        }
        
        // Configure PhotoView for double-tap zoom
        setupPhotoView(holder);
    }
    
    /**
     * Load bitmap from archive asynchronously
     */
    private void loadBitmapAsync(@NonNull ImageViewerViewHolder holder, int position) {
        ImageEntry entry = imageEntries.get(position);
        
        executorService.execute(() -> {
            try {
                File archiveFile = new File(archivePath);
                ZipFile zipFile = new ZipFile(archiveFile);
                
                // Set password if archive is encrypted
                if (zipFile.isEncrypted() && password != null && !password.isEmpty()) {
                    zipFile.setPassword(password.toCharArray());
                }
                
                FileHeader fileHeader = zipFile.getFileHeader(entry.getFileName());
                if (fileHeader != null) {
                    InputStream inputStream = zipFile.getInputStream(fileHeader);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    inputStream.close();
                    
                    if (bitmap != null) {
                        // Cache the loaded bitmap
                        loadedBitmaps.put(position, bitmap);
                        
                        // Update UI on main thread
                        mainHandler.post(() -> {
                            if (holder.getBindingAdapterPosition() == position) {
                                displayBitmap(holder, position, bitmap);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                // Show error on main thread
                mainHandler.post(() -> {
                    if (context != null) {
                        Toast.makeText(context, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    /**
     * Display bitmap with rotation if applicable
     */
    private void displayBitmap(@NonNull ImageViewerViewHolder holder, int position, Bitmap bitmap) {
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
    
    /**
     * Setup PhotoView configuration
     */
    private void setupPhotoView(@NonNull ImageViewerViewHolder holder) {
        // Set maximum scale to show actual pixels (1:1)
        holder.photoView.setMaximumScale(5.0f);
        holder.photoView.setMediumScale(3.0f);
        holder.photoView.setMinimumScale(0.5f);
        
        // Enable zoom and double-tap
        holder.photoView.setZoomable(true);
        
        // Add double-tap listener to toggle between fit and actual size
        holder.photoView.setOnDoubleTapListener(new android.view.GestureDetector.OnDoubleTapListener() {
            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                return false;
            }

            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                float currentScale = holder.photoView.getScale();
                float minScale = holder.photoView.getMinimumScale();
                float maxScale = holder.photoView.getMaximumScale();
                
                // Toggle between fit and actual size (1:1 scale)
                if (currentScale < 0.95f || currentScale > 1.05f) {
                    // Not at 1:1, zoom to actual size
                    holder.photoView.setScale(1.0f, e.getX(), e.getY(), true);
                } else {
                    // At actual size, zoom back to fit
                    holder.photoView.setScale(minScale, true);
                }
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(android.view.MotionEvent e) {
                return false;
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageEntries.size();
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
            // Check if this bitmap is one of the loaded images (don't recycle those)
            Bitmap loadedBitmap = loadedBitmaps.get(position);
            if (loadedBitmap != null && oldRotated != loadedBitmap) {
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
        // Shutdown executor service
        executorService.shutdown();
        
        // Clean up rotated bitmaps
        for (Map.Entry<Integer, Bitmap> entry : rotatedBitmaps.entrySet()) {
            Bitmap bitmap = entry.getValue();
            int position = entry.getKey();
            // Only recycle if it's not null, not recycled, and not one of the loaded images
            if (bitmap != null && !isBitmapRecycled(bitmap)) {
                Bitmap loadedBitmap = loadedBitmaps.get(position);
                if (loadedBitmap == null || bitmap != loadedBitmap) {
                    bitmap.recycle();
                }
            }
        }
        rotatedBitmaps.clear();
        
        // Clean up loaded bitmaps
        for (Bitmap bitmap : loadedBitmaps.values()) {
            if (bitmap != null && !isBitmapRecycled(bitmap)) {
                bitmap.recycle();
            }
        }
        loadedBitmaps.clear();
        
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
