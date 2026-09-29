package com.almlk.swiftkey.ime;

import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.data.ClipboardRepository;
import com.almlk.swiftkey.engine.*;
import com.almlk.swiftkey.model.*;
import java.util.*;

public abstract class AlmlkImeRuntimePart1 extends AlmlkImeRuntimeBase {

  public boolean onEvaluateFullscreenMode() {
    return false;
  }

  public boolean onEvaluateInputViewShown() {
    return true;
  }

  protected void configureDockedWindow(Window win) {

    if (win == null) return;

    WindowManager.LayoutParams params = win.getAttributes();

    params.width = WindowManager.LayoutParams.MATCH_PARENT;

    params.height = WindowManager.LayoutParams.WRAP_CONTENT;

    params.gravity = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;

    params.windowAnimations = 0;

    params.x = 0;

    params.y = 0;

    win.setAttributes(params);

    // Round 40-1: the window surface above the keyboard stays TRANSPARENT — the app shows
    // through any transient resize lag, so dragging leaves zero band of any color behind.
    win.setBackgroundDrawable(windowBackdrop);

    win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
  }

  public View onCreateInputView() {
    final View root = getLayoutInflater().inflate(R.layout.ime_view, null);
    inputRoot = root;
    root.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    root.post(
        new Runnable() {
          public void run() {
            android.app.Dialog dialog = getWindow();
            if (dialog != null && dialog.getWindow() != null)
              configureDockedWindow(dialog.getWindow());
            root.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
            root.requestLayout();
          }
        });
    keyboard = root.findViewById(R.id.keyboard);
    resizeOverlay = root.findViewById(R.id.keyboard_resize_overlay);
    // the sheet + handle weld to the panel column (toolbar+suggestions+keys): its top edge IS
    // the resize edge, exactly like the reference keyboard's arrows at the keyboard top
    resizeOverlay.setTargetView(root.findViewById(R.id.main_keyboard_frame));
    inputRootView = root;
    imePanelRootView = root.findViewById(R.id.main_keyboard_frame);
    suggestions = root.findViewById(R.id.suggestion_strip);
    languageIndicator = root.findViewById(R.id.language_indicator);
    toolBar = root.findViewById(R.id.tool_bar);
    toolBarContainer = root.findViewById(R.id.tool_bar_scroll);
    toolMore = root.findViewById(R.id.tool_more);
    standardKeyboardPanel = root.findViewById(R.id.standard_keyboard_panel);
    normalSuggestionRow = root.findViewById(R.id.normal_suggestion_row);
    emojiSearchHeader = root.findViewById(R.id.emoji_search_header);
    emojiSearchResults = root.findViewById(R.id.emoji_search_results);
    emojiSearchQuery = root.findViewById(R.id.emoji_search_query);
    emojiPanel = root.findViewById(R.id.emoji_panel);
    translationPanel = root.findViewById(R.id.translation_panel);
    moreToolsPanel = root.findViewById(R.id.more_tools_panel);
    clipboardPanel = root.findViewById(R.id.clipboard_panel);
    layoutsPanel = root.findViewById(R.id.layouts_panel);
    voiceInputPanel = root.findViewById(R.id.voice_input_panel);
    voiceInputPanel.setCallback(
        new VoiceInputPanelView.Callback() {
          public void onClose() {
            closeVoiceInput(true);
          }

          public void onLanguageToggle() {
            closeVoiceInput(false);
            language = "ar".equals(language) ? "en" : "ar";
            shifted = false;
            symbols = false;
            applyLayout();
            showVoiceInput();
          }
        });
    clipboardPanel.setCallback(
        new ClipboardPanelView.Callback() {
          public void onInsert(String value) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(value, 1);
            hideClipboard();
          }

          public void onClose() {
            hideClipboard();
          }
        });
    // Round 70: ربط لوحة متجر الملصقات
    stickerStorePanel = root.findViewById(R.id.sticker_store_panel);
    if (stickerStorePanel != null) {
      stickerStorePanel.setCallback(
          new StickerStorePanelView.Callback() {
            public void onStickerSelected(String stickerText) {
              InputConnection ic = getCurrentInputConnection();
              if (ic != null) ic.commitText(stickerText, 1);
              hideStickerStore();
            }

            public void onClose() {
              hideStickerStore();
            }
          });
    }

    if (Build.VERSION.SDK_INT >= 17) toolBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    keyboard.setListener(this);
    // Boot restore (pure-Java SharedPreferences): the persisted row height rides onto the
    // keyboard view's row model; the insets channel is welded once, here, for good.
    keyboard.setKeyHeightDp(KeyboardResizeModel.loadRowHeightDp(getApplicationContext()));
    bindInsetsToPanel();
    bindToolbar(root);
    bindPanels();
    bindMoreTools();
    bindResizeOverlay();
    applyLiveSettings();
    return root;
  }

  // Round 70: إظهار وإخفاء متجر الملصقات
  protected void showStickerStore() {
    if (stickerStorePanel == null) return;
    closeTransientInterfaces(false);
    if (clipboardPanel != null) clipboardPanel.setVisibility(View.GONE);
    translationMode = false;
    emojiSearchMode = false;
    if (translationPanel != null) translationPanel.setVisibility(View.GONE);
    if (emojiPanel != null) emojiPanel.setVisibility(View.GONE);
    if (emojiSearchHeader != null) emojiSearchHeader.setVisibility(View.GONE);
    if (standardKeyboardPanel != null) standardKeyboardPanel.setVisibility(View.GONE);
    if (normalSuggestionRow != null) normalSuggestionRow.setVisibility(View.GONE);
    if (moreToolsPanel != null) moreToolsPanel.setVisibility(View.GONE);
    setToolbarVisibility(View.GONE);
    stickerStorePanel.setVisibility(View.VISIBLE);
    stickerStorePanel.requestLayout();
  }

  protected void hideStickerStore() {
    if (stickerStorePanel == null) return;
    stickerStorePanel.setVisibility(View.GONE);
    if (standardKeyboardPanel != null) standardKeyboardPanel.setVisibility(View.VISIBLE);
    if (normalSuggestionRow != null) {
      normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
    }
    if (!inputNumeric) setToolBarVisible(toolBarVisible);
    if (keyboard != null) keyboard.requestLayout();
  }

  protected void capturePrimaryClip() {
    if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) return;
    // The clipboard's own settings page decides whether new copies are recorded.
    if (!com.almlk.swiftkey.util.FeatureSettings.enabled(
        this, com.almlk.swiftkey.util.FeatureSettings.CLIPBOARD_ENABLED)) {
      return;
    }
    android.content.ClipData clip = clipboardManager.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0) return;
    CharSequence text = clip.getItemAt(0).coerceToText(this);
    if (text != null) ClipboardRepository.get(this).add(text.toString());
    if (clipboardPanel != null && clipboardPanel.getVisibility() == View.VISIBLE)
      clipboardPanel.refresh();
  }

  protected void bindPanels() {
    translationPanel.setCallback(
        new TranslationPanelView.Callback() {
          public void onBack() {
            hideTranslation();
          }

          public void onInsert(String value) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            hideTranslation();
          }

          public void onTranslationChanged(String value) {
            translationPreview = value;
            if (translationMode) {
              InputConnection ic = getCurrentInputConnection();
              if (ic != null) ic.setComposingText(value, 1);
            }
          }

          public void onSourceChanged(String source, String sourceLanguage) {
            if (!translationMode) return;
            String next = "ar".equals(sourceLanguage) ? "ar" : "en";
            if (!next.equals(language)) {
              language = next;
              applyLayout();
            }
            updateTranslationSuggestions(source, sourceLanguage);
          }
        });
    emojiPanel.setCallback(
        new EmojiPanelView.Callback() {
          public void onEmoji(String value) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(value, 1);
          }

          public void onDelete() {
            feedback();
            deletePreviousGrapheme();
          }

          public void onKeyboard() {
            hideEmojiPanel();
          }

          public void onSearchRequested() {
            showEmojiSearch();
          }

          public void onFeature(String value) {
            showMessage(value);
          }
        });
  }

  protected View.OnClickListener listener(final int action) {
    return new View.OnClickListener() {
      public void onClick(View v) {
        if (action == 1) showEmojiPanel();
        else if (action == 2) showTranslation();
        else if (action == 3) {
          if (moreToolsPanel != null && moreToolsPanel.getVisibility() == View.VISIBLE)
            hideMoreTools();
          else showMoreTools();

        } else if (action == 4) showMessage("البحث");
        else if (action == 5) showVoiceInput();
        else if (action == 6) showMessage("المساعد الذكي");
      }
    };
  }

  protected void bindToolbar(View root) {
    root.findViewById(R.id.emoji_search_back).setOnClickListener(listener(1));
    final View toggle = root.findViewById(R.id.tool_bar_toggle);
    toggle.setContentDescription("إخفاء شريط الأدوات");
    toggle.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View v) {
            toolBarVisible = !toolBarVisible;
            setToolBarVisible(toolBarVisible);
            toggle.animate().rotation(toolBarVisible ? 0f : 180f).setDuration(180).start();
            toggle.setContentDescription(
                toolBarVisible ? "إخفاء شريط الأدوات" : "إظهار شريط الأدوات");
          }
        });
    root.findViewById(R.id.tool_more).setOnClickListener(listener(3));
    root.findViewById(R.id.tool_search).setOnClickListener(listener(4));
    root.findViewById(R.id.tool_mic).setOnClickListener(listener(5));
    root.findViewById(R.id.tool_translate).setOnClickListener(listener(2));
    // Round 70: الضغط المطول على زر الترجمة يفتح إعدادات الترجمة
    root.findViewById(R.id.tool_translate).setOnLongClickListener(
        new View.OnLongClickListener() {
          public boolean onLongClick(View v) {
            handleToolAction("translate_settings");
            return true;
          }
        });
    root.findViewById(R.id.tool_ai).setOnClickListener(listener(6));
    root.findViewById(R.id.tool_emoji).setOnClickListener(listener(1));
    root.findViewById(R.id.tool_clipboard)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View v) {
                showClipboard();
              }
            });
    // Round 70: الضغط المطول على زر الحافظة يفتح إعدادات الحافظة
    root.findViewById(R.id.tool_clipboard).setOnLongClickListener(
        new View.OnLongClickListener() {
          public boolean onLongClick(View v) {
            handleToolAction("clipboard_settings");
            return true;
          }
        });
    bindToolButton(root, R.id.tool_settings, "settings");
    bindToolButton(root, R.id.tool_theme, "theme");
    // Round 70: الضغط المطول على زر السمات يفتح إعدادات السمات
    root.findViewById(R.id.tool_theme).setOnLongClickListener(
        new View.OnLongClickListener() {
          public boolean onLongClick(View v) {
            handleToolAction("theme_settings");
            return true;
          }
        });
    bindToolButton(root, R.id.tool_resize, "resize");
    bindToolButton(root, R.id.tool_layouts, "layouts");
    // Round 70: الضغط المطول على زر التخطيطات يفتح إعدادات التخطيطات
    root.findViewById(R.id.tool_layouts).setOnLongClickListener(
        new View.OnLongClickListener() {
          public boolean onLongClick(View v) {
            handleToolAction("layouts_settings");
            return true;
          }
        });
    bindToolButton(root, R.id.tool_gif, "gif");
    bindToolButton(root, R.id.tool_languages, "languages");
    bindToolButton(root, R.id.tool_incognito, "incognito");
    bindToolButton(root, R.id.tool_modes, "modes");
  }
}
