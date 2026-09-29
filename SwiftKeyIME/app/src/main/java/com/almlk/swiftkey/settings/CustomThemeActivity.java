package com.almlk.swiftkey.settings;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.ime.KeyboardResizeModel;
import com.almlk.swiftkey.ime.KeyboardXmlParser;
import com.almlk.swiftkey.ime.LayoutProvider;
import com.almlk.swiftkey.ime.SmartKeyboardView;
import com.almlk.swiftkey.ime.ThemeDecorationBarLayout;
import com.almlk.swiftkey.model.KeyboardLayout;
import com.almlk.swiftkey.model.KeyboardLayouts;
import com.almlk.swiftkey.theme.KeyFrames;
import com.almlk.swiftkey.theme.PressEffects;
import com.almlk.swiftkey.theme.KeyArtProcessor;
import com.almlk.swiftkey.theme.KeyboardTheme;
import com.almlk.swiftkey.theme.ThemeRepository;
import com.almlk.swiftkey.util.Prefs;

/** Live custom-theme studio with built-in backgrounds, key shapes, colors and transparency. */
public final class CustomThemeActivity extends AppCompatActivity {
  private static final int PICK_IMAGE = 82, PICK_BACKGROUND = 83;
  // Round 49: خلفيات حقيقية مصوّرة (لا أشكال تدرجية) — عشر صور:
  // طبيعة ×2، مشاهير ×2، رومانسي لحبيبين ×2، ورود ×2، وردي ×2 (WebP).
  private static final String[] BACKGROUND_NAMES = {
    "custom_bg_nature_lake",
    "custom_bg_nature_forest",
    "custom_bg_star_male",
    "custom_bg_star_female",
    "custom_bg_love_sunset",
    "custom_bg_love_hands",
    "custom_bg_roses_red",
    "custom_bg_roses_pink",
    "custom_bg_pink_sky",
    "custom_bg_pink_soft"
  };
  private static final int[] BACKGROUND_RESOURCES = {
    R.drawable.custom_bg_nature_lake,
    R.drawable.custom_bg_nature_forest,
    R.drawable.custom_bg_star_male,
    R.drawable.custom_bg_star_female,
    R.drawable.custom_bg_love_sunset,
    R.drawable.custom_bg_love_hands,
    R.drawable.custom_bg_roses_red,
    R.drawable.custom_bg_roses_pink,
    R.drawable.custom_bg_pink_sky,
    R.drawable.custom_bg_pink_soft
  };
  private static final int[] KEY_COLORS = {
    0xffffffff,
    0xffe9edf3,
    0xff202328,
    0xff101114,
    0xffdcecff,
    0xffb8e5d7,
    0xffffdca0,
    0xffffb9cb,
    0xffcdbbf2,
    0xff72b7e8
  };
  private static final int[] ACCENT_COLORS = {
    0xff1677e8, 0xff00a884, 0xff8b5cc7, 0xffe34865, 0xffff9d19, 0xff10a9c8, 0xff48515f, 0xffffffff
  };
  private static final float[] SHAPE_RADII = {0f, 5f, 10f, 17f, 28f, 36f};
  private static final float MAIN_TEXT_POS_MIN = .50f;
  private static final float MAIN_TEXT_POS_MAX = .90f;
  private static final float SUB_TEXT_POS_MIN = .02f;
  private static final float SUB_TEXT_POS_MAX = .42f;
  // Round 50: «حاد» صارت «مربع» بطلب المالك (زر مربع/دائري/خفيف...) — القيم كما هي.
  private static final String[] SHAPE_NAMES = {"مربع", "خفيف", "متوسط", "دائري", "كبير", "كبسولة"};

  private int background, key, text, sub, accent, fontStyle, surfaceColor;
  private float radius, mainSize, subSize, opacity, backgroundDim;
  /** Round 50: الإطار الاحترافي المختار (0 = الوجه الكلاسيكي، 1..10 = صور KeyFrames). */
  private int keyFrame;
  /** Round 51: مسار إطار محمَّل من الإنترنت — أولوية على الإطارات المدمجة. */
  private String keyFrameUri = "";
  /** Round 72: مسار زر المسافة المرافق لإطار ثيماتي المحمّل. */
  private String keySpaceFrameUri = "";
  /** Round 69: مقياسا عرض/ارتفاع الزر داخل خانة المفتاح (شريطا قسم الخط). */
  private float keyWidthScale = 1f, keyHeightScale = 1f;
  /** Round 45: موضع الحرف الرئيسي والحرف الصغير داخل الزر من قسم الخط. */
  private float mainTextPosition = .73f, subTextPosition = .18f;
  private String image = "", editingId;
  /**
   * Round 48: جلد مفاتيح الثيم المفتوح (lux/مستورد) يبقى حياً في الجلسة —
   * الفتح يطبق الثيم المستخدم «بكل تفاصيله»، وأي تعديل على وجه الزر
   * (لون/شكل/وضوح) يسقط الجلد كي يظهر التعديل.
   */
  private String sessionSkin = "";
  /** Round 40: true while customizing a copy of a BUILT-IN theme (long-press flow). */
  private boolean editingBuiltin;
  private SmartKeyboardView preview;
  private EditText name;
  private TextView opacityValue;
  private LinearLayout backgroundPresets, fontPresets;
  /** Round 68: شريط ألوان الخلفية (كلون) + أشرطة ألوان الخط الرئيسي والصغير. */
  private LinearLayout backgroundColorPresets, textColorPresets, subColorPresets;
  /** Round 50/68: شبكة عناصر قسم الأزرار — أشكال أو إطارات مدمجة أو نتائج قسم ثيماتي. */
  private GridLayout framePresets;
  // Round 67/68: أزرار مكتبة ثيماتي — رقائق أقسام أفقية ثابتة ثم نتائج/محفوظات
  // في الشبكة نفسها (تُقرأ من أرشيف ثيماتي على GitHub عن بُعد بطلبات النطاق).
  private GridLayout onlineFrameQueries; // Round 57: شبكة بطاقات لا شريط أفقي
  private GridLayout onlineFrameGrid;
  private LinearLayout buttonSectionsStrip; // Round 68: شريط أقسام الأزرار الأفقي الثابت
  private final java.util.ArrayList<String> buttonChipIds = new java.util.ArrayList<String>();
  private final java.util.ArrayList<TextView> buttonChips = new java.util.ArrayList<TextView>();
  private boolean buttonsLoaded; // Round 68: جلب أقسام الأزرار عند أول فتح للقسم
  private String buttonsSection = "builtin"; // shapes | builtin | معرف قسم ثيماتي
  private String onlineFrameSet = "";
  private boolean frameReload;
  private java.util.List<OnlineAssetStore.Item> onlineFrameResults;
  private java.util.List<ThematyStore.SetInfo> frameSets;
  // Round 68: خلفيات مكتبة ثيماتي داخل قسم الخلفيات — رقائق أقسام + شبكة بطاقات
  private LinearLayout designerBgSectionsStrip;
  private final java.util.ArrayList<String> designerBgChipIds = new java.util.ArrayList<String>();
  private final java.util.ArrayList<TextView> designerBgChips = new java.util.ArrayList<TextView>();
  private GridLayout designerBgGrid;
  private boolean designerBgLoaded;
  private java.util.List<ThematyStore.SetInfo> designerBgSets;
  private String designerBgSection = "";
  private String designerBgStatus;
  private String designerBgRetry;
  private java.util.List<OnlineAssetStore.Item> designerBgResults;
  // Round 56: تفاعلات الضغط المتحركة — قسم تلقائي يمثل التفاعلات عند الضغط
  private GridLayout pressFxPresets;
  private GridLayout pressFxQueries; // Round 57: شبكة بطاقات لا شريط أفقي
  private GridLayout pressFxOnlineGrid;
  private boolean pressFxLoaded;
  private String pressFxSet = "";
  private boolean pressFxReload;
  private java.util.List<OnlineAssetStore.Item> pressFxResults;
  private java.util.List<ThematyStore.SetInfo> pressFxSets;
  /** Round 59: حالة قسم الإنترنت داخل اللستة الموحدة — null = النتائج جاهزة. */
  private String frameStatus;
  private String frameRetry;
  private String pressFxStatus;
  private String pressFxRetry;
  private int pressEffect;
  private String pressEffectUri = "";

  private View autoSection,
      backgroundSection,
      keysSection, // Round 68: تراكب قسم الأزرار الثابت فوق محتوى التمرير
      fontSection;
  private ImageButton autoTab, backgroundTab, keysTab, fontTab;
  private GridLayout autoThemeGrid;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_custom_theme);
    AppGate.ensureInternet(this); // Round 57: تفعيل صلاحية الإنترنت عند فتح الاستوديو
    editingId = getIntent().getStringExtra("theme_id");
    // Round 40: long-pressing a BUILT-IN theme opens the editor preloaded with it; saving
    // always creates a NEW custom copy (ThemeRepository.save never rewrites a built-in id).
    editingBuiltin = editingId != null && !editingId.startsWith("custom_");
    // Round 48: فتح الاستوديو يطبق «الثيم المستخدم» بكل تفاصيله (ألوانه وصورته
    // وجلد مفاتيحه وسطحه) — لا شكلاً واحداً ثابتاً. الاحتياط السابق يبقى لغريب.
    KeyboardTheme theme;
    if (editingId != null) {
      theme = KeyboardTheme.load(this, editingId);
    } else {
      theme = KeyboardTheme.load(this, new Prefs(this).theme());
      if (theme == null) {
        theme =
            new KeyboardTheme(
                0xff20252c,
                0xffffffff,
                0xffd8e4f5,
                0xff172033,
                0xff637083,
                0xff2e73db,
                10f,
                22f,
                10.5f,
                "custom_bg_aurora",
                .72f);
      }
    }
    sessionSkin = theme == null ? "" : theme.keySkin;
    readTheme(theme);
    bindViews();
    configureHeader();
    configureTabs();
    configureSliders();
    configureImageActions();
    configureDelete();
    rebuildControls();
    showSection(0);
    // Round 52: المعاينة هي الكيبورد نفسه — نفس ارتفاع الزر الحقيقي وحالة صف
    // الأرقام الفعلية، فما تراه أثناء التصميم هو ما سيُطبَّق تماماً.
    preview.setNumberRowVisible(realNumberRowVisible());
    preview.setKeyHeightDp(KeyboardResizeModel.loadRowHeightDp(this));
    updatePreview();
    // Round 48: لا نفتح منتقي الخلفية تلقائياً — المعاينة تظهر الثيم المستخدم
    // بكامل تفاصيله فور الفتح، والمنتقي يبقى متاحاً من تبويب الخلفيات.
  }

  /** Round 52: هل صف الأرقام ظاهر في الكيبورد الحقيقي الآن؟ المعاينة تطابقه. */
  private boolean realNumberRowVisible() {
    return getSharedPreferences("keyboard_ui", 0).getBoolean("number_row", false);
  }

  /**
   * Round 52: نفس تخطيط الحروف الذي يرسمه الكيبورد الحي — تفضيل المستخدم
   * للمدرسة العربية (arabic_layout) عبر نفس محلل XML، وارتجاع إلى
   * LayoutProvider.arabic() إن تعذر التحميل.
   */
  private KeyboardLayout realKeyboardLayout() {
    try {
      android.content.SharedPreferences ui = getSharedPreferences("keyboard_ui", 0);
      String variant = ui.getString("arabic_layout", "ARABIC_102");
      int xml = KeyboardLayouts.xmlFor(variant, true);
      KeyboardLayout parsed = KeyboardXmlParser.loadLetters(this, xml, true, false, null, "ar");
      if (parsed != null && parsed.rows.size() > 0) return parsed;
    } catch (Exception ignored) {
    }
    return LayoutProvider.arabic();
  }

  private void readTheme(KeyboardTheme theme) {
    background = theme.background;
    key = theme.key;
    text = theme.text;
    sub = theme.sub;
    accent = theme.accent;
    radius = theme.keyRadiusDp;
    mainSize = theme.mainTextSizeDp;
    subSize = theme.subTextSizeDp;
    image = theme.imageUri;
    opacity = theme.keyOpacity;
    fontStyle = theme.fontStyle;
    backgroundDim = theme.backgroundDim;
    surfaceColor = theme.surfaceOverride;
    keyFrame = theme.keyFrame;
    keyFrameUri = theme.keyFrameUri == null ? "" : theme.keyFrameUri;
    keySpaceFrameUri = theme.keySpaceFrameUri == null ? "" : theme.keySpaceFrameUri;
    keyWidthScale = theme.keyWidthScale;
    keyHeightScale = theme.keyHeightScale;
    mainTextPosition =
        theme.mainTextPosition < 0f ? defaultMainTextPosition(theme) : theme.mainTextPosition;
    subTextPosition =
        theme.subTextPosition < 0f ? defaultSubTextPosition(theme) : theme.subTextPosition;
    pressEffect = theme.pressEffect;
    pressEffectUri = theme.pressEffectUri == null ? "" : theme.pressEffectUri;
  }

  private float defaultMainTextPosition(KeyboardTheme theme) {
    return theme != null && theme.keyFrameUri != null && theme.keyFrameUri.length() > 0
        ? .80f
        : .73f;
  }

  private float defaultSubTextPosition(KeyboardTheme theme) {
    return theme != null && theme.keyFrameUri != null && theme.keyFrameUri.length() > 0
        ? .18f
        : .06f;
  }

  private int progressForPosition(float value, float min) {
    return Math.round((value - min) * 100f);
  }

  private String mainPositionLabel() {
    return "موقع الحرف الرئيسي: " + Math.round(mainTextPosition * 100f) + "٪ للأسفل";
  }

  private String subPositionLabel() {
    return "موقع الحرف الصغير: " + Math.round(subTextPosition * 100f) + "٪ من أعلى الزر";
  }

  private void bindViews() {
    name = (EditText) findViewById(R.id.custom_theme_name);
    name.setText(
        editingId == null
            ? "سمة جديدة"
            : editingBuiltin
                ? "نسخة من " + ThemeRepository.name(this, editingId)
                : ThemeRepository.name(this, editingId));
    preview = (SmartKeyboardView) findViewById(R.id.custom_theme_preview);
    preview.setKeyboardLayout(realKeyboardLayout());
    opacityValue = (TextView) findViewById(R.id.key_opacity_value);
    backgroundColorPresets = (LinearLayout) findViewById(R.id.background_color_presets);
    backgroundPresets = (LinearLayout) findViewById(R.id.background_presets);
    // Round 69: إصلاح انهيار واجهة التصميم — fontPresets كان مفقوداً من bindViews
    // منذ إعادة هيكلة الأقسام، فكان الوصول للواجهة ينهار في buildFontPresets.
    fontPresets = (LinearLayout) findViewById(R.id.font_presets);
    textColorPresets = (LinearLayout) findViewById(R.id.text_color_presets);
    subColorPresets = (LinearLayout) findViewById(R.id.sub_color_presets);
    buttonSectionsStrip = (LinearLayout) findViewById(R.id.button_sections_strip);
    designerBgSectionsStrip = (LinearLayout) findViewById(R.id.designer_bg_sections_strip);
    designerBgGrid = (GridLayout) findViewById(R.id.designer_bg_grid);
    autoThemeGrid = (GridLayout) findViewById(R.id.auto_theme_grid);
    framePresets = (GridLayout) findViewById(R.id.button_elements_grid);
    // Round 59/68: لستة واحدة — نتائج الإنترنت داخل شبكة عناصر القسم نفسها
    onlineFrameQueries = framePresets;
    onlineFrameGrid = framePresets;
    pressFxPresets = (GridLayout) findViewById(R.id.pressfx_presets);
    pressFxQueries = pressFxPresets;
    pressFxOnlineGrid = pressFxPresets;
    autoSection = findViewById(R.id.section_auto);
    backgroundSection = findViewById(R.id.section_background);
    keysSection = findViewById(R.id.section_keys_fixed); // الشفافية + الأقسام + العناصر
    fontSection = findViewById(R.id.section_font);
    autoTab = (ImageButton) findViewById(R.id.tab_auto);
    backgroundTab = (ImageButton) findViewById(R.id.tab_background);
    keysTab = (ImageButton) findViewById(R.id.tab_keys);
    fontTab = (ImageButton) findViewById(R.id.tab_font);
  }

  private void configureHeader() {
    findViewById(R.id.custom_theme_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });
    findViewById(R.id.custom_theme_save)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                save();
              }
            });
  }

  private void configureTabs() {
    // Round 68: أربعة أقسام فقط — الخلفيات، Auto (التفاعلات)، الأزرار، الخط
    View.OnClickListener backgroundAction =
        new View.OnClickListener() {
          public void onClick(View view) {
            showSection(0);
          }
        };
    View.OnClickListener autoAction =
        new View.OnClickListener() {
          public void onClick(View view) {
            showSection(1);
          }
        };
    View.OnClickListener keysAction =
        new View.OnClickListener() {
          public void onClick(View view) {
            showSection(2);
          }
        };
    View.OnClickListener fontAction =
        new View.OnClickListener() {
          public void onClick(View view) {
            showSection(3);
          }
        };
    backgroundTab.setOnClickListener(backgroundAction);
    autoTab.setOnClickListener(autoAction);
    keysTab.setOnClickListener(keysAction);
    fontTab.setOnClickListener(fontAction);
    findViewById(R.id.tab_background_container).setOnClickListener(backgroundAction);
    findViewById(R.id.tab_auto_container).setOnClickListener(autoAction);
    findViewById(R.id.tab_keys_container).setOnClickListener(keysAction);
    findViewById(R.id.tab_font_container).setOnClickListener(fontAction);
  }

  /**
   * Round 68: أربعة أقسام حصراً — الخلفيات(0) تجمع اللون والصورة والمتحرك
   * وخلفيات المكتبة؛ Auto(1) هو التفاعلات؛ الأزرار(2) بشريط شفافية وشريط
   * أقسام ثابتين أعلى الشبكة؛ الخط(3) العائلة والأحجام وألوان الحروف.
   */
  private void showSection(int section) {
    backgroundSection.setVisibility(section == 0 ? View.VISIBLE : View.GONE);
    autoSection.setVisibility(section == 1 ? View.VISIBLE : View.GONE);
    keysSection.setVisibility(section == 2 ? View.VISIBLE : View.GONE);
    fontSection.setVisibility(section == 3 ? View.VISIBLE : View.GONE);
    // Round 53: الشريط ثابت يملأ العرض — التبويبات متساوية بلا أي تمرير
    styleTab(backgroundTab, findViewById(R.id.tab_background_container), 0, section == 0);
    styleTab(autoTab, findViewById(R.id.tab_auto_container), 1, section == 1);
    styleTab(keysTab, findViewById(R.id.tab_keys_container), 2, section == 2);
    styleTab(fontTab, findViewById(R.id.tab_font_container), 3, section == 3);
    // Round 68/73: جلب كسول مع تعافٍ دائم. إن فشل الجلب أو رجع المستخدم
    // إلى القسم بعد حالة فارغة نعيد الطلب تلقائياً، حتى تظهر الثيمات/الأزرار
    // في كل دخول ولا يعلق القسم على نتيجة قديمة.
    if (section == 0 && (!designerBgLoaded || (designerBgSets == null && !isLoading(designerBgStatus)))) {
      designerBgLoaded = true;
      loadDesignerBgSets();
    }
    // تفاعلات الضغط عند أول فتح لقسم Auto + إعادة محاولة بعد الفشل.
    if (section == 1 && (!pressFxLoaded || (pressFxSets == null && !isLoading(pressFxStatus)))) {
      pressFxLoaded = true;
      buildPressFxPresets();
      loadPressFxSets();
    }
    // أقسام الأزرار عند أول فتح لقسم الأزرار + إعادة محاولة بعد الفشل.
    if (section == 2 && (!buttonsLoaded || (frameSets == null && !isLoading(frameStatus)))) {
      buttonsLoaded = true;
      loadFrameSets();
    }
  }

  /**
   * Round 53/68: أيقونات التبويبات صور ويب أنيقة — المختارة زرقاء وغير
   * المختارة رمادية، بلا colorFilter يشوّه الصورة.
   * الترتيب: الخلفيات، Auto (التفاعلات)، الأزرار، الخط.
   */
  private static final int[][] TAB_ICONS = {
    {R.drawable.custom_skin_tab_sliding_unselect, R.drawable.custom_skin_tab_sliding_select},
    {R.drawable.icon_diy_automatic, R.drawable.icon_diy_automatic_hover},
    {R.drawable.custom_skin_tab_button_unselect, R.drawable.custom_skin_tab_button_select},
    {R.drawable.custom_skin_tab_font_unselect, R.drawable.custom_skin_tab_font_select},
  };

  private boolean isLoading(String status) {
    return status != null && status.indexOf("جار") >= 0;
  }

  private void styleTab(ImageButton tab, View container, int index, boolean selected) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(selected ? 0xffe8f3ff : Color.WHITE);
    drawable.setCornerRadius(dp(12));
    if (selected) drawable.setStroke(dp(1), 0xff169bea);
    container.setBackground(drawable);
    tab.setImageResource(TAB_ICONS[index][selected ? 1 : 0]);
    tab.setColorFilter(null);
    if (container instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) container;
      for (int child = 0; child < group.getChildCount(); child++) {
        View view = group.getChildAt(child);
        if (view instanceof TextView)
          ((TextView) view).setTextColor(selected ? 0xff087fc5 : 0xff555b65);
      }
    }
  }

  private void configureSliders() {
    // Round 50/68: شريط شفافية الزر — أعلى قسم الأزرار، ثابت لا يتحرك مع
    // الشبكة. صفر = زر معتم، والحد الأعلى 72٪ شفافية (المدى التاريخي معكوساً).
    final SeekBar opacityBar = (SeekBar) findViewById(R.id.key_opacity);
    opacityBar.setProgress(Math.round((1f - opacity) * 100f));
    opacityBar.setOnSeekBarChangeListener(
        new SimpleSeekListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            opacity = 1f - progress / 100f;
            sessionSkin = ""; // الجلد يرسم بعتامة كاملة — الشفافية تحتاج وجهاً برمجياً
            opacityValue.setText("شفافية الزر المختار: " + progress + "٪");
            updatePreview();
          }

          public void onStopTrackingTouch(SeekBar bar) {
            persistNow(); // Round 68: تطبيق فوري عند إفلات الشريط
          }
        });

    final TextView dimValue = (TextView) findViewById(R.id.background_dim_value);
    SeekBar dimBar = (SeekBar) findViewById(R.id.background_dim);
    dimBar.setMax(160);
    dimBar.setProgress(Math.round(backgroundDim * 100f) + 80);
    dimBar.setOnSeekBarChangeListener(
        new SimpleSeekListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            backgroundDim = (progress - 80) / 100f;
            String label =
                backgroundDim == 0f
                    ? "تعتيم/إضاءة الخلفية: بدون"
                    : backgroundDim < 0f
                        ? "تعتيم الخلفية: " + Math.round(-backgroundDim * 100) + "٪"
                        : "إضاءة الخلفية: " + Math.round(backgroundDim * 100) + "٪";
            dimValue.setText(label);
            updatePreview();
          }

          public void onStopTrackingTouch(SeekBar bar) {
            persistNow(); // Round 68: تطبيق فوري عند إفلات الشريط
          }
        });

    final TextView mainSizeValue = (TextView) findViewById(R.id.main_font_size_value);
    final TextView subSizeValue = (TextView) findViewById(R.id.sub_font_size_value);
    SeekBar mainSizeBar = (SeekBar) findViewById(R.id.main_font_size);
    mainSizeBar.setProgress(Math.round(mainSize - 17f));
    mainSizeBar.setOnSeekBarChangeListener(
        new SimpleSeekListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            mainSize = 17f + progress;
            mainSizeValue.setText("حجم الحرف الرئيسي: " + Math.round(mainSize) + "dp");
            updatePreview();
          }

          public void onStopTrackingTouch(SeekBar bar) {
            persistNow(); // Round 68: تطبيق فوري عند إفلات الشريط
          }
        });
    SeekBar subSizeBar = (SeekBar) findViewById(R.id.sub_font_size);
    subSizeBar.setProgress(Math.round(subSize - 10f));
    subSizeBar.setOnSeekBarChangeListener(
        new SimpleSeekListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            subSize = 10f + progress;
            subSizeValue.setText("حجم الحرف الصغير: " + Math.round(subSize) + "dp");
            updatePreview();
          }

          public void onStopTrackingTouch(SeekBar bar) {
            persistNow(); // Round 68: تطبيق فوري عند إفلات الشريط
          }
        });

    // Round 45: تحكم مستقل في موضع الحرف الرئيسي والحرف الصغير داخل الزر.
    final TextView mainPositionValue = (TextView) findViewById(R.id.main_text_position_value);
    SeekBar mainPositionBar = (SeekBar) findViewById(R.id.main_text_position);
    mainPositionBar.setMax(Math.round((MAIN_TEXT_POS_MAX - MAIN_TEXT_POS_MIN) * 100f));
    mainPositionBar.setProgress(progressForPosition(mainTextPosition, MAIN_TEXT_POS_MIN));
    mainPositionBar.setOnSeekBarChangeListener(
        new SimpleSeekListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            mainTextPosition = MAIN_TEXT_POS_MIN + progress / 100f;
            mainPositionValue.setText(mainPositionLabel());
            updatePreview();
          }

          public void onStopTrackingTouch(SeekBar bar) {
            persistNow();
          }
        });

    final TextView subPositionValue = (TextView) findViewById(R.id.sub_text_position_value);
    SeekBar subPositionBar = (SeekBar) findViewById(R.id.sub_text_position);
    subPositionBar.setMax(Math.round((SUB_TEXT_POS_MAX - SUB_TEXT_POS_MIN) * 100f));
    subPositionBar.setProgress(progressForPosition(subTextPosition, SUB_TEXT_POS_MIN));
    subPositionBar.setOnSeekBarChangeListener(
        new SimpleSeekListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            subTextPosition = SUB_TEXT_POS_MIN + progress / 100f;
            subPositionValue.setText(subPositionLabel());
            updatePreview();
          }

          public void onStopTrackingTouch(SeekBar bar) {
            persistNow();
          }
        });
    mainPositionValue.setText(mainPositionLabel());
    subPositionValue.setText(subPositionLabel());

    // Round 69: شريطا عرض/ارتفاع الزر — حجم الزر المرسوم داخل خانة المفتاح
    // (40٪..100٪ من الخانة). يطبق فورياً على المعاينة والكيبورد الحي.
    final TextView keyWidthValue = (TextView) findViewById(R.id.key_width_value);
    SeekBar keyWidthBar = (SeekBar) findViewById(R.id.key_width);
    keyWidthBar.setProgress(Math.round(keyWidthScale * 100f) - 40);
    keyWidthBar.setOnSeekBarChangeListener(
        new SimpleSeekListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            keyWidthScale = (progress + 40) / 100f;
            keyWidthValue.setText("عرض الزر: " + Math.round(keyWidthScale * 100) + "٪");
            updatePreview();
          }

          public void onStopTrackingTouch(SeekBar bar) {
            persistNow(); // تطبيق فوري عند إفلات الشريط
          }
        });
    keyWidthValue.setText("عرض الزر: " + Math.round(keyWidthScale * 100) + "٪");

    final TextView keyHeightValue = (TextView) findViewById(R.id.key_height_value);
    SeekBar keyHeightBar = (SeekBar) findViewById(R.id.key_height);
    keyHeightBar.setProgress(Math.round(keyHeightScale * 100f) - 40);
    keyHeightBar.setOnSeekBarChangeListener(
        new SimpleSeekListener() {
          public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            keyHeightScale = (progress + 40) / 100f;
            keyHeightValue.setText("ارتفاع الزر: " + Math.round(keyHeightScale * 100) + "٪");
            updatePreview();
          }

          public void onStopTrackingTouch(SeekBar bar) {
            persistNow(); // تطبيق فوري عند إفلات الشريط
          }
        });
    keyHeightValue.setText("ارتفاع الزر: " + Math.round(keyHeightScale * 100) + "٪");

    mainSizeValue.setText("حجم الحرف الرئيسي: " + Math.round(mainSize) + "dp");
    subSizeValue.setText("حجم الحرف الصغير: " + Math.round(subSize) + "dp");
    opacityValue.setText("شفافية الزر المختار: " + Math.round((1f - opacity) * 100f) + "٪");
  }

  private void openBackgroundPicker() {
    Intent intent = new Intent(this, CustomBackgroundActivity.class);
    intent.putExtra(CustomBackgroundActivity.EXTRA_CURRENT, baseImageSource());
    startActivityForResult(intent, PICK_BACKGROUND);
  }

  private void configureImageActions() {
    findViewById(R.id.theme_add_image)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, PICK_IMAGE);
              }
            });
    findViewById(R.id.theme_remove_image)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                image = "";
                buildBackgroundColorStrip();
                buildBackgroundPresets();
                updatePreview();
                persistNow(); // Round 68: تطبيق فوري
              }
            });
  }

  private void configureDelete() {
    View delete = findViewById(R.id.custom_theme_delete);
    delete.setVisibility(
        editingId != null && editingId.startsWith("custom_") ? View.VISIBLE : View.GONE);
    delete.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            ThemeRepository.delete(CustomThemeActivity.this, editingId);
            if (editingId.equals(new Prefs(CustomThemeActivity.this).theme())) {
              // Round 41-3: الخلفيات أُخليت من القائمة — الاحتياطي ثيم مدرج
              new Prefs(CustomThemeActivity.this).setTheme("white_pure");
            }
            finish();
          }
        });
  }

  private void rebuildControls() {
    buildAutoThemes();
    buildBackgroundColorStrip();
    buildBackgroundPresets();
    buildFontPresets();
    buildTextColorStrip();
    buildSubColorStrip();
  }

  private void buildAutoThemes() {
    autoThemeGrid.removeAllViews();
    final String[] titles = {
      "Default", "Stars", "Planets", "Hearts", "Sparkles", "Snow",
      "Bubbles", "Petals", "Fireflies", "Meteors", "Music", "Fireworks"
    };
    final String[] names = {
      "", "stars", "planets", "hearts", "sparkles", "snow",
      "bubbles", "petals", "fireflies", "meteors", "music", "fireworks"
    };
    final int[] styles = {
      KeyboardTheme.ANIMATION_NONE,
      KeyboardTheme.ANIMATION_STARS,
      KeyboardTheme.ANIMATION_PLANETS,
      KeyboardTheme.ANIMATION_HEARTS,
      KeyboardTheme.ANIMATION_SPARKLES,
      KeyboardTheme.ANIMATION_SNOW,
      KeyboardTheme.ANIMATION_BUBBLES,
      KeyboardTheme.ANIMATION_PETALS,
      KeyboardTheme.ANIMATION_FIREFLIES,
      KeyboardTheme.ANIMATION_SPEED,
      KeyboardTheme.ANIMATION_MUSIC,
      KeyboardTheme.ANIMATION_FIREWORKS
    };
    int selectedStyle = current().animationStyle;
    int width = (getResources().getDisplayMetrics().widthPixels - dp(44)) / 3;
    for (int index = 0; index < titles.length; index++) {
      final String effectName = names[index];
      final int effectStyle = styles[index];
      // Round 48: البطاقة معاينة كيبورد حقيقية مصغرة بثيم الجلسة الحالي،
      // وجسيمات التأثير فوقها، وتسمية صغيرة تحتها — لا خلفية متدرجة
      // يظهر عليها النص وحده.
      final LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.VERTICAL);
      GradientDrawable border = new GradientDrawable();
      border.setColor(Color.WHITE);
      border.setCornerRadius(dp(11));
      boolean selected = selectedStyle == effectStyle;
      border.setStroke(dp(selected ? 3 : 1), selected ? 0xffffc400 : 0xffd8dce3);
      card.setBackground(border);
      card.setPadding(dp(3), dp(3), dp(3), dp(1));
      final FrameLayout art = new FrameLayout(this);
      String source = baseImageSource();
      String effectImage =
          effectStyle == KeyboardTheme.ANIMATION_NONE
              ? source
              : "animated:" + effectName + ":" + source;
      ThemeThumbnailView thumb = new ThemeThumbnailView(this);
      thumb.setTheme(currentForImage(effectImage));
      art.addView(
          thumb,
          new FrameLayout.LayoutParams(
              FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
      ThemeDecorationThumbnailView sample = new ThemeDecorationThumbnailView(this);
      sample.setOverlayOnly(true);
      sample.setStyle(effectStyle);
      art.addView(
          sample,
          new FrameLayout.LayoutParams(
              FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
      if (selected) {
        TextView check = new TextView(this);
        check.setText("✓");
        check.setTextColor(0xff202124);
        check.setTextSize(13);
        check.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xffffd51a);
        check.setBackground(circle);
        FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(22), dp(22));
        badge.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        badge.setMargins(0, 0, dp(3), dp(3));
        art.addView(check, badge);
      }
      LinearLayout.LayoutParams artParams =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      artParams.height = 0;
      artParams.weight = 1f;
      card.addView(art, artParams);
      TextView label = new TextView(this);
      label.setText(titles[index]);
      label.setTextColor(0xff343840);
      label.setTextSize(11);
      label.setGravity(Gravity.CENTER);
      card.addView(
          label,
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, dp(19)));
      card.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              String picked = baseImageSource();
              image =
                  effectStyle == KeyboardTheme.ANIMATION_NONE
                      ? picked
                      : "animated:" + effectName + ":" + picked;
              buildAutoThemes();
              updatePreview();
              persistNow(); // Round 59: تطبيق فوري على الكيبورد الحي كاملاً
            }
          });
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = width;
      params.height = dp(88);
      params.setMargins(dp(4), dp(4), dp(4), dp(4));
      autoThemeGrid.addView(card, params);
    }
  }

  private String baseImageSource() {
    if (!image.startsWith("animated:")) return image;
    String value = image.substring(9);
    int separator = value.indexOf(':');
    return separator < 0 ? "" : value.substring(separator + 1);
  }

  private void buildBackgroundPresets() {
    backgroundPresets.removeAllViews();
    for (int index = 0; index < BACKGROUND_NAMES.length; index++) {
      final String source = BACKGROUND_NAMES[index];
      final FrameLayout frame = new FrameLayout(this);
      GradientDrawable border = new GradientDrawable();
      border.setColor(Color.WHITE);
      border.setCornerRadius(dp(12));
      border.setStroke(
          dp(image.equals(source) ? 3 : 1), image.equals(source) ? 0xff149fe8 : 0xffd7dbe2);
      frame.setBackground(border);
      frame.setPadding(dp(3), dp(3), dp(3), dp(3));
      ImageView sample = new ImageView(this);
      sample.setImageResource(BACKGROUND_RESOURCES[index]);
      sample.setScaleType(ImageView.ScaleType.CENTER_CROP);
      frame.addView(sample, new FrameLayout.LayoutParams(dp(92), dp(78)));
      frame.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              image = source;
              buildBackgroundPresets();
              updatePreview();
              persistNow(); // Round 59: تطبيق فوري
            }
          });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(100), dp(86));
      params.setMargins(dp(4), 0, dp(4), 0);
      backgroundPresets.addView(frame, params);
    }
  }

  /**
   * Round 68: شريط ألوان الخلفية — نقرة تجعل الخلفية لوناً صافياً وتسقط
   * الصورة، وتُطبَّق فوراً على الكيبورد الحي.
   */
  private void buildBackgroundColorStrip() {
    backgroundColorPresets.removeAllViews();
    for (int index = 0; index < KEY_COLORS.length; index++) {
      final int color = KEY_COLORS[index];
      boolean selected = image.length() == 0 && background == color;
      TextView chip = new TextView(this);
      chip.setGravity(Gravity.CENTER);
      chip.setText(selected ? "✓" : "");
      chip.setTextSize(18);
      chip.setTextColor(contentColor(color));
      GradientDrawable drawable = new GradientDrawable();
      drawable.setShape(GradientDrawable.OVAL);
      drawable.setColor(color);
      drawable.setStroke(dp(selected ? 3 : 1), selected ? 0xff149fe8 : 0xffb9bec6);
      chip.setBackground(drawable);
      chip.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              background = color;
              image = ""; // اللون الصافي يسقط الصورة
              buildBackgroundColorStrip();
              buildBackgroundPresets();
              updatePreview();
              persistNow(); // تطبيق فوري
            }
          });
      backgroundColorPresets.addView(chip, chipParams());
    }
  }

  /** Round 68: لون الخط في المفاتيح — شريط ألوان مباشر يطبّق فوراً. */
  private void buildTextColorStrip() {
    textColorPresets.removeAllViews();
    for (int index = 0; index < KEY_COLORS.length; index++) {
      final int color = KEY_COLORS[index];
      boolean selected = text == color;
      TextView chip = new TextView(this);
      chip.setGravity(Gravity.CENTER);
      chip.setText(selected ? "✓" : "");
      chip.setTextSize(18);
      chip.setTextColor(contentColor(color));
      GradientDrawable drawable = new GradientDrawable();
      drawable.setShape(GradientDrawable.OVAL);
      drawable.setColor(color);
      drawable.setStroke(dp(selected ? 3 : 1), selected ? 0xff149fe8 : 0xffb9bec6);
      chip.setBackground(drawable);
      chip.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              text = color;
              buildTextColorStrip();
              updatePreview();
              persistNow(); // تطبيق فوري
            }
          });
      textColorPresets.addView(chip, chipParams());
    }
  }

  /** Round 68: لون الخط في الحروف الصغيرة — شريط ألوان مباشر يطبّق فوراً. */
  private void buildSubColorStrip() {
    subColorPresets.removeAllViews();
    for (int index = 0; index < KEY_COLORS.length; index++) {
      final int color = KEY_COLORS[index];
      boolean selected = sub == color;
      TextView chip = new TextView(this);
      chip.setGravity(Gravity.CENTER);
      chip.setText(selected ? "✓" : "");
      chip.setTextSize(18);
      chip.setTextColor(contentColor(color));
      GradientDrawable drawable = new GradientDrawable();
      drawable.setShape(GradientDrawable.OVAL);
      drawable.setColor(color);
      drawable.setStroke(dp(selected ? 3 : 1), selected ? 0xff149fe8 : 0xffb9bec6);
      chip.setBackground(drawable);
      chip.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              sub = color;
              buildSubColorStrip();
              updatePreview();
              persistNow(); // تطبيق فوري
            }
          });
      subColorPresets.addView(chip, chipParams());
    }
  }

  /**
   * Round 50/68: قسم «الأشكال» في لستة عناصر الأزرار — بطاقات بيضاء داخل
   * كل واحدة مخطط الزر (keyshape_*) بشكله وحرف «ع» فوقه؛ الاختيار يعيد
   * رسم عناصر القسم بالشكل الجديد ديناميكياً ويطبَّق فوراً.
   */
  private void renderShapeCards(int width) {
    for (int index = 0; index < SHAPE_RADII.length; index++) {
      final float value = SHAPE_RADII[index];
      boolean selected = Math.abs(radius - value) < .6f;
      final LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.VERTICAL);
      GradientDrawable panel = new GradientDrawable();
      panel.setColor(Color.WHITE);
      panel.setCornerRadius(dp(12));
      panel.setStroke(dp(selected ? 3 : 1), selected ? 0xffffc400 : 0xffd8dce3);
      card.setBackground(panel);
      card.setContentDescription(SHAPE_NAMES[index]);
      final FrameLayout art = new FrameLayout(this);
      ImageView shape = new ImageView(this);
      int artId =
          getResources()
              .getIdentifier(
                  KeyFrames.shapeName(value), "drawable", getPackageName());
      if (artId != 0) shape.setImageResource(artId);
      shape.setScaleType(ImageView.ScaleType.FIT_CENTER);
      FrameLayout.LayoutParams artParams = new FrameLayout.LayoutParams(dp(40), dp(56));
      artParams.gravity = Gravity.CENTER;
      art.addView(shape, artParams);
      // الحرف فوق صورة الشكل — TextView يرسم العربية بخط الجهاز
      TextView letter = new TextView(this);
      letter.setText("ع");
      letter.setTextSize(14);
      letter.setGravity(Gravity.CENTER);
      letter.setTextColor(0xff202124);
      FrameLayout.LayoutParams letterParams = new FrameLayout.LayoutParams(dp(40), dp(56));
      letterParams.gravity = Gravity.CENTER;
      art.addView(letter, letterParams);
      LinearLayout.LayoutParams artLayout =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT);
      artLayout.height = 0;
      artLayout.weight = 1f;
      card.addView(art, artLayout);
      TextView label = new TextView(this);
      label.setText(SHAPE_NAMES[index]);
      label.setTextColor(0xff343840);
      label.setTextSize(10);
      label.setGravity(Gravity.CENTER);
      card.addView(
          label,
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));
      card.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              radius = value;
              sessionSkin = ""; // تغيير شكل الزر يسقط الجلد
              renderButtonElements(); // الإطارات صور تتبع الشكل المختار
              updatePreview();
              persistNow(); // Round 68: تطبيق فوري
            }
          });
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = width;
      params.height = frameCardHeight(width); // بطاقة طولية كبطاقات الإطارات
      params.setMargins(dp(4), dp(4), dp(4), dp(4));
      framePresets.addView(card, params);
    }
  }

  /**
   * Round 68: لستة عناصر قسم الأزرار — ترسم عناصر القسم المختار من شريط
   * الأقسام الثابت أعلاها: الأشكال، أو الإطارات المدمجة، أو نتائج قسم
   * من مكتبة ثيماتي (المحفوظ أولاً). تُمسح كلياً قبل كل رسم فلا بقايا
   * لأي تحميل سابق.
   */
  private void renderButtonElements() {
    framePresets.removeAllViews();
    int width = (getResources().getDisplayMetrics().widthPixels - dp(24) - dp(30)) / 4;
    styleButtonChips();
    if ("shapes".equals(buttonsSection)) {
      renderShapeCards(width);
      return;
    }
    if ("builtin".equals(buttonsSection)) {
      renderBuiltinFrames(width);
      return;
    }
    if (frameStatus != null) {
      framePresets.addView(onlineFrameNote(frameStatus, frameRetry));
      return;
    }
    renderFrameResults(width);
  }

  /** Round 68: شريط أقسام الأزرار الأفقي الثابت — الأشكال + المدمج + أقسام ثيماتي. */
  private void buildButtonStrip() {
    buttonSectionsStrip.removeAllViews(); // لا بقايا لتحميل سابق
    buttonChipIds.clear();
    buttonChips.clear();
    addButtonStripChip("الأشكال", "shapes");
    addButtonStripChip("المدمج", "builtin");
    if (frameSets != null) {
      for (int index = 0; index < frameSets.size(); index++) {
        addButtonStripChip(frameSets.get(index).title, frameSets.get(index).id);
      }
    } else if (frameReload) {
      addButtonStripChip("↻ إعادة تحميل المكتبة", "__retry");
    }
    styleButtonChips();
  }

  private void addButtonStripChip(String title, final String id) {
    TextView chip = new TextView(this);
    chip.setText(title);
    chip.setTextSize(12);
    chip.setGravity(Gravity.CENTER);
    chip.setPadding(dp(12), 0, dp(12), 0);
    chip.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            if ("__retry".equals(id)) {
              loadFrameSets();
              return;
            }
            selectButtonSection(id);
          }
        });
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
    params.setMargins(dp(4), 0, dp(4), 0);
    buttonSectionsStrip.addView(chip, params);
    buttonChipIds.add(id);
    buttonChips.add(chip);
  }

  private void styleButtonChips() {
    for (int index = 0; index < buttonChips.size(); index++) {
      boolean selected = buttonsSection.equals(buttonChipIds.get(index));
      TextView chip = buttonChips.get(index);
      chip.setTextColor(selected ? Color.WHITE : 0xff37424e);
      GradientDrawable drawable = new GradientDrawable();
      drawable.setCornerRadius(dp(17));
      drawable.setColor(selected ? 0xff2e73db : 0xfff1f3f7);
      drawable.setStroke(dp(selected ? 2 : 1), selected ? 0xff2e73db : 0xffd8dce3);
      chip.setBackground(drawable);
    }
  }

  private void selectButtonSection(String id) {
    if ("shapes".equals(id) || "builtin".equals(id)) {
      buttonsSection = id;
      renderButtonElements();
      return;
    }
    showThematyFrames(id);
  }

  /**
   * Round 50: عشرة إطارات احترافية — كل إطار صورة حقيقية (keyframe_ID_R.png)
   * تُعرض بنسخة الشكل المختار حالياً، فتتبدل البطاقات كلها ديناميكياً بين
   * دائري/مربع/خفيف/كبسولة... والنقر يطبق الإطار على أزرار المعاينة والكيبورد.
   */
  /**
   * Round 69: ارتفاع بطاقة الإطارات — بطاقة أفقية صغيرة أنيقة كزر حقيقي
   * (نسبة المفتاح العريضة h=w×0.63) + سطر تسمية قصير، لا صورة طويلة.
   */
  private int frameCardHeight(int width) {
    return Math.round(width * KeyArtProcessor.KEY_ASPECT) + dp(18);
  }

  /** Round 62/68: الإطارات المدمجة — أزرار طولية 4 في الصف كالمفاتيح الحقيقية. */
  private void renderBuiltinFrames(int width) {
    for (int index = 0; index < KeyFrames.COUNT; index++) {
      final int frameId = index + 1;
      boolean selected = keyFrame == frameId;
      final LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.VERTICAL);
      GradientDrawable border = new GradientDrawable();
      border.setColor(Color.WHITE);
      border.setCornerRadius(dp(12));
      border.setStroke(dp(selected ? 3 : 1), selected ? 0xffffc400 : 0xffd8dce3);
      card.setBackground(border);
      card.setPadding(dp(3), dp(3), dp(3), dp(1));
      card.setContentDescription(KeyFrames.NAMES[index]);
      final FrameLayout art = new FrameLayout(this);
      final ImageView image = new ImageView(this);
      int artId =
          getResources()
              .getIdentifier(
                  KeyFrames.artName(frameId, radius), "drawable", getPackageName());
      if (artId != 0) image.setImageResource(artId);
      // Round 69: الفن بشكله الحقيقي متمركزاً — بطاقة أفقية أنيقة كزر حقيقي
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      art.addView(
          image,
          new FrameLayout.LayoutParams(
              FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
      if (selected) {
        TextView check = new TextView(this);
        check.setText("✓");
        check.setTextColor(0xff202124);
        check.setTextSize(12);
        check.setGravity(Gravity.CENTER);
        GradientDrawable badge = new GradientDrawable();
        badge.setShape(GradientDrawable.OVAL);
        badge.setColor(0xffffd51a);
        check.setBackground(badge);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(20), dp(20));
        badgeParams.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        badgeParams.setMargins(0, 0, dp(2), dp(2));
        art.addView(check, badgeParams);
      }
      LinearLayout.LayoutParams artParams =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      artParams.height = 0;
      artParams.weight = 1f;
      card.addView(art, artParams);
      TextView label = new TextView(this);
      label.setText(KeyFrames.NAMES[index]);
      label.setTextColor(0xff343840);
      label.setTextSize(10);
      label.setGravity(Gravity.CENTER);
      card.addView(
          label,
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));
      card.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              keyFrame = frameId;
              keyFrameUri = ""; // الإطار المدمج يسقط إطار الإنترنت
              keySpaceFrameUri = "";
              sessionSkin = ""; // الإطار صورة — يسقط الجلد كي يظهر على الأزرار
              // Round 69: اختيار الزر يلوّن كل الواجهات — لون الخط يصبح لون
              // حروف الزر نفسه (اقتراحات وأدوات ولوحات) كالمفاتيح تماماً.
              text = KeyFrames.textColor(frameId);
              sub = blend(text, key, .38f);
              buildTextColorStrip();
              buildSubColorStrip();
              renderButtonElements();
              updatePreview();
              persistNow(); // Round 59: تطبيق فوري على الكيبورد الحي كاملاً
            }
          });
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = width;
      params.height = frameCardHeight(width); // Round 62: زر طولي
      params.setMargins(dp(4), dp(4), dp(4), dp(4));
      framePresets.addView(card, params);
    }
  }

  /** Round 59: فاصل نصي بعرض اللستة يفصل المدمج عن تحميلات الإنترنت. */
  private void addListHeader(GridLayout grid, String title, int columns) {
    TextView label = new TextView(this);
    label.setText(title);
    label.setTextSize(13);
    label.setTextColor(0xff5f6a7d);
    label.setGravity(Gravity.CENTER);
    label.setPadding(dp(4), dp(10), dp(4), dp(6));
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    // Round 66: مانع انهيار GridLayout — الامتداد لا يتجاوز أعمدة الشبكة الفعلية أبداً
    columns = Math.min(columns, grid.getColumnCount());
    if (columns < 1) { columns = 1; }
    params.columnSpec = android.widget.GridLayout.spec(0, columns);
    params.width = GridLayout.LayoutParams.MATCH_PARENT;
    grid.addView(label, params);
  }

  /**
   * Round 67: أقسام أزرار مكتبة ثيماتي — تُقرأ من الأرشيف عن بُعد بطلبات
   * النطاق الجزئية (بلا تنزيل الأرشيف كاملاً وبلا تضخيم حجم التطبيق).
   */
  /** Round 59/68: نتائج قسم أزرار المكتبة داخل لستة العناصر — المحفوظ أولاً. */
  private void renderFrameResults(int width) {
    java.util.List<OnlineAssetStore.Item> merged =
        new java.util.ArrayList<OnlineAssetStore.Item>();
    java.util.List<OnlineAssetStore.Item> saved =
        OnlineAssetStore.saved(this, OnlineAssetStore.TYPE_FRAME);
    for (int index = 0; index < saved.size(); index++) merged.add(saved.get(index));
    if (onlineFrameResults != null) {
      for (int index = 0; index < onlineFrameResults.size(); index++) {
        OnlineAssetStore.Item item = onlineFrameResults.get(index);
        if (OnlineAssetStore.savedItem(this, OnlineAssetStore.TYPE_FRAME, item.id) == null)
          merged.add(item);
      }
    }
    if (merged.isEmpty()) {
      framePresets.addView(onlineFrameNote("لا عناصر في هذا القسم", null));
      return;
    }
    for (int index = 0; index < merged.size(); index++) {
      addOnlineFrameCard(merged.get(index), width);
    }
  }

  private void loadFrameSets() {
    frameStatus = "جارٍ تحميل مكتبة ثيماتي…";
    frameRetry = null;
    frameReload = true;
    buildButtonStrip(); // رقاقة إعادة المحاولة تحل محل الأقسام مؤقتاً
    ThematyStore.sets(
        OnlineAssetStore.TYPE_FRAME,
        new ThematyStore.SetsListener() {
          public void onReady(
              java.util.List<ThematyStore.SetInfo> sets, String error) {
            if (sets == null || sets.isEmpty()) {
              frameStatus =
                  "تعذر الوصول لمكتبة ثيماتي ("
                      + OnlineAssetStore.shortError(error)
                      + ")";
              frameRetry = null;
              frameReload = true;
              if (!"shapes".equals(buttonsSection) && !"builtin".equals(buttonsSection))
                buttonsSection = "builtin"; // لا يبقى المستخدم على قسم فارغ
              buildButtonStrip();
              renderButtonElements();
              return;
            }
            frameSets = sets;
            frameReload = false;
            buildButtonStrip();
            if ("shapes".equals(buttonsSection) || "builtin".equals(buttonsSection)) {
              renderButtonElements(); // تحديث حالة رقائق الشريط فقط
            } else {
              showThematyFrames(buttonsSection); // إعادة جلب القسم المفتوح بعد الإصلاح
            }
          }
        });
  }

  // ------------------------------------------------------------ Round 56: تفاعلات الضغط

  /** بطاقات التصاميم المدمجة العشرة + «بدون» — تُبنى فور فتح قسم تلقائي. */
  /** Round 59: لستة التفاعلات الموحدة — المدمجة ثم عناوين الإنترنت ونتائجه بشبكة واحدة 3 في الصف. */
  private void buildPressFxPresets() {
    pressFxPresets.removeAllViews();
    int width = (getResources().getDisplayMetrics().widthPixels - dp(24) - dp(16)) / 3;
    pressFxPresets.addView(pressFxNoneCard(width));
    for (int index = 0; index < PressEffects.COUNT; index++) {
      final int effectId = index + 1;
      boolean selected = pressEffect == effectId && pressEffectUri.length() == 0;
      final LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.VERTICAL);
      GradientDrawable border = new GradientDrawable();
      border.setColor(Color.WHITE);
      border.setCornerRadius(dp(12));
      border.setStroke(dp(selected ? 3 : 1), selected ? 0xffffc400 : 0xffd8dce3);
      card.setBackground(border);
      card.setPadding(dp(3), dp(3), dp(3), dp(1));
      card.setContentDescription(PressEffects.NAMES[index]);
      final FrameLayout art = new FrameLayout(this);
      final ImageView image = new ImageView(this);
      int artId =
          getResources()
              .getIdentifier(
                  PressEffects.artName(effectId), "drawable", getPackageName());
      if (artId != 0) image.setImageResource(artId);
      image.setScaleType(ImageView.ScaleType.FIT_CENTER);
      art.addView(
          image,
          new FrameLayout.LayoutParams(
              FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
      LinearLayout.LayoutParams artParams =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      artParams.height = 0;
      artParams.weight = 1f;
      card.addView(art, artParams);
      TextView label = new TextView(this);
      label.setText(PressEffects.NAMES[index]);
      label.setTextColor(0xff343840);
      label.setTextSize(10);
      label.setGravity(Gravity.CENTER);
      card.addView(
          label,
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));
      card.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              pressEffect = effectId;
              pressEffectUri = ""; // المدمج يسقط تصميم الإنترنت
              buildPressFxPresets();
              updatePreview();
              preview.demoPressEffect(); // عرض فوري فوق المعاينة
              persistNow(); // Round 59: تطبيق فوري على الكيبورد الحي كاملاً
            }
          });
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = width;
      params.height = dp(84);
      params.setMargins(dp(4), dp(4), dp(4), dp(4));
      pressFxPresets.addView(card, params);
    }
    // Round 59: جزء الإنترنت داخل اللستة نفسها — فاصل ثم العناوين ثم الحالة/النتائج
    addListHeader(pressFxPresets, "تفاعلات من مكتبة ثيماتي", 3);
    buildPressFxSetCards(width);
    if (pressFxStatus != null) {
      pressFxPresets.addView(pressFxNote(pressFxStatus, pressFxRetry));
    } else {
      renderPressFxResults(width);
    }
  }

  /** Round 67: بطاقات أقسام تفاعلات ثيماتي — خلايا داخل اللستة الموحدة نفسها. */
  private void buildPressFxSetCards(int width) {
    for (int index = 0; index < pressFxSetCount(); index++) {
      final String setId = pressFxSetId(index);
      boolean chosen = pressFxSet.equals(setId);
      final LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.VERTICAL);
      GradientDrawable border = new GradientDrawable();
      border.setColor(chosen ? 0xffffe9a8 : Color.WHITE);
      border.setCornerRadius(dp(12));
      border.setStroke(dp(chosen ? 3 : 1), chosen ? 0xffffc400 : 0xffd8dce3);
      card.setBackground(border);
      card.setGravity(Gravity.CENTER);
      card.setContentDescription(pressFxSetTitle(index));
      TextView title = new TextView(this);
      title.setText(pressFxSetTitle(index));
      title.setTextSize(12);
      title.setTextColor(chosen ? 0xff8a5a00 : 0xff37424e);
      title.setGravity(Gravity.CENTER);
      title.setMaxLines(2);
      card.addView(
          title,
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT));
      card.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              showThematyPressFx(setId);
            }
          });
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = width;
      params.height = dp(72);
      params.setMargins(dp(4), dp(4), dp(4), dp(4));
      pressFxPresets.addView(card, params);
    }
  }

  /** Round 59: نتائج التفاعلات الأونلاين داخل اللستة الموحدة. */
  private void renderPressFxResults(int width) {
    java.util.List<OnlineAssetStore.Item> merged =
        new java.util.ArrayList<OnlineAssetStore.Item>();
    java.util.List<OnlineAssetStore.Item> saved =
        OnlineAssetStore.saved(this, OnlineAssetStore.TYPE_EFFECT);
    for (int index = 0; index < saved.size(); index++) merged.add(saved.get(index));
    if (pressFxResults != null) {
      for (int index = 0; index < pressFxResults.size(); index++) {
        OnlineAssetStore.Item item = pressFxResults.get(index);
        if (OnlineAssetStore.savedItem(this, OnlineAssetStore.TYPE_EFFECT, item.id) == null)
          merged.add(item);
      }
    }
    if (merged.isEmpty()) {
      pressFxPresets.addView(pressFxNote("لا عناصر في هذا القسم", null));
      return;
    }
    for (int index = 0; index < merged.size(); index++) {
      addPressFxCard(merged.get(index), width);
    }
  }

  /** بطاقة إيقاف التفاعلات. */
  private View pressFxNoneCard(int width) {
    final boolean selected = pressEffect == 0 && pressEffectUri.length() == 0;
    final LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable border = new GradientDrawable();
    border.setColor(Color.WHITE);
    border.setCornerRadius(dp(12));
    border.setStroke(dp(selected ? 3 : 1), selected ? 0xffffc400 : 0xffd8dce3);
    card.setBackground(border);
    card.setPadding(dp(3), dp(3), dp(3), dp(1));
    card.setContentDescription("بدون تفاعل");
    final FrameLayout art = new FrameLayout(this);
    TextView none = new TextView(this);
    none.setText("✕");
    none.setTextSize(22);
    none.setTextColor(0xff9aa3ad);
    none.setGravity(Gravity.CENTER);
    art.addView(
        none,
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    LinearLayout.LayoutParams artParams =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    artParams.height = 0;
    artParams.weight = 1f;
    card.addView(art, artParams);
    TextView label = new TextView(this);
    label.setText("بدون");
    label.setTextColor(0xff343840);
    label.setTextSize(10);
    label.setGravity(Gravity.CENTER);
    card.addView(
        label,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(16)));
    card.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            pressEffect = 0;
            pressEffectUri = "";
            buildPressFxPresets();
            updatePreview();
            persistNow(); // Round 59: تطبيق فوري
          }
        });
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    params.width = width;
    params.height = dp(84);
    params.setMargins(dp(4), dp(4), dp(4), dp(4));
    return card;
  }

  /** Round 67: أقسام تفاعلات مكتبة ثيماتي (تاغات الأيقونات). */
  private int pressFxSetCount() {
    return pressFxSets == null ? 0 : pressFxSets.size();
  }

  private String pressFxSetTitle(int index) {
    return pressFxSets.get(index).title;
  }

  private String pressFxSetId(int index) {
    return pressFxSets.get(index).id;
  }

  /** Round 67: أقسام تفاعلات مكتبة ثيماتي — تاغات الأيقونات من الأرشيف عن بُعد. */
  private void loadPressFxSets() {
    pressFxStatus = "جارٍ تحميل مكتبة ثيماتي…";
    pressFxRetry = null;
    pressFxReload = true;
    buildPressFxPresets();
    ThematyStore.sets(
        OnlineAssetStore.TYPE_EFFECT,
        new ThematyStore.SetsListener() {
          public void onReady(
              java.util.List<ThematyStore.SetInfo> sets, String error) {
            if (sets == null || sets.isEmpty()) {
              pressFxStatus =
                  "تعذر الوصول لمكتبة ثيماتي (" + OnlineAssetStore.shortError(error)
                      + ") — انقر للإعادة";
              pressFxRetry = null;
              pressFxReload = true;
              buildPressFxPresets();
              return;
            }
            pressFxSets = sets;
            pressFxReload = false;
            showThematyPressFx(sets.get(0).id);
          }
        });
  }

  /** Round 57: عناوين التفاعلات بطاقات بنفس تصميم التصاميم الافتراضية — لا شريط أفقي. */
  /** Round 59: اللستة الموحدة — العناوين تُرسم داخل شبكة التفاعلات نفسها. */
  private void buildPressFxQueries() {
    buildPressFxPresets();
  }

  /** Round 67: تفاعلات قسم من مكتبة ثيماتي — المحفوظ أولاً ثم عناصر القسم. */
  private void showThematyPressFx(final String setId) {
    pressFxSet = setId;
    pressFxStatus = "جارٍ جلب التفاعلات…";
    pressFxRetry = null;
    pressFxReload = false;
    pressFxResults = null;
    buildPressFxQueries();
    ThematyStore.items(
        OnlineAssetStore.TYPE_EFFECT,
        setId,
        new OnlineAssetStore.Listener() {
          public void onReady(
              java.util.List<OnlineAssetStore.Item> items, String error) {
            if (!pressFxSet.equals(setId)) return; // قسم أحدث حلّ محله
            if (error != null || items == null) {
              pressFxStatus =
                  "تعذر الوصول لمكتبة ثيماتي (" + OnlineAssetStore.shortError(error)
                      + ") — انقر للإعادة";
              pressFxRetry = setId;
            } else {
              pressFxResults = items;
              pressFxStatus = null;
              pressFxRetry = null;
            }
            renderPressFx();
          }
        });
  }

  /** بطاقة نص لحالة تفاعلات الشبكة — اختيارية النقر لإعادة المحاولة. */
  private TextView pressFxNote(String message, final String retryQuery) {
    TextView note = new TextView(this);
    note.setText(message);
    note.setTextSize(13);
    note.setTextColor(0xff37424e);
    note.setGravity(Gravity.CENTER);
    note.setPadding(dp(8), dp(12), dp(8), dp(12));
    if (retryQuery != null) {
      note.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              showThematyPressFx(retryQuery);
            }
          });
    } else if (pressFxReload) {
      note.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              loadPressFxSets();
            }
          });
    }
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    params.columnSpec = android.widget.GridLayout.spec(0, 3); // Round 59: عرض اللستة
    params.width = GridLayout.LayoutParams.MATCH_PARENT;
    params.setMargins(dp(4), dp(8), dp(4), dp(8));
    note.setLayoutParams(params);
    return note;
  }

  /** شبكة تصاميم التفاعل الأونلاين — المحفوظ أولاً ثم نتائج البحث. */
  /** Round 59: اللستة الموحدة — النتائج تُرسم داخل شبكة التفاعلات نفسها. */
  private void renderPressFx() {
    buildPressFxPresets();
  }

  /** بطاقة تفاعل أونلاين: مصغرة + تسمية كالبطاقات الافتراضية + شارة + نقرة تحمّل ثم تطبّق. */
  private void addPressFxCard(final OnlineAssetStore.Item item, int width) {
    final boolean stored = item.path.length() > 0;
    final boolean applied = stored && item.path.equals(pressEffectUri);
    final LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable border = new GradientDrawable();
    border.setColor(Color.WHITE);
    border.setCornerRadius(dp(12));
    border.setStroke(dp(applied ? 3 : 1), applied ? 0xffffc400 : 0xffd8dce3);
    card.setBackground(border);
    card.setPadding(dp(3), dp(3), dp(3), dp(1));
    card.setContentDescription(item.title);
    final FrameLayout art = new FrameLayout(this);
    final ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    art.addView(
        image,
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    ThematyStore.preview(
        item,
        480,
        new OnlineAssetStore.BitmapListener() {
          public void onReady(Bitmap picture) {
            if (picture != null) image.setImageBitmap(picture);
          }
        });
    // Round 63: شارة الحالة — ↓ أزرق يحتاج تحميلاً، ✓ أخضر محمَّل، ✓ كهرماني مطبَّق
    art.addView(
        OnlineAssetStore.statusBadge(this, stored, applied, 0xffffd51a, 0xff202124, dp(18)));
    LinearLayout.LayoutParams artParams =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    artParams.height = 0;
    artParams.weight = 1f;
    card.addView(art, artParams);
    TextView label = new TextView(this);
    label.setText(item.title);
    label.setTextColor(0xff343840);
    label.setTextSize(9);
    label.setGravity(Gravity.CENTER);
    label.setMaxLines(1);
    card.addView(
        label,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));
    card.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            OnlineAssetStore.Item local =
                OnlineAssetStore.savedItem(
                    CustomThemeActivity.this, OnlineAssetStore.TYPE_EFFECT, item.id);
            if (local != null) {
              applyPressFx(local.path);
              return;
            }
            // Round 63: لا تحميلاً مزدوجاً — حلقة قائمة تعني تحميلاً جارياً
            for (int child = 0; child < art.getChildCount(); child++) {
              if (art.getChildAt(child) instanceof android.widget.ProgressBar) return;
            }
            final android.widget.ProgressBar ring =
                OnlineAssetStore.loadingRing(CustomThemeActivity.this);
            FrameLayout.LayoutParams ringParams =
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
            art.addView(ring, ringParams);
            Toast.makeText(
                    CustomThemeActivity.this, "جارٍ جلب التفاعل من المكتبة…", Toast.LENGTH_SHORT)
                .show();
            ThematyStore.install(
                CustomThemeActivity.this,
                OnlineAssetStore.TYPE_EFFECT,
                item,
                new OnlineAssetStore.DownloadListener() {
                  public void onReady(String path, String error) {
                    art.removeView(ring); // Round 63: إيقاف الحلقة فور انتهاء التحميل
                    if (error != null || path == null) {
                      Toast.makeText(
                              CustomThemeActivity.this,
                              "تعذر الجلب من المكتبة — تحقق من الاتصال",
                              Toast.LENGTH_SHORT)
                          .show();
                      return;
                    }
                    applyPressFx(path);
                  }
                });
          }
        });
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    params.width = width; // Round 59: خلية اللستة الموحدة (عرض/3) — أصغر وأكثر إحكاماً
    params.height = dp(88); // كارتفاع بطاقات التصاميم الافتراضية
    params.setMargins(dp(4), dp(4), dp(4), dp(4));
    pressFxOnlineGrid.addView(card, params);
  }

  /** تطبيق تفاعل محمّل — أولوية على المدمج، مع عرض تجريبي فوري. */
  private void applyPressFx(String path) {
    pressEffectUri = path;
    pressEffect = 0;
    buildPressFxPresets();
    updatePreview();
    preview.demoPressEffect();
    persistNow(); // Round 59: تطبيق فوري على الكيبورد الحي كاملاً
    Toast.makeText(this, "طُبِّق تفاعل الضغط — جرّب الضغط على المعاينة", Toast.LENGTH_SHORT)
        .show();
  }

  /** Round 67: أزرار قسم من مكتبة ثيماتي — المحفوظ أولاً ثم عناصر القسم. */
  private void showThematyFrames(final String setId) {
    onlineFrameSet = setId;
    buttonsSection = setId;
    frameStatus = "جارٍ جلب أزرار القسم…";
    frameRetry = null;
    onlineFrameResults = null;
    renderButtonElements();
    ThematyStore.items(
        OnlineAssetStore.TYPE_FRAME,
        setId,
        new OnlineAssetStore.Listener() {
          public void onReady(
              java.util.List<OnlineAssetStore.Item> items, String error) {
            if (!onlineFrameSet.equals(setId)) return; // قسم أحدث حلّ محله
            if (error != null || items == null) {
              frameStatus =
                  "تعذر الوصول لمكتبة ثيماتي (" + OnlineAssetStore.shortError(error)
                      + ") — انقر للإعادة";
              frameRetry = setId;
            } else {
              onlineFrameResults = items;
              frameStatus = null;
              frameRetry = null;
            }
            renderButtonElements();
          }
        });
  }

  /** بطاقة نص بحالة الشبكة — اختيارية النقر لإعادة المحاولة. */
  private TextView onlineFrameNote(final String message, final String retryQuery) {
    TextView note = new TextView(this);
    note.setText(message);
    note.setTextSize(13);
    note.setTextColor(0xff37424e);
    note.setGravity(Gravity.CENTER);
    note.setPadding(dp(8), dp(12), dp(8), dp(12));
    if (retryQuery != null) {
      note.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              showThematyFrames(retryQuery);
            }
          });
    } else if (frameReload) {
      note.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              loadFrameSets();
            }
          });
    }
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    // Round 66: امتداد الملاحظة مشتق من أعمدة الشبكة نفسها — لا رقم صلب يخالف الـXML
    int span = Math.min(4, framePresets.getColumnCount());
    if (span < 1) { span = 1; }
    params.columnSpec = android.widget.GridLayout.spec(0, span); // Round 62: لستة الإطارات 4 أعمدة
    params.width = GridLayout.LayoutParams.MATCH_PARENT;
    params.setMargins(dp(4), dp(8), dp(4), dp(8));
    note.setLayoutParams(params);
    return note;
  }

  // ------------------------------------------------------------ Round 68: خلفيات المكتبة

  /** Round 68: أقسام خلفيات مكتبة ثيماتي داخل قسم الخلفيات — رقائق أفقية ثم بطاقات. */
  private void loadDesignerBgSets() {
    designerBgStatus = "جارٍ تحميل مكتبة ثيماتي…";
    designerBgRetry = null;
    renderDesignerBg();
    ThematyStore.sets(
        OnlineAssetStore.TYPE_BACKGROUND,
        new ThematyStore.SetsListener() {
          public void onReady(
              java.util.List<ThematyStore.SetInfo> sets, String error) {
            if (sets == null || sets.isEmpty()) {
              designerBgSets = null;
              designerBgStatus =
                  "تعذر الوصول لمكتبة ثيماتي ("
                      + OnlineAssetStore.shortError(error)
                      + ")";
              designerBgRetry = "__reload";
              buildDesignerBgStrip();
              renderDesignerBg();
              return;
            }
            designerBgSets = sets;
            designerBgStatus = null;
            buildDesignerBgStrip();
            showDesignerBg(sets.get(0).id);
          }
        });
  }

  private void buildDesignerBgStrip() {
    designerBgSectionsStrip.removeAllViews(); // لا بقايا لتحميل سابق
    designerBgChipIds.clear();
    designerBgChips.clear();
    if (designerBgSets != null) {
      for (int index = 0; index < designerBgSets.size(); index++) {
        addDesignerBgChip(designerBgSets.get(index).title, designerBgSets.get(index).id);
      }
    } else {
      addDesignerBgChip("↻ إعادة تحميل المكتبة", "__reload");
    }
    styleDesignerBgChips();
  }

  private void addDesignerBgChip(String title, final String id) {
    TextView chip = new TextView(this);
    chip.setText(title);
    chip.setTextSize(12);
    chip.setGravity(Gravity.CENTER);
    chip.setPadding(dp(12), 0, dp(12), 0);
    chip.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            if ("__reload".equals(id)) loadDesignerBgSets();
            else showDesignerBg(id);
          }
        });
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
    params.setMargins(dp(4), 0, dp(4), 0);
    designerBgSectionsStrip.addView(chip, params);
    designerBgChipIds.add(id);
    designerBgChips.add(chip);
  }

  private void styleDesignerBgChips() {
    for (int index = 0; index < designerBgChips.size(); index++) {
      boolean selected = designerBgSection.equals(designerBgChipIds.get(index));
      TextView chip = designerBgChips.get(index);
      chip.setTextColor(selected ? Color.WHITE : 0xff37424e);
      GradientDrawable drawable = new GradientDrawable();
      drawable.setCornerRadius(dp(17));
      drawable.setColor(selected ? 0xff2e73db : 0xfff1f3f7);
      drawable.setStroke(dp(selected ? 2 : 1), selected ? 0xff2e73db : 0xffd8dce3);
      chip.setBackground(drawable);
    }
  }

  /** يعرض خلفيات القسم المختار من رقائق الأقسام — المحفوظ أولاً. */
  private void showDesignerBg(final String setId) {
    designerBgSection = setId;
    designerBgStatus = "جارٍ جلب خلفيات القسم…";
    designerBgRetry = null;
    designerBgResults = null;
    renderDesignerBg();
    ThematyStore.items(
        OnlineAssetStore.TYPE_BACKGROUND,
        setId,
        new OnlineAssetStore.Listener() {
          public void onReady(
              java.util.List<OnlineAssetStore.Item> items, String error) {
            if (!designerBgSection.equals(setId)) return; // قسم أحدث حلّ محله — لا بقايا
            if (error != null || items == null) {
              designerBgStatus =
                  "تعذر جلب خلفيات القسم ("
                      + OnlineAssetStore.shortError(error)
                      + ") — انقر للإعادة";
              designerBgRetry = setId;
            } else {
              designerBgResults = items;
              designerBgStatus = null;
              designerBgRetry = null;
            }
            renderDesignerBg();
          }
        });
  }

  private void renderDesignerBg() {
    designerBgGrid.removeAllViews(); // لا بقايا لتحميل سابق
    if (designerBgStatus != null) {
      designerBgGrid.addView(designerBgNote(designerBgStatus, designerBgRetry));
      return;
    }
    java.util.List<OnlineAssetStore.Item> merged =
        new java.util.ArrayList<OnlineAssetStore.Item>();
    java.util.List<OnlineAssetStore.Item> saved =
        OnlineAssetStore.saved(this, OnlineAssetStore.TYPE_BACKGROUND);
    for (int index = 0; index < saved.size(); index++) merged.add(saved.get(index));
    if (designerBgResults != null) {
      for (int index = 0; index < designerBgResults.size(); index++) {
        OnlineAssetStore.Item item = designerBgResults.get(index);
        if (OnlineAssetStore.savedItem(this, OnlineAssetStore.TYPE_BACKGROUND, item.id) == null)
          merged.add(item);
      }
    }
    if (merged.isEmpty()) {
      designerBgGrid.addView(designerBgNote("لا عناصر في هذا القسم", null));
      return;
    }
    int width = (getResources().getDisplayMetrics().widthPixels - dp(24) - dp(24)) / 3;
    for (int index = 0; index < merged.size(); index++) {
      addDesignerBgCard(merged.get(index), width);
    }
  }

  /** بطاقة نص بحالة الشبكة — اختيارية النقر لإعادة المحاولة. */
  private TextView designerBgNote(final String message, final String retryId) {
    TextView note = new TextView(this);
    note.setText(message);
    note.setTextSize(13);
    note.setTextColor(0xff37424e);
    note.setGravity(Gravity.CENTER);
    note.setPadding(dp(8), dp(12), dp(8), dp(12));
    if (retryId != null) {
      note.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              if ("__reload".equals(retryId)) loadDesignerBgSets();
              else showDesignerBg(retryId);
            }
          });
    }
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    // امتداد الملاحظة مشتق من أعمدة الشبكة نفسها — لا رقم صلب يخالف الـXML
    int span = Math.min(3, designerBgGrid.getColumnCount());
    if (span < 1) span = 1;
    params.columnSpec = android.widget.GridLayout.spec(0, span);
    params.width = GridLayout.LayoutParams.MATCH_PARENT;
    params.setMargins(dp(4), dp(8), dp(4), dp(8));
    note.setLayoutParams(params);
    return note;
  }

  /**
   * بطاقة خلفية مكتبة: معاينة فورية (الكاش المتوازي) + شارة تثبيت + نقرة
   * تُجلبها عند اللزوم ثم تطبّقها على الكيبورد الحي فوراً.
   */
  private void addDesignerBgCard(final OnlineAssetStore.Item item, int width) {
    final boolean stored = item.path.length() > 0;
    final boolean applied = stored && ("file://" + item.path).equals(image);
    final FrameLayout card = new FrameLayout(this);
    GradientDrawable border = new GradientDrawable();
    border.setColor(Color.WHITE);
    border.setCornerRadius(dp(12));
    border.setStroke(dp(applied ? 3 : 1), applied ? 0xff149fe8 : 0xffd8dce3);
    card.setBackground(border);
    card.setPadding(dp(3), dp(3), dp(3), dp(3));
    card.setContentDescription(item.title);
    final ImageView preview = new ImageView(this);
    preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
    ThematyStore.preview(
        item,
        360,
        new OnlineAssetStore.BitmapListener() {
          public void onReady(Bitmap picture) {
            if (picture != null && !isFinishing()) preview.setImageBitmap(picture);
          }
        });
    card.addView(
        preview,
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    card.addView(
        OnlineAssetStore.statusBadge(this, stored, applied, 0xff2e73db, Color.WHITE, dp(22)));
    card.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            if (stored) {
              applyDesignerBackground(item.path);
              return;
            }
            final ProgressBar ring = OnlineAssetStore.loadingRing(CustomThemeActivity.this);
            FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dp(30), dp(30));
            ringParams.gravity = Gravity.CENTER;
            card.addView(ring, ringParams);
            ThematyStore.install(
                CustomThemeActivity.this,
                OnlineAssetStore.TYPE_BACKGROUND,
                item,
                new OnlineAssetStore.DownloadListener() {
                  public void onReady(String path, String error) {
                    if (isFinishing()) return;
                    card.removeView(ring);
                    if (path != null) applyDesignerBackground(path);
                    else
                      Toast.makeText(
                              CustomThemeActivity.this,
                              "تعذر جلب الخلفية — تحقق من الاتصال",
                              Toast.LENGTH_SHORT)
                          .show();
                  }
                });
          }
        });
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    params.width = width;
    params.height = Math.round(width * 0.75f);
    params.setMargins(dp(4), dp(4), dp(4), dp(4));
    designerBgGrid.addView(card, params);
  }

  private void applyDesignerBackground(String path) {
    image = "file://" + path;
    buildBackgroundColorStrip();
    buildBackgroundPresets();
    buildAutoThemes();
    updatePreview();
    persistNow(); // Round 68: تطبيق فوري على الكيبورد الحي
    Toast.makeText(this, "طُبِّقت الخلفية", Toast.LENGTH_SHORT).show();
  }

  /** بطاقة إطار أونلاين: مصغرة + تسمية كالبطاقات الافتراضية + شارة + نقرة تحمّل ثم تطبّق. */
  private void addOnlineFrameCard(final OnlineAssetStore.Item item, int width) {
    final boolean stored = item.path.length() > 0;
    final boolean applied =
        stored && item.path.equals(keyFrameUri) && item.spacePath.equals(keySpaceFrameUri);
    final LinearLayout card = new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable border = new GradientDrawable();
    border.setColor(Color.WHITE);
    border.setCornerRadius(dp(12));
    border.setStroke(dp(applied ? 3 : 1), applied ? 0xffffc400 : 0xffd8dce3);
    card.setBackground(border);
    card.setPadding(dp(3), dp(3), dp(3), dp(1));
    card.setContentDescription(item.title);
    final FrameLayout art = new FrameLayout(this);
    final ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.FIT_CENTER); // Round 69: الزر بشكله الحقيقي داخل بطاقة أفقية أنيقة
    art.addView(
        image,
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    ThematyStore.preview(
        item,
        480,
        new OnlineAssetStore.BitmapListener() {
          public void onReady(Bitmap picture) {
            if (picture != null)
              image.setImageBitmap(KeyArtProcessor.shapeButton(picture)); // Round 62: زر حقيقي
          }
        });
    // Round 63: شارة الحالة — ↓ أزرق يحتاج تحميلاً، ✓ أخضر محمَّل، ✓ كهرماني مطبَّق
    art.addView(
        OnlineAssetStore.statusBadge(this, stored, applied, 0xffffd51a, 0xff202124, dp(18)));
    LinearLayout.LayoutParams artParams =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    artParams.height = 0;
    artParams.weight = 1f;
    card.addView(art, artParams);
    TextView label = new TextView(this);
    label.setText(item.title);
    label.setTextColor(0xff343840);
    label.setTextSize(9);
    label.setGravity(Gravity.CENTER);
    label.setMaxLines(1);
    card.addView(
        label,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));
    card.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            OnlineAssetStore.Item local =
                OnlineAssetStore.savedItem(
                    CustomThemeActivity.this, OnlineAssetStore.TYPE_FRAME, item.id);
            if (local != null) {
              applyOnlineFrame(local.path, local.spacePath);
              return;
            }
            // Round 63: لا تحميلاً مزدوجاً — حلقة قائمة تعني تحميلاً جارياً
            for (int child = 0; child < art.getChildCount(); child++) {
              if (art.getChildAt(child) instanceof android.widget.ProgressBar) return;
            }
            final android.widget.ProgressBar ring =
                OnlineAssetStore.loadingRing(CustomThemeActivity.this);
            FrameLayout.LayoutParams ringParams =
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
            art.addView(ring, ringParams);
            Toast.makeText(
                    CustomThemeActivity.this, "جارٍ جلب الزر من المكتبة…", Toast.LENGTH_SHORT)
                .show();
            ThematyStore.install(
                CustomThemeActivity.this,
                OnlineAssetStore.TYPE_FRAME,
                item,
                new OnlineAssetStore.DownloadListener() {
                  public void onReady(String path, String error) {
                    art.removeView(ring); // Round 63: إيقاف الحلقة فور انتهاء التحميل
                    if (error != null || path == null) {
                      Toast.makeText(
                              CustomThemeActivity.this,
                              "تعذر الجلب من المكتبة — تحقق من الاتصال",
                              Toast.LENGTH_SHORT)
                          .show();
                      return;
                    }
                    OnlineAssetStore.Item saved =
                        OnlineAssetStore.savedItem(
                            CustomThemeActivity.this, OnlineAssetStore.TYPE_FRAME, item.id);
                    applyOnlineFrame(path, saved == null ? "" : saved.spacePath);
                  }
                });
          }
        });
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    params.width = width; // Round 59: خلية اللستة الموحدة (عرض/3) — أصغر وأكثر إحكاماً
    params.height = frameCardHeight(width); // Round 62: زر طولي
    params.setMargins(dp(4), dp(4), dp(4), dp(4));
    onlineFrameGrid.addView(card, params);
  }

  /** تطبيق إطار محمَّل — أولوية على الإطار المدمج، ويسقط الجلد كي يظهر. */
  private void applyOnlineFrame(String path, String spacePath) {
    keyFrameUri = path == null ? "" : path;
    keySpaceFrameUri = spacePath == null ? "" : spacePath;
    keyFrame = 0;
    sessionSkin = "";
    // Round 69: اختيار الزر يطبّق صورته وألوانه على كل العناصر — لون الخط
    // يصبح لون حروف الزر نفسه، فتتطابق الاقتراحات وشريط الأدوات واللوحات
    // مع المفاتيح لحظة الاختيار.
    Bitmap art = KeyArtProcessor.fileKeyArt(path);
    if (art != null) {
      text = KeyArtProcessor.frameTextColor(art);
      sub = blend(text, key, .38f);
    }
    buildTextColorStrip();
    buildSubColorStrip();
    renderButtonElements();
    updatePreview();
    persistNow(); // Round 59: تطبيق فوري على الكيبورد الحي كاملاً
    Toast.makeText(this, "طُبِّق زر مكتبة ثيماتي — وتحديث مباشر للكيبورد", Toast.LENGTH_SHORT)
        .show();
  }

  // ------------------------------------------------------------ الخط

  /** عائلات الخط الخمس — عينة «ع» بخط العائلة نفسه. */
  private void buildFontPresets() {
    fontPresets.removeAllViews();
    for (int index = 0; index < 5; index++) {
      final int style = index;
      TextView sample = new TextView(this);
      sample.setText("ع");
      sample.setTextSize(30);
      sample.setGravity(Gravity.CENTER);
      sample.setTextColor(fontStyle == style ? 0xff087fc5 : 0xff3c4654);
      sample.setTypeface(Typeface.create(fontFamily(style), Typeface.NORMAL));
      GradientDrawable drawable = new GradientDrawable();
      drawable.setColor(fontStyle == style ? 0xffe8f3ff : Color.WHITE);
      drawable.setCornerRadius(dp(28));
      drawable.setStroke(
          dp(fontStyle == style ? 3 : 1), fontStyle == style ? 0xff149fe8 : 0xffd3d8df);
      sample.setBackground(drawable);
      sample.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              fontStyle = style;
              buildFontPresets();
              updatePreview();
              persistNow(); // Round 68: تطبيق فوري
            }
          });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(64), dp(64));
      params.setMargins(dp(6), 0, dp(6), 0);
      fontPresets.addView(sample, params);
    }
  }

  private String fontFamily(int style) {
    if (style == 1) return "serif";
    if (style == 2) return "sans-serif-condensed";
    if (style == 3) return "monospace";
    if (style == 4) return "sans-serif-light";
    return "sans";
  }

  private LinearLayout.LayoutParams chipParams() {
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
    params.setMargins(dp(5), 0, dp(5), 0);
    return params;
  }

  private int pressed() {
    return blend(key, background, .25f);
  }

  private KeyboardTheme current() {
    return currentForImage(image);
  }

  /** Round 48: نفس الجلسة بصورة بديلة — تبني بطاقات Auto بمعاينة كل تأثير. */
  private KeyboardTheme currentForImage(String overrideImage) {
    return new KeyboardTheme(
        background,
        key,
        pressed(),
        text,
        sub,
        accent,
        radius,
        mainSize,
        subSize,
        overrideImage,
        opacity,
        fontStyle,
        backgroundDim,
        surfaceColor)
        .withKeyFrame(keyFrame)
        .withKeyFrameUris(keyFrameUri, keySpaceFrameUri)
        .withKeySize(keyWidthScale, keyHeightScale)
        .withTextPositions(mainTextPosition, subTextPosition)
        .withPressEffect(pressEffect)
        .withPressEffectUri(pressEffectUri)
        .withKeySkin(sessionSkin);
  }

  /** Round 52: جلد المسطرة أولاً وإلا صورة الخلفية — دروابل جديدة لكل واجهة. */
  private android.graphics.drawable.Drawable previewBarArt(KeyboardTheme value) {
    android.graphics.drawable.Drawable skin =
        com.almlk.swiftkey.theme.ThemeSkinBar.forTheme(this, value);
    if (skin != null) return skin;
    return com.almlk.swiftkey.theme.ThemeSurfacePaint.forTheme(this, value);
  }

  private void updatePreview() {
    KeyboardTheme value = current();
    // Round 69: لون حروف موحّد لكل عناصر المعاينة — لون حروف الزر المفعّل
    final int chipTextColor = com.almlk.swiftkey.theme.ThemeChipArt.textColor(this, value);
    if (preview != null) preview.setTheme(value);
    // Round 48: المعاينة تحمل تفاصيل الثيم كلها — فن المسطرة على الشريطين
    // (إن كان للثيم جلد) بدل لون السطح وحده.
    // Round 52: وبلا جلد تُرسم صورة الخلفية نفسها على الشريطين — المعاينة
    // مطابقة للواجهة الحقيقية في كل حالة (جلد > صورة > لون سطح)، بدروابل
    // مستقلة لكل شريط لأن bounds خاصة بكل واجهة.
    View suggestionPreview = findViewById(R.id.custom_preview_suggestions);
    if (suggestionPreview != null) {
      android.graphics.drawable.Drawable rowArt = previewBarArt(value);
      if (rowArt != null) suggestionPreview.setBackgroundDrawable(rowArt);
      else suggestionPreview.setBackgroundColor(value.surface);
      // Round 69: عند تفعيل إطار زر تحمل شارات المعاينة فن الزر نفسه (قصّاً
      // متمركزاً بزوايا مدورة) ولون حروفها لون حروف الزر — مطابقة كاملة
      // للواجهة الحية، وبلا إطار تبقى رقاقة الزر الكلاسيكية (صيغة round47).
      boolean frameActive =
          (keyFrameUri.length() > 0 && KeyArtProcessor.fileKeyArt(keyFrameUri) != null)
              || keyFrame > 0;
      if (suggestionPreview instanceof ViewGroup) {
        ViewGroup group = (ViewGroup) suggestionPreview;
        for (int index = 0; index < group.getChildCount(); index++) {
          View child = group.getChildAt(index);
          if (!(child instanceof TextView)) continue;
          ((TextView) child).setTextColor(chipTextColor);
          if (frameActive) {
            // نسخة مستقلة لكل شارة — دروابل واحد لا يُشارك بين واجهات
            child.setBackground(
                com.almlk.swiftkey.theme.ThemeChipArt.forTheme(this, value));
            continue;
          }
          // Round 48: الرقاقة زر كيبورد كامل (صيغة round47) لا لون مسطح
          GradientDrawable chip =
              new GradientDrawable(
                  GradientDrawable.Orientation.TOP_BOTTOM,
                  new int[] {
                    blend(key, Color.WHITE, .34f),
                    key,
                    key,
                    blend(key, Color.BLACK, .22f),
                  });
          chip.setStroke(dp(1), value.sub & 0x88ffffff);
          // Round 52: الرقاقة تتبع شكل الزر الحقيقي (نفس صيغة الواجهة الحية)
          chip.setCornerRadius(dp((int) Math.min(14f, value.keyRadiusDp)));
          child.setBackground(chip);
        }
      }
    }
    View toolbar = findViewById(R.id.custom_preview_toolbar);
    if (toolbar != null) {
      if (toolbar instanceof ThemeDecorationBarLayout) {
        ((ThemeDecorationBarLayout) toolbar).setTheme(value);
        // Round 48: جلد المسطرة فوق شريط الأدوات كالواجهة الحقيقية
        android.graphics.drawable.Drawable barArt = previewBarArt(value);
        if (barArt != null) toolbar.setBackgroundDrawable(barArt);
      } else {
        toolbar.setBackgroundColor(value.surface);
      }
      if (toolbar instanceof ViewGroup) {
        ViewGroup group = (ViewGroup) toolbar;
        for (int index = 0; index < group.getChildCount(); index++) {
          View child = group.getChildAt(index);
          // Round 48/69: عناصر شريط الأدوات بلون حروف الزر/الاقتراحات نفسه —
          // لون واحد لكل عناصر الواجهة كالحية تماماً.
          if (child instanceof TextView) ((TextView) child).setTextColor(chipTextColor);
          // Round 52: أيقونات الأدوات الحقيقية تتلوّن كالواجهة الحية تماماً
          else if (child instanceof ImageButton)
            ((ImageButton) child).setColorFilter(chipTextColor);
        }
      }
    }
  }

  private void save() {
    persistNow();
    Toast.makeText(this, "تم حفظ وتطبيق السمة", Toast.LENGTH_SHORT).show();
    finish();
  }

  /**
   * Round 59: تطبيق فوري — اختيار صورة أو زر يُحفظ ويصل الكيبورد الحي كاملاً
   * (المفاتيح وشريط الاقتراحات وشريط الأدوات والواجهات) في اللحظة نفسها.
   */
  private void persistNow() {
    String title = name.getText().toString().trim();
    if (title.length() == 0) title = "سمة مخصصة";
    editingId = ThemeRepository.save(this, editingId, title, current());
    new Prefs(this).setTheme(editingId);
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == PICK_BACKGROUND && result == RESULT_OK && data != null) {
      String selected = data.getStringExtra(CustomBackgroundActivity.EXTRA_BACKGROUND);
      if (selected != null && selected.length() > 0) {
        image = selected;
        buildBackgroundColorStrip();
        buildAutoThemes();
        updatePreview();
        persistNow(); // Round 59: تطبيق فوري
      }
      return;
    }
    if (request == PICK_IMAGE && result == RESULT_OK && data != null && data.getData() != null) {
      Uri uri = data.getData();
      try {
        getContentResolver()
            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (Exception ignored) {
      }
      image = uri.toString();
      buildBackgroundColorStrip();
      updatePreview();
      persistNow(); // Round 59: تطبيق فوري
    }
  }

  private int hueOf(int color) {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    return Math.round(hsv[0]);
  }

  private int contentColor(int color) {
    int brightness =
        (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
    return brightness < 145 ? Color.WHITE : 0xff172033;
  }

  private int blend(int first, int second, float amount) {
    float keep = 1f - amount;
    return Color.rgb(
        Math.round(Color.red(first) * keep + Color.red(second) * amount),
        Math.round(Color.green(first) * keep + Color.green(second) * amount),
        Math.round(Color.blue(first) * keep + Color.blue(second) * amount));
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
    public void onStartTrackingTouch(SeekBar bar) {}

    public void onStopTrackingTouch(SeekBar bar) {}
  }
}
