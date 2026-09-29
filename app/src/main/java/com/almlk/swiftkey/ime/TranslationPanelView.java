package com.almlk.swiftkey.ime;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.*;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.engine.LocalTranslator;
import com.almlk.swiftkey.theme.KeyboardTheme;
import com.almlk.swiftkey.util.Prefs;

public final class TranslationPanelView extends LinearLayout {
  public interface Callback {
    void onBack();

    void onInsert(String value);

    void onTranslationChanged(String value);

    void onSourceChanged(String source, String language);
  }

  private static final String[] CODES = {"ar", "en", "zh", "fr", "es", "tr", "de"},
      NAMES = {"العربية", "English", "中文", "Français", "Español", "Türkçe", "Deutsch"};
  private android.content.SharedPreferences languagePrefs;
  private TextView fromView, toView, sourceView;
  private final StringBuilder text = new StringBuilder();
  private Callback callback;
  private KeyboardTheme theme;
  private String from = "ar", to = "en", translated = "";
  private int request;
  private final android.os.Handler caretHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private boolean caretVisible = true;
  private final Runnable caretLoop =
      new Runnable() {
        public void run() {
          caretVisible = !caretVisible;
          updateSourceDisplay();
          caretHandler.postDelayed(this, 500);
        }
      };

  public TranslationPanelView(Context c, AttributeSet a) {
    super(c, a);
    init();
  }

  public TranslationPanelView(Context c) {
    super(c);
    init();
  }

  private void init() {
    inflate(getContext(), R.layout.translation_panel, this);
    languagePrefs =
        getContext().getSharedPreferences("translation_languages", Context.MODE_PRIVATE);
    from = languagePrefs.getString("from", "ar");
    to = languagePrefs.getString("to", "en");
    if (from.equals(to)) {
      from = "ar";
      to = "en";
    }
    fromView = findViewById(R.id.translation_from);
    toView = findViewById(R.id.translation_to);
    sourceView = findViewById(R.id.translation_source);
    fromView.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            from = next(from, to);
            saveLanguages();
            refresh();
          }
        });
    toView.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            to = next(to, from);
            saveLanguages();
            refresh();
          }
        });
    findViewById(R.id.translation_swap)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View v) {
                swap();
              }
            });
    findViewById(R.id.translation_close)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View v) {
                if (callback != null) callback.onBack();
              }
            });
    findViewById(R.id.translation_insert)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View v) {
                insert();
              }
            });
    applyTheme();
  }

  public void activate() {
    caretHandler.removeCallbacks(caretLoop);
    caretVisible = true;
    caretHandler.postDelayed(caretLoop, 500);
  }

  public void releaseTransientState() {
    request++;
    caretHandler.removeCallbacksAndMessages(null);
    text.setLength(0);
    translated = "";
    caretVisible = false;
    if (sourceView != null) sourceView.setText("");
    clearAnimation();
    setVisibility(GONE);
  }

  public void setCallback(Callback value) {
    callback = value;
  }

  public void setTheme(KeyboardTheme value) {
    theme = value;
    applyTheme();
  }

  private String name(String code) {
    for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return NAMES[i];
    return code;
  }

  private String next(String code, String excluded) {
    int i = 0;
    for (; i < CODES.length; i++) if (CODES[i].equals(code)) break;
    do {
      i = (i + 1) % CODES.length;
    } while (CODES[i].equals(excluded));
    return CODES[i];
  }

  public void setSource(String value) {
    text.setLength(0);
    if (value != null) text.append(value);
    from = languagePrefs.getString("from", "ar");
    to = languagePrefs.getString("to", "en");
    if (from.equals(to)) {
      from = "ar";
      to = "en";
      saveLanguages();
    }
    refresh();
  }

  public void append(String value) {
    text.append(value);
    refresh();
  }

  public void delete() {
    if (text.length() > 0) {
      int cp = text.codePointBefore(text.length());
      text.delete(text.length() - Character.charCount(cp), text.length());
      refresh();
    }
  }

  public String getSourceText() {
    return text.toString();
  }

  public String getSourceLanguage() {
    return from;
  }

  public void acceptSuggestion(String word) {
    String value = text.toString(),
        updated =
            value.replaceFirst("[^\\s،؛؟,.!?]+$", java.util.regex.Matcher.quoteReplacement(word));
    if (updated.equals(value) && !value.endsWith(" ")) updated = value + word;
    text.setLength(0);
    text.append(updated).append(' ');
    refresh();
  }

  public void insert() {
    if (callback != null) callback.onInsert(translated);
  }

  private void saveLanguages() {
    languagePrefs.edit().putString("from", from).putString("to", to).apply();
  }

  private void swap() {
    String value = from;
    from = to;
    to = value;
    saveLanguages();
    if (!translated.isEmpty()) {
      text.setLength(0);
      text.append(translated);
    }
    refresh();
  }

  private void updateSourceDisplay() {
    sourceView.setText(text.length() == 0 ? "" : text.toString() + (caretVisible ? "│" : " "));
  }

  private void refresh() {
    fromView.setText(name(from));
    toView.setText(name(to));
    updateSourceDisplay();
    final int id = ++request;
    translated = LocalTranslator.translate(text.toString(), from, to);
    if (callback != null) {
      callback.onSourceChanged(text.toString(), from);
      callback.onTranslationChanged(translated);
    }
    LocalTranslator.translateAsync(
        text.toString(),
        from,
        to,
        new LocalTranslator.TranslationCallback() {
          public void onResult(String value) {
            if (id == request && value != null && !value.isEmpty()) {
              translated = value;
              if (callback != null) callback.onTranslationChanged(value);
              ((ImageButton) findViewById(R.id.translation_insert))
                  .setContentDescription("إدراج: " + value);
            }
          }
        });
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + .5f);
  }

  private int interfaceTextColor(KeyboardTheme value) {
    return com.almlk.swiftkey.theme.ThemeChipArt.textColor(getContext(), value);
  }

  private void applyTheme() {
    KeyboardTheme t = theme == null ? KeyboardTheme.from(new Prefs(getContext()).theme()) : theme;
    // Round 52: خلفية الثيم نفسها على لوحة الترجمة كالكيبورد
    com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(this, getContext(), t);
    if (getChildCount() > 0)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(getChildAt(0), getContext(), t);
    int uiColor = interfaceTextColor(t);
    fromView.setTextColor(uiColor);
    toView.setTextColor(uiColor);
    android.graphics.drawable.GradientDrawable inputBackground =
        new android.graphics.drawable.GradientDrawable();
    inputBackground.setColor(t.key);
    inputBackground.setStroke(dp(1), t.accent);
    inputBackground.setCornerRadius(dp(18));
    View sourceContainer = (View) sourceView.getParent();
    sourceContainer.setBackground(inputBackground);
    sourceView.setTextColor(t.text);
    sourceView.setHintTextColor(t.sub);
    ((ImageButton) findViewById(R.id.translation_swap)).setColorFilter(uiColor);
    ((ImageButton) findViewById(R.id.translation_close)).setColorFilter(uiColor);
    ((ImageButton) findViewById(R.id.translation_insert)).setColorFilter(t.accent);
  }
}
