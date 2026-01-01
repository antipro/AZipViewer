package com.bitifyware.zipviewer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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
    private String archiveFileName;
    private List<ImageEntry> images;
    private PasswordManager passwordManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        archivePath = getIntent().getStringExtra(EXTRA_ARCHIVE_PATH);
        archiveFileName = getIntent().getStringExtra(EXTRA_ARCHIVE_NAME);
        password = getIntent().getStringExtra(EXTRA_PASSWORD);

        passwordManager = new PasswordManager(this);

        imageRecyclerView = findViewById(R.id.imageRecyclerView);
        btnBack = findViewById(R.id.btnBack);
        btnGridView = findViewById(R.id.btnGridView);
        btnListView = findViewById(R.id.btnListView);
        archiveName = findViewById(R.id.archiveName);

        archiveName.setText(archiveFileName);

        images = new ArrayList<>();
        imageAdapter = new ImageAdapter(images, this::onImageClick);
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

    @Override
    public void onConfigurationChanged(@androidx.annotation.NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Handle orientation change
        // The layout manager will automatically adjust, but we can do custom handling here if needed
        if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            // Landscape mode - could adjust grid columns if desired
        } else if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            // Portrait mode
        }
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
                List<ImageEntry> loadedImages = new ArrayList<>();

                // First pass: Create image entries and load thumbnails only
                for (FileHeader fileHeader : fileHeaders) {
                    String fileName = fileHeader.getFileName().toLowerCase();
                    if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ||
                        fileName.endsWith(".png") || fileName.endsWith(".gif") ||
                        fileName.endsWith(".bmp") || fileName.endsWith(".webp")) {

                        ImageEntry imageEntry = new ImageEntry(fileHeader.getFileName());
                        imageEntry.setFileSize(fileHeader.getUncompressedSize());
                        imageEntry.setArchivePath(archivePath);
                        imageEntry.setPassword(password);
                        
                        // Load image data into byte array
                        InputStream inputStream = zipFile.getInputStream(fileHeader);
                        byte[] imageData = readAllBytes(inputStream);
                        inputStream.close();
                        
                        if (imageData != null && imageData.length > 0) {
                            // Decode directly as thumbnail using efficient sampling
                            Bitmap thumbnail = ThumbnailGenerator.decodeSampledBitmap(
                                imageData, 300, 300
                            );
                            
                            if (thumbnail != null) {
                                imageEntry.setThumbnail(thumbnail);
                                loadedImages.add(imageEntry);
                            }
                        }
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

            } catch (ZipException e) {
                // Check if it's a password-related error
                runOnUiThread(() -> {
                    if (e.getMessage() != null && 
                        (e.getMessage().contains("password") || 
                         e.getMessage().contains("Wrong password") ||
                         e.getMessage().contains("encrypted"))) {
                        // Prompt for password
                        promptForPassword();
                    } else {
                        Toast.makeText(this, "Error loading images: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading images: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Prompt user to enter password for encrypted archive
     */
    private void promptForPassword() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_password, null);
        EditText passwordInput = dialogView.findViewById(R.id.passwordInput);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        
        // Make dialog background transparent to show custom background
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> {
            dialog.dismiss();
            finish(); // Close gallery if user cancels
        });
        
        dialogView.findViewById(R.id.btnUnlock).setOnClickListener(v -> {
            String newPassword = passwordInput.getText().toString();
            if (!newPassword.isEmpty()) {
                password = newPassword;
                passwordManager.savePassword(archiveFileName, newPassword);
                Toast.makeText(this, "Password saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                loadImagesFromArchive(); // Retry loading
            } else {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }

    private void onImageClick(int position) {
        // Load full bitmaps on-demand from archive
        new Thread(() -> {
            List<Bitmap> fullBitmaps = new ArrayList<>();
            try {
                File archiveFile = new File(archivePath);
                ZipFile zipFile = new ZipFile(archiveFile);

                // Set password if archive is encrypted
                if (zipFile.isEncrypted() && password != null && !password.isEmpty()) {
                    zipFile.setPassword(password.toCharArray());
                }

                // Load full bitmaps for all images
                for (ImageEntry entry : images) {
                    try {
                        FileHeader fileHeader = zipFile.getFileHeader(entry.getFileName());
                        if (fileHeader != null) {
                            InputStream inputStream = zipFile.getInputStream(fileHeader);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            inputStream.close();
                            
                            if (bitmap != null) {
                                fullBitmaps.add(bitmap);
                            }
                        }
                    } catch (Exception e) {
                        // If loading fails, use thumbnail as fallback
                        if (entry.hasThumbnail()) {
                            fullBitmaps.add(entry.getThumbnail());
                        }
                    }
                }

                runOnUiThread(() -> {
                    // Set shared images and open viewer
                    ImageViewerActivity.setSharedImages(fullBitmaps);
                    android.content.Intent intent = new android.content.Intent(this, ImageViewerActivity.class);
                    intent.putExtra(ImageViewerActivity.EXTRA_POSITION, position);
                    startActivity(intent);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error loading images: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * Helper method to read all bytes from an InputStream
     */
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up bitmaps when activity is destroyed
        if (images != null) {
            for (ImageEntry entry : images) {
                if (entry != null) {
                    entry.recycle();
                }
            }
            images.clear();
        }
    }
}
