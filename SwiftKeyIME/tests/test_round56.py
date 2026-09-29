# -*- coding: utf-8 -*-
"""Round 56 — قسم Auto يمثل التفاعلات المتحركة عند الضغط على المفاتيح.

طلب المالك: «اصلح قسم Auto في الصورة حيث يمثل التفاعلات المتحركة التي تظهر عند
الضغط على اي مفتاح واذا كان هناك اي مواقع للتفاعلات مثل ظهور قلوب او بوسات او
اشياء متحركة تستخدم للظهور اثناء الضغط على مفاتيح الكيبورد فاضف تصاميم جديدة
للقسم لتحميلها».
"""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
IME = JAVA / "ime/SmartKeyboardView.java"
THEME = JAVA / "theme"
STUDIO = JAVA / "settings/CustomThemeActivity.java"
CATALOG = JAVA / "settings/ThematyStore.java"
STORE = JAVA / "settings/OnlineAssetStore.java"
EFFECTS = THEME / "PressEffects.java"
ASSETS = ROOT / "app/src/main/assets/theme_downloads"
LAYOUT = ROOT / "app/src/main/res/layout/activity_custom_theme.xml"
DRAWABLES = ROOT / "app/src/main/res/drawable-nodpi"


def read(path):
    return path.read_text(encoding="utf-8")


def method_body(src, signature):
    i = src.index(signature)
    j = src.index("\n  }\n", i)
    return src[i:j]


def balanced(path):
    """عدّاد أقواس واعٍ للسلاسل والمحارف والتعليقات (نمط الجولات)."""
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
    return (
        out.count("{") == out.count("}")
        and out.count("(") == out.count(")")
        and out.count("[") == out.count("]")
    )


class PressEffectsCatalogRound56(unittest.TestCase):
    def test_catalog_constants(self):
        """كتالوج التصاميم المدمجة: عشرة تصاميم بأسماء وتسميات عربية وسلوك صعود."""
        src = read(EFFECTS)
        self.assertIn("public static final int COUNT = 10;", src)
        for name in ("heart", "kiss", "star", "spark", "snow",
                     "bubble", "petal", "flame", "note", "confetti"):
            self.assertIn('"%s"' % name, src)
        for label in ("قلوب", "بوسات", "نجوم", "شرارات", "ثلج",
                      "فقاعات", "بتلات", "لهب", "نوتات", "قصاصات"):
            self.assertIn('"%s"' % label, src)
        self.assertIn("public static String artName(int effect)", src)
        self.assertIn('return "pressfx_" + IDS[effect - 1];', src)
        self.assertIn("public static boolean rises(int effect)", src)

    def test_sprites_exist_and_transparent(self):
        """عشر صور 64×64 بخلفية شفافة — لا GIF ولا WebP."""
        from PIL import Image
        names = ["heart", "kiss", "star", "spark", "snow",
                 "bubble", "petal", "flame", "note", "confetti"]
        for name in names:
            path = DRAWABLES / ("pressfx_%s.png" % name)
            self.assertTrue(path.exists(), name)
            image = Image.open(path).convert("RGBA")
            self.assertEqual((64, 64), image.size, name)
            self.assertLess(image.getpixel((1, 1))[3], 40, name)
            lit = sum(1 for px in image.getdata() if px[3] > 40)
            self.assertGreater(lit, 450, name)
        self.assertEqual(10, len(list(DRAWABLES.glob("pressfx_*.png"))))

    def test_catalog_discipline(self):
        src = read(EFFECTS)
        code = re.sub(r"/\*[\s\S]*?\*/", " ", src)
        code = re.sub(r"//[^\n]*", " ", code)
        self.assertNotIn("->", code, "Lambda في الكتالوج")
        self.assertTrue(balanced(EFFECTS), "أقواس غير متوازنة")


class ThemeModelRound56(unittest.TestCase):
    def test_theme_carries_press_effect(self):
        """النموذج يحمل تفاعل الضغط المدمج والمحمّل معاً في كل النسخ."""
        src = read(THEME / "KeyboardTheme.java")
        self.assertIn("public final int pressEffect;", src)
        self.assertIn("public final String pressEffectUri;", src)
        self.assertIn("int effect,", src)
        self.assertIn("String effectUri)", src)
        self.assertIn("Math.min(PressEffects.COUNT, effect)", src)
        self.assertIn("public KeyboardTheme withPressEffect(int effect)", src)
        self.assertIn("public KeyboardTheme withPressEffectUri(String effectUri)", src)
        # كل سلسلة النسخ الأربع تحمل التفاعل ولا تسقطه
        # Round 69: ذيل النسخ صار يشمل مقياسي الزر — 4 نسخ بالذيل الكامل
        # (مع withKeySize الخاص بهما ونسختي التفاعل بذيليهما المعدلين)
        self.assertEqual(
            src.count("pressEffect, pressEffectUri,\n        keyWidthScale, keyHeightScale);"), 4)
        self.assertIn("pressEffect, pressEffectUri, w, h);", src)
        self.assertIn(
            "pressEffect, value,\n        keyWidthScale, keyHeightScale);", src)

    def test_repository_persists_press_effect(self):
        """المستودع يحفظ التفاعل ويستعيده ضمن نفس المفتاح base."""
        src = read(THEME / "ThemeRepository.java")
        self.assertIn('p.getInt(base + "press_effect", 0)', src)
        self.assertIn('p.getString(base + "press_effect_uri", "")', src)
        self.assertIn('.putInt(b + "press_effect", theme.pressEffect)', src)
        self.assertIn('.putString(b + "press_effect_uri", theme.pressEffectUri)', src)
        self.assertIn('"press_effect"', src)
        self.assertIn('"press_effect_uri"', src)


class EngineRound56(unittest.TestCase):
    def test_engine_bursts_particles_on_press(self):
        """المحرك ينثر جسيمات التفاعل فوق المفتاح لحظة ضغطه ويحرّكها بإطارات عرض."""
        src = read(IME)
        self.assertIn("private int pressEffect;", src)
        self.assertIn("private String pressEffectUri = \"\";", src)
        self.assertIn("spawnPressParticles(pressed.rect.centerX(), pressed.rect.centerY());", src)
        self.assertIn("drawPressParticles(c);", src)
        self.assertIn("for (int index = 0; index < 9; index++)", src)
        self.assertIn("postInvalidateOnAnimation();", src)
        self.assertIn("public void demoPressEffect()", src)
        # تفكيك sprite التفاعل: الإنترنت أولوية ثم المدمج، والفشل لا يوقف الكيبورد
        self.assertIn("private void updatePressSprite()", src)
        # Round 63: فكّ محدود بكاش عبر KeyArtProcessor — لا decodeFile خام
        self.assertIn("KeyArtProcessor.fileSprite(pressEffectUri)", src)
        self.assertIn("PressEffects.artName(pressEffect)", src)
        self.assertIn("pressSprite = null; // تصميم تالف لا يوقف الكيبورد أبداً", src)
        self.assertIn("PressEffects.rises(pressEffect)", src)

    def test_engine_discipline(self):
        self.assertTrue(balanced(IME), "أقواس غير متوازنة في المحرك")
        code = re.sub(r"/\*[\s\S]*?\*/", " ", read(IME))
        code = re.sub(r"//[^\n]*", " ", code)
        self.assertNotIn("->", code, "Lambda في المحرك")


class StudioSectionRound56(unittest.TestCase):
    def test_auto_section_leads_with_press_effects(self):
        """قسم تلقائي يفتح على تفاعلات الضغط المتحركة قبل زخرفة الكيبورد."""
        xml = read(LAYOUT)
        self.assertIn('android:id="@+id/pressfx_presets"', xml)
        # Round 59: لستة واحدة — الشبكات المنفصلة أُزيلت بالدمج
        self.assertNotIn('android:id="@+id/pressfx_queries"', xml)
        self.assertNotIn('android:id="@+id/pressfx_online_grid"', xml)
        self.assertIn("تفاعلات الضغط المتحركة", xml)
        self.assertIn("قلوب وبوسات ونجوم تتناثر فوق المفتاح لحظة ضغطه", xml)
        # ج68: قسم Auto هو التفاعلات وحدها — الخلفيات المتحركة انتقلت لقسم الخلفيات
        i_auto = xml.index('android:id="@+id/section_auto"')
        i_fx = xml.index('android:id="@+id/pressfx_presets"')
        end_auto = xml.index("</LinearLayout>", i_fx)
        self.assertLess(i_fx, end_auto, "التفاعلات داخل قسم Auto")
        auto_block = xml[i_auto:end_auto]
        self.assertNotIn('android:id="@+id/auto_theme_grid"', auto_block)
        bg_block = xml[
            xml.index('android:id="@+id/section_background"') :
            xml.index('android:id="@+id/section_auto"')
        ]
        self.assertIn('android:id="@+id/auto_theme_grid"', bg_block)
        import xml.etree.ElementTree as ET
        ET.fromstring(xml)  # سلامة البنية

    def test_studio_builds_preset_cards(self):
        """بطاقات التصاميم المدمجة العشرة + بطاقة «بدون» — بنفس قياسات بطاقات الأقسام."""
        src = read(STUDIO)
        body = method_body(src, "private void buildPressFxPresets()")
        self.assertIn("pressFxPresets.addView(pressFxNoneCard(width));", body)
        self.assertIn("for (int index = 0; index < PressEffects.COUNT; index++)", body)
        self.assertIn("params.height = dp(84);", body)
        self.assertIn("preview.demoPressEffect();", body)
        self.assertIn("pressEffectUri = \"\"; // المدمج يسقط تصميم الإنترنت", body)
        none = method_body(src, "private View pressFxNoneCard(int width)")
        self.assertIn("none.setText(\"✕\");", none)
        self.assertIn('label.setText("بدون");', none)

    def test_studio_downloads_new_designs(self):
        """تصاميم جديدة تُحمَّل من الإنترنت: عناوين + بحث + بطاقات + تطبيق فوري."""
        src = read(STUDIO)
        self.assertIn("private void loadPressFxSets()", src)
        self.assertIn("ThematyStore.sets(", src)
        self.assertIn("OnlineAssetStore.TYPE_EFFECT,", src)
        self.assertIn("private void showThematyPressFx(final String setId)", src)
        self.assertIn("private void addPressFxCard(final OnlineAssetStore.Item item, int width)", src)
        self.assertIn("private void applyPressFx(String path)", src)
        self.assertIn("OnlineAssetStore.TYPE_EFFECT, item.id)", src)
        self.assertIn('"جارٍ جلب التفاعل من المكتبة…"', src)
        self.assertIn('"تعذر الجلب من المكتبة — تحقق من الاتصال"', src)
        self.assertIn('"طُبِّق تفاعل الضغط — جرّب الضغط على المعاينة"', src)
        # النقر على بطاقة محفوظة يطبّقها مباشرة بلا تحميل جديد
        body = method_body(src, "private void addPressFxCard(final OnlineAssetStore.Item item, int width)")
        self.assertIn("if (local != null) {\n              applyPressFx(local.path);", body)

    def test_studio_saves_effect_in_theme(self):
        """الاستوديو يمرر التفاعل للثيم عند الحفظ."""
        src = read(STUDIO)
        self.assertIn(".withPressEffect(pressEffect)", src)
        self.assertIn(".withPressEffectUri(pressEffectUri)", src)


    def test_sets_come_from_library(self):
        """Round 67: أقسام التفاعلات من تاغات مكتبة ثيماتي — لا استعلامات مدمجة."""
        src = read(STUDIO)
        self.assertNotIn("FALLBACK_FX_QUERIES", src)
        self.assertIn("private void loadPressFxSets()", src)
        self.assertIn("private int pressFxSetCount()", src)
        self.assertIn("ThematyStore.sets(", src)

    def test_interactions_section_described_for_user(self):
        """قسم التفاعلات موصوف بالعربية: تفاعلات مكتبة ثيماتي."""
        src = read(STUDIO)
        self.assertIn('addListHeader(pressFxPresets, "تفاعلات من مكتبة ثيماتي", 3);', src)

    def test_studio_renders_saved_before_network(self):
        """النتائج تُعرض المحفوظة أولاً ثم نتائج الشبكة غير المكررة."""
        src = read(STUDIO)
        body = method_body(src, "private void renderPressFxResults(int width)")
        i_saved = body.index("OnlineAssetStore.saved(this, OnlineAssetStore.TYPE_EFFECT)")
        i_merge = body.index("merged.add(item)")
        self.assertLess(i_saved, i_merge)
        self.assertIn("if (OnlineAssetStore.savedItem(this, OnlineAssetStore.TYPE_EFFECT, item.id) == null)", body)
        self.assertIn('"لا عناصر في هذا القسم"', body)

    def test_studio_and_reader_discipline(self):
        for path in (STUDIO, CATALOG):
            self.assertTrue(balanced(path), "أقواس غير متوازنة في %s" % path.name)
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, "Lambda في %s" % path.name)


if __name__ == "__main__":
    unittest.main()
