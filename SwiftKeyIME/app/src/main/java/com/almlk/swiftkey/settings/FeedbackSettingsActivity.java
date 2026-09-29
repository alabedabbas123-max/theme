package com.almlk.swiftkey.settings;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.util.Prefs;

/** Working feedback, popup and long-delete controls. */
public final class FeedbackSettingsActivity extends AppCompatActivity {
  private Prefs prefs;
  private Switch sound;
  private Switch vibration;
  private TextView soundLabel;
  private TextView strengthLabel;
  private TextView durationLabel;
  private TextView popupLabel;
  private TextView deleteSpeedLabel;
  private TextView deleteDelayLabel;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_feedback_settings);
    prefs = new Prefs(this);

    findViewById(R.id.feedback_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });

    sound = (Switch) findViewById(R.id.setting_sound);
    vibration = (Switch) findViewById(R.id.setting_vibration);
    soundLabel = (TextView) findViewById(R.id.sound_volume_label);
    strengthLabel = (TextView) findViewById(R.id.vibration_strength_label);
    durationLabel = (TextView) findViewById(R.id.vibration_duration_label);
    popupLabel = (TextView) findViewById(R.id.popup_delay_label);
    deleteSpeedLabel = (TextView) findViewById(R.id.delete_speed_label);
    deleteDelayLabel = (TextView) findViewById(R.id.delete_delay_label);

    sound.setChecked(prefs.sound());
    vibration.setChecked(prefs.vibration());
    sound.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            prefs.set("sound", checked);
            updateEnabledState();
          }
        });
    vibration.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            prefs.set("vibration", checked);
            updateEnabledState();
          }
        });

    bindRange(R.id.setting_sound_volume, prefs.soundVolume() - 5, 0);
    bindRange(R.id.setting_vibration_strength, prefs.vibrationStrength() - 1, 1);
    bindRange(R.id.setting_vibration_duration, prefs.vibrationDuration() - 5, 2);
    bindRange(R.id.setting_popup_delay, prefs.popupDelay() - 220, 3);
    bindRange(R.id.setting_delete_speed, 90 - prefs.deleteRepeatInterval(), 4);
    bindRange(R.id.setting_delete_delay, 500 - prefs.deleteStartDelay(), 5);

    Switch preview = (Switch) findViewById(R.id.setting_key_preview);
    Switch sticky = (Switch) findViewById(R.id.setting_sticky_alternatives);
    preview.setChecked(prefs.keyPreview());
    sticky.setChecked(prefs.stickyAlternatives());
    preview.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            prefs.set("key_popup_preview", checked);
          }
        });
    sticky.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            prefs.set("alternative_popup_sticky", checked);
          }
        });

    findViewById(R.id.test_feedback)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                playFeedback();
              }
            });
    updateLabels();
    updateEnabledState();
  }

  private void bindRange(int id, int progress, final int type) {
    SeekBar bar = (SeekBar) findViewById(id);
    bar.setProgress(progress);
    bar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
            if (!fromUser) return;
            if (type == 0) prefs.setInt("sound_volume", value + 5);
            else if (type == 1) prefs.setInt("vibration_strength", value + 1);
            else if (type == 2) prefs.setInt("vibration_duration", value + 5);
            else if (type == 3) prefs.setInt("popup_delay", value + 220);
            else if (type == 4) prefs.setInt("delete_repeat_interval", 90 - value);
            else prefs.setInt("delete_start_delay", 500 - value);
            updateLabels();
          }

          public void onStartTrackingTouch(SeekBar seekBar) {}

          public void onStopTrackingTouch(SeekBar seekBar) {
            if (type == 0 || type == 1 || type == 2) playFeedback();
          }
        });
  }

  private void updateLabels() {
    soundLabel.setText("مستوى الصوت: " + prefs.soundVolume() + "٪");
    strengthLabel.setText("قوة الاهتزاز: " + prefs.vibrationStrength() + " من 255");
    durationLabel.setText("مدة الاهتزاز: " + prefs.vibrationDuration() + " مللي ثانية");
    popupLabel.setText("زمن ظهور البدائل: " + prefs.popupDelay() + " مللي ثانية");
    deleteSpeedLabel.setText(
        "سرعة التكرار: " + (1000 / prefs.deleteRepeatInterval()) + " حذف في الثانية");
    deleteDelayLabel.setText("بدء التكرار بعد: " + prefs.deleteStartDelay() + " مللي ثانية");
  }

  private void updateEnabledState() {
    findViewById(R.id.setting_sound_volume).setEnabled(sound.isChecked());
    findViewById(R.id.setting_vibration_strength).setEnabled(vibration.isChecked());
    findViewById(R.id.setting_vibration_duration).setEnabled(vibration.isChecked());
  }

  private void playFeedback() {
    if (prefs.sound()) {
      AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
      if (audio != null) {
        audio.playSoundEffect(AudioManager.FX_KEY_CLICK, prefs.soundVolume() / 100f);
      }
    }
    if (prefs.vibration()) {
      Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      if (vibrator != null && vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= 26) {
          vibrator.vibrate(
              VibrationEffect.createOneShot(prefs.vibrationDuration(), prefs.vibrationStrength()));
        } else {
          vibrator.vibrate(prefs.vibrationDuration());
        }
      }
    }
  }
}
