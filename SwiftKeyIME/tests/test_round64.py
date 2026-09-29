# -*- coding: utf-8 -*-
"""Round 64 — إصلاح كل أخطاء سجل أخطاء المالك (ErrorLog).

ثلاثة انهيارات من السجل:
1. **«y must be < bitmap.height()» ×5** في frameTextColour — عيّنة السطوع كانت
   تحسب art.getHeight()*2/2 = الارتفاع نفسه (خارج الحدود) لكل إطار إنترنت.
   انفجرت فور اختبار أزرار جولة 62. الإصلاح: قيود Math.min صارمة + try/catch
   يعيد اللون الافتراضي — الصورة التالفة لا توقف الكيبورد أبداً.
2. **«Canvas: trying to use a recycled bitmap» ×2** في drawThemeBackground —
   recycle() المبكر على صورة الخلفية بينما قوائم العرض العتادية (display lists)
   ما زالت تملكها، وعند فصل العرض (onDetachedFromWindow) الذي يُعاد توصيله
   باستمرار في الكيبورد. الإصلاح الجذري: **صفر recycle() في العرض كله** —
   إسقاط المرجع والجامع يكفيان والفك محدود الحجم — + حارس isRecycled() في
   drawThemeBackground يعامل الصورة المعاد تدويرها كغياب الخلفية.
3. **SecurityException ACCESS_NETWORK_STATE** — بنيات قديمة بلا الصلاحية
   (المانيفست الحالي يحويها). الإصلاح الدفاعي: حارس يقفل وجود الصلاحيتين
   (INTERNET + ACCESS_NETWORK_STATE) للأبد، وAppGate ملفوف بـ catch(Throwable)
   كله فلا انهيار حتى لو غابت.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"
VIEW = JAVA / "com/almlk/swiftkey/ime/SmartKeyboardView.java"
THUMB = JAVA / "com/almlk/swiftkey/settings/ThemeThumbnailView.java"
GATE = JAVA / "com/almlk/swiftkey/settings/AppGate.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"


def read(path):
    return path.read_text(encoding="utf-8")


def method_body(src, signature):
    i = src.index(signature)
    j = src.index("\n  }\n", i)
    return src[i:j]


def strip_comments(src):
    src = re.sub(r"/\*[\s\S]*?\*/", " ", src)
    src = re.sub(r"//[^\n]*", " ", src)
    return src


class FrameTextColourFixRound64(unittest.TestCase):
    def test_pixel_sampling_is_clamped(self):
        """عيّنات getPixel مقيّدة بـ Math.min داخل الحدود — لا getHeight() خام."""
        # Round 69: العيّنة انتقلت إلى KeyArtProcessor.frameTextColor المشتركة
        processor = (JAVA / "com/almlk/swiftkey/theme/KeyArtProcessor.java").read_text()
        body = method_body(processor, "public static int frameTextColor(Bitmap art) {")
        self.assertIn("int px = Math.min(art.getWidth() - 1, art.getWidth() * x / 4);", body)
        self.assertIn("int py = Math.min(art.getHeight() - 1, art.getHeight() * y / 2);", body)
        self.assertNotIn("art.getPixel(art.getWidth() * x / 4, art.getHeight() * y / 2)", body)

    def test_broken_art_never_crashes_the_keyboard(self):
        """try/catch كامل يعيد اللون الافتراضي 0xff172033."""
        processor = (JAVA / "com/almlk/swiftkey/theme/KeyArtProcessor.java").read_text()
        body = method_body(processor, "public static int frameTextColor(Bitmap art) {")
        self.assertIn("} catch (Exception ignored) {", body)
        self.assertIn("return 0xff172033;", body)
        self.assertIn('total / Math.max(1, count) < 145 ? Color.WHITE : 0xff172033', body)
        # والكيبورد يفوّض للمشتركة نفسها — عيّنة واحدة لا اثنتان
        view_body = method_body(read(VIEW), "private int frameTextColour(Bitmap art) {")
        self.assertIn("return KeyArtProcessor.frameTextColor(art);", view_body)


class RecycledBitmapFixRound64(unittest.TestCase):
    def test_zero_recycle_in_views(self):
        """صفر recycle() في المحرك والمصغرات — GC وحده يملك دورة الحياة."""
        for path in (VIEW, THUMB):
            code = strip_comments(read(path))
            self.assertNotIn(".recycle()", code, "recycle في %s" % path.name)

    def test_draw_guard_treats_recycled_as_absent(self):
        """drawThemeBackground: صورة معاد تدويرها = خلفية لون فوراً."""
        body = method_body(read(VIEW), "private void drawThemeBackground(Canvas canvas) {")
        self.assertIn("if (themeImage == null || themeImage.isRecycled()) {", body)
        self.assertIn("canvas.drawColor(theme.background);", body)

    def test_detach_no_longer_recycles(self):
        """onDetachedFromWindow يسقط المرجع فقط — العرض يعاد توصيله دائماً."""
        body = method_body(read(VIEW), "protected void onDetachedFromWindow() {")
        self.assertIn("themeImage = null;", body)
        self.assertNotIn("recycle", body)


class NetworkPermissionRound64(unittest.TestCase):
    def test_manifest_has_both_network_permissions(self):
        """الصلاحيتان مقفولتان في المانيفست — لا SecurityException بعد اليوم."""
        manifest = read(MANIFEST)
        self.assertIn('android:name="android.permission.INTERNET"', manifest)
        self.assertIn('android:name="android.permission.ACCESS_NETWORK_STATE"', manifest)

    def test_app_gate_is_crash_proof(self):
        """AppGate كله ملفوف — أي استثناء يعني «متصل» فلا انهيار ولا إزعاج."""
        src = read(GATE)
        self.assertIn("catch (Throwable ignored)", src)
        self.assertIn("getNetworkCapabilities", src)


class DisciplineRound64(unittest.TestCase):
    def test_no_lambdas_in_touched_files(self):
        for path in (VIEW, THUMB, GATE):
            code = strip_comments(read(path))
            self.assertNotIn("->", code, "Lambda في %s" % path.name)

    def test_touched_files_exist(self):
        for path in (VIEW, THUMB, GATE, MANIFEST):
            self.assertTrue(path.exists())


if __name__ == "__main__":
    unittest.main()
