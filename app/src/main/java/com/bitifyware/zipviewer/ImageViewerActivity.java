package com.bitifyware.zipviewer;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.github.chrisbanes.photoview.PhotoView;

import java.util.List;

/**
 * Full-screen image viewer with swipe navigation, zoom, and rotate
 */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_POSITION = "position";
    
    // Static field to hold images temporarily (avoids parcelable/serialization issues)
    private static List<Bitmap> sharedImages;

    private ViewPager2 viewPager;
    private ImageButton btnBack, btnRotateLeft, btnRotateRight, btnZoomIn, btnZoomOut;
    private TextView tvImageCounter, tvSwipeHint;
    private ImageViewerAdapter adapter;
    private int currentPosition;

    public static void setSharedImages(List<Bitmap> images) {
        sharedImages = images;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        // Hide system UI for immersive experience
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        viewPager = findViewById(R.id.viewPager);
        btnBack = findViewById(R.id.btnBack);
        btnRotateLeft = findViewById(R.id.btnRotateLeft);
        btnRotateRight = findViewById(R.id.btnRotateRight);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        tvImageCounter = findViewById(R.id.tvImageCounter);
        tvSwipeHint = findViewById(R.id.tvSwipeHint);

        // Get position from intent
        currentPosition = getIntent().getIntExtra(EXTRA_POSITION, 0);

        if (sharedImages == null || sharedImages.isEmpty()) {
            finish();
            return;
        }

        // Setup adapter
        adapter = new ImageViewerAdapter(sharedImages);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentPosition, false);

        // Update counter
        updateImageCounter(currentPosition);

        // Setup page change listener
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                updateImageCounter(position);
            }
        });

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Rotate left button - rotates current image by -90 degrees
        btnRotateLeft.setOnClickListener(v -> {
            float currentRotation = adapter.getRotation(currentPosition);
            float newRotation = (currentRotation - 90f) % 360f;
            adapter.saveRotation(currentPosition, newRotation);
            adapter.notifyItemChanged(currentPosition);
        });

        // Rotate right button - rotates current image by 90 degrees
        btnRotateRight.setOnClickListener(v -> {
            float currentRotation = adapter.getRotation(currentPosition);
            float newRotation = (currentRotation + 90f) % 360f;
            adapter.saveRotation(currentPosition, newRotation);
            adapter.notifyItemChanged(currentPosition);
        });

        // Zoom in button
        btnZoomIn.setOnClickListener(v -> {
            PhotoView photoView = getCurrentPhotoView();
            if (photoView != null) {
                float currentScale = photoView.getScale();
                photoView.setScale(currentScale * 1.5f, true);
            }
        });

        // Zoom out button
        btnZoomOut.setOnClickListener(v -> {
            PhotoView photoView = getCurrentPhotoView();
            if (photoView != null) {
                float currentScale = photoView.getScale();
                photoView.setScale(currentScale / 1.5f, true);
            }
        });

        // Toggle UI on tap
        findViewById(R.id.topBar).setOnClickListener(v -> toggleUI());
    }

    private PhotoView getCurrentPhotoView() {
        try {
            // Get the RecyclerView that ViewPager2 uses internally
            if (viewPager.getChildCount() > 0) {
                View recyclerView = viewPager.getChildAt(0);
                if (recyclerView instanceof androidx.recyclerview.widget.RecyclerView) {
                    androidx.recyclerview.widget.RecyclerView rv = (androidx.recyclerview.widget.RecyclerView) recyclerView;
                    // Find the view holder for the current position
                    androidx.recyclerview.widget.RecyclerView.ViewHolder holder = 
                        rv.findViewHolderForAdapterPosition(currentPosition);
                    if (holder != null && holder.itemView != null) {
                        return holder.itemView.findViewById(R.id.photoView);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private void updateImageCounter(int position) {
        if (sharedImages != null) {
            tvImageCounter.setText((position + 1) + " / " + sharedImages.size());
        }
    }

    private void toggleUI() {
        View topBar = findViewById(R.id.topBar);
        if (topBar.getVisibility() == View.VISIBLE) {
            topBar.setVisibility(View.GONE);
            tvSwipeHint.setVisibility(View.GONE);
        } else {
            topBar.setVisibility(View.VISIBLE);
            tvSwipeHint.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear shared images to prevent accessing recycled bitmaps
        sharedImages = null;
    }
}
