# -*- coding: utf-8 -*-
"""Round 41-2 contract: space bar matches the keys' shape; the Arabic bottom row
gains the emoji button and the comma key carrying the mic icon in the
alternative-letter slot (exactly like the English reference layout).

Owner request: "اضبط شكل زر المسافة ليكون متناسبًا مع شكل بقية الأزرار، ولا تنسَ
إضافة زر الإيموجي وزر رمز الفاصلة + أيقونة الميكرفون في مكان الحرف البديل له
كما في الصورة الثانية" (the second image = the English layout's bottom row).
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
XML = ROOT / "app/src/main/res/xml"
VIEW = JAVA / "ime/SmartKeyboardView.java"
THUMB = JAVA / "settings/ThemeThumbnailView.java"
PROVIDER = JAVA / "ime/LayoutProvider.java"

ARABIC_FILES = [
    "kbd_arabic.xml",
    "kbd_arabic_102.xml",
    "kbd_arabic_azerty.xml",
    "kbd_arabic_mac.xml",
    "kbd_arabic_original.xml",
    "kbd_arabic_pc.xml",
]


def read(path):
    return path.read_text(encoding="utf-8")


def bottom_row(text):
    return re.findall(r'<Row[^>]*?rowEdgeFlags="bottom"[^>]*>\n(.*?)\n    </Row>', text, re.S)[0]


class BottomRowRound412(unittest.TestCase):
    def test_arabic_bottom_rows_carry_emoji_and_mic_comma(self):
        for name in ARABIC_FILES:
            bottom = bottom_row(read(XML / name))
            codes = re.findall(r'android:codes="(-?\d+)"', bottom)
            self.assertEqual(
                ["-4", "-6", "-7", "32", "46", "-3"], codes, name + " ترتيب الصف السفلي"
            )
            self.assertIn('android:keyIcon="@drawable/ic_emoji"', bottom, name)
            self.assertIn('android:keyLabel="،"', bottom, name)
            self.assertNotIn('android:codes="1548"', bottom, name)

    def test_english_reference_row_untouched(self):
        bottom = bottom_row(read(XML / "kbd_qwerty.xml"))
        codes = re.findall(r'android:codes="(-?\d+)"', bottom)
        self.assertEqual(["-4", "-6", "-7", "32", "46", "-3"], codes)

    def test_layout_provider_fallbacks_match(self):
        src = read(PROVIDER)
        # اثنان عربيان + الإنجليزي المرجعي
        self.assertEqual(3, src.count('s("", KeySpec.EMOJI, 1.15f),'))
        self.assertEqual(2, src.count('s("\\u060c", KeySpec.MIC, 1.1f),'))
        self.assertNotIn('k("\\u060c", "", "")', src)

    def test_space_bar_draws_as_horizontal_three_slice(self):
        """المسطرة العريضة تُرسم بثلاث شرائح: الحواف بمقياس المفاتيح والوسط وحده يتمدد."""
        src = read(VIEW)
        self.assertIn("private void drawWideSkinFace(Canvas canvas, Bitmap skin, RectF face, Paint paint) {", src)
        body = src[src.index("private void drawWideSkinFace"):src.index("\n  }\n", src.index("private void drawWideSkinFace")) ]
        for token in ("leftSrc", "middleSrc", "rightSrc"):
            self.assertIn(token, body)
        # Round 42: صيغة تكيفية — رسم مباشر للجلود عريضة النسبة (≥3 كالشريط الذهبي)
        # وحافة تقطيع حتى 48 بكسل مصدر بدل 14 الثابتة.
        self.assertIn("if (skin.getWidth() >= skin.getHeight() * 3f) {", body)
        self.assertIn("int src = Math.min(48, skin.getWidth() / 3);", body)
        self.assertIn("float edge = src * scale;", body)
        self.assertIn("if (key.code == KeySpec.SPACE) {\n        drawWideSkinFace(canvas, skinFace, face, paint);", src)

    def test_mic_key_draws_letter_with_mic_in_alternative_slot(self):
        """الفاصلة تُرسم كأي حرف (خط الأساس 73%) والميكرفون يحل محل الحرف البديل أعلاها."""
        src = read(VIEW)
        self.assertIn("if (icon != null && key.code != KeySpec.MIC) {", src)
        # رسم الحرف للميكرفون يمر بمسار الحروف نفسه (لا مسار خاص قديم)
        self.assertNotIn("face.centerY() - dp(13)", src)
        self.assertNotIn("microphoneMetrics", src)
        # أيقونة الميكرفون الصغيرة أعلى المفتاح
        self.assertIn("if (key.code == KeySpec.MIC) {", src)
        self.assertIn("int micSize = (int) dp(15);", src)
        self.assertIn("int micTop = (int) (face.top + dp(2.5f));", src)
        # Round 58: الأيقونة صارت حافظة — والفاصلة تُدخل نقرةً والتسجيل مطولاً
        self.assertIn("? R.drawable.ic_clipboard // Round 58: زر الفاصلة يمثل الحافظة", src)
        self.assertNotIn("? R.drawable.ic_mic", src)

    def test_thumbnail_renders_comma_with_mic(self):
        src = read(THUMB)
        # Round 58: زر النقطة صار «.» والاستفهام في البدائل (كان «؟»)
        self.assertIn('{"١٢٣", "", "،", "العربية", ".", ""}', src)
        self.assertIn("if (icon != null && code != KeySpec.MIC) {", src)
        self.assertIn("float micSize = rowHeight * .34f;", src)

    def test_no_lambdas_in_round412_files(self):
        for path in (VIEW, THUMB, PROVIDER):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")


if __name__ == "__main__":
    unittest.main()
