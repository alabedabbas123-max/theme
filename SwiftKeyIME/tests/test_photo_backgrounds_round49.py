# -*- coding: utf-8 -*-
"""Round 49 contract: real photo backgrounds replace the gradient shapes.

Owner request: تخلص من هذه الأشكال واستبدلها بصور للطبيعة وصور مشاهير وصور
رومانسية لحبيبين وصور ورود وصور وردية — أنشئ 10 صور مختلفة.
"""
import glob
import hashlib
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
RES = ROOT / "app/src/main/res"
SKINS = RES / "drawable-nodpi"
STUDIO = JAVA / "settings/CustomThemeActivity.java"
PICKER = JAVA / "settings/CustomBackgroundActivity.java"

PHOTOS = [
    "custom_bg_nature_lake",
    "custom_bg_nature_forest",
    "custom_bg_star_male",
    "custom_bg_star_female",
    "custom_bg_love_sunset",
    "custom_bg_love_hands",
    "custom_bg_roses_red",
    "custom_bg_roses_pink",
    "custom_bg_pink_sky",
    "custom_bg_pink_soft",
]
KEPT_THEME_BGS = [
    "custom_bg_aurora", "custom_bg_sunset", "custom_bg_ocean", "custom_bg_rose",
    "custom_bg_graphite", "custom_bg_lavender", "custom_bg_forest",
]


def read(path):
    return path.read_text(encoding="utf-8")


def balanced(path):
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
    code = "".join(out)
    return code.count("{") == code.count("}") and code.count("(") == code.count(")")


class PhotoBackgroundsRound49(unittest.TestCase):
    def test_ten_real_photo_webps(self):
        """عشر صور WebP فوتوغرافية 1536×1024، متمايزة، وعالية التفاصيل
        (ليست تدرجات مسطحة)."""
        from PIL import Image
        hashes = set()
        for name in PHOTOS:
            path = SKINS / f"{name}.webp"
            self.assertTrue(path.exists(), f"{name}.webp مفقود")
            im = Image.open(path).convert("RGB")
            self.assertEqual((1536, 1024), im.size, name)
            small = im.resize((64, 42))
            px = list(small.getdata())
            mean = sum(sum(p) for p in px) / len(px)
            var = sum((sum(p) - mean) ** 2 for p in px) / len(px)
            self.assertGreater(var, 3000, f"{name}: يبدو مسطحاً كالتدرج القديم")
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            self.assertNotIn(digest, hashes, f"{name}: صورة مكررة")
            hashes.add(digest)

    def test_shapes_are_gone(self):
        """الأشكال القديمة محذوفة: لا kpop ولا romantic ولا التدرجات الخمس
        غير المستخدمة — وتبقى السبع التي تستخدمها ثيمات فعلية."""
        files = {Path(f).stem for f in glob.glob(str(SKINS / "custom_bg_*"))}
        for gone in ("mint", "gold", "midnight", "sky", "coral"):
            self.assertNotIn(f"custom_bg_{gone}", files, gone)
        self.assertFalse([f for f in files if "kpop" in f or "romantic_" in f])
        for keep in KEPT_THEME_BGS:
            self.assertIn(keep, files, keep)
        for name in PHOTOS:
            self.assertIn(name, files, name)
        self.assertEqual(17, len(files), "الجرد: 7 ثيمات + 10 صور")

    def test_theme_backdrops_survive(self):
        """الثيمات السبع المستخدمة للتدرجات القديمة لم تُمس."""
        src = read(JAVA / "theme/KeyboardTheme.java")
        for tid, bg in (
            ("dark_minimal", "custom_bg_graphite"),
            ("rose_gold", "custom_bg_rose"),
            ("ocean_night", "custom_bg_ocean"),
            ("purple_waves", "custom_bg_lavender"),
            ("green_matrix", "custom_bg_forest"),
            ("sunset_palms", "custom_bg_sunset"),
            ("pearl", "custom_bg_aurora"),
        ):
            self.assertIn(f'"{bg}").withKeySkin("{tid}")', src, tid)

    def test_picker_shows_five_arabic_categories(self):
        """المنتقي: خمس فئات عربية (طبيعة/مشاهير/رومانسي/ورود/وردي) بصورتين
        لكل فئة — لا مجموعات kpop/romantic الوهمية ولا مولّد الأسماء."""
        src = read(PICKER)
        for title in ("طبيعة", "مشاهير", "رومانسي", "ورود", "وردي"):
            self.assertIn(f'addCategory("{title}"', src, title)
        self.assertEqual(5, src.count('addCategory("'))  # فئات فقط لا intent.addCategory
        self.assertNotIn("generatedNames", src)
        self.assertNotIn("K-Pop", src)
        self.assertNotIn("custom_bg_kpop_", src)
        self.assertNotIn("custom_bg_romantic_", src)
        self.assertNotIn('"custom_bg_aurora"', src)  # لا تدرجات في المنتقي
        # خليتان كبيرتان وواضحتان لكل فئة
        self.assertIn("grid.setColumnCount(3);", src)
        self.assertIn("/ 3;", src)
        for name in PHOTOS:
            self.assertIn(f'"{name}"', src, name)

    def test_studio_presets_are_the_photos(self):
        """بانات تبويب الخلفيات في الاستوديو = الصور العشر نفسها."""
        src = read(STUDIO)
        for name in PHOTOS:
            self.assertIn(f'"{name}"', src, name)
            self.assertIn(f"R.drawable.{name}", src, name)
        self.assertNotIn('"custom_bg_mint"', src)
        self.assertNotIn('"custom_bg_coral"', src)
        # الاحتياط الثابت للفتح يبقى (aurora موجودة كملف ثيم)
        self.assertIn('"custom_bg_aurora",\n                .72f)', src)

    def test_no_lambdas_and_balanced(self):
        for path in (PICKER, STUDIO):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")
            self.assertTrue(balanced(path), f"أقواس غير متوازنة في {path.name}")


if __name__ == "__main__":
    unittest.main()
