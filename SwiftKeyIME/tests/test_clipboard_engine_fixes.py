import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res"
SETTINGS = JAVA / "com/almlk/swiftkey/settings"


def defined_classes():
    return {p.stem for p in JAVA.rglob("*.java")}


class ClipboardActivityTest(unittest.TestCase):
    def test_class_exists_and_is_wired(self):
        activity = SETTINGS / "ClipboardSettingsActivity.java"
        self.assertTrue(activity.is_file(), "ClipboardSettingsActivity.java is missing")
        text = activity.read_text(encoding="utf-8")
        self.assertIn("ClipboardRepository.get(this)", text)
        self.assertIn("R.layout.activity_clipboard_settings", text)
        for view_id in (
            "clipboard_list",
            "clipboard_add",
            "clipboard_clear",
            "clipboard_status",
        ):
            self.assertIn("@+id/" + view_id, (RES / "layout/activity_clipboard_settings.xml").read_text(encoding="utf-8"))
        self.assertIn(
            '<include layout="@layout/settings_page_header" />',
            (RES / "layout/activity_clipboard_settings.xml").read_text(encoding="utf-8"),
        )
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn("com.almlk.swiftkey.settings.ClipboardSettingsActivity", manifest)

    def test_repository_api_used_by_the_screen_exists(self):
        repository = (JAVA / "com/almlk/swiftkey/data/ClipboardRepository.java").read_text(
            encoding="utf-8"
        )
        for method in ("public synchronized void add", "public synchronized List<Item> list",
                       "public synchronized void pin", "public synchronized void edit",
                       "public synchronized void delete"):
            self.assertIn(method, repository)


class NoDanglingClassReferencesTest(unittest.TestCase):
    def test_settings_targets_all_resolve(self):
        settings = (SETTINGS / "SettingsActivity.java").read_text(encoding="utf-8")
        block = settings[settings.index("TARGETS") :]
        block = block[: block.index("};")]
        defined = defined_classes()
        for match in re.finditer(r"([A-Z][A-Za-z0-9_]*)\.class", block):
            self.assertIn(match.group(1), defined, "unresolved target: " + match.group(1))

    def test_no_activity_class_references_a_missing_file(self):
        defined = defined_classes()
        pattern = re.compile(r"([A-Z][A-Za-z0-9_]*Activity)\.class")
        offenders = []
        for file in JAVA.rglob("*.java"):
            for match in pattern.finditer(file.read_text(encoding="utf-8")):
                if match.group(1) not in defined:
                    offenders.append((file.name, match.group(1)))
        self.assertEqual([], offenders)


class SuggestionEngineCompileFixTest(unittest.TestCase):
    def test_top_ranked_uses_its_own_parameter(self):
        engine = (JAVA / "com/almlk/swiftkey/engine/SuggestionEngine.java").read_text(
            encoding="utf-8"
        )
        match = re.search(
            r"private List<String> topRanked\(Map<String, RankedWord> (\w+),.*?"
            r"new ArrayList<RankedWord>\((\w+)\.values\(\)\)",
            engine,
            re.S,
        )
        self.assertIsNotNone(match, "topRanked body not found")
        self.assertEqual(match.group(1), match.group(2))
        self.assertNotIn("ranked.values()", engine)


if __name__ == "__main__":
    unittest.main()
