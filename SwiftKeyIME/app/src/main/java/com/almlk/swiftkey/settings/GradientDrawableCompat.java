package com.almlk.swiftkey.settings;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

final class GradientDrawableCompat {
  static void apply(View view, int fill, int stroke, float radiusDp) {
    float d = view.getResources().getDisplayMetrics().density;
    GradientDrawable g = new GradientDrawable();
    g.setColor(fill);
    g.setStroke(Math.max(1, (int) d), stroke);
    g.setCornerRadius(radiusDp * d);
    view.setBackground(g);
  }

  private GradientDrawableCompat() {}
}
