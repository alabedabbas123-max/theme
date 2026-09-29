# -*- coding: utf-8 -*-
"""Round 66 — إصلاح انهيار واجهة تصميم الثيمات (GridLayout).

طلب المالك: «اصلح الاخطاء التي تؤدي لاغلاق التطبيق عند محاولة فتح واجهة تصميم
الثيمات المخصصة وتحميل الصور والازرار وغيرها» — والسجل الأخير:
java.lang.IllegalArgumentException: column indices (start + span) mustn't
exceed the column count — في addListHeader ← buildFramePresets ←
rebuildControls ← onCreate (أرقام الأسطر تطابق مصدر ج65 حرفياً).

الطبقات التي كشّحتها الجولات السابقة في السجل نفسه (كلها أُصلحت قبل ج66):
- Bitmap معاد تدويره في drawThemeBackground (ج64)
- SecurityException لACCESS_NETWORK_STATE (ج64)
- y must be < bitmap.height() في frameTextColour (ج64)
- NoClassDefFoundError لCustomThemeActivity — الالتقاط غير final (ج65)

وبعد أن صار الصنف يُحمَّل بنجاح ظهرت الطبقة الأخيرة: **تعارض XML/كود** —
التصميم من ج62 يقسم لستة الإطارات على 4 أعمدة (والعرض يُحسب ÷4) لكن XML ظل
يعرّف frame_presets بـ3 أعمدة، بينما addListHeader يمدّد العنوان على 4 →
انهيار فوري عند فتح الواجهة.

الإصلاح:
1. XML: frame_presets صار 4 أعمدة فعلاً (يتوافق مع حساب العرض والتصميم).
2. حارسان في الكود: امتداد العنوان والملاحظة يُشتقان من أعمدة الشبكة
   الفعلية (Math.min مع getColumnCount) — هذا الجنس من التعارض لا ينهر
   التطبيق مرة أخرى حتى لو انزلقت قيمة مستقبلاً.
3. تحديث 3 حرّاس قدامى كانت تحرس القيمة القديمة 3 (سبب بقاء الثغرة).
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LAYOUT = ROOT / "app/src/main/res/layout/activity_custom_theme.xml"
STUDIO = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/CustomThemeActivity.java"
PICKER = ROOT / "app/src/main/java/com/almlk/swiftkey/settings/CustomBackgroundActivity.java"


def read(path):
    return path.read_text(encoding="utf-8")


def xml_columns(xml, grid_id):
    """عدد أعمدة GridLayout من الـXML بحسب المعرّف."""
    i = xml.index('android:id="@+id/%s"' % grid_id)
    seg = xml[i : i + 300]
    self_i = seg.index('android:columnCount="')
    return int(seg[self_i + len('android:columnCount="') : self_i + 400].split('"')[0])


class GridColumnsRound66(unittest.TestCase):
    def test_frame_grid_has_four_columns(self):
        """جذر الإصلاح: لستة الإطارات 4 أعمدة كما يفترض تصميم ج62 وحساب العرض."""
        xml = read(LAYOUT)
        self.assertEqual(4, xml_columns(xml, "button_elements_grid"))

    def test_other_grids_keep_their_design(self):
        """بقية الشبكات كما صُممت: تفاعلات 3، حزم 2، سمات تلقائية 3."""
        xml = read(LAYOUT)
        self.assertEqual(3, xml_columns(xml, "pressfx_presets"))
        self.assertEqual(3, xml_columns(xml, "designer_bg_grid"))  # ج68: خلفيات المكتبة
        self.assertEqual(3, xml_columns(xml, "auto_theme_grid"))
        # ج68: شبكة الحزم أُلغيت — الثيمات في واجهة الثيمات
        self.assertNotIn('android:id="@+id/online_bundle_grid"', xml)

    def test_header_span_never_exceeds_real_columns(self):
        """حارس الانهيار: امتداد addListHeader مشبوك بأعمدة الشبكة الفعلية."""
        src = read(STUDIO)
        self.assertIn("columns = Math.min(columns, grid.getColumnCount());", src)
        self.assertIn("if (columns < 1) { columns = 1; }", src)
        # ج68: فاصل التفاعلات باقٍ — والإطارات بفواصل الشريط الثابت
        self.assertIn(
            'addListHeader(pressFxPresets, "تفاعلات من مكتبة ثيماتي", 3);',
            src,
        )
        self.assertIn("int span = Math.min(4, framePresets.getColumnCount());", src)

    def test_frame_note_span_derived_from_grid(self):
        """ملاحظة الإطارات تمتد بامتداد مشتق من framePresets لا برقم صلب."""
        src = read(STUDIO)
        self.assertIn("int span = Math.min(4, framePresets.getColumnCount());", src)
        self.assertIn("params.columnSpec = android.widget.GridLayout.spec(0, span);", src)

    def test_no_span_larger_than_any_grid(self):
        """كل امتدادات الصف الكامل في الاستوديو ≤ أعمدة شبكة موجودة (2/3/4)."""
        import re

        src = read(STUDIO)
        spans = [int(n) for n in re.findall(r"GridLayout\.spec\(0,\s*(\d+)\)", src)]
        self.assertTrue(spans)
        for span in spans:
            self.assertIn(span, (2, 3, 4), "امتداد غير مألوف: %d" % span)
        # لا يوجد امتداد 4 إلا في سياق عناصر الأزرار (الشبكة الوحيدة ذات 4 أعمدة)
        xml = read(LAYOUT)
        four = [g for g in ("button_elements_grid", "pressfx_presets", "designer_bg_grid", "auto_theme_grid")
                if xml_columns(xml, g) == 4]
        self.assertEqual(["button_elements_grid"], four)

    def test_picker_grids_stay_consistent(self):
        """المنتقي: شبكتاه برمجيتان 3 أعمدة وملاحظته تمتد 3 — بلا تعارض."""
        src = read(PICKER)
        self.assertEqual(2, src.count("setColumnCount(3);"))
        self.assertIn("params.columnSpec = android.widget.GridLayout.spec(0, 3);", src)
        self.assertNotIn("spec(0, 4)", src)


class RegressionRound66(unittest.TestCase):
    def test_rebuild_path_from_oncreate_intact(self):
        """مسار الانهيار الأصلي ما زال متماسكاً: onCreate ← rebuildControls ← البناة."""
        src = read(STUDIO)
        self.assertIn("rebuildControls();", src)
        self.assertIn("private void renderButtonElements()", src)  # ج68: المصيّر الموحد
        self.assertIn("private void addListHeader(GridLayout grid, String title, int columns) {", src)

    def test_no_lambdas_in_touched_java(self):
        import re

        for path in (STUDIO, PICKER):
            code = re.sub(r"//[^\n]*", " ", read(path))
            code = re.sub(r"/\*[\s\S]*?\*/", " ", code)
            self.assertNotIn("->", code, "Lambda في %s" % path.name)


if __name__ == "__main__":
    unittest.main()
