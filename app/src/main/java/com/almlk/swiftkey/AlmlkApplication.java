package com.almlk.swiftkey;

import android.app.Application;
import com.almlk.swiftkey.data.EmojiDb;
import com.almlk.swiftkey.diagnostics.ErrorTracker;

/** Application entry point shared by the settings UI and the IME service. */
public final class AlmlkApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    ErrorTracker.install(this);
    EmojiDb.getInstance(this).ensureSeededAsync();
  }
}
