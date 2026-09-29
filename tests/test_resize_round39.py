# -*- coding: utf-8 -*-
"""Resize round-39 contract: opaque drag sheet, frame-coalesced live tracking, new bounds.

User request (three points):
  1. Lower the maximum height so the keyboard looks proportionate and elegant.
  2. While dragging up/down NOTHING may show behind the drag view; the keyboard must
     rise/fall with the sheet precisely, no jitter; the drag UI stays 100% linked to the
     keyboard height (drag up = grow, drag down = shrink).
  3. Reduce the maximum height by 5% and raise the minimum by 3%; keep the drag smooth
     and light.

Implementation contract pinned here:
  - KeyboardResizeModel bounds: 96 -> 91.2dp (-5%), 28 -> 28.84dp (+3%), default 48dp.
  - The scrim stays the legacy 13% translucent dim (round 39-1: the user restored it —
    the real keys remain visible under the drag sheet);
  - The live hand-off is coalesced to ONE reportLiveRow() per display frame via
    postOnAnimation, so the panel grows/shrinks exactly with the sheet, lightly.
  - Release still commits through the SAME pendingRowHeightDp() the live steps rode;
    ACTION_CANCEL still rolls back and saves nothing.
  - The overlay never touches keyboard geometry itself (no requestLayout/setKeyHeightDp
    inside drag handlers) — onLiveRowHeight stays the only channel.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VIEW = ROOT / "app/src/main/java/com/almlk/swiftkey/ime/KeyboardResizeOverlay.java"
MODEL = ROOT / "app/src/main/java/com/almlk/swiftkey/ime/KeyboardResizeModel.java"
PART2A = ROOT / "app/src/main/java/com/almlk/swiftkey/ime/AlmlkImeRuntimePart2A.java"


def read(path):
    return path.read_text(encoding="utf-8")


class ResizeRound39(unittest.TestCase):
    def test_bounds_round39(self):
        src = read(MODEL)
        self.assertIn("public static final float MIN_KEY_ROW_DP = 36.84f;", src)
        self.assertIn("public static final float MAX_KEY_ROW_DP = 75.2f;", src)
        self.assertIn("public static final float DEFAULT_KEY_ROW_DP = 48f;", src)
        self.assertNotIn("28.84f", src)   # الحدود القديمة زالت نهائياً
        self.assertNotIn("91.2f", src)

    def test_translucent_scrim_restored(self):
        """Round 39-1: the user restored the translucent scrim — keys stay visible under it."""
        src = read(VIEW)
        self.assertIn("BACKDROP_BLACK = 0x21000000;", src)
        self.assertIn("paint.setColor(BACKDROP_BLACK);", src)
        self.assertNotIn("SCRIM_FALLBACK", src)
        self.assertNotIn("scrimColor", src)

    def test_live_handoff_coalesced_per_frame(self):
        src = read(VIEW)
        self.assertIn("private final Runnable liveRowStep =", src)
        # خطوة واحدة لكل إطار عرض: الإلغاء ثم الجدولة في ACTION_MOVE
        move = src[src.index("if (action == MotionEvent.ACTION_MOVE && dragging) {"):]
        move = move[: move.index("\n    }")]
        self.assertIn("trackGhost(event, pointerIndex);", move)
        self.assertIn("removeCallbacks(liveRowStep);", move)
        self.assertIn("postOnAnimation(liveRowStep);", move)
        # والخطوة المجدولة هي نفسها reportLiveRow
        step = src[src.index("private final Runnable liveRowStep ="):]
        step = step[: step.index("};") + 2]
        self.assertIn("if (dragging) {", step)
        self.assertIn("reportLiveRow();", step)

    def test_every_terminal_cancels_pending_step(self):
        src = read(VIEW)
        for anchor, label in (
            ("if (action == MotionEvent.ACTION_UP) {", "ACTION_UP"),
            ("if (action == MotionEvent.ACTION_CANCEL) {", "ACTION_CANCEL"),
        ):
            block = src[src.index(anchor):]
            block = block[: block.index("\n    }")]
            self.assertIn("removeCallbacks(liveRowStep);", block, label)

    def test_direction_law_unchanged(self):
        # السحب لأعلى يكبر الكيبورد ولأسفل يصغّره: dyPx سالب صعوداً => -dyPx موجب
        src = read(MODEL)
        self.assertIn("return clampRowHeightDp(startRowHeightDp - dyPx / rowsPx);", src)

    def test_overlay_never_touches_keyboard_geometry(self):
        import re

        src = read(VIEW)
        code = re.sub(r"/\*\*[\s\S]*?\*/", "", src)
        code = re.sub(r"//[^\n]*", "", code)
        move = code[code.index("if (action == MotionEvent.ACTION_MOVE && dragging) {"):]
        move = move[: move.index("\n    }")]
        for banned in ("requestLayout", "setKeyHeightDp", "onResizeApplied"):
            self.assertNotIn(banned, move, banned + " داخل ACTION_MOVE")

    def test_runtime_live_channel_untouched(self):
        src = read(PART2A)
        self.assertEqual(2, src.count("keyboard.setKeyHeightDp(rowHeightDp);"))
        self.assertIn("public void onLiveRowHeight(float rowHeightDp) {", src)

    def test_build_config_untouched(self):
        gradle = read(ROOT / "app/build.gradle")
        self.assertIn("versionCode 36", gradle)
        self.assertIn("versionName '1.9-round36'", gradle)


if __name__ == "__main__":
    unittest.main()
