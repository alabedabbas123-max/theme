package com.almlk.swiftkey.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.almlk.swiftkey.data.ShortcutRepository;
import com.almlk.swiftkey.util.FeatureSettings;

/** Settings owned by the shortcuts manager itself — kept out of typing settings. */
public final class ShortcutOptionsActivity extends FeatureOptionsActivity {

  protected CharSequence screenTitle() {
    return "إعدادات الاختصارات";
  }

  protected void buildOptions(final LinearLayout container) {
    final Context context = this;
    container.addView(
        noteCard(
            context,
            "هذه إعدادات قسم «الاختصارات» فقط؛ لإضافة البطاقات أو حذفها أو إعادة ترتيبها ارجع لشاشة الاختصارات."));
    container.addView(
        switchCard(
            context,
            "تفعيل الإحلال التلقائي",
            "كتابة الاختصار ثم مسافة تبدّله إلى النص الكامل داخل أي تطبيق.",
            FeatureSettings.enabled(context, FeatureSettings.SHORTCUT_ENABLED),
            new OnToggle() {
              public void onToggle(boolean value) {
                FeatureSettings.setEnabled(context, FeatureSettings.SHORTCUT_ENABLED, value);
                Toast.makeText(
                        context,
                        value ? "الإحلال التلقائي يعمل الآن" : "تم إيقاف الإحلال التلقائي",
                        Toast.LENGTH_SHORT)
                    .show();
              }
            }));
    container.addView(
        switchCard(
            context,
            "إظهار الاختصارات في شريط الاقتراحات",
            "ترشيح نصوص الاختصارات المطابقة أثناء الكتابة وسط الاقتراحات.",
            FeatureSettings.enabled(context, FeatureSettings.SHORTCUT_IN_STRIP),
            new OnToggle() {
              public void onToggle(boolean value) {
                FeatureSettings.setEnabled(context, FeatureSettings.SHORTCUT_IN_STRIP, value);
              }
            }));
    container.addView(
        actionCard(
            context,
            "حذف كل الاختصارات",
            "إزالة جميع بطاقات الاختصار المخزنة على هذا الجهاز.",
            new OnAction() {
              public void onAction() {
                new AlertDialog.Builder(ShortcutOptionsActivity.this)
                    .setTitle("حذف كل الاختصارات")
                    .setMessage("سيُحذف كل الاختصارات نهائيًا. متابعة؟")
                    .setPositiveButton(
                        "حذف",
                        new DialogInterface.OnClickListener() {
                          public void onClick(DialogInterface dialog, int which) {
                            new ShortcutRepository(context).clear();
                            Toast.makeText(
                                    context, "تم حذف كل الاختصارات", Toast.LENGTH_SHORT)
                                .show();
                            container.removeAllViews();
                            buildOptions(container);
                          }
                        })
                    .setNegativeButton("إلغاء", null)
                    .show();
              }
            }));
  }
}
