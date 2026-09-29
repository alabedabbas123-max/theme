package com.almlk.swiftkey.settings;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Small persistent store for the language-settings UI. */
final class LanguagePreferences {
  private static final String FILE = "language_settings";
  private static final String KEY = "installed_language_codes";

  static List<String> installed(Context context) {
    String saved = context.getSharedPreferences(FILE, 0).getString(KEY, "ar,en");
    ArrayList<String> result = new ArrayList<String>();
    for (String code : saved.split(","))
      if (code.length() > 0 && !result.contains(code)) result.add(code);
    if (!result.contains("ar")) result.add(0, "ar");
    if (!result.contains("en")) result.add(Math.min(1, result.size()), "en");
    return result;
  }

  static boolean contains(Context context, String code) {
    return installed(context).contains(code);
  }

  static void add(Context context, String code) {
    List<String> values = installed(context);
    if (!values.contains(code)) values.add(code);
    save(context, values);
  }

  static void remove(Context context, String code) {
    if ("ar".equals(code) || "en".equals(code)) return;
    List<String> values = installed(context);
    values.remove(code);
    save(context, values);
  }

  private static void save(Context context, List<String> values) {
    StringBuilder text = new StringBuilder();
    for (String value : values) {
      if (text.length() > 0) text.append(',');
      text.append(value);
    }
    context.getSharedPreferences(FILE, 0).edit().putString(KEY, text.toString()).apply();
  }

  private LanguagePreferences() {}
}
