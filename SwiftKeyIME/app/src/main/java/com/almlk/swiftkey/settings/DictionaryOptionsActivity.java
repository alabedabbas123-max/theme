package com.almlk.swiftkey.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.almlk.swiftkey.data.DictionaryDb;
import com.almlk.swiftkey.util.FeatureSettings;

/** Settings owned by the dictionaries manager — separate from typing settings. */
public final class DictionaryOptionsActivity extends FeatureOptionsActivity {

  protected CharSequence screenTitle() {
    return "إعدادات القواميس";
  }

  protected void buildOptions(final LinearLayout container) {
    final Context context = this;
    container.addView(
        noteCard(
            context,
            "تتحكم هذه الخيارات بسلوك القواميس أثناء الترشيح؛ أما إضافة الكلمات وحذفها فيتم من شاشة القواميس."));
    container.addView(
        switchCard(
            context,
            "استخدام القاموس المدمج في الاقتراحات",
            "إيقافه يبقي ترشيحات الاختصارات والكلمات المتعلمة فقط.",
            FeatureSettings.enabled(context, FeatureSettings.DICTIONARY_IN_SUGGESTIONS),
            new OnToggle() {
              public void onToggle(boolean value) {
                FeatureSettings.setEnabled(
                    context, FeatureSettings.DICTIONARY_IN_SUGGESTIONS, value);
              }
            }));
    container.addView(
        switchCard(
            context,
            "تفعيل قائمة الكلمات المحظورة",
            "الكلمات المحذوفة من القاموس لا تعود للترشيحات ما دام هذا الخيار يعمل.",
            FeatureSettings.enabled(context, FeatureSettings.DICTIONARY_BLOCKLIST),
            new OnToggle() {
              public void onToggle(boolean value) {
                FeatureSettings.setEnabled(context, FeatureSettings.DICTIONARY_BLOCKLIST, value);
              }
            }));
    container.addView(
        actionCard(
            context,
            "مسح الكلمات المتعلمة",
            "حذف كل الكلمات التي تعلّمها الكيبورد من كتاباتك السابقة.",
            new OnAction() {
              public void onAction() {
                confirm(
                    "مسح الكلمات المتعلمة",
                    "سيفقد الكيبورد كل الكلمات المتعلمة. متابعة؟",
                    new Runnable() {
                      public void run() {
                        DictionaryDb.getInstance(context).clearLearned();
                        done("تم مسح الكلمات المتعلمة");
                      }
                    });
              }
            }));
    container.addView(
        actionCard(
            context,
            "مسح قائمة الحظر",
            "إعادة السماح لكل الكلمات المحظورة بالظهور في الاقتراحات.",
            new OnAction() {
              public void onAction() {
                confirm(
                    "مسح قائمة الحظر",
                    "ستعود الكلمات المحظورة للترشيح إن كانت في القاموس. متابعة؟",
                    new Runnable() {
                      public void run() {
                        DictionaryDb.getInstance(context).clearBlocked();
                        done("تم مسح قائمة الحظر");
                      }
                    });
              }
            }));
  }

  private void confirm(String title, String message, final Runnable action) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(
            "تنفيذ",
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                action.run();
              }
            })
        .setNegativeButton("إلغاء", null)
        .show();
  }

  private void done(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }
}
