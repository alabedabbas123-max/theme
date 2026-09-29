package com.almlk.swiftkey.ime;

import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.almlk.swiftkey.engine.*;
import com.almlk.swiftkey.model.*;
import java.util.*;

public abstract class AlmlkImeRuntimePart4 extends AlmlkImeRuntimePart3 {

  public void onAlternatives(KeySpec key, String selected) {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) {
      feedback();
      ic.commitText(selected, 1);
      refreshSuggestions();
    }
  }

  public void onSpaceSwipe(int direction) {
    gestureDecodeGeneration++;
    language = "ar".equals(language) ? "en" : "ar";
    shifted = false;
    symbols = false;
    applyLayout();
    refreshSuggestions();
    if (languageIndicator != null) {
      languageIndicator.setVisibility(View.VISIBLE);
      languageIndicator.setBackgroundColor(currentTheme.accent);
      handler.removeMessages(MSG_HIDE_LANGUAGE_INDICATOR);
      handler.sendEmptyMessageDelayed(MSG_HIDE_LANGUAGE_INDICATOR, 550);
    }
  }

  public void onSpaceVerticalSwipe(boolean showNumberRow) {
    if (inputNumeric) return;
    getSharedPreferences("keyboard_ui", 0).edit().putBoolean("number_row", showNumberRow).apply();
    symbols = false;
    applyLayout();
    showMessage(showNumberRow ? "تم إظهار صف الأرقام" : "تم إخفاء صف الأرقام");
  }

  protected void handleTranslationKey(KeySpec key) {
    if (key.code == KeySpec.DELETE) translationPanel.delete();
    else if (key.code == KeySpec.SPACE) translationPanel.append(" ");
    else if (key.code == KeySpec.ENTER) translationPanel.insert();
    else if (key.code >= 0) translationPanel.append(key.label);
  }

  protected void handleEmojiSearchKey(KeySpec key) {
    if (key.code == KeySpec.DELETE) {
      if (emojiQuery.length() > 0) {
        int cp = emojiQuery.codePointBefore(emojiQuery.length());
        emojiQuery.delete(emojiQuery.length() - Character.charCount(cp), emojiQuery.length());
      }

    } else if (key.code == KeySpec.SPACE) emojiQuery.append(' ');
    else if (key.code == KeySpec.EMOJI) {
      showEmojiPanel();
      return;

    } else if (key.code == KeySpec.ENTER) {
      if (emojiSearchResults.getChildCount() > 0)
        commitEmojiResult((String) emojiSearchResults.getChildAt(0).getTag());
      return;

    } else if (key.code >= 0) emojiQuery.append(key.label);
    updateEmojiSearchHeader();
  }

  protected void updateEmojiSearchHeader() {
    final String query = emojiQuery.toString();
    final int generation = ++emojiSearchGeneration;
    emojiSearchQuery.setText(query);
    emojiSearchResults.removeAllViews();
    if (query.trim().length() == 0) return;
    handler.removeMessages(MSG_SEARCH_EMOJI);
    pendingEmojiQuery = query;
    pendingEmojiGeneration = generation;
    handler.sendEmptyMessageDelayed(MSG_SEARCH_EMOJI, 70);
  }

  protected void commitEmojiResult(String value) {
    if (value == null) return;
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) ic.commitText(value, 1);
    emojiPanel.remember(value);
  }

  protected void deletePreviousGrapheme() {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    CharSequence selected = null;
    try {
      selected = ic.getSelectedText(0);
    } catch (RuntimeException ignored) {
    }
    if (selected != null && selected.length() > 0) {
      ic.beginBatchEdit();
      boolean replaced = ic.commitText("", 1);
      ic.endBatchEdit();
      if (!replaced) {
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
      }
      return;
    }
    CharSequence before = ic.getTextBeforeCursor(32, 0);
    if (before == null || before.length() == 0) return;
    String text = before.toString();
    int end = text.length(), start = text.offsetByCodePoints(end, -1), cp = text.codePointAt(start);
    while (start > 0
        && (cp == 0xFE0F
            || cp == 0xFE0E
            || (cp >= 0x1F3FB && cp <= 0x1F3FF)
            || Character.getType(cp) == Character.NON_SPACING_MARK)) {
      start = text.offsetByCodePoints(start, -1);
      cp = text.codePointAt(start);
    }
    while (start > 0) {
      int prev = text.offsetByCodePoints(start, -1);
      if (text.codePointAt(prev) != 0x200D) break;
      start = prev;
      if (start > 0) start = text.offsetByCodePoints(start, -1);
    }
    if (cp >= 0x1F1E6 && cp <= 0x1F1FF && start > 0) {
      int prev = text.offsetByCodePoints(start, -1), p = text.codePointAt(prev);
      if (p >= 0x1F1E6 && p <= 0x1F1FF) start = prev;
    }
    ic.deleteSurroundingText(end - start, 0);
  }

  protected void performEnter(InputConnection ic) {
    EditorInfo e = getCurrentInputEditorInfo();
    int options = e == null ? 0 : e.imeOptions;
    int action = options & EditorInfo.IME_MASK_ACTION;
    boolean explicit =
        action == EditorInfo.IME_ACTION_GO
            || action == EditorInfo.IME_ACTION_SEARCH
            || action == EditorInfo.IME_ACTION_SEND
            || action == EditorInfo.IME_ACTION_NEXT
            || action == EditorInfo.IME_ACTION_DONE
            || action == EditorInfo.IME_ACTION_PREVIOUS;
    if (!multilineField && (options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 && explicit)
      ic.performEditorAction(action);
    else ic.commitText("\n", 1);
  }

  protected boolean completeWithSpace(InputConnection connection) {
    if (connection == null || passwordField || !prefs.spaceAutocomplete()) return false;
    String current = WordComposer.current(connection);
    if (current.length() == 0) return false;
    String completion = engine.expandShortcut(current, language);
    if (completion.length() == 0 && prefs.autocomplete() && current.equals(latestSuggestionInput)) {
      completion = latestSuggestion;
    }
    if (completion.length() == 0) return false;
    String previous = WordComposer.previous(connection);
    String previous2 = WordComposer.previous2(connection);
    WordComposer.replaceCurrent(connection, completion, true);
    if (prefs.learning() && completion.indexOf(' ') < 0) {
      learnAsync(previous2, previous, completion, language);
    }
    latestSuggestionInput = "";
    latestSuggestion = "";
    refreshSuggestions();
    return true;
  }

  protected void commitFinishedWord() {
    if (passwordField) return;
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    String word = WordComposer.current(ic);
    if (word.isEmpty()) return;
    String previous = WordComposer.previous(ic);
    String previous2 = WordComposer.previous2(ic);
    if (prefs.autocorrect()) {
      String corrected = engine.cachedAutocorrection(word, language);
      List<String> context = WordComposer.previousWords(ic, 4);
      if (!corrected.equals(word)
          && engine.isConfidentAutocorrection(word, corrected, context, language)) {
        WordComposer.replaceCurrent(ic, corrected, false);
        word = corrected;
      }
    }
    if (prefs.learning() && word.indexOf(' ') < 0) {
      learnAsync(previous2, previous, word, language);
    }
  }

  /** Shows two complete words, the beginning of the third, then an ellipsis. */
  private String suggestionLabel(String value) {
    if (value == null) return "";
    String clean = value.trim();
    if (clean.length() == 0) return "";
    String[] parts = clean.split("\\s+", -1);
    if (parts.length <= 2) return clean;
    String third = parts[2];
    int codePoints = third.codePointCount(0, third.length());
    int prefixCount = Math.min(3, codePoints);
    int prefixEnd = third.offsetByCodePoints(0, prefixCount);
    return parts[0] + " " + parts[1] + " " + third.substring(0, prefixEnd) + "...";
  }

  protected void addSuggestion(
      final String word, final boolean translation, final boolean correction) {
    SuggestionChipView chip = new SuggestionChipView(this);
    chip.setCorrection(correction);
    chip.setText(suggestionLabel(word));
    chip.setContentDescription(word);
    chip.setTextSize(17);
    chip.setTextColor(
        android.content.res.ColorStateList.valueOf(
            word.isEmpty() ? Color.TRANSPARENT : suggestionTextColor()));
    chip.setAlpha(1f);
    chip.setEnabled(true);
    chip.setGravity(Gravity.CENTER);
    chip.setSingleLine(true);
    chip.setHorizontallyScrolling(false);
    chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
    chip.setPadding(dp(5), 0, dp(5), 0);
    chip.setBackground(suggestionBackground());
    if (!word.isEmpty()) {
      chip.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View v) {
              if (translation) translationPanel.acceptSuggestion(word);
              else acceptSuggestion(word);
            }
          });
      if (!translation)
        chip.setOnLongClickListener(
            new View.OnLongClickListener() {
              public boolean onLongClick(View v) {
                showSuggestionEditor(v, word);
                return true;
              }
            });
    }
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
    p.setMargins(dp(2), 0, dp(2), 0);
    suggestions.addView(chip, p);
  }

  protected void addEmojiSuggestion(final String emoji) {
    TextView chip = new TextView(this);
    chip.setText(emoji);
    chip.setTextSize(22);
    chip.setGravity(Gravity.CENTER);
    chip.setSingleLine(true);
    chip.setContentDescription("اقتراح إيموجي " + emoji);
    chip.setBackground(suggestionBackground());
    chip.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null) return;
            CharSequence before = ic.getTextBeforeCursor(1, 0);
            String prefix =
                before != null
                        && before.length() > 0
                        && !Character.isWhitespace(before.charAt(before.length() - 1))
                    ? " "
                    : "";
            ic.commitText(prefix + emoji + " ", 1);
            emojiPanel.remember(emoji);
            refreshSuggestions();
          }
        });
    LinearLayout.LayoutParams p =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
    p.setMargins(dp(2), 0, dp(2), 0);
    suggestions.addView(chip, p);
  }

  /**
   * Draws the three-dot accuracy marker under the fixed center suggestion only when the center
   * holds a real priority suggestion for the word being typed. Echo, filler, default and empty
   * slots never get dots, so the marker is not permanently visible.
   */
  protected void markCenterSuggestionBest(boolean hasPriority) {
    if (!hasPriority || suggestions == null || suggestions.getChildCount() < 2) return;
    View center = suggestions.getChildAt(1);
    if (center instanceof SuggestionChipView) {
      ((SuggestionChipView) center).setBest(true, suggestionTextColor());
    }
  }

  /** Keeps the configured letter color unless it becomes unreadable over the suggestion key. */
  private int suggestionTextColor() {
    return currentTheme.suggestionTextColor();
  }

  protected void updateTranslationSuggestions(String source, String sourceLanguage) {
    suggestions.removeAllViews();
    String[] parts = source.split("[\\s،؛؟,.!?]+", -1);
    String current = parts.length == 0 ? "" : parts[parts.length - 1];
    List<String> words = engine.suggest(current, "", "ar".equals(sourceLanguage) ? "ar" : "en");
    String best = words.size() > 0 ? words.get(0) : "";
    String second = words.size() > 1 ? words.get(1) : "";
    String third = words.size() > 2 ? words.get(2) : "";
    addSuggestion(second, true, false);
    addSuggestion(best, true, false);
    addSuggestion(third, true, false);
    markCenterSuggestionBest(current.length() > 0 && best.length() > 0);
  }
}
