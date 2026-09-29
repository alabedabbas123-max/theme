package com.almlk.swiftkey.settings;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.almlk.swiftkey.R;

/** Shared bottom tab bar for the three settings roots: more, stickers and shapes. */
public final class SettingsTabs {
  public static final int TAB_MORE = 0;
  public static final int TAB_STICKERS = 1;
  public static final int TAB_SHAPES = 2;

  private SettingsTabs() {}

  public static void bind(final Activity host, int selected) {
    styleTab(host, R.id.tab_more_icon, R.id.tab_more_label, selected == TAB_MORE);
    styleTab(host, R.id.tab_stickers_icon, R.id.tab_stickers_label, selected == TAB_STICKERS);
    styleTab(host, R.id.tab_shapes_icon, R.id.tab_shapes_label, selected == TAB_SHAPES);
    openOnClick(host, R.id.tab_more, TAB_MORE, selected, SettingsActivity.class);
    openOnClick(host, R.id.tab_stickers, TAB_STICKERS, selected, StickersActivity.class);
    openOnClick(host, R.id.tab_shapes, TAB_SHAPES, selected, ThemeSettingsActivity.class);
  }

  private static void styleTab(Activity host, int iconId, int labelId, boolean selected) {
    ImageView icon = (ImageView) host.findViewById(iconId);
    TextView label = (TextView) host.findViewById(labelId);
    if (icon == null || label == null) {
      return;
    }
    if (selected) {
      icon.setBackgroundResource(R.drawable.bg_tab_selected);
      icon.setColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN);
      label.setTextColor(0xff2196f3);
    } else {
      icon.setBackgroundColor(0x00000000);
      icon.setColorFilter(0xff9e9e9e, PorterDuff.Mode.SRC_IN);
      label.setTextColor(0xff9e9e9e);
    }
  }

  private static void openOnClick(
      final Activity host, int tabId, final int tab, int selected, final Class<?> target) {
    View view = host.findViewById(tabId);
    if (view == null || tab == selected) {
      return;
    }
    view.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View clicked) {
            host.startActivity(new Intent(host, target));
            host.overridePendingTransition(0, 0);
            host.finish();
          }
        });
  }
}
