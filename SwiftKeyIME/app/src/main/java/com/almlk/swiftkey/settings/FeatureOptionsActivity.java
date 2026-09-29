package com.almlk.swiftkey.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;

/**
 * Base for the per-feature settings pages (اختصارات / قواميس / حافظة). Every
 * feature gets its own standalone settings screen — nothing here is merged into
 * the typing settings — and rows are rendered in the same rounded cards as the
 * manager lists.
 */
public abstract class FeatureOptionsActivity extends AppCompatActivity {

  /** Callback for a card with a switch. */
  public interface OnToggle {
    void onToggle(boolean value);
  }

  /** Callback for a tappable action card. */
  public interface OnAction {
    void onAction();
  }

  protected abstract CharSequence screenTitle();

  protected abstract void buildOptions(LinearLayout container);

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_feature_options);
    ((TextView) findViewById(R.id.settings_page_title)).setText(screenTitle());
    findViewById(R.id.settings_page_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });
    buildOptions((LinearLayout) findViewById(R.id.options_container));
  }

  protected View switchCard(
      Context context, String title, String subtitle, boolean checked, final OnToggle listener) {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setBackgroundResource(R.drawable.bg_settings_card);
    attachCardParams(card);

    card.addView(textColumn(context, title, subtitle));

    final androidx.appcompat.widget.SwitchCompat toggle =
        new androidx.appcompat.widget.SwitchCompat(context);
    toggle.setChecked(checked);
    LinearLayout.LayoutParams toggleParams =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    toggleParams.leftMargin = dp(10);
    toggle.setLayoutParams(toggleParams);
    toggle.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            listener.onToggle(toggle.isChecked());
          }
        });
    card.addView(toggle);
    return card;
  }

  protected View actionCard(Context context, String title, String subtitle, final OnAction action) {
    LinearLayout card = new LinearLayout(context);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackgroundResource(R.drawable.bg_settings_card);
    card.setClickable(true);
    card.setFocusable(true);
    attachCardParams(card);
    card.addView(textColumn(context, title, subtitle));
    card.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            action.onAction();
          }
        });
    return card;
  }

  protected View noteCard(Context context, String text) {
    TextView note = new TextView(context);
    note.setText(text);
    note.setTextColor(0xff6B7484);
    note.setTextSize(12);
    note.setGravity(Gravity.RIGHT);
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(10);
    params.bottomMargin = dp(4);
    params.leftMargin = dp(6);
    params.rightMargin = dp(6);
    note.setLayoutParams(params);
    return note;
  }

  private LinearLayout textColumn(Context context, String title, String subtitle) {
    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams columnParams =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    column.setLayoutParams(columnParams);

    TextView titleView = new TextView(context);
    titleView.setText(title);
    titleView.setTextColor(0xff172033);
    titleView.setTextSize(16);
    titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
    column.addView(titleView);

    if (subtitle != null && subtitle.length() > 0) {
      TextView subtitleView = new TextView(context);
      subtitleView.setText(subtitle);
      subtitleView.setTextColor(0xff6B7484);
      subtitleView.setTextSize(12);
      LinearLayout.LayoutParams subtitleParams =
          new LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      subtitleParams.topMargin = dp(3);
      subtitleView.setLayoutParams(subtitleParams);
      column.addView(subtitleView);
    }
    return column;
  }

  private void attachCardParams(View card) {
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(6);
    params.bottomMargin = dp(6);
    card.setLayoutParams(params);
  }

  protected int dp(int value) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(value * density);
  }
}
