package com.almlk.swiftkey.diagnostics;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Privacy-friendly local crash tracker. Reports are stored only in internal app storage and are
 * never uploaded unless the user explicitly shares the text.
 */
public final class ErrorTracker {
  private static final String FILE_NAME = "almlk_error_log.txt";
  private static final long MAX_LOG_BYTES = 256L * 1024L;
  private static boolean installed;

  private ErrorTracker() {}

  public static synchronized void install(final Context context) {
    if (installed) {
      return;
    }
    installed = true;
    final Context appContext = context.getApplicationContext();
    final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();

    Thread.setDefaultUncaughtExceptionHandler(
        new Thread.UncaughtExceptionHandler() {
          @Override
          public void uncaughtException(Thread thread, Throwable throwable) {
            record(appContext, "UNCAUGHT / " + thread.getName(), throwable);
            if (previous != null) {
              previous.uncaughtException(thread, throwable);
            } else {
              android.os.Process.killProcess(android.os.Process.myPid());
              System.exit(10);
            }
          }
        });
  }

  public static synchronized void record(Context context, String source, Throwable error) {
    if (context == null || error == null) {
      return;
    }
    File file = logFile(context);
    rotateIfNeeded(file);
    FileOutputStream stream = null;
    PrintWriter writer = null;
    try {
      stream = new FileOutputStream(file, true);
      writer = new PrintWriter(stream);
      writer.println("==================================================");
      writer.println("Time: " + timestamp());
      writer.println("Source: " + safe(source));
      writer.println("App: " + appVersion(context));
      writer.println("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
      writer.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
      writer.println("Message: " + safe(error.getMessage()));
      StringWriter stack = new StringWriter();
      error.printStackTrace(new PrintWriter(stack));
      writer.println(stack.toString());
      writer.flush();
    } catch (Exception ignored) {
      // A crash tracker must never cause another crash.
    } finally {
      if (writer != null) {
        writer.close();
      } else if (stream != null) {
        try {
          stream.close();
        } catch (Exception ignored) {
          // Nothing else to release.
        }
      }
    }
  }

  public static synchronized String read(Context context) {
    File file = logFile(context);
    if (!file.exists()) {
      return "";
    }
    StringBuilder result = new StringBuilder();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
      String line;
      while ((line = reader.readLine()) != null) {
        result.append(line).append('\n');
      }
    } catch (Exception error) {
      return "Unable to read log: " + safe(error.getMessage());
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (Exception ignored) {
          // Nothing else to release.
        }
      }
    }
    return result.toString();
  }

  public static synchronized boolean hasErrors(Context context) {
    File file = logFile(context);
    return file.exists() && file.length() > 0;
  }

  public static synchronized void clear(Context context) {
    File file = logFile(context);
    if (file.exists()) {
      file.delete();
    }
  }

  private static File logFile(Context context) {
    return new File(context.getFilesDir(), FILE_NAME);
  }

  private static void rotateIfNeeded(File file) {
    if (file.exists() && file.length() >= MAX_LOG_BYTES) {
      file.delete();
    }
  }

  private static String appVersion(Context context) {
    try {
      PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      return info.versionName + " (" + info.versionCode + ")";
    } catch (Exception ignored) {
      return "unknown";
    }
  }

  private static String timestamp() {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
