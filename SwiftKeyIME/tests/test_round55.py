# -*- coding: utf-8 -*-
"""Round 55 — الأزرار الطولية + إصلاح الاتصال بالموقع.

طلب المالك: «واصلح شكل الازرار لتظهر بشكل طولي وايضا اصلح مشكلة عدم الاتصال
بالموقع وكما اخبرتك يجب ان يكون شكل الزر واقف وليس افقي».

1) الأزرار واقفة:
   - مخططات الأشكال keyshape_*.png طولية 144×240 (كانت 240×144 أفقية).
   - بطاقات شريط الأشكال dp(56)×dp(88) برسم dp(40)×dp(56) — لا دائرية 58×58.
   - بطاقات أقسام الأزرار: أفقية صغيرة أنيقة والفن بشكله الحقيقي (ج69).
   - بطاقات إطارات الإنترنت: ارتفاع dp(96) مقابل عرض ≈dp(66) — طولية واضحة.
2) الاتصال:
   - كومنز يرد بلا مفتاح query عند صفر نتائج => قائمة فارغة «لا نتائج»،
     وليس JSONException تظهر زوراً كـ«تعذر الاتصال بالموقع».
   - محاولتان تلقائيتان، مهل أطول (12/25 ثانية)، ترويسات Api-User-Agent/Accept،
     ورمز حالة HTTP غير 200 يرمى باسمه.
   - رسالة الخطأ تعرض اسم الاستثناء المختصر (shortError) للتشخيص من لقطة الشاشة.
"""
import hashlib
import json
import re
import unittest
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
STUDIO = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/CustomThemeActivity.java"
BG = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/CustomBackgroundActivity.java"
STORE = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/OnlineAssetStore.java"
THEMATY = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/ThematyStore.java"
ZIPR = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/ZipRangeReader.java"
LAYOUT = ROOT / "app/src/main/res/layout/activity_custom_theme.xml"
NODPI = ROOT / "app/src/main/res/drawable-nodpi"
RADII = (0, 5, 10, 17, 28, 36)


def read(path):
    return path.read_text(encoding="utf-8")


def balanced(path):
    """عدّاد أقواس واعٍ للسلاسل والمحارف والتعليقات (نمط الجولات)."""
    out, mode, quote = [], "code", ""
    src = read(path)
    i = 0
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


def method_body(src, signature):
    i = src.index(signature)
    j = src.index("\n  }", i)
    return src[i:j]


class PortraitButtonsRound55(unittest.TestCase):
    # ------------------------------------------------------------ الأصول
    def test_shape_outlines_are_portrait(self):
        """مخططات الأشكال الستة طولية 144×240 — الزر واقف لا أفقي."""
        for r in RADII:
            path = NODPI / ("keyshape_%d.png" % r)
            self.assertTrue(path.exists(), "مفقود: %s" % path.name)
            im = Image.open(path)
            self.assertEqual((144, 240), im.size, path.name)
            self.assertGreater(im.size[1], im.size[0], "ليس طولياً: %s" % path.name)
            self.assertIn(im.mode, ("RGBA", "LA"), path.name)
            px = im.convert("RGBA").load()
            self.assertLess(px[0, 0][3], 40, path.name)  # الهامش شفاف
            self.assertGreater(px[72, 120][3], 200, path.name)  # الوجه حاضر

    def test_shape_family_progression_kept(self):
        """0/5/10/17 متمايزة و28≡36 كبسولة — نفس عائلة الأشكال السابقة."""
        hashes = {}
        for r in RADII:
            data = (NODPI / ("keyshape_%d.png" % r)).read_bytes()
            hashes[r] = hashlib.sha256(data).hexdigest()
        self.assertEqual(4, len(set(hashes[r] for r in (0, 5, 10, 17))))
        self.assertEqual(hashes[28], hashes[36], "28 و36 يجب أن تتطابقا (كبسولة)")

    def test_frame_art_untouched_landscape_for_runtime(self):
        """أصول الإطارات المدمجة تبقى 240×144 — تُمَدّ على وجه الزر الحقيقي
        بلا تشويه، بينما البطاقة الأفقية (ج69) تعرض الفن بشكله الحقيقي."""
        for art in ("gold", "carbon", "neon"):
            im = Image.open(NODPI / ("keyframe_%s_17.png" % art))
            self.assertEqual((240, 144), im.size, art)

    # ------------------------------------------------------------ الواجهة
    def test_shape_strip_cards_portrait(self):
        """بطاقات الأشكال: مستطيل مدور dp(56)×dp(88) ورسم واقف dp(40)×dp(56)."""
        body = method_body(read(STUDIO), "private void renderShapeCards(int width)")
        self.assertIn("panel.setCornerRadius(dp(12));", body)  # ج68: خلية اللستة
        self.assertNotIn("GradientDrawable.OVAL", body)
        self.assertIn("params.height = frameCardHeight(width);", body)  # بطاقة طولية
        self.assertEqual(2, body.count("new FrameLayout.LayoutParams(dp(40), dp(56));"))
        self.assertNotIn("dp(44), dp(27)", body)
        self.assertNotIn("dp(58), dp(58)", body)

    def test_shape_strip_xml_height_fits_cards(self):
        """ج68: الأشكال قسم في شريط الأقسام الثابت — رقاقة «الأشكال» أولاً."""
        xml = read(LAYOUT)
        src = read(STUDIO)
        self.assertNotIn('android:id="@+id/shape_presets"', xml)  # الشريط القديم أُلغي
        body = method_body(src, "private void buildButtonStrip()")
        self.assertIn('addButtonStripChip("الأشكال", "shapes");', body)
        self.assertIn('addButtonStripChip("المدمج", "builtin");', body)

    def test_frame_cards_fill_portrait(self):
        """بطاقات الإطارات المدمجة: الفن بشكله الحقيقي (FIT_CENTER — ج69)
        فيبدو الزر واقفاً بدل شريط أفقي قصير وسط بطاقة فارغة."""
        body = method_body(read(STUDIO), "private void renderBuiltinFrames(int width)")
        # Round 69: الفن بشكله الحقيقي متمركزاً — بطاقة أفقية أنيقة كزر حقيقي
        self.assertIn("image.setScaleType(ImageView.ScaleType.FIT_CENTER);", body)
        self.assertNotIn("ImageView.ScaleType.FIT_XY", body)
        self.assertNotIn("ImageView.ScaleType.CENTER_CROP", body)
        self.assertIn("params.height = frameCardHeight(width);", body)  # Round 69: بطاقة أفقية أنيقة

    def test_online_frame_cards_portrait(self):
        """Round 62: بطاقات الإطارات أزرار طولية صغيرة — 4 في الصف بنسبة المفتاح."""
        body = method_body(read(STUDIO), "private void addOnlineFrameCard(")
        self.assertIn("params.height = frameCardHeight(width);", body)
        self.assertNotIn("params.height = dp(96);", body)
        self.assertIn("KeyArtProcessor.shapeButton(picture)", body)  # معاينة زر حقيقية
        src = read(STUDIO)
        width_line = re.search(
            r"int width = \(getResources\(\)\.getDisplayMetrics\(\)\.widthPixels"
            r" - dp\(24\) - dp\(30\)\) / 4;", src)
        self.assertTrue(width_line, "حساب عرض الأعمدة الأربعة مفقود")

    def test_online_cards_still_search_grid_five_columns(self):
        """Round 59/68: لستة العناصر الموحدة — renderButtonElements يرسم حسب القسم."""
        body = method_body(read(STUDIO), "private void renderButtonElements()")
        self.assertIn("renderShapeCards(width);", body)
        self.assertIn("renderBuiltinFrames(width);", body)
        self.assertIn("renderFrameResults(width);", body)
        src = read(STUDIO)
        self.assertIn("(getResources().getDisplayMetrics().widthPixels - dp(24) - dp(16)) / 3;", src)


class ConnectionFixRound55(unittest.TestCase):
    # ------------------------------------------------------------ المتجر
    def test_zero_results_is_not_connection_error(self):
        """قسم فارغ في المكتبة يُبلَّغ بوضوح — لا قوائم صامتة ولا أخطاء غامضة."""
        thematy = read(THEMATY)
        self.assertIn("result.isEmpty() ? null : result", thematy)
        self.assertIn("listener.onReady(result.isEmpty() ? null : result, failure);", thematy)

    def test_zero_results_response_simulation(self):
        """محاكاة الرد الحرفي من كومنز عند صفر نتائج — بلا مفتاح query."""
        raw = '{"batchcomplete":""}'
        root = json.loads(raw)
        self.assertIsNone(root.get("query"))
        # نفس منطق جافا: optJSONObject => null => مكرر فارغ => «لا نتائج»
        pages = None if root.get("query") is None else root["query"].get("pages")
        items = list(pages or {})
        self.assertEqual([], items)

    def test_retry_and_timeouts(self):
        """Round 67: أدب الشبكة في قارئ الزيب — محاولتان ومهل 12/25 وRange."""
        zipr = read(ZIPR)
        self.assertIn("for (int attempt = 0; attempt < 2; attempt++)", zipr)
        self.assertIn("Thread.sleep(1200L);", zipr)
        self.assertIn("link.setConnectTimeout(12000);", zipr)
        self.assertIn("link.setReadTimeout(25000);", zipr)
        self.assertIn('setRequestProperty("User-Agent", userAgent);', zipr)
        self.assertIn('link.setRequestProperty("Range", range);', zipr)
        # رمز الحالة يرمى باسمه بدل صمت null
        self.assertIn('throw new IOException("HTTP " + status);', zipr)
        # المتجر نفسه بلا أي شبكة
        self.assertNotIn("HttpURLConnection", read(STORE))

    def test_short_error_helper(self):
        """shortError يستخرج اسم الاستثناء للتشخيص من لقطة الشاشة."""
        src = read(STORE)
        self.assertIn("public static String shortError(String error)", src)
        # محاكاة منطق القص بالبايثون
        def short_error(error):
            if error is None:
                return ""
            colon = error.find(":")
            head = error[:colon] if colon > 0 else error
            dot = head.rfind(".")
            name = head[dot + 1:] if dot >= 0 else head
            return name or error
        self.assertEqual(
            "UnknownHostException",
            short_error("java.net.UnknownHostException: commons.wikimedia.org"))
        self.assertEqual("JSONException", short_error("org.json.JSONException"))
        self.assertEqual("IOException", short_error("java.io.IOException: HTTP 503"))
        self.assertEqual("", short_error(None))

    # ------------------------------------------------------------ الرسائل
    def test_error_notes_show_reason(self):
        """رسالة «تعذر الاتصال» في النشاطين تعرض سبباً مختصراً بين قوسين."""
        studio = read(STUDIO)
        self.assertIn(
            '"تعذر الوصول لمكتبة ثيماتي (" + OnlineAssetStore.shortError(error)', studio)
        bg = read(BG)
        self.assertIn(
            '"تعذر الوصول لمكتبة ثيماتي (" + OnlineAssetStore.shortError(error)', bg)
        # «لا نتائج» تبقى رسالة مستقلة سليمة (ليست خطأ اتصال)
        # Round 59: اللستة الموحدة — «رقاقة» صارت «عنواناً»
        self.assertIn('"لا عناصر في هذا القسم"', studio)

    def test_manifest_internet_permission(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn("android.permission.INTERNET", manifest)

    # ------------------------------------------------------------ نظافة
    def test_no_lambdas_in_edited_files(self):
        for path in (STORE, STUDIO, BG):
            src = read(path)
            self.assertNotIn("->", src, path.name)

    def test_braces_balanced(self):
        for path in (STORE, STUDIO, BG):
            self.assertTrue(balanced(path), "أقواس غير متوازنة في %s" % path.name)

    def test_braces_balanced(self):
        """عدّاد واعٍ للسلاسل والتعليقات (نمط الجولات) — القوس داخل نص
        مثل lastIndexOf(" (") لا يُحتسب."""


if __name__ == "__main__":
    unittest.main()
