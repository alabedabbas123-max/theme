# -*- coding: utf-8 -*-
"""Round 69 — الزر الحقيقي بلا زوائد + التطبيق على كل العناصر + شاشة الثيمات.

طلب المالك (لقطتان + رسالة):
«اختيار زر أو خلفية يجب أن يطبّق الصورة والألوان على كل عناصر الكيبورد:
تتغير الاقتراحات، ولون زر الاقتراحات بلون زر الكيبورد، ولون الخط نفسه،
وعناصر شريط الأدوات بنفس لون الخط، وكل الواجهات بنفس اللون» +
«رسم الزر على المفاتيح يجب أن يطابق شكل أيقونة الزر — لا زوائد مستطيلة» +
«بطاقات الأزرار صغيرة أنيقة كزر حقيقي لا صورة طويلة» +
«شريطا عرض/ارتفاع الزر في قسم الخط» ثم بعد لقطات جديدة:
«الثيمات الافتراضية شريط أفقي + أقسام التحميل بنفس تصميم سماتي + ثيمات
التنزيل تُرسم ككيبورد لا كصورة فارغة + إصلاح انهيار واجهة التصميم».

التنفيذ:
1. KeyArtProcessor: الفن الجاهز (زوايا شفافة) لا يُقصّ — fitSize فقط؛
   والمعتم يُقصّ لنسبة المفتاح الحقيقية w/h=1/KEY_ASPECT (القلب القديم
   بKEY_ASPECT نفسها كان يقصّ زر 448×616 فيشطب زخرفة الحد).
2. SmartKeyboardView: زر الإنترنت يُرسم fitCenterRect بنسبته الأصلية داخل
   خانة الوجه — بلا مطّ وبلا تقطيع — وframeTextColour تفوّض للمشتركة.
3. مقاييس عرض/ارتفاع الزر (keyWidthScale/keyHeightScale) حقلا ثيم +
   withKeySize + حفظ/تحميل + شريطان في قسم الخط + خانة الوجه keyFaceRect
   للإطارات والوجه الكلاسيكي + ضمن توقيع الثيم الحي.
4. ThemeChipArt الجديد: شارات الاقتراحات تحمل فن الزر (قصّ متمركز بزوايا
   مدورة) ولون حروفها لون حروف الزر — حية وفي المعاينة وفي شريط الأدوات.
5. اختيار زر يلوّن كل الواجهات: applyOnlineFrame/renderBuiltinFrames يضبطان
   text=لون حروف الزر وsub مشتقاً منه.
6. شاشة الثيمات: الافتراضية شريط أفقي (default_strip) كبطاقات سماتي؛
   بطاقات مكتبة ثيماتي كيبوردات مصغّرة (libraryPreviewTheme + فن الزر من
   الكاش المتوازي setFrameArtOverride) — لا صور فارغة.
7. إصلاح الانهيار: fontPresets كان مفقوداً من bindViews منذ ج68 فيسقط
   الوصول للاستوديو فوراً (NullPointerException في buildFontPresets).
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res"
STUDIO = JAVA / "com/almlk/swiftkey/settings/CustomThemeActivity.java"
PICKER = JAVA / "com/almlk/swiftkey/settings/ThemeSettingsActivity.java"
THUMB = JAVA / "com/almlk/swiftkey/settings/ThemeThumbnailView.java"
VIEW = JAVA / "com/almlk/swiftkey/ime/SmartKeyboardView.java"
PART2A = JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart2A.java"
PART5 = JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart5.java"
MODEL = JAVA / "com/almlk/swiftkey/theme/KeyboardTheme.java"
REPO = JAVA / "com/almlk/swiftkey/theme/ThemeRepository.java"
PROCESSOR = JAVA / "com/almlk/swiftkey/theme/KeyArtProcessor.java"
CHIP = JAVA / "com/almlk/swiftkey/theme/ThemeChipArt.java"
STUDIO_XML = RES / "layout/activity_custom_theme.xml"
THEMES_XML = RES / "layout/activity_themes.xml"
STRIP_XML = RES / "layout/item_theme_strip.xml"


def read(path):
    return path.read_text(encoding="utf-8")


def method_body(src, signature):
    i = src.index(signature)
    j = src.index("\n  }", i)
    return src[i:j]


class StudioCrashFixRound69(unittest.TestCase):
    """واجهة التصميم تنهار عند الفتح — fontPresets بلا تعيين منذ ج68."""

    def test_bind_views_assigns_every_section_container(self):
        body = method_body(read(STUDIO), "private void bindViews() {")
        self.assertIn("fontPresets = (LinearLayout) findViewById(R.id.font_presets);", body)
        self.assertIn("backgroundPresets = (LinearLayout) findViewById(R.id.background_presets);", body)
        # كل حاوية يلمسها rebuildControls معيَّنة في bindViews نفسها
        for container in ("backgroundColorPresets", "textColorPresets", "subColorPresets",
                          "framePresets", "designerBgSectionsStrip", "designerBgGrid"):
            self.assertIn("%s = (" % container, body, container)

    def test_font_presets_exists_in_layout(self):
        self.assertIn('android:id="@+id/font_presets"', read(STUDIO_XML))


class TrueShapeArtRound69(unittest.TestCase):
    """شكل الزر المرسوم يطابق أيقونته الحرفية — لا قصّ ولا مطّ ولا زوائد."""

    def test_processor_never_crops_pre_shaped_art(self):
        body = method_body(read(PROCESSOR), "public static Bitmap fileKeyArt(String path) {")
        self.assertIn("out = fitSize(decoded, KEY_MAX_EDGE);", body)
        self.assertNotIn("shape3D(fitAspect(decoded, KEY_ASPECT", body)
        # والمعتم يُقصّ لنسبة المفتاح الحقيقية w/h = 1/KEY_ASPECT
        self.assertIn("shape3D(fitAspect(decoded, 1f / KEY_ASPECT, KEY_MAX_EDGE))", body)

    def test_fit_size_keeps_true_aspect(self):
        body = method_body(read(PROCESSOR), "private static Bitmap fitSize(Bitmap src, int maxEdge) {")
        self.assertIn("int longEdge = Math.max(src.getWidth(), src.getHeight());", body)
        self.assertNotIn("createBitmap(src,", body)  # لا قصّ إطلاقاً — تحديد حجم فقط

    def test_online_frame_draws_true_aspect_fit_center(self):
        src = read(VIEW)
        self.assertIn("RectF artRect = fitCenterRect(onlineFrame, face);", src)
        self.assertIn("canvas.drawBitmap(onlineFrame, null, artRect, paint);", src)
        # نسبة الأصل محفوظة داخل الصندوق — مقياس min بلا أي مطّ
        body = method_body(src, "private RectF fitCenterRect(Bitmap art, RectF box) {")
        self.assertIn("Math.min(box.width() / art.getWidth(), box.height() / art.getHeight())", body)
        self.assertNotIn("drawFrameSlices(canvas, onlineFrame", src)

    def test_frame_text_colour_delegates_to_shared_sampler(self):
        body = method_body(read(VIEW), "private int frameTextColour(Bitmap art) {")
        self.assertIn("return KeyArtProcessor.frameTextColor(art);", body)

    def test_shared_sampler_is_safe(self):
        body = method_body(read(PROCESSOR), "public static int frameTextColor(Bitmap art) {")
        self.assertIn("if (art == null) return 0xff172033;", body)
        self.assertIn("} catch (Exception ignored) {", body)
        self.assertIn("Math.min(art.getWidth() - 1,", body)
        self.assertIn("Math.min(art.getHeight() - 1,", body)


class KeySizeSlidersRound69(unittest.TestCase):
    """شريطا عرض/ارتفاع الزر في قسم الخط — مقاييس محفوظة في الثيم."""

    def test_model_carries_scales_with_clamp(self):
        src = read(MODEL)
        self.assertIn("public final float keyWidthScale;", src)
        self.assertIn("public final float keyHeightScale;", src)
        self.assertIn("public KeyboardTheme withKeySize(float width, float height) {", src)
        self.assertIn("keyWidthScale = Math.max(.4f, Math.min(1f, keyWidth));", src)
        self.assertIn("keyHeightScale = Math.max(.4f, Math.min(1f, keyHeight));", src)

    def test_repository_persists_scales(self):
        src = read(REPO)
        self.assertIn('.putFloat(b + "key_width_scale", theme.keyWidthScale)', src)
        self.assertIn('.putFloat(b + "key_height_scale", theme.keyHeightScale)', src)
        self.assertIn('p.getFloat(base + "key_width_scale", 1f)', src)
        self.assertIn('p.getFloat(base + "key_height_scale", 1f)', src)

    def test_layout_has_both_sliders(self):
        xml = read(STUDIO_XML)
        for slider in ("key_width", "key_height"):
            self.assertIn('android:id="@+id/%s"' % slider, xml)
            self.assertIn('android:id="@+id/%s_value"' % slider, xml)
        self.assertIn('android:text="عرض الزر"', xml)
        self.assertIn('android:text="ارتفاع الزر"', xml)
        ET.fromstring(xml)

    def test_sliders_wired_and_persist(self):
        src = read(STUDIO)
        body = method_body(src, "private void configureSliders() {")
        self.assertIn("SeekBar keyWidthBar = (SeekBar) findViewById(R.id.key_width);", body)
        self.assertIn("SeekBar keyHeightBar = (SeekBar) findViewById(R.id.key_height);", body)
        self.assertIn("keyWidthScale = (progress + 40) / 100f;", body)
        self.assertIn("keyHeightScale = (progress + 40) / 100f;", body)
        self.assertIn('"عرض الزر: " + Math.round(keyWidthScale * 100) + "٪"', body)
        self.assertIn('"ارتفاع الزر: " + Math.round(keyHeightScale * 100) + "٪"', body)
        self.assertEqual(2, body.count("persistNow(); // تطبيق فوري عند إفلات الشريط"),
                         "الشريطان الجديدان يطبّقان فوراً")
        # القراءة والبناء
        self.assertIn("keyWidthScale = theme.keyWidthScale;", src)
        self.assertIn("keyHeightScale = theme.keyHeightScale;", src)
        self.assertIn(".withKeySize(keyWidthScale, keyHeightScale)", src)

    def test_face_rect_applies_scales_to_frames_and_classic(self):
        src = read(VIEW)
        body = method_body(src, "private RectF keyFaceRect(RectF bounds) {")
        self.assertIn("bounds.width() * theme.keyWidthScale", body)
        self.assertIn("bounds.height() * theme.keyHeightScale", body)
        draw = method_body(src, "private void drawKey(")
        # الإطارات (المدمج والأونلاين) والوجه الكلاسيكي — الثلاثة عبر keyFaceRect
        self.assertEqual(3, draw.count("keyFaceRect(bounds)"), "الجلد يبقى بملء الخانة")

    def test_live_signature_includes_scales(self):
        body = method_body(read(PART2A), "private int themeSignature(KeyboardTheme theme) {")
        self.assertIn("Float.floatToIntBits(theme.keyWidthScale)", body)
        self.assertIn("Float.floatToIntBits(theme.keyHeightScale)", body)


class ChipArtEverywhereRound69(unittest.TestCase):
    """الشارات والأدوات تحمل فن الزر ولون حروفه — لون واحد في كل الواجهات."""

    def test_chip_art_class_exists(self):
        src = read(CHIP)
        self.assertIn("public static ThemeChipArt forTheme(Context context, KeyboardTheme theme) {", src)
        self.assertIn("public static int textColor(Context context, KeyboardTheme theme) {", src)
        self.assertIn("KeyArtProcessor.frameTextColor(art)", src)
        # قصّ متمركز بزوايا مدورة — لا مطّ
        self.assertIn("Math.max(", src)
        self.assertIn("addRoundRect", src)

    def test_live_suggestions_use_frame_art(self):
        body = method_body(read(PART5), "protected android.graphics.drawable.Drawable suggestionBackground() {")
        self.assertIn("ThemeChipArt.forTheme(this, currentTheme)", body)
        self.assertIn("if (frameChip != null) return frameChip;", body)

    def test_live_chip_and_toolbar_colors_follow_frame(self):
        src = read(PART2A)
        body = method_body(src, "protected void applySuggestionTheme() {")
        self.assertIn("ThemeChipArt.textColor(this, currentTheme)", body)
        self.assertIn("chipColor);", body)
        toolbar = method_body(src, "protected void applyLiveSettings() {")
        self.assertIn(
            "? com.almlk.swiftkey.theme.ThemeChipArt.textColor(this, currentTheme)", toolbar)

    def test_choosing_a_button_recolors_everything(self):
        src = read(STUDIO)
        online = method_body(src, "private void applyOnlineFrame(String path) {")
        self.assertIn("text = KeyArtProcessor.frameTextColor(art);", online)
        self.assertIn("sub = blend(text, key, .38f);", online)
        self.assertIn("buildTextColorStrip();", online)
        builtin = method_body(src, "private void renderBuiltinFrames(int width) {")
        self.assertIn("text = KeyFrames.textColor(frameId);", builtin)
        self.assertIn("sub = blend(text, key, .38f);", builtin)

    def test_preview_chips_carry_frame_art(self):
        src = read(STUDIO)
        body = method_body(src, "private void updatePreview() {")
        self.assertIn("ThemeChipArt.forTheme(this, value)", body)
        self.assertIn("((TextView) child).setTextColor(chipTextColor);", body)
        self.assertIn("((ImageButton) child).setColorFilter(chipTextColor);", body)

    def test_button_cards_small_and_elegant(self):
        src = read(STUDIO)
        body = method_body(src, "private int frameCardHeight(int width) {")
        self.assertIn("Math.round(width * KeyArtProcessor.KEY_ASPECT)", body)
        self.assertIn("+ dp(18)", body)
        # الفن بشكله الحقيقي في بطاقات المدمج والأونلاين
        builtin = method_body(src, "private void renderBuiltinFrames(int width) {")
        self.assertIn("ImageView.ScaleType.FIT_CENTER);", builtin)
        online = method_body(src, "private void addOnlineFrameCard(")
        self.assertIn("ImageView.ScaleType.FIT_CENTER);", online)


class ThemesScreenRound69(unittest.TestCase):
    """الافتراضية شريط أفقي + بطاقات المكتبة كيبوردات مصغّرة لا صور فارغة."""

    def test_default_themes_horizontal_strip(self):
        xml = read(THEMES_XML)
        self.assertIn('android:id="@+id/default_strip"', xml)
        self.assertIn('android:id="@+id/default_strip_scroll"', xml)
        self.assertNotIn("GridView", xml)  # لا شبكة بعد الآن
        ET.fromstring(xml)
        src = read(PICKER)
        self.assertIn("private LinearLayout defaultStrip;", src)
        self.assertIn("private void buildDefaultStrip() {", src)
        self.assertIn("R.layout.item_theme_strip", src)
        # الشبكة القديمة وملحقاتها أُلغيت
        self.assertNotIn("GridView grid;", src)
        self.assertNotIn("ThemeAdapter", src)
        self.assertNotIn("grid.setAdapter", src)

    def test_default_strip_follows_category_filter(self):
        src = read(PICKER)
        body = method_body(src, "private void applyFilter() {")
        self.assertIn("buildDefaultStrip();", body)

    def test_library_cards_are_mini_keyboards(self):
        src = read(PICKER)
        card = method_body(src, "private void addLibraryThemeCard(")
        # بطاقة «سماتي» نفسها — مصغر كيبورد كامل لا لون مسطح
        self.assertIn("R.layout.item_theme_strip", card)
        self.assertIn("ThemeThumbnailView", card)
        self.assertIn("libraryPreviewTheme(", card)
        self.assertNotIn("border.setColor(theme.light);", card)
        # فن الزر يصل بأسرع وقت من الكاش المتوازي فوق المفاتيح
        self.assertIn("thumb.setFrameArtOverride(", card)
        # الحالة: ◉ مفعّل / ✓ منزّل / ↓ يحتاج تنزيلاً
        self.assertIn('setText(applied ? "◉" : (stored ? "✓" : "↓"))', card)

    def test_preview_theme_from_themes_json_colors(self):
        src = read(PICKER)
        body = method_body(src, "private KeyboardTheme libraryPreviewTheme(")
        for color in ("theme.light", "theme.base", "theme.dark", "theme.textColor"):
            self.assertIn(color, body)
        self.assertIn(".withKeyFrameUri(", body)

    def test_thumbnail_draws_button_art_true_shape(self):
        src = read(THUMB)
        self.assertIn("public void setFrameArtOverride(Bitmap art) {", src)
        self.assertIn("private Bitmap thumbnailFrame() {", src)
        draw = method_body(src, "private void drawKey(")
        self.assertIn("frameOverride != null ? frameOverride : thumbnailFrame()", draw)
        self.assertIn("Math.min(face.width() / frame.getWidth(), face.height() / frame.getHeight())", draw)
        # لون الحروف فوق الفن = لون حروف الزر نفسه
        self.assertIn("letterColor = KeyArtProcessor.frameTextColor(frame);", draw)

    def test_sections_look_like_custom_section(self):
        src = read(PICKER)
        body = method_body(src, "private void buildAllThemes() {")
        self.assertIn("header.setBackgroundColor(Color.WHITE);", body)
        self.assertIn("header.setTextSize(15);", body)
        self.assertIn("scroll.setBackgroundColor(Color.WHITE);", body)
        self.assertIn("dp(152)", body)  # ارتفاع شريط سماتي نفسه


if __name__ == "__main__":
    unittest.main()
