package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.model.KeySpec;
import com.almlk.swiftkey.model.KeyboardLayout;
import com.almlk.swiftkey.model.KeyboardLayouts;
import com.almlk.swiftkey.settings.ThemeThumbnailView;
import com.almlk.swiftkey.theme.KeyboardTheme;
import java.util.List;

/**
 * In-keyboard layouts panel opened from the tools tile التخطيطات, mirroring the reference
 * screenshot: blue header with the back chevron, the bold التخطيطات title and the موافق action,
 * one horizontal strip of miniature keyboard cards (each card is the approved theme-style
 * thumbnail fed with that layout's exact key placement) with a dark ✓ badge on the selected
 * card, and the English (US) / العربية language tabs at the bottom. Selecting a card stores the
 * variant in the same keyboard_ui prefs the IME resync reads and re-flows the keyboard live.
 */
public final class LayoutsPanelView extends LinearLayout {
  public interface Listener {
    String selectedVariant(boolean arabicGroup);

    void onLayoutSelected(String variantName, boolean arabicGroup);

    void onClose();

    /** موافق: confirm + keep the selection, close the panel; never leaves the keyboard. */
    void onConfirmSelection();
  }

  private final LinearLayout cards;
  private final TextView englishTab;
  private final TextView arabicTab;
  private final View englishLine;
  private final View arabicLine;
  private TextView confirmAction;
  private TextView titleView;
  private TextView backView;
  private LinearLayout headerView;
  private LinearLayout tabsView;
  private KeyboardTheme theme = KeyboardTheme.from("light");
  private Listener listener;
  private boolean arabicGroup;
  private android.widget.LinearLayout.LayoutParams scrollerParams;
  private int keyboardHeightPx;

  public LayoutsPanelView(Context context) {
    super(context);
    cards = new LinearLayout(context);
    englishTab = new TextView(context);
    arabicTab = new TextView(context);
    englishLine = new View(context);
    arabicLine = new View(context);
    build();
  }

  public LayoutsPanelView(Context context, AttributeSet attrs) {
    super(context, attrs);
    cards = new LinearLayout(context);
    englishTab = new TextView(context);
    arabicTab = new TextView(context);
    englishLine = new View(context);
    arabicLine = new View(context);
    build();
  }

  public void setListener(Listener value) {
    listener = value;
  }

  public void open(boolean arabic, int keyboardHeight) {
    arabicGroup = arabic;
    keyboardHeightPx = Math.max(0, keyboardHeight);
    applyPanelHeight();
    render();
  }

  public void setTheme(KeyboardTheme value) {
    if (value == null) return;
    theme = value;
    applyTheme();
    render();
  }

  private void applyTheme() {
    com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(this, getContext(), theme);
    if (headerView != null) com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(headerView, getContext(), theme);
    if (tabsView != null) tabsView.setBackgroundColor(theme.surface);
    int ui = interfaceTextColor();
    if (confirmAction != null) confirmAction.setTextColor(ui);
    if (titleView != null) titleView.setTextColor(ui);
    if (backView != null) backView.setTextColor(ui); // سهم الرجوع يتبع لون الحروف
  }

  private int interfaceTextColor() {
    return com.almlk.swiftkey.theme.ThemeChipArt.textColor(getContext(), theme);
  }

  private int blend(int first, int second, float amount) {
    float keep = 1f - amount;
    return android.graphics.Color.rgb(
        Math.round(android.graphics.Color.red(first) * keep + android.graphics.Color.red(second) * amount),
        Math.round(android.graphics.Color.green(first) * keep + android.graphics.Color.green(second) * amount),
        Math.round(android.graphics.Color.blue(first) * keep + android.graphics.Color.blue(second) * amount));
  }

  /** The panel fills exactly the keyboard's area: header + flexible cards + tabs. */
  private void applyPanelHeight() {
    int header = dp(46);
    int tabs = dp(44);
    int scroller =
        keyboardHeightPx > header + tabs + dp(60)
            ? keyboardHeightPx - header - tabs
            : dp(150);
    scrollerParams.height = scroller;
    // LayoutParams has no requestLayout(): ask the view itself to re-lay out.
    requestLayout();
  }

  private void build() {
    setOrientation(VERTICAL);
    setBackgroundColor(0xff5d8fc9);

    headerView = new LinearLayout(getContext());
    headerView.setOrientation(HORIZONTAL);
    headerView.setGravity(Gravity.CENTER_VERTICAL);
    addView(headerView, new LayoutParams(LayoutParams.MATCH_PARENT, dp(46)));

    confirmAction = new TextView(getContext());
    confirmAction.setText("\u0645\u0648\u0627\u0641\u0642");
    confirmAction.setTextColor(0xffffffff);
    confirmAction.setTextSize(16);
    confirmAction.setTypeface(Typeface.DEFAULT_BOLD);
    confirmAction.setPadding(dp(14), 0, dp(14), 0);
    confirmAction.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            if (listener != null) {
              listener.onConfirmSelection();
            }
          }
        });
    headerView.addView(confirmAction);

    View headerGap = new View(getContext());
    LayoutParams gapParams = new LayoutParams(0, 1, 1f);
    headerView.addView(headerGap, gapParams);

    titleView = new TextView(getContext());
    titleView.setText("\u0627\u0644\u062a\u062e\u0637\u064a\u0637\u0627\u062a");
    titleView.setTextColor(0xffffffff);
    titleView.setTextSize(19);
    titleView.setTypeface(Typeface.DEFAULT_BOLD);
    headerView.addView(
        titleView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

    backView = new TextView(getContext());
    backView.setText("\u203a");
    backView.setContentDescription("\u0631\u062c\u0648\u0639");
    backView.setTextColor(0xffffffff);
    backView.setTextSize(26);
    backView.setGravity(Gravity.CENTER);
    backView.setPadding(dp(14), 0, dp(12), 0);
    backView.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            if (listener != null) {
              listener.onClose();
            }
          }
        });
    headerView.addView(backView);

    HorizontalScrollView scroller = new HorizontalScrollView(getContext());
    scroller.setHorizontalScrollBarEnabled(false);
    scrollerParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(150));
    addView(scroller, scrollerParams);
    cards.setOrientation(HORIZONTAL);
    cards.setPadding(dp(10), dp(4), dp(10), dp(4));
    cards.setGravity(Gravity.CENTER_VERTICAL);
    scroller.addView(
        cards,
        new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

    tabsView = new LinearLayout(getContext());
    tabsView.setOrientation(HORIZONTAL);
    tabsView.setBackgroundColor(0xffeef3fb);
    addView(tabsView, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));
    tabsView.addView(buildTab(true));
    tabsView.addView(buildTab(false));
    applyTheme();
    render();
  }

  private View buildTab(final boolean english) {
    LinearLayout column = new LinearLayout(getContext());
    column.setOrientation(VERTICAL);
    column.setGravity(Gravity.CENTER_HORIZONTAL);
    TextView label = english ? englishTab : arabicTab;
    label.setText(english ? "English (US)" : "\u0627\u0644\u0639\u0631\u0628\u064a\u0629");
    label.setGravity(Gravity.CENTER);
    label.setTextSize(16);
    column.addView(
        label, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    View line = english ? englishLine : arabicLine;
    column.addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, dp(3)));
    column.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            arabicGroup = !english;
            render();
          }
        });
    LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
    column.setLayoutParams(params);
    return column;
  }

  private void render() {
    int ui = interfaceTextColor();
    int muted = blend(ui, theme.surface, .45f);
    englishTab.setTextColor(arabicGroup ? muted : ui);
    arabicTab.setTextColor(arabicGroup ? ui : muted);
    englishTab.setTypeface(arabicGroup ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
    arabicTab.setTypeface(arabicGroup ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    englishLine.setBackgroundColor(arabicGroup ? 0x00000000 : theme.accent);
    arabicLine.setBackgroundColor(arabicGroup ? theme.accent : 0x00000000);
    cards.removeAllViews();
    String selected = listener == null ? "" : listener.selectedVariant(arabicGroup);
    KeyboardTheme cardTheme = theme;
    KeyboardLayouts.Entry[] entries = KeyboardLayouts.forLanguage(arabicGroup);
    for (KeyboardLayouts.Entry entry : entries) {
      cards.addView(buildCard(entry, entry.name.equals(selected), cardTheme));
    }
  }

  private View buildCard(
      final KeyboardLayouts.Entry entry, boolean selected, KeyboardTheme theme) {
    FrameLayout card = new FrameLayout(getContext());
    int scrollerH = scrollerParams.height > 0 ? scrollerParams.height : dp(150);
    int cardHeight = scrollerH - dp(10);
    int fullWidth = Math.round(cardHeight * 1.5f);
    int maxWidth = Math.round(getResources().getDisplayMetrics().widthPixels * 0.80f);
    int cardWidth = Math.min(fullWidth, maxWidth);
    LayoutParams cardParams =
        new LayoutParams(cardWidth, cardHeight);
    cardParams.leftMargin = dp(6);
    cardParams.rightMargin = dp(6);
    cardParams.gravity = Gravity.CENTER_VERTICAL;
    card.setLayoutParams(cardParams);
    card.setBackgroundResource(R.drawable.bg_layout_card);

    LinearLayout stack = new LinearLayout(getContext());
    stack.setOrientation(VERTICAL);
    stack.setPadding(dp(10), dp(9), dp(10), dp(3));
    card.addView(
        stack,
        new FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

    ThemeThumbnailView thumb = new ThemeThumbnailView(getContext());
    thumb.setTheme(theme);
    applyPreview(thumb, entry);
    stack.addView(
        thumb,
        new LayoutParams(
            LayoutParams.MATCH_PARENT, cardHeight - Math.round(cardWidth * 0.16f) - dp(12)));

    TextView caption = new TextView(getContext());
    caption.setText(entry.title);
    caption.setTextColor(selected ? theme.accent : interfaceTextColor());
    caption.setTextSize(13);
    caption.setTypeface(Typeface.create("sans-serif", selected ? Typeface.BOLD : Typeface.NORMAL));
    caption.setGravity(Gravity.CENTER);
    caption.setSingleLine(true);
    stack.addView(
        caption,
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    if (selected) {
      TextView badge = new TextView(getContext());
      badge.setText("\u2713");
      badge.setTextColor(theme.contentColorFor(theme.accent));
      badge.setTextSize(16);
      badge.setGravity(Gravity.CENTER);
      GradientDrawable circle = new GradientDrawable();
      circle.setShape(GradientDrawable.OVAL);
      circle.setColor(theme.accent);
      badge.setBackground(circle);
      FrameLayout.LayoutParams badgeParams =
          new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER);
      card.addView(badge, badgeParams);
    }

    card.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            if (listener != null) {
              listener.onLayoutSelected(entry.name, arabicGroup);
              render();
            }
          }
        });
    return card;
  }

  /** Same miniature feed the approved settings screen uses: exact labels from the layout XML. */
  private void applyPreview(ThemeThumbnailView thumb, KeyboardLayouts.Entry entry) {
    KeyboardLayout layout =
        KeyboardXmlParser.load(getContext(), entry.xmlResource, entry.arabic);
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
      for (int col = 0; col < columns; col++) {
        KeySpec key = keys.get(col);
        String label = key.label == null ? "" : key.label;
        if (key.code == KeySpec.SPACE && label.trim().length() == 0) {
          label = entry.arabic ? "\u0627\u0644\u0639\u0631\u0628\u064a\u0629" : "English (US)";
        }
        labels[row][col] = label;
        trails[row][col] = key.subLabel == null ? "" : key.subLabel.replace("\\", "");
        codes[row][col] = key.code;
        weights[row][col] = key.weight > 0f ? key.weight : 1f;
      }
    }
    thumb.setPreviewData(labels, trails, codes, weights);
  }

  private int dp(int value) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(value * density);
  }
}
