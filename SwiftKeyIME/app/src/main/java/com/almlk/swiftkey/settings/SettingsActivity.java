package com.almlk.swiftkey.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.diagnostics.ErrorLogActivity;

/** "More settings" home screen: grouped rows that open each settings page. */
public final class SettingsActivity extends AppCompatActivity {

  private static final int[] GROUP_SIZES = {4, 4, 3, 1};

  private static final int[] ICONS = {
    R.drawable.ic_keyboard,
    R.drawable.ic_correct,
    R.drawable.ic_gesture,
    R.drawable.ic_mic,
    R.drawable.ic_palette,
    R.drawable.ic_music,
    R.drawable.ic_font,
    R.drawable.ic_toolbar_add,
    R.drawable.ic_shortcut,
    R.drawable.ic_book,
    R.drawable.ic_clipboard,
    R.drawable.ic_report
  };

  private static final String[] TITLES = {
    "اللغات ولوحة المفاتيح",
    "التصحيح و الترشيح",
    "الإيماءات",
    "الكتابة بالصوت",
    "السمات",
    "الصوت والاهتزاز",
    "الخط",
    "تخصيص شريط الادوات",
    "الاختصارات",
    "القواميس والنسخ الاحتياطي",
    "الحافظة",
    "الابلاغ عن مشكلة\nو اقتراح ميزة"
  };

  private static final Class<?>[] TARGETS = {
    LanguageSettingsActivity.class,
    SuggestionSettingsActivity.class,
    GestureSettingsActivity.class,
    VoiceSettingsActivity.class,
    ThemeSettingsActivity.class,
    FeedbackSettingsActivity.class,
    FontSettingsActivity.class,
    ToolbarSettingsActivity.class,
    ShortcutSettingsActivity.class,
    DictionarySettingsActivity.class,
    ClipboardSettingsActivity.class,
    ErrorLogActivity.class
  };

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_settings);
    AppGate.ensureInternet(this); // Round 57: تفعيل صلاحية الإنترنت عند فتح التطبيق
    LinearLayout groups = (LinearLayout) findViewById(R.id.settings_groups);
    int index = 0;
    for (int group = 0; group < GROUP_SIZES.length; group++) {
      LinearLayout card = buildCard();
      for (int row = 0; row < GROUP_SIZES[group]; row++) {
        if (row > 0) {
          card.addView(buildDivider());
        }
        card.addView(buildRow(ICONS[index], TITLES[index], TARGETS[index]));
        index++;
      }
      groups.addView(card);
    }
    SettingsTabs.bind(this, SettingsTabs.TAB_MORE);
  }

  private LinearLayout buildCard() {
    LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setBackgroundColor(0xffffffff);
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.topMargin = dp(12);
    card.setLayoutParams(params);
    return card;
  }

  private View buildDivider() {
    View divider = new View(this);
    divider.setBackgroundColor(0xffe8e8e8);
    divider.setLayoutParams(
        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    return divider;
  }

  private View buildRow(int icon, String title, final Class<?> target) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setBackgroundColor(0xffffffff);
    row.setClickable(true);
    row.setFocusable(true);
    int padding = dp(16);
    row.setPadding(padding, dp(6), padding, dp(6));
    row.setMinimumHeight(dp(58));

    ImageView image = new ImageView(this);
    image.setImageResource(icon);
    LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(30), dp(30));
    imageParams.leftMargin = dp(14);
    image.setLayoutParams(imageParams);
    row.addView(image);

    TextView text = new TextView(this);
    text.setText(title);
    text.setTextSize(16);
    text.setTextColor(0xff212121);
    LinearLayout.LayoutParams textParams =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    text.setLayoutParams(textParams);
    row.addView(text);

    ImageView chevron = new ImageView(this);
    chevron.setImageResource(R.drawable.ic_chevron_left);
    chevron.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
    row.addView(chevron);

    row.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View clicked) {
            startActivity(new Intent(SettingsActivity.this, target));
          }
        });
    return row;
  }

  private int dp(int value) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(value * density);
  }
}
