# -*- coding: utf-8 -*-
"""Round 63 — سهم التحميل + صح المحمَّل + حلقة التحميل + ضبط تجمّد الكيبورد.

طلب المالك: «لا تنسئ سهم التحميل كي يكون دليل على ضرورة تحميل الصورة او الشكل او
التفاعلات وكذلك دائرة التحميل التي تعمل اثناء التحميل بحيث تكون الدائرة حول الزر او
حول الصورة او حول العنصر المراد تحميله مع ضبط التحميل حيث ان الكيبورد يظهر خطا
ويتوقف اثناء التحميل» + صورة مرجعية: أزرار بسهم تحميل و✓ على العنصر المحمَّل مسبقاً.

التنفيذ:
1. **الشارات** (OnlineAssetStore.statusBadge موحدة لكل اللستات): «↓» بدائرة زرقاء
   = يحتاج تحميلاً؛ «✓» بدائرة خضراء = محمَّل مسبقاً؛ «✓» بدائرة لون التطبيق
   (كهرماني للإطارات/التفاعلات، أزرق للمنتقي) = المطبَّق حالياً.
2. **حلقة التحميل**: ProgressBar دوّار (indeterminate) يُعلَّق وسط البطاقة أثناء
   التحميل ويُزال فور انتهائه، مع حارس «لا تحميل مزدوج» (نقر ثانٍ أثناء الحلقة
   يُتجاهل) — في الإطارات والتفاعلات ومنتقي الخلفيات.
3. **ضبط التجمّد**: الكيبورد كان يفكّ صور التحميل على خيطه الرئيسي لحظة التطبيق
   فيتجمد/يتوقف. الحل: warmArt على خيط التحميل نفسه (fileKeyArt للإطارات،
   fileSprite الجديد ≤256px للتفاعلات) قبل تسليم النتيجة — والمحرك يجدها في الكاش
   فلا فكّ على الرئيسي إطلاقاً (الأنشطة والكيبورد عملية واحدة بلا android:process).
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"
STORE = JAVA / "com/almlk/swiftkey/settings/OnlineAssetStore.java"
STUDIO = JAVA / "com/almlk/swiftkey/settings/CustomThemeActivity.java"
PICKER = JAVA / "com/almlk/swiftkey/settings/CustomBackgroundActivity.java"
VIEW = JAVA / "com/almlk/swiftkey/ime/SmartKeyboardView.java"
KAP = JAVA / "com/almlk/swiftkey/theme/KeyArtProcessor.java"
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


class StatusBadgesRound63(unittest.TestCase):
    def test_unified_badge_helper(self):
        """statusBadge: ↓ أزرق لغير المحمَّل، ✓ أخضر للمحمَّل، ✓ بلون التطبيق للمطبَّق."""
        src = read(STORE)
        body = method_body(src, "public static TextView statusBadge(")
        self.assertIn('badge.setText(applied || stored ? "✓" : "↓");', body)
        self.assertIn("0xff168fe5", body)  # أزرق: سهم التحميل
        self.assertIn("0xff2e9e4f", body)  # أخضر: محمَّل مسبقاً
        self.assertIn("GradientDrawable.OVAL", body)
        self.assertIn("Gravity.RIGHT | Gravity.BOTTOM", body)

    def test_all_three_lists_use_the_badge(self):
        """الإطارات والتفاعلات والمنتقي كلها تستدعي statusBadge."""
        studio = read(STUDIO)
        for builder in ("private void addOnlineFrameCard(", "private void addPressFxCard("):
            body = method_body(studio, builder)
            self.assertIn("OnlineAssetStore.statusBadge(this, stored, applied, 0xffffd51a, 0xff202124, dp(18))", body, builder)
        picker = method_body(read(PICKER), "private void addOnlineBackgroundCard(")
        self.assertIn("OnlineAssetStore.statusBadge(this, stored, applied, 0xff168fe5, Color.WHITE, dp(24))", picker)
        # الشارة القديمة الفارغة للمحمَّل اختفت
        self.assertNotIn('(stored ? "" : "↓")', studio)
        self.assertNotIn('(stored ? "" : "↓")', read(PICKER))


class LoadingRingRound63(unittest.TestCase):
    def test_ring_helper(self):
        """loadingRing: ProgressBar دوّار indeterminate."""
        body = method_body(read(STORE), "public static ProgressBar loadingRing(")
        self.assertIn("new ProgressBar(context)", body)
        self.assertIn("setIndeterminate(true)", body)

    def test_ring_around_card_during_download(self):
        """الحلقة تُعلَّق وسط البطاقة عند النقر وتُزال فور انتهاء التحميل."""
        studio = read(STUDIO)
        for builder, host in (
            ("private void addOnlineFrameCard(", "art"),
            ("private void addPressFxCard(", "art"),
        ):
            body = method_body(studio, builder)
            self.assertIn("OnlineAssetStore.loadingRing(", body)
            self.assertIn("%s.addView(ring, ringParams);" % host, body, builder)
            self.assertIn("%s.removeView(ring);" % host, body, builder)
            self.assertIn("new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER)", body)
        picker = method_body(read(PICKER), "private void addOnlineBackgroundCard(")
        self.assertIn("frame.addView(ring, ringParams);", picker)
        self.assertIn("frame.removeView(ring);", picker)

    def test_no_double_download_guard(self):
        """نقر ثانٍ أثناء دوران الحلقة يُتجاهل — لا تحميل مزدوج."""
        for path, builders in (
            (STUDIO, ("private void addOnlineFrameCard(", "private void addPressFxCard(")),
            (PICKER, ("private void addOnlineBackgroundCard(",)),
        ):
            src = read(path)
            for builder in builders:
                body = method_body(src, builder)
                self.assertIn("instanceof android.widget.ProgressBar) return;", body, builder)


class FreezeFixRound63(unittest.TestCase):
    def test_download_warms_art_off_main_thread(self):
        """warmArt على خيط التحميل: الإطار fileKeyArt والتفاعل fileSprite."""
        src = read(STORE)
        self.assertIn("warmArt(type, out.getAbsolutePath());", src)
        body = method_body(src, "private static void warmArt(")
        self.assertIn("KeyArtProcessor.fileKeyArt(path);", body)
        self.assertIn("KeyArtProcessor.fileSprite(path);", body)
        # Round 67: التسخين داخل saveBytes قبل إعادة المسار
        save = method_body(src, "public static String saveBytes(")
        self.assertIn("warmArt(type, out.getAbsolutePath());", save)
        self.assertIn("markSaved(context, type, item, out.getAbsolutePath());", save)

    def test_file_sprite_is_bounded_and_cached(self):
        """fileSprite: فكّ ≤256px بذاكرة محدودة + كاش sprite|."""
        src = read(KAP)
        body = method_body(src, "public static Bitmap fileSprite(String path) {")
        self.assertIn('cacheKey = "sprite|" + path', body)
        self.assertIn("decodeFileCapped(path, 256)", body)

    def test_engine_never_decodes_download_on_main(self):
        """المحرك: تفاعل الضغط عبر fileSprite — لا decodeFile خام على الرئيسي."""
        src = read(VIEW)
        self.assertIn("pressSprite = KeyArtProcessor.fileSprite(pressEffectUri);", src)
        self.assertNotIn("pressSprite = BitmapFactory.decodeFile(pressEffectUri);", src)
        self.assertIn("KeyArtProcessor.fileKeyArt(theme.keyFrameUri);", src)

    def test_same_process_so_warm_cache_reaches_the_ime(self):
        """الأنشطة والكيبورد عملية واحدة (لا android:process) — الكاش المشترك يصل."""
        self.assertNotIn("android:process", read(MANIFEST))


class DisciplineRound63(unittest.TestCase):
    def test_no_lambdas_in_touched_files(self):
        for path in (STORE, STUDIO, PICKER, VIEW, KAP):
            code = strip_comments(read(path))
            self.assertNotIn("->", code, "Lambda في %s" % path.name)

    def test_touched_files_exist(self):
        for path in (STORE, STUDIO, PICKER, VIEW, KAP):
            self.assertTrue(path.exists())


if __name__ == "__main__":
    unittest.main()
