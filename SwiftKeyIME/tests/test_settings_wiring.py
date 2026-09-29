#!/usr/bin/env python3
"""Static contract tests for typing, suggestion, shortcut and dictionary settings."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app" / "src" / "main" / "java"
RES = ROOT / "app" / "src" / "main" / "res"


class SettingsWiringTest(unittest.TestCase):
    def test_activities_are_declared(self):
        manifest = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text()
        for name in (
            "TypingSettingsActivity",
            "SuggestionSettingsActivity",
            "DictionarySettingsActivity",
        ):
            self.assertIn(name, manifest)

    def test_required_preferences_are_exposed_and_used(self):
        prefs = (JAVA / "com/almlk/swiftkey/util/Prefs.java").read_text()
        runtime = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart5.java").read_text()
        space = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart4.java").read_text()
        for key in ("autocomplete", "space_autocomplete", "emoji_suggestions_enabled"):
            self.assertIn(key, prefs)
        self.assertIn("prefs.emojiSuggestions()", runtime)
        self.assertIn("prefs.spaceAutocomplete()", space)
        self.assertIn("engine.expandShortcut(current, language)", space)

    def test_shortcut_crud_and_dictionary_crud_exist(self):
        shortcuts = (JAVA / "com/almlk/swiftkey/data/ShortcutRepository.java").read_text()
        dictionary = (JAVA / "com/almlk/swiftkey/data/DictionaryDb.java").read_text()
        for signature in ("void save(", "void delete(", "String expansion(", "List<String> suggestions("):
            self.assertIn(signature, shortcuts)
        for signature in ("dictionaryWords(", "addUserWord(", "deleteWord(", "editWord("):
            self.assertIn(signature, dictionary)

    def test_suggestion_choice_finishes_with_a_boundary(self):
        runtime = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart5.java").read_text()
        composer = (JAVA / "com/almlk/swiftkey/engine/WordComposer.java").read_text()
        self.assertIn("WordComposer.replaceCurrent(ic, word, true)", runtime)
        self.assertIn("separatorPrefixLength", composer)
        self.assertIn("right + boundaryLength", composer)
        self.assertIn("connection.commitText(replacement, 1)", composer)
        self.assertIn('boundaryLength > 0 ? boundary : " "', composer)
        self.assertIn("connection.finishComposingText()", composer)
        self.assertGreaterEqual(composer.count("ensureTrailingSpace(connection)"), 2)
        self.assertNotIn("separatorAlreadyExists", composer)

    def test_suggestion_letters_follow_the_active_theme(self):
        runtime = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart4.java").read_text()
        live_theme = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart2A.java").read_text()
        editor = (JAVA / "com/almlk/swiftkey/settings/CustomThemeActivity.java").read_text()
        layout = (RES / "layout/activity_custom_theme.xml").read_text()
        self.assertIn("suggestionTextColor()", runtime)
        self.assertIn("protected void applySuggestionTheme()", live_theme)
        theme = (JAVA / "com/almlk/swiftkey/theme/KeyboardTheme.java").read_text()
        self.assertIn("currentTheme.suggestionTextColor()", runtime)
        suggestions = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart5.java").read_text()
        # Round 69: لون حروف الشارات من فن الزر المفعّل — لون واحد للكل
        self.assertIn("com.almlk.swiftkey.theme.ThemeChipArt.textColor(this, currentTheme)", live_theme)
        self.assertIn("ColorStateList.valueOf", runtime)
        self.assertIn("ColorStateList.valueOf", live_theme)
        self.assertIn("setAlpha(1f)", runtime)
        self.assertIn('new String[] {"مرحباً", "شكراً", "نعم"}', suggestions)
        self.assertIn("setClickable(false)", suggestions)
        self.assertIn("result = engine.suggest(current, history, following, requestLanguage)", suggestions)
        self.assertNotIn("prefs.autocomplete()", suggestions)
        suggestion_layout = (RES / "layout/suggestion_row.xml").read_text()
        self.assertIn("suggestionTextColor()", theme)
        self.assertIn("custom_preview_suggestions", layout)
        # Round 69: المعاينة تستخدم اللون الموحّد نفسه (chipTextColor)
        self.assertIn("ThemeChipArt.textColor(this, value)", editor)
        self.assertNotIn("HorizontalScrollView", suggestion_layout)
        self.assertIn("android:layout_weight=\"1\"", suggestion_layout)
        self.assertIn("String best = visibleWords.get(0)", suggestions)
        self.assertIn("addSuggestion(best, false", suggestions)
        self.assertIn("third.offsetByCodePoints(0, prefixCount)", runtime)
        self.assertIn('parts[0] + " " + parts[1] + " " + third.substring', runtime)
        self.assertIn("chip.setHorizontallyScrolling(false)", runtime)
        chip_view = (JAVA / "com/almlk/swiftkey/ime/SuggestionChipView.java").read_text()
        self.assertIn("setBest(boolean value, int color)", chip_view)
        self.assertEqual(chip_view.count("canvas.drawCircle"), 3)
        self.assertIn("suggestions.getChildAt(1)", runtime)
        self.assertIn("markCenterSuggestionBest(boolean hasPriority)", runtime)
        self.assertIn("current.length() > 0 && best.length() > 0", runtime)
        self.assertIn("hasPrioritySuggestion(", suggestions)
        self.assertIn("best.equals(current)", suggestions)
        self.assertIn("best.equals(shortcutExpansion)", suggestions)
        self.assertIn("isCloseCorrection(typedFold, bestFold)", suggestions)
        self.assertIn("centerHoldsMatch()", suggestions)
        self.assertNotIn("markCenterSuggestionBest()", runtime)
        self.assertNotIn("markCenterSuggestionBest()", suggestions)

    def test_word_composer_uses_java6_compatible_code_point_api(self):
        composer = (JAVA / "com/almlk/swiftkey/engine/WordComposer.java").read_text()
        self.assertIn("Character.codePointBefore(value, value.length())", composer)
        self.assertNotIn("value.codePointBefore", composer)

    def test_resize_overlay_spans_the_whole_keyboard_area(self):
        overlay = (JAVA / "com/almlk/swiftkey/ime/KeyboardResizeOverlay.java").read_text()
        runtime = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart1.java").read_text()
        self.assertIn("setTargetView", overlay)
        # Round-29 model: the frame spans the WHOLE window; the handle welds to keyboardTopY()
        # while idle (window-location read, paint-only) and to the clamped ghost while dragging
        self.assertIn("keyboardFrame.set(edge / 2f, contentTop, width - edge / 2f,", overlay)
        self.assertIn("float top = keyboardTopY();", overlay)
        self.assertIn("int[] probes = { R.id.tool_bar, R.id.tool_bar_scroll, R.id.keyboard };", overlay)
        self.assertIn("if (y < minY) {", overlay)  # ball clamped fully inside the scrim
        self.assertIn("handleBar.set(cx - barW / 2f, gripCy - barH / 2f,", overlay)
        self.assertIn("void onResizeCommitted(float rowHeightDp);", overlay)
        self.assertNotIn("onLiveHeight", overlay)
        self.assertIn("setOnTouchListener", overlay)
        self.assertNotIn("resolveKeyboardFrame", overlay)
        self.assertNotIn("RESIZE_HEADROOM_DP", overlay)
        for gone in ("topHandle", "moveGrip", "gripTop", "dragHandleOffsetPx"):
            self.assertNotIn(gone, overlay)
        resize_runtime = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimePart2B.java").read_text()
        base = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimeBase.java").read_text()
        self.assertIn("resizeOverlay.setTargetView", runtime)
        self.assertIn("R.id.keyboard", runtime)
        self.assertIn("KeyboardResizeOverlay resizeOverlay", base)
        self.assertNotIn("setMinimumHeight", resize_runtime)
        self.assertNotIn("workspaceHeight", resize_runtime)
        self.assertNotIn("resetResizeWorkspace", resize_runtime)
        self.assertNotIn("resetResizeWorkspace", base)
        self.assertNotIn("prepareResizeWorkspace", resize_runtime)
        self.assertNotIn("MAX_KEY_HEIGHT_DP", overlay)  # bounds live in the controller (28..96dp)
        self.assertNotIn("setKeyHeightDp", overlay)
        self.assertIn('android:id="@+id/keyboard_resize_overlay"',
                      (RES / "layout/ime_view.xml").read_text())

    def test_layouts_have_required_controls(self):
        xml = "\n".join(path.read_text() for path in (RES / "layout").glob("*.xml"))
        required = (
            "typing_autocomplete",
            "typing_space_autocomplete",
            "typing_autocorrect",
            "typing_learning",
            "typing_emoji_suggestions",
            "shortcut_add",
            "shortcut_list",
            "dictionary_language",
            "dictionary_search",
            "dictionary_add",
            "dictionary_list",
        )
        for view_id in required:
            self.assertRegex(xml, r"@\+id/" + re.escape(view_id) + r"\b")


class MoreSettingsRedesignTest(unittest.TestCase):
    def test_new_activities_are_declared(self):
        manifest = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text()
        for name in (
            "ClipboardSettingsActivity",
            "VoiceSettingsActivity",
            "FontSettingsActivity",
            "ToolbarSettingsActivity",
            "ShortcutSettingsActivity",
            "TranslationSettingsActivity",
            "StickersActivity",
        ):
            self.assertIn(name, manifest)

    def test_home_screen_has_twelve_rows_in_four_groups(self):
        home = (JAVA / "com/almlk/swiftkey/settings/SettingsActivity.java").read_text()
        self.assertIn("{4,4,3,1}", home.replace(" ", ""))
        for target in (
            "LanguageSettingsActivity.class",
            "SuggestionSettingsActivity.class",
            "GestureSettingsActivity.class",
            "VoiceSettingsActivity.class",
            "ThemeSettingsActivity.class",
            "FeedbackSettingsActivity.class",
            "FontSettingsActivity.class",
            "ToolbarSettingsActivity.class",
            "ShortcutSettingsActivity.class",
            "DictionarySettingsActivity.class",
            "ClipboardSettingsActivity.class",
            "ErrorLogActivity.class",
        ):
            self.assertIn(target, home)
        for icon in (
            "ic_keyboard",
            "ic_correct",
            "ic_gesture",
            "ic_mic",
            "ic_palette",
            "ic_music",
            "ic_font",
            "ic_toolbar_add",
            "ic_shortcut",
            "ic_book",
            "ic_clipboard",
            "ic_report",
        ):
            self.assertIn(icon, home)
        for drawable in (
            "ic_correct.xml",
            "ic_music.xml",
            "ic_font.xml",
            "ic_toolbar_add.xml",
            "ic_shortcut.xml",
            "ic_book.xml",
            "ic_report.xml",
            "ic_chevron_left.xml",
        ):
            self.assertTrue((RES / "drawable-anydpi-v21" / drawable).is_file())

    def test_bottom_tabs_are_shared_by_the_three_roots(self):
        tabs = (RES / "layout/settings_bottom_tabs.xml").read_text()
        for view_id in ("tab_more", "tab_stickers", "tab_shapes"):
            self.assertIn("@+id/" + view_id, tabs)
        helper = (JAVA / "com/almlk/swiftkey/settings/SettingsTabs.java").read_text()
        for target in ("SettingsActivity.class", "StickersActivity.class", "ThemeSettingsActivity.class"):
            self.assertIn(target, helper)
        for layout in ("activity_settings.xml", "activity_stickers.xml", "activity_themes.xml"):
            self.assertIn(
                "settings_bottom_tabs", (RES / "layout" / layout).read_text()
            )
        home = (JAVA / "com/almlk/swiftkey/settings/SettingsActivity.java").read_text()
        stickers = (JAVA / "com/almlk/swiftkey/settings/StickersActivity.java").read_text()
        shapes = (JAVA / "com/almlk/swiftkey/settings/ThemeSettingsActivity.java").read_text()
        self.assertIn("SettingsTabs.bind(this, SettingsTabs.TAB_MORE)", home)
        self.assertIn("SettingsTabs.bind(this, SettingsTabs.TAB_STICKERS)", stickers)
        self.assertIn("SettingsTabs.bind(this, SettingsTabs.TAB_SHAPES)", shapes)

    def test_shortcuts_moved_to_a_standalone_screen(self):
        suggestion = (
            JAVA / "com/almlk/swiftkey/settings/SuggestionSettingsActivity.java"
        ).read_text()
        self.assertNotIn("shortcut_add", suggestion)
        self.assertNotIn("renderShortcuts", suggestion)
        self.assertIn("التصحيح والترشيح", suggestion)
        standalone = (
            JAVA / "com/almlk/swiftkey/settings/ShortcutSettingsActivity.java"
        ).read_text()
        self.assertIn("ShortcutRepository", standalone)
        self.assertIn("repository.save(", standalone)
        self.assertIn("repository.delete(", standalone)
        layout = (RES / "layout/activity_shortcut_settings.xml").read_text()
        self.assertIn("@+id/shortcut_add", layout)
        self.assertIn("@+id/shortcut_list", layout)

    def test_voice_language_reaches_the_recognizer(self):
        prefs = (JAVA / "com/almlk/swiftkey/util/Prefs.java").read_text()
        self.assertIn("voiceLanguage()", prefs)
        self.assertIn("voice_language", prefs)
        voice = (JAVA / "com/almlk/swiftkey/settings/VoiceSettingsActivity.java").read_text()
        self.assertIn("setVoiceLanguage", voice)
        self.assertIn("VoicePermissionActivity", voice)
        self.assertIn('ToolPreferences.isVisible', voice)
        runtime = (JAVA / "com/almlk/swiftkey/ime/AlmlkImeRuntimeBase.java").read_text()
        self.assertIn("voiceLocale(language)", runtime)
        self.assertIn("prefs.voiceLanguage()", runtime)

    def test_font_override_reaches_key_painting(self):
        prefs = (JAVA / "com/almlk/swiftkey/util/Prefs.java").read_text()
        self.assertIn("fontOverride()", prefs)
        self.assertIn("key_font_override", prefs)
        screen = (JAVA / "com/almlk/swiftkey/settings/FontSettingsActivity.java").read_text()
        self.assertIn("key_font_override", screen)
        self.assertIn("font_preview", screen)
        keys = (JAVA / "com/almlk/swiftkey/ime/SmartKeyboardView.java").read_text()
        self.assertIn("key_font_override", keys)
        self.assertIn("sans-serif-condensed", keys)

    def test_toolbar_screen_drives_tool_preferences(self):
        screen = (
            JAVA / "com/almlk/swiftkey/settings/ToolbarSettingsActivity.java"
        ).read_text()
        self.assertIn("ToolPreferences.order(", screen)
        self.assertIn("ToolPreferences.saveOrder(", screen)
        self.assertIn("ToolPreferences.toggleVisible(", screen)
        self.assertIn("ToolPreferences.reset(", screen)
        layout = (RES / "layout/activity_toolbar_settings.xml").read_text()
        self.assertIn("@+id/toolbar_list", layout)
        self.assertIn("@+id/toolbar_reset", layout)

    def test_translation_screen_uses_the_panel_language_file(self):
        screen = (
            JAVA / "com/almlk/swiftkey/settings/TranslationSettingsActivity.java"
        ).read_text()
        self.assertIn('"translation_languages"', screen)
        self.assertIn('"from"', screen)
        self.assertIn('"to"', screen)
        layout = (RES / "layout/activity_translation_settings.xml").read_text()
        for view_id in ("translation_from", "translation_to", "translation_swap"):
            self.assertIn("@+id/" + view_id, layout)

    def test_stickers_tab_browses_searches_and_copies(self):
        screen = (JAVA / "com/almlk/swiftkey/settings/StickersActivity.java").read_text()
        self.assertIn("emoji/emoji_data.tsv", screen)
        self.assertIn("searchAsync", screen)
        self.assertIn("ClipboardRepository.get(this).add(", screen)
        self.assertIn("setPrimaryClip", screen)
        packs = (JAVA / "com/almlk/swiftkey/settings/StickerPacks.java").read_text()
        for pack in ("KAOMOJI", "ISLAMIC", "DECORATIONS"):
            self.assertIn(pack, packs)
        layout = (RES / "layout/activity_stickers.xml").read_text()
        for view_id in ("sticker_packs", "sticker_search", "sticker_grid"):
            self.assertIn("@+id/" + view_id, layout)

    def test_typing_settings_nested_inside_languages(self):
        layout = (RES / "layout/activity_languages.xml").read_text()
        self.assertIn("@+id/open_typing_settings", layout)
        screen = (
            JAVA / "com/almlk/swiftkey/settings/LanguageSettingsActivity.java"
        ).read_text()
        self.assertIn("TypingSettingsActivity.class", screen)


class NumberRowDigitTypeTest(unittest.TestCase):
    def test_digit_type_attr_is_declared_as_enum(self):
        attrs = (RES / "values/attrs.xml").read_text()
        self.assertIn('<attr name="digitType">', attrs)
        self.assertRegex(attrs, r'<enum\s+name="western"\s+value="0"')
        self.assertRegex(attrs, r'<enum\s+name="arabic"\s+value="1"')

    def test_letter_layouts_declare_their_native_digit_type(self):
        xml = RES / "xml"
        self.assertIn('app:digitType="arabic"', (xml / "kbd_arabic.xml").read_text())
        for name in ("kbd_qwerty.xml", "kbd_azerty.xml", "kbd_dvorak.xml"):
            self.assertIn('app:digitType="western"', (xml / name).read_text())

    def test_parser_reads_digit_type_without_styleable(self):
        parser = (JAVA / "com/almlk/swiftkey/ime/KeyboardXmlParser.java").read_text()
        self.assertIn('"digitType"', parser)
        self.assertIn("source.digitType", parser)
        layout = (JAVA / "com/almlk/swiftkey/model/KeyboardLayout.java").read_text()
        self.assertIn("public final int digitType", layout)
        provider = (JAVA / "com/almlk/swiftkey/ime/LayoutProvider.java").read_text()
        self.assertIn("new KeyboardLayout(r, true, 1)", provider)
        self.assertIn("new KeyboardLayout(r, false, 0)", provider)
        for path in (
            JAVA / "com/almlk/swiftkey/ime/KeyboardXmlParser.java",
            JAVA / "com/almlk/swiftkey/ime/SmartKeyboardView.java",
        ):
            content = path.read_text()
            self.assertNotIn("obtainStyledAttributes", content)
            self.assertNotIn("R.styleable", content)

    def test_digit_setting_reaches_the_number_row(self):
        prefs = (JAVA / "com/almlk/swiftkey/util/Prefs.java").read_text()
        self.assertIn("digitType()", prefs)
        self.assertIn("setDigitType", prefs)
        self.assertIn("number_digit_type", prefs)
        view = (JAVA / "com/almlk/swiftkey/ime/SmartKeyboardView.java").read_text()
        self.assertIn("number_digit_type", view)
        self.assertIn("layout.digitType", view)
        self.assertIn("ARABIC_DIGITS", view)
        self.assertIn("WESTERN_DIGITS", view)

    def test_languages_screen_controls_row_and_digits(self):
        layout = (RES / "layout/activity_languages.xml").read_text()
        for view_id in (
            "languages_number_row",
            "number_row_preview",
            "number_digits_group",
            "number_digits_arabic",
            "number_digits_western",
            "number_digits_auto",
        ):
            self.assertIn("@+id/" + view_id, layout)
        screen = (
            JAVA / "com/almlk/swiftkey/settings/LanguageSettingsActivity.java"
        ).read_text()
        self.assertIn("setDigitType(1)", screen)
        self.assertIn("setDigitType(0)", screen)
        self.assertIn("setDigitType(2)", screen)
        self.assertIn("bg_digit_key", screen)
        self.assertTrue((RES / "drawable/bg_digit_key.xml").is_file())

    def test_preview_mirrors_digit_choice(self):
        preview = (ROOT.parent / "keyboard-preview" / "languages.html").read_text()
        self.assertIn("number_digit_type", preview)


if __name__ == "__main__":
    unittest.main()
