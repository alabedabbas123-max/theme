package com.almlk.swiftkey.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;

/**
 * Round 57: بوابة الإنترنت — تُستدعى عند فتح التطبيق: تفعّل صلاحية الإنترنت
 * (android.permission.INTERNET) إن كانت غير مفعلة بطلب رسمي، ثم تفحص الاتصال
 * الفعلي وترشد بالعربية إلى الإعدادات إن كان الجهاز بلا شبكة.
 */
public final class AppGate {
  private static final int INTERNET_REQUEST = 2457;

  private AppGate() {}

  /** عند فتح التطبيق — الصلاحية أولاً ثم فحص الاتصال والإرشاد. */
  public static void ensureInternet(final Activity activity) {
    if (activity == null || activity.isFinishing()) return;
    // 1) صلاحية الإنترنت تُمنح تلقائياً عند التثبيت، لكن بعض الواجهات
    //    (MIUI/EMUI...) تسمح بسحبها — فتُطلَب طلباً رسمياً إن لم تكن مفعلة.
    if (Build.VERSION.SDK_INT >= 23
        && activity.checkSelfPermission(android.Manifest.permission.INTERNET)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      activity.requestPermissions(
          new String[] {android.Manifest.permission.INTERNET}, INTERNET_REQUEST);
      return;
    }
    // 2) الصلاحية ممنوحة — هل يوجد اتصال فعلي؟
    if (!connected(activity)) guide(activity);
  }

  /** هل يوجد اتصال شبكي نشط؟ (فشل الفحص نفسه لا يُفسَّر انقطاعاً). */
  /** هل يوجد اتصال شبكي فعلي؟ Round 59: فحص حديث عبر NetworkCapabilities —
   * وأي شك في النتيجة يُفسَّر اتصالاً كي لا يزعج الحوارُ المستخدمَ المتصل. */
  private static boolean connected(Context context) {
    try {
      ConnectivityManager manager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (manager == null) return true;
      if (Build.VERSION.SDK_INT >= 23) {
        android.net.Network network = manager.getActiveNetwork();
        if (network == null) {
          NetworkInfo legacy = manager.getActiveNetworkInfo();
          return legacy == null ? false : legacy.isConnected();
        }
        android.net.NetworkCapabilities caps = manager.getNetworkCapabilities(network);
        if (caps == null) return true; // فحص غير حاسم — لا نزعج المستخدم
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
      }
      NetworkInfo info = manager.getActiveNetworkInfo();
      return info == null ? false : info.isConnected();
    } catch (Throwable ignored) {
      return true;
    }
  }

  /** حوار إرشادي عربي: إعدادات الواي فاي أو تفاصيل التطبيق. */
  private static void guide(final Activity activity) {
    if (activity == null || activity.isFinishing()) return;
    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setTitle("تفعيل الإنترنت");
    builder.setMessage(
        "صلاحية الإنترنت مفعلة، لكن الجهاز غير متصل حالياً.\n"
            + "افتح الواي فاي أو بيانات الجوال كي تظهر صور الإطارات والخلفيات"
            + " والتفاعلات من الموقع.");
    builder.setPositiveButton(
        "إعدادات الواي فاي",
        new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface dialog, int which) {
            try {
              activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            } catch (Throwable ignored) {
            }
            dialog.dismiss();
          }
        });
    builder.setNeutralButton(
        "تفاصيل التطبيق",
        new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface dialog, int which) {
            try {
              Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
              intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
              activity.startActivity(intent);
            } catch (Throwable ignored) {
            }
            dialog.dismiss();
          }
        });
    builder.setNegativeButton("لاحقاً", null);
    builder.setCancelable(true);
    builder.show();
  }
}
