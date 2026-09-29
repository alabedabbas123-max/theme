# -*- coding: utf-8 -*-
"""Round 48 contract: the custom-theme studio.

Owner request: أصلح شكل الأيقونات في قسم الثيمات المخصصة ليكون الشكل جميلاً
وواضحاً وبشكل أصغر — لا خلفيات لا يظهر عليها إلا نصها؛ وعند فتح هذه الواجهة
يتم تطبيق الثيم المستخدم مباشرة بكل تفاصيله وشريط الأدوات والاقتراحات —
ليس تثبيت شكل واحد، بل يظهر الكيبورد بنفس شكله وتفاصيله.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
STUDIO = JAVA / "settings/CustomThemeActivity.java"
FX = JAVA / "settings/ThemeDecorationThumbnailView.java"
THUMB = JAVA / "settings/ThemeThumbnailView.java"


def read(path):
    return path.read_text(encoding="utf-8")


def method_body(src, signature):
    i = src.index(signature)
    j = src.index("\n  }", i)
    return src[i:j]


def balanced(path):
    """عدّاد أقواس واعٍ للسلاسل النصية والمحارف والتعليقات."""
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
    code = "".join(out)
    return code.count("{") == code.count("}") and code.count("(") == code.count(")")


class CustomStudioRound48(unittest.TestCase):
    # ------------------------------------------------------------ بطاقات Auto
    def test_auto_cards_are_real_keyboard_previews(self):
        """بطاقات Auto: معاينة كيبورد حقيقية مصغرة (ThemeThumbnailView بثيم
        الجلسة) وجسيمات التأثير فوقها كتراكب، وتسمية صغيرة تحتها — لا خلفية
        متدرجة كبيرة يظهر عليها النص وحده."""
        body = method_body(read(STUDIO), "private void buildAutoThemes()")
        self.assertIn("ThemeThumbnailView thumb = new ThemeThumbnailView(this);", body)
        self.assertIn("thumb.setTheme(currentForImage(effectImage));", body)
        self.assertIn("sample.setOverlayOnly(true);", body)
        self.assertIn("sample.setStyle(effectStyle);", body)
        # أصغر: بطاقة 88dp بأيقونة موزونة وتسمية 19dp بحجم 11sp
        self.assertIn("params.height = dp(88);", body)
        self.assertIn("label.setTextSize(11);", body)
        self.assertIn("dp(19)", body)
        # لا تسمية كبيرة بيضاء بظل فوق الخلفية (التصميم القديم)
        self.assertNotIn("label.setShadowLayer", body)
        self.assertNotIn("label.setTextSize(15)", body)
        # التأثير المختار يبنى بصورة الجلسة الحالية
        self.assertIn('String effectImage =\n          effectStyle == KeyboardTheme.ANIMATION_NONE', body)

    def test_decoration_thumbnail_has_overlay_mode(self):
        src = read(FX)
        self.assertIn("public void setOverlayOnly(boolean value)", src)
        self.assertIn("private boolean overlayOnly;", src)
        self.assertIn("if (!overlayOnly) {", src)
        # الجسيمات ترسم دائماً (التراكب حي فوق المعاينة)
        i = src.index("if (!overlayOnly) {")
        j = src.index("drawDecoration(canvas, now);", i)
        self.assertLess(i, j)

    # ------------------------------------------------------------ الفتح بالثيم المستخدم
    def test_studio_opens_with_the_used_theme(self):
        """فتح الاستوديو بلا theme_id يطبق «الثيم المستخدم» (Prefs.theme())
        بكامل تفاصيله — لا الشكل الثابت القديم."""
        src = read(STUDIO)
        self.assertIn("KeyboardTheme.load(this, new Prefs(this).theme())", src)
        oncreate = method_body(src, "protected void onCreate(Bundle state)")
        # لا منتقي خلفية تلقائي يحجب المعاينة عند الفتح
        self.assertNotIn("openBackgroundPicker()", oncreate)
        # الاحتياط الثابت يبقى لغريب فقط
        self.assertIn("if (theme == null) {", src)

    def test_session_skin_preserved_until_key_face_edit(self):
        """جلد الثيم المفتوح يبقى حياً في الجلسة (المعاينة بكل التفاصيل)،
        وأي تعديل على وجه الزر يسقطه كي يظهر التعديل."""
        src = read(STUDIO)
        self.assertIn("private String sessionSkin = \"\";", src)
        self.assertIn("sessionSkin = theme == null ? \"\" : theme.keySkin;", src)
        self.assertIn(".withKeySkin(sessionSkin);", src)
        self.assertGreaterEqual(src.count("sessionSkin = \"\";"), 4)  # لون+شكل+وضوح+قطر
        self.assertIn("private KeyboardTheme currentForImage(String overrideImage)", src)
        self.assertIn("return currentForImage(image);", src)

    # ------------------------------------------------------------ تفاصيل المعاينة
    def test_preview_shows_bars_and_button_chips(self):
        """المعاينة تحمل شريط الاقتراحات وشريط الأدوات بفن المسطرة (إن وجد)
        ورقائق كأزرار كيبورد كاملة، وعناصر الأدوات بلون الاقتراحات نفسه."""
        body = method_body(read(STUDIO), "private void updatePreview()")
        # Round 52: previewBarArt() = جلد المسطرة أولاً وإلا صورة الخلفية نفسها
        self.assertIn("com.almlk.swiftkey.theme.ThemeSkinBar.forTheme(this, value)", read(STUDIO))
        self.assertIn("android.graphics.drawable.Drawable rowArt = previewBarArt(value);", body)
        self.assertIn("android.graphics.drawable.Drawable barArt = previewBarArt(value);", body)
        self.assertIn("GradientDrawable.Orientation.TOP_BOTTOM", body)
        self.assertIn("blend(key, Color.WHITE, .34f)", body)
        self.assertIn("blend(key, Color.BLACK, .22f)", body)
        # Round 52: الرقاقة تتبع شكل الزر الحقيقي بلا أرضية (قص عند 14)
        self.assertIn(
            "chip.setCornerRadius(dp((int) Math.min(14f, value.keyRadiusDp)));",
            body,
        )
        # Round 69: لون موحّد من فن الزر — chipTextColor في كل عناصر المعاينة
        self.assertIn("((TextView) child).setTextColor(chipTextColor);", body)
        self.assertNotIn("value.surfaceText", body)  # توحيد round46

    def test_thumbnail_view_untouched_contract(self):
        """ThemeThumbnailView ما زال المصغر الأمين (لا تعديلات جولة 48 عليه
        تكسر عقوده — setTheme/from library)."""
        src = read(THUMB)
        self.assertIn("public void setTheme(KeyboardTheme value)", src)
        self.assertIn("AssetThemeLibrary.keyboardBackground(getContext(), source)", src)

    def test_no_lambdas_and_balanced(self):
        for path in (STUDIO, FX):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")
            self.assertTrue(balanced(path), f"أقواس غير متوازنة في {path.name}")


if __name__ == "__main__":
    unittest.main()
