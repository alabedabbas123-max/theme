package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.Toast;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.settings.OnlineAssetStore;
import com.almlk.swiftkey.settings.ThematyStore;
import com.almlk.swiftkey.theme.KeyboardTheme;
import java.util.ArrayList;
import java.util.List;

/**
 * Round 70: لوحة متجر الملصقات داخل لوحة المفاتيح — تعرض حزم الملصقات
 * من مكتبة ثيماتي مع إمكانية التنزيل والتطبيق المباشر.
 */
public final class StickerStorePanelView extends FrameLayout {

  public interface Callback {
    void onStickerSelected(String stickerText);
    void onClose();
  }

  private Callback callback;
  private LinearLayout contentContainer;
  private KeyboardTheme theme = KeyboardTheme.from("light");
  private int storeGeneration;

  public StickerStorePanelView(Context context) {
    super(context);
    initialize();
  }

  public StickerStorePanelView(Context context, AttributeSet attrs) {
    super(context, attrs);
    initialize();
  }

  private void initialize() {
    // إنشاء الهيكل الأساسي
    setBackgroundColor(Color.WHITE);

    LinearLayout root = new LinearLayout(getContext());
    root.setOrientation(LinearLayout.VERTICAL);
    root.setLayoutParams(new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));

    // شريط العنوان
    LinearLayout header = new LinearLayout(getContext());
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setBackgroundColor(0xFFF5F5F5);
    header.setPadding(dp(16), dp(12), dp(16), dp(12));

    TextView title = new TextView(getContext());
    title.setText("متجر الملصقات");
    title.setTextSize(18);
    title.setTextColor(interfaceTextColor());
    title.setLayoutParams(new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

    ImageButton closeBtn = new ImageButton(getContext());
    closeBtn.setImageResource(R.drawable.ic_close);
    closeBtn.setColorFilter(interfaceTextColor());
    closeBtn.setBackgroundColor(Color.TRANSPARENT);
    closeBtn.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        if (callback != null) callback.onClose();
      }
    });

    header.addView(title);
    header.addView(closeBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
    root.addView(header, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    // شريط أقسام الملصقات
    HorizontalScrollView packScroll = new HorizontalScrollView(getContext());
    packScroll.setHorizontalScrollBarEnabled(false);
    LinearLayout packBar = new LinearLayout(getContext());
    packBar.setOrientation(LinearLayout.HORIZONTAL);
    packBar.setGravity(Gravity.CENTER_VERTICAL);
    packBar.setPadding(dp(8), dp(8), dp(8), dp(8));
    packBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    final String[] packNames = {"الكل", "وجوه", "حيوانات", "طعام", ".activities", "رموز"};
    for (int i = 0; i < packNames.length; i++) {
      TextView chip = new TextView(getContext());
      chip.setText(packNames[i]);
      chip.setTextSize(13);
      chip.setTextColor(interfaceTextColor());
      chip.setGravity(Gravity.CENTER);
      chip.setPadding(dp(16), dp(8), dp(16), dp(8));
      final int index = i;
      chip.setOnClickListener(new OnClickListener() {
        public void onClick(View v) {
          // تصفية حسب القسم
          showMessage("قسم: " + packNames[index]);
        }
      });
      LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
      chipParams.setMargins(dp(4), 0, dp(4), 0);
      packBar.addView(chip, chipParams);
    }

    packScroll.addView(packBar);
    root.addView(packScroll, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    // محتوى المتجر
    ScrollView scroll = new ScrollView(getContext());
    scroll.setFillViewport(true);
    scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

    contentContainer = new LinearLayout(getContext());
    contentContainer.setOrientation(LinearLayout.VERTICAL);
    contentContainer.setPadding(dp(8), dp(8), dp(8), dp(8));

    scroll.addView(contentContainer, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));

    root.addView(scroll, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    addView(root);

    // تحميل حزم الملصقات
    loadStickerPacks();
  }

  private void loadStickerPacks() {
    final int generation = ++storeGeneration;
    contentContainer.removeAllViews();

    // رسالة تحميل
    TextView loading = new TextView(getContext());
    loading.setText("جارٍ جلب حزم الملصقات…");
    loading.setTextSize(14);
    loading.setTextColor(mutedTextColor());
    loading.setGravity(Gravity.CENTER);
    loading.setPadding(dp(16), dp(24), dp(16), dp(24));
    contentContainer.addView(loading);

    // جلب أقسام الملصقات من ثيماتي
    ThematyStore.sets("sticker", new ThematyStore.SetsListener() {
      public void onReady(List<ThematyStore.SetInfo> sets, String error) {
        if (generation != storeGeneration) return;
        contentContainer.removeAllViews();

        if (sets == null || sets.isEmpty()) {
          // عرض ملصقات افتراضية
          showDefaultStickers();
          return;
        }

        for (int i = 0; i < sets.size(); i++) {
          final ThematyStore.SetInfo set = sets.get(i);
          addStickerSection(set.title, set.count, set.id, generation);
        }
      }
    });
  }

  private void showDefaultStickers() {
    // حزم ملصقات افتراضية
    String[][] defaultPacks = {
      {"وجوه مبتسمة", "😀😃😄😁😆😅🤣😂🙂🙃😉😊😇"},
      {"قلوب وأحاسيس", "❤️🧡💛💚💙💜🖤🤍🤎💔❣️💕💞💓💗💖💘💝💟"},
      {"حيوانات", "🐶🐱🐭🐹🐰🦊🐻🐼🐨🐯🦁🐮🐷🐸🐵"},
      {"طعام", "🍎🍊🍋🍌🍉🍇🍓🫐🍈🍒🍑🥭🍍🥥🥝"},
      {".activities", "⚽🏀🏈⚾🎾🏐🏉🎱🏓🏸🏒🥅⛳🏹"},
      {"رموز", "✅❌⭕❗❓‼️⁉️💯🔴🟡🟢🔵🟣⚫⚪"}
    };

    for (int i = 0; i < defaultPacks.length; i++) {
      addDefaultStickerPack(defaultPacks[i][0], defaultPacks[i][1]);
    }
  }

  private void addDefaultStickerPack(String title, String emojis) {
    // عنوان القسم
    TextView header = new TextView(getContext());
    header.setText(title);
    header.setTextSize(15);
    header.setTextColor(interfaceTextColor());
    header.setBackgroundColor(Color.TRANSPARENT);
    header.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    header.setPadding(dp(18), 0, dp(18), 0);
    contentContainer.addView(header, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

    // شبكة الملصقات
    LinearLayout grid = new LinearLayout(getContext());
    grid.setOrientation(LinearLayout.VERTICAL);
    grid.setPadding(dp(8), dp(4), dp(8), dp(4));

    String[] stickers = emojis.split("");
    int columns = 6;
    LinearLayout row = null;

    for (int i = 1; i < stickers.length; i++) {
      if ((i - 1) % columns == 0) {
        row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(2), 0, dp(2));
        grid.addView(row, rowParams);
      }

      TextView sticker = new TextView(getContext());
      sticker.setText(stickers[i]);
      sticker.setTextSize(28);
      sticker.setGravity(Gravity.CENTER);
      sticker.setPadding(dp(8), dp(8), dp(8), dp(8));
      final String emoji = stickers[i];
      sticker.setOnClickListener(new OnClickListener() {
        public void onClick(View v) {
          if (callback != null) callback.onStickerSelected(emoji);
        }
      });

      LinearLayout.LayoutParams stickerParams = new LinearLayout.LayoutParams(
          0, dp(56), 1f);
      stickerParams.setMargins(dp(2), 0, dp(2), 0);
      row.addView(sticker, stickerParams);
    }

    contentContainer.addView(grid);
  }

  private void addStickerSection(
      String title, int count, final String setId, final int generation) {
    // عنوان القسم
    TextView header = new TextView(getContext());
    header.setText(title + " (" + count + ")");
    header.setTextSize(15);
    header.setTextColor(interfaceTextColor());
    header.setBackgroundColor(Color.TRANSPARENT);
    header.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    header.setPadding(dp(18), 0, dp(18), 0);
    contentContainer.addView(header, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));

    // شريط أفقي للملصقات
    final HorizontalScrollView scroll = new HorizontalScrollView(getContext());
    scroll.setHorizontalScrollBarEnabled(false);
    final LinearLayout strip = new LinearLayout(getContext());
    strip.setOrientation(LinearLayout.HORIZONTAL);
    strip.setGravity(Gravity.CENTER_VERTICAL);
    strip.setPadding(dp(10), 0, dp(10), 0);
    strip.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

    scroll.addView(strip, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    scroll.setBackgroundColor(Color.TRANSPARENT);

    contentContainer.addView(scroll, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));

    // تحميل ملصقات القسم
    ThematyStore.items("sticker", setId, new OnlineAssetStore.Listener() {
      public void onReady(List<OnlineAssetStore.Item> items, String error) {
        if (generation != storeGeneration) return;
        if (strip.getChildCount() > 0) return;

        if (items == null || items.isEmpty()) {
          TextView note = new TextView(getContext());
          note.setText("لا توجد ملصقات");
          note.setTextSize(12);
          note.setTextColor(mutedTextColor());
          note.setGravity(Gravity.CENTER);
          note.setPadding(dp(8), dp(8), dp(8), dp(8));
          strip.addView(note);
          return;
        }

        for (int i = 0; i < items.size(); i++) {
          addStickerCard(strip, items.get(i), generation);
        }
      }
    });
  }

  private void addStickerCard(
      final LinearLayout strip, final OnlineAssetStore.Item item, final int generation) {
    FrameLayout card = new FrameLayout(getContext());
    card.setBackgroundColor(0xFFF5F5F5);

    final ImageView image = new ImageView(getContext());
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    image.setBackgroundColor(0xFFE0E0E0);
    card.addView(image, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // تحميل الصورة
    ThematyStore.preview(item, 120, new OnlineAssetStore.BitmapListener() {
      public void onReady(Bitmap picture) {
        if (picture != null && generation == storeGeneration) {
          image.setImageBitmap(picture);
        }
      }
    });

    // اسم الملصق
    TextView name = new TextView(getContext());
    name.setText(item.title);
    name.setTextSize(11);
    name.setTextColor(interfaceTextColor());
    name.setGravity(Gravity.CENTER);
    name.setBackgroundColor(0x80FFFFFF);
    name.setPadding(dp(4), dp(2), dp(4), dp(2));
    FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    nameParams.gravity = Gravity.BOTTOM;
    card.addView(name, nameParams);

    card.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        showMessage("تم اختيار: " + item.title);
        if (callback != null) callback.onStickerSelected(item.title);
      }
    });

    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(80), dp(100));
    params.setMargins(dp(4), dp(6), dp(4), dp(6));
    strip.addView(card, params);
  }

  public void setCallback(Callback cb) {
    callback = cb;
  }

  public void setTheme(KeyboardTheme t) {
    if (t == null) return;
    theme = t;
    // تطبيق خلفية الثيم ولون الحروف على متجر الملصقات أيضاً.
    com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(this, getContext(), theme);
    tintInterfaceTree(this);
  }

  private int interfaceTextColor() {
    return com.almlk.swiftkey.theme.ThemeChipArt.textColor(getContext(), theme);
  }

  private int mutedTextColor() {
    return blend(interfaceTextColor(), theme.surface, .38f);
  }

  private int blend(int first, int second, float amount) {
    float keep = 1f - amount;
    return android.graphics.Color.rgb(
        Math.round(android.graphics.Color.red(first) * keep + android.graphics.Color.red(second) * amount),
        Math.round(android.graphics.Color.green(first) * keep + android.graphics.Color.green(second) * amount),
        Math.round(android.graphics.Color.blue(first) * keep + android.graphics.Color.blue(second) * amount));
  }

  private void tintInterfaceTree(View view) {
    if (view instanceof TextView) {
      ((TextView) view).setTextColor(interfaceTextColor());
    } else if (view instanceof ImageButton) {
      ((ImageButton) view).setColorFilter(interfaceTextColor());
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        tintInterfaceTree(group.getChildAt(i));
      }
    }
  }

  private void showMessage(String msg) {
    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }
}