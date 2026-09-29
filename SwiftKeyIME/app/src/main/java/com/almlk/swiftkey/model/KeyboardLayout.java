package com.almlk.swiftkey.model;

import java.util.List;

public final class KeyboardLayout {
  public final List<List<KeySpec>> rows;
  public final boolean rtl;

  /** Number-row digits from the layout XML: -1 unset, 0 western, 1 arabic. */
  public final int digitType;

  /** KeyboardLayouts enum id from the layout XML, or -1 when the tag omitted it. */
  public final int keyboardLayoutId;

  public KeyboardLayout(List<List<KeySpec>> rows, boolean rtl) {
    this(rows, rtl, -1, -1);
  }

  public KeyboardLayout(List<List<KeySpec>> rows, boolean rtl, int digitType) {
    this(rows, rtl, digitType, -1);
  }

  public KeyboardLayout(
      List<List<KeySpec>> rows, boolean rtl, int digitType, int keyboardLayoutId) {
    this.rows = rows;
    this.rtl = rtl;
    this.digitType = digitType;
    this.keyboardLayoutId = keyboardLayoutId;
  }
}
