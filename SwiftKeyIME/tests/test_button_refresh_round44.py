# -*- coding: utf-8 -*-
"""Round 44 contract: the photo-theme buttons re-cut from the owner's NEW reference
sheet, the golden background for the gold theme, the slimmed 3D bottom edge, and the
button shape extended over the suggestion strip / toolbar chrome.

Owner request: عدل شكل الأزرار في قسم ثيمات الصور وفي مجلد الأصول لتكون بالأزرار
المرفقة في الصورة الجديدة، واضبط خلفية الثيم الذهبي لتكون ذهبية مع تقليص شكل
الثريدي من أسفل الزر، واستبدل زر القلب بالجديد، والذهبي بالذهبي والوردي بالوردي،
مع ضبط تغيير الشكل ليشمل شريط الاقتراحات مع الكيبورد وبقية الواجهات.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
RES = ROOT / "app/src/main/res"
SKINS = RES / "drawable-nodpi"
ASSETS = ROOT / "app/src/main/assets/theme"
THEME = JAVA / "theme/KeyboardTheme.java"
SKINBAR = JAVA / "theme/ThemeSkinBar.java"
PART2A = JAVA / "ime/AlmlkImeRuntimePart2A.java"


def read(path):
    return path.read_text(encoding="utf-8")


def px(path, x, y):
    from PIL import Image
    return Image.open(path).convert("RGBA").getpixel((x, y))


def near(actual, expected, tol):
    return all(abs(a - e) <= tol for a, e in zip(actual[:3], expected))


class ButtonRefreshRound44(unittest.TestCase):
    def test_new_button_colors_match_reference(self):
        """ألوان أجسام الأزرار الجديدة من المرجع: ذهبي (237,193,40)، وردي
        (247,153,194)، فضي (224,224,224)، قلب لمعة حمراء (249,116,119)."""
        from PIL import Image
        cases = [
            ("keybg_lux_gold_up.png", (237, 193, 40)),
            ("keybg_lux_pink_up.png", (247, 153, 194)),
            ("keybg_lux_silver_up.png", (224, 224, 224)),
        ]
        for name, expected in cases:
            w, h = Image.open(SKINS / name).size
            self.assertTrue(near(px(SKINS / name, w // 2, h // 2), expected, 28), name)
        # Round 46: زر الوردة — جسم أحمر عند 55% وقاع أحمر عميق ساطع
        w, h = Image.open(SKINS / "keybg_lux_heart_up.png").size
        self.assertTrue(near(px(SKINS / "keybg_lux_heart_up.png", w // 2, int(h * 0.55)),
                             (191, 15, 44), 30), "جسم الوردة الأحمر")
        self.assertTrue(near(px(SKINS / "keybg_lux_heart_up.png", w // 2, h - 4),
                             (165, 46, 64), 45), "قاع الوردة أحمر عميق")

    def test_3d_bottom_edge_slimmed(self):
        """الثريدي مقلّص: آخر صفوف كل زر ساطعة (لا الظل الداكن ولا قاعدة 3D
        الكاملة)، وارتفاع الجلد يقارب الجسم + حافة نحيفة."""
        from PIL import Image
        for name, body_h in [
            ("keybg_lux_gold_up.png", 150), ("keybg_lux_pink_up.png", 144),
            ("keybg_lux_silver_up.png", 146), ("keybg_lux_heart_up.png", 150),
        ]:
            im = Image.open(SKINS / name).convert("RGBA")
            w, h = im.size
            bottom = px(SKINS / name, w // 2, h - 3)
            self.assertGreater(max(bottom[:3]), 140, f"{name}: قاع مظلل — الثريدي لم يُقلّص")
            self.assertLess(h, body_h + 22, f"{name}: الحافة السفلية سميكة")

    def test_gold_theme_background_is_golden(self):
        """خلفية الذهبية ذهبية: اللون الاحتياطي 0xffb07712 والصورة متوسطها ذهبي."""
        src = read(THEME)
        self.assertIn("return lux(0xffb07712, 0xffd9b45c", src)
        from PIL import ImageStat
        from PIL import Image
        bg = Image.open(SKINS / "bkg_lux_gold.webp").convert("RGB")
        r, g, b = (int(v) for v in ImageStat.Stat(bg).mean)
        self.assertGreater(r, 140, "الخلفية ليست ذهبية (أحمر)")
        self.assertGreater(g, 85, "الخلفية ليست ذهبية (أخضر)")
        self.assertLess(b, 70, "الخلفية ليست ذهبية (أزرق)")
        self.assertGreater(r, b + 70, "الصبغة ليست ذهبية")
        # تباين الزر مع الخلفية كافٍ
        key = px(SKINS / "keybg_lux_gold_up.png",
                 Image.open(SKINS / "keybg_lux_gold_up.png").size[0] // 2,
                 Image.open(SKINS / "keybg_lux_gold_up.png").size[1] // 2)
        self.assertGreater(abs(key[1] - g) + abs(key[0] - r), 90, "تباين ضعيف")

    def test_surface_pinned_to_bar_art(self):
        src = read(THEME)
        self.assertIn("public KeyboardTheme withSurface(int surface) {", src)
        self.assertIn(".withSurface(0xffd9b45c);", src)  # الذهبية (مسطرة ذهبية)
        self.assertIn(".withSurface(0xffc2203b);", src)  # القلب: مسطرة حمراء (round 46)
        self.assertIn(".withSurface(0xffe795b8);", src)  # الوردية
        self.assertIn(".withSurface(0xffc9ced6);", src)  # الفضية

    def test_skin_bar_exists_and_mirrors_wide_face(self):
        src = read(SKINBAR)
        self.assertIn("public final class ThemeSkinBar extends Drawable", src)
        self.assertIn("public static ThemeSkinBar forTheme(Context context, KeyboardTheme theme)", src)
        self.assertIn('token.startsWith(AssetThemeLibrary.URI_PREFIX)', src)
        self.assertIn('token.startsWith("lux_")', src)
        # نفس عقد المسطرة: مباشر عند ≥3 وإلا ثلاث شرائح بحافة تكيفية
        self.assertIn("if (skin.getWidth() >= skin.getHeight() * 3f) {", src)
        self.assertIn("int src = Math.min(48, skin.getWidth() / 3);", src)
        code = re.sub(r"/\*[\s\S]*?\*/", " ", src)
        code = re.sub(r"//[^\n]*", " ", code)
        self.assertNotIn("->", code, "Lambda في ThemeSkinBar")

    def test_chrome_wiring_in_runtime(self):
        """شريط الاقتراحات + الفاصل + شريط التمرير + شريط الأدوات كلها تحمل
        جلد المسطرة للثيمات المصورة، ولون السطح لغيرها."""
        src = read(PART2A)
        # Round 52 وحّد الاستدعاءات الأربعة في barSurfaceArt() — الجلد أولاً
        # ثم صورة الخلفية (ThemeSurfacePaint) ثم لون السطح المسطح.
        self.assertIn("ThemeSkinBar.forTheme(this, currentTheme);", src)
        self.assertIn("ThemeSurfacePaint.forTheme(this, currentTheme);", src)
        self.assertEqual(4, src.count("barSurfaceArt();"))
        self.assertIn(
            "if (rowArt != null) normalSuggestionRow.setBackgroundDrawable(rowArt);",
            src,
        )
        self.assertIn(
            "else normalSuggestionRow.setBackgroundColor(currentTheme.surface);",
            src,
        )

    def test_assets_demo_refreshed_from_new_art(self):
        """مجلدا العرض في assets محدّثان بالأزرار الجديدة والخلفية الذهبية."""
        from PIL import Image
        import json
        kay_key = Image.open(ASSETS / "kay/key.png")
        kw, kh = kay_key.size
        center = kay_key.convert("RGBA").getpixel((kw // 2, kh // 2))
        self.assertTrue(near(center, (237, 193, 40), 40), f"زر kay ليس ذهبياً جديداً: {center}")
        kay1_key = Image.open(ASSETS / "kay1/key.png").convert("RGBA")
        cw, ch = kay1_key.size
        c1 = kay1_key.getpixel((cw // 2, int(ch * 0.55)))
        self.assertGreater(c1[0], c1[1] + 40, f"زر kay1 ليس أحمر: {c1}")
        cfg = json.loads((ASSETS / "kay/config.json").read_text(encoding="utf-8"))
        self.assertEqual("#D9B45C", cfg["key"])

    def test_press_twins_survive_refresh(self):
        from PIL import Image
        for tid in ("lux_gold", "lux_pink", "lux_silver", "lux_heart"):
            up = Image.open(SKINS / f"keybg_{tid}_up.png").convert("RGBA")
            press = Image.open(SKINS / f"keybg_{tid}_press.png").convert("RGBA")
            self.assertEqual(up.size, press.size, tid)
            cu = px(SKINS / f"keybg_{tid}_up.png", up.size[0] // 2, up.size[1] // 2)
            cp = px(SKINS / f"keybg_{tid}_press.png", up.size[0] // 2, up.size[1] // 2)
            self.assertLess(cp[0] + cp[1] + cp[2], (cu[0] + cu[1] + cu[2]) * 0.75, tid)

    def test_gold_ruler_still_direct_draw(self):
        # Round 47: زر المسافة بنفس شكل الأزرار — نسبة 2.88 لثلاث شرائح
        from PIL import Image
        for tid in ("lux_gold", "lux_heart", "lux_pink"):
            w, h = Image.open(SKINS / f"keybg_{tid}_space.png").size
            self.assertLess(w / h, 3.0, tid)
            self.assertGreaterEqual(w / h, 2.5, tid)

    def test_no_lambdas_in_round44_files(self):
        for path in (THEME, SKINBAR, PART2A):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")


if __name__ == "__main__":
    unittest.main()
