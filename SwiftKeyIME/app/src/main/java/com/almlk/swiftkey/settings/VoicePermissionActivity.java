package com.almlk.swiftkey.settings;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

/**
 * Minimal transparent bridge used because an InputMethodService cannot show a runtime permission
 * dialog. The API-23 entry points are reached through reflection so this file compiles against
 * any platform jar the IDE happens to ship; on older platforms runtime grants do not exist and
 * the bridge simply reports granted.
 */
public final class VoicePermissionActivity extends Activity {
  public static final String ACTION_RESULT = "com.almlk.swiftkey.VOICE_PERMISSION_RESULT";
  private static final int REQUEST_MIC = 71;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    if (android.os.Build.VERSION.SDK_INT < 23
        || checkSelf() == PackageManager.PERMISSION_GRANTED) {
      send(true);
      return;
    }
    if (!requestViaReflection()) {
      send(false);
    }
  }

  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    send(
        request == REQUEST_MIC
            && results.length > 0
            && results[0] == PackageManager.PERMISSION_GRANTED);
  }

  private int checkSelf() {
    try {
      Object result =
          Activity.class
              .getMethod("checkSelfPermission", String.class)
              .invoke(this, Manifest.permission.RECORD_AUDIO);
      return result instanceof Integer
          ? ((Integer) result).intValue()
          : PackageManager.PERMISSION_GRANTED;
    } catch (Throwable ignored) {
      return PackageManager.PERMISSION_GRANTED;
    }
  }

  private boolean requestViaReflection() {
    try {
      Activity.class
          .getMethod("requestPermissions", String[].class, int.class)
          .invoke(this, new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private void send(boolean granted) {
    Intent result = new Intent(ACTION_RESULT);
    result.setPackage(getPackageName());
    result.putExtra("granted", granted);
    sendBroadcast(result);
    finish();
    overridePendingTransition(0, 0);
  }
}
