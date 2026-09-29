package com.almlk.swiftkey.ime;

import android.content.Intent;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.engine.*;
import com.almlk.swiftkey.model.*;
import java.util.*;

public abstract class AlmlkImeRuntimePart2C extends AlmlkImeRuntimePart2B {
  protected void showMoreTools() {
    closeTransientInterfaces(false);
    clipboardPanel.setVisibility(View.GONE);
    translationMode = false;
    emojiSearchMode = false;
    translationPanel.setVisibility(View.GONE);
    emojiPanel.setVisibility(View.GONE);
    emojiSearchHeader.setVisibility(View.GONE);
    standardKeyboardPanel.setVisibility(View.GONE);
    moreToolsPanel.setVisibility(View.VISIBLE);
    moreToolsPanel.refresh();
    setToolbarVisibility(View.VISIBLE);
    toolMore.setImageResource(R.drawable.ic_close);
  }

  protected void hideMoreTools() {
    if (moreToolsPanel == null) return;
    moreToolsPanel.releaseTransientState();
    standardKeyboardPanel.setVisibility(View.VISIBLE);
    normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
    toolMore.setImageResource(R.drawable.ic_more);
    if (!inputNumeric) setToolBarVisible(toolBarVisible);
    keyboard.requestLayout();
  }

  protected void handleToolAction(String key) {
    if ("settings".equals(key)) {
      Intent intent = new Intent(this, com.almlk.swiftkey.settings.SettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);

    } else if ("gestures".equals(key)) {
      Intent intent = new Intent(this, com.almlk.swiftkey.settings.GestureSettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);

    } else if ("theme".equals(key)) {
      // Round 45: زر «السمات» في الأدوات مرتبط مباشرة بقسم السمات في الإعدادات.
      openThemeSettings();

    } else if ("resize".equals(key)) {
      showResizeOverlay();

    } else if ("layouts".equals(key)) {
      hideMoreTools();
      showLayoutsPanel();

    } else if ("gif".equals(key)) {
      showEmojiPanel();
      showMessage("واجهة GIF");

    } else if ("stickers".equals(key)) {
      // Round 70: فتح لوحة متجر الملصقات داخل لوحة المفاتيح
      showStickerStore();

    } else if ("languages".equals(key)) {
      onSpaceSwipe(1);

    } else if ("incognito".equals(key)) {
      boolean enabled = prefs.learning();
      prefs.set("learning", !enabled);
      showMessage(enabled ? "تم تشغيل التصفح الخفي" : "تم إيقاف التصفح الخفي");

    } else if ("modes".equals(key)) {
      symbols = !symbols;
      symbolPage2 = false;
      hideMoreTools();
      applyLayout();

    } else if ("search".equals(key)) showMessage("البحث");
    else if ("voice".equals(key)) showVoiceInput();
    else if ("translate".equals(key)) showTranslation();
    else if ("clipboard".equals(key)) inputRoot.findViewById(R.id.tool_clipboard).performClick();
    else if ("tips".equals(key)) showMessage("التلميحات الذكية");
    else if ("rewards".equals(key)) showMessage("Rewards");
    else if ("login".equals(key)) showMessage("تسجيل الدخول");
    // Round 70: ربط عناصر شريط الأدوات بشاشات الإعدادات
    else if ("translate_settings".equals(key)) {
      Intent intent = new Intent(this, com.almlk.swiftkey.settings.TranslationSettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    } else if ("clipboard_settings".equals(key)) {
      Intent intent = new Intent(this, com.almlk.swiftkey.settings.ClipboardSettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    } else if ("layouts_settings".equals(key)) {
      Intent intent = new Intent(this, com.almlk.swiftkey.settings.LayoutsSettingsActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
    } else if ("theme_settings".equals(key)) {
      openThemeSettings();
    }
  }

  private void openThemeSettings() {
    if (moreToolsPanel != null && moreToolsPanel.getVisibility() == View.VISIBLE) {
      hideMoreTools();
    }
    Intent intent = new Intent(this, com.almlk.swiftkey.settings.ThemeSettingsActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra("open_section", "themes");
    startActivity(intent);
  }

  /** Opens the layout picker inside the keyboard window: no activity, no external screens. */
  protected void showLayoutsPanel() {
    if (layoutsPanel == null) {
      return;
    }
    int keyboardHeight = standardKeyboardPanel.getHeight();
    clipboardPanel.setVisibility(View.GONE);
    translationPanel.setVisibility(View.GONE);
    emojiPanel.setVisibility(View.GONE);
    emojiSearchHeader.setVisibility(View.GONE);
    moreToolsPanel.setVisibility(View.GONE);
    standardKeyboardPanel.setVisibility(View.GONE);
    normalSuggestionRow.setVisibility(View.GONE);
    setToolbarVisibility(View.GONE);
    layoutsPanel.setListener(
        new LayoutsPanelView.Listener() {
          public String selectedVariant(boolean arabicGroup) {
            return getSharedPreferences("keyboard_ui", 0)
                .getString(
                    arabicGroup ? "arabic_layout" : "layout",
                    arabicGroup ? "ARABIC_DIGITS" : "QWERTY");
          }

          public void onLayoutSelected(String variantName, boolean arabicGroup) {
            getSharedPreferences("keyboard_ui", 0)
                .edit()
                .putString(arabicGroup ? "arabic_layout" : "layout", variantName)
                .apply();
            if (arabicGroup != "ar".equals(language)) {
              language = arabicGroup ? "ar" : "en";
              shifted = false;
            }
            symbols = false;
            applyLayout();
            showMessage("\u062a\u0645 \u062a\u0637\u0628\u064a\u0642 \u0627\u0644\u062a\u062e\u0637\u064a\u0637: " + KeyboardLayouts.title(variantName));
          }

          public void onClose() {
            hideLayoutsPanel();
          }

          public void onConfirmSelection() {
            // موافق confirms the highlighted layout and closes: never jumps to settings.
            applyLayout();
            hideLayoutsPanel();
            showMessage("\u062a\u0645 \u062a\u0641\u0639\u064a\u0644 \u0627\u0644\u062a\u062e\u0637\u064a\u0637");
          }
        });
    layoutsPanel.open("ar".equals(language), keyboardHeight);
    layoutsPanel.setVisibility(View.VISIBLE);
    layoutsPanel.requestLayout();
  }

  protected void hideLayoutsPanel() {
    if (layoutsPanel == null) {
      return;
    }
    layoutsPanel.setVisibility(View.GONE);
    standardKeyboardPanel.setVisibility(View.VISIBLE);
    normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
    if (!inputNumeric) {
      setToolBarVisible(toolBarVisible);
    }
    keyboard.requestLayout();
  }
}
