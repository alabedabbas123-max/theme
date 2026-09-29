package com.almlk.swiftkey.settings;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.ime.ToolPreferences;
import com.almlk.swiftkey.util.Prefs;

/** Voice typing: toolbar button visibility, recognition language and mic permission. */
public final class VoiceSettingsActivity extends AppCompatActivity {
  private Prefs prefs;
  private TextView permissionStatus;
  private Button permissionRequest;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_voice_settings);
    prefs = new Prefs(this);

    TextView title = (TextView) findViewById(R.id.settings_page_title);
    title.setText("الكتابة بالصوت");
    findViewById(R.id.settings_page_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });

    Switch toolbarButton = (Switch) findViewById(R.id.voice_toolbar_button);
    toolbarButton.setChecked(ToolPreferences.isVisible(this, "voice"));
    toolbarButton.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            if (ToolPreferences.isVisible(VoiceSettingsActivity.this, "voice") != checked) {
              ToolPreferences.toggleVisible(VoiceSettingsActivity.this, "voice");
            }
          }
        });

    RadioGroup languageGroup = (RadioGroup) findViewById(R.id.voice_language_group);
    String language = prefs.voiceLanguage();
    if ("ar".equals(language)) {
      languageGroup.check(R.id.voice_language_ar);
    } else if ("en".equals(language)) {
      languageGroup.check(R.id.voice_language_en);
    } else {
      languageGroup.check(R.id.voice_language_auto);
    }
    languageGroup.setOnCheckedChangeListener(
        new RadioGroup.OnCheckedChangeListener() {
          public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (checkedId == R.id.voice_language_ar) {
              prefs.setVoiceLanguage("ar");
            } else if (checkedId == R.id.voice_language_en) {
              prefs.setVoiceLanguage("en");
            } else {
              prefs.setVoiceLanguage("auto");
            }
          }
        });

    permissionStatus = (TextView) findViewById(R.id.voice_permission_status);
    permissionRequest = (Button) findViewById(R.id.voice_permission_request);
    permissionRequest.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            startActivity(new Intent(VoiceSettingsActivity.this, VoicePermissionActivity.class));
          }
        });
  }

  protected void onResume() {
    super.onResume();
    updatePermissionState();
  }

  private void updatePermissionState() {
    boolean granted = hasMicPermission();
    if (granted) {
      permissionStatus.setText("حالة الإذن: ممنوح — الكتابة بالصوت جاهزة.");
      permissionStatus.setTextColor(0xff2e7d32);
      permissionRequest.setEnabled(false);
    } else {
      permissionStatus.setText("حالة الإذن: غير ممنوح — امنح الإذن لاستخدام الميكروفون.");
      permissionStatus.setTextColor(0xffc62828);
      permissionRequest.setEnabled(true);
    }
  }

  private boolean hasMicPermission() {
    if (Build.VERSION.SDK_INT < 23) {
      return true;
    }
    return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED;
  }
}
