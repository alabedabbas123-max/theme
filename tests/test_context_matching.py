#!/usr/bin/env python3
"""Contracts for fast 4-gram/trigram/bigram Arabic next-word matching."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INTEL = ROOT / "app/src/main/java/com/almlk/swiftkey/engine/IntelligentSuggestionManager.java"
ENGINE = ROOT / "app/src/main/java/com/almlk/swiftkey/engine/SuggestionEngine.java"
DB = ROOT / "app/src/main/java/com/almlk/swiftkey/data/DictionaryDb.java"
ASSET = ROOT / "app/src/main/assets/suggestions/context_phrases.json"


def fold_ar(value):
    value = re.sub(r"[\u064b-\u065f\u0670\u0640]", "", value)
    value = value.translate(str.maketrans("أإآٱؤئىة", "ااااوييه"))
    if value.startswith("وال") and len(value) > 4:
        value = value[3:]
    elif value.startswith("ال") and len(value) > 3:
        value = value[2:]
    return value


def build_index(contexts, language):
    """Mirrors IntelligentSuggestionManager.loadContexts with sizes 1..4."""
    index = {}
    for item in contexts:
        if item["language"] != language:
            continue
        words = item["previous"]
        for size in range(1, min(4, len(words)) + 1):
            key = tuple(map(fold_ar, words[len(words) - size :]))
            slot = index.setdefault(key, [])
            for suggestion in item["suggestions"]:
                if suggestion not in slot:
                    slot.append(suggestion)
    return index


def longest_match(index, previous):
    """Mirrors matchedContextLength/getNextWordSuggestions backoff 4..1."""
    folded = list(map(fold_ar, previous))
    for size in range(min(4, len(folded)), 0, -1):
        key = tuple(folded[len(folded) - size :])
        if key in index:
            return size, list(index[key])
    return 0, []


class ContextMatchingTest(unittest.TestCase):
    def test_java_uses_hash_index_and_longest_context_first(self):
        source = INTEL.read_text(encoding="utf-8")
        self.assertIn("MAX_CONTEXT_WORDS = 4", source)
        self.assertIn("Math.min(MAX_CONTEXT_WORDS, previousWords.size())", source)
        self.assertIn("contextIndex.get(key)", source)
        self.assertIn("matchedContextLength", source)
        self.assertIn("size * 100000 + frequencyScore + orderScore", source)
        self.assertIn("A shorter context is only considered", source)

    def test_engine_ranks_every_source_by_context_length_first(self):
        engine = ENGINE.read_text(encoding="utf-8")
        db = DB.read_text(encoding="utf-8")
        # One length-dominant formula for all sources; the 10000 tier gap dwarfs the
        # 1200 source bonus plus 8 x 120 rank bonus, so frequency can only break ties
        # inside a single tier and can never promote a shorter context.
        self.assertIn("CONTEXT_TIER_UNIT = 10000", engine)
        self.assertIn("SOURCE_LEARNED_BONUS = 1200", engine)
        self.assertIn("SOURCE_CURATED_BONUS = 800", engine)
        self.assertIn("CONTEXT_RANK_STEP = 120", engine)
        self.assertIn("contextLength * CONTEXT_TIER_UNIT + sourceBonus + rankBonus", engine)
        self.assertIn("rankNextWord", engine)
        self.assertIn("addContextDefaultsTiered", engine)
        # Learned n-grams are queried per tier instead of one mixed list.
        self.assertIn("nextTrigram", db)
        self.assertIn("nextBigram", db)
        self.assertIn("database.nextTrigram(previous2, previous, language, 8)", engine)
        self.assertIn("database.nextBigram(previous, language, 8)", engine)
        # The old blind prepend let a short asset fallback shadow longer evidence.
        self.assertNotIn(
            "merged.addAll(intelligence.getNextWordSuggestions(previousWords, language))",
            engine,
        )

    def test_arabic_article_hamza_and_diacritic_folding(self):
        self.assertEqual(fold_ar("السَّلَام"), fold_ar("سلام"))
        self.assertEqual(fold_ar("الإكرام"), fold_ar("اكرام"))

    def test_common_greeting_continues_after_allah(self):
        data = json.loads(ASSET.read_text(encoding="utf-8"))
        wanted = tuple(map(fold_ar, ["عليكم", "ورحمة", "الله"]))
        matches = []
        for item in data["contexts"]:
            previous = tuple(map(fold_ar, item["previous"][-3:]))
            if item["language"] == "ar" and previous == wanted:
                matches.extend(item["suggestions"])
        self.assertIn("وبركاته", matches)

    def test_similar_endings_disambiguated_by_longer_context(self):
        data = json.loads(ASSET.read_text(encoding="utf-8"))
        index = build_index(data["contexts"], "ar")
        # The bare unigram is genuinely ambiguous and still orders الوكيل first, which
        # documents exactly why length must dominate: frequency/order alone would pick
        # الوكيل after "نعم المولى ونعم".
        self.assertEqual(index[(fold_ar("ونعم"),)][0], "الوكيل")
        # Full sentence: 4-word match wins.
        size, words = longest_match(index, ["الوكيل", "نعم", "المولى", "ونعم"])
        self.assertEqual(size, 4)
        self.assertEqual(words[0], "النصير")
        # Trigram context still resolves it when the 4th word is missing.
        size, words = longest_match(index, ["نعم", "المولى", "ونعم"])
        self.assertEqual(size, 3)
        self.assertEqual(words[0], "النصير")
        # The keyword alone breaks the tie even with an unknown word before it.
        size, words = longest_match(index, ["قال", "المولى", "ونعم"])
        self.assertEqual(size, 2)
        self.assertEqual(words[0], "النصير")
        # The other ending keeps working: no regression for حسبي/حسبنا.
        size, words = longest_match(index, ["حسبي", "الله", "ونعم"])
        self.assertEqual(size, 3)
        self.assertEqual(words[0], "الوكيل")
        size, words = longest_match(index, ["حسبنا", "الله", "ونعم"])
        self.assertEqual(size, 3)
        self.assertEqual(words[0], "الوكيل")


if __name__ == "__main__":
    unittest.main()
