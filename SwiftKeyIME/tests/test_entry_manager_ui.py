#!/usr/bin/env python3
"""Round-35 contract: shortcuts / dictionaries / clipboard are card managers.

Every manager screen keeps its OWN settings page (never merged into typing
settings), renders RecyclerView cards, offers live search, a floating ＋,
per-card edit/delete/reorder and follows the app theme.
"""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java" / "com" / "almlk" / "swiftkey"
RES = ROOT / "app" / "src" / "main" / "res"
MANIFEST = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text()

MANAGERS = {
    "shortcut": "ShortcutSettingsActivity",
    "dictionary": "DictionarySettingsActivity",
    "clipboard": "ClipboardSettingsActivity",
}
OPTIONS = ("ShortcutOptionsActivity", "DictionaryOptionsActivity", "ClipboardOptionsActivity")


class EntryManagerTest(unittest.TestCase):
    def test_all_three_screens_share_the_card_manager_base(self):
        base = (JAVA / "settings" / "EntryManagerActivity.java").read_text()
        self.assertIn("extends RecyclerView.Adapter<EntryHolder>", base)
        self.assertIn("ItemTouchHelper", base)
        self.assertIn("postDelayed(applyFilterTask, 150)", base)
        for name in MANAGERS.values():
            screen = (JAVA / "settings" / (name + ".java")).read_text()
            self.assertIn("extends EntryManagerActivity", screen, name)

    def test_card_list_uses_cardview_rows_with_a_per_item_handle(self):
        item = (RES / "layout" / "item_entry_card.xml").read_text()
        ET.fromstring(item)
        self.assertIn("androidx.cardview.widget.CardView", item)
        for view_id in ("entry_card_body", "entry_title", "entry_subtitle", "entry_handle", "entry_badge"):
            self.assertIn("@+id/" + view_id, item)

    def test_search_add_button_and_recycler_per_screen(self):
        for prefix in MANAGERS:
            layout = (RES / "layout" / ("activity_%s_settings.xml" % prefix)).read_text()
            ET.fromstring(layout)
            self.assertIn("@+id/%s_search" % prefix, layout)
            self.assertIn("androidx.recyclerview.widget.RecyclerView", layout)
            self.assertIn("floatingactionbutton.FloatingActionButton", layout)
            self.assertIn("@+id/%s_list" % prefix, layout)
            self.assertIn("@+id/%s_add" % prefix, layout)

    def test_reorder_only_where_the_store_persists_it(self):
        shortcuts = (JAVA / "settings" / (MANAGERS["shortcut"] + ".java")).read_text()
        clipboard = (JAVA / "settings" / (MANAGERS["clipboard"] + ".java")).read_text()
        dictionary = (JAVA / "settings" / (MANAGERS["dictionary"] + ".java")).read_text()
        self.assertIn("protected boolean reorderAllowed() {\n    return true;", shortcuts)
        self.assertIn("protected boolean reorderAllowed() {\n    return true;", clipboard)
        self.assertIn("protected boolean reorderAllowed() {\n    return false;", dictionary)

    def test_position_never_depends_on_alternatives_or_weights(self):
        base = (JAVA / "settings" / "EntryManagerActivity.java").read_text()
        self.assertIn("void moveEntry(Entry entry, int delta)", base.replace("private void moveEntry", "void moveEntry"))
        self.assertIn("onReordered()", base)
        self.assertNotIn("weight", (RES / "layout" / "item_entry_card.xml").read_text().replace("layout_weight", ""))

    def test_each_feature_has_its_own_settings_page_not_typing_settings(self):
        for name in OPTIONS:
            self.assertIn("com.almlk.swiftkey.settings." + name, MANIFEST)
            activity = (JAVA / "settings" / (name + ".java")).read_text()
            self.assertIn("extends FeatureOptionsActivity", activity)
        typing = (JAVA / "settings" / "TypingSettingsActivity.java").read_text()
        suggestion = (JAVA / "settings" / "SuggestionSettingsActivity.java").read_text()
        for key in ("shortcut_enabled", "clipboard_enabled", "dictionary_in_suggestions",
                    "dictionary_blocklist", "shortcut_in_strip", "clipboard_max_items",
                    "clipboard_purge_days"):
            self.assertNotIn(key, typing)
            self.assertNotIn(key, suggestion)

    def test_gear_opens_the_feature_page_from_every_manager(self):
        for name, expected in (
            (MANAGERS["shortcut"], "ShortcutOptionsActivity.class"),
            (MANAGERS["dictionary"], "DictionaryOptionsActivity.class"),
            (MANAGERS["clipboard"], "ClipboardOptionsActivity.class"),
        ):
            screen = (JAVA / "settings" / (name + ".java")).read_text()
            self.assertIn(expected, screen)
        header = (RES / "layout" / "settings_page_header.xml").read_text()
        ET.fromstring(header)
        self.assertIn("@+id/settings_page_gear", header)
        self.assertIn("@+id/settings_page_more", header)
        # Other screens stay untouched: both buttons default to gone.
        self.assertEqual(header.count('android:visibility="gone"'), 2)

    def test_feature_switches_reach_engine_and_repositories(self):
        engine = (JAVA / "engine" / "SuggestionEngine.java").read_text()
        self.assertIn("FeatureSettings.SHORTCUT_ENABLED", engine)
        self.assertIn("FeatureSettings.SHORTCUT_IN_STRIP", engine)
        self.assertIn("FeatureSettings.DICTIONARY_IN_SUGGESTIONS", engine)
        self.assertIn("blocklistActive()", engine)
        part1 = (JAVA / "ime" / "AlmlkImeRuntimePart1.java").read_text()
        self.assertIn("FeatureSettings.CLIPBOARD_ENABLED", part1)
        clips = (JAVA / "data" / "ClipboardRepository.java").read_text()
        self.assertIn("CLIPBOARD_MAX_ITEMS", clips)
        self.assertIn("CLIPBOARD_PURGE_DAYS", clips)
        self.assertIn("public synchronized void clearAll()", clips)
        shortcuts = (JAVA / "data" / "ShortcutRepository.java").read_text()
        self.assertIn("public synchronized void reorder(", shortcuts)
        self.assertIn("public synchronized void clear()", shortcuts)
        self.assertNotIn("Collections.sort", shortcuts)

    def test_all_touch_points_parse_and_stay_java6(self):
        touched = [
            JAVA / "settings" / "EntryManagerActivity.java",
            JAVA / "settings" / "FeatureOptionsActivity.java",
        ] + [JAVA / "settings" / (n + ".java") for n in MANAGERS.values()] + [
            JAVA / "settings" / (n + ".java") for n in OPTIONS
        ] + [JAVA / "data" / "ShortcutRepository.java", JAVA / "data" / "ClipboardRepository.java"]
        for path in touched:
            text = path.read_text()
            self.assertNotIn("->", text, path.name)
            self.assertNotIn("::", text, path.name)
            self.assertNotIn("<>()", text, path.name)
            self.assertNotIn("try (", text, path.name)
            self.assertNotIn("switch (", text, path.name)
        for layout in RES.glob("layout/activity_*_settings.xml"):
            ET.fromstring(layout.read_text())

    def test_no_kotlin_anywhere(self):
        self.assertEqual([], list((ROOT / "app").rglob("*.kt")))


if __name__ == "__main__":
    unittest.main()
