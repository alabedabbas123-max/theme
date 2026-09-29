package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.theme.KeyboardTheme;

public final class KeyboardResizeOverlay extends View {
    /** Applied = "fix the keyboard at this row height now". Confirm = close only. */
    public interface Callback {
        void onResizeCommitted(float rowHeightDp);
        /** Live elastic step while dragging: the pending ROW height, applied through the
         *  runtime's onComputeInsets pipeline — never through LayoutParams writes. */
        void onLiveRowHeight(float rowHeightDp);

        void onConfirm();

        void onReset();
    }
    private static final float GRIP_RADIUS_DP = 23f;
    private static final float HANDLE_BAR_WIDTH_DP = 132f;
    private static final float HANDLE_BAR_HEIGHT_DP = 8f;
    private static final float CAPSULE_WIDTH_DP = 150f;
    private static final float CAPSULE_HEIGHT_DP = 56f;
    private static final int GRIP_BLUE = 0xff2e8bff;
    private static final int BACKDROP_BLACK = 0x21000000;   // the 13% translucent dim
    private static final int ICON_DARK = 0xff243043;
    private static final DashPathEffect GHOST_DASH =
    new DashPathEffect(new float[] {12f, 10f}, 0f);
    private float startY = 0f;
    private float endY = 0f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final RectF capsule = new RectF();
    private final RectF resetButton = new RectF();
    private final RectF confirmButton = new RectF();
    private final RectF keyboardFrame = new RectF();
    private final RectF iconArc = new RectF();
    private final RectF topGrip = new RectF();
    private final RectF gripDst = new RectF();
    private final RectF handleBar = new RectF();
    private final int[] locTarget = new int[2];
    private final int[] locSelf = new int[2];
    private Bitmap gripBitmap;
    private Callback callback;
    private View targetView;
    private KeyboardTheme theme = KeyboardTheme.from("light");
    private final View.OnLayoutChangeListener repaintOnLayout =
    new View.OnLayoutChangeListener() {
        public void onLayoutChange(
            View view,
            int left,
            int top,
            int right,
            int bottom,
            int oldLeft,
            int oldTop,
            int oldRight,
            int oldBottom) {
            invalidate();
        }
    };
    // ---- drag state (live rows through the insets channel; persistence at the release) ----
    private boolean dragging;
    private int activePointerId = -1;
    private float startRowHeightDp = KeyboardResizeModel.DEFAULT_KEY_ROW_DP;
    private int rowCount = 4;
    private float dragDyPx;
    private float lastRawYpx;
    private float lastReportedRowDp = Float.NaN;
    /** One live hand-off per display frame (round 39): coalesces the touch stream so the panel
     *  rows grow and shrink exactly with the sheet — lightly, no mid-frame layout storms. */
    private final Runnable liveRowStep =
    new Runnable() {
        public void run() {
            if (dragging) {
                reportLiveRow();
            }
        }
    };
    public KeyboardResizeOverlay(Context context) {
        super(context);
        initialize();
    }

    public KeyboardResizeOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setClickable(true);
        setFocusable(true);
        // explicit empty backdrop: no default drawable, no Overdraw behind the dimmer
        setBackgroundColor(0x00000000);
        try {
            gripBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.kbd_resize_grip);
        } catch (Throwable ignored) {
            gripBitmap = null;
        }
        // The whole touch model of this feature is one OnTouchListener, as requested — plain
        // Java, no Kotlin, no helper libraries.
        setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View view, MotionEvent event) {
                    return handleTouch(event);
                }
            });
    }

    public void setCallback(Callback value) {
        callback = value;
    }

    public void setTheme(KeyboardTheme value) {
        if (value != null) {
            theme = value;
            invalidate();
        }
    }

    /** Kept so the frame repaints if the keyboard re-layouts behind the dimmer. */
    public void setTargetView(View value) {
        if (targetView == value) {
            return;
        }
        if (targetView != null) {
            targetView.removeOnLayoutChangeListener(repaintOnLayout);
        }
        targetView = value;
        if (targetView != null) {
            targetView.addOnLayoutChangeListener(repaintOnLayout);
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) getParent()).addOnLayoutChangeListener(repaintOnLayout);
        }
    }
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) getParent()).removeOnLayoutChangeListener(repaintOnLayout);
        }
    }
    /** Opens the interface riding the keyboard's CURRENT row height and row count. */
    public void show(float currentRowHeightDp, int referenceRows) {
        startRowHeightDp = KeyboardResizeModel.clampRowHeightDp(currentRowHeightDp);
        rowCount = referenceRows > 0 ? referenceRows : 1;
        dragging = false;
        activePointerId = -1;
        dragDyPx = 0f;
        lastReportedRowDp = startRowHeightDp;
        setVisibility(VISIBLE);
        bringToFront();
        requestFocus();
        requestLayout();
        invalidate();
    }
    public void releaseTransientState() {
        dragging = false;
        activePointerId = -1;
        dragDyPx = 0f;
        clearAnimation();
        setVisibility(GONE);
        requestLayout();
    }
    /**
     * Fallback block height for the pre-layout frame (targetView not attached yet). Paint-only
     * value — height decisions come from the drag start + finger travel through the model.
     */
    private int currentBlockHeightPx() {
        return getHeight() > 0
            ? getHeight()
            : (int) (dp(KeyboardResizeModel.DEFAULT_KEY_ROW_DP) * 4f + dp(110f));
    }
    /**
     * THE sheet IS the keyboard panel's own rect BY CONSTRUCTION: height := the measured height
     * of main_keyboard_frame (toolbar + suggestions + keys). No spec adoption, no sibling scan,
     * no full-window branch — a surface taller than the keyboard is not merely avoided here, it
     * is unrepresentable, exactly the reference mechanism (image 2): frame + ball + pill live on
     * the keyboard and nowhere else. Panel not measured yet -> the scrim stays 0 tall for that
     * single frame; the panel's own layout pass relayouts the root and re-measures this view.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        // prefer the PANEL from the tree over targetView so stale external wiring (e.g. a project
        // still pointing at the keys view only) can never shrink the sheet below the keyboard chrome
        View panel = null;
        if (getParent() instanceof android.view.ViewGroup) {
            panel = ((android.view.ViewGroup) getParent()).findViewById(R.id.main_keyboard_frame);
        }
        if (panel == null) {
            panel = targetView;
        }
        int height = 0;
        if (panel != null && panel.getVisibility() == View.VISIBLE) {
            height = panel.getMeasuredHeight();
        }
        setMeasuredDimension(width, height);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }


    /** The pending height at the finger — THE release value, shared by preview and commit. */
    private float pendingRowHeightDp() {
        return KeyboardResizeModel.pendingRowHeightDp(
            startRowHeightDp, dragDyPx, rowCount, getResources().getDisplayMetrics().density);
    }

    /**
     * The TOP edge of the panel column in this view's coordinates — the handle and the dashed
     * guide weld HERE (image 2: arrows at the keyboard's own top, never mid-screen). Window
     * location read = paint geometry only; live dragging grows the panel rows with the finger,
     * so this edge and the handle ride it 1:1 while the scrim stays exactly as tall as the
     * keyboard block. No headroom, no full-window sheet.
     */
    private int keyboardTopY() {
        // THE content top: the min window-top of the real keyboard chrome (toolbar, its scroll host,
        // the keys view). Leading dead strips inside the panel (voice panel, folded headers, spacers)
        // are NOT content — no grey band is painted above the toolbar any more, and because this scan
        // lives entirely in THIS file, even a stale setTargetView elsewhere cannot drag the handle
        // back down onto the keys row.
        View scope = null;
        if (getParent() instanceof View) {
            View root = (View) getParent();
            scope = root.findViewById(R.id.main_keyboard_frame);
            if (scope == null) {
                scope = root;
            }
        }
        int self = windowTopOf(this);
        int best = -1;
        if (scope != null) {
            int[] probes = { R.id.tool_bar, R.id.tool_bar_scroll, R.id.keyboard };
            for (int id : probes) {
                View v = scope.findViewById(id);
                if (v != null && v.getVisibility() == View.VISIBLE && v.getHeight() > 0) {
                    int t = windowTopOf(v) - self;
                    if (best < 0 || t < best) {
                        best = t;
                    }
                }
            }
        }
        if (best < 0) {
            best =
                targetView != null
                ? windowTopOf(targetView) - self
                : getHeight() - currentBlockHeightPx();
        }
        return Math.max(0, best);
    }
    private int windowTopOf(View v) {
        int[] p = new int[2];
        v.getLocationInWindow(p);
        return p[1];
    }

    /** Y of the ghost edge for the current drag (identity while not dragging: the block's top). */
    private float ghostTopY() {
        // the ball's CENTRE is clamped one radius inside the scrim, so it always rides the panel's
        // top edge fully visible (reference image 2) — no half-ball floating outside the sheet
        float edge = dp(2.5f);
        float minY = dp(GRIP_RADIUS_DP) + edge / 2f;
        float top = keyboardTopY();
        if (top < minY) {
            top = minY;
        }
        if (!dragging) {
            return top;
        }
        float deltaPx = (pendingRowHeightDp() - startRowHeightDp) * rowCount
            * getResources().getDisplayMetrics().density;
        float y = top - deltaPx;
        if (y < minY) {
            y = minY;
        }
        if (y > (float) getHeight() - dp(24f)) {
            y = (float) getHeight() - dp(24f);
        }
        return y;
    }

    /** Painting and hit-testing share this: the handle rides the ghost edge, capsule stays put. */
    private void computeAnchors() {
        float width = getWidth();
        float bottom = getHeight();
        float edge = dp(2.5f);
        // frame + dim ride the CONTENT top (toolbar line), never the raw view top: the empty strips
        // above it stay fully transparent — the "grey band above the keyboard" cannot exist.
        float contentTop = Math.min((float) keyboardTopY(), bottom - dp(60f));
        if (contentTop < edge / 2f) {
            contentTop = edge / 2f;
        }
        keyboardFrame.set(edge / 2f, contentTop, width - edge / 2f, bottom - edge / 2f);
        float ghostY = ghostTopY();
        float cx = width / 2f;
        float gripR = dp(GRIP_RADIUS_DP);
        float gripCy = ghostY;
        topGrip.set(cx - gripR, gripCy - gripR, cx + gripR, gripCy + gripR);
        float barW = Math.min(dp(HANDLE_BAR_WIDTH_DP), width * 0.5f);
        float barH = dp(HANDLE_BAR_HEIGHT_DP);
        handleBar.set(cx - barW / 2f, gripCy - barH / 2f, cx + barW / 2f, gripCy + barH / 2f);
        float capsuleW = Math.min(dp(CAPSULE_WIDTH_DP), width * 0.66f);
        float capsuleH = dp(CAPSULE_HEIGHT_DP);
        float capsuleBottom = bottom - dp(16f);
        capsule.set(cx - capsuleW / 2f, capsuleBottom - capsuleH, cx + capsuleW / 2f, capsuleBottom);
        float iconR = capsuleH / 2f - dp(2f);
        float iconCy = capsule.centerY();
        confirmButton.set(
            cx + capsuleW / 2f - capsuleH / 2f - iconR,
            iconCy - iconR,
            cx + capsuleW / 2f - capsuleH / 2f + iconR,
            iconCy + iconR);
        resetButton.set(
            cx - capsuleW / 2f + capsuleH / 2f - iconR,
            iconCy - iconR,
            cx - capsuleW / 2f + capsuleH / 2f + iconR,
            iconCy + iconR);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        computeAnchors();
        float width = getWidth();
        float bottom = getHeight();
        float radius = dp(18f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(BACKDROP_BLACK);
        canvas.drawRoundRect(0f, keyboardFrame.top, width, bottom, radius, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.5f));
        paint.setColor(GRIP_BLUE);
        canvas.drawRoundRect(keyboardFrame, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        if (dragging) {
            // the dashed guide the finger rides: where the keyboard's top edge will land on release
            paint.setStrokeWidth(dp(1.5f));
            paint.setColor(GRIP_BLUE);
            paint.setAlpha(200);
            paint.setPathEffect(GHOST_DASH);
            canvas.drawLine(dp(10f), topGrip.centerY(), width - dp(10f), topGrip.centerY(), paint);
            paint.setPathEffect(null);
            paint.setAlpha(255);
        }

        drawHandleBar(canvas);
        drawGrip(canvas);
        drawActionCapsule(canvas);
    }

    private void drawHandleBar(Canvas canvas) {
        float r = handleBar.height() / 2f;
        paint.setColor(dragging ? GRIP_BLUE : 0xffffffff);
        paint.setAlpha(dragging ? 235 : 200);
        canvas.drawRoundRect(handleBar, r, r, paint);
        paint.setAlpha(255);
    }

    /** The up/down drag image, welded to the handle center (PNG asset + vector fallback). */
    private void drawGrip(Canvas canvas) {
        gripDst.set(topGrip);
        if (gripBitmap != null && !gripBitmap.isRecycled()) {
            paint.setFilterBitmap(true);
            canvas.drawBitmap(gripBitmap, null, gripDst, paint);
            paint.setFilterBitmap(false);
            return;
        }
        paint.setColor(GRIP_BLUE);
        canvas.drawCircle(topGrip.centerX(), topGrip.centerY(), topGrip.width() / 2f, paint);
        paint.setColor(0xffffffff);
        float cx = topGrip.centerX();
        float cy = topGrip.centerY();
        float w = topGrip.width() * 0.36f;
        arrow.reset();
        arrow.moveTo(cx, cy - w);
        arrow.lineTo(cx - w * 0.85f, cy - w * 0.06f);
        arrow.lineTo(cx + w * 0.85f, cy - w * 0.06f);
        arrow.close();
        canvas.drawPath(arrow, paint);
        arrow.reset();
        arrow.moveTo(cx, cy + w);
        arrow.lineTo(cx - w * 0.85f, cy + w * 0.06f);
        arrow.lineTo(cx + w * 0.85f, cy + w * 0.06f);
        arrow.close();
        canvas.drawPath(arrow, paint);
    }

    private void drawActionCapsule(Canvas canvas) {
        float r = capsule.height() / 2f;
        paint.setColor(0x59000000);
        canvas.drawRoundRect(
            capsule.left + dp(1.5f),
            capsule.top + dp(2.5f),
            capsule.right + dp(1.5f),
            capsule.bottom + dp(2.5f),
            r,
            r,
            paint);
        paint.setColor(0xffffffff);
        canvas.drawRoundRect(capsule, r, r, paint);
        paint.setColor(ICON_DARK);
        canvas.drawLine(capsule.centerX(), capsule.top + dp(6f), capsule.centerX(),
                        capsule.bottom - dp(6f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        float checkCx = confirmButton.centerX();
        float checkCy = confirmButton.centerY();
        canvas.drawLine(checkCx - dp(8f), checkCy, checkCx - dp(2f), checkCy + dp(7f), paint);
        canvas.drawLine(
            checkCx - dp(2f), checkCy + dp(7f), checkCx + dp(10f), checkCy - dp(8f), paint);

        float resetCx = resetButton.centerX();
        float resetCy = resetButton.centerY();
        float arcR = dp(8.5f);
        iconArc.set(resetCx - arcR, resetCy - arcR, resetCx + arcR, resetCy + arcR);
        canvas.drawArc(iconArc, 55f, 275f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        arrow.reset();
        float hx = resetCx - arcR * 0.55f;
        float hy = resetCy - arcR * 1.02f;
        arrow.moveTo(hx, hy);
        arrow.lineTo(hx + dp(6.5f), hy - dp(3.5f));
        arrow.lineTo(hx + dp(6.5f), hy + dp(3.5f));
        arrow.close();
        canvas.drawPath(arrow, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private float rawY(MotionEvent event, int pointerIndex) {
        return event.getRawY() + event.getY(pointerIndex) - event.getY();
    }

    private boolean hitCircle(RectF rect, float x, float y, float slop) {
        float dx = x - rect.centerX();
        float dy = y - rect.centerY();
        float r = rect.width() / 2f + slop;
        return dx * dx + dy * dy <= r * r;
    }

    /**
     * THE OnTouchListener body (installed in initialize()). This is the whole feature's touch
     * model in one switch statement — the request's literal "عبر OnTouchListener".
     */


    private boolean handleTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            computeAnchors();
            float x = event.getX();
            float y = event.getY();
            if (hitCircle(confirmButton, x, y, dp(4f))) {
                //الحفظ
                if (callback != null) {
                    callback.onConfirm();
                }
                return true;
            }
            if (hitCircle(resetButton, x, y, dp(4f))) {
                //اعادة التعيين
                dragging = false;
                dragDyPx = 0f;
                startRowHeightDp = KeyboardResizeModel.saveRowHeightDp(
                    getContext(), KeyboardResizeModel.DEFAULT_KEY_ROW_DP);
                if (callback != null) {
                    callback.onResizeCommitted(KeyboardResizeModel.DEFAULT_KEY_ROW_DP);
                    callback.onReset();
                }
                invalidate();
                return true;
            }

            if (hitCircle(topGrip, x, y, dp(22f))) {
                //بدء السحب
                beginDrag(event);
                startY = event.getY();
                // حفظ نقطة البداية للمحور Y عند بدء السحب
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && dragging) {
            int pointerIndex = event.findPointerIndex(activePointerId);
            if (pointerIndex >= 0) {
                float currentY = event.getY(pointerIndex);
                float deltaY = currentY - startY; // حساب الفارق لمعرفة الاتجاه

                // --- عزل السحب لأعلى عن السحب لأسفل ---
                if (deltaY < 0) {
                    endY=deltaY;
                    // ⬆️ تم السحب لأعلى (الإصبع يتحرك للأعلى، Y ينقص)
                    // يمكنك وضع كود خاص بالسحب لأعلى هنا إذا أردت
                    // handleDragUp(Math.abs(deltaY));
                    toast("لاعلى");
                } else if (deltaY > 0) {
                    endY=deltaY;
                    toast("لاسفل");
                    // ⬇️ تم السحب لأسفل (الإصبع يتحرك للأسفل، Y يزداد)
                    // يمكنك وضع كود خاص بالسحب لأسفل هنا إذا أردت
                    // handleDragDown(deltaY);
                }
                // -------------------------------------
                trackGhost(event, pointerIndex);
                removeCallbacks(liveRowStep);
                postOnAnimation(liveRowStep);

            }
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP
            && dragging
            && event.getPointerId(event.getActionIndex()) == activePointerId) {
            removeCallbacks(liveRowStep);
            commitDrag();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (dragging) {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex >= 0) {
                    trackGhost(event, pointerIndex);
                }
                removeCallbacks(liveRowStep);
                commitDrag();
            }
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            // A cancelled touch drops the WHOLE preview: nothing was applied, nothing was saved.
            removeCallbacks(liveRowStep);
            dragging = false;
            activePointerId = -1;
            dragDyPx = 0f;
            // the sheet rolls the LIVE height back to where the drag started; nothing was saved
            if (callback != null) {
                callback.onLiveRowHeight(startRowHeightDp);
            }
            lastReportedRowDp = startRowHeightDp;
            invalidate();
        }

        return true;
    }
    public void toast(final String str) {
        if (str == null || str.isEmpty()) return;
        // ضمان عرض التوست حتى لو تم استدعاؤها من Background Thread
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    android.widget.Toast.makeText(getContext(), str, android.widget.Toast.LENGTH_SHORT).show();
                }
            });
    }


     /*
    private boolean handleTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            computeAnchors();
            float x = event.getX();
            float y = event.getY();
            if (hitCircle(confirmButton, x, y, dp(4f))) {
                if (callback != null) {
                    callback.onConfirm();
                }
                return true;
            }
            if (hitCircle(resetButton, x, y, dp(4f))) {
                dragging = false;
                dragDyPx = 0f;
                startRowHeightDp = KeyboardResizeModel.saveRowHeightDp(
                    getContext(), KeyboardResizeModel.DEFAULT_KEY_ROW_DP);
                if (callback != null) {
                    callback.onResizeCommitted(KeyboardResizeModel.DEFAULT_KEY_ROW_DP);
                    callback.onReset();
                }
                invalidate();
                return true;
            }
            if (hitCircle(topGrip, x, y, dp(22f))) {
                beginDrag(event);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && dragging) {
            int pointerIndex = event.findPointerIndex(activePointerId);
            if (pointerIndex >= 0) {
                trackGhost(event, pointerIndex);
                removeCallbacks(liveRowStep);
                postOnAnimation(liveRowStep);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP
            && dragging
            && event.getPointerId(event.getActionIndex()) == activePointerId) {
            removeCallbacks(liveRowStep);
            commitDrag();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (dragging) {
                int pointerIndex = event.findPointerIndex(activePointerId);
                if (pointerIndex >= 0) {
                    trackGhost(event, pointerIndex);
                }
                removeCallbacks(liveRowStep);
                commitDrag();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            // A cancelled touch drops the WHOLE preview: nothing was applied, nothing was saved.
            removeCallbacks(liveRowStep);
            dragging = false;
            activePointerId = -1;
            dragDyPx = 0f;
            // the sheet rolls the LIVE height back to where the drag started; nothing was saved
            if (callback != null) {
                callback.onLiveRowHeight(startRowHeightDp);
            }
            lastReportedRowDp = startRowHeightDp;
            invalidate();
        }
        return true;
    }*/

    /** Drag start: remember where we started, paint only. The keyboard is NOT touched. */
    private void beginDrag(MotionEvent event) {
        dragging = true;
        activePointerId = event.getPointerId(0);
        lastRawYpx = rawY(event, 0);
        dragDyPx = 0f;
        lastReportedRowDp = startRowHeightDp;
        invalidate();
    }

    /**
     * The paint half of a MOVE step: two floats and invalidate on THIS view only — no layout, no
     * window work, nothing that could jitter. The elastic size update is the SEPARATE coalesced
     * reportLiveRow() hand-off, which the runtime funnels through onComputeInsets so the editor
     * animates while the keyboard box stays exactly the rows formula (zero band, zero clipping).
     */
    private void trackGhost(MotionEvent event, int pointerIndex) {
        float raw = rawY(event, pointerIndex);
        dragDyPx += raw - lastRawYpx;
        lastRawYpx = raw;
        invalidate();
    }

    /**
     * The live elastic hand-off: identical pending values are coalesced (the keyboard's own
     * setter also dedups sub-0.05dp noise), so the editor never gets spurious relayouts. The
     * finger step rides the SAME function the release commits — no drift by construction.
     */
    private void reportLiveRow() {
        float pending = pendingRowHeightDp();
        if (pending == lastReportedRowDp) {
            return;
        }
        lastReportedRowDp = pending;
        if (callback != null) {
            callback.onLiveRowHeight(pending);
        }
    }


    /**
     * ACTION_UP: compute the final height ONCE through the same function the live steps rode,
     * persist it to SharedPreferences, and hand it to the runtime — which fixes the size through
     * the official insets channel (AlmlkImeRuntimeBase.onComputeInsets + updateInputViewShown).
     */
    private void commitDrag() {
        if (!dragging) {
            return;
        }
        dragging = false;
        activePointerId = -1;
        float pending = KeyboardResizeModel.saveRowHeightDp(getContext(), pendingRowHeightDp());
        dragDyPx = 0f;
        startRowHeightDp = pending;
        lastReportedRowDp = pending;
        if (callback != null) {
            requestLayout();
            callback.onResizeCommitted(pending);
        }
        invalidate();
    }
}

/*
public final class KeyboardResizeOverlay extends View {
  public interface Callback {
    void onResizeCommitted(float rowHeightDp);
    void onLiveRowHeight(float rowHeightDp);
    void onConfirm();
    void onReset();
  }

  private static final float GRIP_RADIUS_DP = 23f;
  private static final float HANDLE_BAR_WIDTH_DP = 132f;
  private static final float HANDLE_BAR_HEIGHT_DP = 8f;
  private static final float CAPSULE_WIDTH_DP = 150f;
  private static final float CAPSULE_HEIGHT_DP = 56f;
  private static final int GRIP_BLUE = 0xff2e8bff;
  private static final int BACKDROP_BLACK = 0x21000000;   // the 13% translucent dim
  private static final int ICON_DARK = 0xff243043;
  private static final DashPathEffect GHOST_DASH =
      new DashPathEffect(new float[] {12f, 10f}, 0f);

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path arrow = new Path();
  private final RectF capsule = new RectF();
  private final RectF resetButton = new RectF();
  private final RectF confirmButton = new RectF();
  private final RectF keyboardFrame = new RectF();
  private final RectF iconArc = new RectF();
  private final RectF topGrip = new RectF();
  private final RectF gripDst = new RectF();
  private final RectF handleBar = new RectF();
  private final int[] locTarget = new int[2];
  private final int[] locSelf = new int[2];
  private Bitmap gripBitmap;
  private Callback callback;
  private View targetView;
  private KeyboardTheme theme = KeyboardTheme.from("light");
  private final View.OnLayoutChangeListener repaintOnLayout =
      new View.OnLayoutChangeListener() {
        public void onLayoutChange(
            View view,
            int left,
            int top,
            int right,
            int bottom,
            int oldLeft,
            int oldTop,
            int oldRight,
            int oldBottom) {
          invalidate();
        }
      };

  // ---- drag state (live rows through the insets channel; persistence at the release) ----
  private boolean dragging;
  private int activePointerId = -1;
  private float startRowHeightDp = KeyboardResizeModel.DEFAULT_KEY_ROW_DP;
  private int rowCount = 4;
  private float dragDyPx;
  private float lastRawYpx;
  private float dragStartRawYpx;
  private float lastReportedRowDp = Float.NaN;

  private final Runnable liveRowStep =
      new Runnable() {
        public void run() {
          if (dragging) {
            reportLiveRow();
          }
        }
      };

  public KeyboardResizeOverlay(Context context) {
    super(context);
    initialize();
  }

  public KeyboardResizeOverlay(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  private void initialize() {
    setClickable(true);
    setFocusable(true);
    // explicit empty backdrop: no default drawable, no Overdraw behind the dimmer
    setBackgroundColor(0x00000000);
    try {
      gripBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.kbd_resize_grip);
    } catch (Throwable ignored) {
      gripBitmap = null;
    }
    // The whole touch model of this feature is one OnTouchListener, as requested — plain
    // Java, no Kotlin, no helper libraries.
    setOnTouchListener(new View.OnTouchListener() {
      public boolean onTouch(View view, MotionEvent event) {
        return handleTouch(event);
      }
    });
  }

  public void setCallback(Callback value) {
    callback = value;
  }

  public void setTheme(KeyboardTheme value) {
    if (value != null) {
      theme = value;
      invalidate();
    }
  }


  public void setTargetView(View value) {
    if (targetView == value) {
      return;
    }
    if (targetView != null) {
      targetView.removeOnLayoutChangeListener(repaintOnLayout);
    }
    targetView = value;
    if (targetView != null) {
      targetView.addOnLayoutChangeListener(repaintOnLayout);
    }
    invalidate();
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (getParent() instanceof android.view.ViewGroup) {
      ((android.view.ViewGroup) getParent()).addOnLayoutChangeListener(repaintOnLayout);
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (getParent() instanceof android.view.ViewGroup) {
      ((android.view.ViewGroup) getParent()).removeOnLayoutChangeListener(repaintOnLayout);
    }
  }


  public void show(float currentRowHeightDp, int referenceRows) {
    startRowHeightDp = KeyboardResizeModel.clampRowHeightDp(currentRowHeightDp);
    rowCount = referenceRows > 0 ? referenceRows : 1;
    dragging = false;
    activePointerId = -1;
    dragDyPx = 0f;
    lastReportedRowDp = startRowHeightDp;
    setVisibility(VISIBLE);
    bringToFront();
    requestFocus();
    requestLayout();
    invalidate();
  }

  public void releaseTransientState() {
    dragging = false;
    activePointerId = -1;
    dragDyPx = 0f;
    clearAnimation();
    setVisibility(GONE);
    requestLayout();
  }


  private int currentBlockHeightPx() {
    return getHeight() > 0
        ? getHeight()
        : (int) (dp(KeyboardResizeModel.DEFAULT_KEY_ROW_DP) * 4f + dp(110f));
  }


  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = getMeasuredWidth();
    // prefer the PANEL from the tree over targetView so stale external wiring (e.g. a project
    // still pointing at the keys view only) can never shrink the sheet below the keyboard chrome
    View panel = null;
    if (getParent() instanceof android.view.ViewGroup) {
      panel = ((android.view.ViewGroup) getParent()).findViewById(R.id.main_keyboard_frame);
    }
    if (panel == null) {
      panel = targetView;
    }
    int height = 0;
    if (panel != null && panel.getVisibility() == View.VISIBLE) {
      height = panel.getMeasuredHeight();
    }
    setMeasuredDimension(width, height);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }



  private float pendingRowHeightDp() {
    return KeyboardResizeModel.pendingRowHeightDp(
        startRowHeightDp, dragDyPx, rowCount, getResources().getDisplayMetrics().density);
  }


  private int keyboardTopY() {
    // THE content top: the min window-top of the real keyboard chrome (toolbar, its scroll host,
    // the keys view). Leading dead strips inside the panel (voice panel, folded headers, spacers)
    // are NOT content — no grey band is painted above the toolbar any more, and because this scan
    // lives entirely in THIS file, even a stale setTargetView elsewhere cannot drag the handle
    // back down onto the keys row.
    View scope = null;
    if (getParent() instanceof View) {
      View root = (View) getParent();
      scope = root.findViewById(R.id.main_keyboard_frame);
      if (scope == null) {
        scope = root;
      }
    }
    int self = windowTopOf(this);
    int best = -1;
    if (scope != null) {
      int[] probes = { R.id.tool_bar, R.id.tool_bar_scroll, R.id.keyboard };
      for (int id : probes) {
        View v = scope.findViewById(id);
        if (v != null && v.getVisibility() == View.VISIBLE && v.getHeight() > 0) {
          int t = windowTopOf(v) - self;
          if (best < 0 || t < best) {
            best = t;
          }
        }
      }
    }
    if (best < 0) {
      best =
          targetView != null
              ? windowTopOf(targetView) - self
              : getHeight() - currentBlockHeightPx();
    }
    return Math.max(0, best);
  }

  private int windowTopOf(View v) {
    int[] p = new int[2];
    v.getLocationInWindow(p);
    return p[1];
  }


  private float ghostTopY() {
    float edge = dp(2.5f);
    float minY = dp(GRIP_RADIUS_DP) + edge / 2f;
    float y = keyboardTopY();
    if (y < minY) y = minY;
    if (y > (float) getHeight() - dp(24f)) y = (float) getHeight() - dp(24f);
    return y;
  }


  private void computeAnchors() {
    float width = getWidth();
    float bottom = getHeight();
    float edge = dp(2.5f);
    // frame + dim ride the CONTENT top (toolbar line), never the raw view top: the empty strips
    // above it stay fully transparent — the "grey band above the keyboard" cannot exist.
    float contentTop = Math.min((float) keyboardTopY(), bottom - dp(60f));
    if (contentTop < edge / 2f) {
      contentTop = edge / 2f;
    }
    keyboardFrame.set(edge / 2f, contentTop, width - edge / 2f, bottom - edge / 2f);
    float ghostY = ghostTopY();
    float cx = width / 2f;
    float gripR = dp(GRIP_RADIUS_DP);
    float gripCy = ghostY;
    topGrip.set(cx - gripR, gripCy - gripR, cx + gripR, gripCy + gripR);
    float barW = Math.min(dp(HANDLE_BAR_WIDTH_DP), width * 0.5f);
    float barH = dp(HANDLE_BAR_HEIGHT_DP);
    handleBar.set(cx - barW / 2f, gripCy - barH / 2f, cx + barW / 2f, gripCy + barH / 2f);
    float capsuleW = Math.min(dp(CAPSULE_WIDTH_DP), width * 0.66f);
    float capsuleH = dp(CAPSULE_HEIGHT_DP);
    float capsuleBottom = bottom - dp(16f);
    capsule.set(cx - capsuleW / 2f, capsuleBottom - capsuleH, cx + capsuleW / 2f, capsuleBottom);
    float iconR = capsuleH / 2f - dp(2f);
    float iconCy = capsule.centerY();
    confirmButton.set(
        cx + capsuleW / 2f - capsuleH / 2f - iconR,
        iconCy - iconR,
        cx + capsuleW / 2f - capsuleH / 2f + iconR,
        iconCy + iconR);
    resetButton.set(
        cx - capsuleW / 2f + capsuleH / 2f - iconR,
        iconCy - iconR,
        cx - capsuleW / 2f + capsuleH / 2f + iconR,
        iconCy + iconR);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    computeAnchors();
    float width = getWidth();
    float bottom = getHeight();
    float radius = dp(18f);

    // Round 75: واجهة تغيير الحجم أصبحت خفيفة جداً: لا طبقة تعتيم، لا إطار أزرق،
    // لا كبسولة تأكيد/إلغاء. فقط مقبض السحب، حتى لا تظهر أي واجهات فوق الكيبورد
    // أثناء السحب للأسفل أو الأعلى.
    paint.setStyle(Paint.Style.FILL);
    drawHandleBar(canvas);
    drawGrip(canvas);
  }

  private void drawHandleBar(Canvas canvas) {
    float r = handleBar.height() / 2f;
    paint.setColor(dragging ? GRIP_BLUE : 0xffffffff);
    paint.setAlpha(dragging ? 235 : 200);
    canvas.drawRoundRect(handleBar, r, r, paint);
    paint.setAlpha(255);
  }


  private void drawGrip(Canvas canvas) {
    gripDst.set(topGrip);
    if (gripBitmap != null && !gripBitmap.isRecycled()) {
      paint.setFilterBitmap(true);
      canvas.drawBitmap(gripBitmap, null, gripDst, paint);
      paint.setFilterBitmap(false);
      return;
    }
    paint.setColor(GRIP_BLUE);
    canvas.drawCircle(topGrip.centerX(), topGrip.centerY(), topGrip.width() / 2f, paint);
    paint.setColor(0xffffffff);
    float cx = topGrip.centerX();
    float cy = topGrip.centerY();
    float w = topGrip.width() * 0.36f;
    arrow.reset();
    arrow.moveTo(cx, cy - w);
    arrow.lineTo(cx - w * 0.85f, cy - w * 0.06f);
    arrow.lineTo(cx + w * 0.85f, cy - w * 0.06f);
    arrow.close();
    canvas.drawPath(arrow, paint);
    arrow.reset();
    arrow.moveTo(cx, cy + w);
    arrow.lineTo(cx - w * 0.85f, cy + w * 0.06f);
    arrow.lineTo(cx + w * 0.85f, cy + w * 0.06f);
    arrow.close();
    canvas.drawPath(arrow, paint);
  }

  private void drawActionCapsule(Canvas canvas) {
    float r = capsule.height() / 2f;
    paint.setColor(0x59000000);
    canvas.drawRoundRect(
        capsule.left + dp(1.5f),
        capsule.top + dp(2.5f),
        capsule.right + dp(1.5f),
        capsule.bottom + dp(2.5f),
        r,
        r,
        paint);
    paint.setColor(0xffffffff);
    canvas.drawRoundRect(capsule, r, r, paint);
    paint.setColor(ICON_DARK);
    canvas.drawLine(capsule.centerX(), capsule.top + dp(6f), capsule.centerX(),
        capsule.bottom - dp(6f), paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(3f));
    paint.setStrokeCap(Paint.Cap.ROUND);
    float checkCx = confirmButton.centerX();
    float checkCy = confirmButton.centerY();
    canvas.drawLine(checkCx - dp(8f), checkCy, checkCx - dp(2f), checkCy + dp(7f), paint);
    canvas.drawLine(
        checkCx - dp(2f), checkCy + dp(7f), checkCx + dp(10f), checkCy - dp(8f), paint);

    float resetCx = resetButton.centerX();
    float resetCy = resetButton.centerY();
    float arcR = dp(8.5f);
    iconArc.set(resetCx - arcR, resetCy - arcR, resetCx + arcR, resetCy + arcR);
    canvas.drawArc(iconArc, 55f, 275f, false, paint);
    paint.setStyle(Paint.Style.FILL);
    arrow.reset();
    float hx = resetCx - arcR * 0.55f;
    float hy = resetCy - arcR * 1.02f;
    arrow.moveTo(hx, hy);
    arrow.lineTo(hx + dp(6.5f), hy - dp(3.5f));
    arrow.lineTo(hx + dp(6.5f), hy + dp(3.5f));
    arrow.close();
    canvas.drawPath(arrow, paint);
    paint.setStrokeCap(Paint.Cap.BUTT);
  }

  private float rawY(MotionEvent event, int pointerIndex) {
    return event.getRawY() + event.getY(pointerIndex) - event.getY();
  }

  private boolean hitCircle(RectF rect, float x, float y, float slop) {
    float dx = x - rect.centerX();
    float dy = y - rect.centerY();
    float r = rect.width() / 2f + slop;
    return dx * dx + dy * dy <= r * r;
  }


  private boolean handleTouch(MotionEvent event) {
    int action = event.getActionMasked();
    if (action == MotionEvent.ACTION_DOWN) {
      computeAnchors();
      float x = event.getX();
      float y = event.getY();
      if (hitCircle(topGrip, x, y, dp(26f)) || handleBar.contains(x, y)) {
        beginDrag(event);
      } else {
        // لا توجد أزرار تأكيد/إلغاء بعد الآن: لمسة خارج المقبض تغلق وضع تغيير الحجم.
        if (callback != null) callback.onConfirm();
      }
      return true;
    }
    if (action == MotionEvent.ACTION_MOVE && dragging) {
      int pointerIndex = event.findPointerIndex(activePointerId);
      if (pointerIndex >= 0) {
        trackGhost(event, pointerIndex);
        reportLiveRow(); // مباشر وسريع؛ والدالة نفسها تمنع الضجيج والاهتزاز.
      }
      return true;
    }
    if (action == MotionEvent.ACTION_POINTER_UP
        && dragging
        && event.getPointerId(event.getActionIndex()) == activePointerId) {
      removeCallbacks(liveRowStep);
      commitDrag();
      return true;
    }
    if (action == MotionEvent.ACTION_UP) {
      if (dragging) {
        int pointerIndex = event.findPointerIndex(activePointerId);
        if (pointerIndex >= 0) {
          trackGhost(event, pointerIndex);
        }
        removeCallbacks(liveRowStep);
        commitDrag();
      }
      return true;
    }
    if (action == MotionEvent.ACTION_CANCEL) {
      // A cancelled touch drops the WHOLE preview: nothing was applied, nothing was saved.
      removeCallbacks(liveRowStep);
      dragging = false;
      activePointerId = -1;
      dragDyPx = 0f;
      // the sheet rolls the LIVE height back to where the drag started; nothing was saved
      if (callback != null) {
        callback.onLiveRowHeight(startRowHeightDp);
      }
      lastReportedRowDp = startRowHeightDp;
      invalidate();
    }
    return true;
  }


  private void beginDrag(MotionEvent event) {
    dragging = true;
    activePointerId = event.getPointerId(0);
    lastRawYpx = rawY(event, 0);
    dragStartRawYpx = lastRawYpx;
    dragDyPx = 0f;
    lastReportedRowDp = startRowHeightDp;
    invalidate();
  }


  private void trackGhost(MotionEvent event, int pointerIndex) {
    float raw = rawY(event, pointerIndex);
    // Round 75: نعتمد المسافة الكلية من بداية السحب لا جمع الفروق الصغيرة؛
    // هذا يلغي تراكم الضجيج ويجعل الحركة ناعمة بلا اهتزاز أو drift.
    dragDyPx = raw - dragStartRawYpx;
    lastRawYpx = raw;
    invalidate();
  }


  private void reportLiveRow() {
    float pending = pendingRowHeightDp();
    if (!Float.isNaN(lastReportedRowDp) && Math.abs(pending - lastReportedRowDp) < 0.08f) {
      return;
    }
    lastReportedRowDp = pending;
    if (callback != null) {
      callback.onLiveRowHeight(pending);
    }
  }

  private void commitDrag() {
    if (!dragging) {
      return;
    }
    dragging = false;
    activePointerId = -1;
    float pending = KeyboardResizeModel.saveRowHeightDp(getContext(), pendingRowHeightDp());
    dragDyPx = 0f;
    startRowHeightDp = pending;
    lastReportedRowDp = pending;
    if (callback != null) {
      callback.onResizeCommitted(pending);
      requestLayout();
      // حفظ وإغلاق فور الإفلات: بلا أزرار/نوافذ إضافية.
    }
    invalidate();
  }
}*/
