package com.almlk.swiftkey.engine;

import android.view.inputmethod.InputConnection;

/** Extracts and replaces the complete word around the cursor. */
public final class WordComposer {
  private WordComposer() {}

  public static String current(InputConnection connection) {
    if (connection == null) return "";
    CharSequence beforeValue = connection.getTextBeforeCursor(160, 0);
    CharSequence afterValue = connection.getTextAfterCursor(160, 0);
    String before = beforeValue == null ? "" : beforeValue.toString();
    String after = afterValue == null ? "" : afterValue.toString();
    int left = wordLengthBefore(before);
    int right = wordLengthAfter(after);
    String value = before.substring(before.length() - left) + after.substring(0, right);
    return TextNormalizer.clean(value);
  }

  /** Replaces the whole word, not only the characters located before the cursor. */
  public static void replaceCurrent(
      InputConnection connection, String replacement, boolean appendSpace) {
    if (connection == null || replacement == null) return;
    replacement = TextNormalizer.inlineText(replacement);
    if (replacement.length() == 0) return;
    CharSequence selected = connection.getSelectedText(0);
    CharSequence afterValue = connection.getTextAfterCursor(160, 0);
    String after = afterValue == null ? "" : afterValue.toString();
    if (selected != null && selected.length() > 0) {
      int boundaryLength = appendSpace ? separatorPrefixLength(after, 0) : 0;
      String boundary = boundaryLength > 0 ? after.substring(0, boundaryLength) : "";
      connection.beginBatchEdit();
      // Round 70: لا نستدعي finishComposingText قبل الحذف — قد يسبب أسطراً فارغة
      if (boundaryLength > 0) connection.deleteSurroundingText(0, boundaryLength);
      connection.commitText(replacement, 1);
      if (appendSpace) connection.commitText(boundaryLength > 0 ? boundary : " ", 1);
      connection.endBatchEdit();
      return;
    }
    CharSequence beforeValue = connection.getTextBeforeCursor(160, 0);
    String before = beforeValue == null ? "" : beforeValue.toString();
    int left = wordLengthBefore(before);
    int right = wordLengthAfter(after);
    int boundaryLength = appendSpace ? separatorPrefixLength(after, right) : 0;
    String boundary = boundaryLength > 0 ? after.substring(right, right + boundaryLength) : "";
    connection.beginBatchEdit();
    // Round 70: لا نستدعي finishComposingText قبل الحذف — قد يسبب أسطراً فارغة
    if (left > 0 || right > 0 || boundaryLength > 0) {
      connection.deleteSurroundingText(left, right + boundaryLength);
    }
    connection.commitText(replacement, 1);
    if (appendSpace) connection.commitText(boundaryLength > 0 ? boundary : " ", 1);
    connection.endBatchEdit();
  }

  /** Guarantees that the cursor is after a visible word boundary in restrictive editors. */
  private static void ensureTrailingSpace(InputConnection connection) {
    CharSequence value = connection.getTextBeforeCursor(2, 0);
    if (value == null || value.length() == 0) {
      connection.commitText(" ", 1);
      return;
    }
    int last = Character.codePointBefore(value, value.length());
    if (!Character.isWhitespace(last)) connection.commitText(" ", 1);
  }

  public static String previous(InputConnection connection) {
    String[] words = wordsBeforeCursor(connection, 320, false);
    return words.length == 0 ? "" : TextNormalizer.lookup(words[words.length - 1]);
  }

  public static String previous2(InputConnection connection) {
    String[] words = wordsBeforeCursor(connection, 420, false);
    return words.length < 2 ? "" : TextNormalizer.lookup(words[words.length - 2]);
  }

  public static java.util.List<String> previousWords(InputConnection connection, int limit) {
    String[] words = wordsBeforeCursor(connection, Math.max(420, limit * 80), false);
    java.util.ArrayList<String> result = new java.util.ArrayList<String>();
    int start = Math.max(0, words.length - Math.max(1, limit));
    for (int index = start; index < words.length; index++) {
      String value = TextNormalizer.lookup(words[index]);
      if (value.length() > 0) result.add(value);
    }
    return result;
  }

  public static java.util.List<String> followingWords(InputConnection connection, int limit) {
    java.util.ArrayList<String> result = new java.util.ArrayList<String>();
    if (connection == null) return result;
    CharSequence value = connection.getTextAfterCursor(Math.max(320, limit * 80), 0);
    if (value == null || value.length() == 0) return result;
    String text = value.toString();
    int openLength = wordLengthAfter(text);
    if (openLength > 0) text = text.substring(openLength);
    text = text.trim();
    if (text.length() == 0) return result;
    String[] words = text.split("[\\s،؛؟,.!?]+", -1);
    for (int index = 0; index < words.length && result.size() < Math.max(1, limit); index++) {
      String clean = TextNormalizer.lookup(words[index]);
      if (clean.length() > 0) result.add(clean);
    }
    return result;
  }

  private static String[] wordsBeforeCursor(
      InputConnection connection, int maximum, boolean includeOpenWord) {
    if (connection == null) return new String[0];
    CharSequence value = connection.getTextBeforeCursor(maximum, 0);
    if (value == null || value.length() == 0) return new String[0];
    String text = value.toString();
    if (!includeOpenWord) {
      int openLength = wordLengthBefore(text);
      if (openLength > 0) text = text.substring(0, text.length() - openLength);
    }
    text = text.trim();
    return text.length() == 0 ? new String[0] : text.split("[\\s،؛؟,.!?]+", -1);
  }

  /** Returns the existing separator after a word so it can be re-committed after the choice. */
  private static int separatorPrefixLength(String text, int start) {
    int index = Math.max(0, start);
    while (index < text.length()) {
      int codePoint = text.codePointAt(index);
      if (isWordCodePoint(codePoint)) break;
      index += Character.charCount(codePoint);
    }
    return index - Math.max(0, start);
  }

  private static int wordLengthBefore(String text) {
    int index = text.length();
    while (index > 0) {
      int codePoint = text.codePointBefore(index);
      if (!isWordCodePoint(codePoint)) break;
      index -= Character.charCount(codePoint);
    }
    return text.length() - index;
  }

  private static int wordLengthAfter(String text) {
    int index = 0;
    while (index < text.length()) {
      int codePoint = text.codePointAt(index);
      if (!isWordCodePoint(codePoint)) break;
      index += Character.charCount(codePoint);
    }
    return index;
  }

  private static boolean isWordCodePoint(int codePoint) {
    int type = Character.getType(codePoint);
    return Character.isLetterOrDigit(codePoint)
        || type == Character.NON_SPACING_MARK
        || type == Character.COMBINING_SPACING_MARK
        || codePoint == '\''
        || codePoint == 0x2019
        || codePoint == 0x0640;
  }
}
