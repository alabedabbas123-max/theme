# -*- coding: utf-8 -*-
"""Suggestion inline guard (round 37).

User report: with autocomplete + autocorrect enabled, accepting a suggestion
inserted the word and then a NEW LINE instead of a plain space.

Investigation result: the round-36 tree has exactly one "\\n" producer
(performEnter via the ENTER key). The symptom matches an OLDER installed build,
which could also poison the learned-words SQLite database with words that carry
line breaks; those poisoned words then get re-suggested and re-learned forever.

The fix sanitizes every suggestion at every layer so a committed suggestion can
never move the cursor to a new line, no matter what old data the device carries:
  1. TextNormalizer.inlineText: collapses any whitespace run (\\n, \\r, \\t, runs
     of spaces) into one plain space and trims the result.
  2. WordComposer.replaceCurrent: the single insertion chokepoint for chip taps,
     space-bar completion, autocorrect and gesture auto-accept sanitizes the
     replacement and ignores whitespace-only suggestions.
  3. SuggestionEngine: suggestion output, learn(), editSuggestion(),
     cachedAutocorrection() and expandShortcut() all emit inline-safe words so
     poisoned legacy rows cannot surface, be inserted, or be re-learned.
Existing-separator preservation (an earlier round's contract) stays untouched.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
ENGINE = JAVA / "com/almlk/swiftkey/engine"
IME = JAVA / "com/almlk/swiftkey/ime"


def read(path):
    return path.read_text(encoding="utf-8")


class SuggestionInlineGuard(unittest.TestCase):
    def test_inline_text_exists_and_collapses_whitespace(self):
        src = read(ENGINE / "TextNormalizer.java")
        self.assertIn("public static String inlineText(String value)", src)
        body = src.split("public static String inlineText(String value)", 1)[1]
        body = body.split("public static String lookup", 1)[0]
        self.assertIn("Character.isWhitespace(codePoint)", body)
        self.assertIn("out.append(' ')", body)
        self.assertIn("trim()", body)
        # Null must be handled without crashing.
        self.assertIn('if (value == null) return "";', body)

    def test_replace_current_sanitizes_every_insertion(self):
        src = read(ENGINE / "WordComposer.java")
        self.assertIn("replacement = TextNormalizer.inlineText(replacement);", src)
        # Whitespace-only suggestions must be ignored completely.
        self.assertIn("if (replacement.length() == 0) return;", src)
        # The sanitize must happen before any commit/batch work.
        head = src.split("CharSequence selected = connection.getSelectedText", 1)[0]
        self.assertIn("inlineText(replacement)", head)
        # Existing-separator contract from the previous round stays intact.
        self.assertIn("separatorPrefixLength(after, right)", src)
        self.assertIn("ensureTrailingSpace(connection)", src)

    def test_engine_suggest_output_is_inline_safe(self):
        src = read(ENGINE / "SuggestionEngine.java")
        self.assertIn("String safe = TextNormalizer.inlineText(candidate);", src)
        self.assertIn("if (safe.length() == 0 || output.contains(safe)) continue;", src)

    def test_engine_learning_is_inline_safe(self):
        src = read(ENGINE / "SuggestionEngine.java")
        self.assertIn(
            "TextNormalizer.clean(TextNormalizer.inlineText(word))", src
        )
        self.assertIn(
            "TextNormalizer.clean(TextNormalizer.inlineText(newWord))", src
        )

    def test_engine_correction_and_shortcuts_are_inline_safe(self):
        src = read(ENGINE / "SuggestionEngine.java")
        self.assertIn(
            "TextNormalizer.inlineText(intelligence.cachedAutocorrection(inputWord, language))",
            src,
        )
        self.assertIn(
            "TextNormalizer.inlineText(shortcuts.expansion(shortcut, language))", src
        )

    def test_engine_gesture_output_is_inline_safe(self):
        src = read(ENGINE / "SuggestionEngine.java")
        self.assertIn("String safe = TextNormalizer.inlineText(value.word);", src)

    def test_single_newline_commit_invariant(self):
        """performEnter must stay the ONLY code path committing a literal newline."""
        offenders = []
        for path in JAVA.rglob("*.java"):
            for lineno, line in enumerate(read(path).splitlines(), 1):
                if 'commitText("\\n"' in line:
                    offenders.append(f"{path.name}:{lineno}")
        self.assertEqual(
            offenders,
            ["AlmlkImeRuntimePart4.java:149"],
            f"مسارات إدراج سطر جديد غير متوقعة: {offenders}",
        )

    def test_no_keycode_enter_or_raw_line_separators(self):
        for path in JAVA.rglob("*.java"):
            src = read(path)
            self.assertNotIn(
                "KEYCODE_ENTER", src, f"KEYCODE_ENTER غير مسموح في {path.name}"
            )
            for pattern in ('commitText("\\r', "System.lineSeparator"):
                self.assertNotIn(pattern, src, f"{pattern} غير مسموح في {path.name}")

    def test_suggestion_word_can_never_carry_newline(self):
        """الشكل النوعي: أي سلسلة تمر إلى replaceCurrent تمر عبر inlineText أولاً."""
        composer = read(ENGINE / "WordComposer.java")
        self.assertLess(
            composer.index("inlineText(replacement)"),
            composer.index("connection.commitText(replacement"),
            "التطهير يجب أن يسبق commitText في replaceCurrent",
        )

    def test_build_config_untouched(self):
        gradle = read(ROOT / "app/build.gradle")
        self.assertIn("versionCode 36", gradle)
        self.assertIn("versionName '1.9-round36'", gradle)


if __name__ == "__main__":
    unittest.main()
