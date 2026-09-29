# -*- coding: utf-8 -*-
"""Round 68 — إعادة تصميم واجهة الثيمات وواجهة التصميم المخصص.

طلب المالك:
«لنعيد تصميم واجهة الثيمات وواجهة التصميم المخصص يجب تنفيذ كل طلب بدقة» ثم:
«لماذا لا يتم جلب الثيمات من المكتبة حيث توجد بيانات الثيمات في ملف
themes.json عليك جلب البيانات منه وترسمها كثيمات تحتاج تنزيل في قسم
الثيمات. ويجب اظهار صور الازرار والتفاعلات والثيمات باسرع وقت ممكن مع
التخلص مع بقايا التحميلات السابقة»

التنفيذ:
1. الاستوديو أربعة أقسام حصراً: الخلفيات(0)، Auto=التفاعلات(1)، الأزرار(2)،
   الخط(3) — بتراكب ثابت لقسم الأزرار (شفافية + شريط أقسام أعلى الشبكة).
2. الخلفيات: شريط ألوان (كلون يسقط الصورة) + صور مدمجة + صورة الجهاز +
   تعتيم + متحركة + خلفيات مكتبة ثيماتي (رقائق أقسام + شبكة).
3. الأزرار: شريط رقائق ثابت (الأشكال/المدمج/أقسام ثيماتي) وعناصر القسم
   المختار تُرسم في الشبكة بتمرير مستقل — كلا الشريطين ثابتان في الأعلى.
4. الخط: العائلة والأحجام + شريطا لون الخط في المفاتيح والحروف الصغيرة.
5. الثيمات في واجهة الثيمات: قسم «كل الثيمات» يقرأ themes.json من أرشيف
   ثيماتي — كل قسم شريط أفقي، والبطاقة مرسومة بألوان الثيم مع شارة «↓»
   (تحتاج تنزيلاً) والنقر يثبّت (زر+خلفية) ثم يطبّق فوراً.
6. السرعة: كاش صور LruCache (16MB) + أربعة عمال متوازيين بدل خيط لكل
   طلب — والكاش يُسلَّم فوراً بلا شبكة عند الإعادة.
7. لا بقايا: كل شبكة تُمسح قبل الرسم، وكل استدعاء متأخر يُتجاهل بجيل أو
   بمطابقة القسم الحالي — فلا نتائج قديمة ولا حلقات معلقة.
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res"
STUDIO = JAVA / "com/almlk/swiftkey/settings/CustomThemeActivity.java"
PICKER_THEMES = JAVA / "com/almlk/swiftkey/settings/ThemeSettingsActivity.java"
THEMATY = JAVA / "com/almlk/swiftkey/settings/ThematyStore.java"
LAYOUT = RES / "layout/activity_custom_theme.xml"
THEMES_XML = RES / "layout/activity_themes.xml"


def read(path):
    return path.read_text(encoding="utf-8")


def method_body(src, signature):
    i = src.index(signature)
    j = src.index("\n  }", i)
    return src[i:j]


def strip_comments(src):
    code = re.sub(r"/\*[\s\S]*?\*/", " ", src)
    return re.sub(r"//[^\n]*", " ", code)


def xml_columns(xml, grid_id):
    i = xml.index('android:id="@+id/%s"' % grid_id)
    m = re.search(r'android:columnCount="(\d+)"', xml[i : i + 400])
    return int(m.group(1))


class StudioFourSectionsRound68(unittest.TestCase):
    """أربعة أقسام حصراً — والشريطان ثابتان أعلى قسم الأزرار."""

    def test_four_tabs_only(self):
        xml = read(LAYOUT)
        for tab in ("background", "auto", "keys", "font"):
            self.assertIn('android:id="@+id/tab_%s_container"' % tab, xml, tab)
        for gone in ("tab_colors", "tab_effects", "tab_sound"):
            self.assertNotIn('android:id="@+id/%s' % gone, xml, gone)
        labels = xml[: xml.index('android:id="@+id/content_scroll"')]
        for label in ("الخلفيات", "Auto", "الأزرار", "الخط"):
            self.assertIn('android:text="%s"' % label, labels, label)
        ET.fromstring(xml)

    def test_show_section_maps_four(self):
        src = read(STUDIO)
        body = method_body(src, "private void showSection(int section) {")
        self.assertIn("section == 0 ? View.VISIBLE", body)
        self.assertIn("section == 1 ? View.VISIBLE", body)
        self.assertIn("section == 2 ? View.VISIBLE", body)
        self.assertIn("section == 3 ? View.VISIBLE", body)
        self.assertNotIn("section == 4", body)
        # جلب كسول لكل قسم مرة واحدة
        self.assertIn("if (section == 0 && !designerBgLoaded)", body)
        self.assertIn("if (section == 1 && !pressFxLoaded)", body)
        self.assertIn("if (section == 2 && !buttonsLoaded)", body)

    def test_buttons_overlay_bars_fixed_on_top(self):
        """شريط الشفافية وشريط الأقسام ثابتان في الأعلى — والشبكة تتمرر تحتهما."""
        xml = read(LAYOUT)
        i = xml.index('android:id="@+id/section_keys_fixed"')
        j = xml.index('android:id="@+id/button_elements_grid"')
        top = xml[i:j]
        self.assertIn('android:id="@+id/key_opacity_value"', top)
        self.assertIn('android:id="@+id/key_opacity"', top)
        self.assertIn('android:id="@+id/button_sections_strip"', top)
        # ترتيب الشريطين: الشفافية ثم الأقسام ثم الشبكة
        self.assertLess(
            top.index('android:id="@+id/key_opacity"'),
            top.index('android:id="@+id/button_sections_strip"'),
        )
        # الشبكة داخل تمرير مستقل يملأ ما تبقى من التراكب
        scroll = xml[xml.index('android:id="@+id/button_elements_scroll"') : j + 200]
        self.assertIn('android:layout_height="0dp"', scroll)
        self.assertIn('android:layout_weight="1"', scroll)
        self.assertIn('android:fillViewport="true"', scroll)
        # المحتوى الرئيسي في FrameLayout واحد والقسمان متراكبان
        self.assertIn('android:id="@+id/content_scroll"', xml)
        self.assertIn("<FrameLayout", xml[: xml.index('android:id="@+id/content_scroll"')])

    def test_content_scroll_carries_three_sections(self):
        xml = read(LAYOUT)
        body = xml[
            xml.index('android:id="@+id/content_scroll"') :
            xml.index('android:id="@+id/section_keys_fixed"')
        ]
        for section in ("section_background", "section_auto", "section_font"):
            self.assertIn('android:id="@+id/%s"' % section, body, section)
        # قسم الأزرار خارج تمرير المحتوى — تراكب ثابت (لا ScrollView داخل ScrollView)
        self.assertNotIn('android:id="@+id/section_keys_fixed"', body)


class BackgroundsSectionRound68(unittest.TestCase):
    """قسم الخلفيات: لون أو صورة — والمتحرك وخلفيات المكتبة داخله."""

    def test_color_strip_sets_color_and_clears_image(self):
        src = read(STUDIO)
        body = method_body(src, "private void buildBackgroundColorStrip() {")
        self.assertIn("background = color;", body)
        self.assertIn('image = ""; // اللون الصافي يسقط الصورة', body)
        self.assertIn("persistNow(); // تطبيق فوري", body)
        self.assertIn("chipParams()", body)
        self.assertIn("image.length() == 0 && background == color", body)

    def test_backgrounds_section_layout(self):
        xml = read(LAYOUT)
        i = xml.index('android:id="@+id/section_background"')
        j = xml.index('android:id="@+id/section_auto"')
        block = xml[i:j]
        for part in (
            "background_color_presets",
            "background_presets",
            "theme_add_image",
            "theme_remove_image",
            "background_dim",
            "auto_theme_grid",  # المتحركة انتقلت هنا من قسم تلقائي
            "designer_bg_sections_strip",
            "designer_bg_grid",
        ):
            self.assertIn('android:id="@+id/%s"' % part, block, part)
        self.assertEqual(3, xml_columns(xml, "designer_bg_grid"))
        self.assertEqual(3, xml_columns(xml, "auto_theme_grid"))
        # شريط الألوان أفقي قابل للتمرير داخل القسم
        hsv = block[: block.index('android:id="@+id/background_color_presets"')]
        self.assertIn("<HorizontalScrollView", hsv)

    def test_designer_backgrounds_flow(self):
        src = read(STUDIO)
        for needle in (
            "private void loadDesignerBgSets()",
            "private void buildDesignerBgStrip()",
            "private void showDesignerBg(final String setId)",
            "private void renderDesignerBg()",
            "private void addDesignerBgCard(",
            "private void applyDesignerBackground(String path)",
        ):
            self.assertIn(needle, src, needle)
        apply = method_body(src, "private void applyDesignerBackground(String path) {")
        self.assertIn('image = "file://" + path;', apply)
        self.assertIn("persistNow();", apply)
        # جلب كسول من قسم الخلفيات فقط
        self.assertIn("loadDesignerBgSets();", method_body(src, "private void showSection(int section) {"))


class ButtonsSectionRound68(unittest.TestCase):
    """شريط الأقسام الثابت يبدّل محتوى الشبكة — أشكال/مدمج/أقسام ثيماتي."""

    def test_strip_builds_all_sections(self):
        src = read(STUDIO)
        body = method_body(src, "private void buildButtonStrip() {")
        self.assertIn('addButtonStripChip("الأشكال", "shapes");', body)
        self.assertIn('addButtonStripChip("المدمج", "builtin");', body)
        self.assertIn("frameSets.get(index).title", body)
        self.assertIn("frameSets.get(index).id", body)
        # رقاقة إعادة المحاولة عند تعذر الوصول للمكتبة
        self.assertIn('addButtonStripChip("↻ إعادة تحميل المكتبة", "__retry");', body)

    def test_elements_renderer_dispatches(self):
        src = read(STUDIO)
        body = method_body(src, "private void renderButtonElements() {")
        self.assertIn("framePresets.removeAllViews();", body)  # لا بقايا لتحميل سابق
        self.assertIn('if ("shapes".equals(buttonsSection))', body)
        self.assertIn("renderShapeCards(width);", body)
        self.assertIn('if ("builtin".equals(buttonsSection))', body)
        self.assertIn("renderBuiltinFrames(width);", body)
        self.assertIn("renderFrameResults(width);", body)
        self.assertIn("framePresets.addView(onlineFrameNote(frameStatus, frameRetry));", body)
        self.assertEqual(4, xml_columns(read(LAYOUT), "button_elements_grid"))

    def test_every_pick_persists_immediately(self):
        src = read(STUDIO)
        for builder in (
            "private void renderShapeCards(int width) {",
            "private void renderBuiltinFrames(int width) {",
        ):
            self.assertIn("persistNow();", method_body(src, builder), builder)

    def test_stale_results_never_render(self):
        """النتائج المتأخرة من قسم سابق تُتجاهل — لا بقايا تحميلات."""
        src = read(STUDIO)
        self.assertIn(
            "if (!onlineFrameSet.equals(setId)) return; // قسم أحدث حلّ محله",
            src,
        )
        self.assertIn(
            "if (!designerBgSection.equals(setId)) return; // قسم أحدث حلّ محله",
            src,
        )
        # المسح قبل كل رسم
        for cleaner in (
            "private void renderButtonElements() {",
            "private void renderDesignerBg() {",
            "private void buildButtonStrip() {",
            "private void buildDesignerBgStrip() {",
        ):
            self.assertIn(
                "removeAllViews();", method_body(src, cleaner), cleaner + " بلا مسح"
            )


class FontSectionRound68(unittest.TestCase):
    """قسم الخط: لون الخط في المفاتيح ولون الحروف الصغيرة — تطبيق فوري."""

    def test_font_section_layout(self):
        xml = read(LAYOUT)
        i = xml.index('android:id="@+id/section_font"')
        block = xml[i : xml.index('android:id="@+id/custom_theme_delete"')]
        for part in (
            "font_presets",
            "main_font_size",
            "sub_font_size",
            "text_color_presets",
            "sub_color_presets",
        ):
            self.assertIn('android:id="@+id/%s"' % part, block, part)
        self.assertIn("لون الخط في المفاتيح", block)
        self.assertIn("لون الخط في الحروف الصغيرة", block)

    def test_color_strips_apply_immediately(self):
        src = read(STUDIO)
        text = method_body(src, "private void buildTextColorStrip() {")
        self.assertIn("text = color;", text)
        self.assertIn("persistNow();", text)
        sub = method_body(src, "private void buildSubColorStrip() {")
        self.assertIn("sub = color;", sub)
        self.assertIn("persistNow();", sub)

    def test_sliders_persist_on_release(self):
        """إفلات أي منزلق يطبّق فوراً — لا انتظار لزر الحفظ."""
        src = read(STUDIO)
        body = method_body(src, "private void configureSliders() {")
        # Round 69: شريطا عرض/ارتفاع الزر انضما — ستة منزلقات تطبّق فوراً
        self.assertEqual(6, body.count("public void onStopTrackingTouch(SeekBar bar) {"))
        self.assertEqual(6, body.count("persistNow();"))
        # منزلقا الصبغة القديمان أُلغيا مع قسم الألوان
        self.assertNotIn("R.id.background_hue", src)
        self.assertNotIn("R.id.key_hue", src)


class ThemesScreenRound68(unittest.TestCase):
    """قسم «كل الثيمات» في واجهة الثيمات — من themes.json كبطاقات تنزيل."""

    def test_layout_all_themes_section(self):
        xml = read(THEMES_XML)
        # Round 70: all_themes_title/all_themes_scroll أُزيلت — الأقسام تضاف
        # مباشرة في all_themes_container داخل التمرير العمودي الموحد
        self.assertIn('android:id="@+id/all_themes_container"', xml)
        self.assertIn('android:id="@+id/themes_main_scroll"', xml)
        # الترتيب — سماتي ثم الافتراضية ثم حاوية مكتبة ثيماتي
        i_custom = xml.index('android:id="@+id/custom_strip_scroll"')
        i_default = xml.index('android:id="@+id/default_strip_scroll"')
        i_builtin = xml.index('android:id="@+id/builtin_section_title"')
        i_container = xml.index('android:id="@+id/all_themes_container"')
        self.assertLess(i_custom, i_builtin)
        self.assertLess(i_builtin, i_default)
        self.assertLess(i_default, i_container)
        ET.fromstring(xml)

    def test_builds_horizontal_strips_per_section(self):
        """كل قسم ثيمات شريط أفقي يُمرَّر يميناً ويساراً."""
        src = read(PICKER_THEMES)
        body = method_body(src, "private void buildAllThemes() {")
        self.assertIn("new HorizontalScrollView(", body)
        self.assertIn("setOrientation(LinearLayout.HORIZONTAL)", body)
        self.assertIn("fillThemeStrip(generation, strip, set);", body)
        self.assertIn("set.title", body)  # ترويسة القسم باسمه العربي

    def test_themes_drawn_as_download_cards(self):
        """البطاقة مرسومة بألوان الثيم مع شارة الحالة — والنقر يثبّت ثم يطبّق."""
        src = read(PICKER_THEMES)
        card = method_body(src, "private void addLibraryThemeCard(")
        # Round 69: البطاقة كيبورد مصغّر كامل — نفس تصميم بطاقة «سماتي»
        self.assertIn("R.layout.item_theme_strip", card)
        self.assertIn("libraryPreviewTheme(", card)  # ألوان الثيم ترسم الكيبورد فوراً
        self.assertIn("thumb.setFrameArtOverride(", card)  # فن الزر بأسرع وقت
        self.assertIn('setText(applied ? "◉" : (stored ? "✓" : "↓"))', card)
        self.assertIn("installLibraryTheme(card, art, theme);", card)
        install = method_body(src, "private void installLibraryTheme(")
        self.assertIn('savedItem(this, OnlineAssetStore.TYPE_FRAME, "tb-" + theme.id)', install)
        self.assertIn('savedItem(this, OnlineAssetStore.TYPE_BACKGROUND, "tg-" + theme.id)', install)
        self.assertIn("ThematyStore.installTheme(", install)

    def test_apply_builds_theme_from_themes_json_colors(self):
        src = read(PICKER_THEMES)
        body = method_body(src, "private void applyLibraryTheme(")
        for color in ("theme.light", "theme.base", "theme.dark", "theme.textColor"):
            self.assertIn(color, body)
        self.assertIn(".withKeyFrame(0)", body)
        self.assertIn(".withKeyFrameUri(framePath)", body)
        self.assertIn('backgroundPath == null ? "" : "file://" + backgroundPath', body)
        self.assertIn("ThemeRepository.save(this, null, theme.name, built)", body)
        self.assertIn("prefs.setTheme(id);", body)
        self.assertIn("showKeyboardIfEnabled();", body)

    def test_no_stale_remnants(self):
        """جيل متصاعد يتجاهل نتائج جلب قديمة — والمثبَّت يطبَّق بلا تنزيل."""
        src = read(PICKER_THEMES)
        self.assertIn("private int allThemesGeneration;", src)
        self.assertIn("final int generation = ++allThemesGeneration;", src)
        self.assertEqual(
            2, src.count("if (generation != allThemesGeneration || isFinishing()) return;")
        )
        self.assertIn("if (strip.getChildCount() > 0) return;", src)  # لا ازدواج
        self.assertIn("root.removeAllViews();", src)  # لا بقايا لتحميل سابق
        self.assertIn("buildAllThemes();", method_body(src, "private void reload() {"))


class ThematyStoreRound68(unittest.TestCase):
    """themes.json مصدر الثيمات — والصور بأسرع وقت عبر كاش متوازٍ."""

    def test_themes_json_is_the_catalog(self):
        src = read(THEMATY)
        self.assertIn(
            'JSON_THEMES = PREFIX + "downloads/json/themes.json";', src
        )
        self.assertNotIn("theme-sets.json", src)  # المصدر القديم أُلغي كلياً

    def test_theme_info_carries_colors(self):
        src = read(THEMATY)
        self.assertIn("public final int base;", src)
        self.assertIn("public final int dark;", src)
        self.assertIn("public final int light;", src)
        self.assertIn("public final int textColor;", src)
        self.assertIn("public final int toolbarText;", src)
        self.assertIn('colorOf(colors, "base", 0xff8ab4f8)', src)
        self.assertIn('colorOf(colors, "dark", 0xff202124)', src)
        self.assertIn('colorOf(colors, "light", 0xffeceff3)', src)
        self.assertIn('colorOf(colors, "text", 0xff202124)', src)
        # أقسام الثيمات تُشتق من items نفسها (set + setAr + عدد)
        self.assertIn('entry.optString("setAr", id)', src)
        self.assertIn('!setId.equals(entry.optString("set"))', src)

    def test_image_cache_and_parallel_pool(self):
        """كاش 16MB وأربعة عمال متوازيين — والكاش يُسلَّم فوراً بلا شبكة."""
        src = read(THEMATY)
        self.assertIn("new android.util.LruCache<String, Bitmap>(16 * 1024)", src)
        self.assertIn("getByteCount() / 1024", src)
        self.assertIn("java.util.concurrent.Executors.newFixedThreadPool(4)", src)
        self.assertIn("pool.execute(task);", src)
        self.assertNotIn("new Thread(task)", src)  # الخيط لكل طلب أُلغي
        body = method_body(src, "public static void previewSource(")
        self.assertIn("final Bitmap cached = imageCache.get(cacheKey);", body)
        self.assertIn("if (cached != null) {", body)  # تسليم فوري من الكاش
        self.assertIn("imageCache.put(cacheKey, picture);", body)

    def test_studio_uses_thematy_store_only(self):
        """الاعتماد على زيب ثيماتي وحده — لا بقايا مسارات التنزيل القديمة."""
        for path in (STUDIO, PICKER_THEMES):
            src = read(path)
            self.assertNotIn("DownloadCatalog", src)
            self.assertNotIn("theme_downloads", src)


class DisciplineRound68(unittest.TestCase):
    """قيود المشروع الدائمة سارية بعد إعادة التصميم."""

    def test_no_lambdas_in_touched_files(self):
        for path in (STUDIO, PICKER_THEMES, THEMATY):
            code = strip_comments(read(path))
            self.assertNotIn("->", code, "Lambda في %s" % path.name)

    def test_zero_recycle(self):
        for path in (STUDIO, PICKER_THEMES):
            self.assertNotIn(".recycle()", read(path), "recycle في %s" % path.name)

    def test_package_stable_and_ime_service_registered(self):
        manifest = read(ROOT / "app/src/main/AndroidManifest.xml")
        self.assertIn('package="com.almlk.swiftkey"', manifest)
        self.assertIn("android.permission.INTERNET", manifest)  # جلب المكتبة عن بُعد
        # ملفات gradle الأصلية كما هي — لا إضافة ولا تعديل (مشروع AIDE)
        self.assertTrue((ROOT / "app/build.gradle").exists())

    def test_removed_structures_gone(self):
        xml = read(LAYOUT)
        for gone in (
            "section_colors",
            "section_effects",
            "surface_color_presets",
            "online_bundle_grid",
            "frame_presets",
            "shape_presets",
            "background_hue",
            "key_hue",
        ):
            self.assertNotIn('android:id="@+id/%s"' % gone, xml, gone)
        src = read(STUDIO)
        self.assertNotIn("buildColorPresets", src)
        self.assertNotIn("buildThematyThemes", src)
        self.assertNotIn("applyBundle", src)
        self.assertNotIn("onlineBundleGrid", src)


if __name__ == "__main__":
    unittest.main()
