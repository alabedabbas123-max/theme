# -*- coding: utf-8 -*-
"""Round 57 — تفعيل صلاحية الإنترنت عند الفتح + قوائم بنفس تصميم الافتراضية + final.

طلب المالك:
1) «اضف تفعيل صلاحية الانترنت عند فتح التطبيق android.permission.INTERNET بحيث
   يتم فتح الصلاحية ان كانت غير مفعله» — بوابة AppGate عند فتح التطبيق.
2) «اجعل قائمة الصور التي تظهر والمفاتيح التي تظهر بنفس تصميم الصور والمفاتيح
   الافتراضيه وليس كشريط افقي» — عناوين التحميل (الرقائق الأفقية سابقاً) بطاقات
   بنفس تصميم البطاقات الافتراضية، وبطاقات نتائج الإنترنت بتسمية كالافتراضي.
3) «اجعل context في public static void bundles( الخاص بكلاس BundleListener فاينل»
   و«image في اكواد addOnlineBackgroundCard و addOnlineFrameCard» فاينل.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GATE = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/AppGate.java"
STUDIO = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/CustomThemeActivity.java"
PICKER = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/CustomBackgroundActivity.java"
READER = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/ThematyStore.java"
SETTINGS = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/SettingsActivity.java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
LAYOUT = ROOT / "app/src/main/res/layout/activity_custom_theme.xml"


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


class InternetGateRound57(unittest.TestCase):
    def test_gate_class_exists(self):
        """بوابة الإنترنت: طلب الصلاحية رسمياً إن لم تكن مفعلة ثم فحص الاتصال."""
        src = read(GATE)
        self.assertIn("public static void ensureInternet(final Activity activity)", src)
        self.assertIn("android.Manifest.permission.INTERNET", src)
        self.assertIn("activity.requestPermissions(", src)
        self.assertIn("checkSelfPermission", src)
        self.assertIn("Build.VERSION.SDK_INT >= 23", src)  # أمان المستويات القديمة
        self.assertIn("private static boolean connected(Context context)", src)
        self.assertIn("getActiveNetworkInfo", src)
        self.assertIn("info.isConnected()", src)

    def test_gate_guides_in_arabic(self):
        """الحوار الإرشادي: واي فاي أو تفاصيل التطبيق — بالعربية."""
        src = read(GATE)
        self.assertIn('"تفعيل الإنترنت"', src)
        self.assertIn("Settings.ACTION_WIFI_SETTINGS", src)
        self.assertIn("Settings.ACTION_APPLICATION_DETAILS_SETTINGS", src)
        self.assertIn('"إعدادات الواي فاي"', src)
        self.assertIn('"تفاصيل التطبيق"', src)
        self.assertIn('"لاحقاً"', src)

    def test_manifest_permissions(self):
        src = read(MANIFEST)
        self.assertIn("android.permission.INTERNET", src)
        self.assertIn("android.permission.ACCESS_NETWORK_STATE", src)

    def test_every_entry_calls_the_gate(self):
        """القاذف (SettingsActivity) والاستوديو ومنتقي الخلفيات يستدعون البوابة."""
        for path in (SETTINGS, STUDIO, PICKER):
            src = read(path)
            self.assertIn("AppGate.ensureInternet(this);", src, path.name)
            body = method_body(src, "protected void onCreate(Bundle")
            self.assertIn("AppGate.ensureInternet(this);", body, path.name)

    def test_gate_discipline(self):
        src = read(GATE)
        code = re.sub(r"/\*[\s\S]*?\*/", " ", src)
        code = re.sub(r"//[^\n]*", " ", code)
        self.assertNotIn("->", code, "Lambda في البوابة")
        self.assertTrue(balanced(GATE), "أقواس غير متوازنة في البوابة")


class SameDesignRound57(unittest.TestCase):
    def test_studio_query_lists_are_grids_not_strips(self):
        """Round 59: لستة واحدة لكل نوع — الشبكة الموحدة 3 أعمدة ولا شبكات منفصلة."""
        xml = read(LAYOUT)
        # Round 66: الإطارات 4 أعمدة (تصميم ج62) والتفاعلات 3
        for grid, cols in (("button_elements_grid", "4"), ("pressfx_presets", "3")):
            i = xml.index('android:id="@+id/%s"' % grid)
            self.assertIn("<GridLayout", xml[i - 60 : i], grid)
            self.assertIn('android:columnCount="%s"' % cols, xml[i : i + 300], grid)
        for gone in ("online_frame_queries", "online_frame_grid", "pressfx_queries", "pressfx_online_grid"):
            self.assertNotIn('android:id="@+id/%s"' % gone, xml, "%s يجب أن يُدمج" % gone)

    def test_studio_query_cards_match_default_design(self):
        """بطاقات العناوين: بنفس عرض/ارتفاع البطاقات الافتراضية (عرض/5 × dp84)
        وحد مدور والمختار كهرماني."""
        src = read(STUDIO)
        # Round 59: بطاقات العناوين خلايا داخل اللستة الموحدة — 3 في الصف × dp72
        # Round 62: بطاقات عناوين الإطارات أزرار طولية — والتفاعلات كما كانت dp72
        # ج68: أقسام الأزرار رقائق في الشريط الثابت — والمختار أزرق
        chips = method_body(src, "private void styleButtonChips()")
        self.assertIn("drawable.setCornerRadius(dp(17));", chips)
        self.assertIn("0xff2e73db", chips)
        strip = method_body(src, "private void addButtonStripChip(")
        self.assertIn("selectButtonSection(id);", strip)
        body = method_body(src, "private void buildPressFxSetCards(int width)")
        self.assertIn("border.setCornerRadius(dp(12));", body)
        self.assertIn("params.height = dp(72);", body)
        self.assertIn("0xffffc400", body)  # تمييز المختار كهرماني كالافتراضي
        self.assertIn("showThematyPressFx(setId)", body)

    def test_picker_query_list_is_grid_not_strip(self):
        """منتقي الخلفيات: العناوين شبكة ثلاثية الأعمدة لا HorizontalScrollView."""
        src = read(PICKER)
        body = method_body(src, "private void addOnlineCategory()")
        # Round 59: لستة واحدة — العناوين والنتائج في الشبكة نفسها
        self.assertIn("onlineGrid = new GridLayout(this);", body)
        self.assertIn("onlineGrid.setColumnCount(3);", body)
        self.assertIn("onlineQueries = onlineGrid;", body)
        self.assertNotIn("HorizontalScrollView", body)
        queries = method_body(src, "private void buildOnlineQueries()")
        self.assertIn("(getResources().getDisplayMetrics().widthPixels - dp(20)) / 3", queries)
        self.assertIn("border.setCornerRadius(dp(7));", queries)
        self.assertIn("0xff168fe5", queries)  # الأزرق المميز للمنتقي كالافتراضي
        self.assertIn("showThematyBackgrounds(setId)", queries)

    def test_online_cards_carry_labels_like_defaults(self):
        """بطاقات نتائج الإنترنت بتسمية عربية أسفل الصورة كالبطاقات الافتراضية."""
        src = read(STUDIO)
        for builder, grid in (
            ("private void addOnlineFrameCard(", "onlineFrameGrid.addView(card, params);"),
            ("private void addPressFxCard(", "pressFxOnlineGrid.addView(card, params);"),
        ):
            body = method_body(src, builder)
            self.assertIn("label.setText(item.title);", body)
            self.assertIn("label.setMaxLines(1);", body)
            self.assertIn(grid, body)
        # Round 62: بطاقات الإطارات أزرار طولية بنسبة المفتاح
        frames = method_body(src, "private void addOnlineFrameCard(")
        self.assertIn("params.height = frameCardHeight(width);", frames)

    def test_fields_are_grids(self):
        self.assertIn("private GridLayout onlineFrameQueries;", read(STUDIO))
        self.assertIn("private GridLayout pressFxQueries;", read(STUDIO))
        self.assertIn("private GridLayout onlineQueries;", read(PICKER))


class FinalFixesRound57(unittest.TestCase):
    def test_bundles_context_is_final(self):
        """Round 67: معاملات تثبيت الثيم فاينل كما طلب المالك دائماً."""
        self.assertIn(
            "public static void installTheme(",
            read(READER))
        self.assertIn("final Context context,", read(READER))

    def test_online_card_images_are_final(self):
        """image فاينل في بطاقتي الإطار والخلفية الأونلاين (وبطاقة التفاعل كذلك)."""
        studio = read(STUDIO)
        for builder in ("private void addOnlineFrameCard(", "private void addPressFxCard("):
            body = method_body(studio, builder)
            self.assertIn("final ImageView image = new ImageView(this);", body)
        picker = read(PICKER)
        body = method_body(picker, "private void addOnlineBackgroundCard(")
        self.assertIn("final ImageView image = new ImageView(this);", body)

    def test_discipline(self):
        for path in (STUDIO, PICKER, READER, SETTINGS):
            src = read(path)
            code = re.sub(r"/\*[\s\S]*?\*/", " ", src)
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, "Lambda في %s" % path.name)
            self.assertTrue(balanced(path), "أقواس غير متوازنة في %s" % path.name)


if __name__ == "__main__":
    unittest.main()
