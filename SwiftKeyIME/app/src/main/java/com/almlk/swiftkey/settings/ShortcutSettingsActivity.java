package com.almlk.swiftkey.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.app.AlertDialog;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.data.ShortcutRepository;
import java.util.ArrayList;
import java.util.List;

/**
 * Text-shortcut manager: each shortcut lives in its own rounded card, searchable,
 * addable from the ＋ button, editable/deletable/reorderable from the card handle.
 * Position never depends on alternatives — ordering is manual and persisted.
 */
public final class ShortcutSettingsActivity extends EntryManagerActivity {
  private ShortcutRepository repository;

  protected void onCreate(Bundle state) {
    repository = new ShortcutRepository(this);
    super.onCreate(state);
  }

  protected int screenLayout() {
    return R.layout.activity_shortcut_settings;
  }

  protected CharSequence screenTitle() {
    return "الاختصارات";
  }

  protected int listId() {
    return R.id.shortcut_list;
  }

  protected int searchId() {
    return R.id.shortcut_search;
  }

  protected int fabId() {
    return R.id.shortcut_add;
  }

  protected int emptyId() {
    return R.id.shortcut_empty;
  }

  protected Class<?> gearTarget() {
    return ShortcutOptionsActivity.class;
  }

  protected boolean supportsClearAll() {
    return true;
  }

  protected boolean reorderAllowed() {
    return true;
  }

  protected void rebuild() {
    entries.clear();
    List<ShortcutRepository.Item> items = repository.all();
    for (int index = 0; index < items.size(); index++) {
      ShortcutRepository.Item item = items.get(index);
      entries.add(new Entry(item, item.shortcut, item.expansion));
    }
  }

  protected void onAddEntry() {
    showShortcutEditor(null);
  }

  protected void onEditEntry(Entry entry) {
    showShortcutEditor((ShortcutRepository.Item) entry.tag);
  }

  protected void onDeleteEntry(Entry entry) {
    ShortcutRepository.Item item = (ShortcutRepository.Item) entry.tag;
    repository.delete(item.shortcut, item.language);
  }

  protected void onReordered() {
    ArrayList<ShortcutRepository.Item> ordered = new ArrayList<ShortcutRepository.Item>();
    for (int index = 0; index < entries.size(); index++) {
      ordered.add((ShortcutRepository.Item) entries.get(index).tag);
    }
    repository.reorder(ordered);
  }

  protected void onClearAll() {
    repository.clear();
    toast("تم حذف كل الاختصارات");
  }

  private void showShortcutEditor(final ShortcutRepository.Item existing) {
    View form = getLayoutInflater().inflate(R.layout.dialog_shortcut, null);
    final EditText keyInput = (EditText) form.findViewById(R.id.shortcut_key);
    final EditText expansionInput = (EditText) form.findViewById(R.id.shortcut_expansion);
    final Spinner language = (Spinner) form.findViewById(R.id.shortcut_language);
    language.setAdapter(
        new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"عربي", "English"}));
    if (existing != null) {
      keyInput.setText(existing.shortcut);
      expansionInput.setText(existing.expansion);
      language.setSelection("en".equals(existing.language) ? 1 : 0);
    }
    new AlertDialog.Builder(this)
        .setTitle(existing == null ? "إضافة اختصار" : "تعديل الاختصار")
        .setView(form)
        .setPositiveButton(
            "حفظ",
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                String shortcut = keyInput.getText().toString().trim();
                String expansion = expansionInput.getText().toString().trim();
                if (shortcut.length() == 0 || expansion.length() == 0) {
                  toast("أدخل الاختصار والنص الكامل");
                  return;
                }
                String languageValue = language.getSelectedItemPosition() == 1 ? "en" : "ar";
                String oldShortcut = existing == null ? "" : existing.shortcut;
                String oldLanguage = existing == null ? languageValue : existing.language;
                repository.save(oldShortcut, oldLanguage, shortcut, expansion, languageValue);
                refresh();
                toast(existing == null ? "تمت إضافة الاختصار" : "تم حفظ التعديل");
              }
            })
        .setNegativeButton("إلغاء", null)
        .show();
  }
}
