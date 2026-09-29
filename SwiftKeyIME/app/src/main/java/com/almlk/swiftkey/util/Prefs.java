package com.almlk.swiftkey.util;

import android.content.Context;
import android.content.SharedPreferences;

/** Central keyboard preferences with safe ranges for feedback and popup timing. */
public final class Prefs {
  private final SharedPreferences preferences;

  public Prefs(Context context) {
    preferences = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE);
  }

  public boolean learning() {
    return preferences.getBoolean("learning", true);
  }

  public boolean autocorrect() {
    return preferences.getBoolean("autocorrect", true);
  }

  public boolean autocomplete() {
    return preferences.getBoolean("autocomplete", true);
  }

  public boolean spaceAutocomplete() {
    return preferences.getBoolean("space_autocomplete", true);
  }

  public boolean emojiSuggestions() {
    return preferences.getBoolean("emoji_suggestions_enabled", true);
  }

  public boolean vibration() {
    return preferences.getBoolean("vibration", true);
  }

  public int vibrationDuration() {
    return bounded("vibration_duration", 16, 5, 45);
  }

  public int vibrationStrength() {
    return bounded("vibration_strength", 55, 1, 255);
  }

  public boolean sound() {
    return preferences.getBoolean("sound", false);
  }

  public int soundVolume() {
    return bounded("sound_volume", 35, 5, 100);
  }

  public boolean keyPreview() {
    return preferences.getBoolean("key_popup_preview", true);
  }

  public boolean stickyAlternatives() {
    return preferences.getBoolean("alternative_popup_sticky", false);
  }

  public int popupDelay() {
    return bounded("popup_delay", 330, 220, 650);
  }

  public int deleteStartDelay() {
    return bounded("delete_start_delay", 305, 220, 500);
  }

  public int deleteRepeatInterval() {
    return bounded("delete_repeat_interval", 48, 32, 90);
  }

  public boolean gestureTyping() {
    return preferences.getBoolean("gesture_typing", true);
  }

  public boolean gestureAutoInsert() {
    return preferences.getBoolean("gesture_auto_insert", true);
  }

  public boolean gestureTrail() {
    return preferences.getBoolean("gesture_trail", true);
  }

  public int gestureStartDistance() {
    return bounded("gesture_start_distance", 12, 6, 24);
  }

  public int gestureTrailWidth() {
    return bounded("gesture_trail_width", 6, 3, 11);
  }

  public String theme() {
    // Round 41: the out-of-the-box theme is the skinned white family; celebrity ids
    // (removed from the gallery) normalize to their closest new theme.
    String value = preferences.getString("theme", "white_pure");
    if (value.startsWith("star_")) {
      if ("star_football".equals(value)) return "black_graphite";
      if ("star_singer".equals(value)) return "girly_orchid";
      if ("star_idol".equals(value)) return "girly_fuchsia";
      if ("star_actress".equals(value)) return "sunset_palms";
      if ("star_rap".equals(value)) return "blue_navy";
      return "purple_waves"; // star_dance
    }
    if ("light".equals(value)) return "samsung_white";
    if ("blue".equals(value) || "ocean".equals(value)) return "samsung_blue";
    if ("dark".equals(value) || "midnight".equals(value)) return "samsung_black";
    if ("amoled".equals(value)) return "amoled_black";
    return value;
  }

  public void setTheme(String value) {
    preferences.edit().putString("theme", value).apply();
  }

  public String voiceLanguage() {
    return preferences.getString("voice_language", "auto");
  }

  public void setVoiceLanguage(String value) {
    preferences.edit().putString("voice_language", value).apply();
  }

  public int fontOverride() {
    return preferences.getInt("key_font_override", -1);
  }

  /** Number-row digits: 0 western, 1 arabic, 2 automatic from the layout. */
  public int digitType() {
    int value = preferences.getInt("number_digit_type", 2);
    return value >= 0 && value <= 2 ? value : 2;
  }

  public void setDigitType(int value) {
    preferences.edit().putInt("number_digit_type", value).apply();
  }

  public void set(String key, boolean value) {
    preferences.edit().putBoolean(key, value).apply();
  }

  public void setInt(String key, int value) {
    preferences.edit().putInt(key, value).apply();
  }

  private int bounded(String key, int fallback, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, preferences.getInt(key, fallback)));
  }
}
