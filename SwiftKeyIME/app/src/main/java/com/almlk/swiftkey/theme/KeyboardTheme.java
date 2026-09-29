package com.almlk.swiftkey.theme;

import android.content.Context;
import android.graphics.Color;

/** Clear keyboard theme with independent colors for space, delete, bottom row and shift. */
public final class KeyboardTheme {
  public static final int ANIMATION_NONE = 0;
  public static final int ANIMATION_PARALLAX = 1;
  public static final int ANIMATION_NEON = 2;
  public static final int ANIMATION_FIREFLIES = 3;
  public static final int ANIMATION_SPEED = 4;
  public static final int ANIMATION_SPARKLES = 5;
  public static final int ANIMATION_LANTERN = 6;
  public static final int ANIMATION_FIREWORKS = 7;
  public static final int ANIMATION_PULSE = 8;
  public static final int ANIMATION_ROYAL = 9;
  public static final int ANIMATION_STARS = 10;
  public static final int ANIMATION_PLANETS = 11;
  public static final int ANIMATION_HEARTS = 12;
  public static final int ANIMATION_SNOW = 13;
  public static final int ANIMATION_BUBBLES = 14;
  public static final int ANIMATION_PETALS = 15;
  public static final int ANIMATION_MUSIC = 16;

  public final int background, key, keyPressed, text, sub, accent;
  public final int bottomKey, spaceKey, deleteKey, shiftKey;
  public final int surface, surfaceText, surfaceSub;
  public final int animationStyle, fontStyle;
  public final float keyRadiusDp, mainTextSizeDp, subTextSizeDp, keyOpacity;
  public final String imageUri;
  /** Round 40: user dim/brightness over the background; negative dims, positive lightens. */
  public final float backgroundDim;
  /** Round 40: explicit surface color for panels (tools, clipboard...); 0 = derive automatically. */
  public final int surfaceOverride;
  /**
   * Round 41: image key skin — key faces come from the skin drawables
   * keybg_NAME_up / _fun / _space / _press / _enter (subtle curved SwiftKey-style
   * shapes); empty = programmatic key faces.
   */
  public final String keySkin;
  /**
   * Round 50: professional button frame. 0 = the classic programmatic key face;
   * 1..10 = the IMAGE frames catalogued in KeyFrames (keyframe_ID_R.png art that
   * follows the selected key shape).
   */
  public final int keyFrame;
  /**
   * Round 51: مسار إطار محمَّل من الإنترنت (OnlineAssetStore) — إن لم يكن فارغاً
   * فهو أولوية على إطارات keyFrame المدمجة، ويُرسم بأي شكل زر عبر BitmapShader.
   */
  public final String keyFrameUri;
  /** Round 72: مسار فن زر المسافة المرافق لإطار ثيماتي المحمّل. */
  public final String keySpaceFrameUri;
  /**
   * Round 56: تفاعل الضغط المتحرك — 0 = بلا تفاعل، 1..10 تصاميم PressEffects
   * المدمجة (قلوب/بوسات/نجوم...) تتنثر فوق المفتاح لحظة ضغطه.
   */
  public final int pressEffect;
  /** Round 56: مسار صورة تفاعل محمّلة من الإنترنت — أولوية على المدمج. */
  public final String pressEffectUri;
  /**
   * Round 69: مقياس عرض الزر داخل خانة المفتاح (0.4..1.0) — 1.0 = ملء العرض.
   * يطبَّق على الإطارات والوجه الكلاسيكي (لا على جلود الصور المصممة للملء).
   */
  public final float keyWidthScale;
  /** Round 69: مقياس ارتفاع الزر داخل خانة المفتاح (0.4..1.0). */
  public final float keyHeightScale;
  /**
   * Round 45: موضع الحرف الرئيسي داخل وجه الزر كنسبة من أعلى الوجه إلى أسفله.
   * القيمة السالبة تعني استخدام موضع النظام الافتراضي لكل نوع زر.
   */
  public final float mainTextPosition;
  /** Round 45: موضع الحرف/الرمز الصغير من أعلى وجه الزر كنسبة، والسالب = الافتراضي. */
  public final float subTextPosition;

  public KeyboardTheme(int b, int k, int p, int t, int s, int a) {
    this(b, k, p, t, s, a, 7f, 26f, 13f, "", 1f);
  }

  public KeyboardTheme(
      int b,
      int k,
      int p,
      int t,
      int s,
      int a,
      float radius,
      float mainSize,
      float subSize,
      String image) {
    this(b, k, p, t, s, a, radius, mainSize, subSize, image, 1f);
  }

  public KeyboardTheme(
      int b,
      int k,
      int p,
      int t,
      int s,
      int a,
      float radius,
      float mainSize,
      float subSize,
      String image,
      float opacity) {
    this(b, k, p, t, s, a, radius, mainSize, subSize, image, opacity, 0);
  }

  public KeyboardTheme(
      int b,
      int k,
      int p,
      int t,
      int s,
      int a,
      float radius,
      float mainSize,
      float subSize,
      String image,
      float opacity,
      int font) {
    this(b, k, p, t, s, a, radius, mainSize, subSize, image, opacity, k, k, k, a, font, 0f, 0, "");
  }

  /** Round 40: full shape with background dim/brightness and an explicit panel surface color. */
  public KeyboardTheme(
      int b,
      int k,
      int p,
      int t,
      int s,
      int a,
      float radius,
      float mainSize,
      float subSize,
      String image,
      float opacity,
      int font,
      float dim,
      int surface) {
    this(b, k, p, t, s, a, radius, mainSize, subSize, image, opacity, k, k, k, a, font, dim,
        surface, "");
  }

  private KeyboardTheme(
      int b,
      int k,
      int p,
      int t,
      int s,
      int a,
      float radius,
      float mainSize,
      float subSize,
      String image,
      float opacity,
      int bottom,
      int space,
      int delete,
      int shift,
      int font,
      float dim,
      int surfaceOv,
      String skin) {
    this(b, k, p, t, s, a, radius, mainSize, subSize, image, opacity, bottom, space, delete,
        shift, font, dim, surfaceOv, skin, 0, "", "", 0, "", 1f, 1f, -1f, -1f);
  }

  /** Round 50: full shape with the professional key frame (0 = classic face). */
  private KeyboardTheme(
      int b,
      int k,
      int p,
      int t,
      int s,
      int a,
      float radius,
      float mainSize,
      float subSize,
      String image,
      float opacity,
      int bottom,
      int space,
      int delete,
      int shift,
      int font,
      float dim,
      int surfaceOv,
      String skin,
      int frame) {
    this(b, k, p, t, s, a, radius, mainSize, subSize, image, opacity, bottom, space, delete,
        shift, font, dim, surfaceOv, skin, frame, "", "", 0, "", 1f, 1f, -1f, -1f);
  }

  /**
   * Round 51: full shape with an internet-downloaded frame image (empty keeps
   * the built-in frame catalog).
   */
  private KeyboardTheme(
      int b,
      int k,
      int p,
      int t,
      int s,
      int a,
      float radius,
      float mainSize,
      float subSize,
      String image,
      float opacity,
      int bottom,
      int space,
      int delete,
      int shift,
      int font,
      float dim,
      int surfaceOv,
      String skin,
      int frame,
      String frameUri,
      String spaceFrameUri,
      int effect,
      String effectUri,
      float keyWidth,
      float keyHeight,
      float mainPosition,
      float subPosition) {
    background = b;
    key = k;
    keyPressed = p;
    text = t;
    sub = s;
    accent = a;
    bottomKey = bottom;
    spaceKey = space;
    deleteKey = delete;
    shiftKey = shift;
    fontStyle = Math.max(0, Math.min(4, font));
    keyRadiusDp = radius;
    mainTextSizeDp = mainSize;
    subTextSizeDp = subSize;
    imageUri = image == null ? "" : image;
    animationStyle = animationStyle(imageUri);
    keyOpacity = Math.max(.28f, Math.min(1f, opacity));
    backgroundDim = Math.max(-.85f, Math.min(.85f, dim));
    surfaceOverride = surfaceOv;
    keySkin = skin == null ? "" : skin;
    keyFrame = Math.max(0, Math.min(KeyFrames.COUNT, frame));
    keyFrameUri = frameUri == null ? "" : frameUri;
    keySpaceFrameUri = spaceFrameUri == null ? "" : spaceFrameUri;
    pressEffect = Math.max(0, Math.min(PressEffects.COUNT, effect));
    pressEffectUri = effectUri == null ? "" : effectUri;
    // Round 69: مقياسا عرض/ارتفاع الزر — نطاق آمن 40%..100% من خانة المفتاح
    keyWidthScale = Math.max(.4f, Math.min(1f, keyWidth));
    keyHeightScale = Math.max(.4f, Math.min(1f, keyHeight));
    mainTextPosition = mainPosition < 0f ? -1f : Math.max(.50f, Math.min(.90f, mainPosition));
    subTextPosition = subPosition < 0f ? -1f : Math.max(.02f, Math.min(.42f, subPosition));
    boolean dark = luminance(background) < 145;
    surface =
        surfaceOv != 0
            ? surfaceOv
            : blend(background, dark ? Color.WHITE : Color.BLACK, dark ? .13f : .05f);
    surfaceText = luminance(surface) < 150 ? Color.WHITE : 0xff172033;
    surfaceSub = blend(surfaceText, surface, .38f);
  }

  /**
   * Round 41: returns a copy with an image key skin (key faces drawn from that
   * skin's keybg_NAME_* drawables). Default "" = programmatic faces.
   */
  public KeyboardTheme withKeySkin(String skin) {
    if (skin == null || skin.length() == 0 || skin.equals(keySkin)) return this;
    return new KeyboardTheme(
        background, key, keyPressed, text, sub, accent, keyRadiusDp, mainTextSizeDp,
        subTextSizeDp, imageUri, keyOpacity, bottomKey, spaceKey, deleteKey, shiftKey,
        fontStyle, backgroundDim, surfaceOverride, skin, keyFrame, keyFrameUri, keySpaceFrameUri,
        pressEffect, pressEffectUri,
        keyWidthScale, keyHeightScale, mainTextPosition, subTextPosition);
  }

  /**
   * Round 50: copies the theme with a professional button frame. 0 keeps the
   * classic programmatic face; 1..10 pick one of the KeyFrames image frames.
   */
  public KeyboardTheme withKeyFrame(int frame) {
    if (frame == keyFrame) return this;
    return new KeyboardTheme(
        background, key, keyPressed, text, sub, accent, keyRadiusDp, mainTextSizeDp,
        subTextSizeDp, imageUri, keyOpacity, bottomKey, spaceKey, deleteKey, shiftKey,
        fontStyle, backgroundDim, surfaceOverride, keySkin, frame, keyFrameUri, keySpaceFrameUri,
        pressEffect, pressEffectUri,
        keyWidthScale, keyHeightScale, mainTextPosition, subTextPosition);
  }

  /**
   * Round 51: copies the theme with an internet-downloaded frame image path.
   * Empty clears it so the built-in frame (or the classic face) takes over.
   */
  public KeyboardTheme withKeyFrameUri(String frameUri) {
    return withKeyFrameUris(frameUri, "");
  }

  /**
   * Round 72: نسخة بإطار زر ثيماتي ومعه فن مستقل لزر المسافة. عند غياب
   * spaceFrameUri يعود زر المسافة إلى نفس فن الأزرار العادية لضمان التوافق.
   */
  public KeyboardTheme withKeyFrameUris(String frameUri, String spaceFrameUri) {
    String value = frameUri == null ? "" : frameUri;
    String spaceValue = spaceFrameUri == null ? "" : spaceFrameUri;
    if (value.equals(keyFrameUri) && spaceValue.equals(keySpaceFrameUri)) return this;
    return new KeyboardTheme(
        background, key, keyPressed, text, sub, accent, keyRadiusDp, mainTextSizeDp,
        subTextSizeDp, imageUri, keyOpacity, bottomKey, spaceKey, deleteKey, shiftKey,
        fontStyle, backgroundDim, surfaceOverride, keySkin, keyFrame, value, spaceValue,
        pressEffect, pressEffectUri,
        keyWidthScale, keyHeightScale, mainTextPosition, subTextPosition);
  }

  /**
   * Round 56: copies the theme with a built-in press interaction design
   * (0 = none; 1..10 = PressEffects catalog: hearts, kisses, stars...).
   */
  public KeyboardTheme withPressEffect(int effect) {
    if (effect == pressEffect) return this;
    return new KeyboardTheme(
        background, key, keyPressed, text, sub, accent, keyRadiusDp, mainTextSizeDp,
        subTextSizeDp, imageUri, keyOpacity, bottomKey, spaceKey, deleteKey, shiftKey,
        fontStyle, backgroundDim, surfaceOverride, keySkin, keyFrame, keyFrameUri, keySpaceFrameUri,
        effect, pressEffectUri,
        keyWidthScale, keyHeightScale, mainTextPosition, subTextPosition);
  }

  /**
   * Round 56: copies the theme with an internet-downloaded interaction sprite
   * path. Empty clears it so the built-in design (or none) takes over.
   */
  public KeyboardTheme withPressEffectUri(String effectUri) {
    String value = effectUri == null ? "" : effectUri;
    if (value.equals(pressEffectUri)) return this;
    return new KeyboardTheme(
        background, key, keyPressed, text, sub, accent, keyRadiusDp, mainTextSizeDp,
        subTextSizeDp, imageUri, keyOpacity, bottomKey, spaceKey, deleteKey, shiftKey,
        fontStyle, backgroundDim, surfaceOverride, keySkin, keyFrame, keyFrameUri, keySpaceFrameUri,
        pressEffect, value,
        keyWidthScale, keyHeightScale, mainTextPosition, subTextPosition);
  }

  /**
   * Round 69: نسخة بمقياس عرض/ارتفاع الزر داخل خانة المفتاح — يتحكم بها
   * شريطا «عرض الزر» و«ارتفاع الزر» في قسم الخط.
   */
  public KeyboardTheme withKeySize(float width, float height) {
    float w = Math.max(.4f, Math.min(1f, width));
    float h = Math.max(.4f, Math.min(1f, height));
    if (w == keyWidthScale && h == keyHeightScale) return this;
    return new KeyboardTheme(
        background, key, keyPressed, text, sub, accent, keyRadiusDp, mainTextSizeDp,
        subTextSizeDp, imageUri, keyOpacity, bottomKey, spaceKey, deleteKey, shiftKey,
        fontStyle, backgroundDim, surfaceOverride, keySkin, keyFrame, keyFrameUri, keySpaceFrameUri,
        pressEffect, pressEffectUri, w, h, mainTextPosition, subTextPosition);
  }

  /**
   * Round 45: نسخة بموضعي الحرف الرئيسي والحرف الصغير داخل الزر. القيم كنسبة
   * من وجه الزر حتى يبقى الموضع صحيحاً مع كل أحجام الكيبورد والإطارات.
   */
  public KeyboardTheme withTextPositions(float mainPosition, float subPosition) {
    float main = mainPosition < 0f ? -1f : Math.max(.50f, Math.min(.90f, mainPosition));
    float subPos = subPosition < 0f ? -1f : Math.max(.02f, Math.min(.42f, subPosition));
    if (main == mainTextPosition && subPos == subTextPosition) return this;
    return new KeyboardTheme(
        background, key, keyPressed, text, sub, accent, keyRadiusDp, mainTextSizeDp,
        subTextSizeDp, imageUri, keyOpacity, bottomKey, spaceKey, deleteKey, shiftKey,
        fontStyle, backgroundDim, surfaceOverride, keySkin, keyFrame, keyFrameUri, keySpaceFrameUri,
        pressEffect, pressEffectUri, keyWidthScale, keyHeightScale, main, subPos);
  }

  /**
   * Round 44: copies the theme with an explicit surface color. The photo/asset themes
   * skin the suggestion strip and toolbar with their wide bar art (ThemeSkinBar), so
   * the surface — and the text colors derived from it — must match that bar art
   * instead of the automatically derived background tint.
   */
  public KeyboardTheme withSurface(int surface) {
    if (surface == 0 || surface == surfaceOverride) return this;
    return new KeyboardTheme(
        background, key, keyPressed, text, sub, accent, keyRadiusDp, mainTextSizeDp,
        subTextSizeDp, imageUri, keyOpacity, bottomKey, spaceKey, deleteKey, shiftKey,
        fontStyle, backgroundDim, surface, keySkin, keyFrame, keyFrameUri, keySpaceFrameUri,
        pressEffect, pressEffectUri,
        keyWidthScale, keyHeightScale, mainTextPosition, subTextPosition);
  }

  public static KeyboardTheme from(String id) {
    // ---- Round 41-3: the GALLERY lists only the 18 color-family themes. The photo
    // wallpapers below stay as HIDDEN ALIASES (owner emptied the "خلفيات" section for the
    // upcoming professional themes) — they keep their image skins and keep rendering for
    // any install whose active theme still points at them. Celebrity themes are GONE;
    // old star ids normalize to the closest family theme inside Prefs.theme().
    if ("white_pure".equals(id))
      return family(0xffe9ecf1, 0xffffffff, 0xffd3dde9, 0xff172033, 0xff637083, 0xff2f7de1,
          0xffc9cfd8, "white_pure");
    if ("white_silver".equals(id))
      return family(0xffd9dee5, 0xfff3f5f8, 0xffccd4de, 0xff1a2130, 0xff5d6878, 0xff46689e,
          0xffc2c9d3, "white_silver");
    if ("white_warm".equals(id))
      return family(0xffece8e2, 0xfffffdf9, 0xffe0d9cd, 0xff221c14, 0xff6f675c, 0xffb0763a,
          0xffd6cec2, "white_warm");
    if ("black_amoled".equals(id))
      return family(0xff000000, 0xff1a1d21, 0xff30343a, 0xfff2f4f7, 0xff9aa3b0, 0xff0a84ff,
          0xff2a2e35, "black_amoled");
    if ("black_graphite".equals(id))
      return family(0xff14161a, 0xff23262c, 0xff3a3f47, 0xffe8ebef, 0xff8d95a1, 0xff5da4ff,
          0xff343941, "black_graphite");
    if ("black_charcoal".equals(id))
      return family(0xff1e2126, 0xff2c3037, 0xff454b54, 0xffeceef1, 0xff98a0ab, 0xff6aa2e8,
          0xff3d434c, "black_charcoal");
    if ("stone_marble".equals(id))
      return family(0xffe6e4e0, 0xfff7f6f3, 0xffddd9d3, 0xff23211d, 0xff6b675f, 0xff8a7a68,
          0xffd4d0c8, "stone_marble");
    if ("stone_granite".equals(id))
      return family(0xff7f7d79, 0xff9a9892, 0xff7c7a75, 0xff1e1d1a, 0xff4a4844, 0xff5c6f82,
          0xff7e7c76, "stone_granite");
    if ("stone_basalt".equals(id))
      return family(0xff26241f, 0xff37342e, 0xff4d4a42, 0xffece9e3, 0xffa09a8f, 0xffc2a878,
          0xff484439, "stone_basalt");
    if ("girly_rose".equals(id))
      return family(0xfffbe9ef, 0xfffff5f8, 0xfff3cdd9, 0xff3d1f2e, 0xffa06b82, 0xffe0559a,
          0xfff3cdd9, "girly_rose");
    if ("girly_fuchsia".equals(id))
      return family(0xfff3d9e8, 0xfffdeef7, 0xffe8b3d0, 0xff40102f, 0xff96527a, 0xffc2258f,
          0xffeabbd6, "girly_fuchsia");
    if ("girly_orchid".equals(id))
      return family(0xffefe4f6, 0xfffaf3fd, 0xffdcc6ec, 0xff2e1740, 0xff7a5b96, 0xff9a4fd8,
          0xffddc6ee, "girly_orchid");
    if ("blue_sky".equals(id))
      return family(0xffdbeaf8, 0xfff2f8fd, 0xffbcd7f0, 0xff10263f, 0xff4f6d8f, 0xff1976d2,
          0xffc2d9ef, "blue_sky");
    if ("blue_royal".equals(id))
      return family(0xffc6d9f2, 0xffe9f1fc, 0xffa9c4ea, 0xff0e1c3a, 0xff46609a, 0xff1d51c9,
          0xffb3caec, "blue_royal");
    if ("blue_navy".equals(id))
      return family(0xff101a2e, 0xff1d2b47, 0xff2f4266, 0xffe7edf7, 0xff93a4c4, 0xff5d9cf0,
          0xff2a3c5e, "blue_navy");
    if ("gray_mist".equals(id))
      return family(0xffd3d6da, 0xffe4e6e9, 0xffc6cad0, 0xff22262b, 0xff5d6570, 0xff5f7d9e,
          0xffc4c8ce, "gray_mist");
    if ("gray_smoke".equals(id))
      return family(0xffb9bdc3, 0xffced2d7, 0xffb0b5bc, 0xff1e2228, 0xff565e69, 0xff46618a,
          0xffadb2b9, "gray_smoke");
    if ("gray_deep".equals(id))
      return family(0xff7f848c, 0xff959ba3, 0xff7b818a, 0xff17191d, 0xff3f444c, 0xff3a5a8a,
          0xff7d838c, "gray_deep");
    // ---- Round 42: the four professional «خلفيات» themes — glossy 3D key skins cut
    // pixel-exact from the owner's reference sheet (gold, pink, silver, red heart) drawn
    // over full-bleed photo backgrounds. They are LISTED in the gallery's scenes
    // category and render every key from their keybg_lux_*_* drawables.
    // Round 44: buttons re-cut from the owner's NEW reference (slim 3D bottom edge),
    // the gold theme's background is GOLDEN, and every lux theme pins its surface to
    // the bar art color so the skinned suggestion strip / toolbar keep text contrast.
    if ("lux_gold".equals(id))
      return lux(0xffb07712, 0xffd9b45c, 0xffa8833a, 0xff241a08, 0xff6e5a2c, 0xffd4af37,
          0xffd9b45c, "bkg_lux_gold", "lux_gold").withSurface(0xffd9b45c);
    if ("lux_pink".equals(id))
      return lux(0xffe690b4, 0xffe795b8, 0xffc97ba0, 0xfffdf1f6, 0xffb96a8a, 0xffd65d93,
          0xffe795b8, "bkg_lux_pink", "lux_pink").withSurface(0xffe795b8);
    if ("lux_silver".equals(id))
      return lux(0xff33383f, 0xffc9ced6, 0xff9aa2ac, 0xff23272d, 0xff565e68, 0xff8f9aa8,
          0xffc9ced6, "bkg_lux_silver", "lux_silver").withSurface(0xffc9ced6);
    if ("lux_heart".equals(id))
      // Round 46: الوردة — زر أحمر أنيق بوردة صغيرة في رأسه، والثيم كله أحمر
      // موحّد (خلفية/مسطرة/معدِّلات/أسفل) والحروف والعناصر والاقتراحات بيضاء.
      return lux(0xff550e1c, 0xffc2102e, 0xff8f0d22, 0xffffffff, 0xffeec7ce, 0xffffffff,
          0xffc2102e, "bkg_lux_heart", "lux_heart").withSurface(0xffc2203b);
    if ("neon_city".equals(id))
      return imaged(0xff0b1220, 0x99ffffff, Color.WHITE, 0xffbfe3f5, 0xff37d0ff,
          "bkg_theme_neon_city").withKeySkin("neon_city");
    if ("galaxy".equals(id))
      return imaged(0xff120b26, 0x99ffffff, Color.WHITE, 0xffd4c2f0, 0xffa273ff,
          "bkg_theme_galaxy").withKeySkin("galaxy");
    if ("luxury".equals(id))
      return imaged(0xff15110c, 0x99f5e7c8, 0xff241a08, 0xff8a744e, 0xffd8ab4e,
          "bkg_theme_luxury").withKeySkin("luxury");
    if ("dark_minimal".equals(id))
      return imaged(0xff15181d, 0x992a2e36, Color.WHITE, 0xffaeb6c2, 0xff5da4ff,
          "custom_bg_graphite").withKeySkin("dark_minimal");
    if ("rose_gold".equals(id))
      return imagedLight(0xfff3dfe0, 0xccffffff, 0xff3a2b2d, 0xff8a6f72, 0xffc98a8e,
          "custom_bg_rose").withKeySkin("rose_gold");
    if ("ocean_night".equals(id))
      return imaged(0xff0a1526, 0x99ffffff, Color.WHITE, 0xffc4d8ee, 0xff5db8ff,
          "custom_bg_ocean").withKeySkin("ocean_night");
    if ("purple_waves".equals(id))
      return imaged(0xff1d1030, 0x99ffffff, Color.WHITE, 0xffd9c6ee, 0xffb06aff,
          "custom_bg_lavender").withKeySkin("purple_waves");
    if ("green_matrix".equals(id))
      return imaged(0xff08130c, 0x99ffffff, Color.WHITE, 0xffbfe8cf, 0xff37e08c,
          "custom_bg_forest").withKeySkin("green_matrix");
    if ("sunset_palms".equals(id))
      return imaged(0xff241423, 0x99ffffff, Color.WHITE, 0xffecc9c0, 0xffff8a5c,
          "custom_bg_sunset").withKeySkin("sunset_palms");
    if ("pearl".equals(id))
      return imagedLight(0xfff2f3f7, 0xccffffff, 0xff172033, 0xff637083, 0xff2f7de1,
          "custom_bg_aurora").withKeySkin("pearl");

    if ("samsung_white".equals(id) || "light".equals(id))
      return styled(
          0xffeef1f5,
          0xffffffff,
          0xffdbe7f7,
          0xff101820,
          0xff667382,
          0xff2f7de1,
          10f,
          0xffe7edf5,
          0xffdcecff,
          0xffd6e9ff,
          0xff2f7de1);
    if ("samsung_black".equals(id) || "dark".equals(id) || "midnight".equals(id))
      return styled(
          0xff17191d,
          0xff2b2e34,
          0xff434954,
          Color.WHITE,
          0xffc3cad4,
          0xff5da4ff,
          10f,
          0xff252930,
          0xff303741,
          0xff243f64,
          0xff3d7fd5);
    if ("samsung_blue".equals(id) || "blue".equals(id) || "ocean".equals(id))
      return styled(
          0xffdceaf8,
          0xffffffff,
          0xffc9ddf4,
          0xff13345c,
          0xff60758d,
          0xff1976d2,
          10f,
          0xffd1e5f8,
          0xffb8dcff,
          0xff9bcbf7,
          0xff1976d2);
    if ("samsung_mint".equals(id))
      return styled(
          0xffdcefe9,
          0xfff9fffd,
          0xffc8e5db,
          0xff153b31,
          0xff5d786f,
          0xff168b6b,
          11f,
          0xffcce8df,
          0xffafe0d0,
          0xff91d4be,
          0xff168b6b);

    if ("iphone_white".equals(id))
      return styled(
          0xffd1d3d9,
          0xffffffff,
          0xffb8bdc7,
          0xff111216,
          0xff666a73,
          0xff007aff,
          7f,
          0xffadb3be,
          0xffffffff,
          0xffaeb5c1,
          0xff8e95a1);
    if ("iphone_black".equals(id))
      return styled(
          0xff0b0b0d,
          0xff242428,
          0xff3a3a40,
          Color.WHITE,
          0xffc7c7cc,
          0xff0a84ff,
          7f,
          0xff343439,
          0xff2f3035,
          0xff174a73,
          0xff3a3a40);
    if ("iphone_silver".equals(id))
      return styled(
          0xffe4e6ea,
          0xfffafbfc,
          0xffcdd1d8,
          0xff202124,
          0xff6d7178,
          0xff5e6b7a,
          8f,
          0xffc4c9d1,
          0xffffffff,
          0xffb7bec8,
          0xff8f99a6);
    if ("iphone_midnight".equals(id))
      return styled(
          0xff15171b,
          0xff34363c,
          0xff4b4f58,
          0xffffffff,
          0xffc5c8cf,
          0xff64d2ff,
          8f,
          0xff292c32,
          0xff3f444d,
          0xff245b79,
          0xff43474f);

    if ("special_blue".equals(id))
      return styled(
          0xffe8f0fb,
          0xffffffff,
          0xffccdcf1,
          0xff172b46,
          0xff66778e,
          0xff246fd2,
          12f,
          0xffd6e5f8,
          0xff90c2ff,
          0xff4f95eb,
          0xff246fd2);
    if ("special_purple".equals(id))
      return styled(
          0xffeee8f5,
          0xffffffff,
          0xffdacbe9,
          0xff38234e,
          0xff766587,
          0xff8657b5,
          14f,
          0xffe0d3ed,
          0xffc8a7e6,
          0xffa978d2,
          0xff8657b5);
    if ("special_green".equals(id))
      return styled(
          0xffe4f1e8,
          0xffffffff,
          0xffcee4d5,
          0xff1e422b,
          0xff667a6c,
          0xff27864b,
          9f,
          0xffd3ead9,
          0xff9dd9ad,
          0xff61b87a,
          0xff27864b);
    if ("special_gold".equals(id))
      return styled(
          0xfff3eee2,
          0xfffffdf8,
          0xffe5d9be,
          0xff46381e,
          0xff7e725b,
          0xffb27b18,
          11f,
          0xffeadfc8,
          0xffffd783,
          0xffd49b2c,
          0xffb27b18);

    if ("amoled_black".equals(id) || "amoled".equals(id))
      return styled(
          Color.BLACK,
          0xff161719,
          0xff303238,
          Color.WHITE,
          0xffb9bec7,
          0xff35c8ff,
          6f,
          0xff202225,
          0xff282c31,
          0xff0a668b,
          0xff166b8d);
    if ("graphite".equals(id))
      return styled(
          0xff24272c,
          0xff3b3f46,
          0xff545b65,
          0xfff8f9fb,
          0xffc5cbd3,
          0xffffb74d,
          9f,
          0xff30343a,
          0xff454b54,
          0xff78511e,
          0xff6d737c);
    if ("cream".equals(id))
      return styled(
          0xfff0ece4,
          0xfffffdf9,
          0xffe3d9ca,
          0xff3c352b,
          0xff7a7062,
          0xff8f6b3e,
          10f,
          0xffe4dccf,
          0xfffff7e6,
          0xffd7c5a8,
          0xffa8865e);
    if ("contrast".equals(id))
      return styled(
          0xfff4f4f4,
          Color.WHITE,
          0xffd7d7d7,
          Color.BLACK,
          0xff555555,
          0xff0057d9,
          5f,
          0xffd9e7ff,
          0xffb8d4ff,
          0xff6ca5ff,
          0xff0057d9);

    return from("samsung_white");
  }

  /** Round 40: dark glass image theme — translucent white keys, white letters. */
  private static KeyboardTheme imaged(
      int background, int key, int text, int sub, int accent, String image) {
    return new KeyboardTheme(
        background, key, 0x73ffffff, text, sub, accent, 10f, 24f, 12f, image, .58f, 0, 0f, 0);
  }

  /** Round 40: light image theme — opaque white keys, dark letters. */
  private static KeyboardTheme imagedLight(
      int background, int key, int text, int sub, int accent, String image) {
    return new KeyboardTheme(
        background, key, 0xe6ffffff, text, sub, accent, 10f, 26f, 13f, image, .8f, 0, 0f, 0);
  }

  /** Round 41: color family theme — solid background with an image key skin. */
  private static KeyboardTheme family(
      int background, int key, int pressed, int text, int sub, int accent, int fun, String skin) {
    return styled(background, key, pressed, text, sub, accent, 7f, fun, key, fun, accent)
        .withKeySkin(skin);
  }

  /**
   * Round 42: professional wallpaper theme — full-bleed background photo with the glossy
   * 3D key skin cut from the owner's reference sheet. Keys render from the skin's
   * keybg_NAME_* drawables at full opacity; the palette drives text, accents and the
   * suggestion surface. bottom tints the modifier row (golden for the heart theme).
   */
  private static KeyboardTheme lux(
      int background,
      int key,
      int pressed,
      int text,
      int sub,
      int accent,
      int bottom,
      String image,
      String skin) {
    return new KeyboardTheme(
        background, key, pressed, text, sub, accent, 7f, 26f, 13f, image, 1f,
        bottom, bottom, bottom, accent, 0, 0f, 0, skin);
  }

  /**
   * Round 43: theme assembled from an assets/theme/(folder) config (dynamic
   * import). Same geometry contract as lux(), but every value comes from the
   * folder's JSON — the background uri points at the library ("asset:theme/...")
   * and the key skin resolves through AssetThemeLibrary.keySkinPart.
   */
  public static KeyboardTheme assetTheme(
      int background,
      int key,
      int pressed,
      int text,
      int sub,
      int accent,
      int bottom,
      float radius,
      float mainSize,
      float subSize,
      float opacity,
      float dim,
      String image,
      String skin) {
    return new KeyboardTheme(
        background, key, pressed, text, sub, accent, radius, mainSize, subSize, image, opacity,
        bottom, bottom, bottom, accent, 0, dim, 0, skin);
  }

  private static KeyboardTheme styled(
      int background,
      int key,
      int pressed,
      int text,
      int sub,
      int accent,
      float radius,
      int bottom,
      int space,
      int delete,
      int shift) {
    return new KeyboardTheme(
        background,
        key,
        pressed,
        text,
        sub,
        accent,
        radius,
        26f,
        13f,
        "",
        1f,
        bottom,
        space,
        delete,
        shift,
        0,
        0f,
        0,
        "");
  }

  public int contentColorFor(int color) {
    return luminance(color) < 142 ? Color.WHITE : 0xff172033;
  }

  /** Theme-aware suggestion lettering with a contrast fallback for imported custom themes. */
  public int suggestionTextColor() {
    return Math.abs(luminance(text) - luminance(key)) >= 72 ? text : contentColorFor(key);
  }

  private static int luminance(int color) {
    return (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
  }

  private static int blend(int first, int second, float amount) {
    float keep = 1f - amount;
    return Color.rgb(
        Math.round(Color.red(first) * keep + Color.red(second) * amount),
        Math.round(Color.green(first) * keep + Color.green(second) * amount),
        Math.round(Color.blue(first) * keep + Color.blue(second) * amount));
  }

  public String imageSource() {
    if (!imageUri.startsWith("animated:")) return imageUri;
    String value = imageUri.substring(9);
    int separator = value.indexOf(':');
    return separator < 0 ? value : value.substring(separator + 1);
  }

  private static int animationStyle(String image) {
    if (image == null || !image.startsWith("animated:")) return ANIMATION_NONE;
    String value = image.substring(9);
    int separator = value.indexOf(':');
    String style = separator < 0 ? value : value.substring(0, separator);
    if ("stars".equals(style)) return ANIMATION_STARS;
    if ("planets".equals(style)) return ANIMATION_PLANETS;
    if ("hearts".equals(style)) return ANIMATION_HEARTS;
    if ("sparkles".equals(style)) return ANIMATION_SPARKLES;
    if ("snow".equals(style)) return ANIMATION_SNOW;
    if ("bubbles".equals(style)) return ANIMATION_BUBBLES;
    if ("petals".equals(style)) return ANIMATION_PETALS;
    if ("fireflies".equals(style)) return ANIMATION_FIREFLIES;
    if ("meteors".equals(style)) return ANIMATION_SPEED;
    if ("music".equals(style)) return ANIMATION_MUSIC;
    if ("neon".equals(style)) return ANIMATION_NEON;
    if ("fireworks".equals(style)) return ANIMATION_FIREWORKS;
    return ANIMATION_PARALLAX;
  }

  public static KeyboardTheme load(Context context, String id) {
    return ThemeRepository.load(context, id);
  }
}
