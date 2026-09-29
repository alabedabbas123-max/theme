# -*- coding: utf-8 -*-
"""Round 45 contract: the key-art helper class + WebP backgrounds.

Owner request: كلاس مساعد يضبط صورة الزر لتكون ثلاثية الأبعاد بتناسب مع حجم أزرار
الكيبورد — إذا أُضيفت صورة كبيرة/غير مناسبة في مجلد الممتلكات كثيم مستورد ضبط
أبعادها وشكلها بشكل مثالي وحفظها في الذاكرة بحيث يستدعيها الثيم جاهزة بلا إعادة
رسم كل مرة مع تقليل استهلاك الذاكرة — والخلفيات بصيغة webp لتقليل الحجم مع
المحافظة على الدقة.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
RES = ROOT / "app/src/main/res"
SKINS = RES / "drawable-nodpi"
ASSETS = ROOT / "app/src/main/assets/theme"
KAP = JAVA / "theme/KeyArtProcessor.java"
LIB = JAVA / "theme/AssetThemeLibrary.java"


def read(path):
    return path.read_text(encoding="utf-8")


class KeyArtRound45(unittest.TestCase):
    def test_helper_class_exists_pure_java(self):
        self.assertTrue(KAP.exists())
        self.assertTrue(read(KAP).startswith("package com.almlk.swiftkey.theme;"))
        code = re.sub(r"/\*[\s\S]*?\*/", " ", read(KAP))
        code = re.sub(r"//[^\n]*", " ", code)
        self.assertNotIn("->", code, "Lambda في KeyArtProcessor")
        self.assertNotIn("val ", code, "صيغة Kotlin")

    def test_public_api(self):
        src = read(KAP)
        for token in (
            "public static Bitmap keyArt(Context context, String path)",
            "public static Bitmap spaceArt(Context context, String path)",
            "public static Bitmap pressTwin(Context context, String path)",
            "public static Bitmap background(Context context, String path)",
        ):
            self.assertIn(token, src)

    def test_bounded_lru_cache_no_reprocessing(self):
        """كاش LRU محدود: المفتاح "variant|aspect|path" — نفس الصورة تُجلب جاهزة
        ولا تُعالج ثانية، والطرد لا يعيد تدوير صور مشتركة."""
        src = read(KAP)
        self.assertIn("private static final LinkedHashMap<String, Bitmap> CACHE =", src)
        self.assertIn("new LinkedHashMap<String, Bitmap>(16, 0.75f, true)", src)
        self.assertIn("protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest)", src)
        self.assertIn("return size() > MAX_CACHE;", src)
        self.assertIn("static final int MAX_CACHE = 32;", src)
        self.assertIn("synchronized (CACHE)", src)
        # كل مسار عام يفحص الكاش أولاً قبل أي فك/رسم
        for method in ("pressTwin", "background"):
            i = src.index(f"public static Bitmap {method}")
            body = src[i:src.index("}", src.index("CACHE.get", i))]
            self.assertIn("CACHE.get", body, method)
        self.assertNotIn(".recycle()", src, "لا إعادة تدوير داخل المعالج — الصور مشتركة")

    def test_3d_shape_treatment_for_unsuitable_art(self):
        """الفن المسطح (مستطيل معتم = صورة غير مناسبة) يأخذ المعالجة الثلاثية:
        قصّ زوايا مستدير + لمعة علوية + ظل عمق سفلي + حافة داخلية فاتحة."""
        src = read(KAP)
        self.assertIn("private static boolean needsShaping(Bitmap src)", src)
        self.assertIn("private static Bitmap shape3D(Bitmap src)", src)
        self.assertIn("clip.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);", src)
        self.assertIn("canvas.clipPath(clip);", src)
        self.assertIn("new LinearGradient(", src)
        self.assertIn("0x59FFFFFF, 0x00FFFFFF", src)  # اللمعة العلوية
        self.assertIn("0x00000000, 0x4D000000", src)  # ظل العمق السفلي
        self.assertIn("rim.setColor(0x33FFFFFF);", src)  # الحافة الداخلية
        # والفن المُشكّل مسبقاً (زوايا شفافة كأزرار المرجع) يمر بلا معالجة مزدوجة
        self.assertIn("if (((corners[i] >>> 24) & 0xff) < 200) return false;", src)

    def test_library_delegates_to_helper(self):
        lib = read(LIB)
        self.assertIn("KeyArtProcessor.background(context, path)", lib)
        self.assertIn("KeyArtProcessor.spaceArt(context, spec.space)", lib)
        self.assertIn("KeyArtProcessor.keyArt(context, spec.key)", lib)
        self.assertIn("KeyArtProcessor.pressTwin(context, spec.key)", lib)
        # لا معالجة صور مكررة داخل المكتبة
        for gone in ("private static Bitmap decodeCapped(", "private static Bitmap normalized(",
                     "private static Bitmap darkened(", "BITMAPS"):
            self.assertNotIn(gone, lib, gone)

    def test_backgrounds_are_webp_with_quality(self):
        """كل الخلفيات webp: أبعادها محفوظة وأحجامها أصغر من jpeg المكافئ."""
        from PIL import Image
        originals = {  # أحجام jpeg المسجلة قبل التحويل
            "bkg_lux_gold": 506732, "bkg_lux_pink": 48139,
            "bkg_lux_silver": 135911, "bkg_lux_heart": 101264,
        }
        for name, jpg_size in originals.items():
            webp = SKINS / f"{name}.webp"
            self.assertTrue(webp.exists(), name)
            self.assertFalse((SKINS / f"{name}.jpg").exists(), name)
            im = Image.open(webp)
            self.assertEqual((1536, 1024), im.size, name)  # الدقة محفوظة
            self.assertLess(webp.stat().st_size, jpg_size,
                            f"{name}: webp أكبر من jpeg")
        for folder in ("kay", "kay1"):
            bg = ASSETS / folder / "bg.webp"
            self.assertTrue(bg.exists(), folder)
            self.assertFalse((ASSETS / folder / "bg.jpg").exists(), folder)
            self.assertEqual((1080, 720), Image.open(bg).size, folder)

    def test_webp_accepted_in_scanner(self):
        """الماسح يقبل webp ضمن صور الثيمات المستوردة."""
        src = read(LIB)
        self.assertIn('name.endsWith(".webp")', src)

    def test_demo_configs_point_to_webp(self):
        import json
        for folder in ("kay", "kay1"):
            cfg = json.loads((ASSETS / folder / "config.json").read_text(encoding="utf-8"))
            self.assertEqual("bg.webp", cfg["images"]["keyboard"], folder)
            self.assertTrue((ASSETS / folder / "bg.webp").exists(), folder)

    def test_press_twin_and_fallback_survive(self):
        """عقدا الضغط والاحتياط باقيان عبر الكلاس الجديد."""
        src = read(KAP)
        self.assertIn("* 55 / 100", src)
        self.assertIn("Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)", src)
        lib = read(LIB)
        self.assertIn('if (spec.space.length() > 0) {', lib)
        self.assertIn("// المتطلب: لا أيقونة مسافة → خلفية الزر العادية نفسها عليها", lib)

    def test_no_lambdas_in_round45_files(self):
        for path in (KAP, LIB):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")


if __name__ == "__main__":
    unittest.main()
