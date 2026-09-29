# -*- coding: utf-8 -*-
"""Round 58 — زر الفاصلة/الحافظة وزر النقطة/الاستفهام.

طلب المالك:
1) «اصلح زر ... الذي يمثل الحافظة بحيث عند ضغطه يتم ادخال الفاصلة وعند الضغط
   المطول ينفتح التسجيل» — زر الفاصلة (كان ميكرفوناً يفتح الصوت نقرةً): النقر
   يُدخل «،»، والضغط المطوّل يفتح سجل الحافظة، وأيقونته الصغيرة حافظة.
2) «اصلح الزر الذي يمثل ؟ بحيث يكون عبارة عن . نقطة وال؟ هي الظاهرة في الحرف
   البديل» — زر الصف السفلي قبل الإدخال: «.» أساسية و«؟» ضمن الحرف البديل.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
KEYSPEC = JAVA / "model/KeySpec.java"
VIEW = JAVA / "ime/SmartKeyboardView.java"
PARSER = JAVA / "ime/KeyboardXmlParser.java"
PART3 = JAVA / "ime/AlmlkImeRuntimePart3.java"
THUMB = JAVA / "settings/ThemeThumbnailView.java"
TOOLS = JAVA / "ime/MoreToolsPanelView.java"


def read(path):
    return path.read_text(encoding="utf-8")


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


def method_body(src, signature):
    i = src.index(signature)
    j = src.index("\n  }\n", i)
    return src[i:j]


class CommaKeyRound58(unittest.TestCase):
    def test_tap_inserts_comma_not_voice(self):
        """النقر على زر الفاصلة يُدخل «،» — لم يعد يفتح الصوت."""
        src = read(PART3)
        body = method_body(src, "case KeySpec.MIC:")
        self.assertIn('ic.commitText(key.label == null || key.label.length() == 0 ? "\\u060c" : key.label, 1);', body)
        self.assertNotIn("showVoiceInput();\n        break;", body)

    def test_clipboard_code_defined(self):
        """كود الحافظة الجديد في عقد المفاتيح."""
        src = read(KEYSPEC)
        self.assertIn("CLIPBOARD = -10,", src)
        self.assertIn("/** Round 58: فتح سجل الحافظة (الضغط المطوّل على زر الفاصلة). */", src)

    def test_long_press_fires_clipboard(self):
        """الضغط المطوّل على زر الفاصلة (بلا بدائل) يفتح سجل الحافظة."""
        src = read(VIEW)
        body = method_body(src, "private final Runnable longPress =")
        self.assertIn("} else if (pressed.key.code == KeySpec.MIC) {", body)
        self.assertIn("specialLongPress = true;", body)
        self.assertIn("listener.onKey(CLIPBOARD_KEY);", body)
        self.assertIn(
            'new KeySpec("\\u0627\\u0644\\u062d\\u0627\\u0641\\u0638\\u0629", "", "", KeySpec.CLIPBOARD, 1.1f);',
            src)
        runtime = read(PART3)
        clip = method_body(runtime, "case KeySpec.CLIPBOARD:")
        self.assertIn("showClipboard();", clip)

    def test_release_consumed_after_long_press(self):
        """بعد فتح الحافظة مطولاً: رفع الإصبع لا يُدخل فاصلة — والعلم يُصفَّر في كل مسار."""
        src = read(VIEW)
        self.assertIn("specialLongPress = false; // Round 58: ضغطة جديدة تلغي علم الاستهلاك", src)
        self.assertIn(
            "if (specialLongPress) {\n"
            "            // Round 58: الضغط المطوّل فتح الحافظة — لا تُدخل الفاصلة عند الرفع\n"
            "            specialLongPress = false;\n"
            "            performClick();\n"
            "            return true;\n"
            "          }",
            src)
        # التصفير في الإلغاء وفي تحرير الحالة العابرة
        self.assertGreaterEqual(src.count("specialLongPress = false;"), 4)
        self.assertIn("specialLongPress = false; // Round 58\n          pressed = null;", src)

    def test_comma_key_now_represents_clipboard(self):
        """أيقونة الزر الصغيرة (مكان الحرف البديل) صارت حافظة بدل الميكرفون."""
        view = read(VIEW)
        self.assertIn("? R.drawable.ic_clipboard // Round 58: زر الفاصلة يمثل الحافظة", view)
        self.assertNotIn("? R.drawable.ic_mic", view)
        thumb = read(THUMB)
        self.assertIn("? R.drawable.ic_clipboard // Round 58: زر الفاصلة يمثل الحافظة", thumb)
        self.assertNotIn("? R.drawable.ic_mic", thumb)

    def test_voice_still_reachable_from_toolbar(self):
        """الصوت لم يُفقد: زر شريط الأدوات ولوحة الأدوات يفتحانه كالسابق."""
        self.assertIn("protected void showVoiceInput() {", read(JAVA / "ime/AlmlkImeRuntimeBase.java"))
        tools = read(TOOLS)
        self.assertIn('add("voice", "الصوت", R.drawable.ic_mic, true);', tools)
        self.assertIn('add("clipboard", "الحافظة", R.drawable.ic_clipboard, true);', tools)


class PeriodKeyRound58(unittest.TestCase):
    def test_parser_keeps_dot_in_arabic(self):
        """القارئ لم يعد يستبدل النقطة بعلامة استفهام في التخطيطات العربية."""
        src = read(PARSER)
        self.assertNotIn('label = rtl ? "؟" : ".";', src)
        self.assertNotIn("code = rtl ? '؟' : '.';", src)
        self.assertIn('label = ".";', src)
        self.assertIn("code = '.';", src)

    def test_question_mark_moves_to_alternates(self):
        """«؟» تُضاف للحرف البديل (البديل الأول) في التخطيطات العربية فقط."""
        src = read(PARSER)
        self.assertIn('if (rtl && (alternates == null || !alternates.contains("؟"))) {', src)
        self.assertIn('alternates =\n                  alternates == null || alternates.length() == 0 ? "؟" : "؟ " + alternates;', src)
        # البدائل الممررة للKeySpec الجديد هي المعدلة لا الأصلية
        self.assertIn(
            "transformed.add(\n            new KeySpec(\n                label,\n                key.subLabel,\n                alternates,",
            src)
        # والبريد/الرابط كما هما
        self.assertIn('label = "@";', src)
        self.assertIn('label = "/";', src)

    def test_thumbnail_shows_dot(self):
        """مصغّرة الثيم تعرض «.» في موضع الزر (كانت «؟»)."""
        self.assertIn('{"١٢٣", "", "،", "العربية", ".", ""}', read(THUMB))


class DisciplineRound58(unittest.TestCase):
    def test_no_lambdas_and_balanced(self):
        for path in (VIEW, PARSER, PART3, KEYSPEC, THUMB):
            src = read(path)
            code = re.sub(r"/\*[\s\S]*?\*/", " ", src)
            code = re.sub(r"//[^\n]*", " ", code)
            self.assertNotIn("->", code, "Lambda في %s" % path.name)
            self.assertTrue(balanced(path), "أقواس غير متوازنة في %s" % path.name)


if __name__ == "__main__":
    unittest.main()
