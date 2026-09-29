package com.almlk.swiftkey.ime;

import android.content.SharedPreferences;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.engine.*;
import com.almlk.swiftkey.model.*;
import java.util.*;

public abstract class AlmlkImeRuntimePart3 extends AlmlkImeRuntimePart2 {

  protected ImageButton toolView(String key) {
    ImageButton cached = toolbarButtons.get(key);
    if (cached != null) return cached;
    int id =
        "search".equals(key)
            ? R.id.tool_search
            : "voice".equals(key)
                ? R.id.tool_mic
                : "translate".equals(key)
                    ? R.id.tool_translate
                    : "clipboard".equals(key)
                        ? R.id.tool_clipboard
                        : "stickers".equals(key)
                            ? R.id.tool_emoji
                            : "tips".equals(key)
                                ? R.id.tool_ai
                                : "settings".equals(key)
                                    ? R.id.tool_settings
                                    : "theme".equals(key)
                                        ? R.id.tool_theme
                                        : "resize".equals(key)
                                            ? R.id.tool_resize
                                            : "layouts".equals(key)
                                                ? R.id.tool_layouts
                                                : "gif".equals(key)
                                                    ? R.id.tool_gif
                                                    : "languages".equals(key)
                                                        ? R.id.tool_languages
                                                        : "incognito".equals(key)
                                                            ? R.id.tool_incognito
                                                            : "modes".equals(key)
                                                                ? R.id.tool_modes
                                                                : 0;
    ImageButton found = id == 0 ? null : (ImageButton) inputRoot.findViewById(id);
    if (found != null) toolbarButtons.put(key, found);
    return found;
  }

  protected void applyToolbarConfiguration() {
    if (toolBar == null) return;
    for (String key : ToolPreferences.DEFAULT_ORDER) {
      ImageButton button = toolView(key);
      if (button != null) toolBar.removeView(button);
    }
    String[] primary = {"voice", "translate", "clipboard", "settings"};
    for (String key : ToolPreferences.order(this)) {
      boolean known = false;
      for (String first : primary)
        if (first.equals(key)) {
          known = true;
          break;
        }
      if (!known) addConfiguredTool(key);
    }
    for (String key : primary) addConfiguredTool(key);
  }

  protected void addConfiguredTool(String key) {
    ImageButton button = toolView(key);
    if (button == null) return;
    boolean visible = ToolPreferences.isVisible(this, key);
    button.setVisibility(visible ? View.VISIBLE : View.GONE);
    android.view.ViewParent parent = button.getParent();
    if (parent != null && parent != toolBar && parent instanceof ViewGroup)
      ((ViewGroup) parent).removeView(button);
    if (button.getParent() == null) toolBar.addView(button);
  }

  protected void showMessage(String text) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
  }

  protected void setToolbarVisibility(int visibility) {
    if (toolBarContainer != null) toolBarContainer.setVisibility(visibility);
    else if (toolBar != null) toolBar.setVisibility(visibility);
  }

  protected void setToolBarVisible(boolean visible) {
    // onStartInput/close-transient paths can run before onCreateInputView (cold IME start or
    // after a failed inflate): toolBar is still null then, so only animate when it exists.
    if (toolBar != null) {
      toolBar.animate().cancel();
      toolBar.clearAnimation();
    }
    setToolbarVisibility(visible ? View.VISIBLE : View.GONE);
  }

  protected void showTranslation() {
    closeTransientInterfaces(false);
    if (clipboardPanel != null) clipboardPanel.setVisibility(View.GONE);
    if (moreToolsPanel != null) {
      moreToolsPanel.setVisibility(View.GONE);
      toolMore.setImageResource(R.drawable.ic_more);
    }
    InputConnection ic = getCurrentInputConnection();
    CharSequence selected = ic == null ? null : ic.getSelectedText(0);
    translationMode = true;
    translationPreview = "";
    emojiSearchMode = false;
    emojiPanel.setVisibility(View.GONE);
    standardKeyboardPanel.setVisibility(View.VISIBLE);
    emojiSearchHeader.setVisibility(View.GONE);
    normalSuggestionRow.setVisibility(View.VISIBLE);
    translationPanel.setVisibility(View.VISIBLE);
    translationPanel.activate();
    setToolbarVisibility(View.GONE);
    symbols = false;
    shifted = false;
    applyLayout();
    translationPanel.setSource(selected == null ? "" : selected.toString());
  }

  protected void hideTranslation() {
    InputConnection ic = getCurrentInputConnection();
    if (ic != null) ic.finishComposingText();
    translationMode = false;
    translationPanel.releaseTransientState();
    normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
    if (!inputNumeric) setToolBarVisible(toolBarVisible);
    refreshSuggestions();
  }

  protected void showEmojiPanel() {
    if (emojiPanel == null) return;
    closeTransientInterfaces(false);
    if (clipboardPanel != null) clipboardPanel.setVisibility(View.GONE);
    if (moreToolsPanel != null) {
      moreToolsPanel.setVisibility(View.GONE);
      toolMore.setImageResource(R.drawable.ic_more);
    }
    translationMode = false;
    translationPanel.setVisibility(View.GONE);
    emojiSearchMode = false;
    emojiSearchHeader.setVisibility(View.GONE);
    normalSuggestionRow.setVisibility(View.VISIBLE);
    standardKeyboardPanel.setVisibility(View.GONE);
    emojiPanel.setVisibility(View.VISIBLE);
    emojiPanel.activate();
    setToolbarVisibility(View.GONE);
  }

  protected void showEmojiSearch() {
    closeTransientInterfaces(false);
    translationMode = false;
    translationPanel.setVisibility(View.GONE);
    emojiSearchMode = true;
    emojiQuery.setLength(0);
    emojiPanel.setVisibility(View.GONE);
    standardKeyboardPanel.setVisibility(View.VISIBLE);
    normalSuggestionRow.setVisibility(View.GONE);
    emojiSearchHeader.setVisibility(View.VISIBLE);
    setToolbarVisibility(View.GONE);
    inputNumeric = false;
    symbols = false;
    shifted = false;
    applyLayout();
    updateEmojiSearchHeader();
  }

  protected void hideEmojiPanel() {
    if (emojiPanel == null) return;
    translationMode = false;
    translationPanel.releaseTransientState();
    emojiSearchMode = false;
    emojiPanel.releaseTransientState();
    emojiSearchHeader.setVisibility(View.GONE);
    normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
    standardKeyboardPanel.setVisibility(View.VISIBLE);
    if (!inputNumeric) setToolBarVisible(toolBarVisible);
    keyboard.requestLayout();
  }

  protected void applyLayout() {
    if (keyboard == null) return;
    boolean letters = !inputNumeric && !symbols;
    keyboard.setGestureInputAllowed(
        letters && !passwordField && !translationMode && !emojiSearchMode);
    SharedPreferences keyboardUi = getSharedPreferences("keyboard_ui", 0);
    if (!keyboardUi.contains("compact_letters_v2")) {
      keyboardUi
          .edit()
          .putBoolean("number_row", false)
          .putBoolean("compact_letters_v2", true)
          .apply();
    }
    boolean savedNumberRow = keyboardUi.getBoolean("number_row", false);
    keyboard.setNumberRowVisible(letters && savedNumberRow);
    if (inputNumeric) keyboard.setKeyboardLayout(LayoutProvider.numeric(inputPhone));
    else if (symbols) keyboard.setKeyboardLayout(LayoutProvider.symbols(symbolPage2, language));
    else {
      boolean rtl = "ar".equals(language);
      String variant =
          keyboardUi
              .getString(
                  rtl ? "arabic_layout" : "layout",
                  rtl ? "ARABIC_102" : "QWERTY");
      int xmlResource = KeyboardLayouts.xmlFor(variant, rtl);
      KeyboardLayout xmlLayout =
          KeyboardXmlParser.loadLetters(this, xmlResource, rtl, shifted, editorContext, language);
      if (xmlLayout.rows.size() > 0) keyboard.setKeyboardLayout(xmlLayout);
      else
        keyboard.setKeyboardLayout(
            rtl
                ? LayoutProvider.arabic(editorContext, variant)
                : LayoutProvider.english(shifted, editorContext, variant));
    }
    changeHeightSmoothly(imePanelRootView == null ? 0 : imePanelRootView.getMeasuredHeight());
    // rows changed above — the insets channel follows the re-measured panel automatically
  }

  public void onKey(KeySpec key) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    gestureDecodeGeneration++;
    feedback();
    if (translationMode) {
      handleTranslationKey(key);
      return;
    }
    if (emojiSearchMode) {
      handleEmojiSearchKey(key);
      return;
    }
    switch (key.code) {
      case KeySpec.DELETE:
        deletePreviousGrapheme();
        break;
      case KeySpec.SHIFT:
        shifted = !shifted;
        handler.removeMessages(MSG_UPDATE_SHIFT_STATE);
        handler.sendEmptyMessage(MSG_UPDATE_SHIFT_STATE);
        return;
      case KeySpec.PAGE:
        symbolPage2 = !symbolPage2;
        applyLayout();
        return;
      case KeySpec.MODE:
        symbols = !symbols;
        symbolPage2 = false;
        applyLayout();
        return;
      case KeySpec.ENTER:
        commitFinishedWord();
        performEnter(ic);
        break;
      case KeySpec.EMOJI:
        showEmojiPanel();
        return;
      case KeySpec.MIC:
        // Round 58: زر الفاصلة يُدخل الفاصلة عند النقر — والحافظة تفتح بالضغط المطوّل
        ic.commitText(key.label == null || key.label.length() == 0 ? "\u060c" : key.label, 1);
        break;
      case KeySpec.CLIPBOARD:
        // Round 58: الضغط المطوّل على زر الفاصلة يفتح التسجيل (سجل الحافظة)
        showClipboard();
        return;
      case KeySpec.TAB:
        ic.commitText("\t", 1);
        break;
      case KeySpec.SPACE:
        if (!completeWithSpace(ic)) {
          commitFinishedWord();
          ic.commitText(" ", 1);
        }
        if (shifted) {
          shifted = false;
          applyLayout();
        }
        break;
      default:
        boolean resetShift = !symbols && "en".equals(language) && shifted;
        if (resetShift) shifted = false;
        ic.commitText(key.label, 1);
        if (resetShift) applyLayout();
    }
    refreshSuggestions();
  }
}
