import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res"


class CustomViewInflationTest(unittest.TestCase):
    """Sept-20 21:06 log loop: ime_view line 2 inflated com.almlk.swiftkey.ime.AlmlkInputView,
    a class absent from the APK — ClassNotFoundException killed onCreateInputView, so the
    keyboard never appeared. Every custom view tag in every layout must resolve to a real
    source file that carries the (Context, AttributeSet) inflater constructor."""

    TAG = re.compile(r"<(com\.almlk\.[A-Za-z0-9_.]+)")

    def test_every_custom_view_tag_resolves_to_a_source_class(self):
        pairs = set()
        for path in sorted((RES / "layout").glob("*.xml")):
            for fq in self.TAG.findall(path.read_text(encoding="utf-8")):
                pairs.add((fq, path.name))
        self.assertGreater(len(pairs), 8)
        for fq, layout_name in sorted(pairs):
            simple = fq.rsplit(".", 1)[1]
            source = JAVA / (fq.replace(".", "/") + ".java")
            self.assertTrue(
                source.is_file(),
                "layout/%s inflates missing class %s" % (layout_name, fq),
            )
            text = source.read_text(encoding="utf-8")
            self.assertRegex(
                text,
                r"public\s+%s\s*\(\s*Context\b[^)]*AttributeSet" % re.escape(simple),
                "%s lacks the (Context, AttributeSet) constructor the inflater needs" % fq,
            )

    def test_ime_view_root_is_a_platform_frame_layout(self):
        view = (RES / "layout/ime_view.xml").read_text(encoding="utf-8")
        first_tag = re.search(r"<([A-Za-z0-9_.]+)", view[view.index("?>") + 2 :])
        self.assertEqual("FrameLayout", first_tag.group(1))


class StyleableIndependenceTest(unittest.TestCase):
    """Sept-19 log: SettingsHomeRowView crashed on R$styleable resolution (AIDE builds the R
    class without styleable inner classes whenever no declare-styleable is linked — any use of
    R.styleable is a runtime landmine). The whole app must read custom attrs raw instead."""

    def test_no_java_source_references_R_styleable(self):
        offenders = []
        for path in sorted(JAVA.rglob("*.java")):
            if "R.styleable" in path.read_text(encoding="utf-8"):
                offenders.append(path.name)
        self.assertEqual([], offenders)

    def test_settings_home_row_reads_attributes_raw(self):
        row = (JAVA / "com/almlk/swiftkey/settings/SettingsHomeRowView.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("attributeText(attrs", row)
        self.assertNotIn("obtainStyledAttributes", row)


class ToolbarLifecycleGuardTest(unittest.TestCase):
    """Sept-21 03:16 log: onStartInput -> hideResizeOverlay -> setToolBarVisible animated the
    toolBar LinearLayout while onCreateInputView had not run yet — NPE crashed the service on
    every start, so the keyboard stayed invisible. The restore path must survive a null view
    tree, and hideResizeOverlay must no-op before the overlay exists."""

    def test_setToolBarVisible_guards_the_toolbar(self):
        part3 = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart3.java").read_text(
            encoding="utf-8"
        )
        body = part3[part3.index("protected void setToolBarVisible") :]
        body = body[: body.index("\n  }")]
        self.assertIn("if (toolBar != null) {", body)
        self.assertLess(body.index("if (toolBar != null) {"), body.index(".animate()"))

    def test_hideResizeOverlay_returns_early_without_the_overlay(self):
        part2b = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart2B.java").read_text(
            encoding="utf-8"
        )
        body = part2b[part2b.index("protected void hideResizeOverlay") :]
        body = body[: body.index("\n  }")]
        self.assertIn("if (resizeOverlay == null) return;", body)
        self.assertLess(body.index("if (resizeOverlay == null) return;"),
                        body.index("resizeOverlay.releaseTransientState();"))
        # dim-panel model: chrome was never hidden during resize, so hide() restores nothing
        self.assertNotIn("standardKeyboardPanel.setVisibility(View.VISIBLE);", body)
        self.assertNotIn("normalSuggestionRow", body)

    def test_toolbar_visibility_helper_stays_null_safe(self):
        part3 = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart3.java").read_text(
            encoding="utf-8"
        )
        body = part3[part3.index("protected void setToolbarVisibility") :]
        body = body[: body.index("\n  }")]
        self.assertIn("toolBarContainer != null", body)
        self.assertIn("toolBar != null", body)


class ProviderParallelStringsTest(unittest.TestCase):
    """Sept-21 03:24 log: the fallback arabicRows looped 11 top-row letters against 10 digit
    trails — StringIndexOutOfBoundsException length=10 index=10 inside SmartKeyboardView's
    constructor (LayoutProvider.arabic) blew up the whole input-view inflate, so the keyboard
    never appeared. Paired strings must line up and every loop must clamp to both lengths."""

    def test_loops_clamp_letters_to_trails(self):
        src = (JAVA / "com/almlk/swiftkey/ime/LayoutProvider.java").read_text(encoding="utf-8")
        body = src[src.index("private static KeyboardLayout arabicRows") :]
        body = body[: body.index("\n  }")]
        self.assertIn("i < topLetters.length() && i < topDigits.length()", body)
        self.assertIn("i < homeLetters.length && i < homeTrails.length", body)

    def test_parallel_strings_have_matching_lengths(self):
        src = (JAVA / "com/almlk/swiftkey/ime/LayoutProvider.java").read_text(encoding="utf-8")
        body = src[src.index("private static KeyboardLayout arabicRows") :]

        def expand(raw):
            return re.sub(
                r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), raw
            )

        def var(name):
            m = re.search(r'String %s = "((?:[^"\\]|\\.)*)"' % name, body)
            self.assertIsNotNone(m, name)
            return expand(m.group(1))

        self.assertEqual(10, len(var("topLetters")))

        def array_len(name):
            m = re.search(r"String\[\] %s = \{(.*?)\};" % name, body, re.S)
            self.assertIsNotNone(m, name)
            items = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
            return [expand(x) for x in items]

        home_letters = array_len("homeLetters")
        home_trails = array_len("homeTrails")
        self.assertEqual(10, len(home_letters))
        self.assertEqual(home_letters, [x for x in home_letters])
        self.assertEqual(len(home_letters), len(home_trails))
        self.assertEqual("\u0644\u0627", home_trails[4])  # ل keeps its «لا» trail, no shift
        east = re.search(r'topDigits = "((?:\\u[0-9a-fA-F]{4})+)"', body)
        self.assertIsNotNone(east)
        self.assertEqual(10, len(expand(east.group(1))))

    def test_fallback_top_row_matches_the_bare_xml_card(self):
        # 10 digit-trailed letters + ج (+د only on the pc kind) — never a silent duplicate
        src = (JAVA / "com/almlk/swiftkey/ime/LayoutProvider.java").read_text(encoding="utf-8")
        body = src[src.index("private static KeyboardLayout arabicRows") :]
        body = body[: body.index("r.add(top);")]
        self.assertEqual(1, body.count('top.add(k("\\u062c"'), "top row appends ج exactly once")
        self.assertIn('top.add(k("\\u062c", "\\u0686"', body)


if __name__ == "__main__":
    unittest.main()
