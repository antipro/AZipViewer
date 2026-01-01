package com.bitifyware.zipviewer;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for displaying images in gallery
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    private List<ImageEntry> images;
    private OnImageClickListener clickListener;

    public interface OnImageClickListener {
        void onImageClick(int position);
    }

    public ImageAdapter(List<ImageEntry> images, OnImageClickListener clickListener) {
        this.images = images;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        ImageEntry imageEntry = images.get(position);
        
        // Show thumbnail if available, otherwise show placeholder
        if (imageEntry.hasThumbnail()) {
            holder.imageView.setImageBitmap(imageEntry.getThumbnail());
            holder.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            // Show placeholder icon while loading
            holder.imageView.setImageResource(R.drawable.ic_image_placeholder);
            holder.imageView.setScaleType(ImageView.ScaleType.CENTER);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    clickListener.onImageClick(adapterPosition);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }
}
