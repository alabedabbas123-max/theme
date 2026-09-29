package com.almlk.swiftkey.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.almlk.swiftkey.theme.KeyArtProcessor;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Round 51 → 67: ذاكرة الأصول الداخلية للكيبورد — حفظ العناصر المختارة
 * من مكتبة «ثيماتي» (ThematyStore) في الذاكرة الداخلية وفهرستها، فتبقى
 * متاحة بلا اتصال وتعود فور إعادة تشغيل الجهاز — إضافة إلى مساعدات
 * بطاقات التحميل (الشارات وحلقة الانتظار) وفك الصور للمعاينات.
 *
 * Round 67: أُلغي بحث الإنترنت القديم (كومنز/Openverse/Wallhaven) كلياً —
 * المصدر الوحيد الآن مكتبة ثيماتي، وهذا الصف لا يفتح أي اتصال شبكي.
 */
public final class OnlineAssetStore {
  /** إطار زر (يُرسم كوجه الزر بأي شكل عبر BitmapShader). */
  public static final String TYPE_FRAME = "frame";
  /** خلفية كيبورد كاملة. */
  public static final String TYPE_BACKGROUND = "background";
  /** صورة تفاعل الضغط المتحرك (تُنثر فوق المفتاح عند ضغطه). */
  public static final String TYPE_EFFECT = "effect";
  /** فن زر المسافة المرافق لإطار أزرار ثيماتي — يُخزن مع نفس عنصر الإطار. */
  public static final String TYPE_SPACE_FRAME = "space_frame";

  private static final String PREFS = "online_assets";

  /** عنصر واحد في الفهرس — path فارغ لعنصر لم يُثبَّت بعد. */
  public static final class Item {
    public final String id;
    public final String title;
    public final String thumb;
    public final String download;
    public final String path;
    /** صورة زر المسافة المرافقة لهذا الزر إن وجدت داخل فهارس ثيماتي. */
    public final String spaceThumb;
    public final String spaceDownload;
    public final String spacePath;

    public Item(String id, String title, String thumb, String download, String path) {
      this(id, title, thumb, download, path, "", "", "");
    }

    public Item(
        String id,
        String title,
        String thumb,
        String download,
        String path,
        String spaceThumb,
        String spaceDownload,
        String spacePath) {
      this.id = id;
      this.title = title;
      this.thumb = thumb;
      this.download = download;
      this.path = path == null ? "" : path;
      this.spaceThumb = spaceThumb == null ? "" : spaceThumb;
      this.spaceDownload = spaceDownload == null ? "" : spaceDownload;
      this.spacePath = spacePath == null ? "" : spacePath;
    }
  }

  /** نتيجة حفظ زر رئيسي مع زر المسافة المرافق له. */
  public static final class SavedAsset {
    public final String path;
    public final String spacePath;

    SavedAsset(String path, String spacePath) {
      this.path = path == null ? "" : path;
      this.spacePath = spacePath == null ? "" : spacePath;
    }
  }

  /** قائمة عناصر قسم أو رسالة خطأ. */
  public interface Listener {
    void onReady(List<Item> items, String error);
  }

  /** مسار الملف المثبَّت أو رسالة خطأ. */
  public interface DownloadListener {
    void onReady(String path, String error);
  }

  /** صورة مفكوكة للمعاينات. */
  public interface BitmapListener {
    void onReady(Bitmap image);
  }

  private OnlineAssetStore() {}

  /**
   * Round 67: يثبّت بيانات عنصر جُلبت من مكتبة ثيماتي — يكتبها في الذاكرة
   * الداخلية ويفهرسها ويسخّن فكّها، ويعيد المسار المحلي. (يحل محل التنزيل
   * الشبكي القديم).
   */
  public static String saveBytes(Context context, String type, Item item, byte[] data)
      throws Exception {
    return saveBytesWithSpace(context, type, item, data, null).path;
  }

  /**
   * يحفظ زر ثيماتي مع صورة زر المسافة المرافقة له. المسار الثاني يُفهرس مع
   * نفس العنصر حتى يطبّقه الرسم لاحقاً على KeySpec.SPACE فقط.
   */
  public static SavedAsset saveBytesWithSpace(
      Context context, String type, Item item, byte[] data, byte[] spaceData)
      throws Exception {
    if (data == null || data.length == 0) throw new IOException("empty");
    File dir = context.getDir("online_" + type, Context.MODE_PRIVATE);
    File out = new File(dir, type + "_" + safe(item.id) + ".img");
    writeFile(out, data);
    String spacePath = "";
    if (spaceData != null && spaceData.length > 0) {
      File spaceOut = new File(dir, type + "_" + safe(item.id) + "_space.img");
      writeFile(spaceOut, spaceData);
      spacePath = spaceOut.getAbsolutePath();
      warmArt(TYPE_SPACE_FRAME, spacePath);
    }
    markSaved(context, type, item, out.getAbsolutePath(), spacePath);
    warmArt(type, out.getAbsolutePath()); // تسخين الفك قبل التطبيق كي لا تتجمد الواجهة
    return new SavedAsset(out.getAbsolutePath(), spacePath);
  }

  private static void writeFile(File out, byte[] data) throws Exception {
    FileOutputStream stream = new FileOutputStream(out);
    try {
      stream.write(data);
    } finally {
      stream.close();
    }
  }

  /**
   * تسخين فكّ الصور على خيط التحميل نفسه — كي لا يفكّها محرك الكيبورد
   * على خيطه الرئيسي لحظة التطبيق فتتجمد الواجهة (الإطار عبر fileKeyArt،
   * وتفاعل الضغط عبر fileSprite المحدود).
   */
  private static void warmArt(String type, String path) {
    try {
      if (TYPE_FRAME.equals(type)) {
        KeyArtProcessor.fileKeyArt(path);
      } else if (TYPE_SPACE_FRAME.equals(type)) {
        KeyArtProcessor.fileSpaceArt(path);
      } else if (TYPE_EFFECT.equals(type)) {
        KeyArtProcessor.fileSprite(path);
      }
    } catch (Exception ignored) {
    }
  }

  /**
   * شارة حالة عنصر التحميل — دليل واضح على كل بطاقة:
   * «↓» بدائرة زرقاء = يحتاج تثبيتاً، «✓» بدائرة خضراء = مثبَّت مسبقاً،
   * «✓» بدائرة لون التطبيق = المطبَّق حالياً.
   */
  public static TextView statusBadge(
      Context context,
      boolean stored,
      boolean applied,
      int appliedBg,
      int appliedText,
      int sizePx) {
    TextView badge = new TextView(context);
    badge.setText(applied || stored ? "✓" : "↓");
    badge.setTextSize(12);
    badge.setGravity(Gravity.CENTER);
    GradientDrawable circle = new GradientDrawable();
    circle.setShape(GradientDrawable.OVAL);
    if (applied) {
      circle.setColor(appliedBg);
      badge.setTextColor(appliedText);
    } else if (stored) {
      circle.setColor(0xff2e9e4f); // أخضر: مثبَّت مسبقاً
      badge.setTextColor(Color.WHITE);
    } else {
      circle.setColor(0xff168fe5); // أزرق: سهم التثبيت
      badge.setTextColor(Color.WHITE);
    }
    badge.setBackground(circle);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(sizePx, sizePx);
    params.gravity = Gravity.RIGHT | Gravity.BOTTOM;
    params.setMargins(0, 0, sizePx / 9, sizePx / 9);
    badge.setLayoutParams(params);
    return badge;
  }

  /** حلقة تحميل دوّارة تُعلَّق فوق العنصر أثناء جلبه من المكتبة. */
  public static ProgressBar loadingRing(Context context) {
    ProgressBar ring = new ProgressBar(context);
    ring.setIndeterminate(true);
    return ring;
  }

  /** العناصر المثبَّة محلياً لنوع ما (متاحة بلا اتصال). */
  public static List<Item> saved(Context context, String type) {
    List<Item> items = new ArrayList<Item>();
    try {
      JSONArray raw = new JSONArray(prefs(context).getString("index_" + type, "[]"));
      for (int index = 0; index < raw.length(); index++) {
        JSONObject entry = raw.getJSONObject(index);
        items.add(
            new Item(
                entry.optString("id"),
                entry.optString("title"),
                entry.optString("thumb"),
                entry.optString("download"),
                entry.optString("path"),
                entry.optString("spaceThumb"),
                entry.optString("spaceDownload"),
                entry.optString("spacePath")));
      }
    } catch (Exception ignored) {
    }
    return items;
  }

  /** العنصر المثبَّت المطابق للمعرف إن وُجد. */
  public static Item savedItem(Context context, String type, String id) {
    List<Item> items = saved(context, type);
    for (int index = 0; index < items.size(); index++) {
      if (items.get(index).id.equals(id)) return items.get(index);
    }
    return null;
  }

  /** حذف عنصر مثبَّت (الملف + الفهرس). */
  public static void remove(Context context, String type, String id) {
    Item item = savedItem(context, type, id);
    if (item == null) return;
    if (item.path.length() > 0) {
      try {
        new File(item.path).delete();
      } catch (Exception ignored) {
      }
    }
    if (item.spacePath.length() > 0) {
      try {
        new File(item.spacePath).delete();
      } catch (Exception ignored) {
      }
    }
    try {
      JSONArray raw = new JSONArray(prefs(context).getString("index_" + type, "[]"));
      JSONArray kept = new JSONArray();
      for (int index = 0; index < raw.length(); index++) {
        if (!raw.getJSONObject(index).optString("id").equals(id))
          kept.put(raw.getJSONObject(index));
      }
      prefs(context).edit().putString("index_" + type, kept.toString()).apply();
    } catch (Exception ignored) {
    }
  }

  // ------------------------------------------------------------ فك الصور

  /** فك صورة من بايتات بمقياس مضبوط لا يتجاوز الحد المعطى. */
  public static Bitmap decodeBytes(byte[] data, int maxEdge) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample(bounds.outWidth, bounds.outHeight, maxEdge);
    return BitmapFactory.decodeByteArray(data, 0, data.length, options);
  }

  /** فك صورة من ملف محلي بمقياس مضبوط لا يتجاوز الحد المعطى. */
  public static Bitmap decodeFile(String path, int maxEdge) {
    BitmapFactory.Options bounds = new BitmapFactory.Options();
    bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(path, bounds);
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sample(bounds.outWidth, bounds.outHeight, maxEdge);
    return BitmapFactory.decodeFile(path, options);
  }

  private static int sample(int width, int height, int maxEdge) {
    int sample = 1;
    while (width / (sample * 2) >= maxEdge && height / (sample * 2) >= maxEdge) sample *= 2;
    return sample;
  }

  // ------------------------------------------------------------ داخلية

  private static SharedPreferences prefs(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private static void markSaved(
      Context context, String type, Item item, String path, String spacePath) {
    try {
      JSONArray raw = new JSONArray(prefs(context).getString("index_" + type, "[]"));
      JSONArray next = new JSONArray();
      next.put(
          new JSONObject()
              .put("id", item.id)
              .put("title", item.title)
              .put("thumb", item.thumb)
              .put("download", item.download)
              .put("path", path)
              .put("spaceThumb", item.spaceThumb)
              .put("spaceDownload", item.spaceDownload)
              .put("spacePath", spacePath == null ? "" : spacePath));
      for (int index = 0; index < raw.length(); index++) {
        if (!raw.getJSONObject(index).optString("id").equals(item.id))
          next.put(raw.getJSONObject(index));
      }
      prefs(context).edit().putString("index_" + type, next.toString()).apply();
    } catch (Exception ignored) {
    }
  }

  private static String safe(String id) {
    StringBuilder out = new StringBuilder();
    for (int index = 0; index < id.length(); index++) {
      char c = id.charAt(index);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) out.append(c);
      else out.append('_');
    }
    return out.length() == 0 ? "asset" : out.toString();
  }

  /** اسم الخطأ المختصر — يظهر بجوار رسالة التعذر لتشخيص السبب. */
  public static String shortError(String error) {
    if (error == null) return "";
    int colon = error.indexOf(':');
    String head = colon > 0 ? error.substring(0, colon) : error;
    int dot = head.lastIndexOf('.');
    String name = dot >= 0 ? head.substring(dot + 1) : head;
    return name.length() == 0 ? error : name;
  }
}
