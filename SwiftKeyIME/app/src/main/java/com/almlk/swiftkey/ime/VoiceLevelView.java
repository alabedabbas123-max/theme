package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Lightweight microphone meter. It owns no timer and redraws only when RMS changes. */
public final class VoiceLevelView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private float level;
  private int color = 0xff17263c;

  public VoiceLevelView(Context context) {
    super(context);
  }

  public VoiceLevelView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setMeterColor(int value) {
    color = value;
    invalidate();
  }

  public void setLevel(float rms) {
    float next = Math.max(0f, Math.min(1f, (rms + 2f) / 12f));
    if (Math.abs(next - level) > .025f) {
      level = next;
      invalidate();
    }
  }

  public void release() {
    level = 0f;
    invalidate();
  }

  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float centerX = getWidth() / 2f;
    float centerY = getHeight() / 2f;
    paint.setColor((color & 0x00ffffff) | 0x22000000);
    canvas.drawCircle(centerX, centerY, dp(24), paint);
    paint.setColor(color);
    float gap = dp(7);
    for (int i = 0; i < 5; i++) {
      float distance = Math.abs(2 - i) / 2f;
      float radius = dp(2.4f) + level * dp(4.3f - distance * 1.2f);
      canvas.drawCircle(centerX + (i - 2) * gap, centerY, Math.max(dp(2.4f), radius), paint);
    }
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }
}
