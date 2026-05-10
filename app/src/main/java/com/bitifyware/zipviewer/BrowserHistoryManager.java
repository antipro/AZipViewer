package com.bitifyware.zipviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores recent browser addresses for autocomplete suggestions.
 */
public class BrowserHistoryManager {

    private static final String PREFS_NAME = "browser_history";
    private static final String KEY_RECENT_ADDRESSES = "recent_addresses";
    private static final int MAX_RECENT_ADDRESSES = 12;

    private final SharedPreferences prefs;

    public BrowserHistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<String> getRecentAddresses() {
        List<String> addresses = new ArrayList<>();
        String rawValue = prefs.getString(KEY_RECENT_ADDRESSES, "[]");
        try {
            JSONArray jsonArray = new JSONArray(rawValue);
            for (int i = 0; i < jsonArray.length(); i++) {
                String address = jsonArray.optString(i);
                if (!TextUtils.isEmpty(address)) {
                    addresses.add(address);
                }
            }
        } catch (Exception ignored) {
            // Fall back to an empty history if the stored value cannot be parsed.
        }
        return addresses;
    }

    public void saveAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return;
        }

        List<String> addresses = getRecentAddresses();
        addresses.remove(address);
        addresses.add(0, address);

        while (addresses.size() > MAX_RECENT_ADDRESSES) {
            addresses.remove(addresses.size() - 1);
        }

        JSONArray jsonArray = new JSONArray();
        for (String item : addresses) {
            jsonArray.put(item);
        }
        prefs.edit().putString(KEY_RECENT_ADDRESSES, jsonArray.toString()).apply();
    }
}
