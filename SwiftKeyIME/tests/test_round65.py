# -*- coding: utf-8 -*-
"""Round 65 — إصلاح انهيار واجهة التصميم (NoClassDefFoundError) بضبط final.

طلب المالك: «واجهة التصميم لا تعمل ويتم اغلاقها مباشرة عند محاولة الدخول اليها
ولا تنسئ ان تففص الاستدعاءات التي يجب ان تكون فاينل في الاكواد» — والسجل فيه:
java.lang.NoClassDefFoundError: com.almlk.swiftkey.settings.CustomThemeActivity

التشخيص: جولة 63 أدخلت التقاط متغيرات محلية غير معلنة final داخل الأصناف
المجهولة (art في الاستوديو وframe في المنتقي) — جافاك 8 يقبلها (effectively
final) لكن مترجم AIDE يعمل بقواعد جافا 7 حيث الالتقاط غير النهائي **خطأ
ترجمة**، فيبنى APK ناقص بلا الصنف → NoClassDefFoundError عند الدخول.

الإصلاح:
1. كل الملتقَطات صارت final صراحةً (21 تعريفاً في الاستوديو والمنتقي).
2. التحقق الرسمي صار بالمصدر 7 (= قواعد AIDE): javac -source 7 → صفر أخطاء.
3. مدقق دائم «final_capture_audit» (tools/) — مسح خطي بنطاق الدالة —
   يمنع تكرار هذا الجنس من الأخطاء في أي جولة قادمة.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"
STUDIO = JAVA / "com/almlk/swiftkey/settings/CustomThemeActivity.java"
PICKER = JAVA / "com/almlk/swiftkey/settings/CustomBackgroundActivity.java"
AUDITOR = ROOT / "tools/final_capture_audit.py"


def read(path):
    return path.read_text(encoding="utf-8")


class FinalCapturesRound65(unittest.TestCase):
    def test_captured_view_locals_are_final(self):
        """المتغيرات الملتقَطة في مستمعات النقر معلنة final صراحةً."""
        studio = read(STUDIO)
        self.assertIn("final FrameLayout art = new FrameLayout(this);", studio)
        self.assertIn("final LinearLayout card = new LinearLayout(this);", studio)
        self.assertIn("final ImageView image = new ImageView(this);", studio)
        picker = read(PICKER)
        self.assertIn("final FrameLayout frame = new FrameLayout(this);", picker)
        # لا تعريف غير نهائي متبقٍ لهذه الأسماء في بناة البطاقات
        for src in (studio, picker):
            for line in src.splitlines():
                st = line.strip()
                for bad in (
                    "FrameLayout art = new",
                    "LinearLayout card = new",
                    "ImageView image = new",
                    "FrameLayout frame = new",
                ):
                    if st.startswith(bad):
                        self.fail("تعريف غير نهائي: %s" % st)

    def test_ring_and_guard_capture_final_chain(self):
        """سلسلة الالتقاط المزدوجة (حلقة التحميل داخل مستمع داخل مستمع) نهائية كلها."""
        body = read(STUDIO)
        self.assertIn("final android.widget.ProgressBar ring =", body)
        self.assertIn("for (int child = 0; child < art.getChildCount(); child++)", body)


class AuditorGuardRound65(unittest.TestCase):
    def test_auditor_reports_zero_on_the_project(self):
        """المدقق على المشروع كله: صفر التقاط غير نهائي (قواعد AIDE)."""
        import sys

        sys.path.insert(0, str(ROOT / "tools"))
        import final_capture_audit as auditor
        from pathlib import Path as P

        files = sorted((ROOT / "app/src/main/java").rglob("*.java"))
        self.assertGreater(len(files), 80)
        findings = []
        for path in files:
            findings.extend("%s:%d %s" % (path.name, line, name) for line, name in auditor.audit_file(path))
        self.assertEqual([], findings, "التقاط غير نهائي داخل أصناف مجهولة:\n" + "\n".join(findings))

    def test_auditor_catches_the_original_bug(self):
        """المدقق يكشف الخطأ الأصلي (art غير النهائية) ويتجاوز السليم."""
        import sys

        sys.path.insert(0, str(ROOT / "tools"))
        import final_capture_audit as auditor

        buggy = (
            "package x;\n"
            "public class T {\n"
            "  private void build() {\n"
            "    FrameLayout art = new FrameLayout(null);\n"
            "    art.setOnClickListener(new View.OnClickListener() {\n"
            "      public void onClick(View v) { art.invalidate(); }\n"
            "    });\n"
            "    final FrameLayout ok = new FrameLayout(null);\n"
            "    ok.setOnClickListener(new View.OnClickListener() {\n"
            "      public void onClick(View v) { ok.invalidate(); }\n"
            "    });\n"
            "  }\n"
            "}\n"
        )
        tmp = ROOT / "build_auditor_selftest"
        target = tmp / "app/src/main/java/T.java"
        target.parent.mkdir(parents=True, exist_ok=True)
        try:
            target.write_text(buggy, encoding="utf-8")
            self.assertEqual([(4, "art")], auditor.audit_file(target))
        finally:
            import shutil

            shutil.rmtree(tmp, ignore_errors=True)

    def test_auditor_tool_exists(self):
        self.assertTrue(AUDITOR.exists())


class DisciplineRound65(unittest.TestCase):
    def test_no_lambdas_in_touched_files(self):
        import re

        for path in (STUDIO, PICKER):
            code = re.sub(r"//[^\n]*", " ", read(path))
            code = re.sub(r"/\*[\s\S]*?\*/", " ", code)
            self.assertNotIn("->", code, "Lambda في %s" % path.name)


if __name__ == "__main__":
    unittest.main()
