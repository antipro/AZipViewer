package com.bitifyware.zipviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Gallery activity for viewing images from an archive
 */
public class GalleryActivity extends AppCompatActivity {

    public static final String EXTRA_ARCHIVE_PATH = "archive_path";
    public static final String EXTRA_ARCHIVE_NAME = "archive_name";
    public static final String EXTRA_PASSWORD = "password";

    private RecyclerView imageRecyclerView;
    private ImageButton btnBack, btnGridView, btnListView;
    private TextView archiveName;
    private ImageAdapter imageAdapter;
    private boolean isGridView = true;

    private String archivePath;
    private String password;
    private List<Bitmap> images;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        archivePath = getIntent().getStringExtra(EXTRA_ARCHIVE_PATH);
        String name = getIntent().getStringExtra(EXTRA_ARCHIVE_NAME);
        password = getIntent().getStringExtra(EXTRA_PASSWORD);

        imageRecyclerView = findViewById(R.id.imageRecyclerView);
        btnBack = findViewById(R.id.btnBack);
        btnGridView = findViewById(R.id.btnGridView);
        btnListView = findViewById(R.id.btnListView);
        archiveName = findViewById(R.id.archiveName);

        archiveName.setText(name);

        images = new ArrayList<>();
        imageAdapter = new ImageAdapter(images);
        imageRecyclerView.setAdapter(imageAdapter);
        updateLayoutManager();

        btnBack.setOnClickListener(v -> finish());

        btnGridView.setOnClickListener(v -> {
            isGridView = true;
            updateLayoutManager();
            updateViewButtons();
        });

        btnListView.setOnClickListener(v -> {
            isGridView = false;
            updateLayoutManager();
            updateViewButtons();
        });

        updateViewButtons();
        loadImagesFromArchive();
    }

    private void updateLayoutManager() {
        if (isGridView) {
            imageRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        } else {
            imageRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private void updateViewButtons() {
        int activeColor = getResources().getColor(R.color.accent_purple, getTheme());
        int inactiveColor = getResources().getColor(R.color.dark_text_secondary, getTheme());

        btnGridView.setColorFilter(isGridView ? activeColor : inactiveColor);
        btnListView.setColorFilter(!isGridView ? activeColor : inactiveColor);
    }

    private void loadImagesFromArchive() {
        new Thread(() -> {
            try {
                File archiveFile = new File(archivePath);
                ZipFile zipFile = new ZipFile(archiveFile);

                // Set password if archive is encrypted
                if (zipFile.isEncrypted() && password != null && !password.isEmpty()) {
                    zipFile.setPassword(password.toCharArray());
                }

                List<FileHeader> fileHeaders = zipFile.getFileHeaders();
                List<Bitmap> loadedImages = new ArrayList<>();

                for (FileHeader fileHeader : fileHeaders) {
                    String fileName = fileHeader.getFileName().toLowerCase();
                    if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                        fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                        fileName.endsWith(".bmp") || fileName.endsWith(".webp")) {

                        InputStream inputStream = zipFile.getInputStream(fileHeader);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        if (bitmap != null) {
                            loadedImages.add(bitmap);
                        }
                        inputStream.close();
                    }
                }

                runOnUiThread(() -> {
                    images.clear();
                    images.addAll(loadedImages);
                    imageAdapter.notifyDataSetChanged();

                    if (images.isEmpty()) {
                        Toast.makeText(this, "No images found in archive", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading images: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
