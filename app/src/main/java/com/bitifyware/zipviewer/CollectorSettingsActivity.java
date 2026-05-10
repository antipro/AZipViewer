package com.bitifyware.zipviewer;

import android.os.Bundle;
import android.view.WindowManager;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Settings screen for the browser image collector.
 */
public class CollectorSettingsActivity extends AppCompatActivity {

    private CollectorSettingsManager settingsManager;
    private EditText minWidthInput;
    private EditText minHeightInput;
    private CheckBox typeJpg;
    private CheckBox typePng;
    private CheckBox typeWebp;
    private CheckBox typeGif;
    private CheckBox typeBmp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collector_settings);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        settingsManager = new CollectorSettingsManager(this);

        minWidthInput = findViewById(R.id.minWidthInput);
        minHeightInput = findViewById(R.id.minHeightInput);
        typeJpg = findViewById(R.id.typeJpg);
        typePng = findViewById(R.id.typePng);
        typeWebp = findViewById(R.id.typeWebp);
        typeGif = findViewById(R.id.typeGif);
        typeBmp = findViewById(R.id.typeBmp);
        ImageButton btnBack = findViewById(R.id.btnBack);
        Button btnSave = findViewById(R.id.btnSave);

        loadSettings();

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        minWidthInput.setText(String.valueOf(settingsManager.getMinWidth()));
        minHeightInput.setText(String.valueOf(settingsManager.getMinHeight()));

        Set<String> enabledTypes = settingsManager.getEnabledTypes();
        typeJpg.setChecked(enabledTypes.contains("jpg"));
        typePng.setChecked(enabledTypes.contains("png"));
        typeWebp.setChecked(enabledTypes.contains("webp"));
        typeGif.setChecked(enabledTypes.contains("gif"));
        typeBmp.setChecked(enabledTypes.contains("bmp"));
    }

    private void saveSettings() {
        int minWidth = parseDimension(minWidthInput);
        int minHeight = parseDimension(minHeightInput);

        if (minWidth < 0 || minHeight < 0) {
            Toast.makeText(this, R.string.collector_settings_invalid_size, Toast.LENGTH_SHORT).show();
            return;
        }

        LinkedHashSet<String> enabledTypes = new LinkedHashSet<>();
        if (typeJpg.isChecked()) {
            enabledTypes.add("jpg");
        }
        if (typePng.isChecked()) {
            enabledTypes.add("png");
        }
        if (typeWebp.isChecked()) {
            enabledTypes.add("webp");
        }
        if (typeGif.isChecked()) {
            enabledTypes.add("gif");
        }
        if (typeBmp.isChecked()) {
            enabledTypes.add("bmp");
        }

        if (enabledTypes.isEmpty()) {
            Toast.makeText(this, R.string.collector_settings_choose_type, Toast.LENGTH_SHORT).show();
            return;
        }

        settingsManager.saveSettings(minWidth, minHeight, enabledTypes);
        Toast.makeText(this, R.string.collector_settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private int parseDimension(EditText input) {
        String rawValue = input.getText().toString().trim();
        if (TextUtils.isEmpty(rawValue)) {
            return 0;
        }

        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
