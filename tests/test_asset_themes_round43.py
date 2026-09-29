# -*- coding: utf-8 -*-
"""Round 43 contract: dynamic theme import & management from assets/theme/.

Owner request: مجلد رئيسي assets/theme/ تحته مجلدات ثيمات بأي عدد (kay، kay1، …)
يحتوي كل منها أيقونة الخلفية وأيقونة الأزرار وأيقونة المسافة إن وُجدت وملف
config.json (أو theme.json) بالألوان ومسارات الصور؛ تصغير الصور الكبيرة تلقائياً
لتناسب أزرار الكيبورد بلا تشويه؛ المسافة تأخذ أيقونتها الخاصة وإلا ورثت خلفية
الزر العادية؛ كود Java صرف ينظّم كل ذلك ديناميكياً.
"""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
ASSETS = ROOT / "app/src/main/assets"
LIB = JAVA / "theme/AssetThemeLibrary.java"
KAP = JAVA / "theme/KeyArtProcessor.java"
THEME = JAVA / "theme/KeyboardTheme.java"
REPO = JAVA / "theme/ThemeRepository.java"
VIEW = JAVA / "ime/SmartKeyboardView.java"
THUMB = JAVA / "settings/ThemeThumbnailView.java"
GALLERY = JAVA / "settings/ThemeSettingsActivity.java"

DEMO_FOLDERS = {
# Round 45: الخلفيات بصيغة webp (تقليل الحجم مع الدقة).
    "kay": {"bg.webp", "key.png", "space.png", "config.json"},
    "kay1": {"bg.webp", "key.png", "space.png", "config.json"},  # round46: مسطرة حمراء
}


def read(path):
    return path.read_text(encoding="utf-8")


class AssetThemesRound43(unittest.TestCase):
    def test_library_exists_pure_java_no_lambdas(self):
        self.assertTrue(LIB.exists())
        code = re.sub(r"/\*[\s\S]*?\*/", " ", read(LIB))
        code = re.sub(r"//[^\n]*", " ", code)
        self.assertNotIn("->", code, "Lambda في AssetThemeLibrary")
        self.assertNotIn("val ", code, "صيغة Kotlin")
        self.assertTrue(read(LIB).startswith("package com.almlk.swiftkey.theme;"))

    def test_assets_root_and_dynamic_scan(self):
        src = read(LIB)
        self.assertIn('public static final String ROOT = "theme"', src)
        self.assertIn('public static final String ID_PREFIX = "asset_"', src)
        self.assertIn('public static final String URI_PREFIX = "asset:"', src)
        # مسح ديناميكي عبر AssetManager — لا قائمة مجلدات مثبتة
        self.assertIn("getAssets().list(ROOT)", src)
        self.assertIn("Collections.sort(out)", src)
        self.assertNotIn('"kay1"', src.replace('new String[] {"key", "icon", "button"}', ""))

    def test_config_preference_and_json_parsing(self):
        """config.json مقدَّم على theme.json ثم أي *.json — تحليل org.json."""
        src = read(LIB)
        self.assertIn('String[] preferred = {"config.json", "theme.json"};', src)
        self.assertIn('new JSONObject(new String(buffer.toByteArray(), "UTF-8"))', src)
        self.assertIn("json.optJSONObject(\"images\")", src)
        self.assertIn("json.optString(\"name\", \"\")", src)
        self.assertIn("json.optDouble(name, fallback)", src)

    def test_image_normalization_no_distortion(self):
        """قصّ مركزي على نسبة وجه المفتاح/المسطرة + سقوف أبعاد + عينة مسبقة.
        Round 45: خط المعالجة كله انتقل إلى KeyArtProcessor (الكلاس المساعد)."""
        src = read(KAP)
        self.assertIn("public static final float KEY_ASPECT = 0.63f;", src)
        self.assertIn("public static final float SPACE_ASPECT = 2.88f;", src)
        self.assertIn("public static final int BG_MAX_EDGE = 1080;", src)
        self.assertIn("public static final int KEY_MAX_EDGE = 256;", src)
        self.assertIn("public static final int SPACE_MAX_EDGE = 1024;", src)
        self.assertIn("probe.inJustDecodeBounds = true;", src)
        self.assertIn("options.inSampleSize = sample;", src)
        self.assertIn("Bitmap.createScaledBitmap(", src)
        self.assertIn("Bitmap.createBitmap(src, (w - cropW) / 2, (h - cropH) / 2, cropW, cropH)", src)
        # والمكتبة تفوّض إليه بلا تكرار للمعالجة
        lib = read(LIB)
        self.assertIn("KeyArtProcessor.keyArt(context, spec.key)", lib)
        self.assertNotIn("private static Bitmap normalized(", lib)

    def test_spacebar_fallback_contract(self):
        """أيقونة المسافة إن وُجدت وإلا خلفية الزر العادية نفسها عليها."""
        src = read(LIB)
        self.assertIn('if ("space".equals(part)) {', src)
        self.assertIn(
            "if (spec.space.length() > 0) {\n"
            "        return KeyArtProcessor.spaceArt(context, spec.space);",
            src,
        )
        self.assertIn(
            "return KeyArtProcessor.keyArt(context, spec.key);",
            src,
        )

    def test_press_is_darkened_twin(self):
        src = read(LIB)
        self.assertIn('if ("press".equals(part)) {', src)
        self.assertIn("return KeyArtProcessor.pressTwin(context, spec.key);", src)
        self.assertIn("* 55 / 100", read(KAP))

    def test_keyboard_theme_factory(self):
        src = read(THEME)
        self.assertIn("public static KeyboardTheme assetTheme(", src)
        self.assertIn(
            "bottom, bottom, bottom, accent, 0, dim, 0, skin);", src
        )

    def test_repository_routes_imported_ids(self):
        src = read(REPO)
        self.assertIn('CATEGORY_IMPORTED = "imported"', src)
        self.assertIn(
            "if (id != null && id.startsWith(AssetThemeLibrary.ID_PREFIX)) {\n"
            "      KeyboardTheme imported = AssetThemeLibrary.theme(context, id);",
            src,
        )
        self.assertIn(
            "if (CATEGORY_IMPORTED.equals(category)) return id.startsWith(AssetThemeLibrary.ID_PREFIX);",
            src,
        )
        self.assertIn("String label = AssetThemeLibrary.label(context, id);", src)
        # الثيمات المستوردة ليست ضمن المدمجة المدرجة
        listed = src.split("BUILT_IN_IDS")[1].split("};")[0]
        self.assertNotIn("asset_", listed)

    def test_view_and_thumbnail_asset_branches(self):
        view = read(VIEW)
        self.assertIn(
            "if (value.startsWith(AssetThemeLibrary.URI_PREFIX))\n"
            "      return AssetThemeLibrary.keyboardBackground(getContext(), value);",
            view,
        )
        self.assertIn(
            "if (skin.startsWith(AssetThemeLibrary.URI_PREFIX)) {", view
        )
        self.assertIn("AssetThemeLibrary.keySkinPart(getContext(), skin, parts[i])", view)
        thumb = read(THUMB)
        self.assertIn(
            "if (source.startsWith(AssetThemeLibrary.URI_PREFIX)) {", thumb
        )
        self.assertIn("AssetThemeLibrary.keySkinPart(getContext(), theme.keySkin, part)", thumb)

    def test_shared_bitmaps_never_recycled(self):
        """Round 64: لا إعادة تدوير إطلاقاً في العرض — سجل الأخطاء أثبت أن
        recycle المبكر يكسر الرسم العتادي (recycled bitmap) وعند إعادة
        توصيل العرض؛ الإسقاط والGC يكفيان والفك محدود الحجم أصلاً."""
        view = read(VIEW)
        self.assertIn("private boolean themeImageFromLibrary;", view)
        self.assertNotIn("themeImage.recycle()", view)
        self.assertNotIn("decoded.recycle()", view)
        self.assertIn("themeImage.isRecycled()", view)  # حارس الرسم
        thumb = read(THUMB)
        self.assertIn("private boolean imageFromLibrary;", thumb)
        self.assertNotIn("image.recycle()", thumb)

    def test_gallery_lists_imported_themes_with_chip(self):
        src = read(GALLERY)
        self.assertIn("allIds.addAll(AssetThemeLibrary.ids(this));", src)
        self.assertIn('"مستوردة"', src)
        self.assertIn("ThemeRepository.CATEGORY_IMPORTED", src)

    def test_demo_folders_follow_the_contract(self):
        for folder, files in DEMO_FOLDERS.items():
            base = ASSETS / "theme" / folder
            self.assertTrue(base.is_dir(), folder)
            self.assertEqual(files, {f.name for f in base.iterdir()}, folder)
            cfg = json.loads((base / "config.json").read_text(encoding="utf-8"))
            self.assertIn("name", cfg)
            for role, path in cfg.get("images", {}).items():
                self.assertTrue(
                    (base / path).exists(), f"{folder}/{role} → {path} مفقود"
                )
            for field in ("background", "key"):
                self.assertTrue(re.fullmatch(r"#[0-9A-Fa-f]{6}", cfg[field]), field)
        # kay وkay1 تحملان أيقونة مسافة (round46: مسطرة حمراء للقلب — الاحتياط
        # الآلي بلا مسار خاص يبقى عقداً محروساً في test_spacebar_fallback_contract)
        for folder in ("kay", "kay1"):
            self.assertIn(
                "space",
                json.loads((ASSETS / f"theme/{folder}/config.json").read_text(encoding="utf-8"))["images"],
            )

    def test_demo_art_within_normalized_bounds(self):
        from PIL import Image
        for folder, ratio in (("kay", None), ("kay1", None)):
            key = Image.open(ASSETS / "theme" / folder / "key.png")
            self.assertLessEqual(max(key.size), 256, folder)
            self.assertAlmostEqual(key.size[0] / key.size[1], 0.63, delta=0.02, msg=folder)
            bg = Image.open(ASSETS / "theme" / folder / "bg.webp")
            self.assertLessEqual(max(bg.size), 1080, folder)
        space = Image.open(ASSETS / "theme/kay/space.png")
        # Round 47: زر المسافة بنفس شكل الأزرار — نسبة 2.88 (ثلاث شرائح)
        self.assertLess(space.size[0] / space.size[1], 3.0, "المسطرة خارج عقد الشرائح")
        self.assertGreaterEqual(space.size[0] / space.size[1], 2.5, "المسطرة ضيقة")

    def test_no_kotlin_anywhere(self):
        for path in ROOT.rglob("*.kt"):
            self.fail(f"ملف Kotlin ممنوع: {path}")


if __name__ == "__main__":
    unittest.main()
