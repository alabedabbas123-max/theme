package com.almlk.swiftkey.ime;

import com.almlk.swiftkey.engine.GestureTrace;
import com.almlk.swiftkey.model.KeySpec;

/** Top-level AIDE/D8-safe listener used by the keyboard view and IME service. */
public interface KeyboardActionListener {
  void onKey(KeySpec key);

  void onSpaceSwipe(int direction);

  void onSpaceVerticalSwipe(boolean showNumberRow);

  void onGesturePreview(GestureTrace gesture);

  void onGesture(GestureTrace gesture);

  void onGestureCancelled();

  void onAlternatives(KeySpec key, String selected);
}
