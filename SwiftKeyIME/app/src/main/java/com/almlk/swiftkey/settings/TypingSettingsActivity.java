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

/** Complete entry point for typing, completion, key interaction and gesture controls. */
public final class TypingSettingsActivity extends AppCompatActivity {
  private Prefs prefs;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_typing_settings);
    prefs = new Prefs(this);
    ((TextView) findViewById(R.id.settings_page_title)).setText("إعدادات الكتابة");
    findViewById(R.id.settings_page_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });

    bindKeyboardSwitch(R.id.typing_autocomplete, "autocomplete", prefs.autocomplete());
    bindKeyboardSwitch(
        R.id.typing_space_autocomplete, "space_autocomplete", prefs.spaceAutocomplete());
    bindKeyboardSwitch(R.id.typing_autocorrect, "autocorrect", prefs.autocorrect());
    bindKeyboardSwitch(R.id.typing_learning, "learning", prefs.learning());
    bindKeyboardSwitch(
        R.id.typing_emoji_suggestions, "emoji_suggestions_enabled", prefs.emojiSuggestions());
    bindKeyboardSwitch(R.id.typing_key_preview, "key_popup_preview", prefs.keyPreview());
    bindKeyboardSwitch(
        R.id.typing_sticky_alternatives, "alternative_popup_sticky", prefs.stickyAlternatives());

    final Switch numberRow = (Switch) findViewById(R.id.typing_number_row);
    numberRow.setChecked(getSharedPreferences("keyboard_ui", 0).getBoolean("number_row", false));
    numberRow.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            getSharedPreferences("keyboard_ui", 0).edit().putBoolean("number_row", checked).apply();
          }
        });

    open(R.id.typing_gesture_settings, GestureSettingsActivity.class);
    open(R.id.typing_suggestion_settings, SuggestionSettingsActivity.class);
    open(R.id.typing_dictionary_settings, DictionarySettingsActivity.class);
    open(R.id.typing_feedback_settings, FeedbackSettingsActivity.class);
  }

  protected void onResume() {
    super.onResume();
    if (prefs == null) return;
    ((Switch) findViewById(R.id.typing_autocomplete)).setChecked(prefs.autocomplete());
    ((Switch) findViewById(R.id.typing_space_autocomplete)).setChecked(prefs.spaceAutocomplete());
    ((Switch) findViewById(R.id.typing_autocorrect)).setChecked(prefs.autocorrect());
    ((Switch) findViewById(R.id.typing_learning)).setChecked(prefs.learning());
    ((Switch) findViewById(R.id.typing_emoji_suggestions)).setChecked(prefs.emojiSuggestions());
    ((Switch) findViewById(R.id.typing_key_preview)).setChecked(prefs.keyPreview());
    ((Switch) findViewById(R.id.typing_sticky_alternatives)).setChecked(prefs.stickyAlternatives());
    ((Switch) findViewById(R.id.typing_number_row))
        .setChecked(getSharedPreferences("keyboard_ui", 0).getBoolean("number_row", false));
  }

  private void bindKeyboardSwitch(int id, final String key, boolean checked) {
    Switch control = (Switch) findViewById(id);
    control.setChecked(checked);
    control.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean enabled) {
            prefs.set(key, enabled);
          }
        });
  }

  private void open(int id, final Class<?> activity) {
    findViewById(id)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                startActivity(new Intent(TypingSettingsActivity.this, activity));
              }
            });
  }
}
