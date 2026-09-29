package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.theme.KeyboardTheme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Professional four-column tool manager with persistent visibility and ordering. */
public final class MoreToolsPanelView extends LinearLayout {
  public interface Callback {
    void onToolAction(String key);

    void onToolbarConfigurationChanged();
  }

  private static final class Tool {
    final String key;
    final String label;
    final int icon;
    final boolean toolbarCapable;

    Tool(String key, String label, int icon, boolean toolbarCapable) {
      this.key = key;
      this.label = label;
      this.icon = icon;
      this.toolbarCapable = toolbarCapable;
    }
  }

  private final Map<String, Tool> catalog = new HashMap<String, Tool>();
  private final ArrayList<Tool> tools = new ArrayList<Tool>();
  private GridView grid;
  private TextView hint;
  private Button editButton;
  private Callback callback;
  private ToolAdapter adapter;
  private boolean editMode;
  private KeyboardTheme theme = KeyboardTheme.from("light");
  private int selectedPosition = -1;

  public MoreToolsPanelView(Context context) {
    super(context);
    initialize();
  }

  public MoreToolsPanelView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  private void initialize() {
    inflate(getContext(), R.layout.more_tools_panel, this);
    add("settings", "الإعدادات", R.drawable.ic_settings, true);
    add("gestures", "الكتابة بالإيماءات", R.drawable.ic_gesture, false);
    add("theme", "السمات", R.drawable.ic_palette, true);
    add("rewards", "Rewards", R.drawable.ic_reward, false);
    add("resize", "تغيير الحجم", R.drawable.ic_resize, true);
    add("login", "تسجيل الدخول", R.drawable.ic_user, false);
    add("layouts", "التخطيطات", R.drawable.ic_keyboard, true);
    add("gif", "GIF", R.drawable.ic_gif, true);
    add("tips", "التلميحات", R.drawable.ic_info, true);
    add("stickers", "الملصقات", R.drawable.ic_sticker, true);
    add("languages", "اللغات", R.drawable.ic_language, true);
    add("incognito", "التصفح الخفي", R.drawable.ic_incognito, true);
    add("modes", "الأوضاع", R.drawable.ic_symbols, true);
    add("search", "البحث", R.drawable.ic_search, true);
    add("voice", "الصوت", R.drawable.ic_mic, true);
    add("translate", "الترجمة", R.drawable.ic_translate, true);
    add("clipboard", "الحافظة", R.drawable.ic_clipboard, true);

    grid = (GridView) findViewById(R.id.tools_grid);
    hint = (TextView) findViewById(R.id.tools_hint);
    editButton = (Button) findViewById(R.id.tools_edit);
    adapter = new ToolAdapter();
    reloadOrder();
    grid.setAdapter(adapter);
    grid.setVerticalScrollBarEnabled(true);
    grid.setScrollbarFadingEnabled(false);
    grid.setSmoothScrollbarEnabled(true);

    editButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            editMode = !editMode;
            selectedPosition = -1;
            editButton.setText(editMode ? "تم" : "تعديل شريط الأدوات");
            hint.setText(
                editMode
                    ? "اضغط على الأداة لإظهارها أو إخفائها من الشريط"
                    : "اضغط مطولًا لإظهار أو إخفاء الأداة من الشريط");
            adapter.notifyDataSetChanged();
          }
        });

    grid.setOnItemClickListener(
        new AdapterView.OnItemClickListener() {
          @Override
          public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (!editMode) {
              if (callback != null) {
                callback.onToolAction(tools.get(position).key);
              }
              return;
            }
            Tool tool = tools.get(position);
            if (tool.toolbarCapable) {
              boolean visible = ToolPreferences.toggleVisible(getContext(), tool.key);
              hint.setText(
                  visible
                      ? "تمت إضافة " + tool.label + " إلى شريط الأدوات"
                      : "تم إخفاء " + tool.label + " من شريط الأدوات");
              if (callback != null) {
                callback.onToolbarConfigurationChanged();
              }
            }
            adapter.notifyDataSetChanged();
          }
        });

    grid.setOnItemLongClickListener(
        new AdapterView.OnItemLongClickListener() {
          @Override
          public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            Tool tool = tools.get(position);
            if (!tool.toolbarCapable) {
              return true;
            }
            boolean visible = ToolPreferences.toggleVisible(getContext(), tool.key);
            hint.setText(
                visible
                    ? "تمت إضافة " + tool.label + " إلى شريط الأدوات"
                    : "تم إخفاء " + tool.label + " من شريط الأدوات");
            adapter.notifyDataSetChanged();
            if (callback != null) {
              callback.onToolbarConfigurationChanged();
            }
            return true;
          }
        });
  }

  public void setCallback(Callback value) {
    callback = value;
  }

  public void setTheme(KeyboardTheme value) {
    if (value == null) {
      return;
    }
    theme = value;
    // Round 52: خلفية الثيم نفسها إن وُجدت — وإلا لون السطح كما كان
    com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(this, getContext(), theme);
    if (getChildCount() > 0) {
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(getChildAt(0), getContext(), theme);
    }
    int uiColor = interfaceTextColor();
    hint.setTextColor(blend(uiColor, theme.surface, .35f));
    editButton.setTextColor(uiColor);
    GradientDrawable editBackground = new GradientDrawable();
    editBackground.setColor(theme.key);
    editBackground.setStroke(dp(1), theme.sub);
    editBackground.setCornerRadius(dp(21));
    editButton.setBackground(editBackground);
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
  }

  public void releaseTransientState() {
    editMode = false;
    selectedPosition = -1;
    clearAnimation();
    setVisibility(GONE);
    if (editButton != null) editButton.setText("تعديل شريط الأدوات");
    if (adapter != null) adapter.notifyDataSetChanged();
  }

  public void refresh() {
    reloadOrder();
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
  }

  private void add(String key, String label, int icon, boolean toolbarCapable) {
    catalog.put(key, new Tool(key, label, icon, toolbarCapable));
  }

  private void reloadOrder() {
    tools.clear();
    List<String> order = ToolPreferences.order(getContext());
    for (String key : order) {
      Tool tool = catalog.get(key);
      if (tool != null) {
        tools.add(tool);
      }
    }
  }

  private void saveOrder() {
    ArrayList<String> keys = new ArrayList<String>();
    for (Tool tool : tools) {
      keys.add(tool.key);
    }
    ToolPreferences.saveOrder(getContext(), keys);
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + .5f);
  }

  private int interfaceTextColor() {
    return com.almlk.swiftkey.theme.ThemeChipArt.textColor(getContext(), theme);
  }

  private int blend(int first, int second, float amount) {
    float keep = 1f - amount;
    return android.graphics.Color.rgb(
        Math.round(android.graphics.Color.red(first) * keep + android.graphics.Color.red(second) * amount),
        Math.round(android.graphics.Color.green(first) * keep + android.graphics.Color.green(second) * amount),
        Math.round(android.graphics.Color.blue(first) * keep + android.graphics.Color.blue(second) * amount));
  }

  private final class ToolAdapter extends BaseAdapter {
    @Override
    public int getCount() {
      return tools.size();
    }

    @Override
    public Object getItem(int position) {
      return tools.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      LinearLayout cell;
      if (convertView instanceof LinearLayout) {
        cell = (LinearLayout) convertView;
      } else {
        cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(5), dp(7), dp(5), dp(5));
        ImageView icon = new ImageView(getContext());
        icon.setId(android.R.id.icon);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView label = new TextView(getContext());
        label.setId(android.R.id.text1);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(14);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelParams =
            new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(28));
        cell.addView(label, labelParams);
      }

      Tool tool = tools.get(position);
      ImageView icon = (ImageView) cell.findViewById(android.R.id.icon);
      TextView label = (TextView) cell.findViewById(android.R.id.text1);
      icon.setImageResource(tool.icon);
      int uiColor = interfaceTextColor();
      icon.setColorFilter(uiColor);
      label.setText(tool.label);
      label.setTextColor(uiColor);

      boolean visible = tool.toolbarCapable && ToolPreferences.isVisible(getContext(), tool.key);
      label.setText(tool.label + (visible ? "  ✓" : ""));
      label.setContentDescription(
          visible ? tool.label + "، مستخدمة" : tool.label + "، غير مستخدمة");
      boolean selected = editMode && selectedPosition == position;
      GradientDrawable background = new GradientDrawable();
      background.setColor(selected ? (theme.accent & 0x33ffffff) : theme.key);
      background.setStroke(dp(1), visible ? theme.accent : (theme.sub & 0x66ffffff));
      background.setCornerRadius(dp(12));
      cell.setBackground(background);
      cell.setAlpha(tool.toolbarCapable && !visible ? .72f : 1f);
      cell.setContentDescription(tool.label + (visible ? "، ظاهر في الشريط" : ""));
      return cell;
    }
  }
}
