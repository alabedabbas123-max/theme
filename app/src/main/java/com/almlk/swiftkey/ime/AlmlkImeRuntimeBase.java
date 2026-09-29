package com.almlk.swiftkey.ime;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.engine.*;
import com.almlk.swiftkey.model.*;
import com.almlk.swiftkey.theme.KeyboardTheme;
import com.almlk.swiftkey.util.Prefs;
import java.util.*;
import java.util.concurrent.*;

public abstract class AlmlkImeRuntimeBase extends InputMethodService
    implements KeyboardActionListener {

  protected SmartKeyboardView keyboard;

  protected KeyboardResizeOverlay resizeOverlay;
  protected View inputRootView;

  /**
   * Round 40-1: the IME window background is FULLY TRANSPARENT. While the live drag resizes the
   * panel, the window's surface resize can lag the view pass by a frame; anything the surface
   * exposes above the keyboard's top edge must show the APP BEHIND — never a painted band of
   * ANY color (themed or black). Image themes use near-black fallback backgrounds, so painting
   * the surface produced the black gap the user saw when dragging down. Transparent = zero
   * residue by construction; the root view itself paints the whole keyboard area.
   */
  protected final android.graphics.drawable.ColorDrawable windowBackdrop =
      new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
  protected View imePanelRootView;
  protected int resizePanelHeightPx;
  private boolean insetsSyncRunning;

  protected LinearLayout suggestions, emojiSearchResults, toolBar;
  protected TextView languageIndicator, emojiSearchQuery;
  protected View inputRoot,
      toolBarContainer,
      standardKeyboardPanel,
      normalSuggestionRow,
      emojiSearchHeader;

  protected ImageButton toolMore;

  protected EmojiPanelView emojiPanel;

  protected TranslationPanelView translationPanel;

  protected MoreToolsPanelView moreToolsPanel;

  protected ClipboardPanelView clipboardPanel;
  protected LayoutsPanelView layoutsPanel;
  /** Round 70: لوحة متجر الملصقات */
  protected StickerStorePanelView stickerStorePanel;

  protected VoiceInputPanelView voiceInputPanel;
  protected android.speech.SpeechRecognizer speechRecognizer;
  protected Intent speechIntent;
  protected boolean voiceSessionActive;

  protected android.content.ClipboardManager clipboardManager;

  protected final android.content.ClipboardManager.OnPrimaryClipChangedListener clipboardListener =
      new android.content.ClipboardManager.OnPrimaryClipChangedListener() {
        public void onPrimaryClipChanged() {
          capturePrimaryClip();
        }
      };

  protected boolean translationMode,
      emojiSearchMode,
      toolBarVisible = true,
      inputNumeric,
      inputPhone,
      multilineField,
      shifted,
      symbols,
      symbolPage2,
      passwordField;

  protected int emojiSearchGeneration;
  protected int gestureDecodeGeneration;
  protected volatile int suggestionGeneration;
  protected final ThreadPoolExecutor gestureDecoder =
      new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
  protected final ThreadPoolExecutor suggestionWorker =
      new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
  protected final ExecutorService learningWorker = Executors.newSingleThreadExecutor();

  protected String editorContext = "text", language = "ar", translationPreview = "";
  protected String latestSuggestionInput = "", latestSuggestion = "";

  protected final StringBuilder emojiQuery = new StringBuilder();

  protected SuggestionEngine engine;

  protected Prefs prefs;

  protected final Map<String, ImageButton> toolbarButtons = new HashMap<String, ImageButton>();

  protected KeyboardTheme currentTheme = KeyboardTheme.from("light");
  protected int appliedThemeSignature = Integer.MIN_VALUE;

  protected static final int MSG_UPDATE_SUGGESTIONS = 0;
  protected static final int MSG_START_TUTORIAL = 1;
  protected static final int MSG_UPDATE_SHIFT_STATE = 2;
  protected static final int MSG_VOICE_RESULTS = 3;
  protected static final int MSG_UPDATE_OLD_SUGGESTIONS = 4;
  protected static final int MSG_APPLY_LIVE_SETTINGS = 5;

  /**
   * Round 47: بث «أظهر الكيبورد» — شاشة اختيار الثيم ترسله بعد تطبيق ثيم، فإذا
   * كانت الخدمة حية طلب الظهور (requestShowSelf على API 28+) وأعادت تطبيق
   * الإعدادات فوراً — فيرى المستخدم الكيبورد بالثيم الجديد مباشرة.
   */
  public static final String ACTION_SHOW_KEYBOARD =
      "com.almlk.swiftkey.action.SHOW_KEYBOARD";
  protected static final int MSG_SEARCH_EMOJI = 6;
  protected static final int MSG_HIDE_LANGUAGE_INDICATOR = 7;
  protected static final int MSG_RESTART_VOICE = 8;

  protected boolean dispatcherActive = true;

  /** One main-thread organizer. Duplicate work is coalesced before it reaches the UI. */
  protected final Handler handler =
      new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
          if (!dispatcherActive) return;
          switch (msg.what) {
            case MSG_UPDATE_SUGGESTIONS:
            case MSG_UPDATE_OLD_SUGGESTIONS:
              if (inputRoot != null && keyboard != null) refreshSuggestionsNow();
              break;
            case MSG_START_TUTORIAL:
              break;
            case MSG_UPDATE_SHIFT_STATE:
              if (keyboard != null) applyLayout();
              break;
            case MSG_VOICE_RESULTS:
              if (msg.obj instanceof CharSequence) {
                InputConnection connection = getCurrentInputConnection();
                if (connection != null) connection.commitText((CharSequence) msg.obj, 1);
              }
              break;
            case MSG_APPLY_LIVE_SETTINGS:
              if (inputRoot != null) applyLiveSettings();
              break;
            case MSG_SEARCH_EMOJI:
              if (emojiPanel != null && emojiSearchMode) emojiSearchRequest.run();
              break;
            case MSG_HIDE_LANGUAGE_INDICATOR:
              if (languageIndicator != null) languageIndicator.setVisibility(View.INVISIBLE);
              break;
            case MSG_RESTART_VOICE:
              if (voiceSessionActive) startVoiceRecognizerNow();
              break;
          }
        }
      };

  protected final android.content.SharedPreferences.OnSharedPreferenceChangeListener
      liveSettingsListener =
          new android.content.SharedPreferences.OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(
                android.content.SharedPreferences shared, String key) {
              if (!dispatcherActive) return;
              handler.removeMessages(MSG_APPLY_LIVE_SETTINGS);
              handler.sendEmptyMessageDelayed(MSG_APPLY_LIVE_SETTINGS, 8);
            }
          };

  protected String pendingEmojiQuery = "";

  protected int pendingEmojiGeneration;

  protected final Runnable emojiSearchRequest =
      new Runnable() {
        public void run() {
          final String requested = pendingEmojiQuery;
          final int generation = pendingEmojiGeneration;
          emojiPanel.searchEmojiAsync(
              requested,
              40,
              new EmojiPanelView.SearchResultCallback() {
                public void onResult(String query, List<String> values) {
                  if (generation != emojiSearchGeneration
                      || !requested.equals(emojiQuery.toString())) return;
                  emojiSearchResults.removeAllViews();
                  for (final String value : values) {
                    TextView cell = new TextView(AlmlkImeRuntimeBase.this);
                    cell.setText(value);
                    cell.setTextSize(24);
                    cell.setGravity(Gravity.CENTER);
                    cell.setTag(value);
                    cell.setOnClickListener(
                        new View.OnClickListener() {
                          public void onClick(View v) {
                            commitEmojiResult((String) v.getTag());
                          }
                        });
                    emojiSearchResults.addView(
                        cell,
                        new LinearLayout.LayoutParams(
                            dp(48), LinearLayout.LayoutParams.MATCH_PARENT));
                  }
                }
              });
        }
      };

  protected final android.content.BroadcastReceiver showKeyboardReceiver =
      new android.content.BroadcastReceiver() {
        public void onReceive(android.content.Context context, Intent intent) {
          if (intent == null || !ACTION_SHOW_KEYBOARD.equals(intent.getAction())) return;
          handler.removeMessages(MSG_APPLY_LIVE_SETTINGS);
          handler.sendEmptyMessageDelayed(MSG_APPLY_LIVE_SETTINGS, 8);
          if (Build.VERSION.SDK_INT >= 28) requestShowSelf(0);
        }
      };

  protected final android.content.BroadcastReceiver voicePermissionReceiver =
      new android.content.BroadcastReceiver() {
        public void onReceive(android.content.Context context, Intent intent) {
          if (intent != null
              && com.almlk.swiftkey.settings.VoicePermissionActivity.ACTION_RESULT.equals(
                  intent.getAction())) {
            if (intent.getBooleanExtra("granted", false))
              handler.postDelayed(
                  new Runnable() {
                    public void run() {
                      if (isInputViewShown()) {
                        dispatcherActive = true;
                        showVoiceInput();
                      }
                    }
                  },
                  180);
            else showMessage("يلزم السماح باستخدام الميكروفون للإدخال الصوتي");
          }
        }
      };

  protected final android.speech.RecognitionListener voiceListener =
      new android.speech.RecognitionListener() {
        public void onReadyForSpeech(Bundle values) {
          if (voiceSessionActive && voiceInputPanel != null) voiceInputPanel.setListening();
        }

        public void onBeginningOfSpeech() {}

        public void onRmsChanged(float rms) {
          if (voiceSessionActive && voiceInputPanel != null) voiceInputPanel.setLevel(rms);
        }

        public void onBufferReceived(byte[] data) {}

        public void onEndOfSpeech() {
          if (voiceSessionActive && voiceInputPanel != null) voiceInputPanel.setProcessing();
        }

        public void onPartialResults(Bundle values) {
          updateVoicePreview(values);
        }

        public void onEvent(int type, Bundle values) {}

        public void onResults(Bundle values) {
          if (!voiceSessionActive) return;
          ArrayList<String> results =
              values == null
                  ? null
                  : values.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
          if (results != null && !results.isEmpty()) {
            String value = results.get(0).trim();
            if (value.length() > 0) {
              if (voiceInputPanel != null) voiceInputPanel.setPartial(value);
              handler.removeMessages(MSG_VOICE_RESULTS);
              Message message = handler.obtainMessage(MSG_VOICE_RESULTS, value + " ");
              handler.sendMessageDelayed(message, 120);
            }
          }
          scheduleVoiceRestart(260);
        }

        public void onError(int error) {
          if (!voiceSessionActive) return;
          if (error == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            closeVoiceInput(true);
            showMessage("تعذر الوصول إلى الميكروفون");
          } else
            scheduleVoiceRestart(
                error == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 520 : 280);
        }
      };

  protected void updateVoicePreview(Bundle values) {
    if (!voiceSessionActive || values == null) return;
    ArrayList<String> results =
        values.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
    if (results != null && !results.isEmpty() && voiceInputPanel != null)
      voiceInputPanel.setPartial(results.get(0));
  }

  protected void showVoiceInput() {
    if (android.os.Build.VERSION.SDK_INT >= 23
        && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      Intent permission =
          new Intent(this, com.almlk.swiftkey.settings.VoicePermissionActivity.class);
      permission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
      startActivity(permission);
      return;
    }
    closeTransientInterfaces(false);
    if (voiceInputPanel == null) return;
    voiceSessionActive = true;
    voiceInputPanel.begin(language);
    setToolbarVisibility(View.GONE);
    standardKeyboardPanel.setVisibility(View.VISIBLE);
    normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
    startVoiceRecognizerNow();
  }

  private String voiceLocale(String activeLanguage) {
    String choice = prefs == null ? "auto" : prefs.voiceLanguage();
    if ("ar".equals(choice)) return "ar-YE";
    if ("en".equals(choice)) return "en-US";
    return "ar".equals(activeLanguage) ? "ar-YE" : "en-US";
  }

  protected void startVoiceRecognizerNow() {
    if (!voiceSessionActive || !dispatcherActive) return;
    handler.removeMessages(MSG_RESTART_VOICE);
    try {
      if (speechRecognizer == null) {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
          closeVoiceInput(true);
          showMessage("خدمة التعرف الصوتي غير متاحة");
          return;
        }
        speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(voiceListener);
      }
      speechIntent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      speechIntent.putExtra(
          android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
          android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      speechIntent.putExtra(
          android.speech.RecognizerIntent.EXTRA_LANGUAGE, voiceLocale(language));
      speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
      speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 3);
      speechIntent.putExtra(
          android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 450L);
      speechIntent.putExtra(
          android.speech.RecognizerIntent
              .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
          350L);
      speechIntent.putExtra(
          android.speech.RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
      speechRecognizer.startListening(speechIntent);
      if (voiceInputPanel != null) voiceInputPanel.setListening();
    } catch (Exception failure) {
      closeVoiceInput(true);
      showMessage("تعذر بدء الإدخال الصوتي");
    }
  }

  protected void scheduleVoiceRestart(long delay) {
    if (!voiceSessionActive) return;
    handler.removeMessages(MSG_RESTART_VOICE);
    handler.sendEmptyMessageDelayed(MSG_RESTART_VOICE, delay);
  }

  protected void closeVoiceInput(boolean restoreToolbar) {
    voiceSessionActive = false;
    handler.removeMessages(MSG_RESTART_VOICE);
    handler.removeMessages(MSG_VOICE_RESULTS);
    if (speechRecognizer != null) {
      try {
        speechRecognizer.cancel();
      } catch (Exception ignored) {
      }
      try {
        speechRecognizer.destroy();
      } catch (Exception ignored) {
      }
      speechRecognizer = null;
    }
    speechIntent = null;
    if (voiceInputPanel != null) voiceInputPanel.releaseTransientState();
    if (restoreToolbar && !inputNumeric) setToolBarVisible(toolBarVisible);
  }

  /** Stops timers, callbacks, popups and temporary content owned by every optional surface. */
  protected void closeTransientInterfaces(boolean restoreKeyboard) {
    closeVoiceInput(false);
    handler.removeMessages(MSG_SEARCH_EMOJI);
    emojiSearchGeneration++;
    if (translationPanel != null) translationPanel.releaseTransientState();
    if (emojiPanel != null) emojiPanel.releaseTransientState();
    if (clipboardPanel != null) clipboardPanel.releaseTransientState();
    if (layoutsPanel != null) layoutsPanel.setVisibility(View.GONE);
    if (moreToolsPanel != null) moreToolsPanel.releaseTransientState();
    if (resizeOverlay != null) resizeOverlay.releaseTransientState();
    // Round 70: إغلاق لوحة متجر الملصقات
    if (stickerStorePanel != null) stickerStorePanel.setVisibility(View.GONE);
    translationMode = false;
    emojiSearchMode = false;
    emojiQuery.setLength(0);
    translationPreview = "";
    if (emojiSearchHeader != null) emojiSearchHeader.setVisibility(View.GONE);
    if (toolMore != null) toolMore.setImageResource(R.drawable.ic_more);
    if (restoreKeyboard && standardKeyboardPanel != null) {
      standardKeyboardPanel.setVisibility(View.VISIBLE);
      normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
      if (!inputNumeric) setToolBarVisible(toolBarVisible);
    }
  }

  /**
   * THE official resize channel (the round-30 replace): this IME never resizes itself through
   * View LayoutParams anymore. The visible panel column reports its measured height to
   * changeHeightSmoothly(); everything above it inside the window is drag-sheet headroom that
   * the editor never sees. contentTopInsets/visibleTopInsets push the editor exactly to the
   * keyboard's LIVE top edge (that is the smooth elastic slide), and touchableInsets = VISIBLE
   * keeps the sheet modal while it is open — and when it closes, the window wraps the keyboard
   * again so taps above it go straight to the app behind: zero dead zones, guaranteed.
   */
  // API-3 shape onComputeInsets(Insets) is the base every framework level funnels through:
  // the API-22 (Insets,boolean) overload delegates to this one, so ONE override covers all.
  @Override
  public void onComputeInsets(InputMethodService.Insets out) {
    int rootH = inputRootView == null ? 0 : inputRootView.getHeight();
    int panelH = imePanelRootView == null ? 0 : imePanelRootView.getHeight();
    int top = rootH > panelH && panelH > 0 ? rootH - panelH : 0;
    out.contentTopInsets = top;
    out.visibleTopInsets = top;
    out.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE;
  }

  /**
   * The ONE update entry point the drag (and every keyboard re-layout) calls: it caches the new
   * panel height, re-measures the sheet/window content and forces the system to re-query
   * onComputeInsets, so the editor slides to the new edge through the framework's own
   * animation. Height stays dp-driven (row formula) so the panel can never render a band.
   */
  protected void changeHeightSmoothly(int newHeightPx) {
    if (newHeightPx > 0) {
      resizePanelHeightPx = newHeightPx;
    }
    if (insetsSyncRunning || inputRootView == null) {
      return;
    }
    insetsSyncRunning = true;
    try {
      inputRootView.requestLayout();
      updateInputViewShown();
    } finally {
      insetsSyncRunning = false;
    }
  }

  /** Wire the panel column's layout callbacks into the insets channel (idempotent). */
  protected void bindInsetsToPanel() {
    if (imePanelRootView == null) {
      return;
    }
    imePanelRootView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
      public void onLayoutChange(View view, int left, int top, int right, int bottom,
          int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (bottom - top != oldBottom - oldTop) {
          changeHeightSmoothly(bottom - top);
        }
      }
    });
    changeHeightSmoothly(imePanelRootView.getMeasuredHeight());
  }


  public abstract boolean onEvaluateFullscreenMode();

  public abstract boolean onEvaluateInputViewShown();

  protected abstract void configureDockedWindow(Window win);

  public abstract View onCreateInputView();

  protected abstract void capturePrimaryClip();

  protected abstract void bindPanels();

  protected abstract View.OnClickListener listener(final int action);

  protected abstract void bindToolbar(View root);

  protected abstract void bindToolButton(View root, int id, final String key);

  protected abstract void bindMoreTools();

  protected abstract void bindResizeOverlay();

  protected abstract void applyLiveSettings();

  protected abstract void showResizeOverlay();

  protected abstract void hideResizeOverlay();

  protected abstract void showClipboard();

  protected abstract void hideClipboard();

  protected abstract void showMoreTools();

  protected abstract void hideMoreTools();

  protected abstract void handleToolAction(String key);

  protected abstract ImageButton toolView(String key);

  protected abstract void applyToolbarConfiguration();

  protected abstract void addConfiguredTool(String key);

  protected abstract void showMessage(String text);

  protected abstract void setToolbarVisibility(int visibility);

  protected abstract void setToolBarVisible(boolean visible);

  protected abstract void showTranslation();

  protected abstract void hideTranslation();

  protected abstract void showEmojiPanel();

  protected abstract void showEmojiSearch();

  protected abstract void hideEmojiPanel();

  protected abstract void applyLayout();

  public abstract void onKey(KeySpec key);

  public abstract void onAlternatives(KeySpec key, String selected);

  public abstract void onSpaceSwipe(int direction);

  public abstract void onSpaceVerticalSwipe(boolean showNumberRow);

  public abstract void onGesturePreview(GestureTrace gesture);

  public abstract void onGesture(GestureTrace gesture);

  public abstract void onGestureCancelled();

  protected abstract void handleTranslationKey(KeySpec key);

  protected abstract void handleEmojiSearchKey(KeySpec key);

  protected abstract void updateEmojiSearchHeader();

  protected abstract void commitEmojiResult(String value);

  protected abstract void deletePreviousGrapheme();

  protected abstract void performEnter(InputConnection ic);

  protected abstract void commitFinishedWord();

  protected abstract void addSuggestion(
      final String word, final boolean translation, final boolean correction);

  protected abstract void addEmojiSuggestion(final String emoji);

  protected abstract void updateTranslationSuggestions(String source, String sourceLanguage);

  protected abstract void refreshSuggestions();

  protected abstract void refreshSuggestionsNow();

  protected abstract boolean completeWithSpace(InputConnection connection);

  protected abstract void showSuggestionEditor(View anchor, final String original);

  protected abstract void acceptSuggestion(String word);

  protected abstract void feedback();

  protected abstract android.graphics.drawable.Drawable suggestionBackground();

  protected abstract int dp(int value);

  public void onCreate() {
    setTheme(R.style.ImeTheme);
    super.onCreate();
    engine = new SuggestionEngine(this);
    prefs = new Prefs(this);
    clipboardManager = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
    if (clipboardManager != null) clipboardManager.addPrimaryClipChangedListener(clipboardListener);
    getSharedPreferences("keyboard", 0)
        .registerOnSharedPreferenceChangeListener(liveSettingsListener);
    getSharedPreferences("toolbar_configuration", 0)
        .registerOnSharedPreferenceChangeListener(liveSettingsListener);
    getSharedPreferences("keyboard_ui", 0)
        .registerOnSharedPreferenceChangeListener(liveSettingsListener);
    // Round 59: ملف الثيمات نفسه — اختيار صورة أو زر من الاستوديو يحدّث الكيبورد
    // الحي كاملاً (المفاتيح والاقتراحات وشريط الأدوات والواجهات) في اللحظة نفسها.
    getSharedPreferences("custom_themes", 0)
        .registerOnSharedPreferenceChangeListener(liveSettingsListener);
    registerReceiver(
        voicePermissionReceiver,
        new android.content.IntentFilter(
            com.almlk.swiftkey.settings.VoicePermissionActivity.ACTION_RESULT));
    registerReceiver(
        showKeyboardReceiver, new android.content.IntentFilter(ACTION_SHOW_KEYBOARD));
  }

  public void onDestroy() {
    if (clipboardManager != null)
      clipboardManager.removePrimaryClipChangedListener(clipboardListener);
    getSharedPreferences("keyboard", 0)
        .unregisterOnSharedPreferenceChangeListener(liveSettingsListener);
    getSharedPreferences("toolbar_configuration", 0)
        .unregisterOnSharedPreferenceChangeListener(liveSettingsListener);
    getSharedPreferences("keyboard_ui", 0)
        .unregisterOnSharedPreferenceChangeListener(liveSettingsListener);
    getSharedPreferences("custom_themes", 0) // Round 59
        .unregisterOnSharedPreferenceChangeListener(liveSettingsListener);
    try {
      unregisterReceiver(voicePermissionReceiver);
    } catch (Exception ignored) {
    }
    try {
      unregisterReceiver(showKeyboardReceiver);
    } catch (Exception ignored) {
    }
    gestureDecoder.shutdownNow();
    suggestionWorker.shutdownNow();
    learningWorker.shutdown();
    releaseDispatcher();
    super.onDestroy();
  }

  public void onConfigureWindow(Window win, boolean fullscreen, boolean candidatesOnly) {

    super.onConfigureWindow(win, fullscreen, candidatesOnly);

    configureDockedWindow(win);
  }

  public void onWindowShown() {

    super.onWindowShown();
    dispatcherActive = true;

    android.app.Dialog dialog = getWindow();

    if (dialog != null && dialog.getWindow() != null) configureDockedWindow(dialog.getWindow());

    if (inputRoot != null) {

      applyLiveSettings();

      inputRoot.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;

      inputRoot.requestLayout();
    }
  }

  public void onStartInput(EditorInfo info, boolean restarting) {
    super.onStartInput(info, restarting);
    dispatcherActive = true;
    gestureDecodeGeneration++;
    closeTransientInterfaces(false);
    hideResizeOverlay();
    if (clipboardPanel != null) clipboardPanel.setVisibility(View.GONE);
    if (moreToolsPanel != null) hideMoreTools();
    if (emojiPanel != null) hideEmojiPanel();
    int type = info.inputType,
        cls = type & InputType.TYPE_MASK_CLASS,
        variation = type & InputType.TYPE_MASK_VARIATION;
    passwordField =
        variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            || variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    inputPhone = cls == InputType.TYPE_CLASS_PHONE;
    inputNumeric =
        inputPhone || cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME;
    multilineField =
        cls == InputType.TYPE_CLASS_TEXT && (type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
    symbols = false;
    shifted = false;
    editorContext = inputPhone ? "phone" : inputNumeric ? "number" : "text";
    if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS) editorContext = "email";
    else if (variation == InputType.TYPE_TEXT_VARIATION_URI) editorContext = "url";
    int action = info.imeOptions & EditorInfo.IME_MASK_ACTION, icon = R.drawable.ic_enter;
    boolean accent = false;
    if (action == EditorInfo.IME_ACTION_SEARCH) {
      editorContext = "search";
      icon = R.drawable.ic_search;
      accent = true;

    } else if (action == EditorInfo.IME_ACTION_SEND) {
      editorContext = "send";
      icon = R.drawable.ic_send;
      accent = true;

    } else if (action == EditorInfo.IME_ACTION_GO) {
      editorContext = "url";
      icon = R.drawable.ic_next;
      accent = true;

    } else if (action == EditorInfo.IME_ACTION_NEXT) {
      editorContext = "next";
      icon = R.drawable.ic_next;
      accent = true;

    } else if (action == EditorInfo.IME_ACTION_DONE) {
      editorContext = "done";
      icon = R.drawable.ic_done;
      accent = true;
    }
    if (multilineField || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
      icon = R.drawable.ic_enter;
      accent = false;
    }
    if (keyboard != null) {
      keyboard.setEnterAction(icon, accent);
      setToolbarVisibility(inputNumeric ? View.GONE : (toolBarVisible ? View.VISIBLE : View.GONE));
      normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
      applyLayout();
      refreshSuggestions();
    }
  }

  public void onUpdateSelection(int a, int b, int c, int d, int e, int f) {
    super.onUpdateSelection(a, b, c, d, e, f);
    if (dispatcherActive && keyboard != null) {
      handler.removeMessages(MSG_UPDATE_OLD_SUGGESTIONS);
      handler.sendEmptyMessageDelayed(MSG_UPDATE_OLD_SUGGESTIONS, 18);
    }
  }

  public void onStartInputView(EditorInfo info, boolean restarting) {
    dispatcherActive = true;
    super.onStartInputView(info, restarting);
    syncLayoutFromPrefs();
  }

  /**
   * The layout picker writes keyboard_ui prefs directly; whenever the editor takes the keyboard
   * back (returning from settings, switching apps) this re-reads the stored variant and re-applies
   * it only when it actually changed, so the keys match the picker without disturbing typing.
   */
  private String lastAppliedLayoutVariant;

  protected void syncLayoutFromPrefs() {
    if (keyboard == null || inputNumeric || symbols) {
      return;
    }
    boolean rtl = "ar".equals(language);
    String variant =
        getSharedPreferences("keyboard_ui", 0)
            .getString(rtl ? "arabic_layout" : "layout", rtl ? "ARABIC_DIGITS" : "QWERTY");
    if (!variant.equals(lastAppliedLayoutVariant)) {
      lastAppliedLayoutVariant = variant;
      applyLayout();
    }
  }

  protected void learnAsync(
      final String previous2, final String previous, final String word, final String wordLanguage) {
    if (engine == null || word == null || word.length() == 0) return;
    try {
      learningWorker.execute(
          new Runnable() {
            public void run() {
              try {
                engine.learn(previous2, previous, word, wordLanguage);
              } catch (RuntimeException ignored) {
                // Learning is optional and must never interrupt typing or suggestion selection.
              }
            }
          });
    } catch (RejectedExecutionException ignored) {
    }
  }

  protected void postUi(final Runnable action) {
    if (!dispatcherActive || action == null) return;
    handler.post(
        new Runnable() {
          public void run() {
            if (dispatcherActive) action.run();
          }
        });
  }

  protected void postUiDelayed(final Runnable action, long delayMillis) {
    if (!dispatcherActive || action == null) return;
    handler.postDelayed(
        new Runnable() {
          public void run() {
            if (dispatcherActive) action.run();
          }
        },
        delayMillis);
  }

  protected void releaseDispatcher() {
    dispatcherActive = false;
    gestureDecodeGeneration++;
    suggestionGeneration++;
    gestureDecoder.getQueue().clear();
    suggestionWorker.getQueue().clear();
    closeTransientInterfaces(false);
    handler.removeCallbacksAndMessages(null);
    if (keyboard != null) keyboard.releaseTransientState();
  }

  public void onWindowHidden() {
    releaseDispatcher();
    super.onWindowHidden();
  }

  public void onFinishInputView(boolean finishingInput) {
    releaseDispatcher();
    super.onFinishInputView(finishingInput);
  }

  public void onFinishInput() {
    releaseDispatcher();
    super.onFinishInput();
  }
}
