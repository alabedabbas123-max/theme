# -*- coding: utf-8 -*-
"""Resize round 39-2: no black band behind the shrinking keyboard + final bounds.

User report (screenshot): while dragging DOWN a BLACK background appears behind
the keyboard; the keyboard must move down with the drag sheet without leaving
any opaque or translucent surface matching the PREVIOUS size. Also: exact new
bounds MIN_KEY_ROW_DP = 36.84f, MAX_KEY_ROW_DP = 75.2f.

Root cause: the IME window's SURFACE resize lags the view-layout pass by a frame
while the live drag shrinks the panel. The exposed strip above the shrinking
keyboard was painted with the default (black) IME window backdrop.

Fix contract pinned here:
  - Round 40-1 supersedes the themed-backdrop fix: the IME window background is
    now FULLY TRANSPARENT. Image themes have near-black fallback backgrounds, so
    a painted surface still showed a black band during the resize-lag frames.
    Transparent means the APP shows through any lag — zero band of ANY color.
  - configureDockedWindow installs that drawable as the window background.
  - The window stays WRAP_CONTENT bottom-docked: the surface hugs the panel, and
    the live drag (postOnAnimation-coalesced) keeps panel, sheet and window in
    lockstep; nothing above the keyboard's top edge belongs to the window.
  - Bounds: MIN 36.84f, MAX 75.2f (user's exact values), DEFAULT 48f unchanged.
"""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/almlk/swiftkey/ime"
BASE = JAVA / "AlmlkImeRuntimeBase.java"
PART1 = JAVA / "AlmlkImeRuntimePart1.java"
PART2A = JAVA / "AlmlkImeRuntimePart2A.java"
MODEL = JAVA / "KeyboardResizeModel.java"


def read(path):
    return path.read_text(encoding="utf-8")


class ResizeRound392(unittest.TestCase):
    def test_final_bounds_exact(self):
        src = read(MODEL)
        self.assertIn("public static final float MIN_KEY_ROW_DP = 36.84f;", src)
        self.assertIn("public static final float MAX_KEY_ROW_DP = 75.2f;", src)
        self.assertIn("public static final float DEFAULT_KEY_ROW_DP = 48f;", src)
        self.assertNotIn("28.84f", src)
        self.assertNotIn("91.2f", src)

    def test_window_backdrop_field_exists(self):
        src = read(BASE)
        self.assertIn("protected final android.graphics.drawable.ColorDrawable windowBackdrop =", src)
        # Round 40-1: شفافة تماماً — التطبيق يظهر خلف أي منطقة انتقالية
        self.assertIn(
            "new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);",
            src,
        )
        # النافذة تبقى WRAP_CONTENT سفلية: السطح يحتضن اللوحة ولا يعلو فوقها
        part1 = read(PART1)
        self.assertIn("params.height = WindowManager.LayoutParams.WRAP_CONTENT;", part1)
        self.assertIn("params.gravity = Gravity.BOTTOM | Gravity.FILL_HORIZONTAL;", part1)

    def test_window_background_installed(self):
        src = read(PART1)
        self.assertIn("win.setBackgroundDrawable(windowBackdrop);", src)

    def test_backdrop_stays_transparent_forever(self):
        # Round 40-1: لا تلوين إطلاقاً — أي تلوين يعيد إنتاج شريط بلون الثيم/الأسود
        base = read(BASE)
        part2a = read(PART2A)
        part1 = read(PART1)
        for src in (base, part2a, part1):
            self.assertNotIn("windowBackdrop.setColor", src)
            self.assertNotIn("windowBackdrop.setColor(", src)
        self.assertIn("win.setBackgroundDrawable(windowBackdrop);", part1)

    def test_live_drag_channel_still_frame_coalesced(self):
        view = read(JAVA / "KeyboardResizeOverlay.java")
        self.assertIn("postOnAnimation(liveRowStep);", view)
        self.assertIn("removeCallbacks(liveRowStep);", view)
        # الستارة الشفافة 13% كما أعادها المستخدم في round 39-1
        self.assertIn("BACKDROP_BLACK = 0x21000000;", view)
        self.assertNotIn("scrimColor", view)

    def test_no_black_window_backdrop_can_return(self):
        for path in (BASE, PART1):
            src = read(path)
            self.assertNotIn("setBackgroundDrawable(null)", src, path.name)
            self.assertNotIn("ColorDrawable(Color.BLACK)", src, path.name)

    def test_build_config_untouched(self):
        gradle = read(ROOT / "app/build.gradle")
        self.assertIn("versionCode 36", gradle)
        self.assertIn("versionName '1.9-round36'", gradle)


if __name__ == "__main__":
    unittest.main()
