# -*- coding: utf-8 -*-
"""Round 41-3 contract: the "خلفيات" section is EMPTIED of the 10 wallpaper themes
and stays present-but-empty in the gallery, awaiting the upcoming professional
themes (owner request). Wallpapers survive only as hidden aliases so an install
whose active theme points at one keeps rendering unchanged.

Owner request: "قم بتفريغ قسم الثيمات بخلفيات واحتفظ بالقسم فارغا سننشئ ثيمات
احترافيه فيه"
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
RES = ROOT / "app/src/main/res"
REPO = JAVA / "theme/ThemeRepository.java"
THEME = JAVA / "theme/KeyboardTheme.java"
GALLERY = JAVA / "settings/ThemeSettingsActivity.java"
EDITOR = JAVA / "settings/CustomThemeActivity.java"

FAMILY_IDS = [
    "white_pure", "white_silver", "white_warm",
    "black_amoled", "black_graphite", "black_charcoal",
    "stone_marble", "stone_granite", "stone_basalt",
    "girly_rose", "girly_fuchsia", "girly_orchid",
    "blue_sky", "blue_royal", "blue_navy",
    "gray_mist", "gray_smoke", "gray_deep",
]
# Round 42: الثيمات الاحترافية الأربعة التي تملأ قسم «خلفيات» بعد تفريغه في 41-3.
LUX_IDS = [
    "lux_gold", "lux_pink", "lux_silver", "lux_heart",
]
SCENE_IDS = [
    "neon_city", "galaxy", "luxury", "dark_minimal", "rose_gold",
    "ocean_night", "purple_waves", "green_matrix", "sunset_palms", "pearl",
]
SCENE_IMAGES = {
    "neon_city": "bkg_theme_neon_city", "galaxy": "bkg_theme_galaxy",
    "luxury": "bkg_theme_luxury", "dark_minimal": "custom_bg_graphite",
    "rose_gold": "custom_bg_rose", "ocean_night": "custom_bg_ocean",
    "purple_waves": "custom_bg_lavender", "green_matrix": "custom_bg_forest",
    "sunset_palms": "custom_bg_sunset", "pearl": "custom_bg_aurora",
}


def read(path):
    return path.read_text(encoding="utf-8")


class EmptyScenesRound413(unittest.TestCase):
    def test_scenes_section_emptied_but_present(self):
        src = read(REPO)
        listed = re.findall(r'"([a-z0-9_]+)"', src.split("BUILT_IN_IDS")[1].split("};")[0])
        # Round 42: القسم لم يعد فارغاً — 18 عائلة + 4 ثيمات احترافية
        self.assertEqual(sorted(FAMILY_IDS + LUX_IDS), sorted(listed))
        self.assertIn('CATEGORY_SCENES = "scenes"', src.replace("  public static final String", ""))
        gallery = read(GALLERY)
        self.assertIn('"خلفيات"', gallery)  # القسم باقٍ في المعرض — يعرض الثيمات الاحترافية

    def test_scenes_match_lux_themes_only(self):
        """تصنيف خلفيات يطابق الثيمات المدمجة غير العائلية — ثيمات round42 الأربعة فقط."""
        src = read(REPO)
        self.assertIn("return !isFamilyTheme(id);", src)
        self.assertIn("private static boolean isFamilyTheme(String id) {", src)
        for prefix in ("white_", "black_", "stone_", "girly_", "blue_", "gray_"):
            self.assertIn(f'id.startsWith("{prefix}")', src)
        for lid in LUX_IDS:
            self.assertFalse(
                any(lid.startswith(p) for p in
                    ("white_", "black_", "stone_", "girly_", "blue_", "gray_")),
                lid,
            )

    def test_wallpapers_survive_as_hidden_aliases(self):
        src = read(THEME)
        for tid in SCENE_IDS:
            self.assertIn(f'"{tid}".equals(id)', src, tid)
            self.assertIn(f'.withKeySkin("{tid}")', src, tid)
            found = list((RES / "drawable-nodpi").glob(SCENE_IMAGES[tid] + ".*"))
            self.assertTrue(found, f"صورة الخلفية مفقودة: {tid}")

    def test_scene_key_skins_still_on_disk(self):
        for tid in SCENE_IDS:
            for part in ("up", "fun", "space", "enter"):
                self.assertTrue(
                    (RES / "drawable-nodpi" / f"keybg_{tid}_{part}.png").exists(),
                    f"missing keybg_{tid}_{part}",
                )

    def test_delete_active_fallback_is_listed_theme(self):
        src = read(EDITOR)
        self.assertIn('setTheme("white_pure");', src)
        self.assertNotIn('setTheme("pearl");', src)

    def test_no_lambdas_in_round413_files(self):
        for path in (REPO, THEME, EDITOR):
            code = re.sub(r"/\*[\s\S]*?\*/", " ", read(path))
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")


if __name__ == "__main__":
    unittest.main()
