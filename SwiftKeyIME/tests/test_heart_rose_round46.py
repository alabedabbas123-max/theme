# -*- coding: utf-8 -*-
"""Round 46 contract: the rose heart theme + a real pink background + unified
toolbar/suggestion colors in imported themes.

Owner request: استبدل أزرار القلوب بزر أحمر أنيق في رأسه وردة صغيرة، واجعل
الثيم كاملاً بشكل مماثل (لا ألوان مختلفة أعلى/أسفل/وسط) والحروف والعناصر
والاقتراحات بيضاء — واضبط الثيم الوردي ليحتوي على خلفية مناسبة لا بيضاء
مع أزرار شريط الاقتراحات مثل أزرار الكيبورد — وأصلح الألوان في الثيمات
المستوردة حيث كانت عناصر شريط الأدوات تخالف لون حروف شريط الاقتراحات.
"""
import hashlib
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
RES = ROOT / "app/src/main/res"
SKINS = RES / "drawable-nodpi"
ASSETS = ROOT / "app/src/main/assets/theme"
THEME = JAVA / "theme/KeyboardTheme.java"
PART2A = JAVA / "ime/AlmlkImeRuntimePart2A.java"


def read(path):
    return path.read_text(encoding="utf-8")


def px(path, x, y):
    from PIL import Image
    return Image.open(path).convert("RGBA").getpixel((x, y))


class HeartRoseRound46(unittest.TestCase):
    # ------------------------------------------------------------ زر الوردة
    def test_rose_button_art(self):
        """زر أحمر أنيق مستدير بوردة صغيرة في رأسه، بنسبة وجه المفتاح الأصلية
        (0.63) فلا تشويه عند الرسم، وزواياه شفافة (فن مُشكَّل مسبقاً)."""
        from PIL import Image
        im = Image.open(SKINS / "keybg_lux_heart_up.png").convert("RGBA")
        w, h = im.size
        self.assertEqual((103, 163), (w, h), "أبعاد زر الوردة")
        self.assertAlmostEqual(w / h, 0.63, delta=0.02)
        p = im.load()
        for corner in ((1, 1), (w - 2, 1), (1, h - 2), (w - 2, h - 2)):
            self.assertEqual(0, p[corner[0], corner[1]][3], f"الزاوية {corner} ليست شفافة")
        body = p[w // 2, int(h * 0.55)]
        rose = p[w // 2, int(h * 0.23)]
        self.assertGreater(body[0], body[1] + 80, "الجسم ليس أحمر")
        self.assertGreater(rose[0], 180, "الوردة ليست زاهية")
        self.assertGreater(rose[1] - body[1], 40, "لا وردة وردية في الرأس")
        # الوردة فوق الحرف: منفصلة عن منتصف الوجه
        self.assertLess(int(h * 0.23) + int(0.18 * h), int(h * 0.5), "الوردة تلامس منطقة الحرف")

    def test_rose_on_every_face_and_press_twin(self):
        """كل الأوجه (رئيسي/معدِّلات/إدخال) تحمل الوردة، والتوأم معتم ×55."""
        from PIL import Image
        for name, size in (("up", (103, 163)), ("fun", (136, 163)),
                           ("enter", (154, 163)), ("press", (103, 163))):
            im = Image.open(SKINS / f"keybg_lux_heart_{name}.png")
            self.assertEqual(size, im.size, name)
        for name in ("up", "fun", "enter"):
            w, h = Image.open(SKINS / f"keybg_lux_heart_{name}.png").size
            body = px(SKINS / f"keybg_lux_heart_{name}.png", w // 2, int(h * 0.55))
            rose = px(SKINS / f"keybg_lux_heart_{name}.png", w // 2, int(h * 0.23))
            self.assertGreater(body[0], body[1] + 80, f"{name}: الجسم ليس أحمر")
            self.assertGreater(rose[1] - body[1], 40, f"{name}: لا وردة")
        cu = px(SKINS / "keybg_lux_heart_up.png", 51, 81)
        cp = px(SKINS / "keybg_lux_heart_press.png", 51, 81)
        self.assertLess(cp[0] + cp[1] + cp[2], (cu[0] + cu[1] + cu[2]) * 0.75, "التوأم ليس معتماً")
        self.assertGreater(cp[3], 200, "توأم الضغط شفاف")

    def test_red_ruler_bar(self):
        """مسطرة القلب حمراء (لا نسخة ذهبية) بنسبة ≥3 فتُرسم مباشرة على شريط
        الاقتراحات وشريط الأدوات — فيتوحد الثيم أعلى وأسفل ووسط."""
        from PIL import Image
        import hashlib
        bar = Image.open(SKINS / "keybg_lux_heart_space.png").convert("RGBA")
        w, h = bar.size
        # Round 47: زر المسافة بنفس شكل الأزرار — نسبة 2.88 لثلاث شرائح
        self.assertLess(w / h, 3.0, "نسبة المسطرة")
        self.assertGreaterEqual(w / h, 2.5, "المسطرة ضيقة")
        p = bar.load()
        self.assertEqual(0, p[2, 2][3], "زاوية المسطرة ليست شفافة")
        mids = [p[x, h // 2] for x in range(40, w - 40, 16)]
        avg = tuple(sum(c[i] for c in mids) // len(mids) for i in range(3))
        self.assertGreater(avg[0], avg[1] + 60, "المسطرة ليست حمراء")
        gold = hashlib.sha256((SKINS / "keybg_lux_gold_space.png").read_bytes()).hexdigest()
        mine = hashlib.sha256((SKINS / "keybg_lux_heart_space.png").read_bytes()).hexdigest()
        self.assertNotEqual(gold, mine, "المسطرة ما تزال نسخة من الذهبية")

    def test_heart_background_deep_red_for_white_text(self):
        """خلفية القلب حمراء عميقة (إضاءتها < 70) فالأبيض عليها واضح."""
        from PIL import Image, ImageStat
        bg = Image.open(SKINS / "bkg_lux_heart.webp").convert("RGB")
        r, g, b = (int(v) for v in ImageStat.Stat(bg).mean)
        lum = (r * 299 + g * 587 + b * 114) // 1000
        self.assertEqual((1536, 1024), bg.size, "دقة الخلفية")
        self.assertLess(lum, 70, "الخلفية فاتحة على الأبيض")
        self.assertGreater(r, g + 60, "الخلفية ليست حمراء")

    def test_heart_palette_white_text_red_surface(self):
        """اللوحة: حروف وعناصر واقتراحات بيضاء، وسطح أحمر يطابق فن المسطرة،
        والأسفل (صف المعدِّلات) أحمر لا ذهبي."""
        src = read(THEME)
        self.assertIn(
            "return lux(0xff550e1c, 0xffc2102e, 0xff8f0d22, 0xffffffff, 0xffeec7ce, 0xffffffff,\n"
            "          0xffc2102e, \"bkg_lux_heart\", \"lux_heart\").withSurface(0xffc2203b);",
            src,
        )
        # suggestionTextColor يُنتج الأبيض: |255 - إضاءة(المفتاح)| ≥ 72
        key_lum = (0xc2 * 299 + 0x10 * 587 + 0x2e * 114) // 1000
        self.assertGreater(abs(255 - key_lum), 72, "الاقتراحات لن تكون بيضاء")
        # لا ذهبي متبق في فرع القلب
        branch = src[src.index('if ("lux_heart".equals(id))'):]
        branch = branch[: branch.index("if (", 10)]
        self.assertNotIn("0xffd9b45c", branch, "ذهبي متبق في فرع القلب")
        self.assertNotIn("0xffd4af37", branch, "لمعة ذهبية متبقية في القلب")

    # ------------------------------------------------------------ الثيم الوردي
    def test_pink_background_is_really_pink(self):
        """خلفية الوردية وردية فعلية لا بيضاء، ومسطرتها مسطرة عريضة مثل
        أزرار الكيبورد فتُرسم مباشرة على شريط الاقتراحات."""
        from PIL import Image, ImageStat
        bg = Image.open(SKINS / "bkg_lux_pink.webp").convert("RGB")
        r, g, b = (int(v) for v in ImageStat.Stat(bg).mean)
        self.assertEqual((1536, 1024), bg.size, "دقة الخلفية")
        self.assertGreater(r, 200, "الخلفية داكنة/بيضاء")
        self.assertGreater(r - g, 55, "الخلفية ليست وردية")
        self.assertGreater(b, 140, "الخلفية ليست وردية")
        src = read(THEME)
        self.assertIn("return lux(0xffe690b4, 0xffe795b8,", src, "الاحتياط الوردي")
        bar = Image.open(SKINS / "keybg_lux_pink_space.png").convert("RGBA")
        w, h = bar.size
        # Round 47: نسبة 2.88 — زر عريض بنفس شكل أزرار الكيبورد
        self.assertLess(w / h, 3.0, "مسطرة الوردية خارج عقد الشرائح")
        self.assertGreaterEqual(w / h, 2.5, "مسطرة الوردية ضيقة")
        p = bar.load()
        mids = [p[x, h // 2] for x in range(40, w - 40, 16)]
        avg = tuple(sum(c[i] for c in mids) // len(mids) for i in range(3))
        self.assertGreater(avg[0], avg[2], "المسطرة ليست وردية")
        self.assertLess(avg[0] - avg[2], 110, "المسطرة حمراء لا وردية")
        # زر الوردية نفسه لم يتغير (247,153,194)
        up = Image.open(SKINS / "keybg_lux_pink_up.png").convert("RGBA")
        uw, uh = up.size
        c = up.getpixel((uw // 2, uh // 2))
        self.assertTrue(all(abs(a - e) <= 28 for a, e in zip(c[:3], (247, 153, 194))), c)

    # ------------------------------------------------------------ الثيمات المستوردة
    def test_toolbar_icons_share_suggestion_color(self):
        """أيقونات شريط الأدوات تحمل لون حروف الاقتراحات نفسه — لون واحد
        لكل عناصر الواجهة (كان surfaceText المشتق من الخلفية يخالفه)."""
        src = read(PART2A)
        # Round 69: مع إطار زر مفعّل يتبع اللون فن الزر عبر ThemeChipArt
        self.assertIn(
            "? com.almlk.swiftkey.theme.ThemeChipArt.textColor(this, currentTheme)",
            src,
        )
        self.assertNotIn("? currentTheme.surfaceText", src)

    def test_kay1_demo_is_the_rose_theme(self):
        """مجلد عرض القلب: زر الوردة نفسه + مسطرة حمراء + خلفية عميقة
        + نص أبيض في config."""
        import json
        from PIL import Image, ImageStat
        up = (SKINS / "keybg_lux_heart_up.png").read_bytes()
        self.assertEqual(up, (ASSETS / "kay1/key.png").read_bytes(), "زر kay1 ليس زر الوردة")
        self.assertEqual(
            hashlib.sha256((SKINS / "keybg_lux_heart_space.png").read_bytes()).hexdigest(),
            hashlib.sha256((ASSETS / "kay1/space.png").read_bytes()).hexdigest(),
            "مسطرة kay1 ليست الحمراء",
        )
        cfg = json.loads((ASSETS / "kay1/config.json").read_text(encoding="utf-8"))
        self.assertEqual("#FFFFFF", cfg["text"], "نص kay1 ليس أبيض")
        self.assertEqual("#C2102E", cfg["key"])
        self.assertEqual("#C2102E", cfg["bottom"])
        self.assertEqual("space.png", cfg["images"]["space"])
        bg = Image.open(ASSETS / "kay1/bg.webp").convert("RGB")
        self.assertLessEqual(max(bg.size), 1080)
        r, g, b = (int(v) for v in ImageStat.Stat(bg).mean)
        self.assertGreater(r, g + 50, "خلفية kay1 ليست حمراء")

    def test_no_lambdas_in_round46_files(self):
        for path in (THEME, PART2A):
            code = re.sub(r"/\*[\s\S]*\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")


if __name__ == "__main__":
    unittest.main()
