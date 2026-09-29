package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.util.AttributeSet;
import android.view.*;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.engine.GestureTrace;
import com.almlk.swiftkey.model.*;
import com.almlk.swiftkey.theme.AssetThemeLibrary;
import com.almlk.swiftkey.theme.KeyArtProcessor;
import com.almlk.swiftkey.theme.KeyFrames;
import com.almlk.swiftkey.theme.KeyboardTheme;
import com.almlk.swiftkey.theme.PressEffects;
import java.util.*;

public final class SmartKeyboardView extends View {
  private static final class Hit {
    final KeySpec key;
    final RectF rect;

    Hit(KeySpec k, RectF r) {
      key = k;
      rect = r;
    }
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float configuredKeyHeight, configuredHorizontalGap, configuredVerticalGap;
  private boolean disablePreview;
  private int keyTextWeight = 2;
  private final ArrayList<Hit> hits = new ArrayList<Hit>();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final int touchSlop;
  private KeyboardLayout layout = LayoutProvider.arabic102();
  private int enterIconRes = R.drawable.ic_enter;
  private boolean enterAccent = false, numberRowVisible = false;
  // R3 (images 2-3 rule): the keyboard BOX is anchored once; toggling the number row then
  // re-slices the SAME total over 4 or 5 rows instead of growing the panel by a whole row.
  private int blockAnchorRows = 0;
  private float blockAnchorKeyPx = 0f;
  private KeyboardTheme theme = KeyboardTheme.from("light");
  private Bitmap themeImage;
  /** Round 43: true while themeImage is the SHARED library bitmap — never recycled. */
  private boolean themeImageFromLibrary;
  private int themeImageGeneration;
  /**
   * Round 41: image key skins — decoded parts of the active theme's skin
   * (up / fun / space / press / enter). Empty map = programmatic key faces.
   */
  private final java.util.HashMap<String, Bitmap> keySkinBitmaps =
      new java.util.HashMap<String, Bitmap>();
  private String keySkinLoaded = "";
  /**
   * Round 50: decoded art of the professional button frames (keyframe_ID_R.png),
   * keyed by drawable name so a shape switch just resolves another entry.
   */
  private final java.util.HashMap<String, Bitmap> frameArtCache =
      new java.util.HashMap<String, Bitmap>();
  /**
   * Round 51: decoded art of the INTERNET-downloaded frame (keyFrameUri file),
   * keyed by path; plus the letter color derived from its pixels.
   */
  private final java.util.HashMap<String, Bitmap> onlineFrameCache =
      new java.util.HashMap<String, Bitmap>();
  /** Round 72: فن زر المسافة المستقل المرافق لزر ثيماتي المحمّل. */
  private final java.util.HashMap<String, Bitmap> onlineSpaceFrameCache =
      new java.util.HashMap<String, Bitmap>();
  private int onlineFrameTextColor = 0xff172033;
  private int onlineSpaceFrameTextColor = 0xff172033;
  // ------------------------------------------------ Round 56: تفاعلات الضغط المتحركة
  /** 0 = بلا تفاعل، 1..10 = تصاميم PressEffects المدمجة. */
  private int pressEffect;
  /** مسار صورة تفاعل محمّلة من الإنترنت — أولوية على المدمج. */
  private String pressEffectUri = "";
  /** sprite التفاعل المفكوك موحّد الحجم (~dp22) — مدمج أو من ملف محمّل. */
  private Bitmap pressSprite;
  /** جسيم تفاعل واحد: موضع/سرعة/دوران/حجم/عمر + سلوك (صاعد أو منفجر). */
  private static final class PressParticle {
    float x, y, vx, vy, rot, vr, scale, life;
    boolean rise;
  }
  private final ArrayList<PressParticle> pressParticles = new ArrayList<PressParticle>();
  private long pressFrameNanos;
  private final Paint pressPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final Matrix pressMatrix = new Matrix();
  private String loadingThemeSource = "", loadedThemeSource = "";
  private boolean themeAnimated;
  private KeyboardActionListener listener;
  private Hit pressed;
  private float downX, downY, spaceDiffX, spaceDiffY;
  private int spaceGestureAxis, altSelected = -1, touchPointerId = -1;
  private boolean repeatedDelete, localeVisible, altVisible, altPersistent;
  /** Round 58: true عندما أطلق الضغط المطوّل فعل الحافظة — يستهلك الرفع فلا تُدخل فاصلة. */
  private boolean specialLongPress;
  /** Round 58: مفتاح الحافظة المُرسل للطبقة العليا عند الضغط المطوّل على زر الفاصلة. */
  private static final KeySpec CLIPBOARD_KEY =
      new KeySpec("\u0627\u0644\u062d\u0627\u0641\u0638\u0629", "", "", KeySpec.CLIPBOARD, 1.1f);
  private boolean gestureInputAllowed = true, gestureActive, gestureFading;
  private final ArrayList<PointF> gesturePoints = new ArrayList<PointF>();
  private final StringBuilder gestureKeys = new StringBuilder();
  private final HashSet<String> gestureVisitedKeys = new HashSet<String>();
  private String pendingGestureKey = "";
  private int pendingGestureSamples;
  private long gestureFadeStarted;
  private long lastGesturePreviewTime;
  private String lastGesturePreviewKeys = "";
  private String[] altValues = new String[0];
  private RectF altRect = new RectF();
  private KeySpec alternativeKey;
  private AlternativePopupView alternativePopup;
  private AlternativePopupView keyPreviewPopup;
  private android.widget.PopupWindow alternativeWindow;
  private final Runnable themeMotion =
      new Runnable() {
        public void run() {
          if (themeAnimated && getWindowVisibility() == VISIBLE) {
            invalidate();
            handler.postDelayed(this, 60);
          }
        }
      };
  private final Runnable gestureTrailFade =
      new Runnable() {
        public void run() {
          if (!gestureFading) return;
          if (SystemClock.uptimeMillis() - gestureFadeStarted >= 190) {
            clearGestureTrail();
            return;
          }
          invalidate();
          handler.postDelayed(this, 16);
        }
      };
  private final Runnable longPress =
      new Runnable() {
        public void run() {
          if (pressed == null) return;
          if (hasAlternatives(pressed.key)) {
            showAlternatives(pressed);
          } else if (pressed.key.code == KeySpec.MIC) {
            // Round 58: الضغط المطوّل على زر الفاصلة يفتح التسجيل (سجل الحافظة)
            specialLongPress = true;
            dismissKeyPreview();
            if (listener != null) listener.onKey(CLIPBOARD_KEY);
          }
        }
      };
  private final Runnable deleteRepeat =
      new Runnable() {
        public void run() {
          if (pressed != null && pressed.key.code == KeySpec.DELETE) {
            repeatedDelete = true;
            if (listener != null) listener.onKey(pressed.key);
            handler.postDelayed(this, interactionTiming("delete_repeat_interval", 48, 32, 90));
          }
        }
      };

  public SmartKeyboardView(Context c, AttributeSet a) {
    super(c, a);
    touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
    configuredKeyHeight = attributeDimension(a, "keyHeight", dp(57));
    configuredHorizontalGap = attributeDimension(a, "horizontalGap", dp(3));
    configuredVerticalGap = attributeDimension(a, "verticalGap", dp(3));
    disablePreview = attributeBoolean(a, "disablePreview", false);
    keyTextWeight = attributeInt(a, "keyTextWeight", 2);
    String textWeight = attributeValue(a, "keyTextWeight", "");
    if ("small".equals(textWeight)) {
      keyTextWeight = 1;
    } else if ("normal".equals(textWeight)) {
      keyTextWeight = 2;
    } else if ("large".equals(textWeight)) {
      keyTextWeight = 3;
    }
    setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    setFocusable(true);
  }

  private int attributeIndex(AttributeSet attributes, String name) {
    if (attributes == null) {
      return -1;
    }
    for (int index = 0; index < attributes.getAttributeCount(); index++) {
      if (name.equals(attributes.getAttributeName(index))) {
        return index;
      }
    }
    return -1;
  }

  private String attributeValue(AttributeSet attributes, String name, String fallback) {
    int index = attributeIndex(attributes, name);
    if (index < 0) {
      return fallback;
    }
    String value = attributes.getAttributeValue(index);
    return value == null ? fallback : value;
  }

  private int attributeInt(AttributeSet attributes, String name, int fallback) {
    int index = attributeIndex(attributes, name);
    return index < 0 ? fallback : attributes.getAttributeIntValue(index, fallback);
  }

  private boolean attributeBoolean(AttributeSet attributes, String name, boolean fallback) {
    int index = attributeIndex(attributes, name);
    return index < 0 ? fallback : attributes.getAttributeBooleanValue(index, fallback);
  }

  private float attributeDimension(AttributeSet attributes, String name, float fallback) {
    int index = attributeIndex(attributes, name);
    if (index < 0) {
      return fallback;
    }
    int resource = attributes.getAttributeResourceValue(index, 0);
    if (resource != 0) {
      try {
        return getResources().getDimension(resource);
      } catch (android.content.res.Resources.NotFoundException ignored) {
        return fallback;
      }
    }
    String raw = attributes.getAttributeValue(index);
    if (raw == null) {
      return fallback;
    }
    String value = raw.trim().toLowerCase(java.util.Locale.US);
    float factor = 1f;
    int suffix = 0;
    if (value.endsWith("dip")) {
      factor = getResources().getDisplayMetrics().density;
      suffix = 3;
    } else if (value.endsWith("dp")) {
      factor = getResources().getDisplayMetrics().density;
      suffix = 2;
    } else if (value.endsWith("sp")) {
      factor = getResources().getDisplayMetrics().scaledDensity;
      suffix = 2;
    } else if (value.endsWith("px")) {
      suffix = 2;
    }
    try {
      return Float.parseFloat(value.substring(0, value.length() - suffix)) * factor;
    } catch (Exception ignored) {
      return fallback;
    }
  }

  public void setListener(KeyboardActionListener l) {
    listener = l;
  }

  public void setGestureInputAllowed(boolean allowed) {
    gestureInputAllowed = allowed;
    if (!allowed) clearGestureTrail();
  }

  public void setKeyHeightDp(float value) {
    float next = dp(value);
    if (Math.abs(configuredKeyHeight - next) < .05f) return;
    configuredKeyHeight = next;
    // the resize gesture (live AND commit) owns the anchor: total = rows-now * unit; future
    // number-row toggles re-distribute THIS total, they never change it.
    blockAnchorKeyPx = next;
    blockAnchorRows = visibleRowCount();
    requestLayout();
    invalidate();
  }

  public void setEnterAction(int drawableRes, boolean accent) {
    enterIconRes = drawableRes;
    enterAccent = accent;
    invalidate();
  }

  public void setKeyboardLayout(KeyboardLayout x) {
    layout = x;
    dismissPopups();
    requestLayout();
    invalidate();
  }

  public void setNumberRowVisible(boolean visible) {
    if (numberRowVisible != visible) {
      numberRowVisible = visible;
      requestLayout();
      invalidate();
    }
  }

  public boolean isNumberRowVisible() {
    return numberRowVisible;
  }

  public float getKeyHeightDp() {
    return configuredKeyHeight / getResources().getDisplayMetrics().density;
  }

  public int getVisibleRowCount() {
    return Math.max(1, visibleRows().size());
  }

  public int getHeightReferenceRowCount() {
    return visibleRowCount();   // the SAME count onMeasure and onDraw live by
  }

  public void setTheme(KeyboardTheme t) {
    theme = t == null ? KeyboardTheme.from("light") : t;
    handler.removeCallbacks(themeMotion);
    themeAnimated = theme.animationStyle != KeyboardTheme.ANIMATION_NONE;
    loadThemeImageAsync(theme.imageSource());
    prepareKeySkin();
    frameArtCache.clear(); // Round 50: إطارات الثيم الجديد تُفكّك من جديد
    onlineFrameCache.clear(); // Round 51: وإطار الإنترنت المحمَّل كذلك
    onlineSpaceFrameCache.clear(); // Round 72: وزر المسافة المرافق
    pressEffect = theme.pressEffect; // Round 56: تفاعل الضغط المتحرك
    pressEffectUri = theme.pressEffectUri == null ? "" : theme.pressEffectUri;
    updatePressSprite();
    if (alternativePopup != null) alternativePopup.configure(altValues, altSelected, theme);
    if (themeAnimated) handler.post(themeMotion);
    invalidate();
  }

  private void loadThemeImageAsync(final String uri) {
    if (uri != null && uri.length() > 0) {
      if (uri.equals(loadingThemeSource)) return;
      if (uri.equals(loadedThemeSource) && themeImage != null) return;
    }
    final int generation = ++themeImageGeneration;
    loadingThemeSource = uri == null ? "" : uri;
    loadedThemeSource = "";
    if (themeImage != null) {
      // Round 64: لا إعادة تدوير إطلاقاً — الرسم العتادي (display list) قد يملك
      // الصورة القديمة إطاراً بعد استبدالها فتنهار بـ «recycled bitmap»؛
      // إسقاط المرجع والجامع يكفيان والذاكرة محدودة أصلاً بالفك المتقن.
      themeImage = null;
      themeImageFromLibrary = false;
    }
    if (uri == null || uri.length() == 0) {
      invalidate();
      return;
    }
    Thread loader =
        new Thread(
            new Runnable() {
              public void run() {
                final Bitmap decoded = decodeThemeImage(uri);
                handler.post(
                    new Runnable() {
                      public void run() {
                        if (generation != themeImageGeneration) {
                          // Round 64: نسخة متجاوزة — تُسقط فقط بلا recycle؛
                          // التدوير المبكر كان يكسر قوائم العرض العتادية.
                          return;
                        }
                        loadingThemeSource = "";
                        loadedThemeSource = decoded == null ? "" : uri;
                        themeImage = decoded;
                        themeImageFromLibrary =
                            decoded != null && uri.startsWith(AssetThemeLibrary.URI_PREFIX);
                        invalidate();
                      }
                    });
              }
            },
            "Almlk-Theme-Image");
    loader.setPriority(Thread.MIN_PRIORITY);
    loader.start();
  }

  private Bitmap decodeThemeImage(String value) {
    // Round 43: imported theme backgrounds decode through the shared library
    // cache — the returned bitmap is SHARED and must never be recycled here.
    if (value.startsWith(AssetThemeLibrary.URI_PREFIX))
      return AssetThemeLibrary.keyboardBackground(getContext(), value);
    java.io.InputStream stream = null;
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = 2;
      if (value.indexOf(':') < 0) {
        int id = getResources().getIdentifier(value, "drawable", getContext().getPackageName());
        return id == 0 ? null : BitmapFactory.decodeResource(getResources(), id, options);
      }
      stream = getContext().getContentResolver().openInputStream(android.net.Uri.parse(value));
      return BitmapFactory.decodeStream(stream, null, options);
    } catch (Exception ignored) {
      return null;
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private float dp(float x) {
    return x * getResources().getDisplayMetrics().density;
  }

  private float clampFloat(float value, float minimum, float maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  /**
   * Round 45: حجم تلقائي آمن للنص داخل وجه الزر. يمنع خروج 123 أو {&=}
   * أو أي رمز متعدد المحارف خارج إطار الزر عند تصغير عرض/ارتفاع الأزرار.
   */
  private float fittedTextSize(
      String text, float requestedPx, RectF face, float horizontalPadding, float heightShare) {
    if (text == null || text.length() == 0) return requestedPx;
    float maxWidth = Math.max(dp(8f), face.width() - horizontalPadding * 2f);
    float maxHeight = Math.max(dp(8f), face.height() * heightShare);
    float size = requestedPx;
    paint.setTextSize(size);
    float width = Math.max(1f, paint.measureText(text));
    if (width > maxWidth) size *= maxWidth / width;
    paint.setTextSize(size);
    Paint.FontMetrics metrics = paint.getFontMetrics();
    float height = Math.max(1f, metrics.descent - metrics.ascent);
    if (height > maxHeight) size *= maxHeight / height;
    return Math.max(dp(7f), size);
  }

  private float baselineInside(RectF face, Paint.FontMetrics metrics, float wanted, float margin) {
    float min = face.top + margin - metrics.ascent;
    float max = face.bottom - margin - metrics.descent;
    if (min > max) return face.centerY() - (metrics.ascent + metrics.descent) / 2f;
    return clampFloat(wanted, min, max);
  }

  private void popupFeedback() {
    android.content.SharedPreferences settings =
        getContext().getSharedPreferences("keyboard", Context.MODE_PRIVATE);
    if (!settings.getBoolean("vibration", true)) return;
    android.os.Vibrator vibrator =
        (android.os.Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
    if (vibrator == null || !vibrator.hasVibrator()) return;
    int duration = Math.max(5, Math.min(14, settings.getInt("vibration_duration", 16)));
    int strength = Math.max(1, Math.min(255, settings.getInt("vibration_strength", 55) / 2));
    if (Build.VERSION.SDK_INT >= 26) {
      vibrator.vibrate(VibrationEffect.createOneShot(duration, strength));
    } else {
      vibrator.vibrate(duration);
    }
  }

  private int interactionTiming(String key, int fallback, int minimum, int maximum) {
    int value =
        getContext().getSharedPreferences("keyboard", Context.MODE_PRIVATE).getInt(key, fallback);
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static final String[] WESTERN_DIGITS = {
    "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"
  };
  private static final String[] ARABIC_DIGITS = {
    "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠"
  };

  private List<List<KeySpec>> visibleRows() {
    if (!numberRowVisible) return layout.rows;
    ArrayList<List<KeySpec>> rows = new ArrayList<List<KeySpec>>();
    ArrayList<KeySpec> numbers = new ArrayList<KeySpec>();
    for (String value : numberRowDigits()) numbers.add(KeySpec.c(value, "", ""));
    rows.add(numbers);
    rows.addAll(layout.rows);
    return rows;
  }

  /**
   * Resolves the number-row digits dynamically: the explicit user setting wins, otherwise the
   * layout native digitType from XML, otherwise the layout direction.
   */
  private String[] numberRowDigits() {
    int setting = digitTypeSetting();
    int type;
    if (setting == 0 || setting == 1) {
      type = setting;
    } else if (layout != null && layout.digitType >= 0) {
      type = layout.digitType;
    } else {
      type = layout != null && layout.rtl ? 1 : 0;
    }
    return type == 1 ? ARABIC_DIGITS : WESTERN_DIGITS;
  }

  private int digitTypeSetting() {
    try {
      int value =
          getContext()
              .getSharedPreferences("keyboard", Context.MODE_PRIVATE)
              .getInt("number_digit_type", 2);
      return value >= 0 && value <= 2 ? value : 2;
    } catch (Exception ignored) {
      return 2;
    }
  }

  protected void onMeasure(int w, int h) {
    int width = MeasureSpec.getSize(w);
    // ONE source of truth: the exact rows onDraw will paint (number row included), each row
    // costing key + its vertical gap, plus the 4dp top + 4dp bottom chrome the paint loop uses.
    // Anything else splits the difference into the bottom row — that is the band the resize
    // gesture "changed the keyboard from below" complaint came from.
    int height = getComputedBlockHeightPx();
    setMeasuredDimension(width, resolveSize(height, h));
  }

  /**
   * The exact box the rows model produces. onMeasure and the keyboard-resize commit share THIS
   * formula, so pinning the view's LayoutParams to it can never split from the painted block.
   */
  public int getComputedBlockHeightPx() {
    int rows = visibleRowCount();
    boolean anchored = blockAnchorRows > 0 && blockAnchorKeyPx > 0f;
    if (!anchored && layout != null && layout.rows != null && !layout.rows.isEmpty()) {
      // lazy anchor ONLY at a REAL laid-out frame. Anchoring at the pre-attach stub (layout
      // not assigned yet -> visibleRowCount()==1) would pin the whole keyboard to ONE row's
      // height — the collapse hazard this guard removes. Before any real frame the view simply
      // uses the plain rows*unit box, like always.
      blockAnchorRows = rows;
      blockAnchorKeyPx = configuredKeyHeight;
      anchored = true;
    }
    if (anchored && rows != blockAnchorRows) {
      // total PINS to the anchored box; the paint loop divides getHeight() by the rows it draws
      // this frame, so 5 rows get 5 equal thinner bands, 4 rows get 4 equal fatter ones.
      return (int) (blockAnchorRows * (blockAnchorKeyPx + configuredVerticalGap) + dp(8f));
    }
    return (int) (rows * (configuredKeyHeight + configuredVerticalGap) + dp(8f));
  }

  /** Rows the paint loop will actually draw THIS frame — the height is computed from it. */
  private int visibleRowCount() {
    if (layout == null || layout.rows == null) {
      return 1;
    }
    return Math.max(1, visibleRows().size());
  }

  protected void onDraw(Canvas c) {
    super.onDraw(c);
    drawThemeBackground(c);
    hits.clear();
    List<List<KeySpec>> visible = visibleRows();
    float y = dp(3),
        gap = configuredHorizontalGap,
        rowH = (getHeight() - dp(8)) / visible.size(),
        maxLetterWeight = 0;
    for (List<KeySpec> candidate : visible)
      if (!isBottomRow(candidate)
          && !isNumberRow(candidate)
          && !isEnglishEdgeActionRow(candidate)) {
        float sum = rowWeight(candidate);
        if (sum > maxLetterWeight) maxLetterWeight = sum;
      }
    for (List<KeySpec> row : visible) {
      boolean bottomRow = isBottomRow(row);
      float total = rowWeight(row), unit, x;
      if (!isBottomRow(row)
          && !isNumberRow(row)
          && !isEnglishEdgeActionRow(row)
          && maxLetterWeight > 0) {
        unit = (getWidth() - gap * (maxLetterWeight + 1)) / maxLetterWeight;
        float used = unit * total + gap * (row.size() - 1);
        x = (getWidth() - used) / 2f;
      } else {
        unit = (getWidth() - gap * (row.size() + 1)) / total;
        x = gap;
      }
      for (KeySpec k : row) {
        float kw = unit * k.weight;
        RectF r = new RectF(x, y, x + kw, y + rowH - configuredVerticalGap);
        hits.add(new Hit(k, r));
        drawKey(c, k, r, pressed != null && pressed.key == k, bottomRow);
        x += kw + gap;
      }
      y += rowH;
    }
    drawGestureTrail(c);
    drawPressParticles(c); // Round 56: تفاعلات الضغط فوق كل المفاتيح
  }

  // ------------------------------------------------------------ Round 56: محرك التفاعلات

  /** يفكّ sprite التفاعل الحالي (ملف الإنترنت أولاً ثم المدمج) بحجم موحّد صغير. */
  private void updatePressSprite() {
    pressSprite = null;
    try {
      if (pressEffectUri.length() > 0) {
        // Round 63: فكّ محدود بكاش (مسخَّن على خيط التحميل) — لا صورة ضخمة
        // على الخيط الرئيسي فتتجمد لوحة المفاتيح أو تتوقف
        pressSprite = KeyArtProcessor.fileSprite(pressEffectUri);
      } else if (pressEffect >= 1 && pressEffect <= PressEffects.COUNT) {
        int artId =
            getResources()
                .getIdentifier(
                    PressEffects.artName(pressEffect),
                    "drawable",
                    getContext().getPackageName());
        if (artId != 0) pressSprite = BitmapFactory.decodeResource(getResources(), artId);
      }
      if (pressSprite != null) {
        float size = dp(22);
        float scale =
            Math.min(size / pressSprite.getWidth(), size / pressSprite.getHeight());
        if (scale > 0 && Math.abs(scale - 1f) > .01f) {
          Matrix spriteMatrix = new Matrix();
          spriteMatrix.setScale(scale, scale);
          pressSprite =
              Bitmap.createBitmap(
                  pressSprite, 0, 0, pressSprite.getWidth(), pressSprite.getHeight(),
                  spriteMatrix, true);
        }
      }
    } catch (Throwable ignored) {
      pressSprite = null; // تصميم تالف لا يوقف الكيبورد أبداً
    }
  }

  /** ينثر جسيمات التفاعل فوق المفتاح المضغوط — قلوب تصعد أو شرارات تنفجر. */
  private void spawnPressParticles(float centerX, float centerY) {
    if (pressSprite == null) return;
    boolean rise =
        pressEffectUri.length() > 0
            ? true
            : PressEffects.rises(pressEffect);
    for (int index = 0; index < 9; index++) {
      PressParticle particle = new PressParticle();
      particle.x = centerX + (float) ((Math.random() - .5) * dp(14));
      particle.y = centerY + (float) ((Math.random() - .5) * dp(10));
      particle.rot = (float) ((Math.random() - .5) * 70f);
      particle.vr = (float) ((Math.random() - .5) * 100f);
      particle.scale = .55f + (float) Math.random() * .6f;
      particle.life = 1f;
      particle.rise = rise;
      if (rise) {
        particle.vx = (float) ((Math.random() - .5) * dp(36));
        particle.vy = -dp((float) (54 + Math.random() * 48));
      } else {
        double angle = Math.random() * Math.PI * 2;
        float speed = dp((float) (58 + Math.random() * 60));
        particle.vx = (float) Math.cos(angle) * speed;
        particle.vy = (float) Math.sin(angle) * speed - dp(18);
      }
      pressParticles.add(particle);
    }
    postInvalidateOnAnimation();
  }

  /** عرض تجريبي فوري للتفاعل — يستدعيه الاستوديو عند اختيار تصميم. */
  public void demoPressEffect() {
    spawnPressParticles(getWidth() / 2f, getHeight() * .42f);
  }

  /** يحرّك الجسيمات ويرسمها — حلقة postInvalidateOnAnimation حتى تخمد كلها. */
  private void drawPressParticles(Canvas c) {
    if (pressParticles.isEmpty()) return;
    if (pressSprite == null) {
      pressParticles.clear();
      pressFrameNanos = 0;
      return;
    }
    long now = System.nanoTime();
    float dt =
        pressFrameNanos == 0
            ? .016f
            : Math.min(.05f, (now - pressFrameNanos) / 1000000000f);
    pressFrameNanos = now;
    float half = pressSprite.getWidth() / 2f;
    for (int index = pressParticles.size() - 1; index >= 0; index--) {
      PressParticle particle = pressParticles.get(index);
      particle.life -= dt * (particle.rise ? 1.05f : 1.5f);
      if (particle.life <= 0) {
        pressParticles.remove(index);
        continue;
      }
      particle.x += particle.vx * dt;
      particle.y += particle.vy * dt;
      particle.rot += particle.vr * dt;
      if (particle.rise) particle.vx *= Math.max(0f, 1f - 1.6f * dt);
      else particle.vy += dp(74) * dt;
      float grow = Math.min(1f, (1f - particle.life) * 3.5f);
      float size =
          particle.scale * (.62f + .38f * grow) * (particle.rise ? 1f : .4f + .6f * particle.life);
      float alpha = Math.min(1f, particle.life * 1.7f);
      pressPaint.setAlpha((int) (255 * alpha));
      pressMatrix.reset();
      pressMatrix.postScale(size, size);
      pressMatrix.postRotate(particle.rot, half * size, half * size);
      pressMatrix.postTranslate(particle.x - half * size, particle.y - half * size);
      c.drawBitmap(pressSprite, pressMatrix, pressPaint);
    }
    if (pressParticles.isEmpty()) pressFrameNanos = 0;
    else postInvalidateOnAnimation();
  }

  private float rowWeight(List<KeySpec> row) {
    float total = 0;
    for (KeySpec key : row) total += key.weight;
    return total;
  }

  private boolean isBottomRow(List<KeySpec> row) {
    for (KeySpec key : row) if (key.code == KeySpec.SPACE) return true;
    return false;
  }

  private boolean isEnglishEdgeActionRow(List<KeySpec> row) {
    return !layout.rtl
        && row.size() >= 3
        && row.get(0).code == KeySpec.SHIFT
        && row.get(row.size() - 1).code == KeySpec.DELETE;
  }

  private boolean isNumberRow(List<KeySpec> row) {
    if (row.size() != 10) return false;
    for (KeySpec key : row) {
      boolean western = key.code >= '0' && key.code <= '9',
          arabic = key.code >= '٠' && key.code <= '٩';
      if (!western && !arabic) return false;
    }
    return true;
  }

  private void drawThemeBackground(Canvas canvas) {
    // Round 64: حارس نهائي ضد «Canvas: trying to use a recycled bitmap» —
    // أي صورة معاد تدويرها تُعامل كغياب الخلفية فوراً.
    if (themeImage == null || themeImage.isRecycled()) {
      canvas.drawColor(theme.background);
      applyBackgroundDim(canvas);
      return;
    }
    long now = SystemClock.uptimeMillis();
    float scale =
        Math.max(
            getWidth() / (float) themeImage.getWidth(),
            getHeight() / (float) themeImage.getHeight());
    float phase = .5f;
    int animation = theme.animationStyle;
    if (animation == KeyboardTheme.ANIMATION_SPEED) {
      phase = (float) ((Math.sin(now / 1450.0) + 1.0) * .5);
      scale *= 1.08f;
    } else if (animation == KeyboardTheme.ANIMATION_NEON) {
      phase = .25f + (float) ((Math.sin(now / 2200.0) + 1.0) * .25);
      scale *= 1.07f;
    } else if (animation == KeyboardTheme.ANIMATION_PARALLAX) {
      phase = (float) ((Math.sin(now / 4300.0) + 1.0) * .5);
      scale *= 1.05f;
    } else if (animation == KeyboardTheme.ANIMATION_FIREFLIES) {
      phase = .42f + (float) ((Math.sin(now / 6100.0) + 1.0) * .08);
      scale *= 1.035f;
    } else if (animation == KeyboardTheme.ANIMATION_ROYAL) {
      phase = .46f + (float) ((Math.sin(now / 7200.0) + 1.0) * .04);
      scale *= 1.025f;
    }
    float width = themeImage.getWidth() * scale;
    float height = themeImage.getHeight() * scale;
    float travel = Math.max(0, width - getWidth());
    float left = -travel * phase;
    RectF target =
        new RectF(left, (getHeight() - height) / 2f, left + width, (getHeight() + height) / 2f);
    paint.setAlpha(255);
    canvas.drawBitmap(themeImage, null, target, paint);
    paint.setColor(theme.background);
    paint.setAlpha(theme.animationStyle == KeyboardTheme.ANIMATION_ROYAL ? 42 : 58);
    canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    paint.setAlpha(255);
    applyBackgroundDim(canvas);
    drawAmbientAnimation(canvas, now);
  }

  /**
   * Round 40: the user-controlled dim/brightness layer over the theme background (image or
   * color). Negative dims (black overlay), positive lightens (white overlay); 0 draws nothing.
   */
  private void applyBackgroundDim(Canvas canvas) {
    float dim = theme.backgroundDim;
    if (dim == 0f) return;
    if (dim < 0f) {
      paint.setColor(Color.BLACK);
      paint.setAlpha((int) (Math.min(.85f, -dim) * 255f));
    } else {
      paint.setColor(Color.WHITE);
      paint.setAlpha((int) (Math.min(.85f, dim) * 255f));
    }
    canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    paint.setAlpha(255);
  }

  private void drawAmbientAnimation(Canvas canvas, long now) {
    int style = theme.animationStyle;
    if (style == KeyboardTheme.ANIMATION_NONE || style == KeyboardTheme.ANIMATION_PARALLAX) {
      return;
    }
    paint.setStrokeWidth(dp(1.2f));
    paint.setColor(theme.accent);
    if (style == KeyboardTheme.ANIMATION_FIREFLIES) {
      paint.setStyle(Paint.Style.FILL);
      for (int i = 0; i < 13; i++) {
        float wave = (float) Math.sin(now / 620.0 + i * 1.73);
        float x = getWidth() * ((i * 37 % 101) / 100f) + wave * dp(5);
        float y = getHeight() * ((i * 61 % 97) / 100f) - wave * dp(7);
        paint.setAlpha(45 + (int) (Math.abs(wave) * 145));
        canvas.drawCircle(x, y, dp(1.1f + (i % 3) * .45f), paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_SPEED) {
      paint.setStyle(Paint.Style.STROKE);
      for (int i = 0; i < 7; i++) {
        float progress = ((now / 7f + i * 157f) % 1100f) / 1100f;
        float x = getWidth() * (1f - progress);
        float y = getHeight() * (.12f + i * .13f);
        paint.setAlpha(45 + i * 16);
        canvas.drawLine(x, y, Math.min(getWidth(), x + dp(34 + i * 4)), y, paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_NEON) {
      paint.setStyle(Paint.Style.STROKE);
      for (int i = 0; i < 9; i++) {
        float progress = ((now / 9f + i * 131f) % 900f) / 900f;
        float x = getWidth() * ((i * 29 % 97) / 100f);
        float y = getHeight() * progress;
        paint.setAlpha(38 + i * 11);
        canvas.drawLine(x, y, x - dp(8), y + dp(22), paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_FIREWORKS) {
      paint.setStyle(Paint.Style.STROKE);
      for (int burst = 0; burst < 2; burst++) {
        float progress = ((now + burst * 1250L) % 2500L) / 2500f;
        float cx = getWidth() * (burst == 0 ? .27f : .76f);
        float cy = getHeight() * (burst == 0 ? .25f : .38f);
        float radius = dp(7) + progress * dp(42);
        paint.setAlpha((int) (170 * (1f - progress)));
        for (int ray = 0; ray < 12; ray++) {
          double angle = ray * Math.PI / 6.0;
          float inner = radius * .52f;
          canvas.drawLine(
              cx + (float) Math.cos(angle) * inner,
              cy + (float) Math.sin(angle) * inner,
              cx + (float) Math.cos(angle) * radius,
              cy + (float) Math.sin(angle) * radius,
              paint);
        }
      }
    } else if (style == KeyboardTheme.ANIMATION_LANTERN) {
      paint.setStyle(Paint.Style.FILL);
      float pulse = .5f + .5f * (float) Math.sin(now / 520.0);
      for (int i = 0; i < 4; i++) {
        float x = getWidth() * (.14f + i * .24f);
        float y = getHeight() * (.10f + (i % 2) * .08f);
        paint.setAlpha((int) (24 + pulse * 34));
        canvas.drawCircle(x, y, dp(17 + i * 2), paint);
        paint.setAlpha((int) (65 + pulse * 55));
        canvas.drawCircle(x, y, dp(3.2f), paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_ROYAL
        || style == KeyboardTheme.ANIMATION_SPARKLES) {
      paint.setStyle(Paint.Style.STROKE);
      for (int i = 0; i < 11; i++) {
        float twinkle = Math.abs((float) Math.sin(now / 470.0 + i * 2.11));
        float x = getWidth() * ((i * 43 % 101) / 100f);
        float y = getHeight() * ((i * 67 % 89) / 100f);
        float size = dp(1.5f + twinkle * 3.2f);
        paint.setAlpha((int) (35 + twinkle * 175));
        canvas.drawLine(x - size, y, x + size, y, paint);
        canvas.drawLine(x, y - size, x, y + size, paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_PULSE) {
      paint.setStyle(Paint.Style.STROKE);
      float pulse = .5f + .5f * (float) Math.sin(now / 420.0);
      paint.setStrokeWidth(dp(1f + pulse * 1.5f));
      paint.setAlpha((int) (40 + pulse * 90));
      canvas.drawRoundRect(
          new RectF(dp(4), dp(4), getWidth() - dp(4), getHeight() - dp(4)), dp(18), dp(18), paint);
    } else if (style == KeyboardTheme.ANIMATION_STARS) {
      paint.setStyle(Paint.Style.STROKE);
      for (int i = 0; i < 17; i++) {
        float pulse = Math.abs((float) Math.sin(now / 410.0 + i * 1.41));
        float x = getWidth() * ((i * 47 % 101) / 100f);
        float y = getHeight() * ((i * 71 % 97) / 100f);
        float size = dp(1.3f + pulse * 3.1f);
        paint.setAlpha((int) (35 + pulse * 190));
        canvas.drawLine(x - size, y, x + size, y, paint);
        canvas.drawLine(x, y - size, x, y + size, paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_PLANETS) {
      paint.setStyle(Paint.Style.STROKE);
      for (int i = 0; i < 5; i++) {
        float orbit = (now / (1450f + i * 190f) + i * 1.37f);
        float x = getWidth() * (.16f + i * .18f) + (float) Math.sin(orbit) * dp(9);
        float y = getHeight() * (.18f + (i % 3) * .28f) + (float) Math.cos(orbit) * dp(7);
        float radius = dp(3.5f + i % 3 * 1.7f);
        paint.setAlpha(85 + i * 25);
        canvas.drawCircle(x, y, radius, paint);
        canvas.drawOval(
            new RectF(x - radius * 1.8f, y - radius * .45f, x + radius * 1.8f, y + radius * .45f),
            paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_HEARTS) {
      paint.setStyle(Paint.Style.FILL);
      for (int i = 0; i < 11; i++) {
        float progress = ((now / 10f + i * 137f) % 1200f) / 1200f;
        float x = getWidth() * ((i * 31 % 97) / 100f) + (float) Math.sin(now / 500.0 + i) * dp(4);
        float y = getHeight() * (1.05f - progress);
        paint.setAlpha((int) (45 + 150 * (1f - progress)));
        drawHeart(canvas, x, y, dp(3.5f + i % 3), paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_SNOW) {
      paint.setStyle(Paint.Style.FILL);
      for (int i = 0; i < 24; i++) {
        float progress = ((now / (15f + i % 5) + i * 83f) % 1000f) / 1000f;
        float x = getWidth() * ((i * 43 % 101) / 100f) + (float) Math.sin(now / 720.0 + i) * dp(5);
        float y = getHeight() * progress;
        paint.setAlpha(70 + i % 5 * 25);
        canvas.drawCircle(x, y, dp(1f + i % 3 * .5f), paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_BUBBLES) {
      paint.setStyle(Paint.Style.STROKE);
      for (int i = 0; i < 13; i++) {
        float progress = ((now / (13f + i % 4) + i * 109f) % 1100f) / 1100f;
        float x = getWidth() * ((i * 37 % 97) / 100f) + (float) Math.sin(now / 630.0 + i) * dp(6);
        float y = getHeight() * (1.05f - progress);
        paint.setAlpha((int) (45 + 120 * (1f - progress)));
        canvas.drawCircle(x, y, dp(2.5f + i % 4 * 1.4f), paint);
      }
    } else if (style == KeyboardTheme.ANIMATION_PETALS) {
      paint.setStyle(Paint.Style.FILL);
      for (int i = 0; i < 15; i++) {
        float progress = ((now / (18f + i % 3) + i * 73f) % 1000f) / 1000f;
        float x = getWidth() * ((i * 41 % 103) / 100f) + progress * dp(16);
        float y = getHeight() * progress;
        paint.setAlpha(55 + i % 5 * 25);
        canvas.save();
        canvas.rotate(progress * 260f + i * 29f, x, y);
        canvas.drawOval(new RectF(x - dp(3), y - dp(1.5f), x + dp(3), y + dp(1.5f)), paint);
        canvas.restore();
      }
    } else if (style == KeyboardTheme.ANIMATION_MUSIC) {
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(1.5f));
      for (int i = 0; i < 9; i++) {
        float progress = ((now / 14f + i * 121f) % 1000f) / 1000f;
        float x = getWidth() * ((i * 37 % 93) / 100f);
        float y = getHeight() * (1f - progress);
        float size = dp(4 + i % 3);
        paint.setAlpha((int) (45 + 145 * (1f - progress)));
        canvas.drawCircle(x, y, size * .42f, paint);
        canvas.drawLine(x + size * .4f, y, x + size * .4f, y - size * 2f, paint);
        canvas.drawLine(x + size * .4f, y - size * 2f, x + size * 1.4f, y - size * 1.65f, paint);
      }
    }
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(255);
  }

  private int fontOverride() {
    try {
      return getContext()
          .getSharedPreferences("keyboard", Context.MODE_PRIVATE)
          .getInt("key_font_override", -1);
    } catch (Exception ignored) {
      return -1;
    }
  }

  private String keyFontFamily() {
    int override = fontOverride();
    if (override == 0) return "sans-serif";
    if (override == 1) return "serif";
    if (override == 2) return "sans-serif-condensed";
    if (override == 3) return "monospace";
    if (override == 4) return "sans-serif-light";
    if (theme.fontStyle == 1) return "serif";
    if (theme.fontStyle == 2) return "sans-serif-condensed";
    if (theme.fontStyle == 3) return "monospace";
    if (theme.fontStyle == 4) return "sans-serif-light";
    return "sans";
  }

  private int keyColor(KeySpec key, boolean bottomRow) {
    if (key.code == KeySpec.DELETE) return theme.deleteKey;
    if (key.code == KeySpec.SPACE) return theme.spaceKey;
    if (key.code == KeySpec.SHIFT) return theme.shiftKey;
    if (bottomRow) return theme.bottomKey;
    return theme.key;
  }

  /**
   * Round 41: decodes the active theme's key skin synchronously (tiny PNGs). The skin is
   * used only when the "up" part resolves; otherwise keys fall back to programmatic faces.
   */
  private void prepareKeySkin() {
    String skin = theme.keySkin;
    if (skin.length() == 0 || skin.equals(keySkinLoaded)) return;
    keySkinBitmaps.clear();
    keySkinLoaded = skin;
    // Round 43: imported assets/theme/ skins resolve through the library (key
    // icon normalized to the key face, space icon or the key fallback, darkened
    // press twin). Same "up part must resolve" contract as drawable skins.
    if (skin.startsWith(AssetThemeLibrary.URI_PREFIX)) {
      String[] parts = {"up", "fun", "space", "press", "enter"};
      for (int i = 0; i < parts.length; i++) {
        keySkinBitmaps.put(
            parts[i], AssetThemeLibrary.keySkinPart(getContext(), skin, parts[i]));
      }
      if (keySkinBitmaps.get("up") == null) {
        keySkinBitmaps.clear();
        keySkinLoaded = "";
      }
      return;
    }
    String[] parts = {"up", "fun", "space", "press", "enter"};
    for (int i = 0; i < parts.length; i++) {
      int id =
          getResources()
              .getIdentifier(
                  "keybg_" + skin + "_" + parts[i], "drawable", getContext().getPackageName());
      keySkinBitmaps.put(
          parts[i], id == 0 ? null : BitmapFactory.decodeResource(getResources(), id));
    }
    if (keySkinBitmaps.get("up") == null) {
      keySkinBitmaps.clear();
      keySkinLoaded = "";
    }
  }

  /**
   * Round 41-2: wide keys (the space bar) draw their skin as a horizontal three-slice, so
   * the edge art keeps the letter keys' scale — only the flat middle stretches. The space
   * bar matches the rest of the keys' shape at any row width.
   *
   * Round 42: source art that is already very wide (the golden ruler bar, ratio ≥ 3)
   * draws DIRECTLY — its own proportions carry the bevels at any bar width. Narrower
   * rulers keep the three-slice with an adaptive edge of up to 48 source pixels; the
   * family rulers are flat past column 14, so the wider slice stays pixel-identical.
   */
  private void drawWideSkinFace(Canvas canvas, Bitmap skin, RectF face, Paint paint) {
    if (skin.getWidth() >= skin.getHeight() * 3f) {
      canvas.drawBitmap(skin, null, face, paint);
      return;
    }
    int src = Math.min(48, skin.getWidth() / 3);
    float scale = face.height() / skin.getHeight();
    float edge = src * scale;
    if (face.width() < edge * 2.4f) {
      canvas.drawBitmap(skin, null, face, paint);
      return;
    }
    Rect leftSrc = new Rect(0, 0, src, skin.getHeight());
    Rect middleSrc = new Rect(src, 0, skin.getWidth() - src, skin.getHeight());
    Rect rightSrc = new Rect(skin.getWidth() - src, 0, skin.getWidth(), skin.getHeight());
    canvas.drawBitmap(
        skin, leftSrc, new RectF(face.left, face.top, face.left + edge, face.bottom), paint);
    canvas.drawBitmap(
        skin, middleSrc, new RectF(face.left + edge, face.top, face.right - edge, face.bottom),
        paint);
    canvas.drawBitmap(
        skin, rightSrc, new RectF(face.right - edge, face.top, face.right, face.bottom), paint);
  }

  /** Round 41: picks the skin part for a key — space / enter / function rows have own art. */
  private Bitmap keySkinFace(KeySpec key, boolean bottomRow) {
    if (keySkinLoaded.length() == 0) return null;
    if (key.code == KeySpec.SPACE) return keySkinBitmaps.get("space");
    if (key.code == KeySpec.ENTER) return keySkinBitmaps.get("enter");
    if (key.code == KeySpec.SHIFT || key.code == KeySpec.DELETE || bottomRow)
      return keySkinBitmaps.get("fun");
    return keySkinBitmaps.get("up");
  }

  /**
   * Round 50: the professional frame's art for the CURRENT theme - a real image
   * (keyframe_ID_R.png following the selected key shape) decoded once per art
   * name. Null when no frame is selected or the art cannot resolve, so keys
   * fall back to the classic programmatic face.
   */
  private Bitmap frameArt() {
    if (theme.keyFrame <= 0) return null;
    String name = KeyFrames.artName(theme.keyFrame, theme.keyRadiusDp);
    if (name.length() == 0) return null;
    Bitmap cached = frameArtCache.get(name);
    if (cached != null) return cached;
    int id = getResources().getIdentifier(name, "drawable", getContext().getPackageName());
    if (id == 0) return null;
    cached = BitmapFactory.decodeResource(getResources(), id);
    if (cached != null) frameArtCache.put(name, cached);
    return cached;
  }

  /**
   * Round 51: the internet-downloaded frame's art (a file inside the app's
   * private storage, saved by OnlineAssetStore). Null when no path is set or
   * the file cannot decode, so keys fall back to the built-in frames.
   */
  private Bitmap onlineFrameArt() {
    if (theme.keyFrameUri.length() == 0) return null;
    Bitmap cached = onlineFrameCache.get(theme.keyFrameUri);
    if (cached != null) return cached;
    try {
      // Round 62: نفس خط بيانات الممتلكات — زر حقيقي بمعالجة KeyArtProcessor
      cached = KeyArtProcessor.fileKeyArt(theme.keyFrameUri);
    } catch (Exception error) {
      cached = null;
    }
    if (cached == null) return null;
    onlineFrameCache.put(theme.keyFrameUri, cached);
    onlineFrameTextColor = frameTextColour(cached);
    return cached;
  }

  /** Round 72: فن مستقل لزر المسافة؛ يعود للزر العادي إن لم يتوفر. */
  private Bitmap onlineSpaceFrameArt() {
    String path = theme.keySpaceFrameUri;
    if (path == null || path.length() == 0) {
      Bitmap fallback = onlineFrameArt();
      onlineSpaceFrameTextColor = onlineFrameTextColor;
      return fallback;
    }
    Bitmap cached = onlineSpaceFrameCache.get(path);
    if (cached != null) return cached;
    try {
      cached = KeyArtProcessor.fileSpaceArt(path);
    } catch (Exception error) {
      cached = null;
    }
    if (cached == null) {
      Bitmap fallback = onlineFrameArt();
      onlineSpaceFrameTextColor = onlineFrameTextColor;
      return fallback;
    }
    onlineSpaceFrameCache.put(path, cached);
    onlineSpaceFrameTextColor = frameTextColour(cached);
    return cached;
  }

  private Bitmap onlineFrameArtForKey(KeySpec key) {
    return key.code == KeySpec.SPACE ? onlineSpaceFrameArt() : onlineFrameArt();
  }

  /** Round 51: letter color that reads on top of the downloaded frame art. */
  private int frameTextColour(Bitmap art) {
    // Round 69: العيّنة انتقلت إلى KeyArtProcessor.frameTextColor لتشاركها
    // شارات الاقتراحات والاستوديو — لون الخط واحد في كل الواجهات.
    return KeyArtProcessor.frameTextColor(art);
  }

  /**
   * Round 50: wide keys (the space bar) draw the frame art as a horizontal
   * three-slice - the caps keep the frame border's true scale and only the flat
   * middle stretches, exactly like the key skins' wide faces.
   */
  private void drawFrameSlices(Canvas canvas, Bitmap art, RectF face, Paint paint) {
    int cap = art.getWidth() / 4;
    float edge = Math.min(cap * (face.height() / (float) art.getHeight()), face.width() * .3f);
    canvas.drawBitmap(
        art, new Rect(0, 0, cap, art.getHeight()),
        new RectF(face.left, face.top, face.left + edge, face.bottom), paint);
    canvas.drawBitmap(
        art, new Rect(cap, 0, art.getWidth() - cap, art.getHeight()),
        new RectF(face.left + edge, face.top, face.right - edge, face.bottom), paint);
    canvas.drawBitmap(
        art, new Rect(art.getWidth() - cap, 0, art.getWidth(), art.getHeight()),
        new RectF(face.right - edge, face.top, face.right, face.bottom), paint);
  }

  /**
   * Round 69: خانة وجه الزر بعد مقياسي العرض/الارتفاع (شريطا قسم الخط) —
   * متمركزة داخل حدود المفتاح. الجلود المصورة تبقى بملء الخانة لأنها صُممت
   * لتغطيتها كاملة.
   */
  private RectF keyFaceRect(RectF bounds) {
    float w = bounds.width() * theme.keyWidthScale;
    float h = bounds.height() * theme.keyHeightScale;
    if (w >= bounds.width() - .5f && h >= bounds.height() - .5f) return bounds;
    return new RectF(
        bounds.centerX() - w / 2f,
        bounds.centerY() - h / 2f,
        bounds.centerX() + w / 2f,
        bounds.centerY() + h / 2f);
  }

  /**
   * Round 69: مستطيل الفن بنسبته الأصلية داخل صندوق — تصغير/تكبير متمركز بلا
   * أي مطّ، ف يحافظ شكل الزر المرسوم على شكل أيقونته الحرفي.
   */
  private RectF fitCenterRect(Bitmap art, RectF box) {
    float scale = Math.min(box.width() / art.getWidth(), box.height() / art.getHeight());
    float w = art.getWidth() * scale;
    float h = art.getHeight() * scale;
    return new RectF(
        box.centerX() - w / 2f, box.centerY() - h / 2f,
        box.centerX() + w / 2f, box.centerY() + h / 2f);
  }

  private void drawKey(
      Canvas canvas, KeySpec key, RectF bounds, boolean active, boolean bottomRow) {
    int normalColor = keyColor(key, bottomRow);
    boolean differentiated = normalColor != theme.key;
    int contentColor = differentiated ? theme.contentColorFor(normalColor) : theme.text;
    int faceColor =
        active
            ? (differentiated ? blendColor(normalColor, Color.BLACK, .16f) : theme.keyPressed)
            : normalColor;
    int depthColor = blendColor(normalColor, Color.BLACK, themeAnimated ? .42f : .30f);
    int highlightColor = blendColor(faceColor, Color.WHITE, themeAnimated ? .52f : .38f);
    int borderColor = active ? blendColor(theme.accent, Color.BLACK, .16f) : theme.accent;
    float depth = dp(themeAnimated ? 3.0f : 2.5f);
    float pressedOffset = active ? depth * .72f : 0f;
    float radius = dp(theme.keyRadiusDp);
    float fillOpacity = active ? Math.min(1f, theme.keyOpacity + .20f) : theme.keyOpacity;

    Bitmap skinFace = keySkinFace(key, bottomRow);
    // Round 50: الإطار الاحترافي صورة جاهزة — يعمل فقط بلا جلد (جلد الثيمات
    // المصورة أولوية)، وحروفه بلون الإطار نفسه لأن الصورة هي وجه الزر.
    Bitmap frameBitmap = skinFace == null ? frameArt() : null;
    // Round 51/72: إطار محمَّل من الإنترنت — ومعه زر مسافة مستقل إن توفر.
    Bitmap onlineFrame = frameBitmap == null ? onlineFrameArtForKey(key) : null;
    // Round 72: لا نفرض لوناً محسوباً من صورة الزر هنا؛ لون الحروف يأتي من
    // theme.text حتى تعمل أشرطة تغيير لون الخط، وثيمات JSON تطبق text كما هي.
    // عند اختيار زر من المتجر في الاستوديو نملأ theme.text تلقائياً بلون مناسب،
    // لكن المستخدم يبقى قادراً على تغييره لاحقاً.
    RectF face;
    if (skinFace != null) {
      // Round 41: the skin drawable IS the key face (subtle curved SwiftKey shape) — no
      // depth rect, border stroke or shadow layer, so image keys stay visually light.
      face = new RectF(bounds);
      paint.setStyle(Paint.Style.FILL);
      paint.setFilterBitmap(true);
      paint.clearShadowLayer();
      paint.setAlpha(255);
      if (key.code == KeySpec.SPACE) {
        drawWideSkinFace(canvas, skinFace, face, paint);
      } else {
        canvas.drawBitmap(skinFace, null, face, paint);
      }
      if (active) {
        Bitmap pressFace = keySkinBitmaps.get("press");
        if (pressFace != null) {
          // Round 42: the space bar's press flash keeps the wide-slice shape instead of
          // stretching a key-sized press art across the whole bar.
          if (key.code == KeySpec.SPACE) {
            drawWideSkinFace(canvas, pressFace, face, paint);
          } else {
            canvas.drawBitmap(pressFace, null, face, paint);
          }
        }
      }
    } else if (frameBitmap != null) {
      // Round 50: وجه الزر = صورة الإطار المختار، والمفاتيح العريضة (المسطرة)
      // تُرسم بتقطيع ثلاثي كي لا يتمدد حد الإطار.
      // Round 69: خانة الوجه تخضع لمقياسي عرض/ارتفاع الزر.
      face = keyFaceRect(bounds);
      paint.setStyle(Paint.Style.FILL);
      paint.setFilterBitmap(true);
      paint.setAlpha(Math.round(255f * fillOpacity));
      if (face.width() > face.height() * 2.05f) {
        drawFrameSlices(canvas, frameBitmap, face, paint);
      } else {
        canvas.drawBitmap(frameBitmap, null, face, paint);
      }
      if (active) {
        // الضغط: نفس الإطار بغلالة داكنة وإزاحة خفيفة لأسفل (الحواف تبقى مقصوصة)
        paint.setColorFilter(new PorterDuffColorFilter(0x42000000, PorterDuff.Mode.SRC_ATOP));
        RectF pressedFace =
            new RectF(face.left, face.top + dp(1.2f), face.right, face.bottom + dp(.6f));
        canvas.drawBitmap(frameBitmap, null, pressedFace, paint);
        paint.setColorFilter(null);
      }
      paint.setAlpha(255);
    } else if (onlineFrame != null) {
      // Round 72: أزرار ثيماتي تُمدَّد لتملأ خانة المفتاح مثل الثيمات
      // الافتراضية تماماً؛ وزر المسافة يأخذ صورته الخاصة من buttons-space.
      face = keyFaceRect(bounds);
      paint.setStyle(Paint.Style.FILL);
      paint.setFilterBitmap(true);
      paint.setAlpha(Math.round(255f * fillOpacity));
      canvas.drawBitmap(onlineFrame, null, face, paint);
      if (active) {
        // الضغط: الزر نفسه بغلالة داكنة وإزاحة خفيفة لأسفل — كالإطار المدمج
        paint.setColorFilter(new PorterDuffColorFilter(0x42000000, PorterDuff.Mode.SRC_ATOP));
        RectF pressedArt =
            new RectF(face.left, face.top + dp(1.2f), face.right, face.bottom + dp(.6f));
        canvas.drawBitmap(onlineFrame, null, pressedArt, paint);
        paint.setColorFilter(null);
      }
      paint.setAlpha(255);
    } else {
      // Round 69: الوجه الكلاسيكي يخضع أيضاً لمقياسي عرض/ارتفاع الزر.
      RectF cell = keyFaceRect(bounds);
      RectF depthRect = new RectF(cell.left, cell.top + depth, cell.right, cell.bottom);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(depthColor);
      paint.setAlpha(Math.round(255f * Math.min(1f, fillOpacity + .18f)));
      canvas.drawRoundRect(depthRect, radius, radius, paint);

      face =
          new RectF(
              cell.left,
              cell.top + pressedOffset,
              cell.right,
              cell.bottom - depth + pressedOffset);
      paint.setColor(faceColor);
      paint.setAlpha(Math.round(255f * fillOpacity));
      if (!active) {
        paint.setShadowLayer(dp(1.4f), 0, dp(1.0f), 0x4D000000);
      }
      canvas.drawRoundRect(face, radius, radius, paint);
      paint.clearShadowLayer();

      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(active ? 1.2f : 0.8f));
      paint.setColor(borderColor);
      paint.setAlpha(active ? 200 : (themeAnimated ? 150 : 95));
      canvas.drawRoundRect(face, radius, radius, paint);

      if (active) {
        // The inset highlight/shade pair reads as a second frame at rest — only a press wants it.
        float inset = Math.min(dp(theme.keyRadiusDp * .75f + 2f), face.width() * .22f);
        paint.setStrokeWidth(dp(.7f));
        paint.setColor(highlightColor);
        paint.setAlpha(75);
        canvas.drawLine(
            face.left + inset, face.top + dp(1.4f), face.right - inset, face.top + dp(1.4f), paint);
        paint.setColor(depthColor);
        paint.setAlpha(145);
        canvas.drawLine(
            face.left + inset, face.bottom - dp(.8f), face.right - inset, face.bottom - dp(.8f),
            paint);
      }

      if (themeAnimated) {
        drawAnimatedKeyAccent(canvas, face, key, active, highlightColor);
      }
    }

    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(255);
    if (key.code == KeySpec.SPACE && localeVisible) {
      drawSpaceGesture(canvas, face);
      return;
    }

    Drawable icon = iconFor(key);
    if (icon != null && key.code != KeySpec.MIC) {
      // Round 41-2: MIC keys draw their letter like any glyph (73% baseline); the mic icon
      // rides the alternative-letter slot at the top instead of hijacking the face center.
      int size = (int) dp(key.code == KeySpec.DELETE ? 30 : 27);
      int left = (int) (face.centerX() - size / 2);
      int top = (int) (face.centerY() - size / 2);
      icon.setBounds(left, top, left + size, top + size);
      icon.setColorFilter(contentColor, PorterDuff.Mode.SRC_IN);
      icon.draw(canvas);
    } else {
      paint.setTypeface(
          Typeface.create(
              keyFontFamily(),
              keyTextWeight == 3 || themeAnimated ? Typeface.BOLD : Typeface.NORMAL));
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setColor(contentColor);
      float requestedMainSize =
          dp(
              key.code == KeySpec.SPACE
                  ? Math.min(19f, theme.mainTextSizeDp)
                  : theme.mainTextSizeDp);
      paint.setTextSize(
          fittedTextSize(
              key.label,
              requestedMainSize,
              face,
              key.code == KeySpec.SPACE ? dp(14f) : dp(5f),
              key.code == KeySpec.SPACE ? .52f : .62f));
      // Round 45: النص يتمركز داخل وجه الزر نفسه لا داخل الخانة الكاملة،
      // ثم يُقلم baseline حتى لا يخرج 123 أو رموز صفحات السيمبل خارج الإطار.
      // مرجع قديم: bounds.top + pressedOffset + bounds.height() * 0.73f
      float defaultLabelFactor = onlineFrame != null && key.code != KeySpec.SPACE ? 0.80f : 0.73f;
      float labelFactor =
          theme.mainTextPosition >= 0f ? theme.mainTextPosition : defaultLabelFactor;
      Paint.FontMetrics mainMetrics = paint.getFontMetrics();
      float labelBaseline =
          baselineInside(face, mainMetrics, face.top + face.height() * labelFactor, dp(2.5f));
      canvas.drawText(key.label, face.centerX(), labelBaseline, paint);
    }

    if (!key.subLabel.isEmpty()) {
      // Round 45: الحروف الصغيرة لها موضع مستقل + تصغير تلقائي حتى لا تخرج
      // عن حدود الزر عند الأزرار النحيفة أو الرموز الطويلة.
      paint.setColor(differentiated ? blendColor(contentColor, normalColor, .38f) : theme.sub);
      paint.setTextAlign(Paint.Align.CENTER);
      float requestedSubSize = dp(Math.max(9f, theme.subTextSizeDp));
      paint.setTextSize(fittedTextSize(key.subLabel, requestedSubSize, face, dp(5f), .32f));
      Paint.FontMetrics subMetrics = paint.getFontMetrics();
      float defaultSubFactor =
          onlineFrame != null
              ? Math.min(.18f, dp(7f) / Math.max(1f, face.height()))
              : Math.min(.12f, dp(1.5f) / Math.max(1f, face.height()));
      float subFactor = theme.subTextPosition >= 0f ? theme.subTextPosition : defaultSubFactor;
      float subBaseline =
          baselineInside(
              face,
              subMetrics,
              face.top + face.height() * subFactor - subMetrics.ascent,
              dp(1.5f));
      canvas.drawText(key.subLabel, face.centerX(), subBaseline, paint);
    }
    if (key.code == KeySpec.MIC) {
      // Round 58: أيقونة الحافظة في مكان الحرف البديل فوق الفاصلة (كانت ميكرفوناً).
      Drawable micIcon = iconFor(key);
      if (micIcon != null) {
        int micSize = (int) dp(15);
        int micLeft = (int) (face.centerX() - micSize / 2f);
        int micTop = (int) (face.top + dp(2.5f));
        micIcon.setBounds(micLeft, micTop, micLeft + micSize, micTop + micSize);
        micIcon.setColorFilter(contentColor, PorterDuff.Mode.SRC_IN);
        micIcon.draw(canvas);
      }
    }
    if (key.code == KeySpec.SPACE) {
      paint.setColor(theme.accent);
      canvas.drawRoundRect(
          new RectF(
              face.left + face.width() * .28f,
              face.bottom - dp(6),
              face.right - face.width() * .28f,
              face.bottom - dp(3)),
          dp(2),
          dp(2),
          paint);
    }
  }

  private void drawAnimatedKeyAccent(
      Canvas canvas, RectF face, KeySpec key, boolean active, int highlightColor) {
    long now = SystemClock.uptimeMillis();
    int style = theme.animationStyle;
    int seed = (key.code * 37) & 0x7fffffff;
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(highlightColor);
    if (style == KeyboardTheme.ANIMATION_SPEED) {
      float progress = ((now + seed) % 1250L) / 1250f;
      float y = face.top + progress * face.height();
      paint.setStrokeWidth(dp(1.4f));
      paint.setAlpha(active ? 70 : 120);
      canvas.drawLine(face.left + dp(4), y, face.right - dp(4), y, paint);
    } else if (style == KeyboardTheme.ANIMATION_NEON) {
      float pulse = .5f + .5f * (float) Math.sin(now / 330.0 + seed * .01);
      paint.setStrokeWidth(dp(1f + pulse));
      paint.setAlpha((int) (65 + pulse * 105));
      canvas.drawRoundRect(face, dp(theme.keyRadiusDp), dp(theme.keyRadiusDp), paint);
    } else if (style == KeyboardTheme.ANIMATION_FIREFLIES) {
      float pulse = Math.abs((float) Math.sin(now / 510.0 + seed * .013));
      paint.setStyle(Paint.Style.FILL);
      paint.setAlpha((int) (35 + pulse * 145));
      canvas.drawCircle(
          face.left + face.width() * (.18f + (seed % 55) / 100f),
          face.top + dp(4 + seed % 7),
          dp(.8f + pulse * 1.2f),
          paint);
    } else if (style == KeyboardTheme.ANIMATION_FIREWORKS) {
      float pulse = Math.abs((float) Math.sin(now / 390.0 + seed * .019));
      paint.setStyle(Paint.Style.FILL);
      paint.setAlpha((int) (30 + pulse * 130));
      float radius = dp(.8f + pulse);
      canvas.drawCircle(face.left + dp(5), face.top + dp(5), radius, paint);
      canvas.drawCircle(face.right - dp(5), face.bottom - dp(5), radius, paint);
    } else if (style == KeyboardTheme.ANIMATION_ROYAL) {
      float progress = ((now + seed * 3L) % 2400L) / 2400f;
      float x = face.left + progress * face.width();
      float corner = dp(5);
      paint.setStrokeWidth(dp(1.25f));
      paint.setAlpha(active ? 125 : 205);
      canvas.drawLine(
          face.left + dp(2), face.top + corner, face.left + dp(2), face.top + dp(2), paint);
      canvas.drawLine(
          face.left + dp(2), face.top + dp(2), face.left + corner, face.top + dp(2), paint);
      canvas.drawLine(
          face.right - dp(2), face.bottom - corner, face.right - dp(2), face.bottom - dp(2), paint);
      canvas.drawLine(
          face.right - dp(2), face.bottom - dp(2), face.right - corner, face.bottom - dp(2), paint);
      paint.setStrokeWidth(dp(1.7f));
      paint.setAlpha(active ? 55 : 105);
      canvas.drawLine(x, face.top + dp(5), x, face.bottom - dp(5), paint);
    } else if (style == KeyboardTheme.ANIMATION_LANTERN
        || style == KeyboardTheme.ANIMATION_SPARKLES) {
      float progress = ((now + seed * 3L) % 2200L) / 2200f;
      float x = face.left + progress * face.width();
      paint.setStrokeWidth(dp(1.2f));
      paint.setAlpha(active ? 65 : 115);
      canvas.drawLine(x, face.top + dp(4), x, face.bottom - dp(4), paint);
    } else if (style == KeyboardTheme.ANIMATION_PULSE) {
      float pulse = .5f + .5f * (float) Math.sin(now / 410.0 + seed * .008);
      paint.setStrokeWidth(dp(1f + pulse * .8f));
      paint.setAlpha((int) (50 + pulse * 100));
      canvas.drawRoundRect(face, dp(theme.keyRadiusDp), dp(theme.keyRadiusDp), paint);
    } else if (style >= KeyboardTheme.ANIMATION_STARS) {
      float pulse = Math.abs((float) Math.sin(now / 430.0 + seed * .011));
      float x = face.left + dp(4.5f);
      float y = face.top + dp(4.5f);
      float size = dp(1.4f + pulse * 1.7f);
      paint.setAlpha((int) (45 + pulse * 135));
      if (style == KeyboardTheme.ANIMATION_HEARTS) {
        paint.setStyle(Paint.Style.FILL);
        drawHeart(canvas, x + size, y + size, size, paint);
      } else if (style == KeyboardTheme.ANIMATION_PLANETS
          || style == KeyboardTheme.ANIMATION_BUBBLES) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x + size, y + size, size, paint);
      } else if (style == KeyboardTheme.ANIMATION_PETALS) {
        paint.setStyle(Paint.Style.FILL);
        canvas.drawOval(new RectF(x, y + size * .5f, x + size * 2.2f, y + size * 1.5f), paint);
      } else if (style == KeyboardTheme.ANIMATION_MUSIC) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y + size * 1.5f, size * .55f, paint);
        canvas.drawLine(x + size * .5f, y + size * 1.5f, x + size * .5f, y - size, paint);
      } else {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(x - size, y, x + size, y, paint);
        canvas.drawLine(x, y - size, x, y + size, paint);
      }
    }
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(255);
  }

  private void drawHeart(Canvas canvas, float x, float y, float size, Paint value) {
    Path heart = new Path();
    heart.moveTo(x, y + size * .8f);
    heart.cubicTo(
        x - size * 1.7f, y - size * .2f, x - size * .8f, y - size * 1.35f, x, y - size * .55f);
    heart.cubicTo(
        x + size * .8f, y - size * 1.35f, x + size * 1.7f, y - size * .2f, x, y + size * .8f);
    heart.close();
    canvas.drawPath(heart, value);
  }

  private static int blendColor(int first, int second, float amount) {
    float keep = 1f - amount;
    return Color.rgb(
        Math.round(Color.red(first) * keep + Color.red(second) * amount),
        Math.round(Color.green(first) * keep + Color.green(second) * amount),
        Math.round(Color.blue(first) * keep + Color.blue(second) * amount));
  }

  private void drawSpaceGesture(Canvas canvas, RectF rect) {
    canvas.save();
    canvas.clipRect(rect);
    paint.setTypeface(Typeface.create("sans", Typeface.BOLD));
    paint.setTextAlign(Paint.Align.CENTER);
    // Round 47: اسم اللغة على المسافة أكبر (كان 18dp).
    paint.setTextSize(Math.min(dp(22), rect.height() * .5f));
    Paint.FontMetrics fm = paint.getFontMetrics();
    float baseline = rect.centerY() - (fm.ascent + fm.descent) / 2;
    if (spaceGestureAxis == 1) {
      float diff = Math.max(-rect.width(), Math.min(rect.width(), spaceDiffX));
      String current = layout.rtl ? "العربية" : "English (US)";
      String adjacent = layout.rtl ? "English (US)" : "العربية";
      paint.setColor(theme.contentColorFor(theme.spaceKey));
      canvas.drawText(current, rect.centerX() + diff, baseline, paint);
      canvas.drawText(adjacent, rect.centerX() + diff - rect.width(), baseline, paint);
      canvas.drawText(adjacent, rect.centerX() + diff + rect.width(), baseline, paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(1.8f));
      paint.setColor(theme.accent);
      float cy = rect.centerY(), edge = dp(11);
      canvas.drawLine(rect.left + edge, cy, rect.left + edge + dp(6), cy - dp(5), paint);
      canvas.drawLine(rect.left + edge, cy, rect.left + edge + dp(6), cy + dp(5), paint);
      canvas.drawLine(rect.right - edge, cy, rect.right - edge - dp(6), cy - dp(5), paint);
      canvas.drawLine(rect.right - edge, cy, rect.right - edge - dp(6), cy + dp(5), paint);
      paint.setStyle(Paint.Style.FILL);
    } else {
      boolean show = spaceDiffY < 0;
      String label = show ? "إظهار صف الأرقام" : "إخفاء صف الأرقام";
      paint.setColor(theme.contentColorFor(theme.spaceKey));
      canvas.drawText(
          label,
          rect.centerX(),
          baseline
              + Math.max(-rect.height() * .18f, Math.min(rect.height() * .18f, spaceDiffY * .16f)),
          paint);
      paint.setColor(theme.accent);
      paint.setTextSize(Math.min(dp(15), rect.height() * .32f));
      canvas.drawText(
          show ? "١ ٢ ٣  •  ↑" : "↓  •  ١ ٢ ٣", rect.centerX(), rect.bottom - dp(5), paint);
    }
    canvas.restore();
  }

  private void drawAlternativeOverlay(Canvas canvas) {
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(theme.key);
    paint.setShadowLayer(dp(5), 0, dp(3), 0x66000000);
    canvas.drawRoundRect(altRect, dp(12), dp(12), paint);
    paint.clearShadowLayer();
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(1));
    paint.setColor(theme.sub);
    paint.setAlpha(90);
    canvas.drawRoundRect(altRect, dp(12), dp(12), paint);
    paint.setAlpha(255);
    paint.setStyle(Paint.Style.FILL);
    float cell = altRect.width() / Math.max(1, altValues.length);
    for (int i = 0; i < altValues.length; i++) {
      float left = altRect.left + i * cell;
      if (i == altSelected) {
        paint.setColor(theme.accent);
        canvas.drawRoundRect(
            new RectF(
                left + dp(3), altRect.top + dp(4), left + cell - dp(3), altRect.bottom - dp(4)),
            dp(9),
            dp(9),
            paint);
      }
      paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTextSize(dp(22));
      paint.setColor(i == altSelected ? Color.WHITE : theme.text);
      Paint.FontMetrics fm = paint.getFontMetrics();
      canvas.drawText(
          altValues[i], left + cell / 2, altRect.centerY() - (fm.ascent + fm.descent) / 2, paint);
    }
  }

  private Drawable iconFor(KeySpec key) {
    int code = key.code;
    int id =
        code == KeySpec.SHIFT && key.label.length() == 0
            ? R.drawable.ic_shift
            : code == KeySpec.DELETE
                ? R.drawable.ic_backspace
                : code == KeySpec.ENTER
                    ? enterIconRes
                    : code == KeySpec.EMOJI
                        ? R.drawable.ic_emoji
                        : code == KeySpec.MIC
                            ? R.drawable.ic_clipboard // Round 58: زر الفاصلة يمثل الحافظة
                            : code == KeySpec.TAB && key.label.length() == 0
                                ? R.drawable.ic_tab
                                : 0;
    return id == 0 ? null : getResources().getDrawable(id);
  }

  private boolean gestureTypingEnabled() {
    return gestureInputAllowed
        && getContext()
            .getSharedPreferences("keyboard", Context.MODE_PRIVATE)
            .getBoolean("gesture_typing", true);
  }

  private boolean gestureTrailEnabled() {
    return getContext()
        .getSharedPreferences("keyboard", Context.MODE_PRIVATE)
        .getBoolean("gesture_trail", true);
  }

  private int gestureStartDistance() {
    int value =
        getContext()
            .getSharedPreferences("keyboard", Context.MODE_PRIVATE)
            .getInt("gesture_start_distance", 12);
    return Math.max(6, Math.min(24, value));
  }

  private int gestureTrailWidth() {
    int value =
        getContext()
            .getSharedPreferences("keyboard", Context.MODE_PRIVATE)
            .getInt("gesture_trail_width", 6);
    return Math.max(3, Math.min(11, value));
  }

  private boolean isGestureLetter(Hit hit) {
    if (hit == null || hit.key == null || hit.key.code < 0 || hit.key.label == null) return false;
    String label = hit.key.label;
    return label.codePointCount(0, label.length()) == 1 && Character.isLetter(label.codePointAt(0));
  }

  private Hit nearestGestureLetter(float x, float y) {
    Hit contained = find(x, y);
    if (isGestureLetter(contained)) return contained;
    Hit nearest = null;
    float best = Float.MAX_VALUE;
    for (Hit hit : hits) {
      if (!isGestureLetter(hit)) continue;
      float dx = x - hit.rect.centerX();
      float dy = y - hit.rect.centerY();
      float distance = dx * dx + dy * dy;
      float radius = Math.min(hit.rect.width(), hit.rect.height()) * .52f;
      if (distance <= radius * radius && distance < best) {
        best = distance;
        nearest = hit;
      }
    }
    return nearest;
  }

  private void addGestureKey(float x, float y) {
    Hit hit = nearestGestureLetter(x, y);
    if (hit == null) {
      pendingGestureKey = "";
      pendingGestureSamples = 0;
      return;
    }
    String label = hit.key.label;
    if (gestureVisitedKeys.contains(label)) {
      pendingGestureKey = "";
      pendingGestureSamples = 0;
      return;
    }
    if (gestureKeys.length() == 0) {
      gestureKeys.append(label);
      gestureVisitedKeys.add(label);
      pendingGestureKey = "";
      pendingGestureSamples = 0;
      return;
    }
    if (label.equals(pendingGestureKey)) {
      pendingGestureSamples++;
    } else {
      pendingGestureKey = label;
      pendingGestureSamples = 1;
    }
    if (pendingGestureSamples >= 2) {
      gestureKeys.append(label);
      gestureVisitedKeys.add(label);
      pendingGestureKey = "";
      pendingGestureSamples = 0;
    }
  }

  private void addGesturePoint(float x, float y) {
    if (gesturePoints.size() > 0) {
      PointF previous = gesturePoints.get(gesturePoints.size() - 1);
      float dx = x - previous.x;
      float dy = y - previous.y;
      float distance = (float) Math.sqrt(dx * dx + dy * dy);
      if (distance < dp(2f)) return;
      int samples = Math.max(1, (int) Math.ceil(distance / dp(5f)));
      for (int sample = 1; sample < samples; sample++) {
        float fraction = sample / (float) samples;
        addGestureKey(previous.x + dx * fraction, previous.y + dy * fraction);
      }
    }
    if (gesturePoints.size() >= 240) {
      for (int index = gesturePoints.size() - 2; index > 0; index -= 2) {
        gesturePoints.remove(index);
      }
    }
    gesturePoints.add(new PointF(x, y));
    addGestureKey(x, y);
  }

  private void clearGestureTrail() {
    handler.removeCallbacks(gestureTrailFade);
    gestureActive = false;
    gestureFading = false;
    gesturePoints.clear();
    gestureKeys.setLength(0);
    gestureVisitedKeys.clear();
    pendingGestureKey = "";
    pendingGestureSamples = 0;
    lastGesturePreviewTime = 0;
    lastGesturePreviewKeys = "";
    invalidate();
  }

  private GestureTrace createGestureTrace() {
    int count = gesturePoints.size();
    float[] pathX = new float[count];
    float[] pathY = new float[count];
    float width = Math.max(1, getWidth());
    float height = Math.max(1, getHeight());
    for (int index = 0; index < count; index++) {
      PointF point = gesturePoints.get(index);
      pathX[index] = point.x / width;
      pathY[index] = point.y / height;
    }
    HashMap<String, float[]> centers = new HashMap<String, float[]>();
    for (Hit hit : hits) {
      if (isGestureLetter(hit)) {
        centers.put(
            hit.key.label, new float[] {hit.rect.centerX() / width, hit.rect.centerY() / height});
      }
    }
    return new GestureTrace(pathX, pathY, gestureKeys.toString(), centers);
  }

  private void dispatchGesturePreview() {
    if (listener == null || gesturePoints.size() < 2) return;
    String keys = gestureKeys.toString();
    if (keys.codePointCount(0, keys.length()) < 2) return;
    long now = SystemClock.uptimeMillis();
    long minimumDelay = keys.equals(lastGesturePreviewKeys) ? 110L : 45L;
    if (now - lastGesturePreviewTime < minimumDelay) return;
    lastGesturePreviewTime = now;
    lastGesturePreviewKeys = keys;
    listener.onGesturePreview(createGestureTrace());
  }

  private void finishGestureTrail() {
    gestureActive = false;
    if (gestureTrailEnabled() && gesturePoints.size() > 1) {
      gestureFading = true;
      gestureFadeStarted = SystemClock.uptimeMillis();
      handler.removeCallbacks(gestureTrailFade);
      handler.post(gestureTrailFade);
    } else {
      clearGestureTrail();
    }
  }

  private void drawGestureTrail(Canvas canvas) {
    if ((!gestureActive && !gestureFading) || !gestureTrailEnabled() || gesturePoints.size() < 2) {
      return;
    }
    int alpha = 255;
    if (gestureFading) {
      float progress = (SystemClock.uptimeMillis() - gestureFadeStarted) / 190f;
      alpha = Math.max(0, Math.min(255, (int) (255 * (1f - progress))));
    }
    Path path = new Path();
    PointF first = gesturePoints.get(0);
    path.moveTo(first.x, first.y);
    for (int index = 1; index < gesturePoints.size() - 1; index++) {
      PointF point = gesturePoints.get(index);
      PointF next = gesturePoints.get(index + 1);
      path.quadTo(point.x, point.y, (point.x + next.x) / 2f, (point.y + next.y) / 2f);
    }
    PointF last = gesturePoints.get(gesturePoints.size() - 1);
    path.lineTo(last.x, last.y);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND);
    paint.setStrokeJoin(Paint.Join.ROUND);
    paint.setColor(theme.contentColorFor(theme.background));
    paint.setAlpha(alpha * 45 / 255);
    paint.setStrokeWidth(dp(gestureTrailWidth() + 5));
    canvas.drawPath(path, paint);
    paint.setColor(theme.accent);
    paint.setAlpha(alpha);
    paint.setStrokeWidth(dp(gestureTrailWidth()));
    canvas.drawPath(path, paint);
    paint.setAlpha(255);
    paint.setStyle(Paint.Style.FILL);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int pointerIndex = touchPointerId < 0 ? 0 : event.findPointerIndex(touchPointerId);
    if (pointerIndex < 0) pointerIndex = 0;
    final float x = event.getX(pointerIndex);
    final float y = event.getY(pointerIndex);
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        {
          touchPointerId = event.getPointerId(0);
          if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
          }
          if (altVisible) {
            dismissAlt();
          }
          downX = x;
          downY = y;
          spaceDiffX = 0;
          spaceDiffY = 0;
          spaceGestureAxis = 0;
          pressed = find(x, y);
          repeatedDelete = false;
          specialLongPress = false; // Round 58: ضغطة جديدة تلغي علم الاستهلاك
          localeVisible = false;
          clearGestureTrail();
          if (gestureTypingEnabled() && isGestureLetter(pressed)) {
            addGesturePoint(x, y);
          }
          if (pressed != null) {
            handler.postDelayed(longPress, interactionTiming("popup_delay", 330, 220, 650));
            if (pressed.key.code == KeySpec.DELETE) {
              handler.postDelayed(
                  deleteRepeat, interactionTiming("delete_start_delay", 305, 220, 500));
            }
            showKeyPreview(pressed);
            spawnPressParticles(pressed.rect.centerX(), pressed.rect.centerY());
            invalidate();
          }
          return true;
        }
      case MotionEvent.ACTION_MOVE:
        {
          if (pressed == null) {
            return true;
          }
          if (altVisible) {
            updateAlternativeSelection(x);
            return true;
          }
          final float moveDx = x - downX;
          final float moveDy = y - downY;
          if (gestureTypingEnabled() && isGestureLetter(pressed)) {
            if (!gestureActive
                && moveDx * moveDx + moveDy * moveDy
                    >= dp(gestureStartDistance()) * dp(gestureStartDistance())) {
              gestureActive = true;
              handler.removeCallbacks(longPress);
              dismissKeyPreview();
            }
            if (gestureActive) {
              for (int index = 0; index < event.getHistorySize(); index++) {
                addGesturePoint(
                    event.getHistoricalX(pointerIndex, index),
                    event.getHistoricalY(pointerIndex, index));
              }
              addGesturePoint(x, y);
              dispatchGesturePreview();
              invalidate();
              return true;
            }
          }
          if (pressed.key.code == KeySpec.SPACE
              && (Math.abs(moveDx) > touchSlop || Math.abs(moveDy) > touchSlop)) {
            handler.removeCallbacks(longPress);
          } else if (pressed.key.code != KeySpec.SPACE
              && !pressed.rect.contains(x, y)
              && (Math.abs(moveDx) > touchSlop * 2 || Math.abs(moveDy) > touchSlop * 2)) {
            handler.removeCallbacks(longPress);
          }
          if (pressed.key.code == KeySpec.SPACE) {
            dismissKeyPreview();
            if (spaceGestureAxis == 0 && Math.max(Math.abs(moveDx), Math.abs(moveDy)) > touchSlop) {
              spaceGestureAxis = Math.abs(moveDx) >= Math.abs(moveDy) ? 1 : 2;
              localeVisible = true;
            }
            if (localeVisible) {
              spaceDiffX = spaceGestureAxis == 1 ? moveDx : 0;
              spaceDiffY = spaceGestureAxis == 2 ? moveDy : 0;
              invalidate();
            }
          }
          return true;
        }
      case MotionEvent.ACTION_POINTER_DOWN:
        return true;
      case MotionEvent.ACTION_POINTER_UP:
        {
          if (event.getPointerId(event.getActionIndex()) == touchPointerId) {
            boolean cancelledGesture = gestureActive;
            cancelHandlers();
            dismissPopups();
            pressed = null;
            touchPointerId = -1;
            clearGestureTrail();
            if (cancelledGesture && listener != null) listener.onGestureCancelled();
          }
          return true;
        }
      case MotionEvent.ACTION_CANCEL:
        {
          boolean cancelledGesture = gestureActive;
          cancelHandlers();
          dismissKeyPreview();
          dismissPopups();
          specialLongPress = false; // Round 58
          pressed = null;
          touchPointerId = -1;
          clearGestureTrail();
          if (cancelledGesture && listener != null) listener.onGestureCancelled();
          invalidate();
          return true;
        }
      case MotionEvent.ACTION_UP:
        {
          cancelHandlers();
          dismissKeyPreview();
          final Hit released = pressed;
          pressed = null;
          touchPointerId = -1;
          invalidate();
          if (released == null) {
            clearGestureTrail();
            return true;
          }
          if (specialLongPress) {
            // Round 58: الضغط المطوّل فتح الحافظة — لا تُدخل الفاصلة عند الرفع
            specialLongPress = false;
            performClick();
            return true;
          }
          if (gestureActive) {
            addGesturePoint(x, y);
            GestureTrace trace = createGestureTrace();
            finishGestureTrail();
            String crossedKeys = trace.crossedKeys();
            if (crossedKeys.codePointCount(0, crossedKeys.length()) >= 2 && listener != null) {
              listener.onGesture(trace);
            } else if (listener != null) {
              listener.onKey(released.key);
            }
            performClick();
            return true;
          }
          clearGestureTrail();
          if (altVisible) {
            if (altPersistent) {
              return true;
            }
            final String choice =
                altSelected >= 0 && altSelected < altValues.length ? altValues[altSelected] : null;
            dismissAlt();
            if (choice != null && listener != null) {
              listener.onAlternatives(released.key, choice);
            }
            return true;
          }
          if (released.key.code == KeySpec.SPACE && localeVisible) {
            final float releaseDx = x - downX;
            final float releaseDy = y - downY;
            final int axis = spaceGestureAxis;
            dismissLocale();
            final float threshold =
                Math.max(
                    touchSlop * 3,
                    axis == 1 ? released.rect.width() * .22f : released.rect.height() * .45f);
            if (axis == 1 && Math.abs(releaseDx) > threshold) {
              if (listener != null) {
                listener.onSpaceSwipe(releaseDx > 0 ? 1 : -1);
              }
            } else if (axis == 2 && Math.abs(releaseDy) > threshold) {
              if (listener != null) {
                listener.onSpaceVerticalSwipe(releaseDy < 0);
              }
            } else if (listener != null) {
              listener.onKey(released.key);
            }
            return true;
          }
          if (!repeatedDelete && released.rect.contains(x, y) && listener != null) {
            listener.onKey(released.key);
          }
          performClick();
          return true;
        }
      default:
        return true;
    }
  }

  public void releaseTransientState() {
    cancelHandlers();
    handler.removeCallbacks(themeMotion);
    dismissPopups();
    pressed = null;
    touchPointerId = -1;
    repeatedDelete = false;
    specialLongPress = false; // Round 58
    clearGestureTrail();
    invalidate();
  }

  public boolean performClick() {
    super.performClick();
    return true;
  }

  private Hit find(float x, float y) {
    for (Hit hit : hits) {
      if (hit.rect.contains(x, y)) return hit;
      if (y >= hit.rect.top && y <= hit.rect.bottom) {
        if ((hit.key.edgeFlags & 1) != 0 && x <= hit.rect.right) return hit;
        if ((hit.key.edgeFlags & 2) != 0 && x >= hit.rect.left) return hit;
      }
    }
    return null;
  }

  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (themeImage == null && theme.imageUri.length() > 0) {
      loadThemeImageAsync(theme.imageSource());
    }
  }

  protected void onWindowVisibilityChanged(int visibility) {
    super.onWindowVisibilityChanged(visibility);
    handler.removeCallbacks(themeMotion);
    if (visibility == VISIBLE && themeAnimated) {
      handler.post(themeMotion);
    }
  }

  protected void onDetachedFromWindow() {
    cancelHandlers();
    themeImageGeneration++;
    loadingThemeSource = "";
    loadedThemeSource = "";
    handler.removeCallbacks(themeMotion);
    dismissKeyPreview();
    dismissAlternativePopup();
    if (themeImage != null) {
      // Round 64: عرض الكيبورد يُفصل ويُعاد توصيله باستمرار — إعادة التدوير هنا
      // كانت تكسر الرسم عند التوصيل التالي؛ الإسقاط والجامع يكفيان.
      themeImage = null;
    }
    super.onDetachedFromWindow();
  }

  private void cancelHandlers() {
    handler.removeCallbacks(longPress);
    handler.removeCallbacks(deleteRepeat);
  }

  private boolean hasAlternatives(KeySpec key) {
    return alternativeValues(key).length > 1;
  }

  private String[] alternativeValues(KeySpec key) {
    LinkedHashSet<String> unique = new LinkedHashSet<String>();
    if (key.alternatives != null && key.alternatives.trim().length() > 0)
      unique.addAll(Arrays.asList(key.alternatives.trim().split("\\s+")));
    if (key.subLabel != null && key.subLabel.trim().length() > 0)
      unique.addAll(Arrays.asList(key.subLabel.trim().split("\\s+")));
    if (key.label != null && key.label.length() > 0) unique.remove(key.label);
    ArrayList<String> values = new ArrayList<String>(unique);
    if (key.label != null && key.label.length() > 0) values.add((values.size() + 1) / 2, key.label);
    return values.toArray(new String[values.size()]);
  }

  private void showAlternatives(Hit hit) {
    dismissKeyPreview();
    altValues = alternativeValues(hit.key);
    if (altValues.length < 2) return;
    alternativeKey = hit.key;
    altPersistent =
        getContext()
            .getSharedPreferences("keyboard", Context.MODE_PRIVATE)
            .getBoolean("alternative_popup_sticky", false);
    float height = Math.min(dp(58), Math.max(dp(46), hit.rect.height() + dp(8)));
    float width = Math.min(getWidth() - dp(8), Math.max(dp(54), dp(48) * altValues.length + dp(8)));
    float left =
        Math.max(dp(4), Math.min(getWidth() - width - dp(4), hit.rect.centerX() - width / 2));
    float top = hit.rect.top - height;
    altRect.set(left, top, left + width, top + height);
    altVisible = true;
    altSelected = -1;
    for (int i = 0; i < altValues.length; i++)
      if (altValues[i].equals(hit.key.label)) {
        altSelected = i;
        break;
      }
    if (altSelected < 0)
      altSelected =
          Math.max(
              0,
              Math.min(
                  altValues.length - 1,
                  (int) ((downX - left) / Math.max(1f, width / altValues.length))));
    showAlternativePopup(left, top, (int) width, (int) height);
    popupFeedback();
    invalidate();
  }

  private void showAlternativePopup(float localLeft, float localTop, int width, int height) {
    dismissAlternativePopup();
    alternativePopup = inflatePopup(R.layout.ime_alternative_popup);
    alternativePopup.configure(altValues, altSelected, theme);
    if (altPersistent) {
      alternativePopup.setChoiceListener(
          new AlternativePopupView.ChoiceListener() {
            public void onChoice(int index, String value) {
              if (listener != null && alternativeKey != null)
                listener.onAlternatives(alternativeKey, value);
              dismissAlt();
            }
          });
    }
    showAlternativeRootFallback(localLeft, localTop, width, height);
  }

  private void showAlternativeRootFallback(float localLeft, float localTop, int width, int height) {
    android.widget.FrameLayout frame = findOverlayRoot();
    if (frame == null) return;
    float[] offset = offsetInside(frame);
    int left = Math.round(offset[0] + localLeft);
    int top = Math.round(offset[1] + localTop);
    left = Math.max(0, Math.min(frame.getWidth() - width, left));
    top = Math.max(0, top);
    android.widget.FrameLayout.LayoutParams params =
        new android.widget.FrameLayout.LayoutParams(width, height);
    params.gravity = Gravity.LEFT | Gravity.TOP;
    params.leftMargin = left;
    params.topMargin = top;
    alternativePopup.setPointerX(
        offset[0]
            + (alternativeKey == null ? localLeft + width / 2f : keyCenterX(alternativeKey))
            - left);
    frame.addView(alternativePopup, params);
    alternativePopup.bringToFront();
  }

  private AlternativePopupView inflatePopup(int layoutResource) {
    try {
      View view = LayoutInflater.from(getContext()).inflate(layoutResource, null, false);
      if (view instanceof AlternativePopupView) return (AlternativePopupView) view;
    } catch (RuntimeException ignored) {
    }
    return new AlternativePopupView(getContext());
  }

  private android.widget.FrameLayout findOverlayRoot() {
    android.view.ViewParent parent = getParent();
    android.widget.FrameLayout nearest = null;
    while (parent instanceof View) {
      if (parent instanceof android.widget.FrameLayout)
        nearest = (android.widget.FrameLayout) parent;
      parent = parent.getParent();
    }
    View root = getRootView();
    return root instanceof android.widget.FrameLayout ? (android.widget.FrameLayout) root : nearest;
  }

  private float[] offsetInside(View ancestor) {
    float x = 0f, y = 0f;
    View current = this;
    while (current != ancestor) {
      x += current.getLeft() - current.getScrollX() + current.getTranslationX();
      y += current.getTop() - current.getScrollY() + current.getTranslationY();
      android.view.ViewParent parent = current.getParent();
      if (!(parent instanceof View)) break;
      current = (View) parent;
    }
    return new float[] {x, y};
  }

  private float keyCenterX(KeySpec key) {
    for (Hit hit : hits) if (hit.key == key) return hit.rect.centerX();
    return altRect.centerX();
  }

  private void showKeyPreview(Hit hit) {
    dismissKeyPreview();
    if (hit == null
        || disablePreview
        || hit.key == null
        || hit.key.label == null
        || hit.key.label.length() == 0
        || hit.key.code < 0) return;
    boolean enabled =
        getContext()
            .getSharedPreferences("keyboard", Context.MODE_PRIVATE)
            .getBoolean("key_popup_preview", true);
    if (!enabled) return;
    android.widget.FrameLayout frame = findOverlayRoot();
    if (frame == null) return;
    float width = Math.max(dp(54), Math.min(dp(70), hit.rect.width() + dp(10)));
    float height = dp(68);
    float localLeft = hit.rect.centerX() - width / 2f;
    float localTop = hit.rect.top - height;
    float[] offset = offsetInside(frame);
    int left = Math.round(offset[0] + localLeft);
    int top = Math.round(offset[1] + localTop);
    left = Math.max(0, Math.min(frame.getWidth() - (int) width, left));
    top = Math.max(0, top);
    keyPreviewPopup = inflatePopup(R.layout.ime_key_preview_popup);
    keyPreviewPopup.configurePreview(hit.key.label, theme);
    keyPreviewPopup.setPointerX(offset[0] + hit.rect.centerX() - left);
    android.widget.FrameLayout.LayoutParams params =
        new android.widget.FrameLayout.LayoutParams((int) width, (int) height);
    params.gravity = Gravity.LEFT | Gravity.TOP;
    params.leftMargin = left;
    params.topMargin = top;
    frame.addView(keyPreviewPopup, params);
    keyPreviewPopup.bringToFront();
  }

  private void dismissKeyPreview() {
    if (keyPreviewPopup != null) {
      android.view.ViewParent parent = keyPreviewPopup.getParent();
      if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(keyPreviewPopup);
      keyPreviewPopup = null;
    }
  }

  private void dismissAlternativePopup() {
    if (alternativeWindow != null) {
      alternativeWindow.dismiss();
      alternativeWindow = null;
    }
    if (alternativePopup != null) {
      android.view.ViewParent parent = alternativePopup.getParent();
      if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(alternativePopup);
    }
    alternativePopup = null;
  }

  private void updateAlternativeSelection(float x) {
    if (!altVisible || altValues.length == 0) return;
    altSelected =
        Math.max(
            0,
            Math.min(
                altValues.length - 1,
                (int) ((x - altRect.left) / Math.max(1f, altRect.width() / altValues.length))));
    if (alternativePopup != null) alternativePopup.setSelectedIndex(altSelected);
    invalidate();
  }

  private void dismissAlt() {
    dismissAlternativePopup();
    altVisible = false;
    altPersistent = false;
    alternativeKey = null;
    altSelected = -1;
    altValues = new String[0];
    altRect.setEmpty();
    invalidate();
  }

  private void dismissLocale() {
    localeVisible = false;
    spaceGestureAxis = 0;
    spaceDiffX = 0;
    spaceDiffY = 0;
    invalidate();
  }

  private void dismissPopups() {
    dismissKeyPreview();
    dismissAlt();
    dismissLocale();
  }
}
