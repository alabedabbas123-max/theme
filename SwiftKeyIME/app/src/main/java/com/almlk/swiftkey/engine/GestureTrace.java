package com.almlk.swiftkey.engine;

import java.util.HashMap;
import java.util.Map;

/** Immutable normalized pointer path and the exact key geometry used while it was captured. */
public final class GestureTrace {
  private final float[] x;
  private final float[] y;
  private final String keyTrace;
  private final HashMap<String, float[]> keyCenters;

  public GestureTrace(
      float[] pathX, float[] pathY, String crossedKeys, Map<String, float[]> centers) {
    x = pathX == null ? new float[0] : (float[]) pathX.clone();
    y = pathY == null ? new float[0] : (float[]) pathY.clone();
    keyTrace = crossedKeys == null ? "" : crossedKeys;
    keyCenters = new HashMap<String, float[]>();
    if (centers != null) {
      for (Map.Entry<String, float[]> entry : centers.entrySet()) {
        float[] value = entry.getValue();
        if (entry.getKey() != null && value != null && value.length >= 2) {
          keyCenters.put(entry.getKey(), new float[] {value[0], value[1]});
        }
      }
    }
  }

  public int size() {
    return Math.min(x.length, y.length);
  }

  public float x(int index) {
    return x[index];
  }

  public float y(int index) {
    return y[index];
  }

  public String crossedKeys() {
    return keyTrace;
  }

  public float[] center(char letter, String language) {
    float[] exact = keyCenters.get(String.valueOf(letter));
    if (exact != null) return new float[] {exact[0], exact[1]};
    String target = TextNormalizer.foldForComparison(String.valueOf(letter), language);
    for (Map.Entry<String, float[]> entry : keyCenters.entrySet()) {
      String folded = TextNormalizer.foldForComparison(entry.getKey(), language);
      if (folded.length() > 0 && target.length() > 0 && folded.charAt(0) == target.charAt(0)) {
        float[] value = entry.getValue();
        return new float[] {value[0], value[1]};
      }
    }
    return null;
  }
}
