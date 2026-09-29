package com.almlk.swiftkey.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import java.util.HashMap;

/**
 * Round 44: paints a theme's WIDE bar art (the spacebar ruler skin) as the background
 * of the suggestion strip and the toolbar — the owner asked for the new button shape
 * to cover the suggestion bar along with the keyboard and the rest of the IME chrome.
 *
 * The draw mirrors SmartKeyboardView.drawWideSkinFace exactly: source art with a 3:1+
 * ratio (the golden ruler) draws DIRECT at bar size, anything else slices into three
 * horizontal strips so the rounded edges keep the key scale and only the flat middle
 * stretches. Bitmaps are decoded once per skin and SHARED — never recycled.
 */
public final class ThemeSkinBar extends Drawable {

  private static final HashMap<String, Bitmap> CACHE = new HashMap<String, Bitmap>();

  private final Bitmap skin;
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

  private ThemeSkinBar(Bitmap skin) {
    this.skin = skin;
  }

  /**
   * Skin bar for a theme, or null when the theme has no wide bar art (family and
   * legacy themes keep their flat surface color). Each call returns a FRESH Drawable
   * (bounds are per-view) sharing one cached bitmap per skin.
   */
  public static ThemeSkinBar forTheme(Context context, KeyboardTheme theme) {
    if (context == null || theme == null || theme.keySkin.length() == 0) return null;
    String token = theme.keySkin;
    Bitmap bitmap;
    synchronized (CACHE) {
      bitmap = CACHE.get(token);
    }
    if (bitmap == null) {
      if (token.startsWith(AssetThemeLibrary.URI_PREFIX)) {
        bitmap = AssetThemeLibrary.keySkinPart(context, token, "space");
      } else if (token.startsWith("lux_")) {
        int id =
            context.getResources()
                .getIdentifier("keybg_" + token + "_space", "drawable",
                    context.getPackageName());
        bitmap = id == 0 ? null : BitmapFactory.decodeResource(context.getResources(), id);
      } else {
        return null;
      }
      if (bitmap == null) return null;
      synchronized (CACHE) {
        Bitmap first = CACHE.get(token);
        if (first != null) bitmap = first; // concurrent producer wins
        else CACHE.put(token, bitmap);
      }
    }
    return new ThemeSkinBar(bitmap);
  }

  public void draw(Canvas canvas) {
    Rect b = getBounds();
    if (b.isEmpty() || skin == null || skin.isRecycled()) return;
    float barWidth = b.width();
    float barHeight = b.height();
    if (skin.getWidth() >= skin.getHeight() * 3f) {
      canvas.drawBitmap(skin, null, new RectF(b), paint);
      return;
    }
    int src = Math.min(48, skin.getWidth() / 3);
    float scale = barHeight / skin.getHeight();
    float edge = src * scale;
    if (barWidth < edge * 2.4f) {
      canvas.drawBitmap(skin, null, new RectF(b), paint);
      return;
    }
    Rect leftSrc = new Rect(0, 0, src, skin.getHeight());
    Rect middleSrc = new Rect(src, 0, skin.getWidth() - src, skin.getHeight());
    Rect rightSrc = new Rect(skin.getWidth() - src, 0, skin.getWidth(), skin.getHeight());
    canvas.drawBitmap(skin, leftSrc,
        new RectF(b.left, b.top, b.left + edge, b.bottom), paint);
    canvas.drawBitmap(skin, middleSrc,
        new RectF(b.left + edge, b.top, b.right - edge, b.bottom), paint);
    canvas.drawBitmap(skin, rightSrc,
        new RectF(b.right - edge, b.top, b.right, b.bottom), paint);
  }

  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
  }

  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
  }

  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
