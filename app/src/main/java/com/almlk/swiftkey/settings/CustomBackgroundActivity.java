package com.almlk.swiftkey.settings;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;
import java.util.ArrayList;

/** First custom-theme step: a categorized background gallery plus a device photo picker. */
public final class CustomBackgroundActivity extends AppCompatActivity {
  public static final String EXTRA_BACKGROUND = "selected_background";
  public static final String EXTRA_CURRENT = "current_background";
  private static final int PICK_PHOTO = 92;
  // Round 49: عشر صور حقيقية (طبيعة/مشاهير/رومانسي/ورود/وردي) بدل الأشكال
  // التدرجية القديمة وملفات kpop/romantic الوهمية المكررة.
  private static final String[] NATURE = {
    "custom_bg_nature_lake", "custom_bg_nature_forest"
  };
  private static final String[] STARS = {
    "custom_bg_star_male", "custom_bg_star_female"
  };
  private static final String[] ROMANTIC = {
    "custom_bg_love_sunset", "custom_bg_love_hands"
  };
  private static final String[] ROSES = {
    "custom_bg_roses_red", "custom_bg_roses_pink"
  };
  private static final String[] PINK = {
    "custom_bg_pink_sky", "custom_bg_pink_soft"
  };
  private final ArrayList<Bitmap> thumbnails = new ArrayList<Bitmap>();
  private LinearLayout categories;
  private String current;
  // Round 67: خلفيات مكتبة ثيماتي — تُقرأ من الأرشيف على GitHub عن بُعد
  // بطلبات النطاق الجزئية، والمختار يُحفظ في ذاكرة التطبيق الداخلية.
  private GridLayout onlineQueries; // Round 57: شبكة بطاقات لا شريط أفقي
  private GridLayout onlineGrid;
  private String onlineSet = "";
  private boolean bgReload;
  private java.util.List<OnlineAssetStore.Item> onlineResults;
  /** Round 59: حالة قسم الإنترنت داخل اللستة الموحدة — null = النتائج جاهزة. */
  private String bgStatus;
  private String bgRetry;
  private java.util.List<ThematyStore.SetInfo> bgSets;
  private final ArrayList<Bitmap> onlineThumbnails = new ArrayList<Bitmap>();

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.activity_custom_background);
    AppGate.ensureInternet(this); // Round 57: تفعيل صلاحية الإنترنت عند فتح المنتقي
    current = getIntent().getStringExtra(EXTRA_CURRENT);
    if (current == null) current = "";
    categories = (LinearLayout) findViewById(R.id.background_category_container);
    findViewById(R.id.background_picker_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });
    findViewById(R.id.background_choose_photo)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, PICK_PHOTO);
              }
            });
    addCategory("طبيعة", NATURE);
    addCategory("مشاهير", STARS);
    addCategory("رومانسي", ROMANTIC);
    addCategory("ورود", ROSES);
    addCategory("وردي", PINK);
    addOnlineCategory(); // Round 51: مكتبة لا نهائية من الموقع بلا تكبير الحزمة
  }

  private void addCategory(String title, String[] names) {
    TextView heading = new TextView(this);
    heading.setText(title);
    heading.setTextColor(0xff2c3038);
    heading.setTextSize(17);
    heading.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
    heading.setPadding(dp(16), dp(14), dp(16), dp(6));
    categories.addView(
        heading, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));

    GridLayout grid = new GridLayout(this);
    grid.setColumnCount(3); // Round 49: صورتان لكل فئة بمعاينة كبيرة وواضحة
    grid.setPadding(dp(5), 0, dp(5), dp(8));
    categories.addView(
        grid,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    int cell = (getResources().getDisplayMetrics().widthPixels - dp(20)) / 3;
    for (int index = 0; index < names.length; index++) {
      final String source = names[index];
      int resource = getResources().getIdentifier(source, "drawable", getPackageName());
      final FrameLayout frame = new FrameLayout(this);
      GradientDrawable outline = new GradientDrawable();
      outline.setColor(Color.WHITE);
      outline.setCornerRadius(dp(7));
      outline.setStroke(
          dp(current.equals(source) ? 3 : 1), current.equals(source) ? 0xff168fe5 : 0xffe0e3e8);
      frame.setBackground(outline);
      frame.setPadding(dp(3), dp(3), dp(3), dp(3));
      final ImageView image = new ImageView(this);
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = 4;
      Bitmap thumbnail = BitmapFactory.decodeResource(getResources(), resource, options);
      if (thumbnail != null) thumbnails.add(thumbnail);
      image.setImageBitmap(thumbnail);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      frame.addView(
          image,
          new FrameLayout.LayoutParams(
              FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
      if (current.equals(source)) {
        TextView selected = new TextView(this);
        selected.setText("✓");
        selected.setTextColor(Color.WHITE);
        selected.setTextSize(13);
        selected.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xff168fe5);
        selected.setBackground(circle);
        FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(24), dp(24));
        badge.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        badge.setMargins(0, 0, dp(3), dp(3));
        frame.addView(selected, badge);
      }
      frame.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              returnBackground(source);
            }
          });
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = cell;
      params.height = cell;
      params.setMargins(dp(2), dp(2), dp(2), dp(2));
      grid.addView(frame, params);
    }
  }

  // ------------------------------------------------------------ Round 51

  /** قسم الخلفيات أونلاين — عنوان + رقائق استعلام + شبكة نتائج. */
  private void addOnlineCategory() {
    TextView heading = new TextView(this);
    heading.setText("خلفيات من مكتبة ثيماتي");
    heading.setTextColor(0xff2c3038);
    heading.setTextSize(17);
    heading.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
    heading.setPadding(dp(16), dp(14), dp(16), dp(6));
    categories.addView(
        heading, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
    // Round 59: لستة واحدة — عناوين التحميل ونتائجه في شبكة واحدة بنفس تصميم الخلفيات
    onlineGrid = new GridLayout(this);
    onlineGrid.setColumnCount(3);
    onlineGrid.setPadding(dp(5), 0, dp(5), dp(8));
    categories.addView(
        onlineGrid,
        new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    onlineQueries = onlineGrid; // اللستة الموحدة نفسها — لا شريط منفصل
    loadBgSets(); // Round 67: أقسام مكتبة ثيماتي ثم عرض أول قسم
  }

  /** عدد عناوين الخلفيات: المكتبة أولاً ثم الارتتجاع المدمج. */
  /** Round 67: أقسام خلفيات مكتبة ثيماتي (مجموعات الأزرار المطابقة 1:1). */
  private int bgSetCount() {
    return bgSets == null ? 0 : bgSets.size();
  }

  private String bgSetTitle(int index) {
    return bgSets.get(index).title;
  }

  private String bgSetId(int index) {
    return bgSets.get(index).id;
  }

  /** Round 67: أقسام خلفيات مكتبة ثيماتي — من الأرشيف عن بُعد ثم أول قسم. */
  private void loadBgSets() {
    bgStatus = "جارٍ تحميل مكتبة ثيماتي…";
    bgRetry = null;
    bgReload = true;
    buildOnlineQueries();
    ThematyStore.sets(
        OnlineAssetStore.TYPE_BACKGROUND,
        new ThematyStore.SetsListener() {
          public void onReady(
              java.util.List<ThematyStore.SetInfo> sets, String error) {
            if (sets == null || sets.isEmpty()) {
              bgStatus =
                  "تعذر الوصول لمكتبة ثيماتي (" + OnlineAssetStore.shortError(error)
                      + ") — انقر للإعادة";
              bgRetry = null;
              bgReload = true;
              buildOnlineQueries();
              return;
            }
            bgSets = sets;
            bgReload = false;
            showThematyBackgrounds(sets.get(0).id);
          }
        });
  }

  /** Round 57: عناوين التحميل بطاقات بنفس تصميم الخلفيات الافتراضية. */
  /** Round 59: لستة واحدة — العناوين ثم الحالة/النتائج في الشبكة نفسها 3 في الصف. */
  private void buildOnlineQueries() {
    onlineQueries.removeAllViews();
    int cell = (getResources().getDisplayMetrics().widthPixels - dp(20)) / 3;
    for (int index = 0; index < bgSetCount(); index++) {
      final String setId = bgSetId(index);
      boolean chosen = onlineSet.equals(setId);
      final LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.VERTICAL);
      GradientDrawable border = new GradientDrawable();
      border.setColor(chosen ? 0xffcfe8fb : Color.WHITE);
      border.setCornerRadius(dp(7));
      border.setStroke(dp(chosen ? 3 : 1), chosen ? 0xff168fe5 : 0xffe0e3e8);
      card.setBackground(border);
      card.setGravity(Gravity.CENTER);
      card.setContentDescription(bgSetTitle(index));
      TextView title = new TextView(this);
      title.setText(bgSetTitle(index));
      title.setTextSize(13);
      title.setTextColor(chosen ? 0xff0b5a8f : 0xff37424e);
      title.setGravity(Gravity.CENTER);
      title.setMaxLines(2);
      card.addView(
          title,
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT,
              LinearLayout.LayoutParams.WRAP_CONTENT));
      card.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              showThematyBackgrounds(setId);
            }
          });
      GridLayout.LayoutParams params = new GridLayout.LayoutParams();
      params.width = cell;
      params.height = dp(72);
      params.setMargins(dp(2), dp(2), dp(2), dp(2));
      onlineQueries.addView(card, params);
    }
    if (bgStatus != null) {
      onlineQueries.addView(onlineNote(bgStatus, bgRetry));
    } else {
      renderOnlineBackgrounds(cell);
    }
  }

  /** Round 67: خلفيات قسم من مكتبة ثيماتي — المحفوظ أولاً ثم عناصر القسم. */
  private void showThematyBackgrounds(final String setId) {
    onlineSet = setId;
    bgStatus = "جارٍ جلب الخلفيات…";
    bgRetry = null;
    bgReload = false;
    onlineResults = null;
    buildOnlineQueries();
    ThematyStore.items(
        OnlineAssetStore.TYPE_BACKGROUND,
        setId,
        new OnlineAssetStore.Listener() {
          public void onReady(
              java.util.List<OnlineAssetStore.Item> items, String error) {
            if (!onlineSet.equals(setId)) return; // قسم أحدث حلّ محله
            if (error != null || items == null) {
              bgStatus =
                  "تعذر الوصول لمكتبة ثيماتي (" + OnlineAssetStore.shortError(error)
                      + ") — انقر للإعادة";
              bgRetry = setId;
            } else {
              onlineResults = items;
              bgStatus = null;
              bgRetry = null;
            }
            buildOnlineQueries();
          }
        });
  }

  /** بطاقة نص لحالة الشبكة — اختيارية النقر لإعادة المحاولة. */
  private TextView onlineNote(final String message, final String retryQuery) {
    TextView note = new TextView(this);
    note.setText(message);
    note.setTextSize(13);
    note.setTextColor(0xff37424e);
    note.setGravity(Gravity.CENTER);
    note.setPadding(dp(8), dp(12), dp(8), dp(12));
    if (retryQuery != null) {
      note.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              showThematyBackgrounds(retryQuery);
            }
          });
    } else if (bgReload) {
      note.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              loadBgSets();
            }
          });
    }
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    params.columnSpec = android.widget.GridLayout.spec(0, 3);
    params.width = GridLayout.LayoutParams.MATCH_PARENT;
    params.setMargins(dp(4), dp(8), dp(4), dp(8));
    note.setLayoutParams(params);
    return note;
  }

  /** شبكة الخلفيات الأونلاين — المحفوظ أولاً ثم نتائج البحث غير المحفوظة. */
  /** Round 59: نتائج الخلفيات الأونلاين داخل اللستة الموحدة نفسها. */
  private void renderOnlineBackgrounds(int cell) {
    java.util.List<OnlineAssetStore.Item> merged =
        new java.util.ArrayList<OnlineAssetStore.Item>();
    java.util.List<OnlineAssetStore.Item> saved =
        OnlineAssetStore.saved(this, OnlineAssetStore.TYPE_BACKGROUND);
    for (int index = 0; index < saved.size(); index++) merged.add(saved.get(index));
    if (onlineResults != null) {
      for (int index = 0; index < onlineResults.size(); index++) {
        OnlineAssetStore.Item item = onlineResults.get(index);
        if (OnlineAssetStore.savedItem(this, OnlineAssetStore.TYPE_BACKGROUND, item.id) == null)
          merged.add(item);
      }
    }
    if (merged.isEmpty()) {
      onlineGrid.addView(onlineNote("لا عناصر في هذا القسم", null));
      return;
    }
    for (int index = 0; index < merged.size(); index++) {
      addOnlineBackgroundCard(merged.get(index), cell);
    }
  }

  /** بطاقة خلفية أونلاين: مصغرة + شارة حالة + نقرة تحمّل ثم تُعيد المسار. */
  private void addOnlineBackgroundCard(final OnlineAssetStore.Item item, int cell) {
    final boolean stored = item.path.length() > 0;
    final boolean applied = stored && ("file://" + item.path).equals(current);
    // Round 65: final صريح — مترجم AIDE (جافا 7) يرفض التقاط غير النهائي
    final FrameLayout frame = new FrameLayout(this);
    GradientDrawable outline = new GradientDrawable();
    outline.setColor(Color.WHITE);
    outline.setCornerRadius(dp(7));
    outline.setStroke(
        dp(applied ? 3 : 1), applied ? 0xff168fe5 : 0xffe0e3e8);
    frame.setBackground(outline);
    frame.setPadding(dp(3), dp(3), dp(3), dp(3));
    frame.setContentDescription(item.title);
    final ImageView image = new ImageView(this);
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    frame.addView(
        image,
        new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    ThematyStore.preview(
        item,
        480,
        new OnlineAssetStore.BitmapListener() {
          public void onReady(Bitmap art) {
            if (art != null && !isFinishing()) {
              onlineThumbnails.add(art);
              image.setImageBitmap(art);
            }
          }
        });
    // Round 63: شارة الحالة — ↓ أزرق يحتاج تحميلاً، ✓ أخضر محمَّل، ✓ أزرق مطبَّق
    frame.addView(
        OnlineAssetStore.statusBadge(this, stored, applied, 0xff168fe5, Color.WHITE, dp(24)));
    frame.setOnClickListener(
        new View.OnClickListener() {
          public void onClick(View view) {
            OnlineAssetStore.Item local =
                OnlineAssetStore.savedItem(
                    CustomBackgroundActivity.this,
                    OnlineAssetStore.TYPE_BACKGROUND,
                    item.id);
            if (local != null) {
              returnBackground("file://" + local.path);
              return;
            }
            // Round 63: لا تحميلاً مزدوجاً — حلقة قائمة تعني تحميلاً جارياً
            for (int child = 0; child < frame.getChildCount(); child++) {
              if (frame.getChildAt(child) instanceof android.widget.ProgressBar) return;
            }
            final android.widget.ProgressBar ring =
                OnlineAssetStore.loadingRing(CustomBackgroundActivity.this);
            FrameLayout.LayoutParams ringParams =
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);
            frame.addView(ring, ringParams);
            Toast.makeText(
                    CustomBackgroundActivity.this, "جارٍ جلب الخلفية من المكتبة…", Toast.LENGTH_SHORT)
                .show();
            ThematyStore.install(
                CustomBackgroundActivity.this,
                OnlineAssetStore.TYPE_BACKGROUND,
                item,
                new OnlineAssetStore.DownloadListener() {
                  public void onReady(String path, String error) {
                    frame.removeView(ring); // Round 63: إيقاف الحلقة فور انتهاء التحميل
                    if (error != null || path == null) {
                      Toast.makeText(
                              CustomBackgroundActivity.this,
                              "تعذر الجلب من المكتبة — تحقق من الاتصال",
                              Toast.LENGTH_SHORT)
                          .show();
                      return;
                    }
                    returnBackground("file://" + path);
                  }
                });
          }
        });
    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
    params.width = cell;
    params.height = cell;
    params.setMargins(dp(2), dp(2), dp(2), dp(2));
    onlineGrid.addView(frame, params);
  }

  private void returnBackground(String source) {
    Intent result = new Intent();
    result.putExtra(EXTRA_BACKGROUND, source);
    setResult(RESULT_OK, result);
    finish();
  }

  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == PICK_PHOTO && result == RESULT_OK && data != null && data.getData() != null) {
      Uri uri = data.getData();
      try {
        getContentResolver()
            .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (Exception ignored) {
      }
      returnBackground(uri.toString());
    }
  }

  protected void onDestroy() {
    for (Bitmap thumbnail : thumbnails) {
      if (thumbnail != null && !thumbnail.isRecycled()) thumbnail.recycle();
    }
    thumbnails.clear();
    for (Bitmap thumbnail : onlineThumbnails) { // Round 51: مصغرات الإنترنت كذلك
      if (thumbnail != null && !thumbnail.isRecycled()) thumbnail.recycle();
    }
    onlineThumbnails.clear();
    super.onDestroy();
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }
}
