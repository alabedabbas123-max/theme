package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.data.EmojiDb;
import com.almlk.swiftkey.engine.IntelligentSuggestionManager;
import com.almlk.swiftkey.engine.TextNormalizer;
import com.almlk.swiftkey.theme.KeyboardTheme;
import com.almlk.swiftkey.util.Prefs;
import java.io.*;
import java.util.*;

public final class EmojiPanelView extends LinearLayout {
  public interface Callback {
    void onEmoji(String emoji);

    void onDelete();

    void onKeyboard();

    void onSearchRequested();

    void onFeature(String feature);
  }

  private static final class Category {
    final String id, ar, en;
    final int icon;
    final ArrayList<String> items = new ArrayList<String>();

    Category(String id, String ar, String en, int icon) {
      this.id = id;
      this.ar = ar;
      this.en = en;
      this.icon = icon;
    }
  }

  private final List<Category> categories =
      Arrays.asList(
          new Category("recent", "الأخيرة", "recent", R.drawable.ic_recent),
              new Category("faces", "الوجوه والمشاعر", "faces emotions", R.drawable.ic_emoji),
          new Category("people", "الأشخاص والجسم", "people body", R.drawable.ic_people),
              new Category(
                  "animals", "الحيوانات والطبيعة", "animals nature", R.drawable.ic_animals),
          new Category("food", "الطعام والشراب", "food drink", R.drawable.ic_food),
              new Category(
                  "activities", "الأنشطة والرياضة", "activities sports", R.drawable.ic_activities),
          new Category("travel", "السفر والأماكن", "travel places", R.drawable.ic_travel),
              new Category("objects", "الأشياء", "objects", R.drawable.ic_objects),
          new Category("symbols", "الرموز والقلوب", "symbols hearts", R.drawable.ic_symbols),
              new Category("flags", "الأعلام والدول", "flags countries", R.drawable.ic_flags));
  private final android.graphics.Paint glyphPaint =
      new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private final Map<String, Boolean> glyphSupport = new HashMap<String, Boolean>();
  private final Map<String, String> names = new HashMap<String, String>();
  private final Map<String, Integer> emojiUse = new HashMap<String, Integer>();
  private final Map<String, ArrayList<String>> suggestionIndex =
      new HashMap<String, ArrayList<String>>();
  private final android.os.Handler deleteHandler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable deleteLoop =
      new Runnable() {
        public void run() {
          if (callback != null) callback.onDelete();
          deleteHandler.postDelayed(this, interactionPrefs.deleteRepeatInterval());
        }
      };
  private GridView grid;
  private EditText search;
  private LinearLayout categoryBar;
  private android.content.SharedPreferences emojiPrefs;
  private EmojiDb emojiDb;
  private IntelligentSuggestionManager intelligentSuggestions;
  private Callback callback;
  private int selected = 1;
  private int searchGeneration;
  private float downX;
  private KeyboardTheme theme;
  private Prefs interactionPrefs;
  private EmojiAdapter adapter;
  private volatile boolean emojiDataLoaded;
  private volatile boolean emojiDataLoading;

  public EmojiPanelView(Context c, AttributeSet a) {
    super(c, a);
    init();
  }

  public EmojiPanelView(Context c) {
    super(c);
    init();
  }

  private void init() {
    inflate(getContext(), R.layout.emoji_panel, this);
    interactionPrefs = new Prefs(getContext());
    theme = KeyboardTheme.from(interactionPrefs.theme());
    grid = findViewById(R.id.emoji_grid);
    search = findViewById(R.id.emoji_search);
    categoryBar = findViewById(R.id.emoji_categories);
    emojiPrefs = getContext().getSharedPreferences("emoji_suggestions", Context.MODE_PRIVATE);
    emojiDb = EmojiDb.getInstance(getContext());
    intelligentSuggestions = IntelligentSuggestionManager.getInstance(getContext());
    adapter = new EmojiAdapter();
    grid.setAdapter(adapter);
    if (android.os.Build.VERSION.SDK_INT >= 21) search.setShowSoftInputOnFocus(false);
    search.setFocusable(false);
    search.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            if (callback != null) callback.onSearchRequested();
          }
        });
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            refresh();
          }

          public void afterTextChanged(Editable e) {}
        });
    grid.setOnTouchListener(
        new View.OnTouchListener() {
          public boolean onTouch(View v, MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) downX = e.getX();
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
              float d = e.getX() - downX;
              if (Math.abs(d) > dp(55)) {
                select((selected + (d < 0 ? 1 : -1) + categories.size()) % categories.size());
                return true;
              }
            }
            return false;
          }
        });
    findViewById(R.id.emoji_keyboard_top)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View v) {
                if (callback != null) callback.onKeyboard();
              }
            });
    findViewById(R.id.emoji_ai).setOnClickListener(featureListener("AI"));
    findViewById(R.id.emoji_stickers).setOnClickListener(featureListener("الملصقات"));
    findViewById(R.id.emoji_gif).setOnClickListener(featureListener("GIF"));
    buildCategories();
    applyTheme();
    refresh();
    postDelayed(
        new Runnable() {
          public void run() {
            loadEmojiDataAsync();
          }
        },
        350);
  }

  private void loadEmojiDataAsync() {
    if (emojiDataLoaded || emojiDataLoading) return;
    emojiDataLoading = true;
    Thread loader =
        new Thread(
            new Runnable() {
              public void run() {
                loadUnicodeEmoji();
                loadSearchLexicon();
                seedArabicNames();
                loadRecents();
                buildSuggestionIndex();
                post(
                    new Runnable() {
                      public void run() {
                        emojiDataLoading = false;
                        emojiDataLoaded = true;
                        if (getVisibility() == VISIBLE) refresh();
                      }
                    });
              }
            },
            "Almlk-Emoji-Loader");
    loader.setPriority(Thread.MIN_PRIORITY);
    loader.start();
  }

  private Category byId(String id) {
    for (Category c : categories) if (c.id.equals(id)) return c;
    return null;
  }

  private void loadUnicodeEmoji() {
    try {
      BufferedReader r =
          new BufferedReader(
              new InputStreamReader(
                  getContext().getAssets().open("emoji/emoji_data.tsv"), "UTF-8"));
      String line;
      while ((line = r.readLine()) != null) {
        String[] p = line.split("\\t", 3);
        if (p.length < 3) continue;
        Category c = byId(p[0]);
        if (c != null && !c.items.contains(p[1])) c.items.add(p[1]);
        names.put(p[1], p[2]);
      }
      r.close();
    } catch (IOException ignored) {
    }
    Category recent = categories.get(0);
    recent.items.addAll(Arrays.asList("😊", "😂", "❤️", "👍", "😭", "✨", "🤔", "🎉"));
  }

  private void loadSearchLexicon() {
    try {
      BufferedReader r =
          new BufferedReader(
              new InputStreamReader(
                  getContext().getAssets().open("emoji/emoji_search_ar_en.tsv"), "UTF-8"));
      String line;
      while ((line = r.readLine()) != null) {
        String[] p = line.split("\\t", 3);
        if (p.length < 3) continue;
        String old = names.get(p[0]);
        names.put(p[0], (old == null ? "" : old + " ") + p[1] + " " + p[2]);
      }
      r.close();
    } catch (IOException ignored) {
    }
  }

  private void loadRecents() {
    Category recent = categories.get(0);
    String saved = emojiPrefs.getString("recent", "");
    if (saved != null && saved.length() > 0) {
      String[] values = saved.split("\\u001f");
      for (int i = values.length - 1; i >= 0; i--) {
        String e = values[i];
        if (e.length() > 0) {
          recent.items.remove(e);
          recent.items.add(0, e);
        }
      }
    }
    String usage = emojiPrefs.getString("usage", "");
    if (usage != null) {
      for (String row : usage.split("\\n")) {
        String[] p = row.split("\\t", 2);
        if (p.length == 2)
          try {
            emojiUse.put(p[0], Integer.parseInt(p[1]));
          } catch (Exception ignored) {
          }
      }
    }
  }

  private void seedArabicNames() {
    put("😊", "مبتسم ابتسامة smile happy");
    put("😂", "ضحك دموع الفرح laugh tears");
    put("❤️", "قلب حب heart love");
    put("👍", "اعجاب موافق like thumbs up");
    put("😭", "بكاء حزين cry sad");
    put("✨", "لمعان نجوم sparkles");
    put("🤔", "تفكير thinking");
    put("🎉", "احتفال حفلة party");
    put("🙏", "دعاء شكر pray thanks");
    put("🇾🇪", "اليمن yemen");
    put("☕", "قهوة coffee");
    put("🐱", "قطة cat");
    put("🐶", "كلب dog");
    put("🍕", "بيتزا pizza");
    put("⚽", "كرة قدم football soccer");
    put("✈️", "طائرة سفر plane travel");
  }

  private void put(String e, String n) {
    String old = names.get(e);
    names.put(e, (old == null ? "" : old + " ") + n);
  }

  public void activate() {
    deleteHandler.removeCallbacks(deleteLoop);
    loadEmojiDataAsync();
    refresh();
  }

  public void releaseTransientState() {
    deleteHandler.removeCallbacksAndMessages(null);
    searchGeneration++;
    if (adapter != null) {
      adapter.values.clear();
      adapter.notifyDataSetChanged();
    }
    clearAnimation();
    setVisibility(GONE);
  }

  public void setCallback(Callback c) {
    callback = c;
  }

  public void setTheme(KeyboardTheme value) {
    if (value == null) return;
    theme = value;
    applyTheme();
    updateCategoryColors();
    if (adapter != null) adapter.notifyDataSetChanged();
  }

  private void feature(String s) {
    if (callback != null) callback.onFeature(s);
  }

  private View.OnClickListener featureListener(final String featureName) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        feature(featureName);
      }
    };
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private void buildCategories() {
    categoryBar.removeAllViews();
    ImageButton delete = iconButton(R.drawable.ic_backspace, "حذف");
    ((LinearLayout.LayoutParams) delete.getLayoutParams()).weight = .72f;
    delete.setPadding(dp(8), dp(8), dp(8), dp(8));
    delete.setOnTouchListener(
        new View.OnTouchListener() {
          public boolean onTouch(View v, MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
              if (callback != null) callback.onDelete();
              deleteHandler.postDelayed(deleteLoop, interactionPrefs.deleteStartDelay());
              return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP
                || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
              deleteHandler.removeCallbacks(deleteLoop);
              return true;
            }
            return true;
          }
        });
    categoryBar.addView(delete);
    int[] order = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
    for (int value : order) {
      final int index = value;
      ImageButton b =
          iconButton(
              categories.get(index).icon,
              categories.get(index).ar + " · " + categories.get(index).en);
      b.setTag(index);
      b.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View v) {
              select(index);
            }
          });
      categoryBar.addView(b);
    }
    updateCategoryColors();
  }

  private ImageButton iconButton(int icon, String description) {
    ImageButton b = new ImageButton(getContext());
    b.setImageResource(icon);
    b.setContentDescription(description);
    b.setPadding(dp(9), dp(9), dp(9), dp(9));
    b.setBackgroundColor(Color.TRANSPARENT);
    b.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    return b;
  }

  private void select(int i) {
    selected = i;
    if (search.length() > 0) search.setText("");
    grid.setSelection(0);
    updateCategoryColors();
    refresh();
  }

  private void updateCategoryColors() {
    for (int i = 1; i < categoryBar.getChildCount(); i++) {
      ImageButton b = (ImageButton) categoryBar.getChildAt(i);
      boolean active = ((Integer) b.getTag()) == selected;
      b.setColorFilter(active ? theme.accent : interfaceTextColor());
      GradientDrawable bg = new GradientDrawable();
      bg.setColor(active ? (theme.accent & 0x33ffffff) : Color.TRANSPARENT);
      bg.setCornerRadius(dp(11));
      b.setBackground(bg);
    }
  }

  private int interfaceTextColor() {
    return com.almlk.swiftkey.theme.ThemeChipArt.textColor(getContext(), theme);
  }

  private void applyTheme() {
    // Round 52: خلفية الثيم نفسها على اللوحة كلها — لا لون مسطح مع صورة
    com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(this, getContext(), theme);
    if (getChildCount() > 0)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(getChildAt(0), getContext(), theme);
    com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(grid, getContext(), theme);
    GradientDrawable searchBackground = new GradientDrawable();
    searchBackground.setColor(theme.key);
    searchBackground.setStroke(dp(1), theme.accent);
    searchBackground.setCornerRadius(dp(18));
    search.setBackground(searchBackground);
    search.setTextColor(theme.text);
    search.setHintTextColor(theme.sub);
    search.setHintTextColor(theme.sub);
    int[] top = {
      R.id.emoji_ai,
      R.id.emoji_stickers,
      R.id.emoji_gif,
      R.id.emoji_selected,
      R.id.emoji_keyboard_top
    };
    for (int id : top)
      ((ImageButton) findViewById(id))
          .setColorFilter(id == R.id.emoji_selected ? theme.accent : interfaceTextColor());
  }

  public interface SearchResultCallback {
    void onResult(String query, List<String> values);
  }

  public void searchEmojiAsync(
      final String query, final int limit, final SearchResultCallback callback) {
    emojiDb.searchAsync(
        query,
        limit,
        new EmojiDb.SearchCallback() {
          public void onResult(String returnedQuery, List<String> values) {
            LinkedHashSet<String> ordered = new LinkedHashSet<String>();
            ordered.addAll(intelligentSuggestions.getEmojiSuggestions(returnedQuery));
            ordered.addAll(values);
            ArrayList<String> supported = new ArrayList<String>();
            for (String value : ordered) {
              if (supported.size() >= limit) break;
              if (isGlyphSupported(value)) supported.add(value);
            }
            callback.onResult(returnedQuery, supported);
          }
        });
  }

  private void buildSuggestionIndex() {
    suggestionIndex.clear();
    for (Category c : categories)
      for (String emoji : c.items) {
        String hay =
            TextNormalizer.foldForComparison(
                c.ar + " " + c.en + " " + (names.containsKey(emoji) ? names.get(emoji) : ""), "ar");
        for (String token : hay.split("[\\s,;:|،؛]+", -1)) {
          if (token.length() < 2) continue;
          ArrayList<String> values = suggestionIndex.get(token);
          if (values == null) {
            values = new ArrayList<String>();
            suggestionIndex.put(token, values);
          }
          if (values.size() < 24 && !values.contains(emoji)) values.add(emoji);
        }
      }
  }

  public String suggestEmoji(String query) {
    List<String> curated = intelligentSuggestions.getEmojiSuggestions(query);
    for (String emoji : curated) {
      if (isGlyphSupported(emoji)) return emoji;
    }
    if (!emojiDataLoaded) return "";
    String q = TextNormalizer.foldForComparison(query, "ar");
    if (q.length() < 2) return "";
    LinkedHashSet<String> candidates = new LinkedHashSet<String>();
    ArrayList<String> exact = suggestionIndex.get(q);
    if (exact != null) candidates.addAll(exact);
    String best = "";
    int bestScore = 0;
    for (String emoji : candidates) {
      int usage = emojiUse.containsKey(emoji) ? emojiUse.get(emoji) * 120 : 0;
      int recent = categories.get(0).items.indexOf(emoji);
      int score = 9000 + usage + (recent >= 0 ? 800 - Math.min(700, recent * 20) : 0);
      if (score > bestScore) {
        bestScore = score;
        best = emoji;
      }
    }
    return best;
  }

  private boolean matchesAll(String hay, String query) {
    for (String token : query.split("\\s+"))
      if (token.length() > 0 && !hay.contains(token)) return false;
    return true;
  }

  private int emojiScore(String emoji, String query) {
    int score = emojiUse.containsKey(emoji) ? emojiUse.get(emoji) * 120 : 0;
    int recent = categories.get(0).items.indexOf(emoji);
    if (recent >= 0) score += 3000 - Math.min(2500, recent * 40);
    if (query.length() == 0) return score;
    String hay =
        (emoji + " " + (names.containsKey(emoji) ? names.get(emoji) : "")).toLowerCase(Locale.ROOT);
    if (emoji.equals(query)) score += 20000;
    if (hay.startsWith(query)) score += 9000;
    if (hay.contains(" " + query + " ") || hay.endsWith(" " + query)) score += 7000;
    for (String token : query.split("\\s+")) {
      if (hay.startsWith(token) || hay.contains(" " + token)) score += 1800;
      else if (hay.contains(token)) score += 700;
    }
    return score;
  }

  private boolean isGlyphSupported(String emoji) {
    if (android.os.Build.VERSION.SDK_INT < 23) return true;
    Boolean cached = glyphSupport.get(emoji);
    if (cached != null) return cached;
    glyphPaint.setTextSize(dp(24));
    boolean supported = glyphPaint.hasGlyph(emoji);
    glyphSupport.put(emoji, supported);
    return supported;
  }

  private void refresh() {
    if (adapter == null) return;
    if (!emojiDataLoaded) {
      adapter.values.clear();
      adapter.notifyDataSetChanged();
      return;
    }
    final String q = search.getText().toString().trim();
    final int generation = ++searchGeneration;
    if (q.length() == 0) {
      adapter.values.clear();
      for (String e : categories.get(selected).items)
        if (isGlyphSupported(e)) adapter.values.add(e);
      adapter.notifyDataSetChanged();
      return;
    }
    searchEmojiAsync(
        q,
        300,
        new SearchResultCallback() {
          public void onResult(String returned, List<String> values) {
            if (generation != searchGeneration || !q.equals(search.getText().toString().trim()))
              return;
            adapter.values.clear();
            adapter.values.addAll(values);
            adapter.notifyDataSetChanged();
          }
        });
  }

  public void remember(String e) {
    emojiDb.remember(e);
    Category recent = categories.get(0);
    recent.items.remove(e);
    recent.items.add(0, e);
    while (recent.items.size() > 64) recent.items.remove(recent.items.size() - 1);
    int count = emojiUse.containsKey(e) ? emojiUse.get(e) + 1 : 1;
    emojiUse.put(e, count);
    StringBuilder recents = new StringBuilder();
    for (String value : recent.items) {
      if (recents.length() > 0) recents.append('\u001f');
      recents.append(value);
    }
    ArrayList<Map.Entry<String, Integer>> usage =
        new ArrayList<Map.Entry<String, Integer>>(emojiUse.entrySet());
    Collections.sort(
        usage,
        new Comparator<Map.Entry<String, Integer>>() {
          public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
            return b.getValue() - a.getValue();
          }
        });
    StringBuilder counts = new StringBuilder();
    for (int i = 0; i < Math.min(100, usage.size()); i++) {
      Map.Entry<String, Integer> item = usage.get(i);
      counts.append(item.getKey()).append('\t').append(item.getValue()).append('\n');
    }
    emojiPrefs
        .edit()
        .putString("recent", recents.toString())
        .putString("usage", counts.toString())
        .apply();
  }

  private final class EmojiAdapter extends BaseAdapter {
    final ArrayList<String> values = new ArrayList<String>();

    public int getCount() {
      return values.size();
    }

    public Object getItem(int p) {
      return values.get(p);
    }

    public long getItemId(int p) {
      return p;
    }

    public View getView(int pos, View convert, android.view.ViewGroup parent) {
      TextView t = convert instanceof TextView ? (TextView) convert : new TextView(getContext());
      t.setText(values.get(pos));
      t.setTextSize(24);
      t.setGravity(Gravity.CENTER);
      t.setMinHeight(dp(40));
      t.setBackgroundColor(Color.TRANSPARENT);
      t.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View v) {
              String e = ((TextView) v).getText().toString();
              remember(e);
              if (callback != null) callback.onEmoji(e);
            }
          });
      return t;
    }
  }
}
