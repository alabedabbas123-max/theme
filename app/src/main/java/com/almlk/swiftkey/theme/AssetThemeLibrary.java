package com.almlk.swiftkey.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.json.JSONObject;

/**
 * Round 43: dynamic theme import and management from APK assets.
 *
 * Layout — assets/theme/(folder)/ holds one theme per folder (any number of
 * folders; enumerated at runtime, nothing is hard-coded):
 *
 *   assets/theme/kay/config.json   palette + styling + image paths (or theme.json)
 *   assets/theme/kay/bg.jpg        keyboard background art (any size)
 *   assets/theme/kay/key.png       the key/button icon (any size)
 *   assets/theme/kay/space.png     OPTIONAL spacebar icon
 *
 * config.json schema (all fields optional — smart defaults fill the gaps):
 * {
 *   "name": "كاي الذهبية",
 *   "background": "#17120C", "key": "#D9B45C", "pressed": "#A8833A",
 *   "text": "#241A08", "sub": "#6E5A2C", "accent": "#D4AF37", "bottom": "#D9B45C",
 *   "radius": 7, "mainSize": 26, "subSize": 13, "opacity": 1.0, "dim": 0,
 *   "images": { "keyboard": "bg.jpg", "key": "key.png", "space": "space.png" }
 * }
 * Image paths are relative to the folder. When "images" omits a role the library
 * falls back to matching file names inside the folder (space / key-btn-button /
 * bg-back-wall keywords) — the JSON stays the single source of truth when present.
 *
 * Contracts the renderer relies on:
 *   - ids are "asset_(folder)" (gallery + prefs), key skins carry
 *     "asset:theme/(folder)" and background uris "asset:theme/(folder)/(file)".
 *   - key icons are center-cropped to the key face aspect (0.63, the round-41 skin
 *     geometry) and capped at 256px — they draw pixel-exact with NO distortion.
 *   - space icons are cropped to the wide ruler aspect (3.2 — the round-42
 *     direct-draw ratio) and capped at 1024px; when a folder has no space icon the
 *     REGULAR key icon is applied to the spacebar (the three-slice fallback).
 *   - backgrounds (WebP shipped — smaller package, same quality; opaque lossy
 *     WebP decodes on every API the app runs) are sampled down (inSampleSize) to
 *     at most 1080px on the long edge, so imported 4K art never floods memory.
 *   - the press flash is the darkened twin of the key icon (0.55, round-42 style).
 *
 * Round 45: ALL bitmap sizing/shaping/caching lives in KeyArtProcessor — the
 * fitted 3D copies are computed once per image and served from a bounded LRU
 * cache, so re-selecting a theme fetches the stored copy instead of re-drawing
 * it. Cached bitmaps are SHARED between the keyboard view and the gallery
 * thumbnails — callers must never recycle them (the views guard their own
 * recycle calls with ownership flags instead).
 */
public final class AssetThemeLibrary {
  /** Root folder inside assets/. */
  public static final String ROOT = "theme";
  /** Gallery/prefs id prefix for imported themes. */
  public static final String ID_PREFIX = "asset_";
  /** uri/skin prefix — "asset:theme/...". */
  public static final String URI_PREFIX = "asset:";

  private static final HashMap<String, AssetSpec> SPECS =
      new HashMap<String, AssetSpec>();
  private static final HashMap<String, KeyboardTheme> THEMES =
      new HashMap<String, KeyboardTheme>();
  private static List<String> folders = null;

  /** One imported theme folder resolved from its config (paths are full asset paths). */
  private static final class AssetSpec {
    final String folder;
    final String label;
    final String background;
    final String key;
    final String space;

    AssetSpec(String folder, String label, String background, String key, String space) {
      this.folder = folder;
      this.label = label;
      this.background = background;
      this.key = key;
      this.space = space;
    }
  }

  private AssetThemeLibrary() {}

  /** Gallery ids of every valid theme folder under assets/theme/ (sorted). */
  public static List<String> ids(Context context) {
    ArrayList<String> out = new ArrayList<String>();
    for (String folder : folders(context)) {
      if (resolve(context, folder) != null) out.add(ID_PREFIX + folder);
    }
    return out;
  }

  /** Display name of an imported theme (JSON "name", else the folder name). */
  public static String label(Context context, String id) {
    if (id == null || !id.startsWith(ID_PREFIX)) return null;
    AssetSpec spec = resolve(context, id.substring(ID_PREFIX.length()));
    return spec == null ? null : spec.label;
  }

  /**
   * Builds (and caches) the KeyboardTheme for "asset_(folder)". Colors and
   * styling come from the folder config; the background uri and key skin point
   * back into the library. Returns null when the folder is missing or holds
   * neither a config nor any art — callers fall back to the default theme.
   */
  public static KeyboardTheme theme(Context context, String id) {
    if (id == null || !id.startsWith(ID_PREFIX)) return null;
    String folder = id.substring(ID_PREFIX.length());
    AssetSpec spec = resolve(context, folder);
    if (spec == null) return null;
    synchronized (THEMES) {
      KeyboardTheme cached = THEMES.get(folder);
      if (cached != null) return cached;
    }
    JSONObject json = readConfig(context, folder);
    int key = color(json, "key", 0xffd9b45c);
    int background = color(json, "background", 0xff17120c);
    int pressed = color(json, "pressed", scale(key, .65f));
    int accent = color(json, "accent", key);
    int bottom = color(json, "bottom", key);
    int text =
        color(json, "text", luminance(key) < 142 ? Color.WHITE : 0xff172033);
    int sub = color(json, "sub", blend(text, key, .45f));
    KeyboardTheme built =
        KeyboardTheme.assetTheme(
            background,
            key,
            pressed,
            text,
            sub,
            accent,
            bottom,
            (float) number(json, "radius", 7.0),
            (float) number(json, "mainSize", 26.0),
            (float) number(json, "subSize", 13.0),
            (float) number(json, "opacity", 1.0),
            (float) number(json, "dim", 0.0),
            spec.background.length() == 0 ? "" : URI_PREFIX + spec.background,
            URI_PREFIX + ROOT + "/" + folder);
    synchronized (THEMES) {
      THEMES.put(folder, built);
    }
    return built;
  }

  /**
   * Decodes (once, cached, sampled down) the keyboard background for an
   * "asset:theme/(folder)/(file)" uri — WebP included. Shared bitmap — never recycle.
   * Round 45: the sizing/caching pipeline lives in KeyArtProcessor.
   */
  public static Bitmap keyboardBackground(Context context, String uri) {
    if (uri == null || !uri.startsWith(URI_PREFIX)) return null;
    String path = uri.substring(URI_PREFIX.length());
    if (path.length() == 0) return null;
    return KeyArtProcessor.background(context, path);
  }

  /**
   * Key skin part for an "asset:theme/(folder)" skin token:
   * up/fun/enter — the key icon fitted to the 3D key face (KeyArtProcessor);
   * space — the space icon cropped to the wide ruler aspect, or the regular key
   * icon when the folder ships no space art (graceful fallback);
   * press — the darkened twin of the key icon (0.55).
   * Shared bitmaps — never recycle.
   */
  public static Bitmap keySkinPart(Context context, String skin, String part) {
    if (skin == null || !skin.startsWith(URI_PREFIX + ROOT + "/")) return null;
    String folder = skin.substring((URI_PREFIX + ROOT + "/").length());
    AssetSpec spec = resolve(context, folder);
    if (spec == null || spec.key.length() == 0) return null;
    if ("space".equals(part)) {
      if (spec.space.length() > 0) {
        return KeyArtProcessor.spaceArt(context, spec.space);
      }
      // المتطلب: لا أيقونة مسافة → خلفية الزر العادية نفسها عليها
      return KeyArtProcessor.keyArt(context, spec.key);
    }
    if ("press".equals(part)) {
      return KeyArtProcessor.pressTwin(context, spec.key);
    }
    return KeyArtProcessor.keyArt(context, spec.key);
  }

  // ---------------------------------------------------------------- discovery

  /** Enumerates assets/theme/ subfolders once per process (sorted, dot-files out). */
  private static List<String> folders(Context context) {
    if (folders != null) return folders;
    ArrayList<String> out = new ArrayList<String>();
    try {
      String[] entries = context.getAssets().list(ROOT);
      if (entries != null) {
        for (int i = 0; i < entries.length; i++) {
          String name = entries[i];
          if (name == null || name.startsWith(".") || name.indexOf('.') >= 0) continue;
          out.add(name);
        }
      }
    } catch (Exception ignored) {
      // assets/theme/ absent — no imported themes, perfectly fine
    }
    Collections.sort(out);
    folders = out;
    return out;
  }

  /** Preferred config file names, in order. */
  private static String configName(Context context, String folder) {
    String[] preferred = {"config.json", "theme.json"};
    for (int i = 0; i < preferred.length; i++) {
      if (assetExists(context, ROOT + "/" + folder + "/" + preferred[i])) return preferred[i];
    }
    try {
      String[] files = context.getAssets().list(ROOT + "/" + folder);
      if (files != null) {
        for (int i = 0; i < files.length; i++) {
          if (files[i] != null && files[i].endsWith(".json")) return files[i];
        }
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  private static boolean assetExists(Context context, String path) {
    try {
      InputStream probe = context.getAssets().open(path);
      try {
        probe.close();
      } catch (Exception ignored) {
      }
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static JSONObject readConfig(Context context, String folder) {
    String name = configName(context, folder);
    if (name.length() == 0) return null;
    try {
      InputStream in = context.getAssets().open(ROOT + "/" + folder + "/" + name);
      try {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
        return new JSONObject(new String(buffer.toByteArray(), "UTF-8"));
      } finally {
        in.close();
      }
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Resolves a folder into its art paths (full asset paths, "" when absent) and
   * display label. JSON "images" wins; missing roles fall back to file-name
   * keywords inside the folder. A folder with no config AND no art resolves to
   * null and is skipped by the gallery.
   */
  private static AssetSpec resolve(Context context, String folder) {
    if (folder == null || folder.length() == 0) return null;
    synchronized (SPECS) {
      AssetSpec cached = SPECS.get(folder);
      if (cached != null) return cached;
    }
    if (!folders(context).contains(folder)) return null;
    JSONObject json = readConfig(context, folder);
    JSONObject images = json == null ? null : json.optJSONObject("images");
    String key =
        pickImage(
            context,
            folder,
            images,
            new String[] {"key", "icon", "button"},
            new String[] {"space", "keyboard", "background", "bg", "back", "wall"});
    String space = pickImage(context, folder, images, new String[] {"space", "spacebar"}, null);
    String background =
        pickImage(context, folder, images, new String[] {"keyboard", "background", "bg", "wallpaper"},
            new String[] {"key", "space"});
    String label = folder;
    if (json != null) {
      String configured = json.optString("name", "");
      if (configured.length() > 0) label = configured;
    }
    if (json == null && key.length() == 0 && space.length() == 0 && background.length() == 0) {
      return null; // no config, no art — not a theme
    }
    AssetSpec spec = new AssetSpec(folder, label, background, key, space);
    synchronized (SPECS) {
      SPECS.put(folder, spec);
    }
    return spec;
  }

  /**
   * Finds the art file for one role: JSON aliases first (relative to the folder,
   * or a full "theme/..." path), then heuristic file-name keywords for the role.
   * "exclude" keeps heuristic matches away from files already spoken for.
   */
  private static String pickImage(
      Context context, String folder, JSONObject images, String[] aliases, String[] exclude) {
    if (images != null && aliases != null) {
      for (int i = 0; i < aliases.length; i++) {
        String value = images.optString(aliases[i], "");
        String full = normalizePath(folder, value);
        if (full.length() > 0 && assetExists(context, full)) return full;
      }
    }
    try {
      String[] files = context.getAssets().list(ROOT + "/" + folder);
      if (files == null) return "";
      for (int a = 0; a < aliases.length; a++) {
        for (int i = 0; i < files.length; i++) {
          String file = files[i];
          if (file == null || !isImageName(file)) continue;
          String lower = file.toLowerCase();
          if (exclude != null) {
            boolean skip = false;
            for (int j = 0; j < exclude.length; j++) {
              if (lower.contains(exclude[j])) skip = true;
            }
            if (skip) continue;
          }
          if (lower.contains(aliases[a])) return ROOT + "/" + folder + "/" + file;
        }
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  private static boolean isImageName(String name) {
    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
        || name.endsWith(".webp");
  }

  /** "bg.jpg" (folder-relative) / "theme/kay/bg.jpg" / "/bg.jpg" → "theme/kay/bg.jpg". */
  private static String normalizePath(String folder, String value) {
    if (value == null) return "";
    String path = value.trim();
    while (path.startsWith("/")) path = path.substring(1);
    if (path.length() == 0) return "";
    if (path.startsWith(ROOT + "/")) return path;
    return ROOT + "/" + folder + "/" + path;
  }

  // ------------------------------------------------------------------- colors

  private static int color(JSONObject json, String name, int fallback) {
    if (json == null) return fallback;
    Object raw = json.opt(name);
    if (raw == null) return fallback;
    try {
      if (raw instanceof Number) return (int) ((Number) raw).longValue();
      String text = String.valueOf(raw).trim();
      if (text.startsWith("0x") || text.startsWith("0X")) {
        return (int) Long.parseLong(text.substring(2), 16);
      }
      if (!text.startsWith("#")) text = "#" + text;
      return Color.parseColor(text);
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static double number(JSONObject json, String name, double fallback) {
    if (json == null) return fallback;
    try {
      return json.optDouble(name, fallback);
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static int luminance(int color) {
    return (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
  }

  private static int scale(int color, float factor) {
    return (color & 0xff000000)
        | ((int) (((color >>> 16) & 0xff) * factor) << 16)
        | ((int) (((color >>> 8) & 0xff) * factor) << 8)
        | (int) ((color & 0xff) * factor);
  }

  private static int blend(int a, int b, float towardB) {
    return (a & 0xff000000)
        | ((int) (((a >>> 16) & 0xff) * (1f - towardB) + ((b >>> 16) & 0xff) * towardB) << 16)
        | ((int) (((a >>> 8) & 0xff) * (1f - towardB) + ((b >>> 8) & 0xff) * towardB) << 8)
        | (int) ((a & 0xff) * (1f - towardB) + (b & 0xff) * towardB);
  }
}
