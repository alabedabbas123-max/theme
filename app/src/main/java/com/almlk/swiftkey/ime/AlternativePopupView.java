package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import com.almlk.swiftkey.theme.KeyboardTheme;

/** Root-overlay surface for alternatives and the enlarged key preview. */
public final class AlternativePopupView extends View {
  public interface ChoiceListener {
    void onChoice(int index, String value);
  }

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path pointerPath = new Path();
  private String[] values = new String[0];
  private int selected = -1;
  private KeyboardTheme theme = KeyboardTheme.from("light");
  private ChoiceListener choiceListener;
  private boolean previewMode;
  private float pointerX = -1f;

  public AlternativePopupView(Context context) {
    super(context);
    initialize();
  }

  /** Required when this custom view is created from a layout XML file. */
  public AlternativePopupView(Context context, android.util.AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  /** Required for XML styles on older and newer Android versions. */
  public AlternativePopupView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initialize();
  }

  private void initialize() {
    setFocusable(false);
    setLayerType(View.LAYER_TYPE_SOFTWARE, null);
  }

  public void configure(String[] items, int selectedIndex, KeyboardTheme value) {
    values = items == null ? new String[0] : items;
    selected = selectedIndex;
    previewMode = false;
    if (value != null) theme = value;
    invalidate();
  }

  public void configurePreview(String label, KeyboardTheme value) {
    values = new String[] {label == null ? "" : label};
    selected = -1;
    previewMode = true;
    if (value != null) theme = value;
    invalidate();
  }

  public void setPointerX(float value) {
    pointerX = value;
    invalidate();
  }

  public void setChoiceListener(ChoiceListener listener) {
    choiceListener = listener;
    setClickable(listener != null);
  }

  public void setSelectedIndex(int value) {
    if (selected != value) {
      selected = value;
      invalidate();
    }
  }

  private int indexAt(float x) {
    if (values.length == 0 || getWidth() <= 0) return -1;
    float left = getPaddingLeft();
    float width = Math.max(1f, getWidth() - getPaddingLeft() - getPaddingRight());
    return Math.max(0, Math.min(values.length - 1, (int) ((x - left) / (width / values.length))));
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (choiceListener == null) return false;
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
      setSelectedIndex(indexAt(event.getX()));
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      int index = indexAt(event.getX());
      if (index >= 0) {
        setSelectedIndex(index);
        performClick();
        choiceListener.onChoice(index, values[index]);
      }
      return true;
    }
    return action == MotionEvent.ACTION_CANCEL;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float density = getResources().getDisplayMetrics().density;
    float tail = 8f * density;
    float bodyBottom = Math.max(1f, getHeight() - tail);
    RectF bounds = new RectF(1f * density, 1f * density, getWidth() - 1f * density, bodyBottom);

    paint.setStyle(Paint.Style.FILL);
    paint.setColor(theme.key);
    paint.setShadowLayer(5f * density, 0, 3f * density, 0x66000000);
    canvas.drawRoundRect(bounds, 12f * density, 12f * density, paint);
    if (pointerX >= 0f) {
      float center = Math.max(10f * density, Math.min(getWidth() - 10f * density, pointerX));
      pointerPath.reset();
      pointerPath.moveTo(center - 8f * density, bodyBottom - 1f);
      pointerPath.lineTo(center, getHeight() - 1f);
      pointerPath.lineTo(center + 8f * density, bodyBottom - 1f);
      pointerPath.close();
      canvas.drawPath(pointerPath, paint);
    }
    paint.clearShadowLayer();

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(density);
    paint.setColor(theme.sub);
    paint.setAlpha(100);
    canvas.drawRoundRect(bounds, 12f * density, 12f * density, paint);
    paint.setAlpha(255);
    paint.setStyle(Paint.Style.FILL);

    float contentLeft = getPaddingLeft();
    float contentRight = Math.max(contentLeft + 1f, getWidth() - getPaddingRight());
    float contentTop = getPaddingTop();
    float contentBottom =
        Math.max(contentTop + 1f, bodyBottom - Math.max(0, getPaddingBottom() - (int) tail));
    float cell = (contentRight - contentLeft) / (float) Math.max(1, values.length);
    for (int index = 0; index < values.length; index++) {
      float left = contentLeft + index * cell;
      if (!previewMode && index == selected) {
        paint.setColor(theme.accent);
        canvas.drawRoundRect(
            new RectF(
                left + 3f * density,
                4f * density,
                left + cell - 3f * density,
                contentBottom - 3f * density),
            9f * density,
            9f * density,
            paint);
      }
      paint.setTextAlign(Paint.Align.CENTER);
      paint.setTypeface(
          android.graphics.Typeface.create(
              "sans",
              previewMode ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
      paint.setTextSize((previewMode ? 30f : 22f) * density);
      paint.setColor(!previewMode && index == selected ? Color.WHITE : theme.text);
      Paint.FontMetrics metrics = paint.getFontMetrics();
      float baseline =
          (contentTop + contentBottom) / 2f - (metrics.ascent + metrics.descent) / 2f - density;
      canvas.drawText(values[index], left + cell / 2f, baseline, paint);
    }
  }
}
