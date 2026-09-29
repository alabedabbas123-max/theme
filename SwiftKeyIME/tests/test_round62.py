# -*- coding: utf-8 -*-
"""Round 62 — صور أزرار حقيقية + الربط بخط بيانات الممتلكات + شبكة 4 أزرار طولية.

طلب المالك (بلقطتين): «هناك خطا في الازرار حيث انك تعطينا صور عادية وليس صور ازرار
… والصورة الاخرى لكيبورد اخر يجلب صور ازرار حقيقية وايضا اربطها بملفات البيانات
التي في مجلد الممتلكات، ايضا اجعل لستة الازرار تكون بشكل صغير وشكل الزر طولي وليس
عريض حيث يتكون كل سطر من 4 ازرار».

التنفيذ:
1. **أزرار حقيقية**: كل إطار محمّل يُعالَج عبر KeyArtProcessor — نفس خط بيانات
   الممتلكات (assets/theme/kay/key.png): الصور المعتمة العادية تُقصّ لنسبة المفتاح
   (0.63) وتأخذ معالجة الزر ثلاثي الأبعاد (حواف مدورة + لمعة علوية + ظل عمق سفلي +
   حافة مضيئة)، والأزرار الجاهزة ذات الزوايا الشفافة تمر كما هي.
2. **الربط بملفات البيانات**: buttons.json (assets/theme_downloads) صار إصدار 2
   باستعلامات صور أزرار حقيقية (button icon / round button icon / gold button /
   key icon / ornate frame / decorative border) + الخامات التي تُشكَّل أزراراً.
3. **المعاينات**: بطاقات النتائج تعرض shapeButton(الصورة) — زراً حقيقياً لا صورة عادية.
4. **الشبكة**: لستة الإطارات 4 أعمدة، البطاقة طولية بنسبة المفتاح
   (ج69: frameCardHeight = width×KEY_ASPECT + تسمية — بطاقة أفقية أنيقة).
5. **الرسم على المفاتيح**: الإطار المحمّل يُرسم ممطوطاً على وجه الزر كالإطارات
   المدمجة (لا BitmapShader)، والمسطرة بتقطيع ثلاثي، والضغط غلالة داكنة بإزاحة.
"""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"
STUDIO = JAVA / "com/almlk/swiftkey/settings/CustomThemeActivity.java"
VIEW = JAVA / "com/almlk/swiftkey/ime/SmartKeyboardView.java"
KAP = JAVA / "com/almlk/swiftkey/theme/KeyArtProcessor.java"
BUTTONS = ROOT / "app/src/main/assets/theme_downloads/buttons.json"


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


class ButtonsDataRound62(unittest.TestCase):
    def test_buttons_json_is_real_button_queries(self):
        """Round 67: بيانات الأزرار من أرشيف ثيماتي عن بُعد — لا ملف مدمج."""
        thematy = read(JAVA / "com/almlk/swiftkey/settings/ThematyStore.java")
        self.assertIn('JSON_BUTTONS = PREFIX + "downloads/json/buttons.json";', thematy)
        self.assertIn('JSON_BACKGROUNDS = PREFIX + "downloads/json/backgrounds.json";', thematy)
        self.assertIn('JSON_EFFECTS = PREFIX + "downloads/json/effects.json";', thematy)
        # ج68: الثيمات من themes.json (المصدر الوحيد لبيانات الثيمات)
        self.assertIn('JSON_THEMES = PREFIX + "downloads/json/themes.json";', thematy)
        self.assertIn('"tb-" + entry.optString("id")', thematy)
        self.assertIn('"tx-" + entry.optString("id")', thematy)
        self.assertNotIn("theme_downloads", thematy)

    def test_queries_flow_from_data_files(self):
        """العناوين تُقرأ من ملفات البيانات (DownloadCatalog) لا من كود صلب."""
        studio = read(STUDIO)
        thematy = read(JAVA / "com/almlk/swiftkey/settings/ThematyStore.java")
        self.assertIn('JSON_BUTTONS = PREFIX + "downloads/json/buttons.json";', thematy)
        # ج68: الأقسام رقائق في الشريط الثابت — تُبنى من frameSets مباشرة
        self.assertIn("private void buildButtonStrip()", studio)
        self.assertIn("frameSets.get(index).id", studio)
        self.assertNotIn("FALLBACK_FRAME_QUERIES", studio)


class RealButtonRenderingRound62(unittest.TestCase):
    def test_key_art_processor_serves_files(self):
        """fileKeyArt: فكّ ملف محدود الذاكرة + قصّ 0.63 + تشكيل ثلاثي + كاش."""
        src = read(KAP)
        self.assertIn("public static Bitmap fileKeyArt(String path) {", src)
        body = method_body(src, "public static Bitmap fileKeyArt(String path) {")
        self.assertIn('cacheKey = "fkey|" + path', body)
        self.assertIn("decodeFileCapped(path, KEY_MAX_EDGE * 2)", body)
        # Round 69: المعتم يُقصّ لنسبة المفتاح الحقيقية (w/h = 1/KEY_ASPECT)
        self.assertIn("shape3D(fitAspect(decoded, 1f / KEY_ASPECT, KEY_MAX_EDGE))", body)
        # والفن الجاهز (زوايا شفافة) لا يُقصّ إطلاقاً — تحديد حجم فقط
        self.assertIn("out = fitSize(decoded, KEY_MAX_EDGE);", body)
        self.assertIn("needsShaping(decoded)", body)  # الأزرار الجاهزة الشفافة تمر كما هي
        self.assertIn("BitmapFactory.decodeFile(path, probe)", src)  # فحص الحدود قبل الفك

    def test_shape_button_for_previews(self):
        """shapeButton: أي صورة تُعرض زراً — قصّ وتشكيل للمعتمة فقط."""
        src = read(KAP)
        body = method_body(src, "public static Bitmap shapeButton(Bitmap src) {")
        self.assertIn("if (src == null) return null;", body)
        self.assertIn("needsShaping(src)", body)
        self.assertIn("shape3D(fitAspect(src, 1f / KEY_ASPECT, KEY_MAX_EDGE))", body)
        self.assertIn("return fitSize(src, KEY_MAX_EDGE);", body)

    def test_online_frame_renders_like_builtin_frame(self):
        """الإطار المحمّل يُرسم ممطوطاً كالمدمج — لا ظل تغطية (BitmapShader)."""
        src = read(VIEW)
        art = method_body(src, "private Bitmap onlineFrameArt() {")
        self.assertIn("KeyArtProcessor.fileKeyArt(theme.keyFrameUri)", art)
        self.assertNotIn("BitmapFactory.decodeFile(theme.keyFrameUri)", art)
        self.assertNotIn("new BitmapShader", src)
        # Round 69: زر الإنترنت يُرسم بنسبته الأصلية fit-center — بلا تقطيع
        self.assertIn("RectF artRect = fitCenterRect(onlineFrame, face);", src)
        self.assertNotIn("drawFrameSlices(canvas, onlineFrame", src)
        # الضغط: غلالة داكنة وإزاحة — كالإطار المدمج
        self.assertIn("PorterDuffColorFilter(0x42000000", src)
        self.assertIn("frameTextColour", src)


class FourColumnPortraitGridRound62(unittest.TestCase):
    def test_frame_card_height_formula(self):
        """frameCardHeight: عرض ÷ نسبة المفتاح + التسمية — زر طولي."""
        src = read(STUDIO)
        body = method_body(src, "private int frameCardHeight(int width) {")
        # Round 69: بطاقة أفقية صغيرة أنيقة كزر حقيقي (h = w×0.63 + تسمية)
        self.assertIn("Math.round(width * KeyArtProcessor.KEY_ASPECT)", body)

    def test_frames_grid_four_columns(self):
        """لستة الإطارات: 4 أعمدة بفجوات dp(30) — لا 3."""
        src = read(STUDIO)
        elements = method_body(src, "private void renderButtonElements()")
        self.assertIn("(getResources().getDisplayMetrics().widthPixels - dp(24) - dp(30)) / 4;", elements)
        for builder in (
            "private void renderBuiltinFrames(int width)",
            "private void renderShapeCards(int width)",
            "private void addOnlineFrameCard(",
        ):
            body = method_body(src, builder)
            self.assertIn("params.height = frameCardHeight(width);", body, builder)

    def test_press_fx_grid_untouched(self):
        """التفاعلات والمنتقي بقوا 3 أعمدة بأحجامهم."""
        src = read(STUDIO)
        press = method_body(src, "private void buildPressFxPresets()")
        self.assertIn("(getResources().getDisplayMetrics().widthPixels - dp(24) - dp(16)) / 3;", press)
        self.assertIn("params.height = dp(84);", press)
        self.assertIn("params.height = dp(72);", method_body(src, "private void buildPressFxSetCards(int width)"))
        self.assertIn("params.height = dp(88);", method_body(src, "private void addPressFxCard("))
        picker = read(JAVA / "com/almlk/swiftkey/settings/CustomBackgroundActivity.java")
        self.assertIn("(getResources().getDisplayMetrics().widthPixels - dp(20)) / 3", picker)

    def test_headers_and_notes_span_right_columns(self):
        """الفواصل والملاحظات تمتد على عدد أعمدة لستتها."""
        src = read(STUDIO)
        self.assertIn(
            "private void addListHeader(GridLayout grid, String title, int columns) {", src)
        # ج68: فاصل التفاعلات باقٍ — والإطارات بفواصل الشريط الثابت
        self.assertIn('addListHeader(pressFxPresets, "تفاعلات من مكتبة ثيماتي", 3);', src)
        header = method_body(src, "private void addListHeader(")
        self.assertIn("spec(0, columns)", header)
        # Round 66: الامتداد صار مشتقاً من أعمدة الشبكة الفعلية (مانع الانهيار)
        self.assertIn("int span = Math.min(4, framePresets.getColumnCount());",
                      method_body(src, "private TextView onlineFrameNote("))
        self.assertIn("spec(0, 3);", method_body(src, "private TextView pressFxNote("))

    def test_online_cards_show_button_previews(self):
        """بطاقة النتيجة تعرض shapeButton — لا الصورة الخام."""
        body = method_body(read(STUDIO), "private void addOnlineFrameCard(")
        self.assertIn("KeyArtProcessor.shapeButton(picture)", body)
        # Round 69: FIT_CENTER — الزر بشكله الحقيقي داخل بطاقة أفقية أنيقة
        self.assertIn("ImageView.ScaleType.FIT_CENTER);", body)
        self.assertNotIn("ImageView.ScaleType.CENTER_CROP", body)


class DisciplineRound62(unittest.TestCase):
    def test_no_lambdas_in_touched_files(self):
        for path in (STUDIO, VIEW, KAP):
            code = strip_comments(read(path))
            self.assertNotIn("->", code, "Lambda في %s" % path.name)

    def test_assets_data_files_present(self):
        self.assertTrue((JAVA / "com/almlk/swiftkey/settings/ThematyStore.java").exists())
        self.assertTrue((JAVA / "com/almlk/swiftkey/settings/ZipRangeReader.java").exists())
        self.assertTrue((ROOT / "app/src/main/assets/theme/kay/key.png").exists())
        self.assertTrue((ROOT / "app/src/main/assets/theme/kay/config.json").exists())


if __name__ == "__main__":
    unittest.main()
