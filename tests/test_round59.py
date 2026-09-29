# -*- coding: utf-8 -*-
"""Round 59 — لستات موحدة 3 في الصف + جلب محسّن + تحديث مباشر للكيبورد كاملاً.

طلب المالك:
1) «اصلح جلب الصور والازرار من الموقع... يجب اصلاح صلاحية الوصول للانترنت وجلب
   الصور من الموقع» — فحص اتصال حديث لا يزعج المتصل، والبنية المجرّبة باقية.
2) «اجعل تصميم الصور بشكل اصغر بحيث تكون 3 صور في الصف» — كل شبكات الاستوديو
   3 أعمدة وبطاقات أصغر (dp88 للنتائج وdp72 للعناوين).
3) «ادمج الصور الخاصة بالتحميل لتكون كلسته واحدة للصور والازرار بشكل لسته واحده
   كتصميم الازرار والصور وليس كشريط منفصل» — لستة واحدة لكل نوع: المدمج ثم
   عناوين الإنترنت ثم نتائجه داخل الشبكة نفسها.
4) «عند اختيار صورة او زر يجب ان يتم تطبيقه مباشرة على الكيبورد كامل مع شريط
   الاقتراحات وشريط الادوات والواجهات بحيث يكون تحديث مباشر» — persistNow عند
   كل اختيار + ملف الثيمات ضمن الاستماع الحي + التوقيع يشمل تفاعل الضغط.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
STUDIO = JAVA / "settings/CustomThemeActivity.java"
PICKER = JAVA / "settings/CustomBackgroundActivity.java"
GATE = JAVA / "settings/AppGate.java"
BASE = JAVA / "ime/AlmlkImeRuntimeBase.java"
PART2A = JAVA / "ime/AlmlkImeRuntimePart2A.java"
STORE = JAVA / "settings/OnlineAssetStore.java"
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


class ThreePerRowRound59(unittest.TestCase):
    def test_unified_grids_three_columns(self):
        """لستة واحدة لكل نوع — شبكة موحدة 3 أعمدة ولا شبكات منفصلة."""
        xml = read(LAYOUT)
        # Round 66: الإطارات 4 أعمدة (تصميم ج62) والتفاعلات 3
        for grid, cols in (("button_elements_grid", "4"), ("pressfx_presets", "3")):
            i = xml.index('android:id="@+id/%s"' % grid)
            self.assertIn('android:columnCount="%s"' % cols, xml[i : i + 300], grid)
        for gone in (
            "online_frame_queries",
            "online_frame_grid",
            "pressfx_queries",
            "pressfx_online_grid",
        ):
            self.assertNotIn('android:id="@+id/%s"' % gone, xml, gone)
        # ج68: شريط أقسام الأزرار الأفقي الثابت فوق الشبكة داخل التراكب
        i = xml.index('android:id="@+id/section_keys_fixed"')
        j = xml.index('android:id="@+id/button_elements_grid"', i)
        self.assertIn('android:id="@+id/button_sections_strip"', xml[i:j])

    def test_card_sizes_smaller(self):
        """Round 62: الإطارات أزرار طولية 4 في الصف؛ التفاعلات كما كانت 3 في الصف."""
        src = read(STUDIO)
        elements = method_body(src, "private void renderButtonElements()")
        self.assertIn(
            "(getResources().getDisplayMetrics().widthPixels - dp(24) - dp(30)) / 4;", elements)
        frames = method_body(src, "private void renderBuiltinFrames(int width)")
        self.assertIn("params.height = frameCardHeight(width);", frames)
        press = method_body(src, "private void buildPressFxPresets()")
        self.assertIn(
            "(getResources().getDisplayMetrics().widthPixels - dp(24) - dp(16)) / 3;", press)
        self.assertIn("params.height = dp(84);", press)
        shapes = method_body(src, "private void renderShapeCards(int width)")
        self.assertIn("params.height = frameCardHeight(width);", shapes)  # ج68: خلايا لا شريط
        pq = method_body(src, "private void buildPressFxSetCards(int width)")
        self.assertIn("params.height = dp(72);", pq)
        card = method_body(src, "private void addOnlineFrameCard(")
        self.assertIn("params.height = frameCardHeight(width);", card)
        pcard = method_body(src, "private void addPressFxCard(")
        self.assertIn("params.height = dp(88);", pcard)
        self.assertNotIn("params.height = dp(96);", pcard)

    def test_note_cells_span_three(self):
        """خلايا الملاحظة والفواصل بعرض اللستة (3 أعمدة)."""
        studio = read(STUDIO)
        self.assertIn("params.columnSpec = android.widget.GridLayout.spec(0, 3);", studio)
        self.assertIn(
            "private void addListHeader(GridLayout grid, String title, int columns) {", studio)


class OneListRound59(unittest.TestCase):
    def test_studio_aliases_single_grid(self):
        """الحقول الأربعة تشير كلها إلى الشبكتين المدمجتين — لستة واحدة."""
        src = read(STUDIO)
        self.assertIn("onlineFrameQueries = framePresets;", src)
        self.assertIn("onlineFrameGrid = framePresets;", src)
        self.assertIn("pressFxQueries = pressFxPresets;", src)
        self.assertIn("pressFxOnlineGrid = pressFxPresets;", src)

    def test_renderers_delegate_to_unified(self):
        """المصيّرات القديمة تفوّض للمصيّر الموحد — لا مسارات مزدوجة."""
        src = read(STUDIO)
        for delegate, unified in (
            ("private void buildPressFxQueries()", "buildPressFxPresets();"),
            ("private void renderPressFx()", "buildPressFxPresets();"),
        ):
            body = method_body(src, delegate)
            self.assertIn(unified, body, delegate)
            self.assertNotIn("addView", body, delegate + " يجب أن يكون تفويضاً خالصاً")

    def test_status_flow_inside_list(self):
        """حالات التحميل/البحث/الخطأ تُرسم داخل اللستة نفسها مع إعادة المحاولة."""
        src = read(STUDIO)
        self.assertIn('frameStatus = "جارٍ تحميل مكتبة ثيماتي…";', src)
        self.assertIn('pressFxStatus = "جارٍ تحميل مكتبة ثيماتي…";', src)
        self.assertIn('frameStatus = "جارٍ جلب أزرار القسم…";', src)
        self.assertIn('pressFxStatus = "جارٍ جلب التفاعلات…";', src)
        self.assertIn("framePresets.addView(onlineFrameNote(frameStatus, frameRetry));", src)
        self.assertIn("pressFxPresets.addView(pressFxNote(pressFxStatus, pressFxRetry));", src)
        # الخطأ يعرض السبب المختصر ويحفظ الاستعلام لإعادة المحاولة
        self.assertIn('"تعذر الوصول لمكتبة ثيماتي (" + OnlineAssetStore.shortError(error)', src)
        self.assertIn("frameRetry = setId;", src)
        self.assertIn("pressFxRetry = setId;", src)
        # النجاح يصفّر الحالة فتُرسم النتائج
        self.assertIn("frameStatus = null;", src)
        self.assertIn("pressFxStatus = null;", src)

    def test_list_headers_separate_built_in_from_online(self):
        """فاصل نصي داخل اللستة يميز جزء الإنترنت عن المدمج."""
        src = read(STUDIO)
        # ج68: أقسام الأزرار رقائق ثابتة في الشريط — لا حاجة لفاصل داخل اللستة
        self.assertIn("private void buildButtonStrip()", src)
        self.assertIn(
            'addListHeader(pressFxPresets, "تفاعلات من مكتبة ثيماتي", 3);', src)

    def test_results_merge_saved_first(self):
        """النتائج داخل اللستة: المحفوظ أولاً ثم نتائج البحث غير المكررة."""
        src = read(STUDIO)
        for renderer in ("private void renderFrameResults(int width)", "private void renderPressFxResults(int width)"):
            body = method_body(src, renderer)
            i_saved = body.index("OnlineAssetStore.saved(this,")
            i_merge = body.index("merged.add(item)")
            self.assertLess(i_saved, i_merge, renderer)
            self.assertIn("merged.isEmpty()", body)
            self.assertIn('"لا عناصر في هذا القسم"', body)

    def test_picker_single_list(self):
        """المنتقي: العناوين والنتائج في شبكة واحدة — لا شريط منفصل."""
        src = read(PICKER)
        body = method_body(src, "private void addOnlineCategory()")
        self.assertIn("onlineGrid = new GridLayout(this);", body)
        self.assertIn("onlineQueries = onlineGrid;", body)
        self.assertNotIn("onlineQueries = new GridLayout(this);", body)
        queries = method_body(src, "private void buildOnlineQueries()")
        self.assertIn("(getResources().getDisplayMetrics().widthPixels - dp(20)) / 3", queries)
        self.assertIn("params.height = dp(72);", queries)
        self.assertIn("onlineQueries.addView(onlineNote(bgStatus, bgRetry));", queries)
        self.assertIn("renderOnlineBackgrounds(cell);", queries)
        results = method_body(src, "private void renderOnlineBackgrounds(int cell)")
        self.assertIn("addOnlineBackgroundCard(merged.get(index), cell);", results)
        self.assertIn('onlineNote("لا عناصر في هذا القسم", null)', results)


class LiveRefreshRound59(unittest.TestCase):
    def test_theme_prefs_registered_live(self):
        """ملف الثيمات ضمن الاستماع الحي — تغييره يحدّث الكيبورد فوراً."""
        src = read(BASE)
        self.assertIn(
            'getSharedPreferences("custom_themes", 0)\n        .registerOnSharedPreferenceChangeListener(liveSettingsListener);',
            src)
        self.assertIn(
            'getSharedPreferences("custom_themes", 0) // Round 59\n        .unregisterOnSharedPreferenceChangeListener(liveSettingsListener);',
            src)

    def test_persist_now_saves_and_applies(self):
        """persistNow يحفظ السمة ويجعلها الفعالة — وزر الحفظ يمر به."""
        src = read(STUDIO)
        body = method_body(src, "private void persistNow()")
        self.assertIn("editingId = ThemeRepository.save(this, editingId, title, current());", body)
        self.assertIn("new Prefs(this).setTheme(editingId);", body)
        self.assertIn("persistNow();\n    Toast.makeText(this, \"تم حفظ وتطبيق السمة\"", src)

    def test_every_pick_persists_immediately(self):
        """كل اختيار صورة/زر يطبَّق فوراً — لا انتظار لزر الحفظ."""
        src = read(STUDIO)
        self.assertGreaterEqual(src.count("persistNow();"), 10)
        for moment in (
            "private void applyOnlineFrame(",  # إطار إنترنت
            "private void applyPressFx(",  # تفاعل إنترنت
        ):
            body = method_body(src, moment)
            self.assertIn("persistNow();", body, moment)

    def test_signature_includes_press_effect(self):
        """توقيع الثيم يشمل تفاعل الضغط — اختياره يصل الكيبورد الحي."""
        src = read(PART2A)
        body = method_body(src, "private int themeSignature(KeyboardTheme theme)")
        self.assertIn("31 * result + theme.pressEffect;", body)
        self.assertIn("31 * result + theme.pressEffectUri.hashCode();", body)


class InternetFixRound59(unittest.TestCase):
    def test_gate_uses_modern_capabilities(self):
        """فحص الاتصال عبر NetworkCapabilities — وأي شك يُفسَّر اتصالاً."""
        src = read(GATE)
        body = method_body(src, "private static boolean connected(Context context)")
        self.assertIn("manager.getActiveNetwork()", body)
        self.assertIn("manager.getNetworkCapabilities(network)", body)
        self.assertIn("caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)", body)
        self.assertIn("return true; // فحص غير حاسم — لا نزعج المستخدم", body)
        self.assertIn("manager.getActiveNetworkInfo()", body) # ارتجاع المستويات القديمة

    def test_fetch_pipeline_untouched(self):
        """Round 67: بنية الجلب الجديدة — أرشيف ثيماتي عن بُعد بطلبات النطاق."""
        thematy = read(JAVA / "settings/ThematyStore.java")
        zipr = read(JAVA / "settings/ZipRangeReader.java")
        self.assertIn(
            "raw.githubusercontent.com/alabedabbas123-max/themes/main/thematy-project.zip", thematy)
        self.assertIn('"AlmlkKeyboard/1.9 (Android keyboard; custom themes)"', thematy)
        self.assertIn('link.setRequestProperty("Range", range);', zipr)
        self.assertIn("public byte[] readFile(String name)", zipr)


class DisciplineRound59(unittest.TestCase):
    def test_no_lambdas_and_balanced(self):
        for path in (STUDIO, PICKER, GATE, BASE, PART2A):
            src = read(path)
            code = re.sub(r"/\*[\s\S]*?\*/", " ", src)
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, "Lambda في %s" % path.name)
            self.assertTrue(balanced(path), "أقواس غير متوازنة في %s" % path.name)


if __name__ == "__main__":
    unittest.main()
