package com.almlk.swiftkey.settings;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.theme.*;
import com.almlk.swiftkey.util.Prefs;
import java.util.*;

/** Filterable theme gallery with live, accurately proportioned keyboard cards. */
public final class ThemeSettingsActivity extends AppCompatActivity {
  private final ArrayList<String> allIds = new ArrayList<String>(), ids = new ArrayList<String>();
  private final ArrayList<TextView> chips = new ArrayList<TextView>();
  /** Round 69: شريط الثيمات الافتراضية الأفقي (كان شبكة GridView). */
  private LinearLayout defaultStrip;
  /** Round 72: حقل تجربة مباشر يظهر فوق الكيبورد مثل واجهة SwiftKey الأصلية. */
  private View testBar;
  private EditText testInput;
  private Prefs prefs;
  private String category = ThemeRepository.CATEGORY_ALL;
  /** Round 68: منع تثبيت ثيمين معاً بنقرة مزدوجة. */
  private boolean themeInstalling;
  /** Round 68: جيل قسم «كل الثيمات» — يُرفع عند كل إعادة بناء فتتجاهل
   * الاستدعاءات المتأخرة نتائج تحميل سابق ولا تترك بقايا. */
  private int allThemesGeneration;

  protected void onCreate(Bundle b) {
    super.onCreate(b);
    setContentView(R.layout.activity_themes);
    prefs = new Prefs(this);
    defaultStrip = (LinearLayout) findViewById(R.id.default_strip);
    testBar = findViewById(R.id.theme_test_bar);
    testInput = (EditText) findViewById(R.id.theme_test_input);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    findViewById(R.id.themes_back).setVisibility(View.GONE);
    SettingsTabs.bind(this, SettingsTabs.TAB_SHAPES);
    View.OnClickListener create =
        new View.OnClickListener() {
          public void onClick(View v) {
            startActivity(new Intent(ThemeSettingsActivity.this, CustomThemeActivity.class));
          }
        };
    findViewById(R.id.theme_create).setOnClickListener(create);
    findViewById(R.id.theme_try)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View v) {
                // Round 72: إظهار حقل التجربة والكيبورد داخل نفس صفحة السمات
                showKeyboardDirectly();
              }
            });
    buildCategories();
  }

  protected void onResume() {
    super.onResume();
    reload();
  }


  @Override
  public void onBackPressed() {
    if (testBar != null && testBar.getVisibility() == View.VISIBLE) {
      android.view.inputmethod.InputMethodManager imm =
          (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
      if (imm != null && testInput != null) imm.hideSoftInputFromWindow(testInput.getWindowToken(), 0);
      if (testInput != null) testInput.clearFocus();
      testBar.setVisibility(View.GONE);
      return;
    }
    super.onBackPressed();
  }

  private void reload() {
    allIds.clear();
    allIds.addAll(Arrays.asList(ThemeRepository.BUILT_IN_IDS));
    // Round 43: imported assets/theme/ folders join the grid after the built-ins.
    allIds.addAll(AssetThemeLibrary.ids(this));
    buildCustomStrip();
    applyFilter();
    buildAllThemes(); // Round 73: يعاد جلب/رسم أقسام ثيماتي في كل دخول للواجهة
  }

  /**
   * Round 40: user-created themes ride a horizontal scrolling strip pinned at the TOP of the
   * gallery; the default image themes fill the grid below it.
   */
  private void buildCustomStrip() {
    LinearLayout strip = (LinearLayout) findViewById(R.id.custom_strip);
    if (strip == null) return;
    strip.removeAllViews();
    TextView create = new TextView(this);
    create.setText("＋\nإنشاء سمة");
    create.setTextSize(13);
    create.setGravity(Gravity.CENTER);
    create.setTextColor(0xff2e73db);
    GradientDrawableCompat.apply(create, 0xffeaf2ff, 0xff2e73db, 14);
    create.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            startActivity(new Intent(ThemeSettingsActivity.this, CustomThemeActivity.class));
          }
        });
    LinearLayout.LayoutParams createParams =
        new LinearLayout.LayoutParams(dp(84), dp(132));
    createParams.setMargins(dp(4), dp(6), dp(4), dp(6));
    strip.addView(create, createParams);
    for (final String id : ThemeRepository.customIds(this)) {
      View card = getLayoutInflater().inflate(R.layout.item_theme_strip, strip, false);
      ThemeThumbnailView thumb = (ThemeThumbnailView) card.findViewById(R.id.theme_thumbnail);
      thumb.setTheme(KeyboardTheme.load(ThemeSettingsActivity.this, id));
      ((TextView) card.findViewById(R.id.theme_name))
          .setText(ThemeRepository.name(ThemeSettingsActivity.this, id));
      ((TextView) card.findViewById(R.id.theme_selected))
          .setText(id.equals(prefs.theme()) ? "◉" : "○");
      card.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View v) {
              prefs.setTheme(id);
              refreshSelections();
              Toast.makeText(ThemeSettingsActivity.this, "تم تطبيق السمة", Toast.LENGTH_SHORT)
                  .show();
              showKeyboardIfEnabled();
            }
          });
      card.setOnLongClickListener(
          new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
              Intent edit = new Intent(ThemeSettingsActivity.this, CustomThemeActivity.class);
              edit.putExtra("theme_id", id);
              startActivity(edit);
              return true;
            }
          });
      LinearLayout.LayoutParams params =
          new LinearLayout.LayoutParams(dp(112), dp(132));
      params.setMargins(dp(4), dp(6), dp(4), dp(6));
      strip.addView(card, params);
    }
  }

  // ------------------------------------------------ Round 68: كل الثيمات

  /**
   * Round 68: قسم «كل الثيمات» — أقسام مكتبة ثيماتي تُقرأ من themes.json
   * داخل الأرشيف عن بُعد؛ كل قسم شريط أفقي يُمرَّر يميناً ويساراً، وكل
   * ثيم بطاقة مرسومة بألوانه (base/dark/light/text من themes.json) مع
   * صورة زره الحقيقية، وشارة «↓» لأنه يحتاج تنزيلاً قبل التطبيق.
   * Round 71: الأقسام تُضاف بنفس تصميم «الثيمات الافتراضية» حرفياً —
   * عنوان 40dp + شريط أفقي 152dp + فاصل 6dp بين الأقسام.
   */
  private void buildAllThemes() {
    final LinearLayout root = (LinearLayout) findViewById(R.id.all_themes_container);
    final int generation = ++allThemesGeneration;
    root.removeAllViews();
    TextView loading = new TextView(this);
    loading.setText("جارٍ جلب أقسام الثيمات…");
    loading.setTextSize(13);
    loading.setTextColor(0xff5f6a7d);
    loading.setGravity(Gravity.CENTER);
    loading.setPadding(dp(8), dp(12), dp(8), dp(12));
    root.addView(loading);
    ThematyStore.sets(
        ThematyStore.TYPE_THEME,
        new ThematyStore.SetsListener() {
          public void onReady(java.util.List<ThematyStore.SetInfo> sets, String error) {
            if (generation != allThemesGeneration || isFinishing()) return;
            root.removeAllViews();
            if (sets == null || sets.isEmpty()) {
              root.addView(allThemesNote(root, "تعذر الوصول لمكتبة ثيماتي — انقر للإعادة"));
              return;
            }
            for (int index = 0; index < sets.size(); index++) {
              final ThematyStore.SetInfo set = sets.get(index);
              // فاصل رفيع بين الأقسام (مثل الفواصل الثابتة في التخطيط)
              if (index > 0) {
                View divider = new View(ThemeSettingsActivity.this);
                divider.setBackgroundColor(0xFFF0F0F0);
                root.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));
              }
              // Round 71: عنوان القسم — نفس تصميم «الثيمات الافتراضية» حرفياً
              TextView header = new TextView(ThemeSettingsActivity.this);
              header.setText(set.title + " (" + set.count + ")");
              header.setTextSize(15);
              header.setTextColor(0xff333333);
              header.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
              header.setPadding(dp(18), 0, dp(18), 0);
              root.addView(
                  header,
                  new LinearLayout.LayoutParams(
                      LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
              // Round 71: شريط أفقي — نفس ارتفاع شريط الافتراضية (152dp)
              HorizontalScrollView scroll =
                  new HorizontalScrollView(ThemeSettingsActivity.this);
              scroll.setHorizontalScrollBarEnabled(false);
              LinearLayout strip = new LinearLayout(ThemeSettingsActivity.this);
              strip.setOrientation(LinearLayout.HORIZONTAL);
              strip.setGravity(Gravity.CENTER_VERTICAL);
              strip.setPadding(dp(10), 0, dp(10), 0);
              strip.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
              scroll.addView(
                  strip,
                  new ViewGroup.LayoutParams(
                      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
              root.addView(
                  scroll,
                  new LinearLayout.LayoutParams(
                      LinearLayout.LayoutParams.MATCH_PARENT, dp(152)));
              fillThemeStrip(generation, strip, set);
            }
          }
        });
  }

  /** ملء شريط قسم واحد بثيماته — بلا ازدواج وبلا بقايا لطلب سابق. */
  private void fillThemeStrip(
      final int generation, final LinearLayout strip, final ThematyStore.SetInfo set) {
    ThematyStore.themes(
        set.id,
        new ThematyStore.ThemesListener() {
          public void onReady(java.util.List<ThematyStore.ThemeInfo> themes, String error) {
            if (generation != allThemesGeneration || isFinishing()) return; // جيل أحدث حلّ محله
            if (strip.getChildCount() > 0) return; // مُلئ سابقاً — لا ازدواج
            if (themes == null || themes.isEmpty()) {
              TextView note = new TextView(ThemeSettingsActivity.this);
              note.setText("تعذر جلب ثيمات القسم");
              note.setTextSize(12);
              note.setTextColor(0xff5f6a7d);
              note.setGravity(Gravity.CENTER);
              note.setPadding(dp(8), dp(8), dp(8), dp(8));
              strip.addView(note);
              return;
            }
            for (int index = 0; index < themes.size(); index++) {
              addLibraryThemeCard(strip, themes.get(index));
            }
          }
        });
  }

  /**
   * بطاقة ثيم مكتبة: خلفيتها بلون الثيم الفاتح من themes.json فوراً، وصورة
   * الزر تُجلب من الكاش المتوازي بأسرع وقت، وشارة الحالة (↓ يحتاج تنزيلاً /
   * ✓ مثبَّت) — والنقر يثبّت الثيم كاملاً (زر + خلفية) ثم يطبّقه فوراً.
   */
  private void addLibraryThemeCard(final LinearLayout strip, final ThematyStore.ThemeInfo theme) {
    final OnlineAssetStore.Item frameItem =
        OnlineAssetStore.savedItem(this, OnlineAssetStore.TYPE_FRAME, "tb-" + theme.id);
    final OnlineAssetStore.Item backgroundItem =
        OnlineAssetStore.savedItem(this, OnlineAssetStore.TYPE_BACKGROUND, "tg-" + theme.id);
    final boolean stored =
        frameItem != null
            && backgroundItem != null
            && (theme.spaceEntry.length() == 0 || frameItem.spacePath.length() > 0);
    KeyboardTheme active = KeyboardTheme.load(this, prefs.theme());
    final boolean applied =
        active != null
            && frameItem != null
            && frameItem.path.equals(active.keyFrameUri)
            && frameItem.spacePath.equals(active.keySpaceFrameUri);
    // Round 69: بطاقة الثيم كيبورد مصغّر كامل — نفس تصميم بطاقة «سماتي»
    // تماماً: مصغر يرسم صفوف المفاتيح بألوان الثيم (وفن زره من الكاش
    // المتوازي فور وصوله) — لا صورة فارغة ولا بطاقة لون مسطح.
    final View card = getLayoutInflater().inflate(R.layout.item_theme_strip, strip, false);
    final FrameLayout art = (FrameLayout) card.findViewById(R.id.theme_art);
    final ThemeThumbnailView thumb = (ThemeThumbnailView) card.findViewById(R.id.theme_thumbnail);
    thumb.setTheme(
        libraryPreviewTheme(
            theme,
            stored ? frameItem.path : null,
            stored ? frameItem.spacePath : null,
            stored ? backgroundItem.path : null));
    ((TextView) card.findViewById(R.id.theme_name)).setText(theme.name);
    ((TextView) card.findViewById(R.id.theme_selected))
        .setText(applied ? "◉" : (stored ? "✓" : "↓"));
    ((TextView) card.findViewById(R.id.theme_selected)).setTextColor(
        applied ? 0xff149fe8 : (stored ? 0xff1d9f57 : 0xff2e73db));
    card.setContentDescription(theme.name);
    card.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            installLibraryTheme(card, art, theme);
          }
        });
    // فن الزر من الكاش المتوازي — يكتمل شكل الكيبورد فور وصول الصورة
    ThematyStore.previewSource(
        theme.buttonEntry,
        240,
        new OnlineAssetStore.BitmapListener() {
          public void onReady(Bitmap picture) {
            if (picture != null && !isFinishing())
              thumb.setFrameArtOverride(KeyArtProcessor.shapeButton(picture));
          }
        });
    if (theme.spaceEntry.length() > 0) {
      ThematyStore.previewSource(
          theme.spaceEntry,
          360,
          new OnlineAssetStore.BitmapListener() {
            public void onReady(Bitmap picture) {
              if (picture != null && !isFinishing())
                thumb.setSpaceFrameArtOverride(picture);
            }
          });
    }
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(112), dp(132));
    params.setMargins(dp(4), dp(6), dp(4), dp(6));
    strip.addView(card, params);
  }

  /**
   * Round 69: ثيم معاينة للثيم المكتبة — ألوان themes.json (الخلفية light
   * والزر base والنص textColor) مع فن الزر والخلفية إن كانا منزّلين، فيرسم
   * المصغر كيبورداً حقيقياً بألوان الثيم قبل التنزيل وبعده.
   */
  private KeyboardTheme libraryPreviewTheme(
      ThematyStore.ThemeInfo theme, String framePath, String spacePath, String backgroundPath) {
    return new KeyboardTheme(
            theme.light,
            theme.base,
            theme.dark,
            theme.textColor,
            blend(theme.textColor, theme.base, .45f),
            theme.base,
            12f,
            20f,
            10f,
            backgroundPath == null ? "" : "file://" + backgroundPath,
            1f,
            0,
            0f,
            theme.light)
        .withKeyFrameUris(framePath == null ? "" : framePath, spacePath == null ? "" : spacePath);
  }

  /** نقرة ثيم: المثبَّت يطبَّق فوراً؛ وغيره يُنزَّل (زر + خلفية) ثم يطبَّق. */
  private void installLibraryTheme(
      final View card, final FrameLayout art, final ThematyStore.ThemeInfo theme) {
    if (themeInstalling) return; // لا تثبيتاً مزدوجاً
    final OnlineAssetStore.Item frameItem =
        OnlineAssetStore.savedItem(this, OnlineAssetStore.TYPE_FRAME, "tb-" + theme.id);
    final OnlineAssetStore.Item backgroundItem =
        OnlineAssetStore.savedItem(this, OnlineAssetStore.TYPE_BACKGROUND, "tg-" + theme.id);
    if (frameItem != null
        && backgroundItem != null
        && (theme.spaceEntry.length() == 0 || frameItem.spacePath.length() > 0)) {
      applyLibraryTheme(theme, frameItem.path, frameItem.spacePath, backgroundItem.path);
      return;
    }
    themeInstalling = true;
    // نفتح حقل التجربة فور نقرة المستخدم حتى يبقى طلب إظهار الكيبورد ضمن gesture المستخدم،
    // ثم يتبدل الثيم الحي فور انتهاء التنزيل والحفظ.
    showKeyboardIfEnabled();
    Toast.makeText(this, "جارٍ جلب الثيم من المكتبة…", Toast.LENGTH_SHORT).show();
    final ProgressBar ring = OnlineAssetStore.loadingRing(this);
    FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dp(30), dp(30));
    ringParams.gravity = Gravity.CENTER;
    art.addView(ring, ringParams);
    ThematyStore.installTheme(
        this,
        theme,
        new ThematyStore.ThemeInstallListener() {
          public void onReady(String framePath, String spacePath, String backgroundPath, String error) {
            if (isFinishing()) return;
            themeInstalling = false;
            art.removeView(ring);
            if (framePath == null) {
              Toast.makeText(
                      ThemeSettingsActivity.this,
                      "تعذر جلب زر الثيم — تحقق من الاتصال",
                      Toast.LENGTH_SHORT)
                  .show();
              return;
            }
            applyLibraryTheme(theme, framePath, spacePath, backgroundPath);
          }
        });
  }

  /**
   * يبني ثيماً كاملاً بألوان themes.json (الخلفية light والزر base والنص
   * textColor) مع صورة الزر والخلفية المثبّتين، ويحفظه كسمة مخصصة نشطة
   * ويطبّقه على الكيبورد فوراً.
   */
  private void applyLibraryTheme(
      ThematyStore.ThemeInfo theme, String framePath, String spacePath, String backgroundPath) {
    KeyboardTheme built =
        new KeyboardTheme(
                theme.light, // لون الخلفية تحت صورتها
                theme.base, // وجه الزر تحت صورته
                theme.dark, // الزر المضغوط
                theme.textColor, // لون الخط في المفاتيح
                blend(theme.textColor, theme.base, .45f), // لون الحروف الصغيرة
                theme.base, // لون التمييز
                12f,
                26f,
                13f,
                backgroundPath == null ? "" : "file://" + backgroundPath,
                1f,
                0,
                0f,
                theme.light)
            .withKeyFrame(0)
            .withKeyFrameUris(framePath, spacePath)
            .withKeySize(1f, 1f);
    String id = ThemeRepository.save(this, null, theme.name, built);
    prefs.setTheme(id);
    refreshSelections();
    Toast.makeText(this, "طُبِّق ثيم «" + theme.name + "»", Toast.LENGTH_SHORT).show();
    showKeyboardIfEnabled();
  }

  /** بطاقة نص لإعادة محاولة جلب أقسام الثيمات. */
  private TextView allThemesNote(final LinearLayout root, String message) {
    TextView note = new TextView(this);
    note.setText(message);
    note.setTextSize(13);
    note.setTextColor(0xff37424e);
    note.setGravity(Gravity.CENTER);
    note.setPadding(dp(8), dp(14), dp(8), dp(14));
    note.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            buildAllThemes(); // إعادة الجلب — بلا بقايا للطلب القديم
          }
        });
    return note;
  }

  /** مزج لونين — لون الحروف الصغيرة مشتق من نص الثيم ولون تمييزه. */
  private int blend(int first, int second, float amount) {
    float keep = 1f - amount;
    return Color.rgb(
        Math.round(Color.red(first) * keep + Color.red(second) * amount),
        Math.round(Color.green(first) * keep + Color.green(second) * amount),
        Math.round(Color.blue(first) * keep + Color.blue(second) * amount));
  }

  private void refreshSelections() {
    buildCustomStrip();
    buildDefaultStrip(); // Round 69: تحديث علامات التحديد في الشريط الافتراضي
  }

  /**
   * Round 72: بعد اختيار ثيم — تطبيقه ثم فتح حقل «جرب الإعداد» والكيبورد
   * مباشرة داخل نفس صفحة السمات، كما في لقطة المرجع. هكذا يرى المستخدم
   * اللون/الصورة/ارتفاع وعرض الأزرار فوراً بلا مغادرة الشاشة.
   */
  private void showKeyboardIfEnabled() {
    showThemeKeyboardPreview(false);
  }

  /** زر «التجربة الآن» يستخدم نفس حقل التجربة ولا يغلق واجهة السمات. */
  private void showKeyboardDirectly() {
    showThemeKeyboardPreview(true);
  }

  private void showThemeKeyboardPreview(boolean fromButton) {
    String current =
        android.provider.Settings.Secure.getString(
            getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
    if (current == null || current.indexOf(getPackageName()) < 0) {
      ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
          .showInputMethodPicker();
      if (fromButton) {
        Toast.makeText(this, "فعّل لوحة الملك أولاً ثم اضغط التجربة", Toast.LENGTH_SHORT).show();
      }
      return;
    }
    if (testBar != null) testBar.setVisibility(View.VISIBLE);
    if (testInput != null) {
      testInput.setVisibility(View.VISIBLE);
      testInput.setFocusableInTouchMode(true);
      testInput.requestFocus();
      try {
        testInput.setSelection(testInput.getText().length());
      } catch (Exception ignored) {
      }
    }
    requestImeRefreshAndShow();
  }

  private void requestImeRefreshAndShow() {
    final Intent show = new Intent(com.almlk.swiftkey.ime.AlmlkImeRuntimeBase.ACTION_SHOW_KEYBOARD);
    show.setPackage(getPackageName());
    sendBroadcast(show);
    if (testInput == null) return;
    final android.view.inputmethod.InputMethodManager imm =
        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) imm.showSoftInput(testInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    testInput.postDelayed(
        new Runnable() {
          public void run() {
            sendBroadcast(show);
            if (imm != null)
              imm.showSoftInput(testInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
          }
        },
        120);
  }

  private void applyFilter() {
    ids.clear();
    for (String id : allIds) if (ThemeRepository.matches(id, category)) ids.add(id);
    buildDefaultStrip(); // Round 69: الافتراضية شريط أفقي يتبع الفلتر
    styleChips();
  }

  /**
   * Round 69: الثيمات الافتراضية شريط أفقي يُمرَّر للاختيار بينها — نفس بطاقة
   * «سماتي» تماماً (مصغر كيبورد كامل + الاسم + علامة التحديد)، فتبدو كل
   * أقسام الشاشة تصميماً واحداً كأنها جزء من الكيبورد.
   */
  private void buildDefaultStrip() {
    if (defaultStrip == null) return;
    defaultStrip.removeAllViews(); // لا بقايا
    for (int index = 0; index < ids.size(); index++) {
      final String id = ids.get(index);
      View card = getLayoutInflater().inflate(R.layout.item_theme_strip, defaultStrip, false);
      ThemeThumbnailView thumb = (ThemeThumbnailView) card.findViewById(R.id.theme_thumbnail);
      thumb.setTheme(KeyboardTheme.load(ThemeSettingsActivity.this, id));
      ((TextView) card.findViewById(R.id.theme_name))
          .setText(ThemeRepository.name(ThemeSettingsActivity.this, id));
      ((TextView) card.findViewById(R.id.theme_selected))
          .setText(id.equals(prefs.theme()) ? "◉" : "○");
      card.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View v) {
              prefs.setTheme(id);
              refreshSelections();
              Toast.makeText(ThemeSettingsActivity.this, "تم تطبيق السمة", Toast.LENGTH_SHORT).show();
              showKeyboardIfEnabled();
            }
          });
      card.setOnLongClickListener(
          new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
              // Round 40: long-press opens the customization editor preloaded with the
              // theme; built-ins save back as a NEW custom copy, never the original.
              Intent edit = new Intent(ThemeSettingsActivity.this, CustomThemeActivity.class);
              edit.putExtra("theme_id", id);
              startActivity(edit);
              Toast.makeText(
                      ThemeSettingsActivity.this,
                      id.startsWith("custom_") ? "تحرير السمة" : "تخصيص نسخة من السمة",
                      Toast.LENGTH_SHORT)
                  .show();
              return true;
            }
          });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(112), dp(132));
      params.setMargins(dp(4), dp(6), dp(4), dp(6));
      defaultStrip.addView(card, params);
    }
  }

  private void buildCategories() {
    LinearLayout bar = (LinearLayout) findViewById(R.id.theme_categories);
    String[] labels = {"الكل", "أبيض", "أسود", "حجري", "بناتي", "أزرق", "رمادي", "خلفيات", "مستوردة"};
    final String[] values = {
      ThemeRepository.CATEGORY_ALL,
      ThemeRepository.CATEGORY_WHITE,
      ThemeRepository.CATEGORY_BLACK,
      ThemeRepository.CATEGORY_STONE,
      ThemeRepository.CATEGORY_GIRLY,
      ThemeRepository.CATEGORY_BLUE,
      ThemeRepository.CATEGORY_GRAY,
      ThemeRepository.CATEGORY_SCENES,
      ThemeRepository.CATEGORY_IMPORTED
    };
    for (int i = 0; i < labels.length; i++) {
      final String value = values[i];
      TextView chip = new TextView(this);
      chip.setText(labels[i]);
      chip.setTag(value);
      chip.setTextSize(13);
      chip.setGravity(Gravity.CENTER);
      chip.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View v) {
              category = value;
              applyFilter();
            }
          });
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(36));
      lp.setMargins(dp(4), 0, dp(4), 0);
      bar.addView(chip, lp);
      chips.add(chip);
    }
    styleChips();
  }

  private void styleChips() {
    for (TextView chip : chips) {
      boolean selected = category.equals(chip.getTag());
      chip.setTextColor(selected ? Color.WHITE : Color.DKGRAY);
      GradientDrawableCompat.apply(
          chip, selected ? 0xff2e73db : Color.TRANSPARENT, selected ? 0xff2e73db : 0xffb7b7b7, 18);
    }
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

}
