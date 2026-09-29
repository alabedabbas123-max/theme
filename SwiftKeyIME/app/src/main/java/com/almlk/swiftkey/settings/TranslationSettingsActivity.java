package com.almlk.swiftkey.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;

/** Default translation direction, stored in the same file the keyboard panel reads. */
public final class TranslationSettingsActivity extends AppCompatActivity {
  private static final String[] CODES = {"ar", "en", "zh", "fr", "es", "tr", "de"};
  private static final String[] NAMES = {
    "العربية", "English", "中文", "Français", "Español", "Türkçe", "Deutsch"
  };

  private SharedPreferences languages;
  private Button fromButton;
  private Button toButton;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_translation_settings);
    languages = getSharedPreferences("translation_languages", Context.MODE_PRIVATE);

    TextView title = (TextView) findViewById(R.id.settings_page_title);
    title.setText("الترجمة");
    findViewById(R.id.settings_page_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });

    fromButton = (Button) findViewById(R.id.translation_from);
    toButton = (Button) findViewById(R.id.translation_to);
    fromButton.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            pickLanguage(true);
          }
        });
    toButton.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            pickLanguage(false);
          }
        });
    findViewById(R.id.translation_swap)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                String from = languages.getString("from", "ar");
                String to = languages.getString("to", "en");
                languages.edit().putString("from", to).putString("to", from).apply();
                refreshLabels();
              }
            });
    refreshLabels();
  }

  private void pickLanguage(final boolean pickFrom) {
    String current = languages.getString(pickFrom ? "from" : "to", pickFrom ? "ar" : "en");
    int checked = 0;
    for (int i = 0; i < CODES.length; i++) {
      if (CODES[i].equals(current)) {
        checked = i;
      }
    }
    new AlertDialog.Builder(this)
        .setTitle(pickFrom ? "الترجمة من" : "الترجمة إلى")
        .setSingleChoiceItems(
            NAMES,
            checked,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                String picked = CODES[which];
                String oldFrom = languages.getString("from", "ar");
                String oldTo = languages.getString("to", "en");
                String newFrom = pickFrom ? picked : oldFrom;
                String newTo = pickFrom ? oldTo : picked;
                if (newFrom.equals(newTo)) {
                  if (pickFrom) {
                    newTo = oldFrom;
                  } else {
                    newFrom = oldTo;
                  }
                }
                languages.edit().putString("from", newFrom).putString("to", newTo).apply();
                refreshLabels();
                dialog.dismiss();
              }
            })
        .setNegativeButton("إلغاء", null)
        .show();
  }

  private void refreshLabels() {
    fromButton.setText(name(languages.getString("from", "ar")));
    toButton.setText(name(languages.getString("to", "en")));
  }

  private String name(String code) {
    for (int i = 0; i < CODES.length; i++) {
      if (CODES[i].equals(code)) {
        return NAMES[i];
      }
    }
    return code;
  }
}
