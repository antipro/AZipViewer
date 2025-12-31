package com.bitifyware.zipviewer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying archive files in RecyclerView
 */
public class ArchiveAdapter extends RecyclerView.Adapter<ArchiveAdapter.ArchiveViewHolder> {

    private List<ArchiveItem> archives;
    private OnArchiveClickListener listener;

    public interface OnArchiveClickListener {
        void onArchiveClick(ArchiveItem item);
        void onDeleteClick(ArchiveItem item);
    }

    public ArchiveAdapter(OnArchiveClickListener listener) {
        this.archives = new ArrayList<>();
        this.listener = listener;
    }

    public void setArchives(List<ArchiveItem> archives) {
        this.archives = archives;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ArchiveViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_archive, parent, false);
        return new ArchiveViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArchiveViewHolder holder, int position) {
        ArchiveItem item = archives.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return archives.size();
    }

    class ArchiveViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, fileSize, fileDate, viewCount, passwordText;
        LinearLayout passwordContainer;
        ImageButton btnDelete;

        public ArchiveViewHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            fileSize = itemView.findViewById(R.id.fileSize);
            fileDate = itemView.findViewById(R.id.fileDate);
            viewCount = itemView.findViewById(R.id.viewCount);
            passwordText = itemView.findViewById(R.id.passwordText);
            passwordContainer = itemView.findViewById(R.id.passwordContainer);
            btnDelete = itemView.findViewById(R.id.btnDelete);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onArchiveClick(archives.get(position));
                }
            });

            btnDelete.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onDeleteClick(archives.get(position));
                }
            });
        }

        public void bind(ArchiveItem item) {
            fileName.setText(item.getName());
            fileSize.setText(item.getFormattedSize());
            fileDate.setText(item.getFormattedDate());
            viewCount.setText(String.valueOf(item.getViewCount()));

            // Show password if exists
            if (item.hasPassword()) {
                passwordContainer.setVisibility(View.VISIBLE);
                passwordText.setText("Password: " + item.getPassword());
            } else {
                passwordContainer.setVisibility(View.GONE);
            }
        }
    }
}
