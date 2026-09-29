package com.almlk.swiftkey.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Dedicated per-feature switches for shortcuts, dictionaries and the clipboard.
 * Each manager screen owns its settings here; none of these keys lives in the
 * typing-settings surface. All values are read live by the engine and the
 * repositories so toggling applies without restarting the keyboard.
 */
public final class FeatureSettings {
  public static final String SHORTCUT_ENABLED = "shortcut_enabled";
  public static final String SHORTCUT_IN_STRIP = "shortcut_in_strip";
  public static final String DICTIONARY_IN_SUGGESTIONS = "dictionary_in_suggestions";
  public static final String DICTIONARY_BLOCKLIST = "dictionary_blocklist";
  public static final String CLIPBOARD_ENABLED = "clipboard_enabled";
  public static final String CLIPBOARD_MAX_ITEMS = "clipboard_max_items";
  public static final String CLIPBOARD_PURGE_DAYS = "clipboard_purge_days";

  private FeatureSettings() {}

  public static boolean enabled(Context context, String key) {
    return prefs(context).getBoolean(key, true);
  }

  public static void setEnabled(Context context, String key, boolean value) {
    prefs(context).edit().putBoolean(key, value).apply();
  }

  public static int number(Context context, String key, int fallback, int minimum, int maximum) {
    int value = prefs(context).getInt(key, fallback);
    return Math.max(minimum, Math.min(maximum, value));
  }

  public static void setNumber(Context context, String key, int value) {
    prefs(context).edit().putInt(key, value).apply();
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences("keyboard", Context.MODE_PRIVATE);
  }
}
