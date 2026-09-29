# -*- coding: utf-8 -*-
"""Arabic key placement contract (round 35).

Every kbd_arabic* variant must paint the SAME physical distribution as the reference
photo: row1 ends with د, row2 ends with ذ, and ط/ظ ride the END of row3 (ظ being the
last letter; only function keys may follow). Position is independent of alternatives —
letters never carry width/weight attributes, and ط/ظ must own popupCharacters in every
variant (some layouts used to drop them)."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

A = "{http://schemas.android.com/apk/res/android}"
XML = Path(__file__).resolve().parents[1] / "app/src/main/res/xml"
R1 = list("ض ص ث ق ف غ ع ه خ ح ج د".split())
R2 = list("ش س ي ب ل ا ت ن م ك ذ".split())
R3 = list("ئ ء ؤ ر ى ة و ز ط ظ".split())
FILES = sorted(XML.glob("kbd_arabic*.xml"))
assert FILES, "no kbd_arabic*.xml found"


def row_labels(path, index):
    tree = ET.parse(path)
    row = tree.getroot().findall("Row")[index]
    return [(k.get(A + "keyLabel") or "") for k in row.findall("Key")]


class ArabicRowContract(unittest.TestCase):
    def test_letter_rows_match_reference_distribution(self):
        for p in FILES:
            self.assertEqual(row_labels(p, 1), R1, p.name + " row1")
            self.assertEqual(row_labels(p, 2), R2, p.name + " row2")
            r3 = row_labels(p, 3)
            self.assertEqual(r3[:10], R3, p.name + " row3 letters")
            self.assertTrue(all(x == "" for x in r3[10:]),
                            p.name + " row3: only function keys may follow ظ")

    def test_tah_zah_adjacent_at_the_end_of_row3(self):
        for p in FILES:
            r3 = [x for x in row_labels(p, 3) if x]
            self.assertEqual(r3[-2:], ["ط", "ظ"], p.name + ": ط/ظ must be the final pair")
            self.assertEqual(r3.count("ط"), 1, p.name + ": duplicated ط")
            self.assertEqual(r3.count("ظ"), 1, p.name + ": duplicated ظ")

    def test_tah_zah_have_alternatives_in_every_variant(self):
        for p in FILES:
            text = p.read_text(encoding="utf-8")
            for lab in ("ط", "ظ"):
                m = re.search(r'(?s)<Key\b[^>]*?keyLabel="%s".*?(?:/>|</Key>)' % lab, text)
                self.assertIsNotNone(m, p.name + " missing " + lab)
                self.assertIn("popupCharacters", m.group(0),
                              p.name + ": " + lab + " has no alternatives")

    def test_position_never_coupled_to_alternatives(self):
        for p in FILES:
            text = p.read_text(encoding="utf-8")
            for blk in re.findall(r"(?s)<Key\b.*?(?:/>|</Key>)", text):
                lab = re.search(r'android:keyLabel="(.+?)"', blk)
                if lab and lab.group(1) in R1 + R2 + R3:
                    self.assertNotIn("weight", blk, p.name + ": letter carries a weight attr")
                    self.assertNotIn("keyWidth", blk, p.name + ": letter carries a width attr")

    # ---- round 36: professional faces — the mark IS the first alternative, on every layout ----
    ATTR = r'((?:\\\\.|[^"\\\\])*)'

    def test_trail_mark_is_first_alternative_for_every_single_letter(self):
        for p in FILES:
            text = p.read_text(encoding="utf-8")
            for blk in re.findall(r"(?s)<Key\b.*?(?:/>|</Key>)", text):
                lab = re.search(r'android:keyLabel="%s"' % self.ATTR, blk)
                if not lab:
                    continue
                label = re.sub(r"\\(.)", r"\1", lab.group(1))
                if len(label) != 1:
                    continue
                pop = re.search(r'android:popupCharacters="%s"' % self.ATTR, blk)
                trail = re.search(r'app:trailLabel="%s"' % self.ATTR, blk)
                where = p.name + " " + label
                if pop:
                    popup = re.sub(r"\\(.)", r"\1", pop.group(1))
                    self.assertIsNotNone(trail, where + ": popup without a face mark")
                    face = re.sub(r"\\(.)", r"\1", trail.group(1))
                    self.assertEqual(face, popup[0], where + ": face mark is not its first alternative")
                else:
                    self.assertIsNone(trail, where + ": stray trail mark without a popup")

    def test_glyph_baseline_is_reference_anchored_and_independent_of_marks(self):
        view = (XML.parent.parent / "java/com/almlk/swiftkey/ime/SmartKeyboardView.java").read_text(
            encoding="utf-8")
        self.assertNotIn("face.bottom - dp(2) - metrics.descent", view)
        self.assertNotIn(
            "face.centerY() - (metrics.ascent + metrics.descent) / 2 - dp(1)",
            "optical-center anchoring is retired: the owner reference keyboard pins one baseline")
        self.assertEqual(
            view.count("bounds.top + pressedOffset + bounds.height() * 0.73f"), 1,
            "the letter baseline must sit at 73% of the full key height (reference measure:"
            " 0.729-0.743 across every row), exactly once")
        self.assertIn("paint.setTextAlign(Paint.Align.CENTER);\n      Paint.FontMetrics subMetrics",
                      view)
        self.assertIn(
            "key.subLabel, face.centerX(), face.top + dp(1.5f) - subMetrics.ascent", view)
        self.assertNotIn(
            "key.subLabel, face.right", view,
            "the alternative mark is top-CENTER in the reference, never a right-corner mark")
        self.assertNotIn("Align.RIGHT", view)
        # resting faces are flat: the inset double-frame lines belong to pressed keys only
        self.assertIn("The inset highlight/shade pair reads as a second frame at rest", view)

    def test_theme_letter_sizes_match_the_reference_keyboard(self):
        theme = (XML.parent.parent / "java/com/almlk/swiftkey/theme/KeyboardTheme.java").read_text(
            encoding="utf-8")
        repo = (XML.parent.parent / "java/com/almlk/swiftkey/theme/ThemeRepository.java").read_text(
            encoding="utf-8")
        view = (XML.parent.parent / "java/com/almlk/swiftkey/ime/SmartKeyboardView.java").read_text(
            encoding="utf-8")
        # reference proportions: digit ink = 43% of key height => main em ≈ 54% (26dp on 48dp
        # keys); alternative-mark ink 17-24px => 13dp with a 12dp floor
        self.assertIn('this(b, k, p, t, s, a, 7f, 26f, 13f, "", 1f);', theme)
        self.assertIn("26f,\n        13f,", theme)
        self.assertIn('p.getFloat(base + "main_size", 26f)', repo)
        self.assertIn('p.getFloat(base + "sub_size", 13f)', repo)
        self.assertIn("Math.max(12f, theme.subTextSizeDp)", view)


if __name__ == "__main__":
    unittest.main()
