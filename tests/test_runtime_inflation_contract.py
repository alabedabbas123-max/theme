#!/usr/bin/env python3
"""Regression checks for the two startup InflateException crashes seen on AIDE builds."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res"


class RuntimeInflationContractTest(unittest.TestCase):
    def test_ime_root_uses_framework_class_not_reflection_only_custom_root(self):
        layout = (RES / "layout/ime_view.xml").read_text(encoding="utf-8")
        self.assertIn("<FrameLayout", layout)
        self.assertNotIn("AlmlkInputView", layout)
        self.assertFalse((JAVA / "com/almlk/swiftkey/ime/AlmlkInputView.java").exists())

    def test_no_java_code_references_generated_styleable_arrays(self):
        references = []
        for path in JAVA.rglob("*.java"):
            text = path.read_text(encoding="utf-8")
            if "R.styleable" in text or "obtainStyledAttributes" in text:
                references.append(str(path.relative_to(ROOT)))
        self.assertEqual([], references)

    def test_settings_row_reads_raw_attributes(self):
        source = (
            JAVA / "com/almlk/swiftkey/settings/SettingsHomeRowView.java"
        ).read_text(encoding="utf-8")
        self.assertIn("attrs.getAttributeName(index)", source)
        self.assertIn('attributeText(attrs, "settingsTitle")', source)
        self.assertNotIn("R.styleable", source)

    def test_every_xml_custom_view_has_a_source_class(self):
        missing = []
        pattern = re.compile(r"<(?P<name>com\.almlk\.swiftkey\.[A-Za-z0-9_.$]+)")
        for path in (RES / "layout").glob("*.xml"):
            text = path.read_text(encoding="utf-8")
            for name in pattern.findall(text):
                source = JAVA / (name.replace(".", "/") + ".java")
                if not source.exists():
                    missing.append((path.name, name))
        self.assertEqual([], missing)


if __name__ == "__main__":
    unittest.main()
