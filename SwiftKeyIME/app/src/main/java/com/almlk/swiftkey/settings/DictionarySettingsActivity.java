package com.almlk.swiftkey.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.data.DictionaryDb;
import com.almlk.swiftkey.engine.TextNormalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dictionary manager on the same card design: paged results in rounded cards, a
 * language chooser, live search, ＋ to add a word, and per-card edit/delete.
 * Word order follows dictionary frequency and search relevance, so manual
 * dragging is intentionally off here (positions must stay meaningful).
 */
public final class DictionarySettingsActivity extends EntryManagerActivity {
  private static final int PAGE_SIZE = 200;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final ArrayList<String> words = new ArrayList<String>();
  private DictionaryDb database;
  private Spinner language;
  private String selectedLanguage = "ar";
  private int generation;
  private boolean fetching;

  protected void onCreate(Bundle state) {
    database = DictionaryDb.getInstance(this);
    super.onCreate(state);
    language = (Spinner) findViewById(R.id.dictionary_language);
    language.setAdapter(
        new ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[] {"القاموس العربي", "English dictionary"}));
    language.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            selectedLanguage = position == 1 ? "en" : "ar";
            load(true);
          }

          public void onNothingSelected(AdapterView<?> parent) {}
        });
  }

  protected void onDestroy() {
    generation++;
    main.removeCallbacksAndMessages(null);
    worker.shutdownNow();
    super.onDestroy();
  }

  protected int screenLayout() {
    return R.layout.activity_dictionary_settings;
  }

  protected CharSequence screenTitle() {
    return "القواميس والكلمات";
  }

  protected int listId() {
    return R.id.dictionary_list;
  }

  protected int searchId() {
    return R.id.dictionary_search;
  }

  protected int fabId() {
    return R.id.dictionary_add;
  }

  protected int emptyId() {
    return R.id.dictionary_empty;
  }

  protected int statusId() {
    return R.id.dictionary_status;
  }

  protected Class<?> gearTarget() {
    return DictionaryOptionsActivity.class;
  }

  protected boolean reorderAllowed() {
    return false;
  }

  protected boolean clientSideFilter() {
    return false;
  }

  /** onResume and every mutation re-enter through the paging loader. */
  protected void refresh() {
    load(true);
  }

  private void refreshFromWords() {
    rebuild();
    applyFilter();
  }

  protected void onSearchChanged() {
    load(true);
  }

  protected void onLoadMore() {
    load(false);
  }

  protected void rebuild() {
    entries.clear();
    for (int index = 0; index < words.size(); index++) {
      String word = words.get(index);
      entries.add(new Entry(word, word, ""));
    }
  }

  protected void onAddEntry() {
    showWordEditor("");
  }

  protected void onEditEntry(Entry entry) {
    showWordEditor((String) entry.tag);
  }

  protected void onDeleteEntry(Entry entry) {
    deleteWord((String) entry.tag);
  }

  protected void updateStatus(int shownCount, int totalCount) {
    if (fetching) {
      setStatus("جار تحميل الكلمات…");
      return;
    }
    if (totalCount == 0) {
      setStatus(selectedLanguage.equals("en")
          ? "No words yet for this search"
          : "لا توجد كلمات مطابقة حتى الآن");
      return;
    }
    setStatus(
        "المعروض: "
            + totalCount
            + (query.length() > 0 ? " • نتائج بحث" : " • اضغط البطاقة لتعديل الكلمة"));
  }

  private void load(final boolean reset) {
    if (fetching && !reset) {
      finishLoadMore();
      return;
    }
    final int request = ++generation;
    final String lang = selectedLanguage;
    final String needle = TextNormalizer.foldForComparison(query, lang);
    final int offset = reset ? 0 : words.size();
    fetching = true;
    updateStatus(0, 0);
    worker.execute(
        new Runnable() {
          public void run() {
            final List<String> page = database.dictionaryWords(lang, needle, PAGE_SIZE, offset);
            main.post(
                new Runnable() {
                  public void run() {
                    if (request != generation || isFinishing()) return;
                    fetching = false;
                    finishLoadMore();
                    if (reset) words.clear();
                    for (String word : page) {
                      if (!words.contains(word)) words.add(word);
                    }
                    refreshFromWords();
                  }
                });
          }
        });
  }

  private void showWordEditor(final String oldWord) {
    View form = getLayoutInflater().inflate(R.layout.dialog_dictionary_word, null);
    final EditText editor = (EditText) form.findViewById(R.id.dictionary_word);
    editor.setText(oldWord);
    editor.setSelection(editor.length());
    AlertDialog.Builder dialog =
        new AlertDialog.Builder(this)
            .setTitle(oldWord.length() == 0 ? "إضافة كلمة" : "تعديل الكلمة")
            .setView(form)
            .setPositiveButton(
                "حفظ",
                new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface source, int which) {
                    final String changed = TextNormalizer.clean(editor.getText().toString());
                    if (changed.length() == 0) {
                      Toast.makeText(
                              DictionarySettingsActivity.this,
                              "أدخل كلمة صحيحة",
                              Toast.LENGTH_SHORT)
                          .show();
                      return;
                    }
                    saveWord(oldWord, changed);
                  }
                })
            .setNegativeButton("إلغاء", null);
    if (oldWord.length() > 0) {
      dialog.setNeutralButton(
          "حذف",
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface source, int which) {
              deleteWord(oldWord);
            }
          });
    }
    dialog.show();
  }

  private void saveWord(final String oldWord, final String changed) {
    final String lang = selectedLanguage;
    fetching = true;
    worker.execute(
        new Runnable() {
          public void run() {
            if (oldWord.length() == 0) {
              database.addUserWord(
                  changed, TextNormalizer.foldForComparison(changed, lang), lang);
            } else {
              database.editWord(
                  oldWord, changed, TextNormalizer.foldForComparison(changed, lang), lang);
            }
            main.post(
                new Runnable() {
                  public void run() {
                    fetching = false;
                    if (isFinishing()) return;
                    Toast.makeText(
                            DictionarySettingsActivity.this, "تم حفظ الكلمة", Toast.LENGTH_SHORT)
                        .show();
                    load(true);
                  }
                });
          }
        });
  }

  private void deleteWord(final String word) {
    final String lang = selectedLanguage;
    fetching = true;
    worker.execute(
        new Runnable() {
          public void run() {
            database.deleteWord(word, lang);
            main.post(
                new Runnable() {
                  public void run() {
                    fetching = false;
                    if (isFinishing()) return;
                    Toast.makeText(
                            DictionarySettingsActivity.this,
                            "تم حذف الكلمة من الاقتراحات",
                            Toast.LENGTH_SHORT)
                        .show();
                    words.remove(word);
                    refresh();
                  }
                });
          }
        });
  }
}
