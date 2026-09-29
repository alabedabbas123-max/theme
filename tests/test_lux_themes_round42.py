# -*- coding: utf-8 -*-
"""Round 42 contract: four professional «خلفيات» themes built from the owner's
glossy 3D button reference sheet (gold, pink, silver, red heart) over generated
photo backgrounds — marble-gold, pastel pink sheen, polished steel, rose petals.

Owner request: "اربع ثيمات واجهة رقمية احترافية عالية الجودة في قسم خلفيات ...
بانماط الازرار ثلاثية الاصابع اللامعة من الصورة المرفقة مع الحفاظ الحرفي على
النسيج اللامع واللون والشكل" — قلب أحمر للمفاتيح الرئيسية ومعدِّلات ومسافة
ذهبية في ثيم القلب، ولا علامة «Made with AI» في أي مخرج.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
RES = ROOT / "app/src/main/res"
SKINS = RES / "drawable-nodpi"
REPO = JAVA / "theme/ThemeRepository.java"
THEME = JAVA / "theme/KeyboardTheme.java"
VIEW = JAVA / "ime/SmartKeyboardView.java"

LUX_IDS = ["lux_gold", "lux_pink", "lux_silver", "lux_heart"]
LUX_NAMES = ["الذهبية الفاخرة", "الوردية الناعمة", "الفضية المصقولة", "قلب رومانسي"]
LUX_IMAGES = {
    "lux_gold": "bkg_lux_gold",
    "lux_pink": "bkg_lux_pink",
    "lux_silver": "bkg_lux_silver",
    "lux_heart": "bkg_lux_heart",
}
# لوحات الألوان الحرفية من from() — (bg, key, pressed, text, sub, accent, bottom)
# Round 44: خلفية الذهبية صارت ذهبية (0xffb07712) وكل فرع يثبّت سطح شريطه بـwithSurface.
LUX_PALETTES = {
    "lux_gold": "0xffb07712, 0xffd9b45c, 0xffa8833a, 0xff241a08, 0xff6e5a2c, 0xffd4af37,\n          0xffd9b45c",
    "lux_pink": "0xffe690b4, 0xffe795b8, 0xffc97ba0, 0xfffdf1f6, 0xffb96a8a, 0xffd65d93,\n          0xffe795b8",
    "lux_silver": "0xff33383f, 0xffc9ced6, 0xff9aa2ac, 0xff23272d, 0xff565e68, 0xff8f9aa8,\n          0xffc9ced6",
    "lux_heart": "0xff550e1c, 0xffc2102e, 0xff8f0d22, 0xffffffff, 0xffeec7ce, 0xffffffff,\n          0xffc2102e",
}
LUX_SURFACES = {
    "lux_gold": "0xffd9b45c",
    "lux_pink": "0xffe795b8",
    "lux_silver": "0xffc9ced6",
    "lux_heart": "0xffc2203b",
}


def read(path):
    return path.read_text(encoding="utf-8")


def px(path, x, y):
    from PIL import Image
    return Image.open(path).convert("RGBA").getpixel((x, y))


def opaque_count(path):
    from PIL import Image
    im = Image.open(path).convert("RGBA")
    return im, sum(1 for p in im.getdata() if p[3] > 30)


class LuxThemesRound42(unittest.TestCase):
    def test_from_branches_use_lux_helper_with_exact_palettes(self):
        src = read(THEME)
        self.assertIn("private static KeyboardTheme lux(", src)
        # المُساعد يمرر opacity كاملة وخطوط الحجم نفسها وعائلة المعدِّلات ذهبية
        self.assertIn("7f, 26f, 13f, image, 1f,", src)
        self.assertIn("bottom, bottom, bottom, accent, 0, 0f, 0, skin);", src)
        for tid, palette in LUX_PALETTES.items():
            self.assertIn(f'"{tid}".equals(id)', src, tid)
            self.assertIn(
                f"return lux({palette}, \"{LUX_IMAGES[tid]}\", \"{tid}\")"
                f".withSurface({LUX_SURFACES[tid]});",
                src,
                tid,
            )

    def test_repository_lists_lux_after_families(self):
        src = read(REPO)
        listed = re.findall(r'"([a-z0-9_]+)"', src.split("BUILT_IN_IDS")[1].split("};")[0])
        self.assertEqual(LUX_IDS, listed[-4:])
        names = src.split("BUILT_IN_NAMES")[1].split("};")[0]
        for name in LUX_NAMES:
            self.assertIn(f'"{name}"', names, name)

    def test_backgrounds_and_all_skin_parts_exist(self):
        for tid in LUX_IDS:
            # Round 45: الخلفيات webp
            found = list(SKINS.glob(LUX_IMAGES[tid] + ".webp"))
            self.assertTrue(found, f"خلفية مفقودة: {LUX_IMAGES[tid]}")
            for part in ("up", "fun", "space", "enter", "press"):
                self.assertTrue(
                    (SKINS / f"keybg_{tid}_{part}.png").exists(),
                    f"missing keybg_{tid}_{part}",
                )

    def test_skins_carry_real_button_art(self):
        """الجلود مقصوصة من أزرار المرجع: جسم معتم في المركز وزوايا شفافة
        (قناع smoothstep على خلفية سوداء نقية)."""
        for tid in LUX_IDS:
            up = SKINS / f"keybg_{tid}_up.png"
            im, opaque = opaque_count(up)
            w, h = im.size
            self.assertGreater(opaque, w * h * 0.35, f"{tid}: جسم الزر مفقود")
            self.assertEqual(0, px(up, 1, 1)[3], f"{tid}: الزاوية ليست شفافة")
            self.assertGreater(px(up, w // 2, h // 2)[3], 200, f"{tid}: المركز شبه شفاف")

    def test_press_is_darkened_twin_of_up(self):
        """press = نسخة معتمة (≈0.55) بنفس قناع ألفا — ومضة ضغط واقعية."""
        from PIL import Image
        for tid in LUX_IDS:
            up = Image.open(SKINS / f"keybg_{tid}_up.png").convert("RGBA")
            press = Image.open(SKINS / f"keybg_{tid}_press.png").convert("RGBA")
            self.assertEqual(up.size, press.size, tid)
            cu = px(SKINS / f"keybg_{tid}_up.png", up.size[0] // 2, up.size[1] // 2)
            cp = px(SKINS / f"keybg_{tid}_press.png", up.size[0] // 2, up.size[1] // 2)
            self.assertLess(cp[0] + cp[1] + cp[2], (cu[0] + cu[1] + cu[2]) * 0.75, tid)
            self.assertGreater(cp[3], 200, f"{tid}: ومضة الضغط شفافة")

    def test_space_bar_shares_the_key_shape(self):
        """Round 47: زر المسافة بنفس شكل بقية الأزرار — مسطرة بنسبة 2.88 (أقل
        من 3) فتُرسم بثلاث شرائح: الحواف تحمل زوايا الزر بمقياسه، والوسط
        المسطح يتمدد. Round 46: الوردية صارت مسطرة عريضة حقيقية."""
        from PIL import Image
        for tid in ("lux_gold", "lux_heart", "lux_pink"):
            w, h = Image.open(SKINS / f"keybg_{tid}_space.png").size
            # Round 47: المسطرة زر عريض بنسبة 2.88 — ثلاث شرائح تحفظ الحواف
            # بمقياس الزر والوسط المسطح يتمدد
            self.assertLess(w / h, 3.0, tid)
            self.assertGreaterEqual(w / h, 2.5, tid)

    def test_heart_rose_button_all_red(self):
        """Round 46: زر الوردة — زر أحمر أنيق مستدير بوردة صغيرة في رأسه، وكل
        الأوجه (رئيسي/معدِّلات/إدخال) حمراء موحّدة — لا ذهبي في ثيم القلب."""
        from PIL import Image
        im = Image.open(SKINS / "keybg_lux_heart_up.png").convert("RGBA")
        w, h = im.size
        p = im.load()
        # زر مستدير: الزوايا شفافة والجسم معتم بعرض شبه كامل في المنتصف
        self.assertEqual(0, p[1, 1][3], "الزاوية العلوية ليست شفافة")
        self.assertEqual(0, p[w - 2, h - 2][3], "الزاوية السفلية ليست شفافة")
        mid = sum(1 for x in range(w) if p[x, h // 2][3] > 30)
        self.assertGreater(mid, w * 0.8, "الجسم لا يملك عرض المنتصف")
        # الجسم أحمر والوردة في الرأس وردية واضحة
        body = p[w // 2, int(h * 0.55)]
        self.assertGreater(body[0], body[1] + 80, "الجسم ليس أحمر")
        rose = p[w // 2, int(h * 0.23)]
        self.assertGreater(rose[0], 180, "الوردة ليست زاهية")
        self.assertGreater(rose[1] - body[1], 40, "لا وردة في رأس الزر")
        # القاع أحمر عميق ساطع (لا ظل أسود)
        bottom = p[w // 2, h - 4]
        self.assertGreater(bottom[0], 150, "القاع ليس أحمر ساطعاً")
        self.assertGreater(bottom[0], bottom[1] + 60, "القاع ليس أحمر عميقاً")
        # كل الأوجه حمراء — المعدِّلات والإدخال لم يعودا ذهبيين
        for part in ("fun", "enter"):
            art = Image.open(SKINS / f"keybg_lux_heart_{part}.png").convert("RGBA")
            pw, ph = art.size
            q = art.load()
            c = q[pw // 2, int(ph * 0.55)]
            self.assertGreater(c[0], c[1] + 80, f"{part} ليس أحمر")
            r = q[pw // 2, int(ph * 0.23)]
            self.assertGreater(r[1] - c[1], 40, f"{part}: لا وردة في الرأس")

    def test_wide_face_direct_draw_and_adaptive_slice(self):
        src = read(VIEW)
        body = src[src.index("private void drawWideSkinFace"):]
        body = body[: body.index("\n  }\n")]
        self.assertIn("if (skin.getWidth() >= skin.getHeight() * 3f) {", body)
        self.assertIn("int src = Math.min(48, skin.getWidth() / 3);", body)
        # ومضة ضغط المسافة تُقطَّع بالمثل بدل تمديد صورة مفتاح عبر المسطرة
        self.assertIn(
            "if (key.code == KeySpec.SPACE) {\n            drawWideSkinFace(canvas, pressFace, face, paint);",
            src,
        )

    def test_no_lambdas_in_round42_files(self):
        for path in (THEME, REPO, VIEW):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")

    def test_no_fake_nine_and_no_ai_watermark_names(self):
        """لا أسماء .9 ولا مسافات — والقص من مناطق الأزرار في المرجع فقط
        (منطقة العلامة المائية أسفل y=900 غير مستخدمة)."""
        for f in SKINS.iterdir():
            self.assertNotIn(".9.", f.name, f.name)
            self.assertNotIn(" ", f.name, f.name)
        for tid in LUX_IDS:
            for part in ("up", "fun", "space", "enter", "press"):
                self.assertTrue(
                    re.match(r"^keybg_lux_[a-z]+_(up|fun|space|enter|press)\.png$",
                             f"keybg_{tid}_{part}.png"),
                    part,
                )


if __name__ == "__main__":
    unittest.main()
