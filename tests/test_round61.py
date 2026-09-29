# -*- coding: utf-8 -*-
"""Round 61 — إصلاح خطأ البناء الظاهر في لقطة AIDE.

شخّصنا لقطة المالك: علامات حمراء على استدعاءي this(...) (السطران 150-151 و176-177)
وعلى بانيي KeyboardTheme (155 و184) — والسبب: الباني الرئيسي صار 23 معاملاً منذ
جولة 56 (أضيف frameUri ثم effect+effectUri) بينما استدعاءا التفويض ما زالا يمرران
21 وسيطة => «constructor KeyboardTheme cannot be applied to given types».

الإصلاح المزدوج:
1. تفويض الباني-19: `..., skin, 0, "")` => `..., skin, 0, "", 0, "")`
2. تفويض الباني-20: `..., skin, frame, "")` => `..., skin, frame, "", 0, "")`

وكُشف أثناءها خطآن من جنس آخر في SmartKeyboardView (تمرير double إلى dp(float))
أُصلحا بصرْح (float). ثم جُمِّع المشروع كله بـjavac فعلي (android.jar + androidx
من مافن) — 90 ملفاً و447 class و**صفر أخطاء** — وهذا التحقق الجديد لا يستبدل
الاختبارات الساكنة بل يكملها.

الحرّاس أدناه تُبقي الفئتين من الأخطاء مستحيلة الحدوث مستقبلاً دون javac:
- كل استدعاء this(...) في أي ملف يجب أن يطابق عدد معاملات بانيٍ معلنٍ في الملف نفسه.
- أي تمرير لـMath.random() إلى dp() يجب أن يحمل صرْح (float).
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java"
THEME = JAVA / "com/almlk/swiftkey/theme/KeyboardTheme.java"
VIEW = JAVA / "com/almlk/swiftkey/ime/SmartKeyboardView.java"


def read(path):
    return path.read_text(encoding="utf-8")


def strip_comments(src):
    src = re.sub(r"/\*[\s\S]*?\*/", " ", src)
    src = re.sub(r"//[^\n]*", " ", src)
    return src


def constructor_arities(src):
    """أعداد معاملات كل باني معلن في الملف."""
    arities = []
    for m in re.finditer(r"(?:public|private|protected)\s+\w+\s*\(", src):
        head = src[: m.start()].rstrip().split()[-1] if src[: m.start()].strip() else ""
        if head and re.search(r"(?:public|private|protected)\s+%s\s*\($" % re.escape(head), src[m.start() - len(head) - 2: m.end()]):
            # باني: نفس اسم الصنف
            if not re.search(r"\bclass\s+%s\b" % re.escape(head), src):
                continue
        i, depth, args = m.end(), 1, ""
        while i < len(src) and depth:
            if src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
                if depth == 0:
                    break
            args += src[i]
            i += 1
        params = [a for a in args.split(",") if a.strip()]
        arities.append((src[: m.start()].count("\n") + 1, len(params), head))
    return arities


def this_call_arities(src):
    """أعداد وسائط كل استدعاء this(...) في الملف."""
    calls = []
    for m in re.finditer(r"(?<![.\w])this\s*\(", src):
        i, depth, args = m.end(), 1, ""
        while i < len(src) and depth:
            c = src[i]
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    break
            args += c
            i += 1
        parts, depth2, cur = [], 0, ""
        for ch in args:
            if ch in "([":
                depth2 += 1
            elif ch in ")]":
                depth2 -= 1
            if ch == "," and depth2 == 0:
                parts.append(cur)
                cur = ""
            else:
                cur += ch
        if cur.strip() or parts:
            parts.append(cur)
        parts = [p for p in parts if p.strip()]
        calls.append((src[: m.start()].count("\n") + 1, len(parts)))
    return calls


class ConstructorArityGuard(unittest.TestCase):
    def test_every_this_call_matches_a_declared_constructor(self):
        """لا استدعاء this(...) إلا وعدد وسائطه يطابق بانيًا في الملف نفسه."""
        files = sorted(JAVA.rglob("*.java"))
        self.assertGreater(len(files), 80)
        offenders = []
        for path in files:
            src = strip_comments(read(path))
            arities = {n for _, n, _ in constructor_arities(src)}
            for line, argc in this_call_arities(src):
                if argc not in arities:
                    offenders.append("%s:%d this(%d وسائط) والبواني: %s" % (path.name, line, argc, sorted(arities)))
        self.assertEqual([], offenders, "استدعاءات this لا تطابق أي باني:\n" + "\n".join(offenders))

    def test_keyboard_theme_chain_restored(self):
        """سلسلة كومنز بعد الإصلاح: باني 23 معاملاً وتفويغان بـ23 وسيطة."""
        src = read(THEME)
        # Round 69: مقياسا عرض/ارتفاع الزر أُضيفا لآخر الباني (23→25)
        self.assertIn('shift, font, dim, surfaceOv, skin, 0, "", 0, "", 1f, 1f);', src)
        self.assertIn('shift, font, dim, surfaceOv, skin, frame, "", 0, "", 1f, 1f);', src)
        clean = strip_comments(src)
        arities = sorted(n for _, n, _ in constructor_arities(clean))
        self.assertEqual([6, 10, 11, 12, 14, 19, 20, 25], arities)
        for _, argc in this_call_arities(clean):
            self.assertIn(argc, arities)


class FloatCastGuard(unittest.TestCase):
    def test_no_raw_double_into_dp(self):
        """Math.random() داخل dp() لا يمر إلا بصرْح (float)."""
        bad = []
        for path in sorted(JAVA.rglob("*.java")):
            for num, line in enumerate(read(path).splitlines(), 1):
                if "Math.random" in line and re.search(r"\bdp\s*\(", line):
                    if not re.search(r"\(\s*float\s*\)", line):
                        bad.append("%s:%d %s" % (path.name, num, line.strip()))
        self.assertEqual([], bad, "تمرير double إلى dp(float) بلا صرْح:\n" + "\n".join(bad))

    def test_press_particle_lines_fixed(self):
        src = read(VIEW)
        self.assertIn("particle.vy = -dp((float) (54 + Math.random() * 48));", src)
        self.assertIn("float speed = dp((float) (58 + Math.random() * 60));", src)


class DisciplineRound61(unittest.TestCase):
    def test_no_lambdas_in_touched_files(self):
        for path in (THEME, VIEW):
            code = strip_comments(read(path))
            self.assertNotIn("->", code, "Lambda في %s" % path.name)

    def test_round_files_present(self):
        self.assertTrue(THEME.exists() and VIEW.exists())


if __name__ == "__main__":
    unittest.main()
