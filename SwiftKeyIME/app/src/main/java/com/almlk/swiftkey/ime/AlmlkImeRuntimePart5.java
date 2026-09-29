package com.almlk.swiftkey.ime;

import android.graphics.Color;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.almlk.swiftkey.engine.*;
import com.almlk.swiftkey.model.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

public abstract class AlmlkImeRuntimePart5 extends AlmlkImeRuntimePart4 {

  public void onGesturePreview(final GestureTrace gesture) {
    decodeGesture(gesture, false);
  }

  public void onGesture(final GestureTrace gesture) {
    decodeGesture(gesture, true);
  }

  public void onGestureCancelled() {
    gestureDecodeGeneration++;
    gestureDecoder.getQueue().clear();
    refreshSuggestions();
  }

  private void decodeGesture(final GestureTrace gesture, final boolean released) {
    if (!dispatcherActive
        || passwordField
        || inputNumeric
        || symbols
        || translationMode
        || emojiSearchMode
        || gesture == null
        || gesture.size() < 2) return;
    final InputConnection connection = getCurrentInputConnection();
    if (connection == null) return;
    final String tracedLetters = visibleGestureLetters(gesture.crossedKeys());
    if (!released) {
      showGestureTraceSuggestions(tracedLetters, Collections.<String>emptyList());
    }
    final String previous = WordComposer.previous(connection);
    final String previous2 = WordComposer.previous2(connection);
    final String gestureLanguage = language;
    final int generation = ++gestureDecodeGeneration;
    gestureDecoder.getQueue().clear();
    gestureDecoder.execute(
        new Runnable() {
          public void run() {
            List<String> decoded;
            try {
              decoded =
                  released
                      ? engine.suggestGesture(gesture, previous, previous2, gestureLanguage)
                      : engine.suggestGesturePreview(gesture, previous, previous2, gestureLanguage);
            } catch (RuntimeException error) {
              decoded = Collections.<String>emptyList();
            }
            final List<String> matches = decoded;
            postUi(
                new Runnable() {
                  public void run() {
                    if (generation != gestureDecodeGeneration
                        || !gestureLanguage.equals(language)
                        || matches == null) return;
                    if (released
                        && matches.size() > 0
                        && prefs.gestureAutoInsert()
                        && engine.isConfidentGestureCandidate(
                            gesture, matches.get(0), gestureLanguage)) {
                      acceptSuggestion(matches.get(0));
                    } else {
                      showGestureTraceSuggestions(tracedLetters, matches);
                    }
                  }
                });
          }
        });
  }

  private void showGestureTraceSuggestions(String tracedLetters, List<String> matches) {
    if (suggestions == null) return;
    suggestions.removeAllViews();
    String trace = tracedLetters == null ? "" : tracedLetters;
    int slots = 3;
    if (trace.length() > 0) {
      addSuggestion(trace, false, false);
      View traceChip = suggestions.getChildAt(suggestions.getChildCount() - 1);
      if (traceChip != null) {
        traceChip.setOnClickListener(null);
        traceChip.setOnLongClickListener(null);
        traceChip.setEnabled(false);
        traceChip.setAlpha(.78f);
        traceChip.setContentDescription("الحروف التي مر عليها مسار الإيماءة");
      }
      slots--;
    }
    LinkedHashSet<String> unique = new LinkedHashSet<String>();
    if (matches != null) unique.addAll(matches);
    if (trace.length() > 0) unique.remove(trace);
    for (String match : unique) {
      if (slots <= 0) break;
      addSuggestion(match, false, false);
      slots--;
    }
    while (slots > 0) {
      addSuggestion("", false, false);
      slots--;
    }
    markCenterSuggestionBest(centerHoldsMatch());
  }

  /** The gesture dots appear only when the center holds a real decoded match. */
  private boolean centerHoldsMatch() {
    if (suggestions == null || suggestions.getChildCount() < 2) return false;
    View center = suggestions.getChildAt(1);
    if (!(center instanceof TextView) || !center.isEnabled()) return false;
    CharSequence text = ((TextView) center).getText();
    return text != null && text.length() > 0;
  }

  private String visibleGestureLetters(String crossedKeys) {
    if (crossedKeys == null || crossedKeys.length() == 0) return "";
    StringBuilder output = new StringBuilder();
    int count = 0;
    for (int offset = 0; offset < crossedKeys.length() && count < 40; ) {
      int codePoint = crossedKeys.codePointAt(offset);
      offset += Character.charCount(codePoint);
      if (Character.isLetter(codePoint)) {
        output.appendCodePoint(codePoint);
        count++;
      }
    }
    return output.toString();
  }

  protected void refreshSuggestions() {
    if (!dispatcherActive) return;
    suggestionGeneration++;
    if (suggestions != null) {
      for (int index = 0; index < suggestions.getChildCount(); index++) {
        // Keep the active theme color visible while the next generation is loading.
        suggestions.getChildAt(index).setClickable(false);
      }
    }
    handler.removeMessages(MSG_UPDATE_SUGGESTIONS);
    handler.removeMessages(MSG_UPDATE_OLD_SUGGESTIONS);
    handler.sendEmptyMessageDelayed(MSG_UPDATE_SUGGESTIONS, 24);
  }

  protected void refreshSuggestionsNow() {
    if (suggestions == null) return;
    if (inputNumeric) {
      suggestions.removeAllViews();
      return;
    }
    if (translationMode) {
      updateTranslationSuggestions(
          translationPanel.getSourceText(), translationPanel.getSourceLanguage());
      return;
    }
    final InputConnection connection = getCurrentInputConnection();
    if (connection == null) return;
    final String current = WordComposer.current(connection);
    final List<String> history = WordComposer.previousWords(connection, 4);
    final List<String> following = WordComposer.followingWords(connection, 2);
    final String previous = history.size() == 0 ? "" : history.get(history.size() - 1);
    final String requestLanguage = language;
    final int generation = suggestionGeneration;
    if (passwordField) {
      suggestions.removeAllViews();
      return;
    }
    suggestionWorker.getQueue().clear();
    try {
      suggestionWorker.execute(
          new Runnable() {
            public void run() {
              List<String> result;
              String expansion;
              try {
                // The suggestion strip is always active. Autocomplete only controls automatic
                // acceptance behavior; it must never hide normal word suggestions.
                result = engine.suggest(current, history, following, requestLanguage);
                expansion = engine.expandShortcut(current, requestLanguage);
              } catch (RuntimeException ignored) {
                result = Collections.<String>emptyList();
                expansion = "";
              }
              final List<String> words = result;
              final String shortcutExpansion = expansion == null ? "" : expansion;
              postUi(
                  new Runnable() {
                    public void run() {
                      if (generation != suggestionGeneration
                          || !requestLanguage.equals(language)
                          || translationMode
                          || emojiSearchMode
                          || passwordField) return;
                      InputConnection activeConnection = getCurrentInputConnection();
                      if (activeConnection == null
                          || !current.equals(WordComposer.current(activeConnection))) return;
                      renderSuggestionResults(current, previous, words, shortcutExpansion);
                    }
                  });
            }
          });
    } catch (RejectedExecutionException ignored) {
    }
  }

  private boolean isCorrection(String current, String typedFold, String value) {
    if (current.length() == 0 || value == null || value.length() == 0) return false;
    String folded = TextNormalizer.foldForComparison(value, language);
    return !folded.startsWith(typedFold);
  }

  private void renderSuggestionResults(
      String current, String previous, List<String> words, String shortcutExpansion) {
    if (suggestions == null) return;
    suggestions.removeAllViews();
    latestSuggestionInput = current;
    latestSuggestion = "";
    ArrayList<String> visibleWords = new ArrayList<String>();
    if (words != null) {
      for (String candidate : words) {
        if (candidate != null
            && candidate.length() > 0
            && !visibleWords.contains(candidate)
            && visibleWords.size() < 3) {
          visibleWords.add(candidate);
        }
      }
    }
    if (current.length() == 0) {
      String[] defaults =
          "ar".equals(language)
              ? new String[] {"مرحباً", "شكراً", "نعم"}
              : new String[] {"the", "I", "and"};
      for (int index = 0; index < defaults.length && visibleWords.size() < 3; index++) {
        if (!visibleWords.contains(defaults[index])) visibleWords.add(defaults[index]);
      }
    } else if (visibleWords.size() == 0) {
      visibleWords.add(current);
    }
    while (visibleWords.size() < 3) visibleWords.add("");
    String typedFold = TextNormalizer.foldForComparison(current, language);
    for (int index = 0; index < 3; index++) {
      String value = visibleWords.get(index);
      String valueFold = TextNormalizer.foldForComparison(value, language);
      if (latestSuggestion.length() == 0
          && current.length() >= 2
          && valueFold.startsWith(typedFold)
          && !valueFold.equals(typedFold)) {
        latestSuggestion = value;
      }
    }

    // Keep exactly three equal slots. The highest-ranked word or shortcut is always in the center.
    // While a word is being typed, the typed word itself owns the third slot so the user can
    // always keep the exact spelling; suggestions and emoji never crowd it out. When the typed
    // word is already visible in another slot (echo of a dictionary hit), the third slot keeps
    // its normal content instead of repeating it.
    String best = visibleWords.get(0);
    String second = visibleWords.get(1);
    String third = visibleWords.get(2);
    String typedEcho = "";
    if (current.length() > 0
        && !current.equals(best)
        && !current.equals(second)
        && !current.equals(third)) {
      typedEcho = current;
    }
    addSuggestion(second, false, isCorrection(current, typedFold, second));
    addSuggestion(best, false, isCorrection(current, typedFold, best));
    // Round 70: الإيموجي يظهر دائماً أثناء الكتابة — يأخذ مكان typedEcho
    // أو يُضاف كشارة رابعة عند وجود مطابقة
    String emoji =
        prefs.emojiSuggestions()
            ? emojiPanel.suggestEmoji(current.length() > 0 ? current : previous)
            : "";
    if (emoji.length() > 0) {
      addEmojiSuggestion(emoji);
    } else if (typedEcho.length() > 0) {
      addSuggestion(typedEcho, false, false);
    } else {
      addSuggestion(third, false, isCorrection(current, typedFold, third));
    }
    markCenterSuggestionBest(hasPrioritySuggestion(current, typedFold, best, shortcutExpansion));
  }

  /**
   * The dots mean "this will replace or complete the word being typed". They appear only while a
   * word is being typed and the center holds a genuine priority suggestion: a completion, a close
   * correction, or a shortcut expansion. Echo, filler, default and empty slots never get dots, so
   * the marker is not permanently visible.
   */
  private boolean hasPrioritySuggestion(
      String current, String typedFold, String best, String shortcutExpansion) {
    if (current.length() == 0
        || best.length() == 0
        || best.equals(current)
        || typedFold.length() == 0) {
      return false;
    }
    if (shortcutExpansion.length() > 0 && best.equals(shortcutExpansion)) {
      return true;
    }
    String bestFold = TextNormalizer.foldForComparison(best, language);
    if (bestFold.startsWith(typedFold) && !bestFold.equals(typedFold)) {
      return true;
    }
    return isCloseCorrection(typedFold, bestFold);
  }

  /** Near-miss corrections share a folded prefix (hamza/yaa tolerant) and similar length. */
  private boolean isCloseCorrection(String typedFold, String bestFold) {
    int typedOffset = 0;
    int bestOffset = 0;
    int common = 0;
    while (typedOffset < typedFold.length() && bestOffset < bestFold.length()) {
      int typedCodePoint = typedFold.codePointAt(typedOffset);
      int bestCodePoint = bestFold.codePointAt(bestOffset);
      if (typedCodePoint != bestCodePoint) {
        break;
      }
      common++;
      typedOffset += Character.charCount(typedCodePoint);
      bestOffset += Character.charCount(bestCodePoint);
    }
    if (common < 1) {
      return false;
    }
    int typedCount = typedFold.codePointCount(0, typedFold.length());
    int bestCount = bestFold.codePointCount(0, bestFold.length());
    return Math.abs(typedCount - bestCount) <= 2;
  }

  protected void showSuggestionEditor(View anchor, final String original) {
    feedback();
    final String suggestionLanguage = language;
    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(14), dp(12), dp(14), dp(12));
    android.graphics.drawable.GradientDrawable background =
        new android.graphics.drawable.GradientDrawable();
    background.setColor(currentTheme.background);
    background.setStroke(dp(1), currentTheme.accent);
    background.setCornerRadius(dp(12));
    panel.setBackground(background);
    TextView title = new TextView(this);
    title.setText("تعديل الاقتراح أو حذفه");
    title.setTextColor(currentTheme.text);
    title.setTextSize(14);
    title.setGravity(Gravity.CENTER);
    final EditText editor = new EditText(this);
    editor.setSingleLine(true);
    editor.setText(original);
    editor.setSelection(editor.length());
    editor.setTextColor(currentTheme.text);
    editor.setHintTextColor(currentTheme.sub);
    LinearLayout actions = new LinearLayout(this);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    final PopupWindow popup =
        new PopupWindow(panel, dp(280), ViewGroup.LayoutParams.WRAP_CONTENT, true);
    TextView delete = new TextView(this);
    delete.setText("حذف");
    delete.setTextColor(0xffe53935);
    delete.setTextSize(16);
    delete.setGravity(Gravity.CENTER);
    TextView save = new TextView(this);
    save.setText("حفظ التعديل");
    save.setTextColor(currentTheme.accent);
    save.setTextSize(16);
    save.setGravity(Gravity.CENTER);
    actions.addView(delete, new LinearLayout.LayoutParams(0, dp(46), 1f));
    actions.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1f));
    panel.addView(
        title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
    panel.addView(
        editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
    panel.addView(actions);
    popup.setOutsideTouchable(true);
    popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
    popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    delete.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            popup.dismiss();
            new Thread(
                    new Runnable() {
                      public void run() {
                        engine.deleteSuggestion(original, suggestionLanguage);
                        postUi(
                            new Runnable() {
                              public void run() {
                                refreshSuggestions();
                                showMessage("تم حذف الاقتراح");
                              }
                            });
                      }
                    },
                    "Almlk-Suggestion-Delete")
                .start();
          }
        });
    save.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            final String changed = editor.getText().toString().trim();
            if (changed.length() < 2) return;
            popup.dismiss();
            new Thread(
                    new Runnable() {
                      public void run() {
                        engine.editSuggestion(original, changed, suggestionLanguage);
                        postUi(
                            new Runnable() {
                              public void run() {
                                refreshSuggestions();
                                showMessage("تم تعديل الاقتراح");
                              }
                            });
                      }
                    },
                    "Almlk-Suggestion-Edit")
                .start();
          }
        });
    popup.showAsDropDown(anchor, 0, -dp(170));
    editor.requestFocus();
  }

  protected void acceptSuggestion(String word) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    // Round 70: تنظيف الكلمة من أسطر جديدة ومسافات زائدة قبل الإدخال
    word = TextNormalizer.inlineText(word);
    if (word.length() == 0) return;
    String current = WordComposer.current(ic),
        prev = WordComposer.previous(ic),
        prev2 = WordComposer.previous2(ic);
    WordComposer.replaceCurrent(ic, word, true);
    if (prefs.learning() && word.indexOf(' ') < 0) learnAsync(prev2, prev, word, language);
    refreshSuggestions();
  }

  protected void feedback() {
    if (prefs.sound()) {
      AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
      if (audio != null) {
        audio.playSoundEffect(AudioManager.FX_KEY_CLICK, prefs.soundVolume() / 100f);
      }
    }
    if (prefs.vibration()) {
      android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
      if (vibrator != null && vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= 26) {
          vibrator.vibrate(
              VibrationEffect.createOneShot(prefs.vibrationDuration(), prefs.vibrationStrength()));
        } else {
          vibrator.vibrate(prefs.vibrationDuration());
        }
      }
    }
  }

  protected android.graphics.drawable.Drawable suggestionBackground() {
    // Round 69: عند تفعيل إطار زر (محمّل أو مدمج) تحمل الشارة فن الزر نفسه —
    // قصّاً متمركزاً بزوايا مدورة — فيتطابق شريط الاقتراحات مع الكيبورد،
    // ولون حروفها لون حروف الزر (applySuggestionTheme).
    com.almlk.swiftkey.theme.ThemeChipArt frameChip =
        com.almlk.swiftkey.theme.ThemeChipArt.forTheme(this, currentTheme);
    if (frameChip != null) return frameChip;
    // Round 47: مربع الاقتراح زر كيبورد كامل — لمعة علوية وجسم وعمق سفلي
    // بنفس صيغة أزرار الكيبورد (highlight .34 / depth .22) ونصف قطر المفتاح.
    android.graphics.drawable.GradientDrawable bg =
        new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {
              blendChannel(currentTheme.key, Color.WHITE, .34f),
              currentTheme.key,
              currentTheme.key,
              blendChannel(currentTheme.key, Color.BLACK, .22f),
            });
    bg.setStroke(dp(1), currentTheme.sub & 0x88ffffff);
    // Round 52: الرقاقة تتبع شكل الزر الحقيقي بلا أرضية — صفر يبقى حاداً،
    // والكبسولة (28/36) تُقص عند 14 ليبقى مقياس الرقاقة مقروءاً.
    bg.setCornerRadius(dp((int) Math.min(14f, currentTheme.keyRadiusDp)));
    return bg;
  }

  private static int blendChannel(int base, int toward, float amount) {
    int keep = Math.round((1f - amount) * 255f);
    int add = Math.round(amount * 255f);
    int r = (Color.red(base) * keep + Color.red(toward) * add) / 255;
    int g = (Color.green(base) * keep + Color.green(toward) * add) / 255;
    int b = (Color.blue(base) * keep + Color.blue(toward) * add) / 255;
    return Color.rgb(r, g, b);
  }

  protected int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + .5f);
  }
}
