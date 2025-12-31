package com.bitifyware.zipviewer;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

/**
 * Adapter for ViewPager2 to display full-screen zoomable images
 */
public class ImageViewerAdapter extends RecyclerView.Adapter<ImageViewerAdapter.ImageViewerViewHolder> {

    private List<Bitmap> images;
    private OnRotateListener rotateListener;

    public interface OnRotateListener {
        void onRotate(int position, float rotation);
    }

    public ImageViewerAdapter(List<Bitmap> images) {
        this.images = images;
    }

    public void setOnRotateListener(OnRotateListener listener) {
        this.rotateListener = listener;
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
        holder.photoView.setImageBitmap(bitmap);
        holder.photoView.setRotation(0); // Reset rotation for each bind
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static class ImageViewerViewHolder extends RecyclerView.ViewHolder {
        PhotoView photoView;

        public ImageViewerViewHolder(@NonNull View itemView) {
            super(itemView);
            photoView = itemView.findViewById(R.id.photoView);
        }
    }

    public PhotoView getPhotoViewAt(int position) {
        // This is a helper method that will be used to get the current PhotoView
        return null; // We'll handle rotation differently
    }
}
