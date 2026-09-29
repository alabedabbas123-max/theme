#!/usr/bin/env python3
"""Dependency-free regression tests for packaged suggestion assets."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"


def fold(value):
    value = value.strip().lower().replace("ـ", "")
    value = re.sub(r"[\u064B-\u065F\u0670]", "", value)
    return value.translate(str.maketrans("أإآٱىئیؤةک", "اااايييوهك"))


class SuggestionAssetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = json.loads(
            (ASSETS / "suggestions" / "context_phrases.json").read_text(encoding="utf-8")
        )
        cls.contexts = {
            (item["language"], tuple(fold(word) for word in item["previous"])): item[
                "suggestions"
            ]
            for item in root["contexts"]
        }
        emoji = json.loads(
            (ASSETS / "emoji" / "emoji_exact_ar.json").read_text(encoding="utf-8")
        )
        cls.emoji = {}
        for key, values in emoji.items():
            cls.emoji.setdefault(fold(key), [])
            cls.emoji[fold(key)].extend(
                value for value in values if value not in cls.emoji[fold(key)]
            )

    def test_bismillah_uses_longest_context(self):
        self.assertEqual(
            self.contexts[("ar", (fold("بسم"), fold("الله")))][:3],
            ["الرحمن", "الرحيم", "مجراها"],
        )
        self.assertEqual(
            self.contexts[("ar", (fold("بسم"), fold("الله"), fold("الرحمن")))][0],
            "الرحيم",
        )

    def test_standalone_allah_keeps_general_priority(self):
        self.assertEqual(
            self.contexts[("ar", (fold("الله"),))][:6],
            ["يحفظك", "يسعدك", "يرعاك", "يبارك", "أكبر", "العظيم"],
        )

    def test_arabic_normalization(self):
        self.assertEqual(fold("إبراهيم"), fold("ابراهيم"))
        self.assertEqual(fold("سيارة"), fold("سياره"))
        self.assertEqual(fold("عَلِيّـ"), fold("علي"))

    def test_emoji_is_precise_and_variant_aware(self):
        self.assertEqual(self.emoji[fold("كلب")], ["🐶", "🐕"])
        self.assertNotIn("🦴", self.emoji[fold("كلب")])
        self.assertEqual(self.emoji[fold("عظمة")], ["🦴"])
        self.assertEqual(self.emoji[fold("أسد")], ["🦁"])
        self.assertEqual(self.emoji[fold("اسد")], ["🦁"])

    def test_context_dictionary_is_substantial_and_unique(self):
        self.assertGreaterEqual(len(self.contexts), 500)
        raw = json.loads(
            (ASSETS / "suggestions" / "context_phrases.json").read_text(encoding="utf-8")
        )["contexts"]
        keys = [
            (item["language"], tuple(fold(word) for word in item["previous"]))
            for item in raw
        ]
        self.assertEqual(len(keys), len(set(keys)))


if __name__ == "__main__":
    unittest.main()
