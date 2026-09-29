package com.almlk.swiftkey.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

/** Virtualized searchable list generated from every ISO language known to Android. */
public final class AddLanguageActivity extends AppCompatActivity {
  private final ArrayList<LanguageItem> all = new ArrayList<LanguageItem>();
  private final ArrayList<LanguageItem> filtered = new ArrayList<LanguageItem>();
  private LanguageAdapter adapter;
  private final java.util.HashSet<String> installedCodes = new java.util.HashSet<String>();

  private static final class LanguageItem {
    final String code, arabic, nativeName;

    LanguageItem(String code, String arabic, String nativeName) {
      this.code = code;
      this.arabic = arabic;
      this.nativeName = nativeName;
    }
  }

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_add_language);
    findViewById(R.id.add_language_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });
    installedCodes.addAll(LanguagePreferences.installed(this));
    buildAllLanguages();
    filtered.addAll(all);
    adapter = new LanguageAdapter();
    ListView list = (ListView) findViewById(R.id.all_languages_list);
    list.setAdapter(adapter);
    list.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            install(filtered.get(position));
          }
        });
    ((EditText) findViewById(R.id.language_search))
        .addTextChangedListener(
            new TextWatcher() {
              public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

              public void onTextChanged(CharSequence text, int start, int before, int count) {
                filter(text.toString());
              }

              public void afterTextChanged(Editable value) {}
            });
  }

  private void buildAllLanguages() {
    final Locale arabicLocale = new Locale("ar");
    String[] codes = Locale.getISOLanguages();
    for (String code : codes) {
      Locale locale = new Locale(code);
      String arabic = locale.getDisplayLanguage(arabicLocale);
      String nativeName = locale.getDisplayLanguage(locale);
      if (arabic == null || arabic.length() == 0) arabic = nativeName;
      if (nativeName == null || nativeName.length() == 0) nativeName = code;
      all.add(new LanguageItem(code, arabic, nativeName));
    }
    final Collator collator = Collator.getInstance(arabicLocale);
    Collections.sort(
        all,
        new Comparator<LanguageItem>() {
          public int compare(LanguageItem left, LanguageItem right) {
            return collator.compare(left.arabic, right.arabic);
          }
        });
  }

  private void filter(String query) {
    String wanted = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    filtered.clear();
    for (LanguageItem item : all) {
      String hay = (item.arabic + " " + item.nativeName + " " + item.code).toLowerCase(Locale.ROOT);
      if (wanted.length() == 0 || hay.contains(wanted)) filtered.add(item);
    }
    adapter.notifyDataSetChanged();
  }

  private void install(LanguageItem item) {
    if (installedCodes.contains(item.code)) {
      Toast.makeText(this, "اللغة مضافة بالفعل", Toast.LENGTH_SHORT).show();
      return;
    }
    LanguagePreferences.add(this, item.code);
    installedCodes.add(item.code);
    Toast.makeText(this, "تمت إضافة " + item.arabic, Toast.LENGTH_SHORT).show();
    adapter.notifyDataSetChanged();
  }

  private final class LanguageAdapter extends BaseAdapter {
    public int getCount() {
      return filtered.size();
    }

    public Object getItem(int position) {
      return filtered.get(position);
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View convert, ViewGroup parent) {
      View row =
          convert == null
              ? getLayoutInflater().inflate(R.layout.item_available_language, parent, false)
              : convert;
      LanguageItem item = filtered.get(position);
      ((TextView) row.findViewById(R.id.available_language_name)).setText(item.arabic);
      TextView nativeView = (TextView) row.findViewById(R.id.available_language_native);
      nativeView.setText(item.nativeName + "  ·  " + item.code.toUpperCase(Locale.US));
      TextView add = (TextView) row.findViewById(R.id.available_language_add);
      boolean installed = installedCodes.contains(item.code);
      add.setText(installed ? "✓" : "＋");
      add.setTextColor(installed ? 0xff2e7d32 : 0xff3964ea);
      return row;
    }
  }
}
