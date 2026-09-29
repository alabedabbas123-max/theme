package com.almlk.swiftkey.ime;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The keyboard-resizing state, pure Java: SharedPreferences persistence (so the committed height
 * is restored on every boot of the IME), the hard min/max bounds (the keyboard can never vanish
 * or cover the screen), and the single piece of drag math shared by the live preview and the
 * release commit — total finger travel spread over the visible rows, then clamped.
 *
 * The keyboard's height model is rows * (row + gap) + chrome, so moving the block's top edge by
 * D px makes each row D/rows px taller or shorter. Because the preview line and the release use
 * the exact same function of (drag-start, total travel), the layout can never land anywhere the
 * guide did not show.
 */
public final class KeyboardResizeModel {
  public static final String PREFS = "keyboard_ui";
  public static final String KEY_ROW_DP = "key_height_dp";

  /**
   * Bounds that answer the spec's MinHeight/MaxHeight requirement. Round 39-2: the user's exact
   * final tune — MAX 75.2dp, MIN 36.84dp — the keyboard stays proportionate and elegant at its
   * tallest and never feels cramped at its shortest. Saved legacy heights re-clamp on load.
   */
  public static final float MIN_KEY_ROW_DP = 36.84f;
  public static final float MAX_KEY_ROW_DP = 70.2f;
  public static final float DEFAULT_KEY_ROW_DP = 48f;

  private KeyboardResizeModel() {
  }

  /** Restore the persisted row height; the legacy int "size" setting folds onto dp. */
  public static float loadRowHeightDp(Context context) {
    SharedPreferences ui = context.getSharedPreferences(PREFS, 0);
    if (ui == null) {
      return DEFAULT_KEY_ROW_DP;
    }
    if (ui.contains(KEY_ROW_DP)) {
      return clampRowHeightDp(ui.getFloat(KEY_ROW_DP, DEFAULT_KEY_ROW_DP));
    }
    int legacySize = ui.getInt("size", 1);
    return clampRowHeightDp(legacySize == 0 ? 46f : legacySize == 1
        ? DEFAULT_KEY_ROW_DP : 52f);
  }

  /** Persist a committed row height — returns the clamped value actually stored. */
  public static float saveRowHeightDp(Context context, float rowHeightDp) {
    float clamped = clampRowHeightDp(rowHeightDp);
    context.getSharedPreferences(PREFS, 0)
        .edit()
        .putFloat(KEY_ROW_DP, clamped)
        .apply();
    return clamped;
  }

  public static float clampRowHeightDp(float rowHeightDp) {
    if (rowHeightDp < MIN_KEY_ROW_DP) {
      return MIN_KEY_ROW_DP;
    }
    if (rowHeightDp > MAX_KEY_ROW_DP) {
      return MAX_KEY_ROW_DP;
    }
    return rowHeightDp;
  }

  /**
   * A drag result from the drag's START row and the pointer's TOTAL travel so far (dyPx is
   * negative moving up). Total travel, never chained deltas — no drift, and releasing right
   * where the guide line sits reproduces exactly that height.
   */
  public static float pendingRowHeightDp(float startRowHeightDp, float dyPx, int rowCount,
      float density) {
    int rows = rowCount > 0 ? rowCount : 1;
    float rowsPx = rows * (density > 0f ? density : 1f);
    return clampRowHeightDp(startRowHeightDp - dyPx / rowsPx);
  }
}
