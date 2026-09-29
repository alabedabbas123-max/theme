package com.almlk.swiftkey.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.util.Prefs;

/** Local gesture-typing, decoder and visual trail controls. */
public final class GestureSettingsActivity extends AppCompatActivity {
  private Prefs prefs;
  private Switch enabled;
  private Switch autoInsert;
  private Switch trail;
  private SeekBar sensitivity;
  private SeekBar width;
  private TextView sensitivityLabel;
  private TextView widthLabel;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_gesture_settings);
    prefs = new Prefs(this);
    findViewById(R.id.gesture_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });

    enabled = (Switch) findViewById(R.id.setting_gesture_typing);
    autoInsert = (Switch) findViewById(R.id.setting_gesture_auto_insert);
    trail = (Switch) findViewById(R.id.setting_gesture_trail);
    sensitivity = (SeekBar) findViewById(R.id.setting_gesture_sensitivity);
    width = (SeekBar) findViewById(R.id.setting_gesture_width);
    sensitivityLabel = (TextView) findViewById(R.id.gesture_sensitivity_label);
    widthLabel = (TextView) findViewById(R.id.gesture_width_label);

    enabled.setChecked(prefs.gestureTyping());
    autoInsert.setChecked(prefs.gestureAutoInsert());
    trail.setChecked(prefs.gestureTrail());
    sensitivity.setProgress(24 - prefs.gestureStartDistance());
    width.setProgress(prefs.gestureTrailWidth() - 3);

    enabled.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            prefs.set("gesture_typing", checked);
            updateEnabledState();
          }
        });
    autoInsert.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            prefs.set("gesture_auto_insert", checked);
          }
        });
    trail.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            prefs.set("gesture_trail", checked);
            updateEnabledState();
          }
        });
    sensitivity.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
            if (fromUser) prefs.setInt("gesture_start_distance", 24 - value);
            updateLabels();
          }

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {}
        });
    width.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
            if (fromUser) prefs.setInt("gesture_trail_width", value + 3);
            updateLabels();
          }

          public void onStartTrackingTouch(SeekBar bar) {}

          public void onStopTrackingTouch(SeekBar bar) {}
        });
    updateLabels();
    updateEnabledState();
  }

  private void updateLabels() {
    int sensitivityValue = 24 - sensitivity.getProgress();
    String level = sensitivityValue <= 10 ? "عالية" : sensitivityValue <= 17 ? "متوسطة" : "هادئة";
    sensitivityLabel.setText("حساسية بدء الإيماءة: " + level);
    widthLabel.setText("سُمك خط التتبع: " + (width.getProgress() + 3) + "dp");
  }

  private void updateEnabledState() {
    boolean active = enabled.isChecked();
    autoInsert.setEnabled(active);
    trail.setEnabled(active);
    sensitivity.setEnabled(active);
    sensitivityLabel.setEnabled(active);
    width.setEnabled(active && trail.isChecked());
    widthLabel.setEnabled(active && trail.isChecked());
  }
}
