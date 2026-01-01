package com.bitifyware.zipviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
    private ActivityResultLauncher<String[]> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize file picker launcher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        openArchiveFile(uri, false);
                    }
                }
        );

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
            // Launch file picker for archive files
            filePickerLauncher.launch(new String[]{
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/x-rar-compressed",
                    "application/x-7z-compressed"
            });
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
                openArchiveFile(uri, true);
            }
        }
    }

    /**
     * Open and process archive file from URI
     * Stores file in internal storage for privacy
     * @param uri The URI of the file to open
     * @param fromSharedIntent true if opened from other apps (ACTION_VIEW), false if from file picker
     */
    private void openArchiveFile(Uri uri, boolean fromSharedIntent) {
        new Thread(() -> {
            try {
                // Get filename first to check for conflicts
                String fileName = getFileNameFromUri(uri);
                File internalDir = new File(getFilesDir(), "archives");
                if (!internalDir.exists()) {
                    internalDir.mkdirs();
                }
                
                File targetFile = new File(internalDir, fileName);
                
                // Check if file already exists
                if (targetFile.exists()) {
                    // Show confirmation dialog on UI thread
                    runOnUiThread(() -> {
                        showFileExistsDialog(uri, fileName, fromSharedIntent);
                    });
                } else {
                    // File doesn't exist, proceed with copy
                    File internalFile = copyToInternalStorage(uri, fileName);
                    
                    // Check if archive is encrypted and prompt for password
                    runOnUiThread(() -> {
                        checkAndPromptForPassword(internalFile, fromSharedIntent);
                    });
                }
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error opening archive: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Check if archive is encrypted and prompt for password
     * @param archiveFile The archive file to check
     * @param fromSharedIntent true if opened from other apps, false if from file picker
     */
    private void checkAndPromptForPassword(File archiveFile, boolean fromSharedIntent) {
        new Thread(() -> {
            try {
                ZipFile zipFile = new ZipFile(archiveFile);
                boolean isEncrypted = zipFile.isEncrypted();
                
                runOnUiThread(() -> {
                    if (isEncrypted && fromSharedIntent) {
                        // For shared files, prompt for password and then open
                        promptForPasswordAndOpen(archiveFile.getName(), archiveFile);
                    } else if (isEncrypted) {
                        // For file picker, just prompt to save password (don't open)
                        promptForPassword(archiveFile.getName(), null);
                    } else if (fromSharedIntent) {
                        // Non-encrypted shared file - open directly
                        showArchiveAddedMessage(archiveFile.getName());
                        openGallery(archiveFile, null);
                    } else {
                        // Non-encrypted file from picker - just add it
                        showArchiveAddedMessage(archiveFile.getName());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showArchiveAddedMessage(archiveFile.getName());
                    if (fromSharedIntent) {
                        openGallery(archiveFile, null);
                    }
                });
            }
        }).start();
    }

    /**
     * Show archive added message and reload archives list
     */
    private void showArchiveAddedMessage(String fileName) {
        Toast.makeText(this, "Archive added: " + fileName, Toast.LENGTH_SHORT).show();
        loadArchives();
    }

    /**
     * Prompt user to enter password for encrypted archive and open it after
     */
    private void promptForPasswordAndOpen(String fileName, File archiveFile) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password, null);
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
            loadArchives();
        });
        
        dialogView.findViewById(R.id.btnUnlock).setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            if (!password.isEmpty()) {
                passwordManager.savePassword(fileName, password);
                Toast.makeText(this, "Password saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                loadArchives();
                // Open the gallery after password is set
                openGallery(archiveFile, password);
            } else {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }

    /**
     * Open gallery activity for the given archive file
     */
    private void openGallery(File archiveFile, String password) {
        Intent intent = new Intent(this, GalleryActivity.class);
        intent.putExtra(GalleryActivity.EXTRA_ARCHIVE_PATH, archiveFile.getAbsolutePath());
        intent.putExtra(GalleryActivity.EXTRA_ARCHIVE_NAME, archiveFile.getName());
        intent.putExtra(GalleryActivity.EXTRA_PASSWORD, password);
        startActivity(intent);
    }

    /**
     * Prompt user to enter password for encrypted archive
     */
    private void promptForPassword(String fileName, Runnable onSuccess) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password, null);
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
            loadArchives();
        });
        
        dialogView.findViewById(R.id.btnUnlock).setOnClickListener(v -> {
            String password = passwordInput.getText().toString();
            if (!password.isEmpty()) {
                passwordManager.savePassword(fileName, password);
                Toast.makeText(this, "Password saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                loadArchives();
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } else {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.show();
    }

    /**
     * Copy file to internal storage to ensure privacy
     * Files in internal storage cannot be accessed by other apps
     */
    private File copyToInternalStorage(Uri uri, String fileName) throws Exception {
        File internalDir = new File(getFilesDir(), "archives");
        if (!internalDir.exists()) {
            internalDir.mkdirs();
        }
        
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
     * Extract filename from URI and sanitize it
     */
    private String getFileNameFromUri(Uri uri) {
        String fileName = uri.getLastPathSegment();
        if (fileName == null || fileName.isEmpty()) {
            fileName = "archive_" + System.currentTimeMillis() + ".zip";
        }
        // Sanitize filename to prevent directory traversal attacks
        fileName = new File(fileName).getName();
        return fileName;
    }
    
    /**
     * Show dialog asking user if they want to override existing file
     */
    private void showFileExistsDialog(Uri uri, String fileName, boolean fromSharedIntent) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.file_exists_title)
                .setMessage(getString(R.string.file_exists_message, fileName))
                .setPositiveButton(R.string.override, (dialog, which) -> {
                    // User chose to override, proceed with copy
                    new Thread(() -> {
                        try {
                            File internalFile = copyToInternalStorage(uri, fileName);
                            runOnUiThread(() -> {
                                checkAndPromptForPassword(internalFile, fromSharedIntent);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Error opening archive: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    }).start();
                })
                .setNegativeButton(R.string.keep_both, (dialog, which) -> {
                    // User chose to keep both files, generate unique filename
                    new Thread(() -> {
                        try {
                            String uniqueFileName = generateUniqueFileName(fileName);
                            File internalFile = copyToInternalStorage(uri, uniqueFileName);
                            runOnUiThread(() -> {
                                checkAndPromptForPassword(internalFile, fromSharedIntent);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "Error opening archive: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    }).start();
                })
                .setCancelable(true)
                .show();
    }
    
    /**
     * Generate a unique filename by appending a suffix
     */
    private String generateUniqueFileName(String fileName) {
        File internalDir = new File(getFilesDir(), "archives");
        
        // Split filename into name and extension
        String nameWithoutExt = fileName;
        String extension = "";
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            nameWithoutExt = fileName.substring(0, lastDotIndex);
            extension = fileName.substring(lastDotIndex);
        }
        
        // Try to find a unique name by appending numbers
        int counter = 1;
        String newFileName = fileName;
        while (new File(internalDir, newFileName).exists()) {
            newFileName = nameWithoutExt + " (" + counter + ")" + extension;
            counter++;
        }
        
        return newFileName;
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
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete, null);
        TextView deleteMessage = dialogView.findViewById(R.id.deleteMessage);
        deleteMessage.setText("Are you sure you want to delete " + item.getName() + "?");
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        // Make dialog background transparent to show custom background
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> {
            dialog.dismiss();
        });
        
        dialogView.findViewById(R.id.btnDelete).setOnClickListener(v -> {
            if (item.getFile().delete()) {
                // Also remove password
                passwordManager.removePassword(item.getName());
                Toast.makeText(this, "Archive deleted", Toast.LENGTH_SHORT).show();
                loadArchives();
            } else {
                Toast.makeText(this, "Failed to delete archive", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
        
        dialog.show();
    }
}
