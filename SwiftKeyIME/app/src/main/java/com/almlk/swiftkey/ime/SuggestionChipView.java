package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.TextView;

/**
 * Small suggestion cell with an optional correction marker; kept outside the IME service for
 * D8/AIDE.
 */
public final class SuggestionChipView extends TextView {
  private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint bestPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private boolean correction;
  private boolean best;
  private int bestColor = 0xffffffff;

  public SuggestionChipView(Context context) {
    super(context);
    markerPaint.setColor(0xffe53935);
    markerPaint.setStrokeCap(Paint.Cap.ROUND);
  }

  public void setCorrection(boolean value) {
    correction = value;
    invalidate();
  }

  public void setBest(boolean value, int color) {
    best = value;
    bestColor = color;
    invalidate();
  }

  public void setBestColor(int color) {
    bestColor = color;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (length() == 0) return;
    float density = getResources().getDisplayMetrics().density;
    if (correction) {
      markerPaint.setStrokeWidth(2f * density);
      float half = 9f * density;
      float y = getHeight() - 5f * density;
      canvas.drawLine(getWidth() / 2f - half, y, getWidth() / 2f + half, y, markerPaint);
    }
    if (best) {
      bestPaint.setStyle(Paint.Style.FILL);
      bestPaint.setColor(bestColor);
      bestPaint.setAlpha(255);
      float centerX = getWidth() / 2f;
      float y = getHeight() - 4f * density;
      float radius = 1.35f * density;
      float spacing = 5.5f * density;
      canvas.drawCircle(centerX - spacing, y, radius, bestPaint);
      canvas.drawCircle(centerX, y, radius, bestPaint);
      canvas.drawCircle(centerX + spacing, y, radius, bestPaint);
    }
  }
}
