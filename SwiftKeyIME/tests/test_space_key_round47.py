# -*- coding: utf-8 -*-
"""Round 47 contract: spacebar as a key-shaped button everywhere + bigger space
font/language name + suggestion boxes styled like keyboard buttons + the keyboard
shows itself after picking a theme (when the IME is enabled).

Owner request: يجب أن يكون زر المسافة بنفس شكل بقية الأزرار في كل الثيمات،
والخط فيه واسم اللغة بشكل أكبر، وكذلك الاقتراحات يجب أن يكون مربع الاقتراح
كأنه زر من أزرار الكيبورد في كل الثيمات، وعند اختيار ثيم يجب أن يظهر
الكيبورد إن كان مفعلاً.
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
VIEW = JAVA / "ime/SmartKeyboardView.java"
PART5 = JAVA / "ime/AlmlkImeRuntimePart5.java"
BASE = JAVA / "ime/AlmlkImeRuntimeBase.java"
PART2A = JAVA / "ime/AlmlkImeRuntimePart2A.java"
PART4 = JAVA / "ime/AlmlkImeRuntimePart4.java"
SETTINGS = JAVA / "settings/ThemeSettingsActivity.java"
KAP = JAVA / "theme/KeyArtProcessor.java"


def read(path):
    return path.read_text(encoding="utf-8")


class SpaceKeyRound47(unittest.TestCase):
    # ------------------------------------------------------------ زر المسافة
    def test_space_art_is_a_wide_key_button(self):
        """زر المسافة بنفس شكل بقية الأزرار: نسبة 2.88 (ثلاث شرائح — الحواف
        تحمل زوايا الزر بمقياسه)، زوايا شفافة بانحناء الزر نفسه، لمعة علوية
        وعمق سفلي وحافة، ووسط مسطح بلا شعار."""
        from PIL import Image
        for tid in ("lux_gold", "lux_pink", "lux_silver", "lux_heart"):
            im = Image.open(SKINS / f"keybg_{tid}_space.png").convert("RGBA")
            w, h = im.size
            self.assertEqual((450, 156), (w, h), tid)
            self.assertAlmostEqual(w / h, 2.88, delta=0.01, msg=tid)
            p = im.load()
            self.assertEqual(0, p[2, 2][3], f"{tid}: الزاوية ليست شفافة")
            # انحناء الزاوية بمقياس الزر (نصف القطر ≈ 18px): القوس يمر بين
            # y=8 (خارجه) وy=12 (داخله) عند x=2 — لا حادّ ولا بحجم قرص
            self.assertEqual(0, p[2, 2][3], f"{tid}: الزاوية")
            self.assertLess(p[2, 8][3], 40, f"{tid}: انحناء أوسع من الزر")
            self.assertGreater(p[2, 12][3], 200, f"{tid}: انحناء أضيق من الزر")
            self.assertGreater(p[18, 2][3], 200, f"{tid}: انحناء غائب")
            # لمعة علوية وجسم وعمق سفلي — نفس بنية زر المفتاح
            gloss = p[w // 2, 3]
            body = p[w // 2, h // 2]
            depth = p[w // 2, h - 9]  # داخل نطاق العمق، تحت اللمعة وفوق الحافة
            self.assertGreater(sum(gloss[:3]), sum(body[:3]) + 30, f"{tid}: لا لمعة")
            self.assertGreater(sum(body[:3]), sum(depth[:3]) + 20, f"{tid}: لا عمق سفلي")
            # الوسط مسطح بلا شعار (الفرق بين عمودين بعيدين طفيف)
            self.assertLess(abs(sum(p[w // 4, h // 2][:3]) - sum(p[3 * w // 4, h // 2][:3])), 40,
                            f"{tid}: الوسط ليس مسطحاً")

    def test_heart_space_keeps_roses_at_edges_only(self):
        """مسطرة القلب تحمل الوردتين في منطقتي الحواف (داخل شريحة 48px فلا
        تتمدان أبداً) ويبقى الوسط نظيفاً لاسم اللغة."""
        from PIL import Image
        im = Image.open(SKINS / "keybg_lux_heart_space.png").convert("RGBA")
        w, h = im.size
        p = im.load()

        def avg_green(x0, x1):
            tot = n = 0
            for y in range(14, 62):
                for x in range(x0, x1):
                    if p[x, y][3] > 100:
                        tot += p[x, y][1]
                        n += 1
            return tot / max(1, n)

        left = avg_green(6, 46)
        right = avg_green(w - 46, w - 6)
        middle = avg_green(200, 250)
        self.assertGreater(left - middle, 15, "لا وردة في حافة المسطرة اليسرى")
        self.assertGreater(right - middle, 15, "لا وردة في حافة المسطرة اليمنى")

    def test_asset_demos_carry_the_key_shaped_bars(self):
        """kay تحمل مسطرة الذهبية الجديدة وkay1 مسطرة القلب — بذات بايتاتها."""
        pairs = [
            (SKINS / "keybg_lux_gold_space.png", ASSETS / "kay/space.png"),
            (SKINS / "keybg_lux_heart_space.png", ASSETS / "kay1/space.png"),
        ]
        for skin, demo in pairs:
            self.assertEqual(
                hashlib.sha256(skin.read_bytes()).hexdigest(),
                hashlib.sha256(demo.read_bytes()).hexdigest(),
                f"{demo} ليست مسطرة {skin}",
            )

    def test_space_aspect_contract_updated(self):
        self.assertIn("public static final float SPACE_ASPECT = 2.88f;", read(KAP))

    # ------------------------------------------------------------ الخط واسم اللغة
    def test_space_font_and_language_name_bigger(self):
        src = read(VIEW)
        self.assertIn("Math.min(19f, theme.mainTextSizeDp)", src)
        self.assertNotIn("Math.min(14f, theme.mainTextSizeDp)", src)
        self.assertIn("Math.min(dp(22), rect.height() * .5f)", src)      # اسم اللغة
        self.assertIn("Math.min(dp(15), rect.height() * .32f)", src)     # تلميح الأرقام

    # ------------------------------------------------------------ مربع الاقتراح كزر
    def test_suggestion_chip_is_a_keyboard_button(self):
        """مربع الاقتراح زر كيبورد كامل: تدرج لمعة/جسم/عمق بنفس صيغة الأزرار
        (highlight .34 / depth .22) ونصف قطر المفتاح — في كل الثيمات."""
        src = read(PART5)
        self.assertIn("GradientDrawable.Orientation.TOP_BOTTOM", src)
        self.assertIn("blendChannel(currentTheme.key, Color.WHITE, .34f)", src)
        self.assertIn("blendChannel(currentTheme.key, Color.BLACK, .22f)", src)
        # Round 52: الرقاقة تتبع شكل الزر الحقيقي بلا أرضية (قص عند 14)
        self.assertIn(
            "bg.setCornerRadius(dp((int) Math.min(14f, currentTheme.keyRadiusDp)));",
            src,
        )
        # الرقاقة تُطبَّق على كل مواضع الاقتراح
        self.assertIn("chip.setBackground(suggestionBackground());", read(PART2A))
        self.assertEqual(2, read(PART4).count("chip.setBackground(suggestionBackground());"))

    # ------------------------------------------------------------ إظهار الكيبورد عند الاختيار
    def test_show_keyboard_broadcast_wiring(self):
        """الخدمة تستقبل بث «أظهر الكيبورد» فتعيد تطبيق الإعدادات وتطلب
        الظهور (requestShowSelf على API 28+)."""
        src = read(BASE)
        self.assertIn('public static final String ACTION_SHOW_KEYBOARD =', src)
        self.assertIn('"com.almlk.swiftkey.action.SHOW_KEYBOARD"', src)
        self.assertIn("protected final android.content.BroadcastReceiver showKeyboardReceiver =", src)
        self.assertIn("if (Build.VERSION.SDK_INT >= 28) requestShowSelf(0);", src)
        self.assertIn("registerReceiver(\n        showKeyboardReceiver, new android.content.IntentFilter(ACTION_SHOW_KEYBOARD));", src)
        self.assertIn("unregisterReceiver(showKeyboardReceiver);", src)
        # المستقبل يسأل عن التطبيق الحي فوراً
        self.assertIn("handler.sendEmptyMessageDelayed(MSG_APPLY_LIVE_SETTINGS, 8);", src)

    def test_theme_picker_shows_keyboard_when_ime_enabled(self):
        """شاشة الاختيار: بعد تطبيق الثيم تتحقق أن اللوحة مفعّلة (DEFAULT_INPUT_METHOD)
        ثم تبث طلب الإظهار — Round 70: لا تُغلق الشاشة (المستخدم يبقى في الثيمات)."""
        src = read(SETTINGS)
        self.assertIn("private void showKeyboardIfEnabled()", src)
        self.assertIn("Settings.Secure.DEFAULT_INPUT_METHOD", src)
        self.assertIn("current.indexOf(getPackageName()) < 0", src)
        self.assertIn("AlmlkImeRuntimeBase.ACTION_SHOW_KEYBOARD", src)
        self.assertIn("show.setPackage(getPackageName());", src)
        self.assertIn("sendBroadcast(show);", src)
        # Round 70: finish() only in showKeyboardDirectly(), not in showKeyboardIfEnabled()
        self.assertIn("private void showKeyboardDirectly()", src)
        # يُستدعى من مواقع الاختيار الثلاثة (الشبكة + شريط الثيمات المخصصة
        # + ثيمات مكتبة ثيماتي في قسم «كل الثيمات» — ج68)
        self.assertEqual(3, src.count("showKeyboardIfEnabled();"))

    def test_no_lambdas_in_round47_files(self):
        for path in (VIEW, PART5, BASE, SETTINGS):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")

    def test_edited_java_files_balanced(self):
        import re as _re
        for path in (VIEW, PART5, BASE, SETTINGS, KAP):
            code = _re.sub(r"/\*[\s\S]*?\*/", "", read(path))
            code = "\n".join(line.split("//")[0] for line in code.splitlines())
            for op, cl in (("{", "}"), ("(", ")")):
                self.assertEqual(code.count(op), code.count(cl), f"{path.name}: {op}{cl}")


if __name__ == "__main__":
    unittest.main()
