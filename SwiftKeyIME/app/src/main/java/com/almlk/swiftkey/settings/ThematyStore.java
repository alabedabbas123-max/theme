package com.almlk.swiftkey.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import com.almlk.swiftkey.BuildConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Round 67: مكتبة «ثيماتي» — مصدر الأزرار والخلفيات والتفاعلات والثيمات.
 *
 * الطريقة: يُقرأ أرشيف المشروع (9.9MB على GitHub) عن بُعد عبر طلبات
 * النطاق الجزئية بلا تنزيله كاملاً وبلا أي زيادة في حجم التطبيق:
 *
 * 1) فهرس الأرشيف من نهايته فقط (ذيل ~70KB) — ZipRangeReader.
 * 2) ملفات الفهارس النصية (buttons/backgrounds/effects/themes —
 *    ~0.3MB إجمالاً) تُجلب وحدها وتُخزَّن في الذاكرة طوال الجلسة.
 * 3) أقسام المكتبة تظهر بطاقاتها فوراً — والعنصر الذي يختاره المستخدم
 *    يُجلب وحده من داخل الأرشيف (~20-90KB) ويُحفظ في الذاكرة الداخلية
 *    للتطبيق عبر OnlineAssetStore.saveBytes فيبقى متاحاً بلا اتصال.
 *
 * يحل هذا محل بحث الإنترنت القديم (كومنز/Openverse/Wallhaven) كلياً.
 */
public final class ThematyStore {

  /** رابط أرشيف متجر الثيمات الرسمي — raw يدعم Range، عكس رابط blob. */
  public static final String ZIP_URL =
      "https://raw.githubusercontent.com/alabedabbas123-max/themes/main/Abbas_theme.zip";

  /** توكن اختياري يُحقن محليًا من local.properties ولا يُحفظ في المصدر. */
  private static final String GITHUB_TOKEN = BuildConfig.THEMATY_GITHUB_TOKEN;

  static final String PREFIX = "thematy/public/";
  static final String JSON_BUTTONS = PREFIX + "downloads/json/buttons.json";
  static final String JSON_BACKGROUNDS = PREFIX + "downloads/json/backgrounds.json";
  static final String JSON_EFFECTS = PREFIX + "downloads/json/effects.json";
  // Round 68: themes.json هو مصدر بيانات الثيمات الكاملة (زر + خلفية + ألوان)
  static final String JSON_THEMES = PREFIX + "downloads/json/themes.json";

  /** نوع رابع: أقسام الثيمات الكاملة (زر + خلفية متناسقان). */
  public static final String TYPE_THEME = "theme";

  private static final String USER_AGENT = "AlmlkKeyboard/1.9 (Android keyboard; custom themes)";

  /** قسم واحد من أقسام المكتبة (مجموعة أزرار / تاغ تفاعلات / قسم ثيمات). */
  public static final class SetInfo {
    public final String id;
    public final String title;
    public final int count;

    SetInfo(String id, String title, int count) {
      this.id = id;
      this.title = title;
      this.count = count;
    }
  }

  /**
   * ثيم كامل من themes.json: زر وخلفية متناسقان جاهزان للتطبيق بنقرة واحدة،
   * ومعهما ألوان الثيم (base/dark/light/text/toolbarText) لرسم المعاينة
   * والمفاتيح فوراً بلا أي جلب إضافي.
   */
  public static final class ThemeInfo {
    public final String id;
    public final String name;
    public final String setAr;
    public final String buttonEntry;
    public final String spaceEntry;
    public final String backgroundEntry;
    public final int base;
    public final int dark;
    public final int light;
    public final int textColor;
    public final int toolbarText;

    ThemeInfo(
        String id,
        String name,
        String setAr,
        String buttonEntry,
        String spaceEntry,
        String backgroundEntry,
        int base,
        int dark,
        int light,
        int textColor,
        int toolbarText) {
      this.id = id;
      this.name = name;
      this.setAr = setAr;
      this.buttonEntry = buttonEntry;
      this.spaceEntry = spaceEntry == null ? "" : spaceEntry;
      this.backgroundEntry = backgroundEntry;
      this.base = base;
      this.dark = dark;
      this.light = light;
      this.textColor = textColor;
      this.toolbarText = toolbarText;
    }
  }

  /** أقسام المكتبة أو رسالة خطأ. */
  public interface SetsListener {
    void onReady(List<SetInfo> sets, String error);
  }

  /** ثيمات قسم معين أو رسالة خطأ. */
  public interface ThemesListener {
    void onReady(List<ThemeInfo> themes, String error);
  }

  /** تثبيت ثيم كامل: مسار الزر، مسار زر المسافة، مسار الخلفية أو خطأ. */
  public interface ThemeInstallListener {
    void onReady(String framePath, String spacePath, String backgroundPath, String error);
  }

  private static final Object lock = new Object();
  private static ZipRangeReader reader;
  private static final Map<String, JSONObject> jsonCache =
      new HashMap<String, JSONObject>();
  private static Handler main;
  /**
   * Round 68: كاش صور المعاينات في الذاكرة — أي بطاقة شوفت مرة تعود
   * فوراً في الزيارة التالية بلا أي طلب شبكي، وأي قسم يُعاد فتحه
   * تظهر صوره لحظياً.
   */
  private static final android.util.LruCache<String, Bitmap> imageCache =
      new android.util.LruCache<String, Bitmap>(16 * 1024) { // 16MB بوحدات KB
        protected int sizeOf(String key, Bitmap value) {
          return Math.max(1, value.getByteCount() / 1024);
        }
      };
  /**
   * Round 68: أربع عمال متوازيين بدل خيط جديد لكل طلب — معاينات الشبكة
   * كلها كانت تُطلق دفعة واحدة فتزدحم الشبكة وتبطؤ؛ الآن أربع صور في
   * الوقت نفسه كحد أقصى فتظهر البطاقات بأسرع وقت ممكن.
   */
  private static final java.util.concurrent.ExecutorService pool =
      java.util.concurrent.Executors.newFixedThreadPool(4);

  private ThematyStore() {}

  // ------------------------------------------------------------ الأقسام

  /**
   * أقسام المكتبة لنوع معين — بطاقات الاختيار في الواجهات:
   * الأزرار والخلفيات: مجموعات buttons.json (مطابقة 1:1)؛
   * التفاعلات: تاغات effects.json؛ الثيمات: أقسام themes.json (ج68).
   */
  public static void sets(final String type, final SetsListener listener) {
    start(
        new Runnable() {
          public void run() {
            List<SetInfo> sets = new ArrayList<SetInfo>();
            String error = null;
            try {
              if (OnlineAssetStore.TYPE_EFFECT.equals(type)) {
                JSONObject root = catalogJson(JSON_EFFECTS);
                JSONArray items = root.optJSONArray("items");
                List<String> tags = new ArrayList<String>();
                if (items != null) {
                  for (int index = 0; index < items.length(); index++) {
                    String tag = items.getJSONObject(index).optString("tag", "");
                    if (tag.length() == 0 || tags.contains(tag)) continue;
                    tags.add(tag);
                  }
                }
                for (int index = 0; index < tags.size(); index++) {
                  sets.add(new SetInfo(tags.get(index), tags.get(index), countTag(items, tags.get(index))));
                }
              } else if (TYPE_THEME.equals(type)) {
                // Round 68: أقسام الثيمات تُقرأ من themes.json (المصدر الوحيد)
                JSONArray raw = catalogJson(JSON_THEMES).optJSONArray("items");
                List<String> order = new ArrayList<String>();
                Map<String, String> titles = new HashMap<String, String>();
                Map<String, Integer> counts = new HashMap<String, Integer>();
                if (raw != null) {
                  for (int index = 0; index < raw.length(); index++) {
                    JSONObject entry = raw.getJSONObject(index);
                    String id = entry.optString("set");
                    if (id.length() == 0) continue;
                    if (!order.contains(id)) {
                      order.add(id);
                      titles.put(id, entry.optString("setAr", id));
                      counts.put(id, Integer.valueOf(0));
                    }
                    counts.put(id, Integer.valueOf(counts.get(id).intValue() + 1));
                  }
                }
                for (int index = 0; index < order.size(); index++) {
                  String id = order.get(index);
                  sets.add(
                      new SetInfo(
                          id,
                          titles.get(id).length() == 0 ? id : titles.get(id),
                          counts.get(id).intValue()));
                }
              } else { // الأزرار والخلفيات — مجموعات الأزرار نفسها (مطابقة 1:1)
                JSONObject root = catalogJson(JSON_BUTTONS);
                JSONArray raw = root.optJSONArray("sets");
                JSONArray items = root.optJSONArray("items");
                if (raw != null) {
                  for (int index = 0; index < raw.length(); index++) {
                    JSONObject entry = raw.getJSONObject(index);
                    String id = entry.optString("id");
                    if (id.length() == 0) continue;
                    sets.add(
                        new SetInfo(id, entry.optString("ar", id), countSet(items, id)));
                  }
                }
              }
            } catch (Exception failure) {
              error = String.valueOf(failure);
            }
            final List<SetInfo> result = sets;
            final String failure = error;
            main()
                .post(
                    new Runnable() {
                      public void run() {
                        listener.onReady(result.isEmpty() ? null : result, failure);
                      }
                    });
          }
        });
  }

  /** عناصر قسم معين — جاهزة للعرض ببطاقات (المحفوظ أولاً في الواجهة). */
  public static void items(
      final String type, final String setId, final OnlineAssetStore.Listener listener) {
    start(
        new Runnable() {
          public void run() {
            List<OnlineAssetStore.Item> items = new ArrayList<OnlineAssetStore.Item>();
            String error = null;
            try {
              if (OnlineAssetStore.TYPE_EFFECT.equals(type)) {
                JSONArray raw = catalogJson(JSON_EFFECTS).optJSONArray("items");
                if (raw != null) {
                  for (int index = 0; index < raw.length(); index++) {
                    JSONObject entry = raw.getJSONObject(index);
                    if (!setId.equals(entry.optString("tag"))) continue;
                    String icon = asset(entry.optString("icon"));
                    items.add(
                        new OnlineAssetStore.Item(
                            "tx-" + entry.optString("id"),
                            entry.optString("name"),
                            icon,
                            icon,
                            ""));
                  }
                }
              } else if (OnlineAssetStore.TYPE_BACKGROUND.equals(type)) {
                JSONArray raw = catalogJson(JSON_BACKGROUNDS).optJSONArray("items");
                if (raw != null) {
                  for (int index = 0; index < raw.length(); index++) {
                    JSONObject entry = raw.getJSONObject(index);
                    String id = entry.optString("id");
                    if (!setId.equals(setOf(id))) continue;
                    String src = asset(entry.optString("src"));
                    items.add(
                        new OnlineAssetStore.Item(
                            "tg-" + id, entry.optString("name"), src, src, ""));
                  }
                }
              } else { // الأزرار
                JSONArray raw = catalogJson(JSON_BUTTONS).optJSONArray("items");
                if (raw != null) {
                  for (int index = 0; index < raw.length(); index++) {
                    JSONObject entry = raw.getJSONObject(index);
                    if (!setId.equals(entry.optString("set"))) continue;
                    String src = asset(entry.optString("src"));
                    String space = spaceAsset(entry);
                    items.add(
                        new OnlineAssetStore.Item(
                            "tb-" + entry.optString("id"),
                            entry.optString("name"),
                            src,
                            src,
                            "",
                            space,
                            space,
                            ""));
                  }
                }
              }
            } catch (Exception failure) {
              error = String.valueOf(failure);
            }
            final List<OnlineAssetStore.Item> result = items;
            final String failure = error;
            main()
                .post(
                    new Runnable() {
                      public void run() {
                        listener.onReady(result.isEmpty() ? null : result, failure);
                      }
                    });
          }
        });
  }

  /**
   * ثيمات قسم كامل من themes.json — زر وخلفية متناسقان لكل ثيم ومعهما
   * ألوانه (base/dark/light/text/toolbarText) لرسم المعاينة فوراً.
   */
  public static void themes(final String setId, final ThemesListener listener) {
    start(
        new Runnable() {
          public void run() {
            List<ThemeInfo> themes = new ArrayList<ThemeInfo>();
            String error = null;
            try {
              JSONArray raw = catalogJson(JSON_THEMES).optJSONArray("items");
              if (raw != null) {
                for (int index = 0; index < raw.length(); index++) {
                  JSONObject entry = raw.getJSONObject(index);
                  if (!setId.equals(entry.optString("set"))) continue;
                  JSONObject button = entry.optJSONObject("button");
                  JSONObject background = entry.optJSONObject("background");
                  if (button == null || background == null) continue;
                  String space = spaceAsset(entry);
                  JSONObject colors = entry.optJSONObject("colors");
                  themes.add(
                      new ThemeInfo(
                          entry.optString("id"),
                          entry.optString("name"),
                          entry.optString("setAr"),
                          asset(button.optString("src")),
                          space,
                          asset(background.optString("src")),
                          colorOf(colors, "base", 0xff8ab4f8),
                          colorOf(colors, "dark", 0xff202124),
                          colorOf(colors, "light", 0xffeceff3),
                          colorOf(colors, "text", 0xff202124),
                          colorOf(colors, "toolbarText", 0xff202124)));
                }
              }
            } catch (Exception failure) {
              error = String.valueOf(failure);
            }
            final List<ThemeInfo> result = themes;
            final String failure = error;
            main()
                .post(
                    new Runnable() {
                      public void run() {
                        listener.onReady(result.isEmpty() ? null : result, failure);
                      }
                    });
          }
        });
  }

  // ------------------------------------------------------------ جلب وتثبيت

  /**
   * يجلب العنصر المختار وحده من داخل الأرشيف ويحفظه في الذاكرة الداخلية —
   * بديل تنزيل الإنترنت القديم بنفس شكل الاستدعاء.
   */
  public static void install(
      final Context context,
      final String type,
      final OnlineAssetStore.Item item,
      final OnlineAssetStore.DownloadListener listener) {
    start(
        new Runnable() {
          public void run() {
            try {
              byte[] data = zip().readFile(item.download);
              byte[] spaceData = null;
              if (OnlineAssetStore.TYPE_FRAME.equals(type) && item.spaceDownload.length() > 0) {
                try {
                  spaceData = zip().readFile(item.spaceDownload);
                } catch (Exception ignoredSpace) {
                  spaceData = null; // يبقى الزر الرئيسي قابلاً للتطبيق حتى لو غابت المسافة
                }
              }
              final String path =
                  OnlineAssetStore.saveBytesWithSpace(context, type, item, data, spaceData).path;
              main()
                  .post(
                      new Runnable() {
                        public void run() {
                          listener.onReady(path, null);
                        }
                      });
            } catch (Exception failure) {
              final String error = String.valueOf(failure);
              main()
                  .post(
                      new Runnable() {
                        public void run() {
                          listener.onReady(null, error);
                        }
                      });
            }
          }
        });
  }

  /**
   * يثبّت ثيماً كاملاً: الزر أولاً ثم الخلفية — كلاهما يُحفظ في الذاكرة
   * الداخلية. إن تعذّرت الخلفية يظل الزر مثبتاً وتعود الخلفية null.
   */
  public static void installTheme(
      final Context context,
      final ThemeInfo theme,
      final ThemeInstallListener listener) {
    start(
        new Runnable() {
          public void run() {
            String framePath = null;
            String spacePath = null;
            String backgroundPath = null;
            String error = null;
            try {
              byte[] buttonData = zip().readFile(theme.buttonEntry);
              byte[] spaceData = null;
              if (theme.spaceEntry.length() > 0) {
                try {
                  spaceData = zip().readFile(theme.spaceEntry);
                } catch (Exception ignoredSpace) {
                  spaceData = null;
                }
              }
              OnlineAssetStore.Item buttonItem =
                  new OnlineAssetStore.Item(
                      "tb-" + theme.id,
                      theme.name,
                      theme.buttonEntry,
                      theme.buttonEntry,
                      "",
                      theme.spaceEntry,
                      theme.spaceEntry,
                      "");
              OnlineAssetStore.SavedAsset savedButton =
                  OnlineAssetStore.saveBytesWithSpace(
                      context, OnlineAssetStore.TYPE_FRAME, buttonItem, buttonData, spaceData);
              framePath = savedButton.path;
              spacePath = savedButton.spacePath;
            } catch (Exception failure) {
              error = String.valueOf(failure);
            }
            if (framePath != null) {
              try {
                byte[] backgroundData = zip().readFile(theme.backgroundEntry);
                OnlineAssetStore.Item backgroundItem =
                    new OnlineAssetStore.Item(
                        "tg-" + theme.id,
                        theme.name,
                        theme.backgroundEntry,
                        theme.backgroundEntry,
                        "");
                backgroundPath =
                    OnlineAssetStore.saveBytes(
                        context,
                        OnlineAssetStore.TYPE_BACKGROUND,
                        backgroundItem,
                        backgroundData);
              } catch (Exception backgroundFailure) {
                error = String.valueOf(backgroundFailure); // الزر يُطبَّق وحده
              }
            }
            final String frame = framePath;
            final String space = spacePath;
            final String background = backgroundPath;
            final String failure = error;
            main()
                .post(
                    new Runnable() {
                      public void run() {
                        listener.onReady(frame, space, background, failure);
                      }
                    });
          }
        });
  }

  /** معاينة بطاقة: من ملفها المحفوظ إن وُجد، وإلا من داخل الأرشيف مباشرة. */
  public static void preview(
      final OnlineAssetStore.Item item,
      final int maxEdge,
      final OnlineAssetStore.BitmapListener listener) {
    previewSource(
        item.path.length() > 0 ? item.path : item.thumb, maxEdge, listener);
  }

  /**
   * معاينة من مصدر واحد: مسار ملف محلي (يبدأ بـ/) أو مدخل داخل الأرشيف.
   * Round 68: كاش فوري أولاً — الصورة المفكوكة سابقاً تُسلَّم في اللحظة
   * نفسها بلا شبكة؛ وما ليس في الكاش يُجلب على أحد العمال الأربعة
   * المتوازيين فيظهر بأسرع وقت ممكن.
   */
  public static void previewSource(
      final String source, final int maxEdge, final OnlineAssetStore.BitmapListener listener) {
    final String cacheKey = source + "@" + maxEdge;
    final Bitmap cached = imageCache.get(cacheKey);
    if (cached != null) {
      main()
          .post(
              new Runnable() {
                public void run() {
                  listener.onReady(cached);
                }
              });
      return;
    }
    start(
        new Runnable() {
          public void run() {
            Bitmap picture = null;
            try {
              if (source.startsWith("/")) {
                picture = OnlineAssetStore.decodeFile(source, maxEdge);
              } else {
                byte[] data = zip().readFile(source);
                picture = OnlineAssetStore.decodeBytes(data, maxEdge);
              }
            } catch (Exception ignored) {
            }
            if (picture != null) imageCache.put(cacheKey, picture);
            final Bitmap result = picture;
            main()
                .post(
                    new Runnable() {
                      public void run() {
                        listener.onReady(result);
                      }
                    });
          }
        });
  }

  // ------------------------------------------------------------ داخلية

  /** القارئ — يُنشأ مرة واحدة بتحميل فهرسه (ذيل فقط) ثم يُعاد استخدامه. */
  private static ZipRangeReader zip() throws Exception {
    synchronized (lock) {
      if (reader == null) {
        ZipRangeReader fresh =
            new ZipRangeReader(new ZipRangeReader.HttpFetcher(ZIP_URL, USER_AGENT, GITHUB_TOKEN));
        fresh.loadIndex();
        reader = fresh;
      }
      return reader;
    }
  }

  /** فهرس نصي من الأرشيف مع تخزينه في الذاكرة — لا يُجلب إلا مرة بالجلسة. */
  private static JSONObject catalogJson(String entry) throws Exception {
    synchronized (lock) {
      JSONObject cached = jsonCache.get(entry);
      if (cached != null) return cached;
      byte[] data = zip().readFile(entry);
      JSONObject parsed = new JSONObject(new String(data, "UTF-8"));
      jsonCache.put(entry, parsed);
      return parsed;
    }
  }

  /** يستخرج فن زر المسافة من صيغتي buttons.json وthemes.json: space.src أو spaceSrc. */
  static String spaceAsset(JSONObject entry) {
    if (entry == null) return "";
    String relative = entry.optString("spaceSrc", "");
    JSONObject space = entry.optJSONObject("space");
    if (space != null && space.optString("src", "").length() > 0) {
      relative = space.optString("src", "");
    }
    return relative.length() == 0 ? "" : asset(relative);
  }

  /** مسار أصل داخل الأرشيف: "assets/…" ← "thematy/public/assets/…". */
  static String asset(String relative) {
    if (relative == null || relative.length() == 0) return "";
    if (relative.startsWith(PREFIX) || relative.startsWith("thematy/")) return relative;
    return PREFIX + relative;
  }

  /** مجموعة الخلفية من معرفها — "glossy-01" ← "glossy" (مطابقة مجموعات الأزرار). */
  static String setOf(String id) {
    int dash = id.lastIndexOf('-');
    return dash > 0 ? id.substring(0, dash) : id;
  }

  private static int countSet(JSONArray items, String setId) {
    int count = 0;
    if (items == null) return 0;
    for (int index = 0; index < items.length(); index++) {
      if (setId.equals(items.optJSONObject(index).optString("set"))) count++;
    }
    return count;
  }

  private static int countTag(JSONArray items, String tag) {
    int count = 0;
    if (items == null) return 0;
    for (int index = 0; index < items.length(); index++) {
      if (tag.equals(items.optJSONObject(index).optString("tag"))) count++;
    }
    return count;
  }

  private static void start(Runnable task) {
    pool.execute(task);
  }

  /** لون سداسي من colors{} في themes.json مع قيمة احتياطية آمنة. */
  static int colorOf(JSONObject colors, String key, int fallback) {
    if (colors == null) return fallback;
    try {
      String value = colors.optString(key, "");
      if (value.length() > 0) return android.graphics.Color.parseColor(value);
    } catch (Exception ignored) {
    }
    return fallback;
  }

  private static Handler main() {
    if (main == null) main = new Handler(Looper.getMainLooper());
    return main;
  }
}
