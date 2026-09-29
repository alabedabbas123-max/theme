package com.almlk.swiftkey.ime;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent toolbar visibility and ordering shared by the panel and IME service. */
public final class ToolPreferences {
  private static final String PREFS = "toolbar_configuration";
  private static final String ORDER = "order";
  private static final String VISIBLE = "visible";

  public static final String[] DEFAULT_ORDER = {
    "settings",
    "gestures",
    "theme",
    "rewards",
    "resize",
    "login",
    "layouts",
    "gif",
    "tips",
    "stickers",
    "languages",
    "incognito",
    "modes",
    "search",
    "voice",
    "translate",
    "clipboard"
  };

  private static final String[] DEFAULT_VISIBLE = {"voice", "translate", "clipboard", "settings"};

  private ToolPreferences() {}

  public static List<String> order(Context context) {
    String saved = prefs(context).getString(ORDER, "");
    ArrayList<String> result = new ArrayList<String>();
    if (saved != null && saved.length() > 0) {
      String[] parts = saved.split(",");
      for (String part : parts) {
        if (contains(DEFAULT_ORDER, part) && !result.contains(part)) {
          result.add(part);
        }
      }
    }
    for (String key : DEFAULT_ORDER) {
      if (!result.contains(key)) {
        result.add(key);
      }
    }
    result.remove("gestures");
    int settingsPosition = result.indexOf("settings");
    result.add(settingsPosition < 0 ? 0 : settingsPosition + 1, "gestures");
    return result;
  }

  public static void saveOrder(Context context, List<String> order) {
    StringBuilder value = new StringBuilder();
    for (String key : order) {
      if (value.length() > 0) {
        value.append(',');
      }
      value.append(key);
    }
    prefs(context).edit().putString(ORDER, value.toString()).apply();
  }

  public static boolean isVisible(Context context, String key) {
    Set<String> values = visibleSet(context);
    return values.contains(key);
  }

  public static boolean toggleVisible(Context context, String key) {
    Set<String> values = visibleSet(context);
    boolean visible;
    if (values.contains(key)) {
      values.remove(key);
      visible = false;
    } else {
      values.add(key);
      visible = true;
    }
    prefs(context).edit().putString(VISIBLE, join(values)).apply();
    return visible;
  }

  public static void reset(Context context) {
    prefs(context).edit().clear().apply();
  }

  private static Set<String> visibleSet(Context context) {
    SharedPreferences preferences = prefs(context);
    String value = preferences.getString(VISIBLE, null);
    HashSet<String> result = new HashSet<String>();
    if (value == null) {
      result.addAll(Arrays.asList(DEFAULT_VISIBLE));
    } else if (value.length() > 0) {
      result.addAll(Arrays.asList(value.split(",")));
    }
    if (!preferences.getBoolean("defaults_v2", false)) {
      result.addAll(Arrays.asList(DEFAULT_VISIBLE));
      preferences.edit().putString(VISIBLE, join(result)).putBoolean("defaults_v2", true).apply();
    }
    return result;
  }

  private static String join(Set<String> values) {
    StringBuilder result = new StringBuilder();
    for (String key : DEFAULT_ORDER) {
      if (values.contains(key)) {
        if (result.length() > 0) {
          result.append(',');
        }
        result.append(key);
      }
    }
    return result.toString();
  }

  private static boolean contains(String[] values, String key) {
    for (String value : values) {
      if (value.equals(key)) {
        return true;
      }
    }
    return false;
  }

  private static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}
