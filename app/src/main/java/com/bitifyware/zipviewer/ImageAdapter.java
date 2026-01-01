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

    private List<Bitmap> images;
    private OnImageClickListener clickListener;

    public interface OnImageClickListener {
        void onImageClick(int position);
    }

    public ImageAdapter(List<Bitmap> images, OnImageClickListener clickListener) {
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
        Bitmap bitmap = images.get(position);
        holder.imageView.setImageBitmap(bitmap);
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
