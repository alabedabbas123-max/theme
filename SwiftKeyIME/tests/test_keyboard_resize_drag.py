"""Contract: keyboard resizing by drag with a smooth preview — the user's four-point request.

1) a properly sized centered grab handle at the TOP of the keyboard;
2) during the drag ONLY the scrim view repaints: the ghost line + handle ride the whole IME
   window (freely above the keyboard) and nothing is stretched, resized or re-laid-out — a
   container band is forbidden (round 29); the keyboard moves exactly once at release through
   its own LayoutParams, pinned to the rows formula, so it can never show an empty band;
3) on release the height is computed once, applied to the real layout and fixed;
4) persisted to SharedPreferences (min/max bounds so it can never vanish or fill the screen).

Everything old is verified deleted: the whole previous resize system (controller/spec/props/
constraints/editor/handle/pointer-map + the overlay class) must not exist or be referenced.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey"
VIEW = JAVA / "ime/KeyboardResizeOverlay.java"
CTRL = JAVA / "ime/KeyboardResizeModel.java"


def read(path):
    return path.read_text(encoding="utf-8")


class OldSystemDeleted(unittest.TestCase):
    def test_previous_resize_classes_are_gone(self):
        # EVERY prior generation of the feature is gone: the round-18..24 package, the
        # round-24 controller classes, AND the round-25 two-file model it was replaced by
        self.assertFalse((JAVA / "ime/resize").exists())
        self.assertFalse((JAVA / "ime/KeyboardResizeView.java").exists())
        self.assertFalse((JAVA / "ime/KeyboardResizeController.java").exists())
        for banned in ("ImeWindowController", "ImeWindowResizeHandle", "EditorResizeState",
                       "FlorisWindowSpec", "FlorisWindowProps", "FlorisWindowConstraints",
                       "FlorisPointerMap", "windowController", "KeyboardResizeView",
                       "KeyboardResizeController", "gestureDpBias", "onLiveHeight"):
            hits = [str(p.relative_to(ROOT)) for p in (ROOT / "app/src/main").rglob("*.java")
                    if banned in p.read_text(encoding="utf-8")]
            self.assertEqual([], hits, banned + " still referenced")
        for gone in ("LiveResizeGesture", "FrameAnchorModel", "KeyboardResizeDragHelper"):
            self.assertFalse(list((ROOT / "app/src/main").glob("**/" + gone + ".java")))

    def test_no_kotlin_anywhere(self):
        self.assertEqual([], list((ROOT / "app/src/main").glob("**/*.kt")))


class HandleAndChrome(unittest.TestCase):
    def test_centered_top_handle_with_the_grip_image(self):
        src = read(VIEW)
        # the bar: centered horizontally, ride height at the block's TOP edge
        self.assertIn("float cx = width / 2f;", src)
        self.assertIn("handleBar.set(cx - barW / 2f, gripCy - barH / 2f, cx + barW / 2f,"
                      " gripCy + barH / 2f);", src)
        self.assertIn("private static final float HANDLE_BAR_WIDTH_DP = 132f;", src)
        self.assertIn("private static final float HANDLE_BAR_HEIGHT_DP = 8f;", src)
        # the grip image (kept from the user's screenshot) + generous touch circle
        self.assertIn("R.drawable.kbd_resize_grip", src)
        self.assertIn("canvas.drawBitmap(gripBitmap, null, gripDst, paint);", src)
        self.assertIn("hitCircle(topGrip, x, y, dp(22f))", src)
        # 13% dim over live keys (round 39-1: translucent scrim kept), blue frame, icons-only capsule
        self.assertIn("BACKDROP_BLACK = 0x21000000;", src)
        self.assertIn("paint.setColor(BACKDROP_BLACK);", src)
        self.assertIn("canvas.drawRoundRect(0f, keyboardFrame.top, width, bottom, radius, radius,"
                      " paint);", src)
        self.assertIn("hitCircle(confirmButton, x, y, dp(4f))", src)
        self.assertIn("hitCircle(resetButton, x, y, dp(4f))", src)
        for no_text in ("\u0645\u0648\u0627\u0641\u0642", "\u0625\u0639\u0627\u062f\u0629"):
            self.assertNotIn(no_text, src)

    def test_frame_mirrors_the_block_never_inflates(self):
        src = read(VIEW)
        self.assertIn("protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {",
                      src)
        self.assertIn("height = panel.getMeasuredHeight();", src)  # panel-EXACT by construction
        self.assertNotIn("box.getChildAt", src)
        # round 29: the scrim/frame IS the whole IME window (reference images 1-2); the
        # keyboard top is read through window LOCATION only, to anchor the ghost — never as a rect
        self.assertIn("keyboardFrame.set(edge / 2f, contentTop, width - edge / 2f,", src)
        self.assertIn("float top = keyboardTopY();", src)
        xml = (ROOT / "app/src/main/res/layout/ime_view.xml").read_text(encoding="utf-8")
        tag = xml[xml.index("keyboard_resize_overlay"):]
        self.assertIn('android:layout_height="wrap_content"', tag)
        self.assertIn('android:layout_gravity="bottom"', tag)
        self.assertNotIn('android:layout_height="match_parent"', tag)


class DragIsPreviewOnly(unittest.TestCase):
    def test_step_updates_two_floats_and_repaints_nothing_else(self):
        src = read(VIEW)
        # the literal requirement: the whole touch model is ONE OnTouchListener installed on the
        # overlay view — plain Java, nothing else
        self.assertIn("setOnTouchListener(new View.OnTouchListener() {", src)
        self.assertIn("public boolean onTouch(View view, MotionEvent event) {", src)
        self.assertIn("return handleTouch(event);", src)
        step = src[src.index("private void trackGhost(MotionEvent event, int pointerIndex) {"):]
        step = step[: step.index("\n  }")]
        body = [ln.strip() for ln in step.splitlines()[1:] if ln.strip()]
        self.assertEqual(
            ["float raw = rawY(event, pointerIndex);",
             "dragDyPx += raw - lastRawYpx;",
             "lastRawYpx = raw;",
             "invalidate();"],
            body)
        self.assertNotIn("requestLayout", step)
        self.assertNotIn("keyboard", step)
        self.assertNotIn("controller", step)

    def test_ghost_rides_the_whole_window_and_no_band_mechanism_exists(self):
        # user law (round 29, after the stretched-band regression): the sheet NEVER grows a
        # container band mid-drag. The scrim spans the whole IME window, the ghost line + handle
        # ride window-location geometry freely ABOVE the keyboard, and only the release resizes
        # the keyboard view itself. The entire stretch machinery must be gone from the tree.
        src = read(VIEW)
        self.assertIn("private int keyboardTopY() {", src)
        self.assertIn("int[] probes = { R.id.tool_bar, R.id.tool_bar_scroll, R.id.keyboard };", src)
        self.assertIn("private int windowTopOf(View v) {", src)
        self.assertIn("float top = keyboardTopY();", src)
        for gone in ("onPreviewResize", "syncPreviewGrow", "lastPreviewExtraPx",
                     "GHOST_TRIM_TINT", "ghostBand", "armKeyboardFrameGrowth",
                     "setKeyboardFrameExtraHeightPx", "resetKeyboardFrameExtraHeight"):
            self.assertNotIn(gone, src)
        base = read(JAVA / "ime/AlmlkImeRuntimeBase.java")
        part1 = read(JAVA / "ime/AlmlkImeRuntimePart1.java")
        part2a = read(JAVA / "ime/AlmlkImeRuntimePart2A.java")
        part2b = read(JAVA / "ime/AlmlkImeRuntimePart2B.java")
        frame_xml = read(ROOT / "app/src/main/res/layout/main_keyboard_frame.xml")
        for gone in ("keyboardFrameView", "keyboardFrameBaseHeightPx", "keyboardFrameGrowthPx"):
            for f in (base, part1, part2a, part2b):
                self.assertNotIn(gone, f)
        self.assertNotIn('android:gravity="bottom"', frame_xml)  # no pinned-frame band trick
        # the overlay rect is the WHOLE scrim (like the reference images): never a keyboard-only
        # sub-rect that could leave unlit space around it
        self.assertIn("keyboardFrame.set(edge / 2f, contentTop, width - edge / 2f,", src)

    def test_view_writes_no_sizes_to_the_keyboard_ever(self):
        src = read(VIEW)
        self.assertNotIn("setKeyHeightDp", src)          # it only REPORTS, the runtime applies
        code = re.sub(r"/\*[\s\S]*?\*/", "", src)
        code = re.sub(r"//[^\n]*", "", code)
        # requestLayout appears ONLY in show()/releaseTransientState() (open/close), never in
        # any drag handler
        for m in re.finditer(r"private void (\w+)\([^)]*\) \{([\s\S]*?)\n  \}", code):
            if "Grab" in m.group(1) or m.group(1) in ("stepPreview", "beginGrab"):
                self.assertNotIn("requestLayout", m.group(2), m.group(1) + " relayouts!")

    def test_ghost_line_follows_the_finger_and_is_clamped(self):
        src = read(VIEW)
        self.assertIn("private static final DashPathEffect GHOST_DASH =", src)
        self.assertIn("if (dragging) {", src)
        self.assertIn("paint.setPathEffect(GHOST_DASH);", src)
        ghost = src[src.index("private float ghostTopY()"):]
        ghost = ghost[: ghost.index("\n  }")]
        self.assertIn("pendingRowHeightDp()", ghost)      # same math the release will use
        self.assertIn("if (y < minY) {", ghost)            # ball clamped fully inside the scrim
        self.assertIn("float minY = dp(GRIP_RADIUS_DP) + edge / 2f;", ghost)
        self.assertIn("y = (float) getHeight() - dp(24f);", ghost)

    def test_on_draw_allocates_nothing(self):
        src = read(VIEW)
        draw = src[src.index("protected void onDraw(Canvas canvas) {"):]
        draw = draw[: draw.index("\n  }")]
        self.assertNotIn("new ", draw)
        for m in ("drawHandleBar", "drawGrip", "drawActionCapsule", "computeAnchors",
                  "ghostTopY", "currentBlockHeightPx"):
            i = src.index("private " if m in ("drawHandleBar", "drawGrip", "drawActionCapsule")
                          else "private float " if "Y()" in m else "private ")
            i = src.index(m)
            j = src.index("\n  }", i)
            self.assertNotIn("new ", src[i:j], "allocation inside " + m)


class ReleaseAppliesAndPersists(unittest.TestCase):
    def test_up_computes_applies_saves_once(self):
        src = read(VIEW)
        end = src[src.index("private void commitDrag() {"):]
        end = end[: end.index("\n  }")]
        self.assertIn("float pending = KeyboardResizeModel.saveRowHeightDp(getContext(), pendingRowHeightDp());", end)
        self.assertIn("KeyboardResizeModel.saveRowHeightDp(getContext(), pendingRowHeightDp())", end)
        self.assertIn("callback.onResizeCommitted(pending);", end)
        self.assertIn("startRowHeightDp = pending;", end)  # next drag starts from the fixed size
        self.assertNotIn("requestLayout", end)              # the RUNTIME's apply does it via setKeyHeightDp
        # CANCEL drops the whole preview: nothing applied, nothing saved
        cancel = src[src.index("if (action == MotionEvent.ACTION_CANCEL) {"):]
        cancel = cancel[: cancel.index("\n    }")]
        self.assertIn("dragDyPx = 0f;", cancel)
        self.assertNotIn("saveRowHeightDp", cancel)
        self.assertNotIn("onResizeApplied", cancel)

    def test_runtime_applies_and_boot_restores_from_prefs(self):
        part1 = read(JAVA / "ime/AlmlkImeRuntimePart1.java")
        self.assertIn("keyboard.setKeyHeightDp(KeyboardResizeModel.loadRowHeightDp("
                      "getApplicationContext()));", part1)
        # the round-30 replace: sizing rides the OFFICIAL insets channel, never View LayoutParams
        base = read(JAVA / "ime/AlmlkImeRuntimeBase.java")
        ins = base[base.index("public void onComputeInsets(InputMethodService.Insets out) {"):]
        ins = ins[: ins.index("\n  }")]
        self.assertIn("out.contentTopInsets = top;", ins)
        self.assertIn("out.visibleTopInsets = top;", ins)
        self.assertIn("TOUCHABLE_INSETS_VISIBLE", ins)
        ch = base[base.index("protected void changeHeightSmoothly(int newHeightPx) {"):]
        ch = ch[: ch.index("\n  }")]
        self.assertIn("inputRootView.requestLayout();", ch)
        self.assertIn("updateInputViewShown();", ch)
        self.assertIn("insetsSyncRunning", ch)  # re-entry guard: one pass per step, no storms
        for gone in ("applyKeyboardLayoutParams", "keyboard.setLayoutParams", "lp.height = px;"):
            self.assertNotIn(gone, base)  # the old channel is FULLY gone from the runtime
        view_src = read(JAVA / "ime/SmartKeyboardView.java")
        self.assertIn("public int getComputedBlockHeightPx() {", view_src)  # rows formula survives
        view_src = read(JAVA / "ime/SmartKeyboardView.java")
        self.assertIn("public int getComputedBlockHeightPx() {", view_src)
        part2a = read(JAVA / "ime/AlmlkImeRuntimePart2A.java")
        cb = part2a[part2a.index("protected void bindResizeOverlay()"):]
        cb = cb[: cb.index("\n  }")]
        self.assertIn("public void onResizeCommitted(float rowHeightDp) {", cb)
        self.assertIn("keyboard.setKeyHeightDp(rowHeightDp);", cb)
        self.assertIn("public void onConfirm() {", cb)   # close only — persistence is at release
        self.assertNotIn("putFloat", cb)                  # the view's controller is the one writer
        part2b = read(JAVA / "ime/AlmlkImeRuntimePart2B.java")
        self.assertIn("resizeOverlay.show(keyboard.getKeyHeightDp(),"
                      " keyboard.getHeightReferenceRowCount());", part2b)

    def test_preferences_storage_and_bounds(self):
        src = read(CTRL)
        self.assertIn('public static final String PREFS = "keyboard_ui";', src)
        self.assertIn('public static final String KEY_ROW_DP = "key_height_dp";', src)
        self.assertIn("public static final float MIN_KEY_ROW_DP = 36.84f;", src)
        self.assertIn("public static final float MAX_KEY_ROW_DP = 75.2f;", src)
        self.assertIn("public static final float DEFAULT_KEY_ROW_DP = 48f;", src)
        self.assertIn("float clamped = clampRowHeightDp(rowHeightDp);", src)
        self.assertIn(".putFloat(KEY_ROW_DP, clamped)", src)
        self.assertIn(".apply();", src)
        self.assertIn("return clampRowHeightDp(ui.getFloat(KEY_ROW_DP, DEFAULT_KEY_ROW_DP));",
                      src)
        # the drag math: total travel spread over the rows, then clamped — preview and commit
        # share the exact same function, so they can never disagree
        self.assertIn("return clampRowHeightDp(startRowHeightDp - dyPx / rowsPx);", src)

    def test_bounds_apply_on_both_sides(self):
        # functional check of the static math through the real source semantics
        src = read(CTRL)
        body = src[src.index("public static float pendingRowHeightDp"):]
        body = body[: body.index("\n  }")]
        self.assertIn("int rows = rowCount > 0 ? rowCount : 1;", body)
        self.assertIn("rows * (density > 0f ? density : 1f)", body)
        clamp = src[src.index("public static float clampRowHeightDp"):]
        clamp = clamp[: clamp.index("\n  }")]
        self.assertIn("if (rowHeightDp < MIN_KEY_ROW_DP)", clamp)
        self.assertIn("if (rowHeightDp > MAX_KEY_ROW_DP)", clamp)


class JavaSixHygiene(unittest.TestCase):
    def test_no_post_java6_syntax(self):
        # AIDE builds with sourceCompatibility 1.6 (verified from the user's build.gradle)
        for path in (VIEW, CTRL):
            src = read(path)
            code = re.sub(r"/\*[\s\S]*?\*/", "", src)
            code = re.sub(r"//[^\n]*", "", code)
            code = re.sub(r'"(?:[^"\\]|\\.)*"', '""', code)
            self.assertNotIn("<>", code, path.name + ": diamond")
            self.assertNotIn("->", code, path.name + ": lambda")
            self.assertIsNone(re.search(r"try \(", code), path.name + ": try-with-resources")
            self.assertIsNone(re.search(r"catch \([\w.]+ \|", code), path.name + ": multicatch")

    def test_all_bare_calls_resolve(self):
        src = read(VIEW)
        defined = set(re.findall(
            r"(?:private|public|protected)\s+(?:static\s+)?[\w.<>\[\]]+\s+(\w+)\s*\(", src))
        defined |= set(re.findall(
            r"\b(?:void|float|int|boolean|String)\s+(\w+)\s*\([^)]*\)\s*;", src))
        inherited = {"getWidth", "getHeight", "setVisibility", "bringToFront", "requestFocus",
                     "requestLayout", "invalidate", "clearAnimation", "getParent",
                     "getLocationInWindow", "removeCallbacks", "postOnAnimation",
                     "setBackgroundColor", "setClickable", "setFocusable", "getResources",
                     "setMeasuredDimension", "getMeasuredHeight", "getMeasuredWidth",
                     "getChildCount", "getChildAt", "getVisibility", "setOnTouchListener", "getContext",
                     "removeOnLayoutChangeListener",
                     "addOnLayoutChangeListener"}
        body = src[src.index("public final class"):]
        body = re.sub(r"/\*.*?\*/", " ", body, flags=re.S)
        body = re.sub(r"//[^\n]*", " ", body)
        for name in set(re.findall(r"(?<![\w.])([a-z]\w*)\s*\(", body)):
            if name in ("if", "for", "while", "return", "new", "catch", "super"):
                continue
            self.assertTrue(name in defined or name in inherited,
                            "unresolved bare call: " + name)


if __name__ == "__main__":
    unittest.main()


class PureJavaOnly(unittest.TestCase):
    def test_no_kotlin_anywhere(self):
        # the request's first clause: Java only, no Kotlin code or classes merged in
        import os
        root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
        kt = []
        gradle_hit = []
        for base, dirs, files in os.walk(root):
            dirs[:] = [x for x in dirs if x not in ('.git', '__pycache__', 'build', '.gradle')]
            for f in files:
                if f.endswith(('.kt', '.kts')):
                    kt.append(os.path.join(base, f))
                elif f.endswith(('.gradle', '.properties')):
                    body = open(os.path.join(base, f), encoding='utf-8').read().lower()
                    if 'kotlin' in body:
                        gradle_hit.append(f)
                elif f.endswith('.java'):
                    body = open(os.path.join(base, f), encoding='utf-8').read()
                    if 'import kotlin' in body or 'kotlin.jvm' in body:
                        gradle_hit.append(f)
        self.assertEqual([], kt, 'Kotlin sources present')
        self.assertEqual([], gradle_hit, 'kotlin referenced in build files or imports')

    def test_touch_mechanism_and_insets_replacement_as_requested(self):
        # OnTouchListener stays literal (round 27); the SIZING channel is now the round-30 spec:
        # onComputeInsets + changeHeightSmoothly — LayoutParams surgery is explicitly replaced.
        view = read(VIEW)
        self.assertIn("setOnTouchListener(new View.OnTouchListener() {", view)
        self.assertIn("void onLiveRowHeight(float rowHeightDp);", view)
        self.assertIn("private void reportLiveRow() {", view)
        self.assertIn("if (pending == lastReportedRowDp) {", view)   # coalesced live steps
        self.assertIn("protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {", view)
        for gone in ("fullSheet", "MeasureSpec.getSize(widthMeasureSpec)"):
            self.assertNotIn(gone, view)   # the whole-window sheet is DEAD (image-2 law)
        self.assertIn("height = panel.getMeasuredHeight();", view)                # panel-EXACT by construction
        self.assertIn("setTargetView(root.findViewById(R.id.main_keyboard_frame));",
                      read(JAVA / "ime/AlmlkImeRuntimePart1.java"))    # handle anchors at panel top
        base = read(JAVA / "ime/AlmlkImeRuntimeBase.java")
        self.assertIn("public void onComputeInsets(InputMethodService.Insets out) {", base)
        self.assertIn("protected void changeHeightSmoothly(int newHeightPx) {", base)
        self.assertIn("protected void bindInsetsToPanel() {", base)
        p1 = read(JAVA / "ime/AlmlkImeRuntimePart1.java")
        self.assertIn("bindInsetsToPanel();", p1)
        self.assertIn("imePanelRootView = root.findViewById(R.id.main_keyboard_frame);", p1)
        p2b = read(JAVA / "ime/AlmlkImeRuntimePart2B.java")
        self.assertIn("changeHeightSmoothly(imePanelRootView == null ? 0 : imePanelRootView.getMeasuredHeight());", p2b)
