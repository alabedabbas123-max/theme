# -*- coding: utf-8 -*-
"""Round 52 contract: التطبيق الشامل + معاينة الكيبورد الحقيقي.

طلب المالك: اختيار شكل زر أو خلفية يجب أن يطبّق على كل شيء في الكيبورد —
الأزرار والواجهات والاقتراحات وشريط الأدوات وبقية الواجهات؛ والمعاينة أثناء
التصميم يجب أن تُظهر الكيبورد نفسه (لا واجهة سيئة لا تمثله) بحجم الزر الحقيقي؛
وخلفيات الإنترنت (round51) تظهر في قسم الخلفيات.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
PAINT = JAVA / "theme/ThemeSurfacePaint.java"
SKIN = JAVA / "theme/ThemeSkinBar.java"
RUNTIME = JAVA / "ime/AlmlkImeRuntimePart2A.java"
PART5 = JAVA / "ime/AlmlkImeRuntimePart5.java"
PANELS = [
    JAVA / "ime/EmojiPanelView.java",
    JAVA / "ime/ClipboardPanelView.java",
    JAVA / "ime/MoreToolsPanelView.java",
    JAVA / "ime/TranslationPanelView.java",
    JAVA / "ime/VoiceInputPanelView.java",
    JAVA / "ime/ThemeDecorationBarLayout.java",
]
STUDIO = JAVA / "settings/CustomThemeActivity.java"
PICKER = JAVA / "settings/CustomBackgroundActivity.java"
LAYOUT = ROOT / "app/src/main/res/layout/activity_custom_theme.xml"


def read(path):
    return path.read_text(encoding="utf-8")


def balanced(path):
    """عدّاد أقواس واعٍ للسلاسل النصية والمحارف والتعليقات (نمط الجولات)."""
    out, mode, quote = [], "code", ""
    i = 0
    src = read(path)
    while i < len(src):
        c = src[i]
        if mode == "code":
            if c == '"' or c == "'":
                mode, quote = "str", c
            elif c == "/" and i + 1 < len(src) and src[i + 1] == "/":
                mode = "line"
            elif c == "/" and i + 1 < len(src) and src[i + 1] == "*":
                mode = "block"
            else:
                out.append(c)
        elif mode == "str":
            if c == "\\":
                i += 1
            elif c == quote:
                mode = "code"
        elif mode == "line":
            if c == "\n":
                mode = "code"
        elif mode == "block":
            if c == "*" and i + 1 < len(src) and src[i + 1] == "/":
                mode = "code"
                i += 1
        i += 1
    return (
        out.count("{") == out.count("}")
        and out.count("(") == out.count(")")
        and out.count("[") == out.count("]")
    )


class UniversalThemeRound52(unittest.TestCase):
    # ------------------------------------------------------------ الرسّام المشترك
    def test_surface_paint_exists(self):
        src = read(PAINT)
        self.assertIn("public final class ThemeSurfacePaint extends Drawable", src)
        self.assertIn("public static ThemeSurfacePaint forTheme(", src)
        self.assertIn("public static void apply(View view, Context context, KeyboardTheme theme)", src)
        self.assertIn("theme.imageSource()", src)

    def test_surface_paint_mirrors_keyboard_background(self):
        src = read(PAINT)
        # قص مركزي كقياس لوحة المفاتيح + غسل الخلفية نفسه + تعتيم round40
        self.assertIn("Math.max(", src)
        self.assertIn("canvas.drawBitmap(art, null, target, paint);", src)
        self.assertIn("ANIMATION_ROYAL ? 42 : 58", src)
        self.assertIn("theme.backgroundDim", src)
        self.assertIn("Math.min(.85f, Math.abs(dim))", src)

    def test_surface_paint_decode_contract(self):
        src = read(PAINT)
        self.assertIn("AssetThemeLibrary.URI_PREFIX", src)
        self.assertIn("inSampleSize = 2", src)
        self.assertIn("openInputStream", src)
        self.assertIn("CACHE", src) # فك مشترك ولا تدوير أبداً

    # ------------------------------------------------------------ التطبيق الشامل
    def test_runtime_signature_covers_every_visual_field(self):
        src = read(RUNTIME)
        block = src[src.index("private int themeSignature"): src.rindex("}")]
        for field in (
            "theme.fontStyle",
            "Float.floatToIntBits(theme.backgroundDim)",
            "theme.surfaceOverride",
            "theme.bottomKey",
            "theme.spaceKey",
            "theme.deleteKey",
            "theme.shiftKey",
            "theme.keySkin.hashCode()",
            "theme.keyFrame",
            "theme.keyFrameUri.hashCode()",
        ):
            self.assertIn(field, block, "التوقيع يفتقد %s" % field)

    def test_runtime_bars_priority_skin_then_image(self):
        src = read(RUNTIME)
        self.assertIn("private android.graphics.drawable.Drawable barSurfaceArt()", src)
        self.assertIn("ThemeSkinBar.forTheme(this, currentTheme);", src)
        self.assertIn("ThemeSurfacePaint.forTheme(this, currentTheme);", src)
        # الأشرطة الثلاثة + شريط الأدوات تمر كلها عبر barSurfaceArt
        self.assertEqual(4, src.count("barSurfaceArt()") - 1, "مواضع استخدام barSurfaceArt")

    def test_runtime_paints_every_panel(self):
        src = read(RUNTIME)
        for panel in (
            "emojiPanel",
            "translationPanel",
            "moreToolsPanel",
            "clipboardPanel",
            "voiceInputPanel",
            "layoutsPanel",
        ):
            self.assertIn(
                "ThemeSurfacePaint.apply(%s, this, currentTheme)" % panel,
                src,
                "اللوحة %s بلا خلفية الثيم" % panel,
            )

    def test_panels_use_shared_painter(self):
        for path in PANELS:
            src = read(path)
            self.assertIn(
                "ThemeSurfacePaint.apply(",
                src,
                "%s لم يعتمد الرسّام المشترك" % path.name,
            )
            self.assertNotIn(
                "setBackgroundColor(theme.surface)",
                src.replace("ThemeSurfacePaint.apply(this, getContext(), theme);", ""),
                "%s ما زال يسطح لون السطح مباشرة" % path.name,
            )
            self.assertNotIn(
                "setBackgroundColor(t.surface)",
                src,
                "%s ما زال يسطح لون السطح مباشرة (t)" % path.name,
            )

    def test_suggestion_chips_follow_real_shape(self):
        src = read(PART5)
        self.assertIn("Math.min(14f, currentTheme.keyRadiusDp)", src)
        self.assertNotIn("Math.max(4, Math.min(12", src) # الأرضية القديمة سقطت

    # ------------------------------------------------------------ المعاينة الحقيقية
    def test_studio_shows_real_keyboard(self):
        src = read(STUDIO)
        self.assertIn("realNumberRowVisible()", src)
        self.assertIn('getSharedPreferences("keyboard_ui", 0).getBoolean("number_row", false)', src)
        self.assertIn("realKeyboardLayout()", src)
        block = src[src.index("private KeyboardLayout realKeyboardLayout"): src.index("private void readTheme")]
        self.assertIn('"arabic_layout"', block)
        self.assertIn("KeyboardLayouts.xmlFor(variant, true)", block)
        self.assertIn("KeyboardXmlParser.loadLetters(", block)
        self.assertIn("return LayoutProvider.arabic();", block)

    def test_studio_real_key_size(self):
        src = read(STUDIO)
        self.assertIn(
            "preview.setKeyHeightDp(KeyboardResizeModel.loadRowHeightDp(this))", src
        )
        self.assertNotIn("preview.setNumberRowVisible(true);", src)

    def test_studio_preview_bars_priority(self):
        src = read(STUDIO)
        self.assertIn("private android.graphics.drawable.Drawable previewBarArt(", src)
        self.assertIn("ThemeSurfacePaint.forTheme(this, value)", src)
        # الرقاقة تتبع الشكل الحقيقي في المعاينة أيضاً
        self.assertIn("Math.min(14f, value.keyRadiusDp)", src)
        # أيقونات الأدوات تتلوّن كالواجهة الحية
        # Round 69: أيقونات الأدوات بلون حروف الزر الموحّد (chipTextColor)
        self.assertIn("((ImageButton) child).setColorFilter(chipTextColor);", src)

    def test_layout_real_preview_dimensions(self):
        src = read(LAYOUT)
        # الارتفاع يتبع الكيبورد الحقيقي لا رقم fixed
        preview = src[src.index("custom_preview_suggestions"):]
        self.assertIn('android:layout_height="46dp"', preview) # ارتفاع الشريط الحقيقي
        self.assertIn('android:layout_height="wrap_content"', src[src.index('android:id="@+id/custom_theme_preview"') - 120:])
        container = src[src.index("#161329") - 160: src.index("#161329")]
        self.assertIn('android:layout_height="wrap_content"', container)

    def test_layout_toolbar_uses_real_icons(self):
        src = read(LAYOUT)
        toolbar = src[src.index('custom_preview_toolbar'): src.index("custom_theme_preview")]
        for icon in (
            "ic_more",
            "ic_search",
            "ic_mic",
            "ic_translate",
            "ic_clipboard",
            "ic_emoji",
            "ic_ai",
            "ic_settings",
        ):
            self.assertIn("@drawable/%s" % icon, toolbar, "أيقونة %s غائبة عن المعاينة" % icon)
        for glyph in ("☻", "✦", "⌣", "⌄"):
            self.assertNotIn(glyph, toolbar, "الرمز القديم %s ما زال مكان الأيقونة" % glyph)

    # ------------------------------------------------------------ خلفيات الإنترنت
    def test_online_backgrounds_still_in_picker(self):
        src = read(PICKER)
        self.assertIn("خلفيات من مكتبة ثيماتي", src)
        self.assertIn("ThematyStore.sets(", src)  # Round 67: المصدر أرشيف ثيماتي

    # ------------------------------------------------------------ نظافة عامة
    def test_no_lambdas_and_balanced(self):
        for path in [PAINT, RUNTIME, PART5, STUDIO] + PANELS:
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, "Lambda في %s" % path.name)
            self.assertTrue(balanced(path), "أقواس غير متوازنة في %s" % path.name)

    def test_skin_bar_contract_untouched(self):
        src = read(SKIN)
        self.assertIn("public static ThemeSkinBar forTheme(", src)
        self.assertIn("Each call returns a FRESH Drawable", src)


if __name__ == "__main__":
    unittest.main()
