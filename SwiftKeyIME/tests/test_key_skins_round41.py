# -*- coding: utf-8 -*-
"""Round 41 contract: image key skins (the owner-supplied key art) drive key faces.

Owner request: use the uploaded key images as the DEFAULT key backgrounds of the
default themes, drop the fake ".9" suffixes, drop duplicates, create stone / black /
white / girly / dimmed-white / blue family themes (three shades each), key skins for
every photo wallpaper too, NO celebrity images anywhere, and keep the key shape
subtle (no heavy depth/border layers on image keys).
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
RES = ROOT / "app/src/main/res"
SKINS = RES / "drawable-nodpi"
THEME = JAVA / "theme/KeyboardTheme.java"
REPO = JAVA / "theme/ThemeRepository.java"
VIEW = JAVA / "ime/SmartKeyboardView.java"
THUMB = JAVA / "settings/ThemeThumbnailView.java"
PREFS = JAVA / "util/Prefs.java"

FAMILY_IDS = [
    "white_pure", "white_silver", "white_warm",
    "black_amoled", "black_graphite", "black_charcoal",
    "stone_marble", "stone_granite", "stone_basalt",
    "girly_rose", "girly_fuchsia", "girly_orchid",
    "blue_sky", "blue_royal", "blue_navy",
    "gray_mist", "gray_smoke", "gray_deep",
]
SCENE_IDS = [
    "neon_city", "galaxy", "luxury", "dark_minimal", "rose_gold",
    "ocean_night", "purple_waves", "green_matrix", "sunset_palms", "pearl",
]
# Round 42: جلود الثيمات الاحترافية — لكل منها خمسة أجزاء (press خاص بها).
LUX_IDS = [
    "lux_gold", "lux_pink", "lux_silver", "lux_heart",
]
PARTS = ("up", "fun", "space", "enter")


def read(path):
    return path.read_text(encoding="utf-8")


def px(path, x, y):
    from PIL import Image
    return Image.open(path).convert("RGBA").getpixel((x, y))


class KeySkinsRound41(unittest.TestCase):
    def test_every_skin_has_all_parts_plus_shared_press(self):
        for skin in FAMILY_IDS + SCENE_IDS:
            for part in PARTS:
                self.assertTrue(
                    (SKINS / f"keybg_{skin}_{part}.png").exists(),
                    f"missing keybg_{skin}_{part}",
                )
        # Round 42: جلود lux لها خمسة أجزاء خاصة (up/fun/space/enter/press).
        for skin in LUX_IDS:
            for part in PARTS + ("press",):
                self.assertTrue(
                    (SKINS / f"keybg_{skin}_{part}.png").exists(),
                    f"missing keybg_{skin}_{part}",
                )
        self.assertTrue((SKINS / "keybg_press.png").exists())
        total = len(list(SKINS.glob("keybg_*.png")))
        self.assertEqual(28 * 4 + 4 * 5 + 1, total)

    def test_resource_names_have_no_fake_nine_or_spaces(self):
        pattern = re.compile(r"^[a-z0-9_]+(\.9)?\.(png|jpg|xml|webp)$")
        for folder in RES.glob("drawable*"):
            for f in folder.iterdir():
                self.assertTrue(
                    pattern.match(f.name), f"اسم مورد غير صالح: {f.name}"
                )
        self.assertFalse(list(RES.rglob("*.9.*")) or
                         any(" " in f.name or "(" in f.name
                             for folder in RES.glob("drawable*") for f in folder.iterdir()))

    def test_press_image_keeps_owner_spec(self):
        """keybg_press = مواصفة المالك الحرفية 46×49 alpha 153 (198,210,218) وحافة (155,166,173)."""
        from PIL import Image
        im = Image.open(SKINS / "keybg_press.png").convert("RGBA")
        self.assertEqual((46, 49), im.size)
        center = im.getpixel((23, 25))
        self.assertEqual((198, 210, 218, 153), center)
        bottom = im.getpixel((23, 47))
        self.assertLess(bottom[0], center[0])  # الحافة السفلية أغمق
        self.assertLess(bottom[3], center[3])  # وأكثر شفافية

    def test_enter_image_keeps_owner_spec(self):
        from PIL import Image
        im = Image.open(SKINS / "keybg_white_pure_enter.png").convert("RGBA")
        self.assertEqual((46, 49), im.size)
        self.assertEqual((7, 100, 227, 153), im.getpixel((23, 25)))
        edge = im.getpixel((23, 47))
        self.assertLess(edge[2], 200)  # الحافة الداكنة محفوظة
        self.assertLess(edge[3], 153)

    def test_up_images_carry_curved_bottom_house_shape(self):
        from PIL import Image
        for skin in ("white_pure", "black_amoled", "girly_rose", "neon_city"):
            im = Image.open(SKINS / f"keybg_{skin}_up.png").convert("RGBA")
            w, h = im.size
            p = im.load()

            def row_width(y):
                xs = [x for x in range(w) if p[x, y][3] > 40]
                return xs[-1] - xs[0] if xs else -1

            self.assertLess(row_width(h - 1), row_width(h // 2),
                            f"{skin}: القاع غير منحنٍ")

    def test_recolored_family_colors_exact(self):
        self.assertEqual((255, 255, 255, 255), px(SKINS / "keybg_white_pure_up.png", 36, 57))
        self.assertEqual((26, 29, 33, 255), px(SKINS / "keybg_black_amoled_up.png", 36, 57))
        self.assertEqual((243, 205, 217, 255), px(SKINS / "keybg_girly_rose_fun.png", 48, 57))
        self.assertEqual((194, 168, 120, 153), px(SKINS / "keybg_stone_basalt_enter.png", 23, 25))

    def test_scene_skins_stay_translucent_for_photo(self):
        """جلود الخلفيات المصورة شفافة (~62%) كي تظهر الصورة خلف المفاتيح."""
        for skin in ("neon_city", "pearl", "sunset_palms"):
            a = px(SKINS / f"keybg_{skin}_up.png", 36, 57)[3]
            self.assertGreater(a, 130, skin)
            self.assertLess(a, 180, skin)

    def test_theme_model_has_skin_field_and_copy(self):
        src = read(THEME)
        self.assertIn("public final String keySkin;", src)
        self.assertIn("keySkin = skin == null ? \"\" : skin;", src)
        self.assertIn("public KeyboardTheme withKeySkin(String skin) {", src)
        # كل البوابات العامة الافتراضية بلا جلد
        self.assertIn('font, 0f, 0, "");', src)
        self.assertIn('surface, "");', src)

    def test_from_resolves_28_skins(self):
        src = read(THEME)
        for tid in FAMILY_IDS + SCENE_IDS:
            self.assertIn(f'"{tid}".equals(id)', src, tid)
        # Round 42: 18 عائلة + 4 ثيمات lux احترافية قبل الأسماء المستعارة القديمة
        self.assertEqual(32, len(re.findall(r'\.equals\(id\)\)', src.split("samsung_white")[0])))
        self.assertIn('.withKeySkin("neon_city")', src)
        self.assertIn('.withKeySkin("pearl")', src)
        self.assertIn("private static KeyboardTheme family(", src)
        self.assertIn("private static KeyboardTheme lux(", src)

    def test_draw_key_uses_skin_images_without_heavy_layers(self):
        src = read(VIEW)
        self.assertIn("private void prepareKeySkin() {", src)
        self.assertIn("private Bitmap keySkinFace(KeySpec key, boolean bottomRow) {", src)
        self.assertIn("Bitmap skinFace = keySkinFace(key, bottomRow);", src)
        self.assertIn("the skin drawable IS the key face", src)
        self.assertIn("canvas.drawBitmap(skinFace, null, face, paint);", src)
        # الضغط = غطاء الشفافية 153 فوق صورة المفتاح
        self.assertIn('keySkinBitmaps.get("press");', src)
        # خفة الشكل: كتلة العمق/الإطار ملفوفة داخل else (المسار البرمجي فقط)
        branch = src[src.index("Bitmap skinFace = keySkinFace"):src.index("} else {")]
        self.assertNotIn("drawRoundRect", branch)
        self.assertNotIn("setShadowLayer", branch)
        # ربط الجلد عند تبديل الثيم
        self.assertIn("prepareKeySkin();", src)

    def test_skin_part_selection_by_key_type(self):
        src = read(VIEW)
        self.assertIn('if (key.code == KeySpec.SPACE) return keySkinBitmaps.get("space");', src)
        self.assertIn('if (key.code == KeySpec.ENTER) return keySkinBitmaps.get("enter");', src)
        self.assertIn("key.code == KeySpec.SHIFT || key.code == KeySpec.DELETE || bottomRow", src)

    def test_skin_falls_back_when_images_missing(self):
        src = read(VIEW)
        self.assertIn('if (keySkinBitmaps.get("up") == null) {', src)
        self.assertIn('keySkinLoaded = "";', src)

    def test_thumbnails_render_real_skin_art(self):
        src = read(THUMB)
        self.assertIn("private Bitmap thumbnailSkin(int code, boolean bottomRow) {", src)
        self.assertIn("canvas.drawBitmap(skin, null, face, paint);", src)
        self.assertIn("keySkinCache.clear();", src)

    def test_out_of_box_theme_is_skinned_white(self):
        self.assertIn('getString("theme", "white_pure")', read(PREFS))

    def test_custom_copies_keep_their_skin(self):
        src = read(REPO)
        self.assertIn('.withKeySkin(p.getString(base + "key_skin", ""))', src)
        self.assertIn('.putString(b + "key_skin", theme.keySkin)', src)
        self.assertIn('"key_skin"', src.split("String[] keys")[1].split("};")[0])

    def test_space_skins_match_key_geometry(self):
        """Round 41-1: مسطرة المسافة مركّبة من أساس المفتاح العادي — متناظرة وبلا حشوة وبقوس زوايا."""
        from PIL import Image
        for skin in FAMILY_IDS + SCENE_IDS:
            im = Image.open(SKINS / f"keybg_{skin}_space.png").convert("RGBA")
            w, h = im.size
            self.assertEqual((288, 114), (w, h), skin)
            px = im.load()
            mid = [x for x in range(w) if px[x, h // 2][3] > 40]
            top = [x for x in range(w) if px[x, 2][3] > 40]
            bot = [x for x in range(w) if px[x, h - 2][3] > 40]
            self.assertLessEqual(mid[0], 1, f"{skin}: هامش أيسر")
            self.assertLessEqual(w - 1 - mid[-1], 1, f"{skin}: هامش أيمن")
            self.assertTrue(top and top[0] <= 3, f"{skin}: حشوة علوية")
            self.assertTrue(bot, f"{skin}: قاع فارغ")
            for inset in (bot[0] - mid[0], mid[-1] - bot[-1]):
                self.assertTrue(3 <= inset <= 10, f"{skin}: قوس الزاوية {inset}")
            up = Image.open(SKINS / f"keybg_{skin}_up.png").convert("RGBA")
            self.assertEqual(
                up.getpixel((36, 57))[:3], px[144, 57][:3], f"{skin}: لون المسطرة"
            )
        self.assertEqual(158, Image.open(SKINS / "keybg_neon_city_space.png")
                         .convert("RGBA").getpixel((144, 57))[3])

    def test_no_duplicate_key_art_sources(self):
        """الصور المكررة المستبعدة (tikpb1≈tispb1 وlight_key* الكلاسيكية) ليست في المشروع."""
        for banned in ("tikpb1", "tispb", "tibkb", "tifks", "tinps", "light_key",
                       "light_keypress", "light_panelbar", "light_candidate",
                       "light_key_japanese", "timkkbmk"):
            self.assertFalse(
                list(RES.rglob(f"{banned}*")), f"مصدر مكرر متبقٍ: {banned}"
            )


if __name__ == "__main__":
    unittest.main()
