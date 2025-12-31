package com.bitifyware.zipviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Main activity for ZipViewer - Private Archive Viewer
 * Features:
 * - Privacy First: All data stored in internal storage
 * - Encrypted Archives: Support for password-protected ZIP files
 * - Grid and List views
 * - Zip Editor: Can be called by other apps to open ZIP files
 */
public class MainActivity extends AppCompatActivity implements ArchiveAdapter.OnArchiveClickListener {

    private RecyclerView recyclerView;
    private FloatingActionButton fabAdd;
    private EditText searchBar;
    private ArchiveAdapter archiveAdapter;
    private List<ArchiveItem> archives;
    private List<ArchiveItem> filteredArchives;
    private PasswordManager passwordManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        searchBar = findViewById(R.id.searchBar);

        archives = new ArrayList<>();
        filteredArchives = new ArrayList<>();
        passwordManager = new PasswordManager(this);

        archiveAdapter = new ArchiveAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(archiveAdapter);

        fabAdd.setOnClickListener(v -> {
            Toast.makeText(this, "Select a ZIP file to add", Toast.LENGTH_SHORT).show();
            // TODO: Implement file picker
        });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterArchives(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Load archives from internal storage
        loadArchives();

        // Check if activity was launched with a file intent
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadArchives();
    }

    /**
     * Handle incoming intents from other apps (like Telegram) to open ZIP files
     */
    private void handleIntent(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) {
                openArchiveFile(uri);
            }
        }
    }

    /**
     * Open and process archive file from URI
     * Stores file in internal storage for privacy
     */
    private void openArchiveFile(Uri uri) {
        new Thread(() -> {
            try {
                // Copy file to internal storage for privacy
                File internalFile = copyToInternalStorage(uri);
                
                // Check if archive is encrypted and prompt for password
                runOnUiThread(() -> {
                    checkAndPromptForPassword(internalFile);
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error opening archive: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Check if archive is encrypted and prompt for password
     */
    private void checkAndPromptForPassword(File archiveFile) {
        new Thread(() -> {
            try {
                ZipFile zipFile = new ZipFile(archiveFile);
                boolean isEncrypted = zipFile.isEncrypted();
                
                runOnUiThread(() -> {
                    if (isEncrypted) {
                        promptForPassword(archiveFile.getName(), null);
                    } else {
                        Toast.makeText(this, "Archive added: " + archiveFile.getName(), Toast.LENGTH_SHORT).show();
                        loadArchives();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Archive added: " + archiveFile.getName(), Toast.LENGTH_SHORT).show();
                    loadArchives();
                });
            }
        }).start();
    }

    /**
     * Prompt user to enter password for encrypted archive
     */
    private void promptForPassword(String fileName, Runnable onSuccess) {
        View dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null);
        EditText passwordInput = new EditText(this);
        passwordInput.setHint("Enter password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Password Required")
                .setMessage("This archive is encrypted. Please enter the password:")
                .setView(passwordInput)
                .setPositiveButton("Save", (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    if (!password.isEmpty()) {
                        passwordManager.savePassword(fileName, password);
                        Toast.makeText(this, "Password saved", Toast.LENGTH_SHORT).show();
                        loadArchives();
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    }
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    loadArchives();
                })
                .show();
    }

    /**
     * Copy file to internal storage to ensure privacy
     * Files in internal storage cannot be accessed by other apps
     */
    private File copyToInternalStorage(Uri uri) throws Exception {
        File internalDir = new File(getFilesDir(), "archives");
        if (!internalDir.exists()) {
            internalDir.mkdirs();
        }
        
        // Get filename and sanitize to prevent path traversal
        String fileName = uri.getLastPathSegment();
        if (fileName == null || fileName.isEmpty()) {
            fileName = "archive_" + System.currentTimeMillis() + ".zip";
        }
        // Sanitize filename to prevent directory traversal attacks
        fileName = new File(fileName).getName();
        
        File outputFile = new File(internalDir, fileName);
        
        // Use try-with-resources to ensure streams are properly closed
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            
            if (inputStream == null) {
                throw new Exception("Cannot open input stream");
            }
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        
        return outputFile;
    }

    /**
     * Load all archives from internal storage
     */
    private void loadArchives() {
        archives.clear();
        
        File archivesDir = new File(getFilesDir(), "archives");
        if (archivesDir.exists()) {
            File[] files = archivesDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        ArchiveItem item = new ArchiveItem(file);
                        // Load password from storage
                        String password = passwordManager.getPassword(file.getName());
                        if (password != null) {
                            item.setPassword(password);
                        }
                        archives.add(item);
                    }
                }
            }
        }
        
        filterArchives(searchBar.getText().toString());
    }

    /**
     * Filter archives by search query
     */
    private void filterArchives(String query) {
        filteredArchives.clear();
        
        if (query.isEmpty()) {
            filteredArchives.addAll(archives);
        } else {
            String lowerQuery = query.toLowerCase();
            for (ArchiveItem item : archives) {
                if (item.getName().toLowerCase().contains(lowerQuery)) {
                    filteredArchives.add(item);
                }
            }
        }
        
        archiveAdapter.setArchives(filteredArchives);
    }

    @Override
    public void onArchiveClick(ArchiveItem item) {
        item.incrementViewCount();
        
        // Check if password is needed and validate it
        new Thread(() -> {
            try {
                ZipFile zipFile = new ZipFile(item.getFile());
                if (zipFile.isEncrypted() && item.hasPassword()) {
                    zipFile.setPassword(item.getPassword().toCharArray());
                }
                
                // Try to access the archive
                zipFile.getFileHeaders();
                
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, GalleryActivity.class);
                    intent.putExtra(GalleryActivity.EXTRA_ARCHIVE_PATH, item.getFile().getAbsolutePath());
                    intent.putExtra(GalleryActivity.EXTRA_ARCHIVE_NAME, item.getName());
                    intent.putExtra(GalleryActivity.EXTRA_PASSWORD, item.getPassword());
                    startActivity(intent);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    // Password might be wrong or missing
                    promptForPassword(item.getName(), () -> {
                        // Retry opening
                        onArchiveClick(item);
                    });
                });
            }
        }).start();
    }

    @Override
    public void onDeleteClick(ArchiveItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Archive")
                .setMessage("Are you sure you want to delete " + item.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (item.getFile().delete()) {
                        // Also remove password
                        passwordManager.removePassword(item.getName());
                        Toast.makeText(this, "Archive deleted", Toast.LENGTH_SHORT).show();
                        loadArchives();
                    } else {
                        Toast.makeText(this, "Failed to delete archive", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
