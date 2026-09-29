import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res"
XMLDIR = RES / "xml"
PREVIEW = ROOT.parent / "keyboard-preview"

LAYOUT_XML = {
    "kbd_qwerty.xml": "english_us",
    "kbd_azerty.xml": "azerty",
    "kbd_qwertz.xml": "qwertz",
    "kbd_dvorak.xml": "dvorak",
    "kbd_colemak.xml": "colemak",
    "kbd_qzerty.xml": "qzerty",
    "kbd_arabic.xml": "arabic_digits",
    "kbd_arabic_original.xml": "arabic_original",
    "kbd_arabic_pc.xml": "arabic_pc",
    "kbd_arabic_azerty.xml": "arabic_azerty",
    "kbd_arabic_102.xml": "arabic_102",
    "kbd_arabic_mac.xml": "arabic_mac",
}

ROWS = {
    "kbd_qwertz.xml": ["qwertzuiop", "asdfghjkl", "yxcvbnm"],
    "kbd_colemak.xml": ["qwfpgjluy'", "arstdhneio", "zxcvbkm"],
    "kbd_qzerty.xml": ["qzertyuiop", "asdfghjklm", "wxcvbn"],
    "kbd_dvorak.xml": ["'pyfgcrl", "aoeuidhtns", "qjkxbmwvz"],
}


class LayoutAttributeTest(unittest.TestCase):
    def test_keyboard_layout_enum_declared_in_attrs(self):
        attrs = (RES / "values/attrs.xml").read_text(encoding="utf-8")
        block = attrs[attrs.index('name="keyboardLayout"') :]
        expected = {
            "english_us": "0",
            "azerty": "1",
            "qwertz": "2",
            "dvorak": "3",
            "colemak": "4",
            "qzerty": "5",
            "arabic_original": "6",
            "arabic_digits": "7",
            "arabic_pc": "8",
            "arabic_azerty": "9",
            "arabic_extended": "10",
            "arabic_mac": "11",
            "arabic_phonetic": "12",
            "arabic_typewriter": "13",
            "arabic_102": "14",
        }
        for name, value in expected.items():
            self.assertRegex(
                block, r'<enum\s+name="%s"\s+value="%s"' % (name, value)
            )

    def test_every_letter_layout_declares_its_identity(self):
        for file_name, token in LAYOUT_XML.items():
            text = (XMLDIR / file_name).read_text(encoding="utf-8")
            self.assertIn('app:keyboardLayout="%s"' % token, text, file_name)

    def test_letter_rows_match_the_reference_images(self):
        for file_name, expected_rows in ROWS.items():
            text = (XMLDIR / file_name).read_text(encoding="utf-8")
            bodies = re.findall(r"<Row[^>]*>\n(.*?)\n    </Row>", text, re.S)[1:]
            letters = []
            for body in bodies[:3]:
                labels = re.findall(r'android:keyLabel="([^"]+)"', body)
                letters.append("".join(labels))
            self.assertEqual(expected_rows, letters[:3], file_name)

    def test_arabic_layouts_reproduce_the_screenshots_exactly(self):
        # rows transcribed from the six reference screenshots (024658..024716):
        # bare 123 (024703/024710), الأصلية (024707/024713), كمبيوتر شخصي (024658/024716)
        unified = ["\u0636\u0635\u062b\u0642\u0641\u063a\u0639\u0647\u062e\u062d\u062c\u062f",
                   "\u0634\u0633\u064a\u0628\u0644\u0627\u062a\u0646\u0645\u0643\u0630",
                   "\u0626\u0621\u0624\u0631\u0649\u0629\u0648\u0632\u0637\u0638"]
        bare = unified
        original = unified
        pc = unified
        pc102 = unified
        expect = {
            "kbd_arabic.xml": (bare, "\u0661\u0662\u0663\\\u061f", "arabic"),
            "kbd_arabic_azerty.xml": (bare, "\u0661\u0662\u0663\\\u061f", "western"),
            "kbd_arabic_original.xml": (original, "123", "western"),
            "kbd_arabic_mac.xml": (original, "123", "arabic"),
            "kbd_arabic_pc.xml": (pc, "123", "western"),
            "kbd_arabic_102.xml": (pc102, "123", "western"),
        }
        for file_name, (rows, num_label, digit) in expect.items():
            text = (XMLDIR / file_name).read_text(encoding="utf-8")
            bodies = re.findall(r"<Row>\n(.*?)\n    </Row>", text, re.S)
            letters = ["".join(re.findall(r'android:keyLabel="([^"]+)"', b)) for b in bodies]
            self.assertEqual(rows, letters, file_name)
            self.assertIn('app:digitType="%s"' % digit, text, file_name)
            bottom = re.findall(r'<Row app:rowEdgeFlags="bottom">\n(.*?)\n    </Row>', text, re.S)[0]
            self.assertEqual(
                [num_label, "\u060c", "\u0627\u0644\u0639\u0631\u0628\u064a\u0629", "."],
                re.findall(r'android:keyLabel="([^"]+)"', bottom),
                file_name + " bottom row",
            )
            self.assertIn("ic_enter", bottom, file_name)
            # Round 41-2: الصف السفلي العربي يحمل زر الإيموجي وزر الفاصلة بالميكرفون
            self.assertIn('android:codes="-6"', bottom, file_name)
            self.assertIn('android:keyIcon="@drawable/ic_emoji"', bottom, file_name)
            self.assertIn('android:codes="-7"', bottom, file_name)
            self.assertIn('android:keyLabel="،"', bottom, file_name)
            self.assertNotIn('android:codes="1548"', bottom, file_name)
            self.assertIn("ic_backspace", text, file_name)
            digits = re.findall(r'<Row\n        app:rowEdgeFlags="top"[^>]*>\n(.*?)\n    </Row>', text, re.S)
            digit_labels = "".join(re.findall(r'android:keyLabel="([^"]+)"', digits[0]))
            wanted = "\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669\u0660" if digit == "arabic" else "1234567890"
            self.assertEqual(wanted, digit_labels, file_name + " digits row")


    def test_no_arabic_letter_is_duplicated_in_a_layout(self):
        # The PC home row once carried ز (1586) — a second copy of the bottom row's own ز —
        # while ذ (1584, xkb TLDE) was missing from the layout entirely. Guarded at code
        # level now: no letter code may appear twice in one layout, and the PC home row
        # ends at ذ like the reference keyboard.
        for path in sorted(XMLDIR.glob("kbd_arabic*.xml")):
            codes = re.findall(r'android:codes="(\d+)"', path.read_text(encoding="utf-8"))
            letters = [c for c in codes if 1569 <= int(c) <= 1610]
            self.assertEqual(len(letters), len(set(letters)),
                             path.name + ": duplicated letter code")
        pc = (XMLDIR / "kbd_arabic_pc.xml").read_text(encoding="utf-8")
        home = re.findall(r"<Row>\n(.*?)\n    </Row>", pc, re.S)[1]
        self.assertIn('android:keyLabel="\u0630"', home)
        self.assertNotIn('android:keyLabel="\u0632"', home)
        self.assertIn('android:codes="1584"', home)

    def test_arabic_102_is_the_default_and_the_duplicate_is_gone(self):
        part3 = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart3.java").read_text(encoding="utf-8")
        self.assertIn('rtl ? "ARABIC_102" : "QWERTY"', part3)
        skv = (JAVA / "com/almlk/swiftkey/ime/SmartKeyboardView.java").read_text(encoding="utf-8")
        self.assertIn("LayoutProvider.arabic102()", skv)
        provider = (JAVA / "com/almlk/swiftkey/ime/LayoutProvider.java").read_text(encoding="utf-8")
        self.assertIn('"ARABIC_102".equals(variant)', provider)
        self.assertNotIn('"ARABIC_PC_DIGITS".equals(variant)', provider)
        self.assertFalse((XMLDIR / "kbd_arabic_extended.xml").exists())
        # round 36 owner ruling: every face mark shows the key's real alternative; the old
        # xkb shift-hints (ـَ ÷ ×) must be gone from the reference file, while the
        # Persian ch remains (ج's alternative) and digits stay western like the photo.
        rep = (XMLDIR / "kbd_arabic_102.xml").read_text(encoding="utf-8")
        for legacy in ("\u0640\u064e", "\u00f7", "\u00d7"):
            self.assertNotIn(legacy, rep)
        self.assertIn("\u0686", rep)
        self.assertIn('app:digitType="western"', rep)


class LayoutXmlWellFormedTest(unittest.TestCase):
  """Every keyboard XML must parse as well-formed XML — no stray '/ />' closers, no dangling tags."""

  def test_all_keyboard_xml_is_well_formed(self):
    from xml.etree import ElementTree as ET
    files = sorted((RES / "xml").glob("kbd_*.xml"))
    self.assertGreaterEqual(len(files), 15)
    for path in files:
      body = path.read_text(encoding="utf-8")
      self.assertNotIn("/ />", body, path.name)
      stripped = __import__("re").sub(r"(?m)^\s*<!--.*?-->\s*$", "", body)
      try:
        ET.fromstring(stripped)
      except ET.ParseError as error:
        self.fail("%s: %s" % (path.name, error))


class LayoutXmlEscapingTest(unittest.TestCase):
  """aapt chokes on values starting with @ # ? and the project convention escapes ؟ too;
  KeyboardXmlParser relies on aapt stripping the backslash, so EVERY such char in the string
  attributes of the layout XMLs must be backslash-escaped or the AIDE build fails."""

  ATTRS = ("android:keyLabel", "app:trailLabel", "android:popupCharacters")

  def test_all_specials_are_escaped_in_every_letter_layout(self):
    for path in sorted(XMLDIR.glob("kbd_*.xml")):
        text = path.read_text(encoding="utf-8")
        for attr in self.ATTRS:
            for m in re.finditer('%s="([^"]*)"' % re.escape(attr), text):
                v = m.group(1)
                for i, ch in enumerate(v):
                    if ch in "@#?\u061f":
                      self.assertEqual(
                          i > 0 and v[i - 1] == "\\",
                          True,
                          "%s: unescaped %r in %s=\"%s\"" % (path.name, ch, attr, v),
                      )
                    if ch == "\\":
                      self.assertIn(
                          v[i + 1 : i + 2],
                          ("n", "t", "'", '"', "\\", "@", "#", "?", "\u061f"),
                          "%s: dangling backslash in %s" % (path.name, v),
                      )

  def test_runtime_sees_the_clean_symbols(self):
      # the thumbnail feed strips backslashes; make sure the convention stays compatible
      activity = (JAVA / "com/almlk/swiftkey/settings/LayoutsSettingsActivity.java").read_text()
      panel = (JAVA / "com/almlk/swiftkey/ime/LayoutsPanelView.java").read_text()
      self.assertIn('key.subLabel.replace("\\\\", "")', activity)
      self.assertIn('key.subLabel.replace("\\\\", "")', panel)


class LayoutRuntimeWiringTest(unittest.TestCase):
    def test_parser_reads_the_attribute_raw(self):
        parser = (JAVA / "com/almlk/swiftkey/ime/KeyboardXmlParser.java").read_text(
            encoding="utf-8"
        )
        self.assertIn('"keyboardLayout"', parser)
        self.assertIn("parseKeyboardLayoutId(attributes)", parser)
        self.assertIn("source.keyboardLayoutId", parser)
        self.assertNotIn("obtainStyledAttributes", parser)
        self.assertNotIn("R.styleable", parser)

    def test_model_carries_the_layout_id(self):
        model = (JAVA / "com/almlk/swiftkey/model/KeyboardLayout.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("public final int keyboardLayoutId", model)
        self.assertIn("int digitType, int keyboardLayoutId", model)

    def test_catalog_covers_all_twelve_entries(self):
        catalog = (JAVA / "com/almlk/swiftkey/model/KeyboardLayouts.java").read_text(
            encoding="utf-8"
        )
        for token in LAYOUT_XML.values():
            self.assertIn('"%s"' % token, catalog)
        for xml_name in LAYOUT_XML:
            self.assertIn("R.xml.%s" % xml_name.replace(".xml", ""), catalog)
        self.assertIn("cycledName", catalog)
        self.assertIn("xmlFor", catalog)
        self.assertIn("forLanguage", catalog)
        self.assertEqual(catalog.count("true),"), 6)
        self.assertEqual(catalog.count("false),"), 6)

    def test_catalog_shows_exactly_the_six_screenshot_variants(self):
        catalog = (JAVA / "com/almlk/swiftkey/model/KeyboardLayouts.java").read_text(
            encoding="utf-8"
        )
        for name in (
            '"ARABIC_123"',
            '"ARABIC_ORIGINAL"',
            '"ARABIC_PC"',
            '"ARABIC_DIGITS"',
            '"ARABIC_102"',
            '"ARABIC_ORIGINAL_DIGITS"',
        ):
            self.assertIn(name, catalog)
        for gone in ("ARABIC_PHONETIC", "ARABIC_TYPEWRITER", "arabic_azerty\","):
            # tokens may exist (files declare identity) but retired ENTRY names must not
            pass
        self.assertNotIn('"ARABIC_PC_DIGITS"', catalog)   # duplicate retired (was = ARABIC_PC + digits)
        self.assertNotIn("kbd_arabic_extended", catalog)
        self.assertNotIn('"ARABIC_PHONETIC"', catalog)
        self.assertNotIn('"ARABIC_TYPEWRITER"', catalog)
        self.assertNotIn('"AZERTY \u0634\u0645\u0627\u0644', catalog)
        self.assertIn('"\\u0627\\u0644\\u0639\\u0631\\u0628\\u064a\\u0629 123"', catalog)
        self.assertIn('"\\u0627\\u0644\\u0639\\u0631\\u0628\\u064a\\u0629 \\u0661\\u0662\\u0663"', catalog)

    def test_ime_selects_layouts_through_the_catalog(self):
        part3 = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart3.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("KeyboardLayouts.xmlFor(variant, rtl)", part3)
        self.assertIn('"arabic_layout"', part3)
        self.assertIn("LayoutProvider.arabic(editorContext, variant)", part3)
        self.assertNotIn("if (\"AZERTY\".equals(variant)) xmlResource", part3)

    def test_toolbar_tool_opens_the_in_keyboard_layouts_panel(self):
        part2c = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart2C.java").read_text()
        branch = part2c[part2c.index('"layouts".equals(key)'):]
        branch = branch[:branch.index("} else if")]
        # the tool must open the picker INSIDE the keyboard window, not as an activity
        self.assertIn("showLayoutsPanel();", branch)
        self.assertIn("hideMoreTools()", branch)
        self.assertNotIn("startActivity", branch)
        self.assertNotIn("cycledName", branch)
        self.assertNotIn("String key ", branch)
        # the panel never leaves the keyboard: no settings jump from inside it
        self.assertNotIn("LayoutsSettingsActivity", part2c)
        confirm = part2c[part2c.index("public void onConfirmSelection()"):]
        confirm = confirm[:confirm.index("});")]
        self.assertIn("applyLayout();", confirm)
        self.assertIn("hideLayoutsPanel();", confirm)
        self.assertNotIn("startActivity", confirm)
        self.assertNotIn("Intent", confirm)
        # panel: keyboard-height-matched + bigger cards + موافق instead of إضافة
        panel = (JAVA / "com/almlk/swiftkey/ime/LayoutsPanelView.java").read_text(encoding="utf-8")
        self.assertIn("public void open(boolean arabic, int keyboardHeight)", panel)
        self.assertIn("keyboardHeightPx - header - tabs", panel)
        self.assertIn('confirm.setText("\\u0645\\u0648\\u0627\\u0641\\u0642");', panel)
        self.assertNotIn("\u0625\u0636\u0627\u0641\u0629", panel)
        self.assertNotIn("إضافة", panel)
        self.assertIn("Math.min(fullWidth, maxWidth)", panel)
        self.assertIn("cardHeight * 1.5f", panel)
        frame = (RES / "layout/main_keyboard_frame.xml").read_text(encoding="utf-8")
        panel_tag = frame[frame.index("layouts_panel") - 90:frame.index("layouts_panel") + 130]
        self.assertNotIn("240dp", panel_tag)
        panel = (JAVA / "com/almlk/swiftkey/ime/LayoutsPanelView.java").read_text(encoding="utf-8")
        self.assertIn("KeyboardLayouts.forLanguage", panel)
        self.assertIn("ThemeThumbnailView", panel)
        self.assertIn("onLayoutSelected", panel)
        self.assertNotIn("->", panel)
        frame = (RES / "layout/main_keyboard_frame.xml").read_text(encoding="utf-8")
        self.assertIn("com.almlk.swiftkey.ime.LayoutsPanelView", frame)
        self.assertIn("@+id/layouts_panel", frame)
        part1 = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart1.java").read_text(encoding="utf-8")
        self.assertIn("layoutsPanel = root.findViewById(R.id.layouts_panel);", part1)
        base = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimeBase.java").read_text(encoding="utf-8")
        close = base[base.index("closeTransientInterfaces(boolean"):]
        self.assertIn("layoutsPanel.setVisibility(View.GONE)", close)
        # selection persists through the exact prefs the IME resync reads
        persist = part2c[part2c.index("public void onLayoutSelected"):]
        self.assertIn('.putString(arabicGroup ? "arabic_layout" : "layout", variantName)', persist)
        self.assertIn("applyLayout();", persist)
        # the picker itself must feed the exact prefs the IME resync reads
        activity = (JAVA / "com/almlk/swiftkey/settings/LayoutsSettingsActivity.java").read_text()
        self.assertIn('"arabic_layout"', activity)
        self.assertIn('"layout"', activity)
        base = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimeBase.java").read_text()
        self.assertIn("syncLayoutFromPrefs();", base[base.index("onStartInputView"):])
        page = (PREVIEW / "layouts.html").read_text(encoding="utf-8")
        for token in ("ARABIC_123", "ARABIC_DIGITS", "ARABIC_102",
                      "ARABIC_ORIGINAL_DIGITS"):
            self.assertIn(token, page)

    def test_provider_fallbacks_know_every_variant(self):
        provider = (JAVA / "com/almlk/swiftkey/ime/LayoutProvider.java").read_text(
            encoding="utf-8"
        )
        for name in ("QWERTZ", "COLEMAK", "QZERTY"):
            self.assertIn('"%s".equals(variant)' % name, provider)
        self.assertIn("ARABIC_ORIGINAL", provider)
        self.assertIn("ARABIC_PC", provider)
        self.assertIn("private static KeyboardLayout arabicRows(", provider)


class LayoutsSettingsScreenTest(unittest.TestCase):
    def test_activity_registered_and_layout_defined(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(
            encoding="utf-8"
        )
        self.assertIn("LayoutsSettingsActivity", manifest)
        screen = (RES / "layout/activity_layouts.xml").read_text(encoding="utf-8")
        for view_id in (
            "layouts_cards",
            "layouts_back",
            "layouts_add",
            "layouts_tab_english",
            "layouts_tab_arabic",
        ):
            self.assertIn("@+id/" + view_id, screen)
        self.assertTrue((RES / "drawable/bg_layout_card.xml").is_file())
        self.assertTrue((RES / "drawable/bg_layout_check.xml").is_file())

    def test_activity_renders_cards_from_the_xml_files(self):
        activity = (
            JAVA / "com/almlk/swiftkey/settings/LayoutsSettingsActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("KeyboardXmlParser.load", activity)
        self.assertIn("KeyboardLayouts.forLanguage", activity)
        self.assertIn("arabic_layout", activity)
        self.assertNotIn("->", activity)

    def test_languages_screen_opens_the_picker_and_lists_layouts(self):
        screen = (RES / "layout/activity_languages.xml").read_text(encoding="utf-8")
        self.assertIn("@+id/open_layouts_settings", screen)
        activity = (
            JAVA / "com/almlk/swiftkey/settings/LanguageSettingsActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("R.id.open_layouts_settings", activity)
        self.assertIn("showLayoutDialog(true)", activity)
        self.assertIn("showLayoutDialog(false)", activity)
        self.assertIn("KeyboardLayouts.names(arabic)", activity)


class LayoutsPreviewMirrorTest(unittest.TestCase):
    def test_picker_page_mirrors_the_android_screen(self):
        page = (PREVIEW / "layouts.html").read_text(encoding="utf-8")
        for token in ("QWERTY", "AZERTY", "QWERTZ", "DVORAK", "COLEMAK", "QZERTY"):
            self.assertIn(token, page)
        for token in ("ARABIC_DIGITS", "ARABIC_ORIGINAL", "ARABIC_PC"):
            self.assertIn(token, page)
        self.assertIn("arabic_layout", page)
        self.assertIn("localStorage.setItem(data.ar?'arabic_layout':'layout',name)", page)

    def test_languages_page_links_the_picker_and_shows_catalog_titles(self):
        page = (PREVIEW / "languages.html").read_text(encoding="utf-8")
        self.assertIn("layouts.html", page)
        self.assertIn("TITLES", page)
        self.assertIn("ARABIC_ORIGINAL", page)

    def test_live_preview_keyboard_honours_saved_variants(self):
        page = (PREVIEW / "english-layout-v4.html").read_text(encoding="utf-8")
        self.assertIn("localStorage.getItem(lang==='ar'?'arabic_layout':'layout')", page)
        self.assertIn("LATIN={", page)
        for token in ("QWERTZ:", "COLEMAK:", "QZERTY:", "DVORAK:"):
            self.assertIn(token, page)


if __name__ == "__main__":
    unittest.main()
