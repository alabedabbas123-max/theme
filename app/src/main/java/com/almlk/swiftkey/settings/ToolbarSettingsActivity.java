package com.almlk.swiftkey.settings;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.ime.ToolPreferences;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Toolbar customization: per-tool visibility and ordering backed by ToolPreferences. */
public final class ToolbarSettingsActivity extends AppCompatActivity {
  private static final Map<String, String> LABELS = buildLabels();

  private LinearLayout list;

  private static Map<String, String> buildLabels() {
    Map<String, String> labels = new HashMap<String, String>();
    labels.put("settings", "الإعدادات");
    labels.put("gestures", "الكتابة بالإيماءات");
    labels.put("theme", "السمات");
    labels.put("rewards", "المكافآت");
    labels.put("resize", "تغيير الحجم");
    labels.put("login", "تسجيل الدخول");
    labels.put("layouts", "التخطيطات");
    labels.put("gif", "صور GIF");
    labels.put("tips", "التلميحات");
    labels.put("stickers", "الملصقات");
    labels.put("languages", "اللغات");
    labels.put("incognito", "التصفح الخفي");
    labels.put("modes", "الأوضاع");
    labels.put("search", "البحث");
    labels.put("voice", "الصوت");
    labels.put("translate", "الترجمة");
    labels.put("clipboard", "الحافظة");
    return labels;
  }

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_toolbar_settings);

    TextView title = (TextView) findViewById(R.id.settings_page_title);
    title.setText("تخصيص شريط الادوات");
    findViewById(R.id.settings_page_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });

    list = (LinearLayout) findViewById(R.id.toolbar_list);
    findViewById(R.id.toolbar_reset)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                ToolPreferences.reset(ToolbarSettingsActivity.this);
                renderTools();
              }
            });
    renderTools();
  }

  private void renderTools() {
    list.removeAllViews();
    final List<String> order = ToolPreferences.order(this);
    for (int position = 0; position < order.size(); position++) {
      list.addView(buildRow(order, position));
    }
  }

  private View buildRow(final List<String> order, final int position) {
    final String key = order.get(position);
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setBackgroundColor(0xffffffff);
    row.setPadding(dp(12), dp(8), dp(12), dp(8));
    LinearLayout.LayoutParams params =
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.bottomMargin = dp(8);
    row.setLayoutParams(params);

    TextView name = new TextView(this);
    String label = LABELS.get(key);
    name.setText(label == null ? key : label);
    name.setTextColor(0xff222222);
    name.setTextSize(15);
    LinearLayout.LayoutParams nameParams =
        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    name.setLayoutParams(nameParams);
    row.addView(name);

    TextView up = buildArrow("▲", position > 0);
    up.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            move(order, position, position - 1);
          }
        });
    row.addView(up);

    TextView down = buildArrow("▼", position < order.size() - 1);
    down.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            move(order, position, position + 1);
          }
        });
    row.addView(down);

    Switch visible = new Switch(this);
    visible.setChecked(ToolPreferences.isVisible(this, key));
    visible.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          public void onCheckedChanged(CompoundButton button, boolean checked) {
            if (ToolPreferences.isVisible(ToolbarSettingsActivity.this, key) != checked) {
              ToolPreferences.toggleVisible(ToolbarSettingsActivity.this, key);
            }
          }
        });
    row.addView(visible);
    return row;
  }

  private TextView buildArrow(String symbol, boolean enabled) {
    TextView arrow = new TextView(this);
    arrow.setText(symbol);
    arrow.setTextSize(18);
    arrow.setPadding(dp(12), dp(8), dp(12), dp(8));
    arrow.setTextColor(enabled ? 0xff1976d2 : 0xffdddddd);
    arrow.setEnabled(enabled);
    return arrow;
  }

  private void move(List<String> order, int from, int to) {
    if (to < 0 || to >= order.size()) {
      return;
    }
    String key = order.remove(from);
    order.add(to, key);
    ToolPreferences.saveOrder(this, order);
    renderTools();
  }

  private int dp(int value) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(value * density);
  }
}
