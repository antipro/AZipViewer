package com.bitifyware.zipviewer;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main activity for ZipViewer - Private Archive Viewer
 * Features:
 * - Privacy First: All data stored in internal storage
 * - Encrypted Archives: Support for password-protected ZIP files
 * - Grid and List views
 * - Zip Editor: Can be called by other apps to open ZIP files
 */
public class MainActivity extends AppCompatActivity implements ArchiveAdapter.OnArchiveClickListener {

    private static final String DEFAULT_BROWSER_URL = "https://www.google.com";
    private static final int IMAGE_DOWNLOAD_RETRY_COUNT = 2;
    private static final String COLLECT_IMAGES_SCRIPT = """
            (() => {
              const results = [];
              const seen = new Set();
              const backgroundPattern = /url\\((['"]?)(.*?)\\1\\)/g;
              const toAbsolute = (value) => {
                if (!value) {
                  return '';
                }
                try {
                  return new URL(String(value), document.baseURI).href;
                } catch (error) {
                  return String(value);
                }
              };
              const addImage = (value, width, height) => {
                const src = toAbsolute(value).trim();
                if (!src || seen.has(src)) {
                  return;
                }
                seen.add(src);
                results.push({
                  src,
                  width: Number(width) || 0,
                  height: Number(height) || 0
                });
              };
              const addFromSrcSet = (value, width, height) => {
                if (!value) {
                  return;
                }
                value.split(',').forEach((part) => {
                  addImage(part.trim().split(' ')[0], width, height);
                });
              };
              const addFromBackground = (styleValue) => {
                if (!styleValue) {
                  return;
                }
                let match;
                while ((match = backgroundPattern.exec(styleValue)) !== null) {
                  addImage(match[2], 0, 0);
                }
                backgroundPattern.lastIndex = 0;
              };
              Array.from(document.images || []).forEach((image) => {
                addImage(
                  image.currentSrc
                    || image.src
                    || image.getAttribute('data-src')
                    || image.getAttribute('data-original')
                    || image.getAttribute('data-lazy-src')
                    || image.getAttribute('data-url')
                    || image.getAttribute('src'),
                  image.naturalWidth || image.width,
                  image.naturalHeight || image.height
                );
                addFromSrcSet(
                  image.getAttribute('srcset') || image.getAttribute('data-srcset'),
                  image.naturalWidth || image.width,
                  image.naturalHeight || image.height
                );
              });
              Array.from(document.querySelectorAll('[data-src], [data-original], [data-lazy-src], [data-url], [poster], [style]') || []).forEach((element) => {
                addImage(
                  element.getAttribute('data-src')
                    || element.getAttribute('data-original')
                    || element.getAttribute('data-lazy-src')
                    || element.getAttribute('data-url')
                    || element.getAttribute('poster'),
                  element.clientWidth,
                  element.clientHeight
                );
                addFromSrcSet(
                  element.getAttribute('data-srcset'),
                  element.clientWidth,
                  element.clientHeight
                );
                addFromBackground(element.getAttribute('style'));
                addFromBackground(getComputedStyle(element).backgroundImage);
              });
              return results;
            })();
            """;

    private RecyclerView recyclerView;
    private FloatingActionButton fabAdd;
    private FloatingActionButton fabCollector;
    private EditText searchBar;
    private AutoCompleteTextView addressBar;
    private ArchiveAdapter archiveAdapter;
    private List<ArchiveItem> archives;
    private List<ArchiveItem> filteredArchives;
    private BrowserHistoryManager browserHistoryManager;
    private PasswordManager passwordManager;
    private CollectorSettingsManager collectorSettingsManager;
    private ActivityResultLauncher<String[]> filePickerLauncher;
    private LinearLayout archiveContainer;
    private LinearLayout browserContainer;
    private TextView topTitle;
    private TextView topSubtitle;
    private ImageButton btnModeSwitch;
    private ImageButton btnBrowserSettings;
    private ImageButton btnBrowserBack;
    private ImageButton btnBrowserRefresh;
    private ImageButton btnBrowserGo;
    private WebView webView;
    private ArrayAdapter<String> addressSuggestionsAdapter;
    private boolean browserMode;
    private boolean isCollecting;
    private int collectorSessionId;
    private final Object collectorLock = new Object();
    private final LinkedHashMap<String, CollectedImage> collectedImages = new LinkedHashMap<>();
    private final ExecutorService collectorDownloadExecutor = Executors.newFixedThreadPool(3);

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
        fabCollector = findViewById(R.id.fabCollector);
        searchBar = findViewById(R.id.searchBar);
        addressBar = findViewById(R.id.addressBar);
        archiveContainer = findViewById(R.id.archiveContainer);
        browserContainer = findViewById(R.id.browserContainer);
        topTitle = findViewById(R.id.topTitle);
        topSubtitle = findViewById(R.id.topSubtitle);
        btnModeSwitch = findViewById(R.id.btnModeSwitch);
        btnBrowserSettings = findViewById(R.id.btnBrowserSettings);
        btnBrowserBack = findViewById(R.id.btnBrowserBack);
        btnBrowserRefresh = findViewById(R.id.btnBrowserRefresh);
        btnBrowserGo = findViewById(R.id.btnBrowserGo);
        webView = findViewById(R.id.webView);

        archives = new ArrayList<>();
        filteredArchives = new ArrayList<>();
        browserHistoryManager = new BrowserHistoryManager(this);
        passwordManager = new PasswordManager(this);
        collectorSettingsManager = new CollectorSettingsManager(this);

        archiveAdapter = new ArchiveAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(archiveAdapter);
        addressSuggestionsAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>(browserHistoryManager.getRecentAddresses())
        );
        addressBar.setAdapter(addressSuggestionsAdapter);
        addressBar.setThreshold(1);

        fabAdd.setOnClickListener(v -> {
            // Launch file picker for archive files
            filePickerLauncher.launch(new String[]{
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/x-rar-compressed",
                    "application/x-7z-compressed"
            });
        });

        btnModeSwitch.setOnClickListener(v -> toggleViewMode());
        btnBrowserSettings.setOnClickListener(v ->
                startActivity(new Intent(this, CollectorSettingsActivity.class))
        );
        btnBrowserBack.setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                Toast.makeText(this, R.string.browser_back, Toast.LENGTH_SHORT).show();
            }
        });
        btnBrowserRefresh.setOnClickListener(v -> webView.reload());
        btnBrowserGo.setOnClickListener(v -> loadUrlFromAddressBar());
        addressBar.setOnClickListener(v -> addressBar.showDropDown());
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            boolean shouldLoad = actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (shouldLoad) {
                loadUrlFromAddressBar();
                return true;
            }
            return false;
        });
        fabCollector.setOnClickListener(v -> {
            if (isCollecting) {
                stopCollectionAndPrompt();
            } else {
                startCollection();
            }
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

        configureWebView();
        setupBackHandling();
        updateModeUi();

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
        updateModeUi();
    }

    @Override
    protected void onDestroy() {
        clearCollectorTempFiles();
        collectorDownloadExecutor.shutdownNow();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
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

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!addressBar.hasFocus()) {
                    addressBar.setText(url, false);
                }
                saveRecentAddress(url);
                updateModeUi();
                if (isCollecting) {
                    scheduleCollectionForLoadedPage(url);
                }
            }
        });

        webView.loadUrl(DEFAULT_BROWSER_URL);
        addressBar.setText(DEFAULT_BROWSER_URL, false);
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (browserMode && webView.canGoBack()) {
                    webView.goBack();
                } else if (browserMode) {
                    browserMode = false;
                    updateModeUi();
                } else {
                    finish();
                }
            }
        });
    }

    private void toggleViewMode() {
        if (browserMode && isCollecting) {
            isCollecting = false;
            updateCollectorFab();
        }

        browserMode = !browserMode;
        if (browserMode && TextUtils.isEmpty(webView.getUrl())) {
            webView.loadUrl(DEFAULT_BROWSER_URL);
        }
        updateModeUi();
    }

    private void updateModeUi() {
        archiveContainer.setVisibility(browserMode ? View.GONE : View.VISIBLE);
        browserContainer.setVisibility(browserMode ? View.VISIBLE : View.GONE);
        fabCollector.setVisibility(browserMode ? View.VISIBLE : View.GONE);
        fabAdd.setVisibility(browserMode ? View.GONE : View.VISIBLE);
        btnBrowserSettings.setVisibility(browserMode ? View.VISIBLE : View.GONE);

        if (browserMode) {
            btnModeSwitch.setImageResource(android.R.drawable.ic_menu_agenda);
            btnModeSwitch.setContentDescription(getString(R.string.open_archive_view));
            topTitle.setText(R.string.browser_mode_title);
            topSubtitle.setText(buildBrowserSubtitle());
        } else {
            btnModeSwitch.setImageResource(android.R.drawable.ic_menu_compass);
            btnModeSwitch.setContentDescription(getString(R.string.open_browser_view));
            topTitle.setText(R.string.app_name);
            topSubtitle.setText(R.string.private_gallery);
        }
        updateCollectorFab();
    }

    private void updateCollectorFab() {
        fabCollector.setImageResource(isCollecting
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_menu_save);
        fabCollector.setBackgroundTintList(ContextCompat.getColorStateList(
                this,
                isCollecting ? R.color.accent_blue : R.color.accent_purple
        ));
        fabCollector.setContentDescription(getString(
                isCollecting ? R.string.stop_collecting : R.string.start_collecting
        ));
    }

    private String buildBrowserSubtitle() {
        String summary = collectorSettingsManager.getFilterSummary();
        if (isCollecting) {
            return getString(R.string.stop_collecting) + " • " + getCollectedImageCount() + " • " + summary;
        }
        String pageTitle = webView.getTitle();
        if (!TextUtils.isEmpty(pageTitle)) {
            return pageTitle + " • " + summary;
        }
        return summary;
    }

    private void loadUrlFromAddressBar() {
        String address = addressBar.getText().toString().trim();
        if (address.isEmpty()) {
            return;
        }
        String normalizedUrl = normalizeUrl(address);
        addressBar.setText(normalizedUrl, false);
        addressBar.clearFocus();
        saveRecentAddress(normalizedUrl);
        hideKeyboard();
        webView.loadUrl(normalizedUrl);
    }

    private void saveRecentAddress(String address) {
        browserHistoryManager.saveAddress(address);
        addressSuggestionsAdapter.clear();
        addressSuggestionsAdapter.addAll(browserHistoryManager.getRecentAddresses());
        addressSuggestionsAdapter.notifyDataSetChanged();
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl.startsWith("http://")
                || rawUrl.startsWith("https://")
                || rawUrl.startsWith("about:")
                || rawUrl.startsWith("file:")
                || rawUrl.startsWith("data:")) {
            return rawUrl;
        }
        return "https://" + rawUrl;
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void startCollection() {
        resetCollectorState();
        isCollecting = true;
        updateModeUi();
        Toast.makeText(this, R.string.collecting_started, Toast.LENGTH_SHORT).show();
        collectImagesFromPage(true);
    }

    private void stopCollectionAndPrompt() {
        isCollecting = false;
        updateModeUi();

        if (getCollectedImageCount() == 0) {
            Toast.makeText(this, R.string.collecting_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        showCreateArchiveDialog();
    }

    private void scheduleCollectionForLoadedPage(String loadedUrl) {
        collectImagesFromPage(false);
        webView.postDelayed(() -> {
            if (isCollecting && TextUtils.equals(loadedUrl, webView.getUrl())) {
                collectImagesFromPage(false);
            }
        }, 800);
    }

    private void collectImagesFromPage(boolean showFeedback) {
        String currentUrl = webView.getUrl();
        if (TextUtils.isEmpty(currentUrl)) {
            if (showFeedback) {
                Toast.makeText(this, R.string.collecting_empty, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        DownloadContext downloadContext = buildDownloadContext();
        webView.evaluateJavascript(COLLECT_IMAGES_SCRIPT, value -> {
            int addedCount = consumeCollectedImages(value, currentUrl, downloadContext);
            topSubtitle.setText(buildBrowserSubtitle());
            if (showFeedback) {
                Toast.makeText(
                        this,
                        getString(R.string.collecting_result, addedCount),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private int consumeCollectedImages(String rawValue, String pageUrl, DownloadContext downloadContext) {
        try {
            Object parsedValue = new JSONTokener(rawValue).nextValue();
            JSONArray jsonArray;
            if (parsedValue instanceof JSONArray) {
                jsonArray = (JSONArray) parsedValue;
            } else if (parsedValue instanceof String) {
                jsonArray = new JSONArray((String) parsedValue);
            } else {
                return 0;
            }

            int addedCount = 0;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject imageJson = jsonArray.optJSONObject(i);
                if (imageJson == null) {
                    continue;
                }

                String sourceUrl = imageJson.optString("src");
                int width = imageJson.optInt("width", 0);
                int height = imageJson.optInt("height", 0);
                if (TextUtils.isEmpty(sourceUrl)) {
                    continue;
                }
                if (!collectorSettingsManager.matchesMinimums(width, height)) {
                    continue;
                }
                CollectedImage imageToCache = null;
                synchronized (collectorLock) {
                    CollectedImage existingImage = collectedImages.get(sourceUrl);
                    if (existingImage == null) {
                        imageToCache = new CollectedImage(sourceUrl, pageUrl, width, height, collectorSessionId);
                        collectedImages.put(sourceUrl, imageToCache);
                    } else if (!existingImage.hasCachedFile() && !existingImage.cacheInProgress) {
                        imageToCache = existingImage;
                    }
                }
                if (imageToCache != null) {
                    cacheCollectedImageAsync(imageToCache, downloadContext);
                    addedCount++;
                }
            }
            return addedCount;
        } catch (Exception e) {
            return 0;
        }
    }

    private void showCreateArchiveDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_collect_archive, null);
        EditText fileNameInput = dialogView.findViewById(R.id.fileNameInput);
        EditText passwordInput = dialogView.findViewById(R.id.passwordInput);

        fileNameInput.setText(buildSuggestedArchiveName());
        fileNameInput.setSelection(fileNameInput.getText().length());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnCreate).setOnClickListener(v -> {
            String requestedFileName = ensureZipExtension(fileNameInput.getText().toString().trim());
            String password = passwordInput.getText().toString();

            if (TextUtils.isEmpty(requestedFileName) || ".zip".equalsIgnoreCase(requestedFileName)) {
                Toast.makeText(this, R.string.archive_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(password)) {
                Toast.makeText(this, R.string.archive_password_required, Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            createArchiveFromCollectedImages(requestedFileName, password);
        });

        dialog.show();
    }

    private String buildSuggestedArchiveName() {
        String pageTitle = webView.getTitle();
        if (TextUtils.isEmpty(pageTitle)) {
            pageTitle = getString(R.string.collector_archive_default_name);
        }
        return sanitizeArchiveFileName(pageTitle);
    }

    private void createArchiveFromCollectedImages(String requestedFileName, String password) {
        Toast.makeText(this, R.string.collecting_generating, Toast.LENGTH_SHORT).show();
        DownloadContext downloadContext = buildDownloadContext();
        List<CollectedImage> imagesToArchive = getCollectedImagesSnapshot();

        new Thread(() -> {
            File archiveDirectory = new File(getFilesDir(), "archives");
            if (!archiveDirectory.exists()) {
                archiveDirectory.mkdirs();
            }

            String uniqueName = generateUniqueFileName(sanitizeArchiveFileName(requestedFileName));
            File archiveFile = new File(archiveDirectory, uniqueName);
            int addedCount = 0;
            int skippedCount = 0;

            try {
                ZipFile zipFile = new ZipFile(archiveFile, password.toCharArray());
                Set<String> usedEntryNames = new HashSet<>();

                for (CollectedImage image : imagesToArchive) {
                    CachedCollectedImage cachedImage = ensureCachedImage(image, downloadContext);
                    if (cachedImage == null) {
                        skippedCount++;
                        continue;
                    }

                    ZipParameters zipParameters = new ZipParameters();
                    zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
                    zipParameters.setEncryptFiles(true);
                    zipParameters.setEncryptionMethod(EncryptionMethod.AES);
                    zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                    zipParameters.setFileNameInZip(
                            makeUniqueEntryName(cachedImage.fileName, usedEntryNames)
                    );
                    zipFile.addFile(cachedImage.file, zipParameters);
                    addedCount++;
                }

                if (addedCount == 0) {
                    archiveFile.delete();
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.collector_no_downloads, Toast.LENGTH_LONG).show()
                    );
                    return;
                }

                passwordManager.savePassword(archiveFile.getName(), password);
                int finalAddedCount = addedCount;
                int finalSkippedCount = skippedCount;
                runOnUiThread(() -> {
                    loadArchives();
                    Toast.makeText(
                            this,
                            finalSkippedCount == 0
                                    ? getString(R.string.collector_archive_created, finalAddedCount, archiveFile.getName())
                                    : getString(R.string.collector_archive_partial, finalAddedCount, finalSkippedCount),
                            Toast.LENGTH_LONG
                    ).show();
                    openGallery(archiveFile, password);
                });
            } catch (Exception e) {
                archiveFile.delete();
                runOnUiThread(() -> Toast.makeText(
                        this,
                        getString(R.string.collector_archive_failed, e.getMessage()),
                        Toast.LENGTH_LONG
                ).show());
            } finally {
                synchronized (collectorLock) {
                    collectorSessionId++;
                }
                clearCollectorTempFiles();
            }
        }).start();
    }

    private DownloadContext buildDownloadContext() {
        String userAgent = webView.getSettings().getUserAgentString();
        LinkedHashMap<String, String> cookiesByPageUrl = new LinkedHashMap<>();

        for (CollectedImage image : getCollectedImagesSnapshot()) {
            if (!cookiesByPageUrl.containsKey(image.pageUrl)) {
                cookiesByPageUrl.put(
                        image.pageUrl,
                        CookieManager.getInstance().getCookie(image.pageUrl)
                );
            }
        }

        return new DownloadContext(userAgent, cookiesByPageUrl);
    }

    private void cacheCollectedImageAsync(CollectedImage image, DownloadContext downloadContext) {
        synchronized (collectorLock) {
            if (image.cacheInProgress || image.hasCachedFile()) {
                return;
            }
            image.cacheInProgress = true;
        }
        collectorDownloadExecutor.execute(() -> cacheCollectedImageInternal(image, downloadContext));
    }

    private CachedCollectedImage ensureCachedImage(CollectedImage image, DownloadContext downloadContext) {
        synchronized (collectorLock) {
            if (image.hasCachedFile()) {
                return new CachedCollectedImage(image.cachedFile, image.cachedEntryName);
            }
        }
        return cacheCollectedImageInternal(image, downloadContext);
    }

    private CachedCollectedImage cacheCollectedImageInternal(CollectedImage image, DownloadContext downloadContext) {
        synchronized (collectorLock) {
            if (image.hasCachedFile()) {
                image.cacheInProgress = false;
                return new CachedCollectedImage(image.cachedFile, image.cachedEntryName);
            }
            image.cacheInProgress = true;
        }

        DownloadedImage downloadedImage = downloadImageWithRetries(image, downloadContext);
        if (downloadedImage == null) {
            synchronized (collectorLock) {
                image.cacheInProgress = false;
            }
            return null;
        }

        try {
            File tempFile = writeTempImageFile(downloadedImage, image.sessionId);
            synchronized (collectorLock) {
                if (image.sessionId != collectorSessionId) {
                    tempFile.delete();
                    image.cacheInProgress = false;
                    return null;
                }
                if (image.cachedFile != null && image.cachedFile.exists()) {
                    image.cachedFile.delete();
                }
                image.cachedFile = tempFile;
                image.cachedEntryName = downloadedImage.fileName;
                image.cacheInProgress = false;
                return new CachedCollectedImage(tempFile, downloadedImage.fileName);
            }
        } catch (Exception e) {
            synchronized (collectorLock) {
                image.cacheInProgress = false;
            }
            return null;
        }
    }

    private DownloadedImage downloadImageWithRetries(CollectedImage image, DownloadContext downloadContext) {
        DownloadedImage downloadedImage = null;
        for (int attempt = 0; attempt < IMAGE_DOWNLOAD_RETRY_COUNT && downloadedImage == null; attempt++) {
            downloadedImage = downloadImage(image, downloadContext);
        }
        return downloadedImage;
    }

    private DownloadedImage downloadImage(CollectedImage image, DownloadContext downloadContext) {
        try {
            if (image.sourceUrl.startsWith("data:image/")) {
                return downloadDataImage(image);
            }

            URL url = new URL(image.sourceUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", downloadContext.userAgent);
            connection.setRequestProperty("Referer", image.pageUrl);
            String cookies = downloadContext.cookiesByPageUrl.get(image.pageUrl);
            if (!TextUtils.isEmpty(cookies)) {
                connection.setRequestProperty("Cookie", cookies);
            }

            try {
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    return null;
                }

                byte[] data;
                try (InputStream inputStream = connection.getInputStream()) {
                    data = readAllBytes(inputStream);
                }

                String extension = resolveImageExtension(image.sourceUrl, connection.getContentType());
                if (!collectorSettingsManager.isAllowedExtension(extension)
                        || !matchesDownloadedSize(data, image.width, image.height)) {
                    return null;
                }

                return new DownloadedImage(
                        buildEntryFileName(image.sourceUrl, extension),
                        data
                );
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private DownloadedImage downloadDataImage(CollectedImage image) {
        try {
            int separatorIndex = image.sourceUrl.indexOf(',');
            if (separatorIndex <= 0) {
                return null;
            }

            String header = image.sourceUrl.substring(0, separatorIndex);
            String payload = image.sourceUrl.substring(separatorIndex + 1);
            String extension = resolveImageExtension(image.sourceUrl, header);
            if (!collectorSettingsManager.isAllowedExtension(extension)) {
                return null;
            }

            byte[] data;
            if (header.contains(";base64")) {
                data = android.util.Base64.decode(payload, android.util.Base64.DEFAULT);
            } else {
                data = Uri.decode(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }

            if (!matchesDownloadedSize(data, image.width, image.height)) {
                return null;
            }

            return new DownloadedImage(
                    buildEntryFileName(image.sourceUrl, extension),
                    data
            );
        } catch (Exception e) {
            return null;
        }
    }

    private boolean matchesDownloadedSize(byte[] data, int fallbackWidth, int fallbackHeight) {
        if (collectorSettingsManager.getMinWidth() == 0 && collectorSettingsManager.getMinHeight() == 0) {
            return true;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        int width = options.outWidth > 0 ? options.outWidth : fallbackWidth;
        int height = options.outHeight > 0 ? options.outHeight : fallbackHeight;
        return collectorSettingsManager.matchesMinimums(width, height);
    }

    private String resolveImageExtension(String sourceUrl, String contentTypeHint) {
        String normalizedHint = collectorSettingsManager.normalizeType(contentTypeHint);
        if (!TextUtils.isEmpty(normalizedHint) && !"image".equals(normalizedHint)) {
            return normalizedHint;
        }

        Uri uri = Uri.parse(sourceUrl);
        String lastPathSegment = uri.getLastPathSegment();
        if (!TextUtils.isEmpty(lastPathSegment) && lastPathSegment.contains(".")) {
            return collectorSettingsManager.normalizeType(
                    lastPathSegment.substring(lastPathSegment.lastIndexOf('.') + 1)
            );
        }
        return "jpg";
    }

    private String buildEntryFileName(String sourceUrl, String extension) {
        Uri uri = Uri.parse(sourceUrl);
        String lastPathSegment = uri.getLastPathSegment();
        String candidateName = sanitizeFileName(lastPathSegment);

        if (TextUtils.isEmpty(candidateName)) {
            candidateName = "image_" + Integer.toHexString(sourceUrl.hashCode()) + "." + extension;
        } else if (!candidateName.toLowerCase(Locale.US).endsWith("." + extension)) {
            int dotIndex = candidateName.lastIndexOf('.');
            if (dotIndex > 0) {
                candidateName = candidateName.substring(0, dotIndex);
            }
            candidateName = candidateName + "." + extension;
        }

        return candidateName;
    }

    private File writeTempImageFile(DownloadedImage downloadedImage, int sessionId) throws Exception {
        File tempDir = getCollectorTempDir();
        int dotIndex = downloadedImage.fileName.lastIndexOf('.');
        String suffix = dotIndex >= 0 ? downloadedImage.fileName.substring(dotIndex) : ".img";
        File tempFile = File.createTempFile("collector_" + sessionId + "_", suffix, tempDir);
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            outputStream.write(downloadedImage.data);
        }
        return tempFile;
    }

    private File getCollectorTempDir() {
        File tempDir = new File(getCacheDir(), "collector_images");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        return tempDir;
    }

    private void clearCollectorTempFiles() {
        File tempDir = new File(getCacheDir(), "collector_images");
        File[] files = tempDir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                file.delete();
            }
        }
    }

    private void resetCollectorState() {
        synchronized (collectorLock) {
            collectorSessionId++;
            collectedImages.clear();
        }
        clearCollectorTempFiles();
    }

    private int getCollectedImageCount() {
        synchronized (collectorLock) {
            return collectedImages.size();
        }
    }

    private List<CollectedImage> getCollectedImagesSnapshot() {
        synchronized (collectorLock) {
            return new ArrayList<>(collectedImages.values());
        }
    }

    private String sanitizeArchiveFileName(String fileName) {
        String sanitized = sanitizeFileName(fileName);
        if (TextUtils.isEmpty(sanitized)) {
            return getString(R.string.collector_archive_default_name) + ".zip";
        }
        return ensureZipExtension(sanitized);
    }

    private String ensureZipExtension(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return fileName;
        }
        if (fileName.toLowerCase(Locale.US).endsWith(".zip")) {
            return fileName;
        }
        return fileName + ".zip";
    }

    private String sanitizeFileName(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        sanitized = sanitized.replace(' ', '_');
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        return sanitized;
    }

    private String makeUniqueEntryName(String fileName, Set<String> usedNames) {
        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        String candidate = fileName;
        int counter = 1;
        while (usedNames.contains(candidate)) {
            candidate = baseName + "_" + counter + extension;
            counter++;
        }
        usedNames.add(candidate);
        return candidate;
    }

    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(data)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
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
        fileName = sanitizeArchiveFileName(new File(fileName).getName());
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

    private static class CollectedImage {
        private final String sourceUrl;
        private final String pageUrl;
        private final int width;
        private final int height;
        private final int sessionId;
        private File cachedFile;
        private String cachedEntryName;
        private boolean cacheInProgress;

        private CollectedImage(String sourceUrl, String pageUrl, int width, int height, int sessionId) {
            this.sourceUrl = sourceUrl;
            this.pageUrl = pageUrl;
            this.width = width;
            this.height = height;
            this.sessionId = sessionId;
        }

        private boolean hasCachedFile() {
            return cachedFile != null && cachedFile.exists() && !TextUtils.isEmpty(cachedEntryName);
        }
    }

    private static class DownloadedImage {
        private final String fileName;
        private final byte[] data;

        private DownloadedImage(String fileName, byte[] data) {
            this.fileName = fileName;
            this.data = data;
        }
    }

    private static class CachedCollectedImage {
        private final File file;
        private final String fileName;

        private CachedCollectedImage(File file, String fileName) {
            this.file = file;
            this.fileName = fileName;
        }
    }

    private static class DownloadContext {
        private final String userAgent;
        private final LinkedHashMap<String, String> cookiesByPageUrl;

        private DownloadContext(String userAgent, LinkedHashMap<String, String> cookiesByPageUrl) {
            this.userAgent = userAgent;
            this.cookiesByPageUrl = cookiesByPageUrl;
        }
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
