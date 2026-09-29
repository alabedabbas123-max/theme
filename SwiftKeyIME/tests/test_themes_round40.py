# -*- coding: utf-8 -*-
"""Themes gallery contract after round 41: 18 color-family themes (three shades each
for white / black / stone / girly / blue / dimmed-white) + the 10 photo wallpapers,
with celebrity themes REMOVED by owner request. Keeps the round-40 features that
survive: custom strip on top, long-press editor copies, background dim + surfaces.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
RES = ROOT / "app/src/main/res"
REPO = JAVA / "theme/ThemeRepository.java"
THEME = JAVA / "theme/KeyboardTheme.java"
VIEW = JAVA / "ime/SmartKeyboardView.java"
EDITOR = JAVA / "settings/CustomThemeActivity.java"
GALLERY = JAVA / "settings/ThemeSettingsActivity.java"

FAMILY_IDS = [
    "white_pure", "white_silver", "white_warm",
    "black_amoled", "black_graphite", "black_charcoal",
    "stone_marble", "stone_granite", "stone_basalt",
    "girly_rose", "girly_fuchsia", "girly_orchid",
    "blue_sky", "blue_royal", "blue_navy",
    "gray_mist", "gray_smoke", "gray_deep",
]
# Round 42: الثيمات الاحترافية الأربعة المدرجة في قسم «خلفيات».
LUX_IDS = [
    "lux_gold", "lux_pink", "lux_silver", "lux_heart",
]
SCENE_IDS = [
    "neon_city", "galaxy", "luxury", "dark_minimal", "rose_gold",
    "ocean_night", "purple_waves", "green_matrix", "sunset_palms", "pearl",
]
SCENE_IMAGES = {
    "neon_city": "bkg_theme_neon_city",
    "galaxy": "bkg_theme_galaxy",
    "luxury": "bkg_theme_luxury",
    "dark_minimal": "custom_bg_graphite",
    "rose_gold": "custom_bg_rose",
    "ocean_night": "custom_bg_ocean",
    "purple_waves": "custom_bg_lavender",
    "green_matrix": "custom_bg_forest",
    "sunset_palms": "custom_bg_sunset",
    "pearl": "custom_bg_aurora",
}


def read(path):
    return path.read_text(encoding="utf-8")


class ThemesRound40(unittest.TestCase):
    def test_builtin_list_is_18_families_only(self):
        src = read(REPO)
        for tid in FAMILY_IDS:
            self.assertIn(f'"{tid}"', src, tid)
        # Round 41-3: الخلفيات أُخليت من القائمة. Round 42: قُطعت أربعة ثيمات
        # احترافية (lux_*) من المرجع وأُدرجت في القائمة ضمن قسم «خلفيات».
        listed = src.split("BUILT_IN_IDS")[1].split("};")[0]
        for tid in LUX_IDS:
            self.assertIn(f'"{tid}"', listed, tid)
        for tid in SCENE_IDS:
            self.assertNotIn(f'"{tid}"', listed, tid)
        self.assertNotIn("star_", src)
        self.assertEqual(18, len(FAMILY_IDS))
        self.assertEqual(4, len(LUX_IDS))

    def test_every_scene_theme_image_exists(self):
        src = read(THEME)
        for tid, drawable in SCENE_IMAGES.items():
            self.assertIn(f'"{drawable}")', src, f"{tid} -> {drawable}")
            found = list((RES / "drawable-nodpi").glob(drawable + ".*"))
            self.assertTrue(found, f"الصورة مفقودة: {drawable}")

    def test_celebrity_assets_are_gone(self):
        for star in ["football", "singer", "idol", "actress", "rap", "dance"]:
            self.assertFalse(
                list((RES / "drawable-nodpi").glob(f"bkg_theme_star_{star}.*")),
                f"صورة مشاهير متبقية: {star}",
            )
        for path in JAVA.rglob("*.java"):
            self.assertNotIn(
                "bkg_theme_star", path.read_text(encoding="utf-8"), str(path)
            )

    def test_legacy_ids_still_resolve_as_hidden_aliases(self):
        src = read(THEME)
        self.assertIn('"samsung_white".equals(id) || "light".equals(id)', src)
        self.assertIn("return imaged(", src)
        self.assertIn("return imagedLight(", src)

    def test_old_star_selections_normalize_in_prefs(self):
        src = read(JAVA / "util/Prefs.java")
        self.assertIn('preferences.getString("theme", "white_pure")', src)
        self.assertIn('if (value.startsWith("star_")) {', src)
        self.assertIn('return "black_graphite";', src)
        self.assertIn('return "sunset_palms";', src)
        self.assertIn('return "purple_waves";', src)

    def test_background_dim_and_surface_override_fields(self):
        src = read(THEME)
        self.assertIn("public final float backgroundDim;", src)
        self.assertIn("public final int surfaceOverride;", src)
        self.assertIn("Math.max(-.85f, Math.min(.85f, dim))", src)
        self.assertIn("surfaceOv != 0", src)

    def test_repository_persists_fields_and_skin(self):
        src = read(REPO)
        for token in ('"bg_dim"', '"surface"', '"key_skin"'):
            self.assertIn(token + ",", src)
        self.assertIn('p.getString(base + "key_skin", "")', src)
        self.assertIn('.putString(b + "key_skin", theme.keySkin)', src)

    def test_renderer_draws_dim_layer_on_both_paths(self):
        src = read(VIEW)
        self.assertIn("private void applyBackgroundDim(Canvas canvas) {", src)
        block = src[src.index("private void drawThemeBackground"):]
        block = block[: block.index("\n  }\n") + 4]
        self.assertEqual(2, block.count("applyBackgroundDim(canvas);"))

    def test_editor_has_dim_slider_and_surface_colors(self):
        src = read(EDITOR)
        self.assertIn("R.id.background_dim", src)
        self.assertIn("R.id.background_dim_value", src)
        # ج68: قسم الألوان أُلغي — حلّه شريط ألوان الخلفية في قسم الخلفيات
        self.assertIn("R.id.background_color_presets", src)
        self.assertIn("backgroundDim,", src)

    def test_long_press_builtin_opens_editor_as_copy(self):
        src = read(EDITOR)
        self.assertIn("private boolean editingBuiltin;", src)
        self.assertIn('editingBuiltin = editingId != null && !editingId.startsWith("custom_");', src)
        self.assertIn('"نسخة من " + ThemeRepository.name(this, editingId)', src)
        self.assertIn(
            "editingId != null && editingId.startsWith(\"custom_\") ? View.VISIBLE : View.GONE",
            src,
        )

    def test_gallery_strip_above_defaults(self):
        layout = read(RES / "layout/activity_themes.xml")
        self.assertIn('android:id="@+id/custom_strip"', layout)
        self.assertIn('android:id="@+id/builtin_section_title"', layout)
        # Round 69: الافتراضية شريط أفقي بعد شريط سماتي — لا شبكة بعد الآن
        self.assertLess(
            layout.index("@+id/custom_strip"), layout.index("@+id/default_strip")
        )
        self.assertNotIn("@+id/themes_grid", layout)
        self.assertTrue((RES / "layout/item_theme_strip.xml").exists())

    def test_gallery_logic_strip_and_long_press(self):
        src = read(GALLERY)
        self.assertIn("private void buildCustomStrip() {", src)
        self.assertIn("ThemeRepository.customIds(this))", src)
        self.assertIn("R.layout.item_theme_strip", src)
        self.assertIn("allIds.addAll(Arrays.asList(ThemeRepository.BUILT_IN_IDS));", src)
        self.assertNotIn("allIds.addAll(ThemeRepository.customIds(this));", src)
        self.assertIn('edit.putExtra("theme_id", id);', src)
        self.assertIn('"تخصيص نسخة من السمة"', src)

    def test_categories_six_families_plus_scenes(self):
        src = read(REPO)
        for cat in ("CATEGORY_WHITE", "CATEGORY_BLACK", "CATEGORY_STONE",
                    "CATEGORY_GIRLY", "CATEGORY_BLUE", "CATEGORY_GRAY"):
            self.assertIn(cat + ' = "', src.replace("  public static final String", ""))
        self.assertNotIn("CATEGORY_STARS", src)
        self.assertIn("private static boolean isFamilyTheme(String id) {", src)
        gallery = read(GALLERY)
        for label in ("أبيض", "أسود", "حجري", "بناتي", "أزرق", "رمادي", "خلفيات"):
            self.assertIn(f'"{label}"', gallery)
        self.assertNotIn("مشاهير", gallery)

    def test_quick_cycle_uses_new_list(self):
        src = read(JAVA / "ime/AlmlkImeRuntimePart2C.java")
        self.assertIn("com.almlk.swiftkey.theme.ThemeRepository.BUILT_IN_IDS", src)
        self.assertNotIn('"samsung_blue"', src)

    def test_no_lambdas_in_modified_java(self):
        import re
        for path in (THEME, REPO, VIEW, EDITOR, GALLERY, JAVA / "util/Prefs.java",
                     JAVA / "settings/ThemeThumbnailView.java",
                     JAVA / "ime/AlmlkImeRuntimePart2C.java"):
            src = read(path)
            code = re.sub(r"/\*[\s\S]*?\*/", " ", src)
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, f"Lambda في {path.name}")

    def test_build_config_untouched(self):
        gradle = read(ROOT / "app/build.gradle")
        self.assertIn("versionCode 36", gradle)
        self.assertIn("versionName '1.9-round36'", gradle)


if __name__ == "__main__":
    unittest.main()
