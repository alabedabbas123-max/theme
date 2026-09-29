package com.almlk.swiftkey.settings;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.util.Prefs;

/** Global key font override applied on top of every theme, with a live preview. */
public final class FontSettingsActivity extends AppCompatActivity {
  private static final String[] FAMILIES = {
    "sans-serif", "serif", "sans-serif-condensed", "monospace", "sans-serif-light"
  };

  private Prefs prefs;
  private TextView preview;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_font_settings);
    prefs = new Prefs(this);

    TextView title = (TextView) findViewById(R.id.settings_page_title);
    title.setText("الخط");
    findViewById(R.id.settings_page_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });

    preview = (TextView) findViewById(R.id.font_preview);
    RadioGroup group = (RadioGroup) findViewById(R.id.font_override_group);
    group.check(radioFor(prefs.fontOverride()));
    group.setOnCheckedChangeListener(
        new RadioGroup.OnCheckedChangeListener() {
          public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
            prefs.setInt("key_font_override", valueFor(checkedId));
            applyPreview();
          }
        });
    applyPreview();
  }

  private int radioFor(int value) {
    if (value == 0) {
      return R.id.font_override_sans;
    }
    if (value == 1) {
      return R.id.font_override_serif;
    }
    if (value == 2) {
      return R.id.font_override_condensed;
    }
    if (value == 3) {
      return R.id.font_override_mono;
    }
    if (value == 4) {
      return R.id.font_override_light;
    }
    return R.id.font_override_default;
  }

  private int valueFor(int checkedId) {
    if (checkedId == R.id.font_override_sans) {
      return 0;
    }
    if (checkedId == R.id.font_override_serif) {
      return 1;
    }
    if (checkedId == R.id.font_override_condensed) {
      return 2;
    }
    if (checkedId == R.id.font_override_mono) {
      return 3;
    }
    if (checkedId == R.id.font_override_light) {
      return 4;
    }
    return -1;
  }

  private void applyPreview() {
    int value = prefs.fontOverride();
    if (value < 0 || value >= FAMILIES.length) {
      preview.setTypeface(Typeface.DEFAULT);
    } else {
      preview.setTypeface(Typeface.create(FAMILIES[value], Typeface.NORMAL));
    }
  }
}
