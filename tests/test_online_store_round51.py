# -*- coding: utf-8 -*-
"""Round 51 contract: مكتبة الأصول أونلاين — إطارات وخلفيات من موقع الإنترنت.

طلب المالك: كلاس يعرض أشكال/إطارات الأزرار من موقع إنترنت في قسم المفاتيح،
والتحميل يحفظ في الذاكرة الداخلية ثم يطبّق (توفير مساحة APK)، وكذلك قسم
خلفيات أونلاين في منتقي الخلفيات. المصدر المعتمد: ويكيميديا كومنز —
واجهة بحث JSON مجانية بلا مفاتيح. لا يُبحث أونلاين عن مشاهير (حقوق الصور).
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
STORE = JAVA / "settings/OnlineAssetStore.java"
THEMATY = JAVA / "settings/ThematyStore.java"
ZIPR = JAVA / "settings/ZipRangeReader.java"
STUDIO = JAVA / "settings/CustomThemeActivity.java"
PICKER = JAVA / "settings/CustomBackgroundActivity.java"
VIEW = JAVA / "ime/SmartKeyboardView.java"
MODEL = JAVA / "theme/KeyboardTheme.java"
REPO = JAVA / "theme/ThemeRepository.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
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


class OnlineStoreRound51(unittest.TestCase):
    # ------------------------------------------------------------ الكلاس المطلوب
    def test_store_exists_with_commons_api(self):
        """Round 67: المتجر ذاكرة داخلية — لا كومنز ولا أي بحث إنترنت."""
        src = read(STORE)
        thematy = read(THEMATY)
        self.assertIn("public static String saveBytes(", src)
        # لا أثر للمصادر القديمة في الكود (التعليقات تُجرد قبل الفحص)
        code = re.sub(r"/\*[\s\S]*?\*/", " ", src)
        code = re.sub(r"//[^\n]*", " ", code)
        for gone in ("commons.wikimedia.org", "openverse", "wallhaven", "HttpURLConnection"):
            self.assertNotIn(gone, code.lower())
        self.assertNotIn("HttpURLConnection", read(STORE))
        self.assertIn("raw.githubusercontent.com/alabedabbas123-max/themes", thematy)

    def test_store_types_and_api(self):
        src = read(STORE)
        self.assertIn('TYPE_FRAME = "frame"', src)
        self.assertIn('TYPE_BACKGROUND = "background"', src)
        self.assertIn('TYPE_EFFECT = "effect"', src)
        for api in (
            "public static String saveBytes(",
            "public static List<Item> saved(",
            "public static Item savedItem(",
            "public static void remove(",
            "public static Bitmap decodeBytes(",
            "public static Bitmap decodeFile(",
        ):
            self.assertIn(api, src)

    def test_store_network_discipline(self):
        """الشبكة كلها في قارئ الزيب — أدب مهل وسقوف، والمتجر بلا شبكة."""
        zipr = read(ZIPR)
        self.assertIn("HttpURLConnection", zipr)
        self.assertIn("setConnectTimeout(12000);", zipr)
        self.assertIn("setReadTimeout(25000);", zipr)
        self.assertIn('setRequestProperty("User-Agent"', zipr)
        self.assertIn("MAX_FILE_BYTES", zipr) # سقف ملف واحد
        self.assertNotIn("HttpURLConnection", read(STORE))

    def test_store_saves_into_private_storage(self):
        src = read(STORE)
        # التخزين داخل dirs خاصة بالنوع في ذاكرة التطبيق — لا داخل APK
        self.assertIn('context.getDir("online_" + type, Context.MODE_PRIVATE)', src)
        self.assertIn('"index_" + type', src) # فهرس JSON دائم في SharedPreferences

    def test_store_thumb_derivative(self):
        """المعاينات مفكوكة بمقياس مضبوط — لا صور ضخمة في الذاكرة."""
        src = read(STORE)
        self.assertIn("public static Bitmap decodeBytes(", src)
        self.assertIn("public static Bitmap decodeFile(", src)
        self.assertIn("inSampleSize", src)
        self.assertIn("public static void previewSource(", read(THEMATY))

    # ------------------------------------------------------------ البيان والنموذج
    def test_manifest_internet_permission(self):
        src = read(MANIFEST)
        self.assertIn("android.permission.INTERNET", src)

    def test_theme_model_carries_uri(self):
        src = read(MODEL)
        self.assertIn("public final String keyFrameUri;", src)
        self.assertIn("public KeyboardTheme withKeyFrameUri(String frameUri)", src)
        # السلاسل تنسخ الإطار الأونلاين ولا تسقطه — وRound56 أضاف تفاعل الضغط
        # وRound69 أضاف مقياسي عرض/ارتفاع الزر في آخر كل نسخة
        self.assertIn(
            "keyFrameUri,\n        pressEffect, pressEffectUri,\n        keyWidthScale, keyHeightScale);",
            src.replace("\r", ""))
        self.assertIn(
            "keyFrame, value,\n        pressEffect, pressEffectUri,\n        keyWidthScale, keyHeightScale);",
            src.replace("\r", ""))
        self.assertIn(
            "pressEffect, value,\n        keyWidthScale, keyHeightScale);", src)
        # السلسلة الفارغة = لا إطار أونلاين
        self.assertIn('frameUri == null ? "" : frameUri', src)

    def test_repository_persists_uri(self):
        src = read(REPO)
        self.assertIn('p.getString(base + "key_frame_uri", "")', src)
        self.assertIn('.putString(b + "key_frame_uri", theme.keyFrameUri)', src)
        self.assertIn('"key_frame_uri"', src)

    # ------------------------------------------------------------ الرسم في العرض
    def test_view_renders_downloaded_frame_as_real_button(self):
        """Round 62: إطار الإنترنت يُعالَج زراً حقيقياً عبر KeyArtProcessor."""
        src = read(VIEW)
        self.assertIn("onlineFrameArt()", src)
        self.assertIn("KeyArtProcessor.fileKeyArt(", src)  # خط بيانات الممتلكات نفسه
        # Round 69: الزر يُرسم بشكله الحقيقي — fitCenterRect داخل خانة الوجه
        # (نسبة الأصل محفوظة، بلا مطّ وبلا زوائد مستطيلة على الحدود)
        self.assertIn("RectF artRect = fitCenterRect(onlineFrame, face);", src)
        self.assertIn("canvas.drawBitmap(onlineFrame, null, artRect, paint);", src)
        self.assertNotIn("drawFrameSlices(canvas, onlineFrame", src)
        self.assertIn("2.05f", src)
        self.assertNotIn("new BitmapShader", src)  # عصر التغطية بالظل انتهى
        # لون الحروف يُشتق من سطوع الصورة المحمّلة
        self.assertIn("frameTextColour", src)
        # الكاش يُفرَّغ عند تبديل الثيم
        self.assertIn("onlineFrameCache.clear()", src)
        # الأونلاين لا يعمل فوق جلد الثيم ولا فوق إطار مدمج
        self.assertIn("frameBitmap == null ? onlineFrameArt() : null", src)

    def test_view_falls_back_when_undecodable(self):
        src = read(VIEW)
        # فشل فك الترميز يعيد null فتسقط المفاتيح للوجه الكلاسيكي
        # (Round 62: الفك عبر KeyArtProcessor.fileKeyArt — decodeFileCapped يعيد null عند الفشل)
        self.assertIn("cached = KeyArtProcessor.fileKeyArt(theme.keyFrameUri);", src)
        self.assertIn("if (cached == null) return null;", src)

    # ------------------------------------------------------------ الاستوديو (الإطارات)
    def test_layout_has_online_strip(self):
        """Round 59/68: لستة واحدة — عناصر قسم الأزرار (مدمج ونتائج المكتبة)
        في شبكة button_elements_grid نفسها (4 أعمدة) بلا شبكات منفصلة."""
        src = read(LAYOUT)
        self.assertIn('android:id="@+id/button_elements_grid"', src)
        # الشبكات المنفصلة أُزيلت بالدمج
        self.assertNotIn('android:id="@+id/online_frame_queries"', src)
        self.assertNotIn('android:id="@+id/online_frame_grid"', src)
        # الشبكة داخل تراكب قسم الأزرار الثابت لا خارجه
        keys = src.index('android:id="@+id/section_keys_fixed"')
        grid = src.index('android:id="@+id/button_elements_grid"')
        end = src.index("</ScrollView>", keys)
        self.assertTrue(keys < grid < end)

    def test_studio_wires_online_strip(self):
        src = read(STUDIO)
        # Round 59: اللستة الموحدة — الحقول تشير كلها إلى شبكة الإطارات نفسها
        self.assertIn("onlineFrameQueries = framePresets;", src)
        self.assertIn("onlineFrameGrid = framePresets;", src)
        self.assertIn("R.id.button_elements_grid", src)  # ج68: شبكة عناصر القسم
        # Round 67: الأقسام من أرشيف ثيماتي عن بُعد بطلبات النطاق
        self.assertIn("ThematyStore.sets(", src)
        self.assertIn("frameSets", src)
        # جلب كسول عند أول فتح لقسم الأزرار — ج68
        self.assertIn("buttonsLoaded", src)
        self.assertIn("loadFrameSets()", src)
        self.assertIn("private void showThematyFrames(final String setId)", src)

    def test_studio_apply_flow(self):
        src = read(STUDIO)
        # التطبيق: مسار الملف + إسقاط الإطار المدمج والجلد
        self.assertIn("keyFrameUri = path;", src)
        self.assertIn("applyOnlineFrame", src)
        apply_block = src[src.index("private void applyOnlineFrame"): src.index("private void buildFontPresets")]
        self.assertIn("keyFrame = 0;", apply_block)
        self.assertIn('sessionSkin = "";', apply_block)
        # والمعاينة تحمل الإطار الأونلاين
        self.assertIn(".withKeyFrameUri(keyFrameUri)", src)

    def test_studio_builtin_frame_clears_uri(self):
        src = read(STUDIO)
        i = src.index("private void renderBuiltinFrames")
        block = src[i : src.index("private void addListHeader", i)]
        self.assertIn('keyFrameUri = "";', block)

    def test_studio_queries_are_textures_not_celebrities(self):
        src = read(STUDIO)
        for banned in ("celebrity", "actor", "singer", "kpop"):
            self.assertNotIn(banned, src.lower())
        self.assertNotIn("FALLBACK_FRAME_QUERIES", src)

    # ------------------------------------------------------------ منتقي الخلفيات
    def test_picker_has_online_section(self):
        src = read(PICKER)
        self.assertIn("خلفيات من مكتبة ثيماتي", src)
        self.assertIn("ThematyStore.sets(", src)
        self.assertIn("bgSets", src)
        self.assertIn("addOnlineCategory", src)
        self.assertIn("TYPE_BACKGROUND", src)
        # النقر يحمّل ثم يُعيد مساراً محلياً بصيغة file://
        self.assertIn('returnBackground("file://" + local.path)', src)
        self.assertIn('returnBackground("file://" + path)', src)
        # مصغرات الإنترنت تُعادد كالمدمجة
        self.assertIn("onlineThumbnails", src)

    def test_picker_queries_not_celebrities(self):
        """الحراسة على كود المصدر الجديد (المكتبة) — الفئات المدمجة شأن قديم."""
        for path in (THEMATY, ZIPR):
            src = read(path)
            for banned in ("celebrity", "actor", "singer", "kpop"):
                self.assertNotIn(banned, src.lower())

    # ------------------------------------------------------------ نظافة عامة
    def test_no_lambdas_and_balanced(self):
        for path in (STORE, STUDIO, PICKER, VIEW, MODEL, REPO):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, "Lambda في %s" % path.name)
            self.assertTrue(balanced(path), "أقواس غير متوازنة في %s" % path.name)

    def test_round48_chain_guard_still_holds(self):
        # سلسلة الجلسة الختامية لم تتغير ترتيبها: withKeyFrame ثم withKeySkin
        src = read(STUDIO)
        self.assertIn(".withKeyFrame(keyFrame)", src)
        self.assertIn(".withKeySkin(sessionSkin);", src)


if __name__ == "__main__":
    unittest.main()
