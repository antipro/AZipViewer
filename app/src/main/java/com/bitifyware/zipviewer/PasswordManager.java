package com.bitifyware.zipviewer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manager for storing and retrieving archive passwords
 */
public class PasswordManager {
    private static final String PREFS_NAME = "archive_passwords";
    private SharedPreferences prefs;

    public PasswordManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Save password for an archive file
     */
    public void savePassword(String fileName, String password) {
        prefs.edit().putString(fileName, password).apply();
    }

    /**
     * Get password for an archive file
     */
    public String getPassword(String fileName) {
        return prefs.getString(fileName, null);
    }

    /**
     * Remove password for an archive file
     */
    public void removePassword(String fileName) {
        prefs.edit().remove(fileName).apply();
    }

    /**
     * Check if password exists for an archive file
     */
    public boolean hasPassword(String fileName) {
        return prefs.contains(fileName);
    }
}
