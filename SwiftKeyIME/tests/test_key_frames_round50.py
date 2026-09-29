# -*- coding: utf-8 -*-
"""Round 50 contract: قسم المفاتيح — أشكال + إطارات احترافية + شفافية.

طلب المالك (لقطة Screenshot_20260924-231525.png): تنظيف قسم المفاتيح،
شريط أول بالأشكال (زر دائري/مربع/خفيف...) كما في الصورة، وتحته 10 إطارات
احترافية للأزرار «يجب أن تكون الإطارات صوراً وليس ملفات shape» تتبدل
ديناميكياً مع الشكل المختار، وشريط تمرير يتحكم بشفافية الزر المختار.
"""
import hashlib
import re
import unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
THEME = JAVA / "theme"
VIEW = JAVA / "ime/SmartKeyboardView.java"
STUDIO = JAVA / "settings/CustomThemeActivity.java"
REPO = THEME / "ThemeRepository.java"
MODEL = THEME / "KeyboardTheme.java"
FRAMES = THEME / "KeyFrames.java"
LAYOUT = ROOT / "app/src/main/res/layout/activity_custom_theme.xml"
NODPI = ROOT / "app/src/main/res/drawable-nodpi"

IDS = ["neon", "gold", "glass", "silver", "rose",
       "carbon", "emerald", "sapphire", "royal", "sunset"]
RADII = [0, 5, 10, 17, 28, 36]


def read(path):
    return path.read_text(encoding="utf-8")


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


def method_body(src, signature):
    i = src.index(signature)
    j = src.index("\n  }", i)
    return src[i:j]


class KeyFramesRound50(unittest.TestCase):
    # ------------------------------------------------------------ الصور
    def test_sixty_real_frame_images(self):
        """الإطارات العشرة صور PNG حقيقية (وليست shape/xml) — كل إطار في
        الأشكال الستة، 240×144 RGBA، والتصاميم متمايزة في كل شكل."""
        seen = {}
        for art in IDS:
            for r in RADII:
                path = NODPI / ("keyframe_%s_%d.png" % (art, r))
                self.assertTrue(path.exists(), "مفقود: %s" % path.name)
                data = path.read_bytes()
                self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n", "ليس PNG: %s" % path.name)
                im = Image.open(path)
                self.assertEqual((240, 144), im.size, path.name)
                self.assertIn(im.mode, ("RGBA", "LA"), "بلا قناة ألفا: %s" % path.name)
                px = im.convert("RGBA").load()
                # الهوامش شفافة (وهج النيون قد يلامسها بخفوت) والوجه حاضر
                self.assertLess(px[0, 0][3], 40, path.name)
                self.assertGreater(px[120, 72][3], 15, path.name)
                if art == "gold":
                    self.assertEqual(255, px[120, 72][3], path.name)
                seen[(art, r)] = hashlib.sha256(data).hexdigest()
        # التصاميم العشرة مختلفة في كل شكل
        for r in RADII:
            hashes = [seen[(art, r)] for art in IDS]
            self.assertEqual(10, len(set(hashes)), "تصاميم متطابقة عند r=%d" % r)
        # 28 و36 كبسولة (قصة drawRoundRect نفسها)
        self.assertEqual(seen[("gold", 28)], seen[("gold", 36)])
        self.assertNotEqual(seen[("gold", 0)], seen[("gold", 17)])

    def test_shape_outline_images(self):
        """صور مخططات الأشكال الستة موجودة ومتمايزة (شريط الأشكال الأول)."""
        hashes = []
        for r in RADII:
            path = NODPI / ("keyshape_%d.png" % r)
            self.assertTrue(path.exists(), "مفقود: %s" % path.name)
            im = Image.open(path)
            # Round 55: المخططات طولية (واقفة) 144×240 — شكل الزر واقف لا أفقي
            self.assertEqual((144, 240), im.size, path.name)
            self.assertGreater(im.size[1], im.size[0], path.name)
            self.assertIn(im.mode, ("RGBA", "LA"), path.name)
            hashes.append(hashlib.sha256(path.read_bytes()).hexdigest())
        # 0/5/10/17 متمايزة، و28≡36 كبسولة
        self.assertEqual(4, len(set(hashes[:4])))
        self.assertEqual(hashes[4], hashes[5])

    # ------------------------------------------------------------ النموذج
    def test_theme_model_has_frame(self):
        src = read(MODEL)
        self.assertIn("public final int keyFrame;", src)
        self.assertIn("public KeyboardTheme withKeyFrame(int frame) {", src)
        self.assertIn("keyFrame = Math.max(0, Math.min(KeyFrames.COUNT, frame));", src)
        # النسخ تحفظ الإطار — وRound56 أضاف تفاعل الضغط، وRound69 مقياسي الزر
        self.assertIn(
            "keyFrameUri,\n        pressEffect, pressEffectUri,\n        keyWidthScale, keyHeightScale);",
            src.replace("\r", ""))

    def test_repository_persists_frame(self):
        src = read(REPO)
        self.assertIn('.putInt(b + "key_frame", theme.keyFrame)', src)
        self.assertIn('p.getInt(base + "key_frame", 0)', src)
        self.assertIn('"key_frame"', src)  # قائمة الحذف

    # ------------------------------------------------------------ الرسم
    def test_renderer_draws_frame_images(self):
        """الكيبورد يرسم وجه الزر من صورة الإطار (بلا جلد)، والمفاتيح
        العريضة تُقطَّع ثلاثياً، والضغط غلالة داكنة فوق الصورة نفسها."""
        src = read(VIEW)
        self.assertIn("private Bitmap frameArt() {", src)
        self.assertIn("frameArtCache", src)
        self.assertIn("KeyFrames.artName(theme.keyFrame, theme.keyRadiusDp)", src)
        self.assertIn("KeyFrames.textColor(theme.keyFrame)", src)
        self.assertIn("canvas.drawBitmap(frameBitmap, null, face, paint);", src)
        self.assertIn("private void drawFrameSlices(Canvas canvas, Bitmap art, RectF face, Paint paint) {", src)
        self.assertIn("face.width() > face.height() * 2.05f", src)
        self.assertIn("PorterDuffColorFilter", src)
        # عقد الجلود قائم: مسار الجلد بلا طبقات ثقيلة (حارس 41 كما هو)
        branch = src[src.index("Bitmap skinFace = keySkinFace"):src.index("} else {")]
        self.assertNotIn("drawRoundRect", branch)
        self.assertNotIn("setShadowLayer", branch)

    # ------------------------------------------------------------ الواجهة
    def test_keys_section_layout(self):
        src = read(LAYOUT)
        # Round 68: قسم الأزرار تراكب ثابت — شريط الشفافية وشريط الأقسام
        # أعلى الشبكة، وعناصر القسم المختار تحت عمرها بتمرير مستقل.
        section = src[src.index('android:id="@+id/section_keys_fixed"'):]
        section = section[: section.index("</ScrollView>")]
        strip = section[: section.index('android:id="@+id/button_elements_grid"')]
        self.assertIn('android:id="@+id/key_opacity"', strip)  # الشفافية ثابتة أعلى
        self.assertIn('android:id="@+id/button_sections_strip"', strip)  # الأقسام ثابتة أعلى
        self.assertIn('android:id="@+id/button_elements_grid"', section)
        self.assertNotIn("key_radius", section)
        self.assertIn("شفافية الزر المختار", strip)
        # Round 66/68: لستة العناصر 4 أعمدة (العرض يقسم على 4)
        self.assertIn('android:columnCount="4"', section)
        # الشريطان داخل قسم الأزرار الثابت لا في تمرير المحتوى الرئيسي
        head = src[: src.index('android:id="@+id/section_keys_fixed"')]
        self.assertNotIn('android:id="@+id/key_opacity"', head)

    def test_studio_shape_strip(self):
        """الشريط الأول: بطاقات دائرية بصور المخطط + حرف ع، والاختيار
        يعيد بناء الإطارات (ديناميكية الشكل) ويحدّث المعاينة."""
        body = method_body(read(STUDIO), "private void renderShapeCards(int width)")
        # Round 55: بطاقة طولية مدورة (لا دائرية) ورسم واقف dp(40)×dp(56)
        self.assertIn("panel.setCornerRadius(dp(12));", body)  # ج68: موحدة كبطاقات اللستة
        self.assertNotIn("GradientDrawable.OVAL", body)
        self.assertIn("params.width = width;", body)  # خلية اللستة — بطاقة طولية
        self.assertIn("new FrameLayout.LayoutParams(dp(40), dp(56));", body)
        self.assertIn("KeyFrames.shapeName(value)", body)
        self.assertIn('letter.setText("ع");', body)
        self.assertIn("renderButtonElements(); // الإطارات صور تتبع الشكل المختار", body)
        self.assertIn("sessionSkin = \"\";", body)
        self.assertIn("0xffffc400", body)  # تمييز المختار كهرماني
        # لا رقائق نصية قديمة ولا منزلق قطر
        self.assertNotIn("radiusValue", body)

    def test_studio_frame_cards(self):
        """عشرة بطاقات إطارات بصور keyframe_* بنسخة الشكل الحالي، ووسم
        عربي، ونقرها يطبق الإطار ويسقط الجلد."""
        body = method_body(read(STUDIO), "private void renderBuiltinFrames(int width)")
        self.assertIn("index < KeyFrames.COUNT", body)
        self.assertIn("KeyFrames.artName(frameId, radius)", body)
        self.assertIn("label.setText(KeyFrames.NAMES[index]);", body)
        self.assertIn("keyFrame = frameId;", body)
        self.assertIn("sessionSkin = \"\";", body)
        self.assertIn("0xffffc400", body)

    def test_transparency_slider(self):
        """شريط التمرير الوحيد يتحكم بشفافية الزر المختار (صفر = معتم)."""
        src = read(STUDIO)
        body = method_body(src, "private void configureSliders()")
        self.assertIn("opacity = 1f - progress / 100f;", body)
        self.assertIn('"شفافية الزر المختار: " + progress', body)
        self.assertIn('opacityValue.setText("شفافية الزر المختار: "', src)
        self.assertNotIn("R.id.key_radius", src)
        self.assertNotIn("وضوح خلفية الأزرار", src)
        self.assertNotIn("قطر حواف الزر", src)

    def test_shape_names_and_frame_state(self):
        src = read(STUDIO)
        self.assertIn('"مربع"', src)
        self.assertNotIn('"حاد"', src)
        self.assertIn("keyFrame = theme.keyFrame;", src)
        self.assertIn(".withKeyFrame(keyFrame)", src)
        # ج68: شبكة عناصر قسم الأزرار — الأشكال والمدمج وأقسام ثيماتي فيها
        self.assertIn(
            "framePresets = (GridLayout) findViewById(R.id.button_elements_grid);", src)

    def test_keyframes_catalog(self):
        src = read(FRAMES)
        self.assertIn("public static final int COUNT = 10;", src)
        self.assertIn("keyframe_", src)
        self.assertIn("keyshape_", src)
        self.assertEqual(10, len(re.findall(r'"(نيون|ذهبي|زجاجي|فضي|وردي|كربون|زمردي|ياقوتي|ملكي|غروب)"', src)))
        self.assertIn("nearestRadius", src)

    # ------------------------------------------------------------ نظافة
    def test_no_lambdas_and_balanced(self):
        for path in (FRAMES, MODEL, REPO, VIEW, STUDIO):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, "Lambda في %s" % path.name)
            self.assertTrue(balanced(path), "أقواس غير متوازنة في %s" % path.name)


if __name__ == "__main__":
    unittest.main()
