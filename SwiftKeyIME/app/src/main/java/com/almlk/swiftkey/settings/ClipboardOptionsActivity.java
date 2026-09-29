package com.almlk.swiftkey.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.almlk.swiftkey.data.ClipboardRepository;
import com.almlk.swiftkey.util.FeatureSettings;

/** Settings owned by the clipboard manager — separate from typing settings. */
public final class ClipboardOptionsActivity extends FeatureOptionsActivity {
  private static final int[] MAX_ITEMS = {25, 50, 100};
  private static final int[] PURGE_DAYS = {0, 1, 7, 30};
  private static final String[] PURGE_LABELS = {
    "بدون حذف تلقائي", "بعد يوم واحد", "بعد أسبوع", "بعد شهر"
  };

  protected CharSequence screenTitle() {
    return "إعدادات الحافظة";
  }

  protected void buildOptions(final LinearLayout container) {
    final Context context = this;
    container.addView(
        noteCard(
            context,
            "هذه الإعدادات تخص سجل الحافظة نفسه؛ تعديل البطاقات وحذفها يتم من شاشة الحافظة، وهي نفسها اللوحة داخل الكيبورد."));
    container.addView(
        switchCard(
            context,
            "حفظ النسخ الجديدة تلقائيًا",
            "كل نص تنسخه داخل أي تطبيق يضاف لبطاقات الحافظة أثناء عمل هذا الخيار.",
            FeatureSettings.enabled(context, FeatureSettings.CLIPBOARD_ENABLED),
            new OnToggle() {
              public void onToggle(boolean value) {
                FeatureSettings.setEnabled(context, FeatureSettings.CLIPBOARD_ENABLED, value);
                Toast.makeText(
                        context,
                        value ? "النسخ سيُسجَّل الآن" : "لن يُسجل النسخ الجديد",
                        Toast.LENGTH_SHORT)
                    .show();
              }
            }));
    container.addView(
        actionCard(
            context,
            "الحد الأقصى لعدد العناصر",
            "حاليًا: "
                + FeatureSettings.number(context, FeatureSettings.CLIPBOARD_MAX_ITEMS, 100, 10, 100),
            new OnAction() {
              public void onAction() {
                choose(
                    context,
                    "الحد الأقصى لعدد العناصر",
                    labelsForMax(),
                    FeatureSettings.number(
                        context, FeatureSettings.CLIPBOARD_MAX_ITEMS, 100, 10, 100),
                    MAX_ITEMS,
                    FeatureSettings.CLIPBOARD_MAX_ITEMS);
              }
            }));
    container.addView(
        actionCard(
            context,
            "حذف العناصر القديمة تلقائيًا",
            "حاليًا: " + purgeLabel(currentPurgeDays()),
            new OnAction() {
              public void onAction() {
                choose(
                    context,
                    "حذف العناصر القديمة تلقائيًا",
                    PURGE_LABELS,
                    currentPurgeDays(),
                    PURGE_DAYS,
                    FeatureSettings.CLIPBOARD_PURGE_DAYS);
              }
            }));
    container.addView(
        actionCard(
            context,
            "مسح كل عناصر الحافظة",
            "حذف كل البطاقات المحفوظة (حتى المثبتة) من الجهاز.",
            new OnAction() {
              public void onAction() {
                new AlertDialog.Builder(ClipboardOptionsActivity.this)
                    .setTitle("مسح كل عناصر الحافظة")
                    .setMessage("سيُحذف سجل الحافظة بالكامل. متابعة؟")
                    .setPositiveButton(
                        "حذف",
                        new DialogInterface.OnClickListener() {
                          public void onClick(DialogInterface dialog, int which) {
                            ClipboardRepository.get(context).clearAll();
                            Toast.makeText(context, "تم مسح السجل", Toast.LENGTH_SHORT).show();
                          }
                        })
                    .setNegativeButton("إلغاء", null)
                    .show();
              }
            }));
  }

  private int currentPurgeDays() {
    int value =
        FeatureSettings.number(this, FeatureSettings.CLIPBOARD_PURGE_DAYS, 0, 0, 365);
    for (int index = 0; index < PURGE_DAYS.length; index++) {
      if (PURGE_DAYS[index] == value) return value;
    }
    return 0;
  }

  private String[] labelsForMax() {
    String[] labels = new String[MAX_ITEMS.length];
    for (int index = 0; index < MAX_ITEMS.length; index++) {
      labels[index] = "أقصى عدد: " + MAX_ITEMS[index];
    }
    return labels;
  }

  private String purgeLabel(int days) {
    for (int index = 0; index < PURGE_DAYS.length; index++) {
      if (PURGE_DAYS[index] == days) return PURGE_LABELS[index];
    }
    return PURGE_LABELS[0];
  }

  private void choose(
      final Context context,
      String title,
      String[] labels,
      int current,
      final int[] values,
      final String key) {
    int selected = 0;
    for (int index = 0; index < values.length; index++) {
      if (values[index] == current) selected = index;
    }
    new AlertDialog.Builder(context)
        .setTitle(title)
        .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            FeatureSettings.setNumber(context, key, values[which]);
            dialog.dismiss();
            LinearLayout container =
                (LinearLayout) findViewById(com.almlk.swiftkey.R.id.options_container);
            container.removeAllViews();
            buildOptions(container);
          }
        })
        .setNegativeButton("إلغاء", null)
        .show();
  }
}
