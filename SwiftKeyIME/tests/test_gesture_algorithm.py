#!/usr/bin/env python3
"""Regression model for unique-key gesture composition and ordered coverage."""
import re
import unittest


def fold(value):
    value = value.strip().lower().replace("ـ", "")
    value = re.sub(r"[\u064B-\u065F\u0670]", "", value)
    return value.translate(str.maketrans("أإآٱىئیؤةک", "اااايييوهك"))


def skeleton(value):
    output = []
    for letter in fold(value):
        if letter.isalpha() and letter not in output:
            output.append(letter)
    return "".join(output)


def coverage(left, right):
    current = [0] * (len(right) + 1)
    for a in left:
        diagonal = 0
        for index, b in enumerate(right, 1):
            old = current[index]
            current[index] = diagonal + 1 if a == b else max(current[index], current[index - 1])
            diagonal = old
    return current[-1] * 1000 // max(1, len(left), len(right))


class GestureAlgorithmTest(unittest.TestCase):
    def test_crossed_key_is_recorded_once(self):
        self.assertEqual(skeleton("سسسلللااامممس"), "سلام")

    def test_dictionary_reconstructs_repeated_letters(self):
        self.assertEqual(skeleton("hello"), "helo")
        self.assertEqual(skeleton("banana"), "ban")
        self.assertEqual(coverage(skeleton("helo"), skeleton("hello")), 1000)
        self.assertEqual(coverage(skeleton("ban"), skeleton("banana")), 1000)

    def test_order_rejects_unrelated_words(self):
        self.assertEqual(coverage(skeleton("سلام"), skeleton("سلام")), 1000)
        self.assertLess(coverage(skeleton("سلام"), skeleton("كتاب")), 650)

    def test_arabic_variants_share_gesture_skeleton(self):
        self.assertEqual(skeleton("أسد"), skeleton("اسد"))
        self.assertEqual(skeleton("قطة"), skeleton("قطه"))


if __name__ == "__main__":
    unittest.main()
