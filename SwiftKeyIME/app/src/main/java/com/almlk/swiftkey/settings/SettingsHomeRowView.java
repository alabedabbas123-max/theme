package com.almlk.swiftkey.settings;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.almlk.swiftkey.R;

/** Reusable lightweight row that reads its XML attributes without generated styleable arrays. */
public final class SettingsHomeRowView extends LinearLayout {
  public SettingsHomeRowView(Context context) {
    super(context);
    initialize(null);
  }

  public SettingsHomeRowView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize(attrs);
  }

  private void initialize(AttributeSet attrs) {
    setOrientation(HORIZONTAL);
    setGravity(android.view.Gravity.CENTER_VERTICAL);
    if (android.os.Build.VERSION.SDK_INT >= 17) {
      setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    inflate(getContext(), R.layout.settings_home_row_content, this);
    TextView title = (TextView) findViewById(R.id.settings_row_title);
    TextView subtitle = (TextView) findViewById(R.id.settings_row_subtitle);
    ImageView icon = (ImageView) findViewById(R.id.settings_row_icon);

    if (attrs != null) {
      CharSequence titleValue = attributeText(attrs, "settingsTitle");
      CharSequence detail = attributeText(attrs, "settingsSubtitle");
      int drawable = attributeResource(attrs, "settingsIcon", 0);
      boolean highlighted = attributeBoolean(attrs, "settingsHighlighted", false);

      title.setText(titleValue);
      subtitle.setText(detail);
      subtitle.setVisibility(detail == null || detail.length() == 0 ? GONE : VISIBLE);
      if (drawable != 0) {
        icon.setImageResource(drawable);
      }
      setBackgroundColor(highlighted ? 0xffe0e0e0 : Color.TRANSPARENT);
    }

    setClickable(true);
    setFocusable(true);
    setContentDescription(title.getText());
  }

  private CharSequence attributeText(AttributeSet attrs, String name) {
    int index = attributeIndex(attrs, name);
    if (index < 0) {
      return "";
    }
    int resource = attrs.getAttributeResourceValue(index, 0);
    return resource == 0 ? attrs.getAttributeValue(index) : getResources().getText(resource);
  }

  private static int attributeResource(AttributeSet attrs, String name, int fallback) {
    int index = attributeIndex(attrs, name);
    return index < 0 ? fallback : attrs.getAttributeResourceValue(index, fallback);
  }

  private static boolean attributeBoolean(AttributeSet attrs, String name, boolean fallback) {
    int index = attributeIndex(attrs, name);
    return index < 0 ? fallback : attrs.getAttributeBooleanValue(index, fallback);
  }

  private static int attributeIndex(AttributeSet attrs, String name) {
    for (int index = 0; index < attrs.getAttributeCount(); index++) {
      if (name.equals(attrs.getAttributeName(index))) {
        return index;
      }
    }
    return -1;
  }

  public void setSubtitle(CharSequence value) {
    TextView subtitle = (TextView) findViewById(R.id.settings_row_subtitle);
    subtitle.setText(value);
    subtitle.setVisibility(value == null || value.length() == 0 ? GONE : VISIBLE);
  }
}
