# -*- coding: utf-8 -*-
"""Round 67 — مكتبة «ثيماتي» مصدراً وحيداً بدل بحث الإنترنت.

طلب المالك: يُجلب مشروع ثيماتي (الزيب على GitHub) ويُفحص عبر الرابط بلا
تنزيله كاملاً وبلا تضخيم حجم التطبيق؛ والعنصر المختار يُجلب وحده ويُحفظ
في الذاكرة الداخلية — للأزرار والخلفيات والتفاعلات والثيمات — والتخلص من
الطريقة السابقة (كومنز/Openverse/Wallhaven).

المعمارية:
- ZipRangeReader: قارئ ZIP عن بُعد بطلبات النطاق الجزئية (جافا نقية) —
  يقرأ فهرس الأرشيف من نهايته (~70KB) ثم يجلب أي ملف وحده من داخله.
- ThematyStore: طبقة الفهرس — أقسام الأزرار/الخلفيات (buttons.json)、
  تاغات التفاعلات (effects.json) وأقسام الثيمات (theme-sets.json)، مع
  تثبيت العنصر المختار في الذاكرة عبر OnlineAssetStore.saveBytes.
- OnlineAssetStore: صار ذاكرة داخلية فقط (بلا أي شبكة).
- الواجهات: بطاقات أقسام بدل بطاقات الاستعلامات، والحزم الجاهزة صارت
  أقسام ثيمات كاملة (زر + خلفية بنقرة واحدة).

مُتحقَّق حياً قبل التسليم (أداة tools/ThematyZipSelfTest): 14/14 ناجحة
محلياً وعبر الرابط الفعلي — فهرس 636، قراءة JSON وزر PNG وخلفية JPEG
بأحجام مضبوطة عبر النطاقات.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
SETTINGS = JAVA / "settings"
STUDIO = SETTINGS / "CustomThemeActivity.java"
PICKER = SETTINGS / "CustomBackgroundActivity.java"
THEMATY = SETTINGS / "ThematyStore.java"
ZIPR = SETTINGS / "ZipRangeReader.java"
STORE = SETTINGS / "OnlineAssetStore.java"


def read(path):
    return path.read_text(encoding="utf-8")


def code_of(path):
    import re

    src = read(path)
    src = re.sub(r"/\*[\s\S]*?\*/", " ", src)
    src = re.sub(r"//[^\n]*", " ", src)
    return src


class ZipRangeReaderRound67(unittest.TestCase):
    def test_pure_java_no_android(self):
        """قارئ الزيب جافا نقية — بلا أندرويد — ليقبل الاختبار خارج الجهاز."""
        code = code_of(ZIPR)
        for banned in ("android.", "androidx."):
            self.assertNotIn(banned, code)

    def test_range_requests_discipline(self):
        """طلب النطاق: ترويسة Range ومهل 12/25 ومحاولتان وUA مهذب."""
        src = read(ZIPR)
        self.assertIn('link.setRequestProperty("Range", range);', src)
        self.assertIn("link.setConnectTimeout(12000);", src)
        self.assertIn("link.setReadTimeout(25000);", src)
        self.assertIn("for (int attempt = 0; attempt < 2; attempt++)", src)
        self.assertIn('setRequestProperty("User-Agent", userAgent);', src)

    def test_total_size_from_content_range(self):
        """حجم الأرشيف من ترويسة Content-Range (bytes 0-0/N)."""
        src = read(ZIPR)
        self.assertIn('link.getHeaderField("Content-Range");', src)
        self.assertIn('contentRange.lastIndexOf(\'/\')', src)

    def test_zip_parsing_primitives(self):
        """تحليل EOCD والفهرس المركزي وفك deflate خام — بدلالات موثقة."""
        src = read(ZIPR)
        self.assertIn("static long[] findEocd(byte[] buffer)", src)
        self.assertIn("static Map<String, Entry> parseCentralDirectory(", src)
        self.assertIn("static byte[] inflate(byte[] compressed, int method, int expectedSize)", src)
        self.assertIn("new Inflater(true)", src)  # deflate خام بلا ترويسة zlib
        self.assertIn("if (cdSize == 0xffffffffL", src)  # رفض ZIP64 صراحة

    def test_size_caps(self):
        """سقوف صارمة: ملف واحد ≤ 4MB — لا تفجير ذاكرة بمدخل خبيث."""
        self.assertIn("MAX_FILE_BYTES = 4 * 1024 * 1024", read(ZIPR))

    def test_selftest_tool_exists(self):
        """أداة الاختبار الذاتي الحقيقي موجودة وتفحص المحلي والبعيد."""
        tool = ROOT / "tools/ThematyZipSelfTest.java"
        self.assertTrue(tool.exists())
        body = tool.read_text(encoding="utf-8")
        self.assertIn("new LocalFetcher(", body)
        self.assertIn("ZipRangeReader.HttpFetcher(", body)


class ThematyStoreRound67(unittest.TestCase):
    def test_archive_url_free_no_keys(self):
        """الرابط الرسمي: مستودع GitHub عام بلا مفاتيح ولا اشتراك."""
        src = read(THEMATY)
        self.assertIn(
            "raw.githubusercontent.com/alabedabbas123-max/themes/main/thematy-project.zip", src)
        lowered = src.lower()
        for key in ("apikey=", "api_key", "client_id", "token="):
            self.assertNotIn(key, lowered)

    def test_json_catalog_paths(self):
        """الفهارس الأربعة من مسار downloads/json داخل الأرشيف."""
        src = read(THEMATY)
        # ج68: themes.json حلّ محل theme-sets.json مصدراً وحيداً للثيمات
        for name in ("buttons", "backgrounds", "effects", "themes"):
            self.assertIn('downloads/json/%s.json' % name, src)

    def test_sections_per_type(self):
        """الأزرار/الخلفيات من مجموعات buttons.json (مطابقة 1:1)،
        التفاعلات من التاغات، والثيمات من أقسام theme-sets.json."""
        src = read(THEMATY)
        self.assertIn('!setId.equals(entry.optString("set"))', src)  # أزرار
        self.assertIn('!setId.equals(setOf(id))', src)  # خلفيات (بادئة المعرف)
        self.assertIn('!setId.equals(entry.optString("tag"))', src)  # تفاعلات
        # ج68: ثيمات themes.json تُفلتر بمجموعتها ومعها ألوان colors{}
        self.assertIn('!setId.equals(entry.optString("set"))', src)
        self.assertIn('colorOf(colors, "base", 0xff8ab4f8)', src)
        self.assertIn('colorOf(colors, "text", 0xff202124)', src)
        self.assertIn('static String setOf(String id)', src)

    def test_install_saves_to_internal_memory(self):
        """التثبيت يمر بـ saveBytes — كتابة وفهرسة وتسخين فك."""
        thematy = read(THEMATY)
        self.assertIn("OnlineAssetStore.saveBytes(context, type, item, data)", thematy)
        store = read(STORE)
        self.assertIn("public static String saveBytes(", store)

    def test_install_theme_button_then_background(self):
        """تثبيت الثيم: الزر أولاً ثم الخلفية — والزر يُطبَّق وحده إن تعذّرت."""
        src = read(THEMATY)
        i_button = src.index("zip().readFile(theme.buttonEntry);")
        i_bg = src.index("zip().readFile(theme.backgroundEntry);")
        self.assertLess(i_button, i_bg)
        self.assertIn("// الزر يُطبَّق وحده", src)

    def test_preview_local_or_archive(self):
        """المعاينة: من الملف المحفوظ إن وُجد وإلا من داخل الأرشيف."""
        src = read(THEMATY)
        self.assertIn("item.path.length() > 0 ? item.path : item.thumb", src)
        self.assertIn('source.startsWith("/")', src)

    def test_json_cached_per_session(self):
        """الفهارس تُخزَّن بالذاكرة — لا تُجلب إلا مرة بالجلسة."""
        src = read(THEMATY)
        self.assertIn("jsonCache.get(entry)", src)
        self.assertIn("jsonCache.put(entry, parsed)", src)

    def test_empty_section_reports_error(self):
        """قسم فارغ = null مع خطأ — لا قوائم صامتة."""
        self.assertIn("listener.onReady(result.isEmpty() ? null : result, failure);", read(THEMATY))


class ActivitiesRound67(unittest.TestCase):
    def test_studio_frames_section(self):
        """الأزرار: أقسام ثم عناصر — والمعاينة والتثبيت من المكتبة."""
        src = read(STUDIO)
        for needle in (
            "private void loadFrameSets()",
            "private void showThematyFrames(final String setId)",
            "private void buildButtonStrip()",
            "private void renderButtonElements()",
            "ThematyStore.sets(",
            "ThematyStore.items(",
            "ThematyStore.install(",
            "ThematyStore.preview(",
        ):
            self.assertIn(needle, src)

    def test_studio_pressfx_section(self):
        """التفاعلات: تاغات المكتبة أقساماً وتثبيت الأيقونة المختارة."""
        src = read(STUDIO)
        for needle in (
            "private void loadPressFxSets()",
            "private void buildPressFxSetCards(int width)",
            "private void showThematyPressFx(final String setId)",
            'addListHeader(pressFxPresets, "تفاعلات من مكتبة ثيماتي", 3);',
        ):
            self.assertIn(needle, src)

    def test_studio_theme_sets_replaced_bundles(self):
        """ج68: الثيمات بأقسامها انتقلت لواجهة الثيمات — بطاقات تحتاج تنزيلاً
        في قسم «كل الثيمات»، والاستوديو تخلى عنها كلياً."""
        src = read(STUDIO)
        self.assertNotIn("buildThematyThemes", src)  # لا بقايا في الاستوديو
        self.assertNotIn("applyBundle", src)
        picker = read(JAVA / "settings/ThemeSettingsActivity.java")
        for needle in (
            "private void buildAllThemes()",
            "private void fillThemeStrip(",
            "private void addLibraryThemeCard(",
            "private void installLibraryTheme(",
            "private void applyLibraryTheme(",
            "ThematyStore.installTheme(",
            "if (themeInstalling) return;",  # لا تثبيتاً مزدوجاً
        ):
            self.assertIn(needle, picker)

    def test_picker_backgrounds_section(self):
        """المنتقي: أقسام خلفيات المكتبة وتثبيت المختارة."""
        src = read(PICKER)
        for needle in (
            "private void loadBgSets()",
            "private void showThematyBackgrounds(final String setId)",
            "ThematyStore.sets(",
            "ThematyStore.items(",
            "ThematyStore.install(",
            "ThematyStore.preview(",
            '"خلفيات من مكتبة ثيماتي"',
        ):
            self.assertIn(needle, src)

    def test_old_method_gone_everywhere(self):
        """الطريقة السابقة أُبعدت كلياً: لا بحث ولا كتالوج ولا استعلامات مدمجة."""
        for path in (STUDIO, PICKER, STORE, THEMATY):
            code = code_of(path)
            for gone in (
                "OnlineAssetStore.search(",
                "DownloadCatalog",
                "FALLBACK_FRAME_QUERIES",
                "FALLBACK_FX_QUERIES",
                "FALLBACK_BG_QUERIES",
                "commons.wikimedia.org",
                "openverse",
                "wallhaven",
            ):
                self.assertNotIn(gone, code, "%s يحوي %s" % (path.name, gone))
        self.assertFalse((SETTINGS / "DownloadCatalog.java").exists())
        self.assertFalse((ROOT / "app/src/main/assets/theme_downloads").exists())

    def test_reload_paths_on_initial_failure(self):
        """فشل تحميل الأقسام الأول يعرض ملاحظة نقرها يعيد المحاولة."""
        for src, flag in ((read(STUDIO), "frameReload"), (read(PICKER), "bgReload")):
            self.assertIn(flag, src)
            self.assertIn("loadFrameSets();", read(STUDIO))
            self.assertIn("loadBgSets();", read(PICKER))

    def test_no_lambdas_in_new_code(self):
        for path in (THEMATY, ZIPR, STUDIO, PICKER):
            self.assertNotIn("->", code_of(path), "Lambda في %s" % path.name)


if __name__ == "__main__":
    unittest.main()
