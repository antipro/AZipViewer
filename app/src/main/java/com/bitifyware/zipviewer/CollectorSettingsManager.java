package com.bitifyware.zipviewer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Stores image collector filters for the embedded browser.
 */
public class CollectorSettingsManager {

    private static final String PREFS_NAME = "collector_settings";
    private static final String KEY_MIN_WIDTH = "min_width";
    private static final String KEY_MIN_HEIGHT = "min_height";
    private static final String KEY_ENABLED_TYPES = "enabled_types";

    private final SharedPreferences prefs;

    public CollectorSettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getMinWidth() {
        return Math.max(0, prefs.getInt(KEY_MIN_WIDTH, 0));
    }

    public int getMinHeight() {
        return Math.max(0, prefs.getInt(KEY_MIN_HEIGHT, 0));
    }

    public Set<String> getEnabledTypes() {
        Set<String> storedTypes = prefs.getStringSet(KEY_ENABLED_TYPES, null);
        LinkedHashSet<String> normalizedTypes = new LinkedHashSet<>();

        if (storedTypes == null || storedTypes.isEmpty()) {
            normalizedTypes.addAll(getDefaultTypes());
            return normalizedTypes;
        }

        for (String type : storedTypes) {
            String normalizedType = normalizeType(type);
            if (!normalizedType.isEmpty()) {
                normalizedTypes.add(normalizedType);
            }
        }

        if (normalizedTypes.isEmpty()) {
            normalizedTypes.addAll(getDefaultTypes());
        }

        return normalizedTypes;
    }

    public void saveSettings(int minWidth, int minHeight, Set<String> enabledTypes) {
        LinkedHashSet<String> normalizedTypes = new LinkedHashSet<>();
        for (String type : enabledTypes) {
            String normalizedType = normalizeType(type);
            if (!normalizedType.isEmpty()) {
                normalizedTypes.add(normalizedType);
            }
        }

        if (normalizedTypes.isEmpty()) {
            normalizedTypes.addAll(getDefaultTypes());
        }

        prefs.edit()
                .putInt(KEY_MIN_WIDTH, Math.max(0, minWidth))
                .putInt(KEY_MIN_HEIGHT, Math.max(0, minHeight))
                .putStringSet(KEY_ENABLED_TYPES, normalizedTypes)
                .apply();
    }

    public boolean matchesMinimums(int width, int height) {
        return width >= getMinWidth() && height >= getMinHeight();
    }

    public boolean isAllowedExtension(String extension) {
        String normalizedType = normalizeType(extension);
        return !normalizedType.isEmpty() && getEnabledTypes().contains(normalizedType);
    }

    public String normalizeType(String type) {
        if (type == null) {
            return "";
        }

        String normalized = type.trim().toLowerCase(Locale.US);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        int slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
            normalized = normalized.substring(slashIndex + 1);
        }
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex > 0) {
            normalized = normalized.substring(0, semicolonIndex);
        }
        if ("jpeg".equals(normalized) || "jpg_large".equals(normalized)) {
            return "jpg";
        }
        return normalized;
    }

    public String getFilterSummary() {
        String sizeSummary;
        if (getMinWidth() == 0 && getMinHeight() == 0) {
            sizeSummary = "Any size";
        } else {
            sizeSummary = String.format(
                    Locale.US,
                    "Min %dx%d",
                    getMinWidth(),
                    getMinHeight()
            );
        }
        return sizeSummary + " • " + getEnabledTypesLabel();
    }

    public String getEnabledTypesLabel() {
        StringBuilder labelBuilder = new StringBuilder();
        for (String type : getEnabledTypes()) {
            if (labelBuilder.length() > 0) {
                labelBuilder.append(", ");
            }
            labelBuilder.append(type.toUpperCase(Locale.US));
        }
        return labelBuilder.toString();
    }

    private Set<String> getDefaultTypes() {
        return new LinkedHashSet<>(Arrays.asList("jpg", "png"));
    }
}
