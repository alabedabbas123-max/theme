"""Contract: layout cards reuse the theme miniature with real key icons, no stretched rows."""
import io
import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IME = os.path.join(ROOT, "app", "src", "main", "java", "com", "almlk", "swiftkey")


def read(*parts):
  path = os.path.join(IME, *parts)
  with io.open(path, encoding="utf-8") as handle:
    return handle.read()


class ThemeMiniatureTest(unittest.TestCase):

  def test_thumbnail_accepts_injected_placement(self):
    thumb = read("settings", "ThemeThumbnailView.java")
    self.assertIn("public void setPreviewData(", thumb)
    self.assertIn("previewRows == null ? ROWS : previewRows", thumb)
    self.assertIn("previewCodes == null ? CODES : previewCodes", thumb)

  def test_thumbnail_draws_real_icons_not_glyphs(self):
    thumb = read("settings", "ThemeThumbnailView.java")
    for icon in ["R.drawable.ic_shift", "R.drawable.ic_backspace", "R.drawable.ic_enter",
                 "R.drawable.ic_emoji", "R.drawable.ic_clipboard", "R.drawable.ic_tab"]:  # Round 58
      self.assertIn(icon, thumb)
    self.assertIn("setColorFilter", thumb)
    self.assertIn("PorterDuff.Mode.SRC_IN", thumb)
    # no unicode emoji/arrow glyphs anywhere in the miniature source
    for glyph in [u"\u232b", u"\u263a", u"\u21b5", u"\u21e7", u"\ud83c\udfa4"]:
      self.assertNotIn(glyph, thumb)

  def test_thumbnail_clamps_stretched_keys(self):
    thumb = read("settings", "ThemeThumbnailView.java")
    self.assertIn("rowHeight > minUnit * 1.9f", thumb)
    self.assertIn("rowHeight = minUnit * 1.9f", thumb)
    self.assertIn("/ 2f;", thumb)  # block vertically centered, never pinned to full height

  def test_space_underline_matches_real_keyboard(self):
    thumb = read("settings", "ThemeThumbnailView.java")
    self.assertIn("code == KeySpec.SPACE", thumb)
    self.assertIn("theme.accent", thumb)


class LayoutCardTest(unittest.TestCase):

  def setUp(self):
    self.card = read("settings", "LayoutsSettingsActivity.java")

  def test_card_reuses_theme_miniature(self):
    self.assertIn("ThemeThumbnailView thumb = new ThemeThumbnailView(this);", self.card)
    self.assertIn("thumb.setTheme(KeyboardTheme.load(this, prefs.theme()));", self.card)
    self.assertIn("thumb.setPreviewData(labels, trails, codes, weights);", self.card)

  def test_old_stretched_textview_rows_removed(self):
    self.assertNotIn("buildRow(", self.card)
    self.assertNotIn("buildKey(", self.card)
    self.assertNotIn("glyphFor", self.card)
    self.assertNotIn("bg_digit_key", self.card)
    self.assertNotIn("MATCH_PARENT);\n    cardParams", self.card)  # no full-height stretch

  def test_placement_comes_from_layout_xml(self):
    self.assertIn("KeyboardXmlParser.load(this, entry.xmlResource, entry.arabic)", self.card)
    self.assertIn("labels[row][col] = label;", self.card)
    self.assertIn("codes[row][col] = key.code;", self.card)
    self.assertIn("key.subLabel.replace(\"\\\\\", \"\")", self.card)

  def test_no_emoji_glyphs_in_activity(self):
    for glyph in [u"\u263a", u"\u232b", u"\u21b5", u"\ud83c\udfa4"]:
      self.assertNotIn(glyph, self.card)

  def test_selected_card_marks_caption(self):
    self.assertIn("\\u25c9", self.card)
    self.assertIn("\\u25cb", self.card)


if __name__ == "__main__":
  unittest.main()
