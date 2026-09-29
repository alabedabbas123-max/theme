package com.almlk.swiftkey.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.appcompat.app.AlertDialog;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.data.ClipboardRepository;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Clipboard history manager rendered as the same card list as shortcuts: search,
 * ＋ to add, per-card options (edit, delete, pin, copy, full preview) and manual
 * drag ordering — the same order the keyboard clipboard panel shows.
 */
public final class ClipboardSettingsActivity extends EntryManagerActivity {
  private ClipboardRepository repository;
  private final SimpleDateFormat dateFormat =
      new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ROOT);

  protected void onCreate(Bundle state) {
    repository = ClipboardRepository.get(this);
    super.onCreate(state);
  }

  protected int screenLayout() {
    return R.layout.activity_clipboard_settings;
  }

  protected CharSequence screenTitle() {
    return "الحافظة";
  }

  protected int listId() {
    return R.id.clipboard_list;
  }

  protected int searchId() {
    return R.id.clipboard_search;
  }

  protected int fabId() {
    return R.id.clipboard_add;
  }

  protected int emptyId() {
    return R.id.clipboard_empty;
  }

  protected int statusId() {
    return R.id.clipboard_status;
  }

  protected Class<?> gearTarget() {
    return ClipboardOptionsActivity.class;
  }

  protected boolean supportsClearAll() {
    return true;
  }

  protected boolean reorderAllowed() {
    return true;
  }

  protected void onBindExtraViews() {
    findViewById(R.id.clipboard_clear)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                confirmClearAll();
              }
            });
  }

  protected void rebuild() {
    entries.clear();
    java.util.List<ClipboardRepository.Item> items = repository.list();
    for (int index = 0; index < items.size(); index++) {
      ClipboardRepository.Item item = items.get(index);
      String preview = oneLine(item.text);
      String detail =
          dateFormat.format(new Date(item.created))
              + " • "
              + item.text.length()
              + " حرف"
              + (item.pinned ? " • مثبّت" : "");
      entries.add(new Entry(item, preview, detail, item.pinned ? 1 : 0));
    }
  }

  protected void onAddEntry() {
    showEditor(null);
  }

  protected void onEditEntry(Entry entry) {
    showEditor((ClipboardRepository.Item) entry.tag);
  }

  protected void onDeleteEntry(Entry entry) {
    repository.delete(((ClipboardRepository.Item) entry.tag).id);
  }

  protected void onReordered() {
    ArrayList<ClipboardRepository.Item> ordered = new ArrayList<ClipboardRepository.Item>();
    for (int index = 0; index < entries.size(); index++) {
      ordered.add((ClipboardRepository.Item) entries.get(index).tag);
    }
    repository.reorder(ordered);
  }

  protected void onClearAll() {
    repository.clearAll();
    toast("تم مسح سجل الحافظة");
  }

  protected String[] extraOptions(Entry entry) {
    ClipboardRepository.Item item = (ClipboardRepository.Item) entry.tag;
    return item.pinned
        ? new String[] {"إلغاء التثبيت", "نسخ إلى الحافظة", "معاينة كاملة"}
        : new String[] {"تثبيت في الأعلى", "نسخ إلى الحافظة", "معاينة كاملة"};
  }

  protected void onOptionSelected(Entry entry, String label) {
    final ClipboardRepository.Item item = (ClipboardRepository.Item) entry.tag;
    if ("نسخ إلى الحافظة".equals(label)) {
      copyToClipboard(item.text);
      return;
    }
    if ("معاينة كاملة".equals(label)) {
      new AlertDialog.Builder(this)
          .setTitle("معاينة العنصر")
          .setMessage(TextUtils.isEmpty(item.text) ? "(فارغ)" : item.text)
          .setPositiveButton("تعديل", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
              showEditor(item);
            }
          })
          .setNegativeButton("إغلاق", null)
          .show();
      return;
    }
    repository.pin(item.id, !item.pinned);
    refresh();
  }

  protected void updateStatus(int shownCount, int totalCount) {
    if (totalCount == 0) {
      setStatus("لا توجد عناصر محفوظة — أضف عنصرًا من ＋ أو انسخ نصًا في أي تطبيق");
      return;
    }
    setStatus(
        "المعروض: "
            + shownCount
            + " من "
            + totalCount
            + " — اسحب البطاقة مطولًا لإعادة الترتيب، والـ⋮ للعناصر المثبتة");
  }

  private void showEditor(final ClipboardRepository.Item item) {
    final EditText editor = new EditText(this);
    editor.setGravity(Gravity.RIGHT);
    int pad = dp(18);
    editor.setPadding(pad, dp(10), pad, dp(10));
    editor.setMinLines(3);
    editor.setMaxLines(10);
    editor.setHorizontallyScrolling(false);
    editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    if (item != null) {
      editor.setText(item.text);
      editor.setSelection(editor.getText().length());
    }
    FrameLayout wrapper = new FrameLayout(this);
    wrapper.setPadding(pad, dp(6), pad, 0);
    wrapper.addView(
        editor,
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    new AlertDialog.Builder(this)
        .setTitle(item == null ? "إضافة عنصر" : "تعديل العنصر")
        .setView(wrapper)
        .setPositiveButton(
            "حفظ",
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                String text = editor.getText().toString().trim();
                if (text.length() == 0) {
                  toast("اكتب نصًا أولًا");
                  return;
                }
                if (item == null) {
                  repository.add(text);
                } else {
                  repository.edit(item.id, text);
                }
                refresh();
                toast("تم الحفظ");
              }
            })
        .setNegativeButton("إلغاء", null)
        .show();
  }

  private void copyToClipboard(String text) {
    android.content.ClipboardManager manager =
        (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    manager.setPrimaryClip(android.content.ClipData.newPlainText("clipboard", text));
    toast("تم النسخ");
  }

  private String oneLine(String value) {
    String folded = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    return folded.length() <= 70 ? folded : folded.substring(0, 70) + "…";
  }
}
