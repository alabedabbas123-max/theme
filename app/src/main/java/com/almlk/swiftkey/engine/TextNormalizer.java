package com.almlk.swiftkey.engine;

import java.util.Locale;

/** Language-aware normalization used by learning, completion and typo comparison. */
public final class TextNormalizer {
  private TextNormalizer() {}

  public static String clean(String value) {
    if (value == null) {
      return "";
    }
    String result = value.trim();
    result = result.replace("ـ", "");
    result = result.replaceAll("[\\u064B-\\u065F\\u0670]", "");
    result = result.replaceAll("^[\\p{Punct}،؛؟]+|[\\p{Punct}،؛؟]+$", "");
    return result;
  }

  /**
   * Collapses every whitespace run, including line breaks and tabs, into one plain space so a
   * committed suggestion can never move the cursor to a new line. Learned words left over from
   * older builds may still carry line breaks; this keeps them safe to show, insert and re-learn.
   */
  public static String inlineText(String value) {
    if (value == null) return "";
    StringBuilder out = new StringBuilder(value.length());
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      offset += Character.charCount(codePoint);
      if (Character.isWhitespace(codePoint)) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') out.append(' ');
      } else {
        out.appendCodePoint(codePoint);
      }
    }
    return out.toString().trim();
  }

  public static String lookup(String value) {
    return clean(value).toLowerCase(Locale.ROOT);
  }

  public static String foldForComparison(String value, String language) {
    String result = lookup(value);
    if ("ar".equals(language)) {
      result = result.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا');
      result = result.replace('ى', 'ي').replace('ئ', 'ي').replace('ی', 'ي');
      result = result.replace('ؤ', 'و').replace('ة', 'ه').replace('ک', 'ك');
    }
    return result;
  }

  public static String languageOf(String word) {
    return word != null && word.matches(".*[\\u0600-\\u06FF].*") ? "ar" : "en";
  }
}
