package com.almlk.swiftkey.settings;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.model.KeySpec;
import com.almlk.swiftkey.theme.AssetThemeLibrary;
import com.almlk.swiftkey.theme.KeyArtProcessor;
import com.almlk.swiftkey.theme.KeyFrames;
import com.almlk.swiftkey.theme.KeyboardTheme;
import java.io.InputStream;

/**
 * A faithful static miniature of the real keyboard: same theme colors, same optional key
 * background artwork, and the same drawable icons for special keys (never emoji glyphs).
 * The theme picker uses the built-in Arabic template; the layout picker injects a different
 * key placement through {@link #setPreviewData} while keeping this exact visual design.
 */
public final class ThemeThumbnailView extends View {
  private static final String[][] ROWS = {
    {"ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د"},
    {"ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ذ"},
    {"ئ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز", "ط", "ظ", ""},
    {"١٢٣", "", "،", "العربية", ".", ""}
  };
  private static final int[][] CODES = {
    new int[12],
    new int[11],
    fillLast(11, KeySpec.DELETE),
    new int[] {0, KeySpec.EMOJI, KeySpec.MIC, KeySpec.SPACE, 0, KeySpec.ENTER}
  };
  private static final String[][] SECONDARY = {
    {"١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠", "چ", "ذ"},
    {"@", "#", "&", "پ", "لا", "أ", "=", "(", ")", "گ", "*"},
    {"ـ", "~", "-", "+", ":", "؛", "/", "\"", "؟", "!", ""},
    {"", "", "", "", "", ""}
  };
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private KeyboardTheme theme = KeyboardTheme.from("samsung_white");
  private Bitmap image;
  /** Round 43: true while image is the SHARED library bitmap — never recycled. */
  private boolean imageFromLibrary;
  /** Round 41: decoded key-skin parts so gallery miniatures show the real key art. */
  private final java.util.HashMap<String, Bitmap> keySkinCache =
      new java.util.HashMap<String, Bitmap>();
  /** Round 69: فن الزر (إطار محمّل أو مدمج) بقرار واحد لكل مصغرة. */
  private String frameKey = "\u0000";
  private Bitmap frameCache;
  private String spaceFrameKey = "\u0000";
  private Bitmap spaceFrameCache;
  /** Round 69: فن الزر من الكاش المتوازي — يظهر قبل التنزيل الكامل. */
  private Bitmap frameOverride;
  /** Round 72: فن زر المسافة من الكاش المتوازي. */
  private Bitmap spaceFrameOverride;
  private String[][] previewRows;
  private String[][] previewTrails;
  private int[][] previewCodes;
  private float[][] previewWeights;

  public ThemeThumbnailView(Context context) {
    super(context);
  }

  public ThemeThumbnailView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setTheme(KeyboardTheme value) {
    theme = value;
    keySkinCache.clear();
    frameKey = "\u0000";
    frameCache = null;
    spaceFrameKey = "\u0000";
    spaceFrameCache = null;
    frameOverride = null;
    spaceFrameOverride = null;
    loadImage();
    invalidate();
  }

  /**
   * Round 69: فن زر من الذاكرة (كاش ثيماتي المتوازي) — يرسمه المصغر فوق
   * المفاتيح فوراً حتى قبل تنزيل الثيم كاملاً؛ لا يُعاد تدويره أبداً.
   */
  public void setFrameArtOverride(Bitmap art) {
    if (art == null) return;
    frameOverride = art;
    invalidate();
  }

  /** فن زر المسافة للثيم من الكاش المتوازي. */
  public void setSpaceFrameArtOverride(Bitmap art) {
    if (art == null) return;
    spaceFrameOverride = art;
    invalidate();
  }

  /** Round 69: فن زر الثيم — المحمّل أولاً ثم المدمج — بلا إعادة تدوير. */
  private Bitmap thumbnailFrame() {
    String key =
        theme.keyFrameUri + "\n" + theme.keyFrame + "\n" + Math.round(theme.keyRadiusDp);
    if (key.equals(frameKey)) return frameCache;
    frameKey = key;
    frameCache = null;
    if (theme.keyFrameUri.length() > 0) {
      frameCache = KeyArtProcessor.fileKeyArt(theme.keyFrameUri);
    }
    if (frameCache == null && theme.keyFrame > 0) {
      String name = KeyFrames.artName(theme.keyFrame, theme.keyRadiusDp);
      if (name.length() > 0) {
        int id =
            getResources().getIdentifier(name, "drawable", getContext().getPackageName());
        if (id != 0) frameCache = BitmapFactory.decodeResource(getResources(), id);
      }
    }
    return frameCache;
  }

  /** فن مستقل لزر المسافة، مع ارتجاع لفن الزر العادي إن لم يكن متاحاً. */
  private Bitmap thumbnailSpaceFrame() {
    if (spaceFrameOverride != null) return spaceFrameOverride;
    String key = theme.keySpaceFrameUri;
    if (key == null || key.length() == 0) return frameOverride != null ? frameOverride : thumbnailFrame();
    if (key.equals(spaceFrameKey)) return spaceFrameCache;
    spaceFrameKey = key;
    spaceFrameCache = KeyArtProcessor.fileSpaceArt(key);
    return spaceFrameCache == null ? (frameOverride != null ? frameOverride : thumbnailFrame()) : spaceFrameCache;
  }

  /** Round 41: lazily decodes the theme's skin part for a key type; null = programmatic. */
  private Bitmap thumbnailSkin(int code, boolean bottomRow) {
    if (theme.keySkin.length() == 0) return null;
    String part = "up";
    if (code == KeySpec.SPACE) part = "space";
    else if (code == KeySpec.ENTER) part = "enter";
    else if (code == KeySpec.SHIFT || code == KeySpec.DELETE || bottomRow) part = "fun";
    Bitmap cached = keySkinCache.get(part);
    if (cached != null) return cached;
    // Round 43: imported skins resolve through the library (shared bitmaps).
    if (theme.keySkin.startsWith(AssetThemeLibrary.URI_PREFIX)) {
      Bitmap art = AssetThemeLibrary.keySkinPart(getContext(), theme.keySkin, part);
      if (art != null) keySkinCache.put(part, art);
      return art;
    }
    int id =
        getResources()
            .getIdentifier(
                "keybg_" + theme.keySkin + "_" + part, "drawable", getContext().getPackageName());
    if (id == 0) return null;
    cached = BitmapFactory.decodeResource(getResources(), id);
    keySkinCache.put(part, cached);
    return cached;
  }

  /** Replaces the key placement while keeping the theme-card miniature design untouched. */
  public void setPreviewData(
      String[][] labels, String[][] trails, int[][] codes, float[][] weights) {
    previewRows = labels;
    previewTrails = trails;
    previewCodes = codes;
    previewWeights = weights;
    invalidate();
  }

  protected void onDraw(Canvas canvas) {
    if (image == null) {
      canvas.drawColor(theme.background);
    } else {
      drawCover(canvas, image);
    }
    String[][] rows = previewRows == null ? ROWS : previewRows;
    int[][] codes = previewCodes == null ? CODES : previewCodes;
    String[][] trails = previewTrails == null ? SECONDARY : previewTrails;
    float gap = Math.max(2f, getWidth() / 120f);
    int count = rows.length;
    float[] rowUnits = new float[count];
    float[] rowStarts = new float[count];
    float minUnit = 0f;
    for (int row = 0; row < count; row++) {
      String[] labels = rows[row];
      float[] weights = weightsFor(row, labels.length);
      float total = 0f;
      for (int i = 0; i < labels.length; i++) {
        total += weights == null || i >= weights.length ? 1f : weights[i];
      }
      float unit;
      float first;
      if (previewRows == null && row < count - 1) {
        unit = (getWidth() - gap * 13f) / 12f;
        first = (getWidth() - (unit * total + gap * (labels.length - 1))) / 2f;
      } else {
        unit = (getWidth() - gap * (labels.length + 1f)) / total;
        first = gap;
      }
      rowUnits[row] = unit;
      rowStarts[row] = first;
      if (minUnit == 0f || unit < minUnit) {
        minUnit = unit;
      }
    }
    float rowHeight = (getHeight() - gap * (count + 1f)) / count;
    if (minUnit > 0f && rowHeight > minUnit * 1.9f) {
      rowHeight = minUnit * 1.9f;
    }
    float top = (getHeight() - (rowHeight * count + gap * (count + 1f))) / 2f;
    for (int row = 0; row < count; row++) {
      String[] labels = rows[row];
      String[] rowTrails = row < trails.length ? trails[row] : null;
      float[] weights = weightsFor(row, labels.length);
      float x = rowStarts[row];
      for (int col = 0; col < labels.length; col++) {
        float width =
            rowUnits[row] * (weights == null || col >= weights.length ? 1f : weights[col]);
        RectF bounds = new RectF(x, top, x + width, top + rowHeight);
        String trail = rowTrails == null || col >= rowTrails.length ? "" : rowTrails[col];
        int code = row < codes.length && col < codes[row].length ? codes[row][col] : 0;
        drawKey(canvas, bounds, labels[col], trail, code, row == count - 1, rowHeight);
        x += width + gap;
      }
      top += rowHeight + gap;
    }
  }

  private float[] weightsFor(int row, int columns) {
    if (previewRows != null) {
      return previewWeights != null && row < previewWeights.length ? previewWeights[row] : null;
    }
    return row == ROWS.length - 1
        ? new float[] {1.2f, 1f, 1f, 3.8f, 1f, 1.25f}
        : null;
  }

  private void drawKey(
      Canvas canvas,
      RectF bounds,
      String label,
      String secondary,
      int code,
      boolean bottomRow,
      float rowHeight) {
    int color = keyColor(code, bottomRow);
    Bitmap skin = thumbnailSkin(code, bottomRow);
    // Round 69/72: فن الزر المفعّل، وللمسافة فن مستقل عند توفره.
    Bitmap frame = skin == null ? (code == KeySpec.SPACE ? thumbnailSpaceFrame()
        : (frameOverride != null ? frameOverride : thumbnailFrame())) : null;
    int letterColor = color == theme.key ? theme.text : theme.contentColorFor(color);
    int smallColor = color == theme.key ? theme.sub : theme.contentColorFor(color);
    RectF face;
    if (skin != null) {
      // Round 41: the gallery miniature draws the theme's real key-skin art.
      face = new RectF(bounds);
      paint.setStyle(Paint.Style.FILL);
      paint.setFilterBitmap(true);
      paint.setAlpha(255);
      canvas.drawBitmap(skin, null, face, paint);
    } else if (frame != null) {
      // Round 71: فن الزر يملأ كامل مساحة المفتاح — مطابق لرسم الجلود
      // (skins) ولرسم الكيبورد الحي. يُمطّ ليغطي الحواف البديلة ومساحة
      // زر المسافة بالكامل بلا فراغات.
      face = new RectF(bounds);
      paint.setStyle(Paint.Style.FILL);
      paint.setFilterBitmap(true);
      paint.setAlpha(Math.round(255f * theme.keyOpacity));
      canvas.drawBitmap(frame, null, face, paint);
      paint.setAlpha(255);
    } else {
      float corner = Math.min(dp(theme.keyRadiusDp), rowHeight * .42f);
      float depth = Math.max(1.2f, rowHeight * .06f);
      RectF shadow = new RectF(bounds.left, bounds.top + depth, bounds.right, bounds.bottom);
      face = new RectF(bounds.left, bounds.top, bounds.right, bounds.bottom - depth);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(withAlpha(blend(color, Color.BLACK, .23f), theme.keyOpacity));
      canvas.drawRoundRect(shadow, corner, corner, paint);
      paint.setColor(withAlpha(color, theme.keyOpacity));
      canvas.drawRoundRect(face, corner, corner, paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(.65f);
      paint.setColor(withAlpha(theme.accent, Math.max(.32f, theme.keyOpacity)));
      canvas.drawRoundRect(face, corner, corner, paint);
      paint.setStyle(Paint.Style.FILL);
    }

    Drawable icon = iconFor(code, label);
    if (icon != null && code != KeySpec.MIC) {
      // Round 41-2: MIC keys draw the comma letter; the mic icon rides the top slot.
      float size = rowHeight * (code == KeySpec.DELETE ? .66f : .58f);
      icon.setBounds(
          (int) (face.centerX() - size / 2f),
          (int) (face.centerY() - size / 2f),
          (int) (face.centerX() + size / 2f),
          (int) (face.centerY() + size / 2f));
      icon.setColorFilter(letterColor, PorterDuff.Mode.SRC_IN);
      icon.draw(canvas);
    } else if (label.length() > 0) {
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTypeface(Typeface.create(fontFamily(), Typeface.NORMAL));
      paint.setColor(letterColor);
      paint.setTextSize(
          bottomRow ? Math.min(rowHeight * .33f, bounds.width() * .23f) : rowHeight * .43f);
      Paint.FontMetrics metrics = paint.getFontMetrics();
      float baseline =
          secondary.length() == 0
              ? face.centerY() - (metrics.ascent + metrics.descent) / 2f
              : face.bottom - rowHeight * .08f - metrics.descent;
      canvas.drawText(label, face.centerX(), baseline, paint);
      if (code == KeySpec.SPACE) {
        paint.setColor(theme.accent);
        canvas.drawRoundRect(
            new RectF(
                face.left + face.width() * .26f,
                face.bottom - rowHeight * .16f,
                face.right - face.width() * .26f,
                face.bottom - rowHeight * .06f),
            rowHeight * .05f,
            rowHeight * .05f,
            paint);
      }
    }
    if (code == KeySpec.MIC) {
      // Round 58: أيقونة الحافظة في مكان الحرف البديل فوق الفاصلة (كانت ميكرفوناً).
      Drawable micIcon = iconFor(code, label);
      if (micIcon != null) {
        float micSize = rowHeight * .34f;
        micIcon.setBounds(
            (int) (face.centerX() - micSize / 2f),
            (int) (face.top + rowHeight * .06f),
            (int) (face.centerX() + micSize / 2f),
            (int) (face.top + rowHeight * .06f + micSize));
        micIcon.setColorFilter(letterColor, PorterDuff.Mode.SRC_IN);
        micIcon.draw(canvas);
      }
    }
    if (secondary.length() > 0) {
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTypeface(Typeface.create(fontFamily(), Typeface.NORMAL));
      paint.setColor(smallColor);
      paint.setTextSize(Math.max(5.5f, rowHeight * .22f));
      Paint.FontMetrics smallMetrics = paint.getFontMetrics();
      canvas.drawText(
          secondary, face.centerX(), face.top + rowHeight * .05f - smallMetrics.ascent, paint);
    }
  }

  private Drawable iconFor(int code, String label) {
    int id =
        code == KeySpec.SHIFT && label.length() == 0
            ? R.drawable.ic_shift
            : code == KeySpec.DELETE
                ? R.drawable.ic_backspace
                : code == KeySpec.ENTER
                    ? R.drawable.ic_enter
                    : code == KeySpec.EMOJI
                        ? R.drawable.ic_emoji
                        : code == KeySpec.MIC
                            ? R.drawable.ic_clipboard // Round 58: زر الفاصلة يمثل الحافظة
                            : code == KeySpec.TAB && label.length() == 0
                                ? R.drawable.ic_tab
                                : 0;
    return id == 0 ? null : getResources().getDrawable(id);
  }

  private String fontFamily() {
    if (theme.fontStyle == 1) {
      return "serif";
    }
    if (theme.fontStyle == 2) {
      return "sans-serif-condensed";
    }
    if (theme.fontStyle == 3) {
      return "monospace";
    }
    if (theme.fontStyle == 4) {
      return "sans-serif-light";
    }
    return "sans";
  }

  private int keyColor(int code, boolean bottomRow) {
    if (code == KeySpec.DELETE) {
      return theme.deleteKey;
    }
    if (code == KeySpec.SPACE) {
      return theme.spaceKey;
    }
    if (code == KeySpec.SHIFT) {
      return theme.shiftKey;
    }
    if (bottomRow) {
      return theme.bottomKey;
    }
    return theme.key;
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }

  private void loadImage() {
    if (image != null) {
      // Round 64: لا إعادة تدوير — قوائم العرض العتادية قد تملك الصورة؛ GC يكفي.
      image = null;
      imageFromLibrary = false;
    }
    String source = theme.imageSource();
    if (source.length() == 0) {
      return;
    }
    // Round 43: imported theme backgrounds come from the shared library cache.
    if (source.startsWith(AssetThemeLibrary.URI_PREFIX)) {
      image = AssetThemeLibrary.keyboardBackground(getContext(), source);
      imageFromLibrary = image != null;
      return;
    }
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = 4;
      if (source.indexOf(':') < 0) {
        int id = getResources().getIdentifier(source, "drawable", getContext().getPackageName());
        if (id != 0) {
          image = BitmapFactory.decodeResource(getResources(), id, options);
        }
      } else {
        InputStream stream = getContext().getContentResolver().openInputStream(Uri.parse(source));
        try {
          image = BitmapFactory.decodeStream(stream, null, options);
        } finally {
          if (stream != null) {
            stream.close();
          }
        }
      }
    } catch (Exception ignored) {
      image = null;
    }
  }

  private void drawCover(Canvas canvas, Bitmap bitmap) {
    float scale =
        Math.max(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
    float width = bitmap.getWidth() * scale;
    float height = bitmap.getHeight() * scale;
    canvas.drawBitmap(
        bitmap,
        null,
        new RectF(
            (getWidth() - width) / 2f,
            (getHeight() - height) / 2f,
            (getWidth() + width) / 2f,
            (getHeight() + height) / 2f),
        paint);
  }

  private static int[] fillLast(int length, int value) {
    int[] codes = new int[length];
    codes[length - 1] = value;
    return codes;
  }

  private static int blend(int first, int second, float amount) {
    float keep = 1f - amount;
    return Color.rgb(
        Math.round(Color.red(first) * keep + Color.red(second) * amount),
        Math.round(Color.green(first) * keep + Color.green(second) * amount),
        Math.round(Color.blue(first) * keep + Color.blue(second) * amount));
  }

  private static int withAlpha(int color, float opacity) {
    return Color.argb(
        Math.round(255f * opacity), Color.red(color), Color.green(color), Color.blue(color));
  }

  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (image != null) {
      // Round 64: المصغرة قد تُعاد توصيلها بعد الفصل — لا recycle إطلاقاً؛ GC يكفي.
      image = null;
      imageFromLibrary = false;
    }
  }
}
