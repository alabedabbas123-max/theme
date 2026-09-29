package com.almlk.swiftkey.data;

import android.content.Context;
import android.content.SharedPreferences;
import com.almlk.swiftkey.engine.TextNormalizer;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small local text-expansion store. Nothing is synchronized outside the device. */
public final class ShortcutRepository {
  public static final class Item {
    public final String shortcut;
    public final String expansion;
    public final String language;

    public Item(String key, String value, String lang) {
      shortcut = key;
      expansion = value;
      language = lang;
    }
  }

  private final SharedPreferences preferences;

  public ShortcutRepository(Context context) {
    preferences = context.getSharedPreferences("typing_shortcuts", Context.MODE_PRIVATE);
  }

  /** Manual order as stored: the manager screen persists user reordering, positions are free. */
  public synchronized List<Item> all() {
    return read();
  }

  /** Persist a manual card order; unknown keys keep their relative positions at the end. */
  public synchronized void reorder(List<Item> ordered) {
    if (ordered == null) return;
    ArrayList<Item> current = read();
    ArrayList<Item> output = new ArrayList<Item>();
    for (Item wanted : ordered) {
      for (Item have : current) {
        if (have.language.equals(wanted.language) && have.shortcut.equals(wanted.shortcut)) {
          if (!contains(output, have)) output.add(have);
          break;
        }
      }
    }
    for (Item have : current) {
      if (!contains(output, have)) output.add(have);
    }
    write(output);
  }

  public synchronized void clear() {
    preferences.edit().remove("items_json").apply();
  }

  private boolean contains(List<Item> items, Item probe) {
    for (Item item : items) {
      if (item.language.equals(probe.language) && item.shortcut.equals(probe.shortcut)) {
        return true;
      }
    }
    return false;
  }

  public synchronized void save(
      String oldShortcut, String oldLanguage, String shortcut, String expansion, String language) {
    String cleanKey = TextNormalizer.clean(shortcut);
    String cleanExpansion = expansion == null ? "" : expansion.trim();
    if (cleanKey.length() == 0 || cleanExpansion.length() == 0) return;
    ArrayList<Item> items = read();
    String sourceLanguage = "en".equals(oldLanguage) ? "en" : "ar";
    String oldFold = TextNormalizer.foldForComparison(oldShortcut, sourceLanguage);
    String newFold = TextNormalizer.foldForComparison(cleanKey, language);
    ArrayList<Item> output = new ArrayList<Item>();
    boolean inserted = false;
    for (Item item : items) {
      String folded = TextNormalizer.foldForComparison(item.shortcut, item.language);
      boolean oldMatch =
          item.language.equals(sourceLanguage)
              && oldFold.length() > 0
              && TextNormalizer.foldForComparison(item.shortcut, sourceLanguage).equals(oldFold);
      boolean newMatch = item.language.equals(language) && folded.equals(newFold);
      if (oldMatch || newMatch) {
        if (!inserted) {
          output.add(new Item(cleanKey, cleanExpansion, language));
          inserted = true;
        }
      } else {
        output.add(item);
      }
    }
    if (!inserted) output.add(0, new Item(cleanKey, cleanExpansion, language));
    write(output);
  }

  public synchronized void delete(String shortcut, String language) {
    String target = TextNormalizer.foldForComparison(shortcut, language);
    ArrayList<Item> output = new ArrayList<Item>();
    for (Item item : read()) {
      String folded = TextNormalizer.foldForComparison(item.shortcut, item.language);
      if (!item.language.equals(language) || !folded.equals(target)) output.add(item);
    }
    write(output);
  }

  public synchronized String expansion(String shortcut, String language) {
    String target = TextNormalizer.foldForComparison(shortcut, language);
    if (target.length() == 0) return "";
    for (Item item : read()) {
      if (item.language.equals(language)
          && TextNormalizer.foldForComparison(item.shortcut, language).equals(target)) {
        return item.expansion;
      }
    }
    return "";
  }

  public synchronized List<String> suggestions(String prefix, String language, int limit) {
    String target = TextNormalizer.foldForComparison(prefix, language);
    ArrayList<String> exact = new ArrayList<String>();
    ArrayList<String> starts = new ArrayList<String>();
    if (target.length() == 0) return exact;
    for (Item item : read()) {
      if (!item.language.equals(language)) continue;
      String folded = TextNormalizer.foldForComparison(item.shortcut, language);
      if (folded.equals(target)) {
        if (!exact.contains(item.expansion)) exact.add(item.expansion);
      } else if (folded.startsWith(target) && !starts.contains(item.expansion)) {
        starts.add(item.expansion);
      }
    }
    exact.addAll(starts);
    return exact.subList(0, Math.min(Math.max(1, limit), exact.size()));
  }

  private ArrayList<Item> read() {
    ArrayList<Item> output = new ArrayList<Item>();
    try {
      JSONArray values = new JSONArray(preferences.getString("items_json", "[]"));
      for (int index = 0; index < values.length(); index++) {
        JSONObject item = values.getJSONObject(index);
        String shortcut = item.optString("shortcut", "").trim();
        String expansion = item.optString("expansion", "").trim();
        String language = item.optString("language", "ar");
        if (shortcut.length() > 0 && expansion.length() > 0) {
          output.add(new Item(shortcut, expansion, "en".equals(language) ? "en" : "ar"));
        }
      }
    } catch (Exception ignored) {
    }
    return output;
  }

  private void write(List<Item> items) {
    JSONArray values = new JSONArray();
    try {
      for (Item item : items) {
        JSONObject value = new JSONObject();
        value.put("shortcut", item.shortcut);
        value.put("expansion", item.expansion);
        value.put("language", item.language);
        values.put(value);
      }
      preferences.edit().putString("items_json", values.toString()).apply();
    } catch (Exception ignored) {
    }
  }
}
