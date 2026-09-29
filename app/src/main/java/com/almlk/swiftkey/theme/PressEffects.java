package com.almlk.swiftkey.theme;

/**
 * Round 56: كتالوج تفاعلات الضغط المتحركة — التصاميم التي تتنثر فوق
 * المفتاح لحظة ضغطه (قلوب ترتفع، بوسات، نجوم، شرارات متطايرة...).
 * كل تصميم صورة pressfx_ID.png في drawable-nodpi، والسلوك نوعان:
 * صاعد يتهادى لأعلى (قلوب/ثلج/فقاعات...) أو منفجر دائرياً (شرارات/قصاصات...).
 */
public final class PressEffects {
  public static final int COUNT = 10;

  /** أسماء الأصول بالترتيب (1..COUNT). */
  public static final String[] IDS = {
    "heart", "kiss", "star", "spark", "snow",
    "bubble", "petal", "flame", "note", "confetti"
  };

  /** التسميات العربية المعروضة في بطاقات الاستوديو. */
  public static final String[] NAMES = {
    "قلوب", "بوسات", "نجوم", "شرارات", "ثلج",
    "فقاعات", "بتلات", "لهب", "نوتات", "قصاصات"
  };

  /** true = جسيمات تتهادى صاعدة، false = انفجار دائري سريع. */
  public static final boolean[] RISE = {
    true, true, true, false, true,
    true, true, false, false, false
  };

  private PressEffects() {}

  /** اسم drawable لتصميم تفاعل (1..COUNT). */
  public static String artName(int effect) {
    if (effect < 1 || effect > COUNT) return "";
    return "pressfx_" + IDS[effect - 1];
  }

  /** سلوك التصميم: صاعد أم منفجر (التصاميم المحمّلة صاعدة افتراضياً). */
  public static boolean rises(int effect) {
    if (effect < 1 || effect > COUNT) return true;
    return RISE[effect - 1];
  }
}
