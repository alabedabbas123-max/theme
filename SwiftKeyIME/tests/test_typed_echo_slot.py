# -*- coding: utf-8 -*-
"""Typed-word echo slot contract (round 38).

User report: while autocomplete/autocorrect are enabled, the typed word never
appears in the suggestion strip, so the user cannot keep their exact spelling —
the strip only ever offers corrections/completions.

Fix: while a word is being typed, the TYPED WORD itself owns the third slot
(right chip). Suggestions and the emoji chip never crowd it out. If the typed
word is already visible in the center/left slot (a dictionary echo), the third
slot keeps its normal content instead of repeating the word. Tapping the echo
accepts the exact typed spelling (word + space) and learns it.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PART5 = ROOT / "app/src/main/java/com/almlk/swiftkey/ime/AlmlkImeRuntimePart5.java"


def read(path):
    return path.read_text(encoding="utf-8")


def render_slots(current, candidates):
    """Faithful mirror of renderSuggestionResults' slot decision (Java side is
    authoritative; this copy documents and regression-tests the semantics)."""
    visible = []
    for candidate in candidates:
        if candidate and candidate not in visible and len(visible) < 3:
            visible.append(candidate)
    if len(current) == 0:
        for default in ("مرحباً", "شكراً", "نعم"):
            if len(visible) >= 3:
                break
            if default not in visible:
                visible.append(default)
    elif len(visible) == 0:
        visible.append(current)
    while len(visible) < 3:
        visible.append("")
    best, second, third = visible[0], visible[1], visible[2]
    typed_echo = ""
    if current and current not in (best, second, third):
        typed_echo = current
    left = second
    center = best
    if typed_echo:
        right = typed_echo
    else:
        right = third  # أو الإيموجي عند غياب الصدى
    return left, center, right, typed_echo


class TypedEchoSlotContract(unittest.TestCase):
    def setUp(self):
        self.src = read(PART5)

    def test_typed_echo_computed_while_typing(self):
        self.assertIn("String typedEcho = \"\";", self.src)
        self.assertIn("if (current.length() > 0", self.src)
        for slot in ("best", "second", "third"):
            self.assertIn(f"!current.equals({slot})", self.src)

    def test_echo_renders_in_third_slot_never_as_correction(self):
        self.assertIn("addSuggestion(typedEcho, false, false);", self.src)
        # الرقاقة تعرض الكلمة المطبوعة نفسها دون أي تحويل.
        self.assertNotIn("inlineText(typedEcho)", self.src)

    def test_emoji_only_when_echo_absent(self):
        # الصدى له الأولوية على الإيموجي في الخانة الثالثة أثناء الكتابة.
        self.assertLess(
            self.src.index("if (typedEcho.length() > 0)"),
            self.src.index("String emoji ="),
        )
        self.assertIn("if (typedEcho.length() > 0) {", self.src)
        self.assertIn("} else {", self.src)

    def test_center_best_and_dots_untouched(self):
        self.assertIn("addSuggestion(best, false, isCorrection(current, typedFold, best));", self.src)
        self.assertIn(
            "markCenterSuggestionBest(hasPrioritySuggestion(current, typedFold, best, shortcutExpansion));",
            self.src,
        )

    def test_latest_suggestion_loop_precedes_echo_block(self):
        # اختيار إكمال المسافة يبقى من visibleWords قبل منطق الصدى.
        self.assertLess(
            self.src.index("latestSuggestion = value;"),
            self.src.index("String typedEcho"),
        )

    def test_no_suggestions_keeps_current_in_center(self):
        self.assertIn("visibleWords.add(current);", self.src)

    # ---- سيناريوهات الخانات (مرآة منطق جافا) ----

    def test_scenario_three_candidates_echo_takes_third(self):
        left, center, right, echo = render_slots("مرحب", ["مرحبا", "مرحبتين", "مرحباكم"])
        self.assertEqual((left, center, right), ("مرحبتين", "مرحبا", "مرحب"))
        self.assertEqual(echo, "مرحب")

    def test_scenario_one_candidate(self):
        left, center, right, echo = render_slots("مرحب", ["مرحبا"])
        self.assertEqual((left, center, right), ("", "مرحبا", "مرحب"))

    def test_scenario_no_candidates_current_in_center_no_duplicate(self):
        left, center, right, echo = render_slots("مرحب", [])
        self.assertEqual((left, center, right), ("", "مرحب", ""))
        self.assertEqual(echo, "")

    def test_scenario_dictionary_echo_no_duplicate_third(self):
        left, center, right, echo = render_slots("السلام", ["السلام", "عليكم", "ورحمة"])
        self.assertEqual((left, center, right), ("عليكم", "السلام", "ورحمة"))
        self.assertEqual(echo, "")

    def test_scenario_two_candidates(self):
        left, center, right, echo = render_slots("انشاءالله", ["إن شاء الله", "انشالله"])
        self.assertEqual((left, center, right), ("انشالله", "إن شاء الله", "انشاءالله"))

    def test_scenario_not_typing_defaults(self):
        left, center, right, echo = render_slots("", ["كيف", "الحال"])
        self.assertEqual((left, center), ("الحال", "كيف"))
        self.assertEqual(echo, "")

    def test_build_config_untouched(self):
        gradle = read(ROOT / "app/build.gradle")
        self.assertIn("versionCode 36", gradle)
        self.assertIn("versionName '1.9-round36'", gradle)


if __name__ == "__main__":
    unittest.main()
