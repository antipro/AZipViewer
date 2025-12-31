package com.bitifyware.zipviewer;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.github.chrisbanes.photoview.PhotoView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen image viewer with swipe navigation, zoom, and rotate
 */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_POSITION = "position";
    
    // Static field to hold images temporarily (avoids parcelable/serialization issues)
    private static List<Bitmap> sharedImages;

    private ViewPager2 viewPager;
    private ImageButton btnBack, btnRotate;
    private TextView tvImageCounter;
    private ImageViewerAdapter adapter;
    private int currentPosition;
    private float currentRotation = 0f;

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
        btnRotate = findViewById(R.id.btnRotate);
        tvImageCounter = findViewById(R.id.tvImageCounter);

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
                currentRotation = 0f; // Reset rotation on page change
                updateImageCounter(position);
            }
        });

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Rotate button
        btnRotate.setOnClickListener(v -> rotateCurrentImage());

        // Toggle UI on tap
        findViewById(R.id.topBar).setOnClickListener(v -> toggleUI());
    }

    private void updateImageCounter(int position) {
        tvImageCounter.setText((position + 1) + " / " + sharedImages.size());
    }

    private void rotateCurrentImage() {
        // Rotate by 90 degrees
        currentRotation = (currentRotation + 90f) % 360f;
        
        // Get the current PhotoView and rotate it
        RecyclerView.ViewHolder holder = ((RecyclerView) viewPager.getChildAt(0))
                .findViewHolderForAdapterPosition(currentPosition);
        
        if (holder instanceof ImageViewerAdapter.ImageViewerViewHolder) {
            PhotoView photoView = ((ImageViewerAdapter.ImageViewerViewHolder) holder).photoView;
            photoView.setRotation(currentRotation);
        }
    }

    private void toggleUI() {
        View topBar = findViewById(R.id.topBar);
        if (topBar.getVisibility() == View.VISIBLE) {
            topBar.setVisibility(View.GONE);
        } else {
            topBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't clear shared images as they're managed by GalleryActivity
    }
}
