package com.almlk.swiftkey.settings;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.ime.KeyboardXmlParser;
import com.almlk.swiftkey.model.KeySpec;
import com.almlk.swiftkey.model.KeyboardLayout;
import com.almlk.swiftkey.model.KeyboardLayouts;
import com.almlk.swiftkey.theme.KeyboardTheme;
import com.almlk.swiftkey.util.Prefs;
import java.util.List;

/**
 * Layout picker matching the theme screen: every card is the very same miniature keyboard that
 * the theme cards draw (ThemeThumbnailView) rendered with the currently applied theme. Only the
 * key placement changes from layout to layout, read straight from the layout XML files.
 */
public final class LayoutsSettingsActivity extends AppCompatActivity {
  private static final String PREFS_FILE = "keyboard_ui";
  private static final String DEFAULT_ENGLISH = "QWERTY";
  private static final String DEFAULT_ARABIC = "ARABIC_DIGITS";

  private LinearLayout cards;
  private TextView englishTab;
  private TextView arabicTab;
  private View englishLine;
  private View arabicLine;
  private Prefs prefs;
  private boolean arabicGroup;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_layouts);
    prefs = new Prefs(this);
    arabicGroup = getIntent().getBooleanExtra("arabic", false);
    cards = (LinearLayout) findViewById(R.id.layouts_cards);
    englishTab = (TextView) findViewById(R.id.layouts_tab_english);
    arabicTab = (TextView) findViewById(R.id.layouts_tab_arabic);
    englishLine = findViewById(R.id.layouts_tab_english_line);
    arabicLine = findViewById(R.id.layouts_tab_arabic_line);
    findViewById(R.id.layouts_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });
    findViewById(R.id.layouts_add)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                startActivity(new Intent(LayoutsSettingsActivity.this, AddLanguageActivity.class));
              }
            });
    englishTab.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            arabicGroup = false;
            render();
          }
        });
    arabicTab.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            arabicGroup = true;
            render();
          }
        });
    render();
  }

  protected void onResume() {
    super.onResume();
    render();
  }

  private void render() {
    englishTab.setTextColor(arabicGroup ? 0xffd6e3f2 : 0xffffffff);
    arabicTab.setTextColor(arabicGroup ? 0xffffffff : 0xffd6e3f2);
    englishLine.setBackgroundColor(arabicGroup ? 0x00ffffff : 0xffffffff);
    arabicLine.setBackgroundColor(arabicGroup ? 0xffffffff : 0x00ffffff);
    cards.removeAllViews();
    String selected = selectedName();
    KeyboardLayouts.Entry[] entries = KeyboardLayouts.forLanguage(arabicGroup);
    for (KeyboardLayouts.Entry entry : entries) {
      cards.addView(buildCard(entry, entry.name.equals(selected)));
    }
  }

  private String selectedName() {
    String key = arabicGroup ? "arabic_layout" : "layout";
    String fallback = arabicGroup ? DEFAULT_ARABIC : DEFAULT_ENGLISH;
    return getSharedPreferences(PREFS_FILE, 0).getString(key, fallback);
  }

  private View buildCard(final KeyboardLayouts.Entry entry, boolean selected) {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    int cardWidth = Math.round(getResources().getDisplayMetrics().widthPixels * 0.62f);
    LinearLayout.LayoutParams cardParams =
        new LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
    cardParams.leftMargin = dp(8);
    cardParams.rightMargin = dp(8);
    cardParams.gravity = Gravity.CENTER_VERTICAL;
    card.setLayoutParams(cardParams);
    card.setBackgroundResource(R.drawable.bg_layout_card);

    ThemeThumbnailView thumb = new ThemeThumbnailView(this);
    LinearLayout.LayoutParams thumbParams =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, Math.round(cardWidth * 0.62f));
    int pad = dp(6);
    thumbParams.setMargins(pad, pad, pad, 0);
    card.addView(thumb, thumbParams);
    thumb.setTheme(KeyboardTheme.load(this, prefs.theme()));
    applyLayout(thumb, entry);

    TextView caption = new TextView(this);
    caption.setText((selected ? "\u25c9  " : "\u25cb  ") + entry.title);
    caption.setTextColor(selected ? 0xff3265e8 : 0xff21314a);
    caption.setTextSize(15);
    caption.setTypeface(Typeface.create("sans-serif", selected ? Typeface.BOLD : Typeface.NORMAL));
    caption.setGravity(Gravity.CENTER);
    caption.setPadding(0, dp(10), 0, dp(12));
    card.addView(
        caption,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    card.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            getSharedPreferences(PREFS_FILE, 0)
                .edit()
                .putString(arabicGroup ? "arabic_layout" : "layout", entry.name)
                .apply();
            render();
          }
        });
    return card;
  }

  /** Feeds the miniature with this layout's exact key placement, labels and secondary rows. */
  private void applyLayout(ThemeThumbnailView thumb, KeyboardLayouts.Entry entry) {
    KeyboardLayout layout = KeyboardXmlParser.load(this, entry.xmlResource, entry.arabic);
    int rows = layout.rows.size();
    String[][] labels = new String[rows][];
    String[][] trails = new String[rows][];
    int[][] codes = new int[rows][];
    float[][] weights = new float[rows][];
    for (int row = 0; row < rows; row++) {
      List<KeySpec> keys = layout.rows.get(row);
      int columns = keys.size();
      labels[row] = new String[columns];
      trails[row] = new String[columns];
      codes[row] = new int[columns];
      weights[row] = new float[columns];
      boolean last = row == rows - 1;
      for (int col = 0; col < columns; col++) {
        KeySpec key = keys.get(col);
        String label = key.label == null ? "" : key.label;
        if (key.code == KeySpec.SPACE && label.trim().length() == 0) {
          label = entry.arabic ? "العربية" : "English (US)";
        }
        if (label.trim().length() == 0 && key.code != KeySpec.SHIFT && key.code != KeySpec.TAB) {
          label = iconlessFallback(key);
        }
        labels[row][col] = label;
        trails[row][col] = key.subLabel == null ? "" : key.subLabel.replace("\\", "");
        codes[row][col] = key.code;
        weights[row][col] = key.weight > 0f ? key.weight : 1f;
      }
      if (last) {
        normalizeBottom(weights[row]);
      }
    }
    thumb.setPreviewData(labels, trails, codes, weights);
  }

  private String iconlessFallback(KeySpec key) {
    switch (key.code) {
      case KeySpec.MODE:
        return "123";
      case KeySpec.LANGUAGE:
        return "\u21b9";
      default:
        return "";
    }
  }

  /** Bottom rows keep the theme-card silhouette: side keys, then a wide space bar. */
  private void normalizeBottom(float[] weights) {
    if (weights.length != 6) {
      return;
    }
    for (int i = 0; i < weights.length; i++) {
      weights[i] = i == 3 ? 3.8f : i == 0 ? 1.2f : i == 5 ? 1.25f : 1f;
    }
  }

  private int dp(int value) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(value * density);
  }
}
