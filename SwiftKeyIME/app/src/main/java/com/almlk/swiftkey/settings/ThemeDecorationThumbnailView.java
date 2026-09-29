package com.almlk.swiftkey.settings;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.almlk.swiftkey.theme.KeyboardTheme;

/** Animated decoration card used by the custom-theme Auto gallery. */
public final class ThemeDecorationThumbnailView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int style;
  /**
   * Round 48: overlay mode — يرسم جسيمات التأثير فقط فوق معاينة كيبورد حقيقية
   * (ThemeThumbnailView) بدل خلفية متدرجة تُخفي كل شيء، فتظهر البطاقة واضحة.
   */
  private boolean overlayOnly;

  public ThemeDecorationThumbnailView(Context context) {
    super(context);
  }

  public void setStyle(int value) {
    style = value;
    invalidate();
  }

  public void setOverlayOnly(boolean value) {
    overlayOnly = value;
    invalidate();
  }

  protected void onDraw(Canvas canvas) {
    long now = android.os.SystemClock.uptimeMillis();
    if (!overlayOnly) {
      paint.setShader(
          new LinearGradient(
              0, 0, getWidth(), getHeight(), 0xff17142f, 0xff5f268d, Shader.TileMode.CLAMP));
      canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
      paint.setShader(null);
      drawMiniKeys(canvas);
    }
    drawDecoration(canvas, now);
    if (style != KeyboardTheme.ANIMATION_NONE && getWindowVisibility() == VISIBLE) {
      postInvalidateDelayed(55);
    }
  }

  private void drawMiniKeys(Canvas canvas) {
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(1));
    paint.setColor(0xccffffff);
    for (int row = 0; row < 3; row++) {
      for (int column = 0; column < 7; column++) {
        float gap = dp(2);
        float width = (getWidth() - gap * 8) / 7f;
        float height = (getHeight() - dp(21)) / 3f;
        float left = gap + column * (width + gap);
        float top = dp(15) + row * height;
        canvas.drawRoundRect(
            new RectF(left, top, left + width, top + height - gap), dp(3), dp(3), paint);
      }
    }
    paint.setStyle(Paint.Style.FILL);
  }

  private void drawDecoration(Canvas canvas, long now) {
    paint.setColor(0xffffe066);
    if (style == KeyboardTheme.ANIMATION_NONE) return;
    for (int index = 0; index < 10; index++) {
      float progress = ((now / (12f + index % 5) + index * 97f) % 1000f) / 1000f;
      float x = getWidth() * ((index * 37 % 101) / 100f);
      float y = getHeight() * (1f - progress);
      float size = dp(1.8f + index % 3);
      paint.setAlpha(70 + index % 5 * 32);
      if (style == KeyboardTheme.ANIMATION_STARS || style == KeyboardTheme.ANIMATION_SPARKLES) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(x - size, y, x + size, y, paint);
        canvas.drawLine(x, y - size, x, y + size, paint);
      } else if (style == KeyboardTheme.ANIMATION_PLANETS) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y, size * 1.4f, paint);
        canvas.drawOval(
            new RectF(x - size * 2.2f, y - size * .5f, x + size * 2.2f, y + size * .5f), paint);
      } else if (style == KeyboardTheme.ANIMATION_HEARTS) {
        paint.setStyle(Paint.Style.FILL);
        drawHeart(canvas, x, y, size, paint);
      } else if (style == KeyboardTheme.ANIMATION_BUBBLES) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y, size * 1.5f, paint);
      } else if (style == KeyboardTheme.ANIMATION_PETALS) {
        paint.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.rotate(progress * 260f, x, y);
        canvas.drawOval(new RectF(x - size, y - size * .45f, x + size, y + size * .45f), paint);
        canvas.restore();
      } else if (style == KeyboardTheme.ANIMATION_MUSIC) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y, size * .5f, paint);
        canvas.drawLine(x + size * .45f, y, x + size * .45f, y - size * 2f, paint);
      } else if (style == KeyboardTheme.ANIMATION_SPEED) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(x - size * 3f, y, x + size * 2f, y, paint);
      } else if (style == KeyboardTheme.ANIMATION_FIREWORKS) {
        paint.setStyle(Paint.Style.STROKE);
        for (int ray = 0; ray < 6; ray++) {
          double angle = ray * Math.PI / 3.0;
          canvas.drawLine(
              x,
              y,
              x + (float) Math.cos(angle) * size * 2f,
              y + (float) Math.sin(angle) * size * 2f,
              paint);
        }
      } else {
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, size * .55f, paint);
      }
    }
    paint.setAlpha(255);
    paint.setStyle(Paint.Style.FILL);
  }

  private void drawHeart(Canvas canvas, float x, float y, float size, Paint value) {
    Path heart = new Path();
    heart.moveTo(x, y + size);
    heart.cubicTo(x - size * 1.8f, y, x - size, y - size * 1.4f, x, y - size * .5f);
    heart.cubicTo(x + size, y - size * 1.4f, x + size * 1.8f, y, x, y + size);
    heart.close();
    canvas.drawPath(heart, value);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }
}
