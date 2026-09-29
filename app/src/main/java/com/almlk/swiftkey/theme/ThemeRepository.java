package com.almlk.swiftkey.theme;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Persistent built-in and user-created themes with gallery category metadata. */
public final class ThemeRepository {
  private static final String FILE = "custom_themes", INDEX = "ids";
  /**
   * Round 41-3: the 10 photo wallpapers were EMPTIED OUT of the "خلفيات" section by
   * owner request — the wallpapers survive as hidden aliases in KeyboardTheme.from.
   * Round 42: the section is now FILLED with the four professional themes requested
   * by the owner — glossy 3D key skins (gold, pink, silver, red heart) over photo
   * backgrounds. They are listed after the 18 color-family themes and land in the
   * "خلفيات" gallery category (built-in ids without a family prefix).
   */
  public static final String[] BUILT_IN_IDS = {
    "white_pure",
    "white_silver",
    "white_warm",
    "black_amoled",
    "black_graphite",
    "black_charcoal",
    "stone_marble",
    "stone_granite",
    "stone_basalt",
    "girly_rose",
    "girly_fuchsia",
    "girly_orchid",
    "blue_sky",
    "blue_royal",
    "blue_navy",
    "gray_mist",
    "gray_smoke",
    "gray_deep",
    "lux_gold",
    "lux_pink",
    "lux_silver",
    "lux_heart"
  };
  public static final String[] BUILT_IN_NAMES = {
    "أبيض نقي",
    "أبيض فضي",
    "أبيض دافئ",
    "أسود خالص",
    "أسود جرافيت",
    "أسود فحمي",
    "رخام",
    "جرانيت",
    "بازلت",
    "وردي ناعم",
    "فوشيا",
    "أوركيدا",
    "أزرق سماوي",
    "أزرق ملكي",
    "أزرق كحلي",
    "أبيض ضبابي",
    "أبيض دخاني",
    "رمادي غامق",
    "الذهبية الفاخرة",
    "الوردية الناعمة",
    "الفضية المصقولة",
    "قلب رومانسي"
  };
  public static final String CATEGORY_ALL = "all",
      CATEGORY_SCENES = "scenes",
      CATEGORY_IMPORTED = "imported",
      CATEGORY_WHITE = "white",
      CATEGORY_BLACK = "black",
      CATEGORY_STONE = "stone",
      CATEGORY_GIRLY = "girly",
      CATEGORY_BLUE = "blue",
      CATEGORY_GRAY = "gray",
      CATEGORY_LIGHT = "light",
      CATEGORY_DARK = "dark";

  public static KeyboardTheme load(Context context, String id) {
    if (id != null && id.startsWith("custom_")) {
      SharedPreferences p = context.getSharedPreferences(FILE, 0);
      String base = id + "_";
      return new KeyboardTheme(
          p.getInt(base + "background", 0xffd7dfea),
          p.getInt(base + "key", 0xffffffff),
          p.getInt(base + "pressed", 0xffc8d7ec),
          p.getInt(base + "text", 0xff172033),
          p.getInt(base + "sub", 0xff637083),
          p.getInt(base + "accent", 0xff2e73db),
          p.getFloat(base + "radius", 7f),
          p.getFloat(base + "main_size", 26f),
          p.getFloat(base + "sub_size", 13f),
          p.getString(base + "image", ""),
          p.getFloat(base + "key_opacity", .72f),
          p.getInt(base + "font_style", 0),
          p.getFloat(base + "bg_dim", 0f),
          p.getInt(base + "surface", 0))
      .withKeySkin(p.getString(base + "key_skin", ""))
      // Round 50: الإطار الاحترافي المحفوظ (0 = الوجه الكلاسيكي)
      .withKeyFrame(p.getInt(base + "key_frame", 0))
      // Round 51/72: إطار محمَّل من الإنترنت + زر المسافة المرافق له
      .withKeyFrameUris(
          p.getString(base + "key_frame_uri", ""),
          p.getString(base + "key_space_frame_uri", ""))
      // Round 56: تفاعل الضغط المتحرك (المدمج والمحمّل من الإنترنت)
      .withPressEffect(p.getInt(base + "press_effect", 0))
      .withPressEffectUri(p.getString(base + "press_effect_uri", ""))
      // Round 69: مقياسا عرض/ارتفاع الزر داخل خانة المفتاح
      .withKeySize(
          p.getFloat(base + "key_width_scale", 1f),
          p.getFloat(base + "key_height_scale", 1f))
      // Round 45: موضع الحروف داخل الزر (السالب يعني الافتراضي القديم)
      .withTextPositions(
          p.getFloat(base + "main_text_position", -1f),
          p.getFloat(base + "sub_text_position", -1f));
    }
    // Round 43: imported assets/theme/ folders resolve through the library; a
    // missing folder (deleted at rebuild time) falls back to the default theme.
    if (id != null && id.startsWith(AssetThemeLibrary.ID_PREFIX)) {
      KeyboardTheme imported = AssetThemeLibrary.theme(context, id);
      if (imported != null) return imported;
    }
    return KeyboardTheme.from(id);
  }

  public static boolean matches(String id, String category) {
    if (CATEGORY_ALL.equals(category)) return true;
    // Round 43: imported themes carry their own gallery chip.
    if (CATEGORY_IMPORTED.equals(category)) return id.startsWith(AssetThemeLibrary.ID_PREFIX);
    if (CATEGORY_WHITE.equals(category)) return id.startsWith("white_");
    if (CATEGORY_BLACK.equals(category)) return id.startsWith("black_");
    if (CATEGORY_STONE.equals(category)) return id.startsWith("stone_");
    if (CATEGORY_GIRLY.equals(category)) return id.startsWith("girly_");
    if (CATEGORY_BLUE.equals(category)) return id.startsWith("blue_");
    if (CATEGORY_GRAY.equals(category)) return id.startsWith("gray_");
    if (CATEGORY_SCENES.equals(category)) {
      for (String builtin : BUILT_IN_IDS) {
        if (builtin.equals(id)) return !isFamilyTheme(id);
      }
      return false;
    }
    if (CATEGORY_LIGHT.equals(category)) {
      return id.startsWith("custom_")
          || "samsung_white".equals(id)
          || "samsung_blue".equals(id)
          || "samsung_mint".equals(id)
          || "iphone_white".equals(id)
          || "iphone_silver".equals(id)
          || "special_blue".equals(id)
          || "special_purple".equals(id)
          || "special_green".equals(id)
          || "special_gold".equals(id)
          || "cream".equals(id)
          || "contrast".equals(id);
    }
    if (CATEGORY_DARK.equals(category)) {
      return "samsung_black".equals(id)
          || "iphone_black".equals(id)
          || "iphone_midnight".equals(id)
          || "amoled_black".equals(id)
          || "graphite".equals(id);
    }
    return true;
  }

  /** Round 41: the 18 color-family ids carry a family prefix; the wallpaper ids do not. */
  private static boolean isFamilyTheme(String id) {
    return id.startsWith("white_")
        || id.startsWith("black_")
        || id.startsWith("stone_")
        || id.startsWith("girly_")
        || id.startsWith("blue_")
        || id.startsWith("gray_");
  }

  public static List<String> customIds(Context context) {
    ArrayList<String> out = new ArrayList<String>();
    String raw = context.getSharedPreferences(FILE, 0).getString(INDEX, "");
    for (String id : raw.split(",")) if (id.length() > 0 && !out.contains(id)) out.add(id);
    return out;
  }

  public static String name(Context context, String id) {
    if (id != null && id.startsWith("custom_"))
      return context.getSharedPreferences(FILE, 0).getString(id + "_name", "سمة مخصصة");
    // Round 43: imported themes are named by their folder config.
    if (id != null && id.startsWith(AssetThemeLibrary.ID_PREFIX)) {
      String label = AssetThemeLibrary.label(context, id);
      if (label != null) return label;
    }
    for (int i = 0; i < BUILT_IN_IDS.length; i++)
      if (BUILT_IN_IDS[i].equals(id)) return BUILT_IN_NAMES[i];
    return id;
  }

  public static String save(Context context, String existing, String name, KeyboardTheme theme) {
    String id =
        existing != null && existing.startsWith("custom_")
            ? existing
            : "custom_" + System.currentTimeMillis();
    SharedPreferences p = context.getSharedPreferences(FILE, 0);
    String b = id + "_";
    p.edit()
        .putString(b + "name", name)
        .putInt(b + "background", theme.background)
        .putInt(b + "key", theme.key)
        .putInt(b + "pressed", theme.keyPressed)
        .putInt(b + "text", theme.text)
        .putInt(b + "sub", theme.sub)
        .putInt(b + "accent", theme.accent)
        .putFloat(b + "radius", theme.keyRadiusDp)
        .putFloat(b + "main_size", theme.mainTextSizeDp)
        .putFloat(b + "sub_size", theme.subTextSizeDp)
        .putString(b + "image", theme.imageUri)
        .putFloat(b + "key_opacity", theme.keyOpacity)
        .putInt(b + "font_style", theme.fontStyle)
        .putFloat(b + "bg_dim", theme.backgroundDim)
        .putInt(b + "surface", theme.surfaceOverride)
        .putString(b + "key_skin", theme.keySkin)
        .putInt(b + "key_frame", theme.keyFrame)
        .putString(b + "key_frame_uri", theme.keyFrameUri)
        .putString(b + "key_space_frame_uri", theme.keySpaceFrameUri)
        .putInt(b + "press_effect", theme.pressEffect)
        .putString(b + "press_effect_uri", theme.pressEffectUri)
        // Round 69: مقياسا عرض/ارتفاع الزر (شريطا قسم الخط)
        .putFloat(b + "key_width_scale", theme.keyWidthScale)
        .putFloat(b + "key_height_scale", theme.keyHeightScale)
        // Round 45: موضع الحرف الرئيسي والحرف الصغير داخل وجه الزر
        .putFloat(b + "main_text_position", theme.mainTextPosition)
        .putFloat(b + "sub_text_position", theme.subTextPosition)
        .apply();
    List<String> ids = customIds(context);
    if (!ids.contains(id)) {
      ids.add(0, id);
      saveIndex(p, ids);
    }
    return id;
  }

  public static void delete(Context context, String id) {
    if (id == null || !id.startsWith("custom_")) return;
    SharedPreferences p = context.getSharedPreferences(FILE, 0);
    SharedPreferences.Editor e = p.edit();
    String b = id + "_";
    String[] keys = {
      "name",
      "background",
      "key",
      "pressed",
      "text",
      "sub",
      "accent",
      "radius",
      "main_size",
      "sub_size",
      "image",
      "key_opacity",
      "font_style",
      "bg_dim",
      "surface",
      "key_skin",
      "key_frame",
      "key_frame_uri",
      "key_space_frame_uri",
      "press_effect",
      "press_effect_uri",
      "key_width_scale",
      "key_height_scale",
      "main_text_position",
      "sub_text_position"
    };
    for (String key : keys) e.remove(b + key);
    e.apply();
    List<String> ids = customIds(context);
    ids.remove(id);
    saveIndex(p, ids);
  }

  private static void saveIndex(SharedPreferences p, List<String> ids) {
    StringBuilder s = new StringBuilder();
    for (String id : ids) {
      if (s.length() > 0) s.append(',');
      s.append(id);
    }
    p.edit().putString(INDEX, s.toString()).apply();
  }

  private ThemeRepository() {}
}
