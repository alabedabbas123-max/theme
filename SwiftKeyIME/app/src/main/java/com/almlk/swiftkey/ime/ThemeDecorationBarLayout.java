package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import com.almlk.swiftkey.theme.KeyboardTheme;

/** Toolbar surface that continues the selected custom decoration behind all tool icons. */
public final class ThemeDecorationBarLayout extends LinearLayout {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private KeyboardTheme theme = KeyboardTheme.from("samsung_white");

  public ThemeDecorationBarLayout(Context context) {
    super(context);
    initialize();
  }

  public ThemeDecorationBarLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  private void initialize() {
    setWillNotDraw(false);
  }

  public void setTheme(KeyboardTheme value) {
    if (value != null) theme = value;
    // Round 52: خلفية الثيم نفسها إن وُجدت — وإلا لون السطح كما كان
    com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(this, getContext(), theme);
    invalidate();
  }

  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int style = theme.animationStyle;
    if (style == KeyboardTheme.ANIMATION_NONE) return;
    long now = android.os.SystemClock.uptimeMillis();
    paint.setColor(theme.accent);
    paint.setStrokeWidth(dp(1.2f));
    for (int index = 0; index < 9; index++) {
      float progress = ((now / (13f + index % 4) + index * 113f) % 1000f) / 1000f;
      float x = getWidth() * ((index * 37 % 97) / 100f);
      float y = getHeight() * (1f - progress);
      float size = dp(1.5f + index % 3);
      paint.setAlpha(45 + index % 5 * 25);
      if (style == KeyboardTheme.ANIMATION_HEARTS) {
        paint.setStyle(Paint.Style.FILL);
        drawHeart(canvas, x, y, size, paint);
      } else if (style == KeyboardTheme.ANIMATION_PLANETS
          || style == KeyboardTheme.ANIMATION_BUBBLES) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y, size, paint);
      } else if (style == KeyboardTheme.ANIMATION_MUSIC) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(x, y, size * .45f, paint);
        canvas.drawLine(x + size * .4f, y, x + size * .4f, y - size * 2f, paint);
      } else if (style == KeyboardTheme.ANIMATION_SPEED) {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(x - size * 3f, y, x + size * 2f, y, paint);
      } else {
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(x - size, y, x + size, y, paint);
        canvas.drawLine(x, y - size, x, y + size, paint);
      }
    }
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(255);
    if (getWindowVisibility() == VISIBLE) postInvalidateDelayed(55);
  }

  private void drawHeart(Canvas canvas, float x, float y, float size, Paint value) {
    Path heart = new Path();
    heart.moveTo(x, y + size);
    heart.cubicTo(x - size * 1.7f, y, x - size, y - size * 1.3f, x, y - size * .5f);
    heart.cubicTo(x + size, y - size * 1.3f, x + size * 1.7f, y, x, y + size);
    heart.close();
    canvas.drawPath(heart, value);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }
}
