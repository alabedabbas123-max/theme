package com.almlk.swiftkey.theme;

/**
 * Round 50: the ten professional button FRAMES. Every frame is REAL IMAGE art
 * (res/drawable-nodpi/keyframe_ID_R.png) drawn once per key shape, so the studio
 * swaps the picture dynamically when the owner picks another shape - never a
 * shape/xml/GradientDrawable stand-in. Frame 0 means "no frame" (the classic
 * programmatic key face); frames 1..10 map to the art below.
 */
public final class KeyFrames {
  public static final int COUNT = 10;

  /** Art ids in frame order (1..COUNT). */
  public static final String[] IDS = {
    "neon", "gold", "glass", "silver", "rose",
    "carbon", "emerald", "sapphire", "royal", "sunset"
  };

  /** Arabic labels shown under the studio cards. */
  public static final String[] NAMES = {
    "نيون", "ذهبي", "زجاجي", "فضي", "وردي",
    "كربون", "زمردي", "ياقوتي", "ملكي", "غروب"
  };

  /**
   * Letter color each frame's art needs on the keys (the image IS the key face,
   * like the lux skins - the theme text color would fight the art).
   */
  private static final int[] TEXT_COLORS = {
    0xffeafcff, 0xff3b2a05, 0xff172033, 0xff1d2126, 0xff48102c,
    0xffffffff, 0xff032a1b, 0xffeaf2ff, 0xfff7ecc9, 0xff47200a
  };

  /** The six studio shapes, same radii as CustomThemeActivity.SHAPE_RADII. */
  public static final int[] SHAPE_RADII = {0, 5, 10, 17, 28, 36};

  private KeyFrames() {}

  /** Letter color for a frame id (1..COUNT); falls back to near-black. */
  public static int textColor(int frame) {
    if (frame < 1 || frame > COUNT) return 0xff172033;
    return TEXT_COLORS[frame - 1];
  }

  /** Drawable name of a frame's art in the shape closest to radiusDp. */
  public static String artName(int frame, float radiusDp) {
    if (frame < 1 || frame > COUNT) return "";
    return "keyframe_" + IDS[frame - 1] + "_" + nearestRadius(radiusDp);
  }

  /** Drawable name of the neutral key OUTLINE art used by the shape strip. */
  public static String shapeName(float radiusDp) {
    return "keyshape_" + nearestRadius(radiusDp);
  }

  /** Nearest studio shape radius (28 and 36 both clamp to the capsule art). */
  private static int nearestRadius(float radiusDp) {
    int best = SHAPE_RADII[0];
    float bestDistance = Math.abs(radiusDp - SHAPE_RADII[0]);
    for (int i = 1; i < SHAPE_RADII.length; i++) {
      float distance = Math.abs(radiusDp - SHAPE_RADII[i]);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = SHAPE_RADII[i];
      }
    }
    return best;
  }
}
