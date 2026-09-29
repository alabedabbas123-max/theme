package com.almlk.swiftkey.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.util.Prefs;

/** Correction and prediction switches. Text shortcuts moved to ShortcutSettingsActivity. */
public final class SuggestionSettingsActivity extends AppCompatActivity {
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_suggestion_settings);
    final Prefs prefs = new Prefs(this);

    TextView title = (TextView) findViewById(R.id.settings_page_title);
    title.setText("التصحيح والترشيح");
    findViewById(R.id.settings_page_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });

    bindSwitch(R.id.suggestion_autocomplete, prefs.autocomplete(), "autocomplete", prefs);
    bindSwitch(
        R.id.suggestion_space_autocomplete, prefs.spaceAutocomplete(), "space_autocomplete", prefs);
    bindSwitch(R.id.suggestion_autocorrect, prefs.autocorrect(), "autocorrect", prefs);
    bindSwitch(R.id.suggestion_learning, prefs.learning(), "learning", prefs);
    bindSwitch(
        R.id.suggestion_emoji, prefs.emojiSuggestions(), "emoji_suggestions_enabled", prefs);

    findViewById(R.id.suggestion_open_dictionaries)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                startActivity(
                    new Intent(SuggestionSettingsActivity.this, DictionarySettingsActivity.class));
              }
            });
  }

  private void bindSwitch(int id, boolean checked, final String key, final Prefs prefs) {
    Switch row = (Switch) findViewById(id);
    row.setChecked(checked);
    row.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean value) {
            prefs.set(key, value);
          }
        });
  }
}
