package com.almlk.swiftkey.model;

public final class KeySpec {
  public static final int SHIFT = -1,
      DELETE = -2,
      ENTER = -3,
      MODE = -4,
      LANGUAGE = -5,
      EMOJI = -6,
      MIC = -7,
      TAB = -8,
      PAGE = -9,
      /** Round 58: فتح سجل الحافظة (الضغط المطوّل على زر الفاصلة). */
      CLIPBOARD = -10,
      SPACE = 32;
  public final String label, subLabel, alternatives;
  public final int code;
  public final float weight;

  /** Optional XML keyboard template used by the alternatives window. */
  public final int popupKeyboardRes;

  /** AOSP-compatible edge flags: left=1, right=2. */
  public final int edgeFlags;

  public KeySpec(String label, String subLabel, String alternatives, int code, float weight) {
    this(label, subLabel, alternatives, code, weight, 0, 0);
  }

  public KeySpec(
      String label,
      String subLabel,
      String alternatives,
      int code,
      float weight,
      int popupKeyboardRes,
      int edgeFlags) {
    this.label = label;
    this.subLabel = subLabel;
    this.alternatives = alternatives;
    this.code = code;
    this.weight = weight;
    this.popupKeyboardRes = popupKeyboardRes;
    this.edgeFlags = edgeFlags;
  }

  public static KeySpec c(String label, String sub, String alternatives) {
    return new KeySpec(label, sub, alternatives, label.codePointAt(0), 1f);
  }

  public static KeySpec s(String label, int code, float weight) {
    return new KeySpec(label, "", "", code, weight);
  }
}
