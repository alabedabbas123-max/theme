package com.almlk.swiftkey.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.data.ClipboardRepository;
import com.almlk.swiftkey.data.EmojiDb;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stickers tab: emoji browser grouped by pack plus text sticker packs.
 * Tapping any item copies it and stores it in the keyboard clipboard.
 */
public final class StickersActivity extends AppCompatActivity {
  private static final int COLUMNS = 6;
  private static final int PAGE_LIMIT = 240;

  private static final String[] EMOJI_PACKS = {
    "faces", "people", "animals", "food", "activities", "travel", "objects", "symbols", "flags"
  };
  private static final String[] EMOJI_TITLES = {
    "وجوه", "أشخاص", "حيوانات", "طعام", "أنشطة", "سفر", "أشياء", "رموز", "أعلام"
  };

  private final Map<String, ArrayList<String>> packs = new LinkedHashMap<String, ArrayList<String>>();
  private final List<String> packOrder = new ArrayList<String>();
  private final Map<String, String> packTitles = new LinkedHashMap<String, String>();
  private String currentPack = "faces";
  private String query = "";
  private LinearLayout packBar;
  private LinearLayout grid;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_stickers);
    packBar = (LinearLayout) findViewById(R.id.sticker_packs);
    grid = (LinearLayout) findViewById(R.id.sticker_grid);

    loadTextPacks();
    loadEmojiPacks();
    renderPackChips();
    renderGrid();

    EditText search = (EditText) findViewById(R.id.sticker_search);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

          public void onTextChanged(CharSequence text, int start, int before, int count) {}

          public void afterTextChanged(Editable text) {
            query = text.toString().trim();
            if (query.isEmpty()) {
              renderGrid();
            } else {
              searchAll(query);
            }
          }
        });
    SettingsTabs.bind(this, SettingsTabs.TAB_STICKERS);
  }

  private void loadTextPacks() {
    addPack("kaomoji", StickerPacks.TITLES[0], StickerPacks.KAOMOJI);
    addPack("islamic", StickerPacks.TITLES[1], StickerPacks.ISLAMIC);
    addPack("decorations", StickerPacks.TITLES[2], StickerPacks.DECORATIONS);
  }

  private void addPack(String key, String title, String[] items) {
    ArrayList<String> values = new ArrayList<String>();
    for (String item : items) {
      values.add(item);
    }
    packs.put(key, values);
    packOrder.add(key);
    packTitles.put(key, title);
  }

  private void loadEmojiPacks() {
    Map<String, ArrayList<String>> grouped = new LinkedHashMap<String, ArrayList<String>>();
    for (String pack : EMOJI_PACKS) {
      grouped.put(pack, new ArrayList<String>());
    }
    try {
      InputStream stream = getAssets().open("emoji/emoji_data.tsv");
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
      String line = reader.readLine();
      while (line != null) {
        String[] parts = line.split("\\t");
        if (parts.length >= 2) {
          ArrayList<String> bucket = grouped.get(parts[0]);
          if (bucket != null && parts[1].length() > 0) {
            bucket.add(parts[1]);
          }
        }
        line = reader.readLine();
      }
      reader.close();
    } catch (Exception ignored) {
      return;
    }
    for (int i = 0; i < EMOJI_PACKS.length; i++) {
      ArrayList<String> bucket = grouped.get(EMOJI_PACKS[i]);
      if (bucket != null && !bucket.isEmpty()) {
        packs.put(EMOJI_PACKS[i], bucket);
        packOrder.add(EMOJI_PACKS[i]);
        packTitles.put(EMOJI_PACKS[i], EMOJI_TITLES[i]);
      }
    }
  }

  private void renderPackChips() {
    packBar.removeAllViews();
    for (String key : packOrder) {
      final String pack = key;
      TextView chip = new TextView(this);
      chip.setText(packTitles.get(pack));
      chip.setTextSize(14);
      chip.setGravity(Gravity.CENTER);
      chip.setPadding(dp(16), dp(8), dp(16), dp(8));
      LinearLayout.LayoutParams params =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      params.leftMargin = dp(4);
      params.rightMargin = dp(4);
      chip.setLayoutParams(params);
      boolean selected = pack.equals(currentPack) && query.isEmpty();
      chip.setBackgroundResource(
          selected ? R.drawable.bg_pack_chip_selected : R.drawable.bg_pack_chip);
      chip.setTextColor(selected ? 0xffffffff : 0xff424242);
      chip.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              currentPack = pack;
              query = "";
              ((EditText) findViewById(R.id.sticker_search)).setText("");
              renderPackChips();
              renderGrid();
            }
          });
      packBar.addView(chip);
    }
  }

  private void renderGrid() {
    renderPackChips();
    ArrayList<String> items = packs.get(currentPack);
    if (items == null) {
      items = new ArrayList<String>();
    }
    grid.removeAllViews();
    if (isTextPack(currentPack)) {
      for (String item : items) {
        grid.addView(buildTextRow(item));
      }
      return;
    }
    int shown = Math.min(items.size(), PAGE_LIMIT);
    for (int start = 0; start < shown; start += COLUMNS) {
      grid.addView(buildEmojiRow(items, start, Math.min(start + COLUMNS, shown)));
    }
    if (items.size() > shown) {
      grid.addView(buildFooter("يعرض أول " + shown + " — استخدم البحث لعرض المزيد."));
    }
  }

  private void searchAll(final String text) {
    EmojiDb.getInstance(this)
        .searchAsync(
            text,
            PAGE_LIMIT,
            new EmojiDb.SearchCallback() {
              public void onResult(String answered, List<String> values) {
                if (!text.equals(query)) {
                  return;
                }
                renderSearchResults(answered, values);
              }
            });
  }

  private void renderSearchResults(String answered, List<String> values) {
    renderPackChips();
    grid.removeAllViews();
    boolean empty = true;
    for (String key : packOrder) {
      if (!isTextPack(key)) {
        continue;
      }
      for (String item : packs.get(key)) {
        if (item.contains(answered)) {
          grid.addView(buildTextRow(item));
          empty = false;
        }
      }
    }
    int shown = Math.min(values.size(), PAGE_LIMIT);
    for (int start = 0; start < shown; start += COLUMNS) {
      grid.addView(buildEmojiRow(values, start, Math.min(start + COLUMNS, shown)));
      empty = false;
    }
    if (empty) {
      grid.addView(buildFooter("لا توجد نتائج مطابقة."));
    }
  }

  private boolean isTextPack(String key) {
    return "kaomoji".equals(key) || "islamic".equals(key) || "decorations".equals(key);
  }

  private View buildEmojiRow(List<String> items, int from, int to) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    for (int i = from; i < to; i++) {
      final String symbol = items.get(i);
      TextView cell = new TextView(this);
      cell.setText(symbol);
      cell.setTextSize(30);
      cell.setGravity(Gravity.CENTER);
      cell.setPadding(dp(4), dp(8), dp(4), dp(8));
      LinearLayout.LayoutParams params =
          new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
      cell.setLayoutParams(params);
      cell.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              copyItem(symbol, true);
            }
          });
      row.addView(cell);
    }
    for (int i = to - from; i < COLUMNS; i++) {
      View spacer = new View(this);
      spacer.setLayoutParams(
          new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
      row.addView(spacer);
    }
    return row;
  }

  private View buildTextRow(final String item) {
    TextView row = new TextView(this);
    row.setText(item);
    row.setTextSize(19);
    row.setTextColor(0xff212121);
    row.setGravity(Gravity.CENTER);
    row.setBackgroundColor(0xffffffff);
    row.setPadding(dp(12), dp(14), dp(12), dp(14));
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(8);
    row.setLayoutParams(params);
    row.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            copyItem(item, false);
          }
        });
    return row;
  }

  private View buildFooter(String text) {
    TextView footer = new TextView(this);
    footer.setText(text);
    footer.setTextSize(14);
    footer.setTextColor(0xff9e9e9e);
    footer.setGravity(Gravity.CENTER);
    footer.setPadding(dp(8), dp(16), dp(8), dp(16));
    return footer;
  }

  private void copyItem(String item, boolean emoji) {
    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard != null) {
      clipboard.setPrimaryClip(ClipData.newPlainText("ملصق", item));
    }
    try {
      ClipboardRepository.get(this).add(item);
    } catch (Exception ignored) {
    }
    if (emoji) {
      try {
        EmojiDb.getInstance(this).remember(item);
      } catch (Exception ignored) {
      }
    }
    Toast.makeText(this, "تم النسخ إلى الحافظة", Toast.LENGTH_SHORT).show();
  }

  private int dp(int value) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(value * density);
  }
}
