package com.almlk.swiftkey.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.model.KeyboardLayouts;
import com.almlk.swiftkey.util.Prefs;
import java.util.List;
import java.util.Locale;

/** Installed-language page matching the supplied SwiftKey reference. */
public final class LanguageSettingsActivity extends AppCompatActivity {
  private LinearLayout installedContainer;
  private LinearLayout numberPreview;
  private Prefs prefs;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_languages);
    installedContainer = (LinearLayout) findViewById(R.id.installed_languages);
    findViewById(R.id.languages_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });
    findViewById(R.id.languages_edit)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                Toast.makeText(
                        LanguageSettingsActivity.this,
                        "استخدم زر الإعدادات لتغيير تخطيط اللغة أو إزالتها",
                        Toast.LENGTH_SHORT)
                    .show();
              }
            });
    findViewById(R.id.add_language)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                startActivity(new Intent(LanguageSettingsActivity.this, AddLanguageActivity.class));
              }
            });
    findViewById(R.id.open_typing_settings)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                startActivity(
                    new Intent(LanguageSettingsActivity.this, TypingSettingsActivity.class));
              }
            });
    findViewById(R.id.open_layouts_settings)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                startActivity(
                    new Intent(LanguageSettingsActivity.this, LayoutsSettingsActivity.class));
              }
            });
    prefs = new Prefs(this);
    numberPreview = (LinearLayout) findViewById(R.id.number_row_preview);
    bindNumberRow();
  }

  protected void onResume() {
    super.onResume();
    renderInstalledLanguages();
    refreshNumberRow();
  }

  private void bindNumberRow() {
    Switch numberRow = (Switch) findViewById(R.id.languages_number_row);
    numberRow.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            getSharedPreferences("keyboard_ui", 0).edit().putBoolean("number_row", checked).apply();
          }
        });
    RadioGroup digits = (RadioGroup) findViewById(R.id.number_digits_group);
    digits.setOnCheckedChangeListener(
        new RadioGroup.OnCheckedChangeListener() {
          public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (checkedId == R.id.number_digits_arabic) {
              prefs.setDigitType(1);
            } else if (checkedId == R.id.number_digits_western) {
              prefs.setDigitType(0);
            } else {
              prefs.setDigitType(2);
            }
            renderNumberPreview();
          }
        });
    refreshNumberRow();
  }

  private void refreshNumberRow() {
    Switch numberRow = (Switch) findViewById(R.id.languages_number_row);
    numberRow.setChecked(getSharedPreferences("keyboard_ui", 0).getBoolean("number_row", false));
    RadioGroup digits = (RadioGroup) findViewById(R.id.number_digits_group);
    int setting = prefs.digitType();
    if (setting == 1) {
      digits.check(R.id.number_digits_arabic);
    } else if (setting == 0) {
      digits.check(R.id.number_digits_western);
    } else {
      digits.check(R.id.number_digits_auto);
    }
    renderNumberPreview();
  }

  private void renderNumberPreview() {
    numberPreview.removeAllViews();
    String[] values = previewDigits();
    for (String value : values) {
      TextView key = new TextView(this);
      key.setText(value);
      key.setTextSize(20);
      key.setTextColor(0xff212121);
      key.setGravity(Gravity.CENTER);
      key.setBackgroundResource(R.drawable.bg_digit_key);
      LinearLayout.LayoutParams params =
          new LinearLayout.LayoutParams(0, dp(52), 1);
      params.leftMargin = dp(2);
      params.rightMargin = dp(2);
      key.setLayoutParams(params);
      numberPreview.addView(key);
    }
  }

  private String[] previewDigits() {
    String[] western = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    String[] eastern = {"١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠"};
    int setting = prefs.digitType();
    if (setting == 1) {
      return eastern;
    }
    if (setting == 0) {
      return western;
    }
    return LanguagePreferences.installed(this).contains("ar") ? eastern : western;
  }

  private int dp(int value) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(value * density);
  }

  private void renderInstalledLanguages() {
    installedContainer.removeAllViews();
    List<String> values = LanguagePreferences.installed(this);
    for (String code : values) addLanguageRow(code);
  }

  private void addLanguageRow(final String code) {
    View row =
        getLayoutInflater().inflate(R.layout.item_installed_language, installedContainer, false);
    TextView name = (TextView) row.findViewById(R.id.language_name);
    TextView layout = (TextView) row.findViewById(R.id.language_layout);
    name.setText(displayName(code));
    layout.setText(layoutName(code));
    row.findViewById(R.id.language_options)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                showLanguageOptions(code);
              }
            });
    installedContainer.addView(row);
  }

  private String displayName(String code) {
    if ("ar".equals(code)) return "العربية";
    if ("en".equals(code)) return "English (US)";
    Locale locale = new Locale(code);
    String arabic = locale.getDisplayLanguage(new Locale("ar"));
    String nativeName = locale.getDisplayLanguage(locale);
    if (arabic == null || arabic.length() == 0) arabic = nativeName;
    return arabic;
  }

  private String layoutName(String code) {
    if ("ar".equals(code)) {
      String layout =
          getSharedPreferences("keyboard_ui", 0).getString("arabic_layout", "ARABIC_DIGITS");
      return "تخطيط: " + KeyboardLayouts.title(layout);
    }
    if ("en".equals(code)) {
      String layout = getSharedPreferences("keyboard_ui", 0).getString("layout", "QWERTY");
      return "تخطيط: " + KeyboardLayouts.title(layout);
    }
    return "تخطيط: قياسي متعدد اللغات";
  }

  /** Lists every registered layout of the language and stores the chosen preference name. */
  private void showLayoutDialog(final boolean arabic) {
    final String[] names = KeyboardLayouts.names(arabic);
    CharSequence[] titles = new CharSequence[names.length];
    for (int i = 0; i < names.length; i++) {
      titles[i] = KeyboardLayouts.title(names[i]);
    }
    new AlertDialog.Builder(this)
        .setTitle(arabic ? "تخطيط العربية" : "تخطيط English (US)")
        .setItems(
            titles,
            new android.content.DialogInterface.OnClickListener() {
              public void onClick(android.content.DialogInterface dialog, int which) {
                getSharedPreferences("keyboard_ui", 0)
                    .edit()
                    .putString(arabic ? "arabic_layout" : "layout", names[which])
                    .apply();
                renderInstalledLanguages();
              }
            })
        .setNegativeButton("إغلاق", null)
        .show();
  }

  private void showLanguageOptions(final String code) {
    if ("en".equals(code)) {
      showLayoutDialog(false);
      return;
    }
    if ("ar".equals(code)) {
      showLayoutDialog(true);
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle(displayName(code))
        .setItems(
            new String[] {"تخطيط قياسي متعدد اللغات", "إزالة اللغة"},
            new android.content.DialogInterface.OnClickListener() {
              public void onClick(android.content.DialogInterface dialog, int which) {
                if (which == 1) {
                  LanguagePreferences.remove(LanguageSettingsActivity.this, code);
                  renderInstalledLanguages();
                }
              }
            })
        .setNegativeButton("إغلاق", null)
        .show();
  }
}
