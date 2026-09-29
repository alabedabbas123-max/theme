"""The resize interface laws that survived the rebuild (jitter-free drag, live keys, XML rules).

Round-25 rebuilt resizing around the user's own four-point request (preview-while-dragging,
apply-on-release, SharedPreferences, hard bounds). These are the still-binding laws on top of
that: the panel is a 13% dim over LIVE keys, the scrim mirrors the block instead of inflating it,
the root never goes transparent, onDraw allocates nothing, and controls are icons only.
"""
import os
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
RES = ROOT / "app/src/main/res"


class DimResizePanelTest(unittest.TestCase):
  def overlay(self):
    return (JAVA / "ime/KeyboardResizeOverlay.java").read_text(encoding="utf-8")

  def test_translucent_13_percent_backdrop_over_live_keys(self):
    # round 39-1: أبقى المستخدم على الستارة الشفافة — المفاتيح تبقى مرئية تحت التعتيم
    src = self.overlay()
    self.assertIn("BACKDROP_BLACK = 0x21000000;", src)
    self.assertIn("paint.setColor(BACKDROP_BLACK);", src)
    self.assertNotIn("SCRIM_FALLBACK", src)
    self.assertNotIn("scrimColor", src)
    self.assertIn("canvas.drawRoundRect(0f, keyboardFrame.top, width, bottom, radius, radius,"
                  " paint);", src)
    self.assertIn("keyboardFrame.set(edge / 2f, contentTop, width - edge / 2f,", src)
    # the rect IS the mirror of the panel: the overlay never claims more than the block's height
    self.assertNotIn("getSize(heightMeasureSpec)", src)
    self.assertNotIn("fullSheet", src)
    self.assertIn("setBackgroundColor(0x00000000);", src)  # no overdraw layer

  def test_drag_repaints_the_scrim_only(self):
    # THE anti-jitter law: during MOVE nothing but this view's pixels change. The keyboard view
    # is never touched mid-drag — one layout pass happens at release via the callback.
    src = self.overlay()
    move = src[src.index("if (action == MotionEvent.ACTION_MOVE && dragging) {"):]
    move = move[: move.index("\n    }")]
    self.assertIn("trackGhost(event, pointerIndex);", move)
    self.assertNotIn("keyboard", move)
    for banned in ("requestLayout", "setKeyHeightDp", "onResizeApplied"):
      self.assertNotIn(banned, move, banned + " fired during MOVE")

  def test_release_is_the_single_apply_channel(self):
    # the runtime gets the height exactly once (from endGrab) — and ONLY from endGrab/reset;
    # no second path may set keyboard heights from the resize UI
    src = self.overlay()
    self.assertEqual(2, src.count("callback.onResizeCommitted("))  # release + reset
    part2a = (JAVA / "ime/AlmlkImeRuntimePart2A.java").read_text(encoding="utf-8")
    # live steps AND the release share the ONE row-metric setter (coalesced) — two sites, one
    # channel; persistence stays a single line in commitDrag/reset (model.saveRowHeightDp)
    self.assertEqual(2, part2a.count("keyboard.setKeyHeightDp(rowHeightDp);"))
    # round 30: the apply path runs through the OFFICIAL insets channel instead (user request)
    base = (JAVA / "ime/AlmlkImeRuntimeBase.java").read_text(encoding="utf-8")
    self.assertIn("updateInputViewShown();", base)
    self.assertIn("onComputeInsets", base)
    # and NO code anywhere writes sizes through LayoutParams anymore
    import subprocess
    hits = subprocess.run(["grep", "-rn", "-e", "setLayoutParams", "-e", "lp.height",
                           "app/src/main/java/com/almlk/swiftkey/ime"],
                          capture_output=True, text=True, cwd=str(ROOT)).stdout
    for line in hits.splitlines():
        # the toolbar/boot plumbing that pre-dates resizing may keep width wiring, never heights
        self.assertNotIn(".height", line, "LayoutParams height write survived: " + line)

  def test_single_measured_block_painting(self):
    # the painted frame rides the measured block directly — no paint-only glide model, no
    # smoothing clock; the ghost line is the SAME pending value the release will commit
    src = self.overlay()
    self.assertIn("private int currentBlockHeightPx() {", src)
    self.assertIn("return getHeight() > 0", src)
    self.assertIn("private float pendingRowHeightDp() {", src)
    self.assertIn("KeyboardResizeModel.pendingRowHeightDp(", src)
    for gone in ("frameModel", "0.35f", "MIN_HEIGHT_CHANGE", "emaHeight"):
      self.assertNotIn(gone, src)

  def test_window_follows_keyboard_without_pinning(self):
    # no size writes from the scrim: onMeasure exists ONLY to mirror the sibling block.
    # Comments are stripped first: the javadoc is ALLOWED to describe the runtime-side
    # LayoutParams recipe; the overlay's CODE must never write sizes itself.
    import re as _re
    src = self.overlay()
    code = _re.sub(r"/\*\*[\s\S]*?\*/", "", src)
    code = _re.sub(r"//[^\n]*", "", code)
    for banned in ("heightForCurrent", "measuredCapPx", "applyPanelHeight", "pinnedHeightPx",
                   "params.height", "getLayoutParams", "baseChromePx", "setKeyHeightDp",
                   "setLayoutParams"):
      self.assertNotIn(banned, code)
    # requestLayout may appear ONLY in show()/releaseTransientState(), never in a drag handler
    for m in ("beginDrag", "trackGhost", "commitDrag", "handleTouch"):
      i = code.index("private " + ("boolean " if m == "handleTouch" else "void ") + m)
      j = code.index("\n  }", i)
      self.assertNotIn("requestLayout", code[i:j], m + " relayouts mid-drag")
    self.assertIn("protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {", src)
    self.assertIn("height = panel.getMeasuredHeight();", src)  # panel-EXACT by construction
    self.assertNotIn("box.getChildAt", src)
    xml = (RES / "layout/ime_view.xml").read_text(encoding="utf-8")
    main = xml[xml.index("main_keyboard_frame"):]
    main = main[: main.index("/>")]
    self.assertIn('android:layout_gravity="bottom"', main)
    self.assertIn('android:layout_width="match_parent"', main)
    self.assertIn('android:layout_height="wrap_content"', main)
    tag = xml[xml.index("keyboard_resize_overlay"):]
    self.assertIn('android:layout_height="wrap_content"', tag)
    self.assertIn('android:layout_gravity="bottom"', tag)
    self.assertNotIn('android:layout_height="match_parent"', tag)
    frame = (RES / "layout/main_keyboard_frame.xml").read_text(encoding="utf-8")
    self.assertNotIn("KeyboardResizeOverlay", frame)   # one box: just the visible column
    part2b = (JAVA / "ime/AlmlkImeRuntimePart2B.java").read_text(encoding="utf-8")
    self.assertNotIn("inputRoot.setBackgroundColor(0x00000000);", part2b)

  def test_no_call_swallowed_by_a_comment(self):
    # regression guard: resizeOverlay.show(...) once vanished into a // comment while every
    # string needle stayed green — re-check the calls on comment-stripped source
    part2b = (JAVA / "ime/AlmlkImeRuntimePart2B.java").read_text(encoding="utf-8")
    code = re.sub(r"//[^\n]*", "", part2b)
    show = code[code.index("protected void showResizeOverlay()"):code.index("protected void hideResizeOverlay()")]
    self.assertIn("resizeOverlay.show(", show)
    hide = code[code.index("protected void hideResizeOverlay()"):]
    self.assertIn("resizeOverlay.releaseTransientState();", hide)
    for line in part2b.splitlines():
      stripped = line.strip()
      if stripped.startswith("//") and re.search(r"\w\.\w+\(", stripped):
        self.fail("executable code swallowed inside a comment: " + stripped)

  def test_real_keyboard_stays_live_under_the_dimmer(self):
    part2b = (JAVA / "ime/AlmlkImeRuntimePart2B.java").read_text(encoding="utf-8")
    show = part2b[part2b.index("protected void showResizeOverlay()"):part2b.index("protected void hideResizeOverlay()")]
    self.assertIn("standardKeyboardPanel.setVisibility(View.VISIBLE);", show)
    self.assertNotIn("standardKeyboardPanel.setVisibility(View.GONE)", show)
    self.assertNotIn("setToolbarVisibility(View.GONE);", show)
    hide = part2b[part2b.index("protected void hideResizeOverlay()"):]
    hide = hide[:hide.index("\n  }")]
    self.assertIn("if (resizeOverlay == null) return;", hide)
    self.assertIn("resizeOverlay.releaseTransientState();", hide)
    self.assertIn("keyboard.requestLayout();", hide)  # one-shot relayout at close, not per frame

  def test_draw_path_allocates_nothing(self):
    src = self.overlay()
    self.assertIn("private final RectF iconArc = new RectF();", src)
    self.assertIn("private final RectF handleBar = new RectF();", src)
    sig = {"onDraw": "protected void onDraw", "currentBlockHeightPx": "private int liveChrome",
           "ghostTopY": "private float ghostTopY", "keyboardTopY": "private int keyboardTopY"}
    for m in ("onDraw", "drawHandleBar", "drawGrip", "drawActionCapsule", "computeAnchors",
              "ghostTopY", "beginDrag", "trackGhost", "commitDrag", "keyboardTopY"):
      i = src.index(sig.get(m, "private void " + m))
      j = src.index("\n  }", i)
      self.assertNotIn("new ", src[i:j], "allocation inside " + m)
    self.assertNotIn("RectF arc =", src)
    # the dash effect is one static object, never rebuilt per frame
    self.assertEqual(1, src.count("new DashPathEffect"))

  def test_controls_grip_top_capsule_icons_only(self):
    src = self.overlay()
    self.assertIn("R.drawable.kbd_resize_grip", src)
    self.assertIn("canvas.drawBitmap(gripBitmap, null, gripDst, paint);", src)
    self.assertIn("float gripCy = ghostY;", src)     # handle welded to the (clamped) edge
    self.assertIn("hitCircle(topGrip, x, y, dp(22f))", src)
    self.assertIn("hitCircle(confirmButton, x, y, dp(4f))", src)
    self.assertIn("hitCircle(resetButton, x, y, dp(4f))", src)
    for gone in ("drawBottomTab", "BOTTOM_TAB", "bottomTab"):
      self.assertNotIn(gone, src)
    self.assertNotIn("\u0645\u0648\u0627\u0641\u0642", src)          # no text labels
    self.assertNotIn("\u0625\u0639\u0627\u062f\u0629 \u062a\u0639\u064a\u064a\u0646", src)

  def test_no_band_mechanism_survives_anywhere(self):
    # the round-28 "stretch the container" experiment is FULLY reverted: grepping the entire
    # source tree for its machinery must come up empty, and the overlay's MOVE handler must be
    # paint-only again (invalidate on this view, one switch, zero handoffs)
    import subprocess
    roots = "app/src/main"
    hits = []
    for tok in ("onPreviewResize", "syncPreviewGrow", "armKeyboardFrameGrowth",
                "setKeyboardFrameExtraHeightPx", "resetKeyboardFrameExtraHeight",
                "keyboardFrameView"):
        out = subprocess.run(["grep", "-rl", tok, roots], capture_output=True,
                             text=True, cwd=str(ROOT)).stdout.strip()
        if out:
            hits.append((tok, out))
    self.assertEqual([], hits)
    src = self.overlay()
    move = src[src.index("if (action == MotionEvent.ACTION_MOVE && dragging) {"):]
    move = move[: move.index("\n    }")]
    self.assertEqual(1, move.count("trackGhost(event, pointerIndex);"))
    self.assertNotIn("sync", move)
    self.assertNotIn("Grow", move)
    # keyboard-geometry reads for PAINT only must use window locations, never measure-based sizes
    self.assertIn("private int keyboardTopY() {", src)

  def test_no_legacy_model_left(self):
    src = self.overlay()
    for banned in ("baseChromePx", "baseKeyHeightDp", "rootHeight =", "committedChromePx",
                   "budgetPx", "lastContentHeightPx", "maxGrowPx", "gripInsetPx",
                   "buttonsStripPx", "BUTTONS_STRIP_DP", "moveGrip", "gripTop", "gripLeft",
                   "gripBottom", "dragHandleOffsetPx", "requestedHeadroomPx", "RESIZE_HEADROOM_DP",
                   "0xfff1244b", "topHandle", 'dp"', "dragHelper", "scrollHelper",
                   "frameModel", "LiveResizeGesture", "FrameAnchorModel", "updateDragHeight",
                   "processGrabMove", "drawBottomTab", "BOTTOM_TAB", "gestureDpBias",
                   "florisSnapshot", "heightPixels"):
      self.assertNotIn(banned, src)

  def test_overlay_bare_calls_all_resolve(self):
    src = self.overlay()
    self.assertIn("private float dp(float value) {", src)
    self.assertNotIn("private float clamp(", src)  # bounds live in the controller
    defined = set(re.findall(r"(?:private|public|protected)\s+(?:static\s+)?[\w.<>\[\]]+\s+(\w+)\s*\(", src))
    defined |= set(re.findall(r"\b(?:void|float|int|boolean|String)\s+(\w+)\s*\([^)]*\)\s*;", src))
    inherited = {"getWidth", "getHeight", "setVisibility", "bringToFront", "requestFocus",
                 "requestLayout", "invalidate", "clearAnimation", "getParent",
                 "getLocationInWindow",
                 "removeCallbacks", "postOnAnimation",
                 "setBackgroundColor", "setClickable", "setFocusable", "getResources",
                 "getRootView", "getContext", "isAttachedToWindow", "setMeasuredDimension",
                 "getMeasuredHeight", "getMeasuredWidth", "getChildCount", "getChildAt",
                 "getVisibility", "setOnTouchListener"}
    body = src[src.index("public final class"):]
    body = re.sub(r"/\*.*?\*/", " ", body, flags=re.S)
    body = re.sub(r"//[^\n]*", " ", body)
    for name in set(re.findall(r"(?<![\w.])([a-z]\w*)\s*\(", body)):
      if name in ("if", "for", "while", "return", "new", "catch", "super"):
        continue
      self.assertTrue(name in defined or name in inherited, "unresolved bare call: " + name)

  def test_rows_measure_equals_paint(self):
    # ONE source of truth in the keyboard view itself: onMeasure, getHeightReferenceRowCount and
    # onDraw all live by visibleRowCount() — the number the drag maps finger travel through
    src = (JAVA / "ime/SmartKeyboardView.java").read_text(encoding="utf-8")
    self.assertIn("int rows = visibleRowCount();", src)
    self.assertIn("rows * (configuredKeyHeight + configuredVerticalGap) + dp(8f)", src)
    self.assertIn("if (anchored && rows != blockAnchorRows) {", src)  # toggle re-slices, never grows
    self.assertIn("blockAnchorKeyPx = next;", src)            # resize owns the anchor
    self.assertIn("if (!anchored && layout != null && layout.rows != null", src)  # no stub-frame pin
    self.assertIn("boolean anchored = blockAnchorRows > 0", src)
    self.assertIn("return visibleRowCount();", src)
    self.assertIn("y + rowH - configuredVerticalGap", src)
    self.assertNotIn("referenceRows * configuredKeyHeight", src)


class ResizeWindowHygieneTest(unittest.TestCase):
  def test_resize_overlay_is_registered_exactly_once(self):
    xml = (RES / "layout/ime_view.xml").read_text(encoding="utf-8")
    self.assertEqual(1, xml.count("com.almlk.swiftkey.ime.KeyboardResizeOverlay"))

  def test_preview_files_are_not_app_code(self):
    # keyboard-preview/ is a design scratch folder; app builds must not reference it
    for base, dirs, files in os.walk(JAVA):
      for f in files:
        if f.endswith(".java"):
          self.assertNotIn("keyboard-preview", Path(base, f).read_text(encoding="utf-8"))


if __name__ == "__main__":
  unittest.main()
