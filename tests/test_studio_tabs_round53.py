# -*- coding: utf-8 -*-
"""Round 53 contract: شريط تبويبات الاستوديو بأيقونات ويب أنيقة زرقاء ثابت.

طلب المالك: استخدم الصور المرفقة لأيقونات تبويبات شريط التصميم المخصص
(المفاتيح/الخط/البقية) مع تغيير لونها إلى الأزرق بدل الأصفر، بشكل أنيق كما
في اللقطة المرجعية، دون أي حركة يمين/يسار — التبويبات تملأ الشريط فقط —
والصور مضغوطة بصيغة WebP لتقليل الحجم.
"""
import colorsys
import re
import unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
STUDIO = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/CustomThemeActivity.java"
LAYOUT = ROOT / "app/src/main/res/layout/activity_custom_theme.xml"
NODPI = ROOT / "app/src/main/res/drawable-nodpi"

SELECT_ICONS = [
    "icon_diy_automatic_hover.webp",
    "custom_skin_tab_style_select.webp",
    "custom_skin_tab_sliding_select.webp",
    "custom_skin_tab_font_select.webp",
    "custom_skin_tab_effect_select.webp",
    "custom_skin_tab_button_select.webp",
]
UNSELECT_ICONS = [
    "icon_diy_automatic.webp",
    "custom_skin_tab_style_unselect.webp",
    "custom_skin_tab_sliding_unselect.webp",
    "custom_skin_tab_font_unselect.webp",
    "custom_skin_tab_effect_unselect.webp",
    "custom_skin_tab_button_unselect.webp",
]


def read(path):
    return path.read_text(encoding="utf-8")


def balanced(path):
    """عدّاد أقواس واعٍ للسلاسل النصية والمحارف والتعليقات (نمط الجولات)."""
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
    return (
        out.count("{") == out.count("}")
        and out.count("(") == out.count(")")
        and out.count("[") == out.count("]")
    )


def saturated_hue(path):
    """متوسط تدرج البكسلات المشبعة (غير الرمادية) — None إن لم توجد."""
    im = Image.open(path).convert("RGBA")
    px = im.load()
    hues = []
    for y in range(0, im.size[1], 2):
        for x in range(0, im.size[0], 2):
            r, g, b, a = px[x, y]
            if a <= 40:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if s >= 0.25:
                hues.append(h * 360.0)
    if not hues:
        return None
    return sum(hues) / len(hues)


class StudioTabsRound53(unittest.TestCase):
    # ------------------------------------------------------------ الأصول
    def test_all_twelve_webp_icons_exist(self):
        for name in SELECT_ICONS + UNSELECT_ICONS:
            path = NODPI / name
            self.assertTrue(path.exists(), "الأيقونة %s غائبة" % name)
            im = Image.open(path)
            im.load()
            self.assertEqual("WEBP", im.format, "%s ليست webp" % name)
            self.assertEqual((96, 96), im.size, "%s ليست 96×96" % name)

    def test_selected_icons_are_blue_not_yellow(self):
        for name in SELECT_ICONS:
            hue = saturated_hue(NODPI / name)
            self.assertIsNotNone(hue, "%s بلا بكسلات مشبعة" % name)
            self.assertTrue(
                195.0 <= hue <= 225.0,
                "%s ليست زرقاء (hue=%.0f)" % (name, hue),
            )
            self.assertFalse(35.0 <= hue <= 70.0, "%s ما زالت صفراء" % name)

    def test_unselected_icons_stay_neutral_gray(self):
        for name in UNSELECT_ICONS:
            hue = saturated_hue(NODPI / name)
            self.assertTrue(
                hue is None or not (35.0 <= hue <= 70.0),
                "%s تحولت للأصفر" % name,
            )

    def test_webp_compressed_tiny(self):
        total = 0
        for name in SELECT_ICONS + UNSELECT_ICONS:
            size = (NODPI / name).stat().st_size
            self.assertLess(size, 4096, "%s كبيرة (%d بايت)" % (name, size))
            total += size
        self.assertLess(total, 24576, "مجموع الأيقونات كبير (%d بايت)" % total)

    # ------------------------------------------------------------ التخطيط
    def test_strip_fills_without_movement(self):
        src = read(LAYOUT)
        # شريط التبويبات ليس داخل HorizontalScrollView إطلاقاً — لا حركة
        first_hsv = src.index("<HorizontalScrollView")
        strip = src.index('android:id="@+id/tab_auto_container"')
        end_strip = src.index('android:id="@+id/content_scroll"')
        self.assertTrue(
            strip < first_hsv or end_strip < first_hsv or src.index("</HorizontalScrollView>") < strip,
            "التبويبات داخل شريط تمرير",
        )
        # كل حاوية تبويب تملأ العرض بوزن متساوٍ بلا عرض ثابت
        # ج68: أربعة تبويبات فقط — الخلفيات، Auto، الأزرار، الخط
        for tab in ("auto", "background", "keys", "font"):
            block = src[
                src.index('android:id="@+id/tab_%s_container"' % tab): src.index(
                    'android:id="@+id/tab_%s_container"' % tab
                )
                + 500
            ]
            self.assertIn('android:layout_width="0dp"', block, "tab_%s بلا وزن" % tab)
            self.assertIn('android:layout_weight="1"', block, "tab_%s بلا ملء" % tab)
        self.assertNotIn('android:layout_width="76dp"', src, "عرض ثابت قديم باقٍ")

    def test_strip_uses_attached_icons(self):
        src = read(LAYOUT)
        strip = src[: src.index('android:id="@+id/content_scroll"')]
        for icon in (
            "icon_diy_automatic",
            "custom_skin_tab_sliding_unselect",
            "custom_skin_tab_button_unselect",
            "custom_skin_tab_font_unselect",
        ):
            self.assertIn("@drawable/%s" % icon, strip, "أيقونة %s غير مستخدمة" % icon)

    # ------------------------------------------------------------ السباكة
    def test_tab_icon_pairs_and_style(self):
        src = read(STUDIO)
        block = src[src.index("TAB_ICONS = {"): src.index("};", src.index("TAB_ICONS = {"))]
        for res in (
            "R.drawable.icon_diy_automatic",
            "R.drawable.icon_diy_automatic_hover",
            "R.drawable.custom_skin_tab_sliding_select",
            "R.drawable.custom_skin_tab_button_select",
            "R.drawable.custom_skin_tab_font_select",
        ):
            self.assertIn(res, block, "الزوج ناقص: %s" % res)
        # ج68: أربعة أزواج فقط (الخلفيات، Auto، الأزرار، الخط)
        self.assertEqual(4, block.count("{R.drawable"), "أربعة أزواج مطلوبة")

    def test_style_tab_swaps_icons_and_clears_filter(self):
        src = read(STUDIO)
        self.assertIn("private void styleTab(ImageButton tab, View container, int index, boolean selected)", src)
        self.assertIn("tab.setImageResource(TAB_ICONS[index][selected ? 1 : 0]);", src)
        self.assertIn("tab.setColorFilter(null);", src)
        # تلوين التسمية المختارة بالأزرق لا الأصفر
        self.assertIn("((TextView) view).setTextColor(selected ? 0xff087fc5 : 0xff555b65);", src)
        self.assertIn("drawable.setStroke(dp(1), 0xff169bea);", src)
        # لا كهرمان في شريط التبويبات الجديد
        style_block = src[src.index("private void styleTab"): src.index("}", src.index("tab.setColorFilter(null);"))]
        self.assertNotIn("ffc400", style_block)

    def test_show_section_wires_containers(self):
        src = read(STUDIO)
        for tab in ("auto", "background", "keys", "font"):
            self.assertIn(
                "styleTab(%sTab, findViewById(R.id.tab_%s_container)" % (tab, tab),
                src,
                "تبويب %s غير موصول" % tab,
            )

    # ------------------------------------------------------------ نظافة
    def test_no_lambdas_and_balanced(self):
        code = re.sub(r"/\*[\s\S]*?\*/", " ", read(STUDIO))
        code = re.sub(r"//[^\n]*", " ", code)
        self.assertNotIn("->", code, "Lambda في الاستوديو")
        self.assertTrue(balanced(STUDIO), "أقواس غير متوازنة")


if __name__ == "__main__":
    unittest.main()
