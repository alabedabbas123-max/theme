package com.almlk.swiftkey.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Round 45: the key-art helper. Imported theme folders (assets/theme/...) may carry
 * art of any size and shape — a 4K photo dropped in as a key icon, for instance.
 * This class turns such art into a pixel-fitted 3D KEY BUTTON exactly once, keeps
 * the adjusted copy in a bounded in-memory LRU cache, and hands every later request
 * the SAME bitmap: no re-processing per draw, no duplicated pixels, capped memory.
 *
 * Pipeline per request (path + variant):
 *   1. sampled decode — inJustDecodeBounds probe + inSampleSize (powers of two), so
 *      a 4000px source never fully enters memory;
 *   2. exact cap + center-crop to the canonical aspect (key 0.63 / wide bar 2.88);
 *   3. shape pass — art that arrives ALREADY SHAPED (transparent rounded corners,
 *      like the owner's button references) keeps its own look untouched; opaque
 *      rectangles (photos, unsuitable art) receive the full 3D button treatment:
 *      rounded-corner clip, top gloss gradient, bottom depth shade and a thin
 *      bright rim;
 *   4. cache — LinkedHashMap LRU keyed by "variant|aspect|path", capped at
 *      MAX_CACHE entries. Evicted entries are only dropped from the cache, never
 *      recycled: the keyboard view, thumbnails and sibling themes may still hold
 *      them (the views guard their own recycle calls with ownership flags).
 *
 * Backgrounds (including the owner's .webp files — shipped as WebP to shrink the
 * package without losing quality; opaque lossy WebP decodes everywhere the app
 * runs, unlike alpha WebP which needs newer APIs) decode through the same sampled
 * and capped path, uncropped — cover-fit happens at draw time.
 */
public final class KeyArtProcessor {
  /** Canonical shapes the renderer expects (round 41/42 skin contracts). */
  public static final float KEY_ASPECT = 0.63f;
  /** Target aspect for imported spacebar art — the wide key-button ratio (round 47). */
  public static final float SPACE_ASPECT = 2.88f;
  public static final int BG_MAX_EDGE = 1080;
  public static final int KEY_MAX_EDGE = 256;
  public static final int SPACE_MAX_EDGE = 1024;
  /** LRU ceiling — comfortably holds every part of a handful of live themes. */
  static final int MAX_CACHE = 32;

  private static final LinkedHashMap<String, Bitmap> CACHE =
      new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
          return size() > MAX_CACHE;
        }
      };

  private KeyArtProcessor() {}

  /** Letter-key art: cropped to the key face aspect, 3D-shaped when needed, cached. */
  public static Bitmap keyArt(Context context, String path) {
    return art(context, path, "key", KEY_ASPECT, KEY_MAX_EDGE);
  }

  /** Spacebar art: cropped to the wide ruler aspect, 3D-shaped when needed, cached. */
  public static Bitmap spaceArt(Context context, String path) {
    return art(context, path, "space", SPACE_ASPECT, SPACE_MAX_EDGE);
  }

  /**
   * Round 69: الفن الجاهز (زوايا شفافة = زر مشكَّل مسبقاً) لا يُقصّ إطلاقاً —
   * يُحتفظ بشكل الزر الحقيقي كما هو ويُحدّ الطرف الطويل فقط. القصّ لنسبة
   * المفتاح مخصص للصور المعتمة التي ستمر على معالجة الزر ثلاثي الأبعاد.
   */
  private static Bitmap fitSize(Bitmap src, int maxEdge) {
    int longEdge = Math.max(src.getWidth(), src.getHeight());
    if (longEdge <= maxEdge) return src;
    float factor = (float) maxEdge / (float) longEdge;
    Bitmap scaled =
        Bitmap.createScaledBitmap(
            src,
            Math.max(1, Math.round(src.getWidth() * factor)),
            Math.max(1, Math.round(src.getHeight() * factor)),
            true);
    return scaled != null ? scaled : src;
  }

  /**
   * Round 62: فن زر من ملف محمَّل من الإنترنت (إطارات الاستوديو) — نفس معالجة
   * بيانات الممتلكات: فكّ محدود الذاكرة من ملف، والصور المعتمة العادية تُقصّ
   * إلى نسبة المفتاح وتأخذ معالجة الزر ثلاثي الأبعاد كاملة (حواف مدورة ولمعة
   * وعمق وحافة مضيئة)، والفن الجاهز ذو الزوايا الشفافة (أزرار حقيقية محمّلة)
   * يمر بشكله الأصلي — Round 69: بلا أي قصّ لنسبة، تحديد حجم فقط، كي يبقى
   * شكل الزر مطابقاً لأيقونته الحرفية بلا زوائد مستطيلة.
   */
  public static Bitmap fileKeyArt(String path) {
    if (path == null || path.length() == 0) return null;
    String cacheKey = "fkey|" + path;
    synchronized (CACHE) {
      Bitmap cached = CACHE.get(cacheKey);
      if (cached != null) return cached;
    }
    Bitmap decoded = decodeFileCapped(path, KEY_MAX_EDGE * 2);
    if (decoded == null) return null;
    Bitmap out = decoded;
    // Round 69: النسبة الحقيقية للمفتاح العريض هي 1/KEY_ASPECT (w/h) — القصّ
    // القديم بـKEY_ASPECT نفسها كان يقصّ زر 448×616 الطولي إلى نسبة طولية
    // فيشطب زخرفة حدّي الزر ويظهر «زوائد مستطيلة» على حدود المفتاح.
    if (needsShaping(decoded)) {
      out = shape3D(fitAspect(decoded, 1f / KEY_ASPECT, KEY_MAX_EDGE));
    } else {
      out = fitSize(decoded, KEY_MAX_EDGE);
    }
    synchronized (CACHE) {
      Bitmap first = CACHE.get(cacheKey);
      if (first != null) return first; // concurrent producer wins
      CACHE.put(cacheKey, out);
    }
    return out;
  }


  /**
   * فن زر المسافة من ملف محمّل من ثيماتي. إن كان الزر جاهزاً بزوايا شفافة
   * يبقى كما هو مع تحديد الحجم فقط؛ أما الصور المعتمة فتُقصّ لنسبة المسافة
   * وتُشكَّل كزر عريض ثلاثي الأبعاد. يُستخدم فقط لمفتاح KeySpec.SPACE.
   */
  public static Bitmap fileSpaceArt(String path) {
    if (path == null || path.length() == 0) return null;
    String cacheKey = "fspace|" + path;
    synchronized (CACHE) {
      Bitmap cached = CACHE.get(cacheKey);
      if (cached != null) return cached;
    }
    Bitmap decoded = decodeFileCapped(path, SPACE_MAX_EDGE);
    if (decoded == null) return null;
    Bitmap out;
    if (needsShaping(decoded)) {
      out = shape3D(fitAspect(decoded, SPACE_ASPECT, SPACE_MAX_EDGE));
    } else {
      out = fitSize(decoded, SPACE_MAX_EDGE);
    }
    synchronized (CACHE) {
      Bitmap first = CACHE.get(cacheKey);
      if (first != null) return first;
      CACHE.put(cacheKey, out);
    }
    return out;
  }

  /**
   * Round 62: معاينة زر حقيقية لبطاقات الاستوديو — من أي صورة (مصغرة أو
   * محفوظة): الصور العادية تُقصّ لنسبة المفتاح وتُشكَّل زراً ثلاثي الأبعاد،
   * والأزرار الجاهزة تُعرض كما هي. بلا كاش — البطاقات عابرة والصور صغيرة.
   */
  public static Bitmap shapeButton(Bitmap src) {
    if (src == null) return null;
    if (needsShaping(src)) return shape3D(fitAspect(src, 1f / KEY_ASPECT, KEY_MAX_EDGE));
    return fitSize(src, KEY_MAX_EDGE);
  }

  /**
   * Round 69: لون حروف يُقرأ فوق فن الزر — متوسط إضاءة عيّنة من نقاط الوجه.
   * مشترك بين الكيبورد الحي وشارات الاقتراحات والاستوديو حتى يتطابق لون
   * الخط في كل الواجهات مع لون حروف الزر المختار.
   */
  public static int frameTextColor(Bitmap art) {
    if (art == null) return 0xff172033;
    long total = 0;
    int count = 0;
    try {
      for (int x = 0; x <= 4; x++) {
        for (int y = 0; y <= 2; y++) {
          int px = Math.min(art.getWidth() - 1, art.getWidth() * x / 4);
          int py = Math.min(art.getHeight() - 1, art.getHeight() * y / 2);
          int pixel = art.getPixel(px, py);
          total +=
              (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114)
                  / 1000;
          count++;
        }
      }
    } catch (Exception ignored) {
      return 0xff172033; // افتراضي آمن — صورة تالفة لا توقف الرسم أبداً
    }
    return total / Math.max(1, count) < 145 ? Color.WHITE : 0xff172033;
  }

  /**
   * Round 63: sprite تفاعل الضغط من ملف محمّل — فكّ محدود (≤256px بذاكرة
   * محدودة) مع كاش، فلا يفكّ المحرك صورة ضخمة على خيطه الرئيسي فتتوقف
   * الواجهة، والتسخين يحدث على خيط التحميل نفسه (warmArt).
   */
  public static Bitmap fileSprite(String path) {
    if (path == null || path.length() == 0) return null;
    String cacheKey = "sprite|" + path;
    synchronized (CACHE) {
      Bitmap cached = CACHE.get(cacheKey);
      if (cached != null) return cached;
    }
    Bitmap decoded = decodeFileCapped(path, 256);
    if (decoded == null) return null;
    synchronized (CACHE) {
      Bitmap first = CACHE.get(cacheKey);
      if (first != null) return first; // concurrent producer wins
      CACHE.put(cacheKey, decoded);
    }
    return decoded;
  }

  /** فكّ صورة من ملف على القرص بذاكرة محدودة — نظير decodeCapped للممتلكات. */
  private static Bitmap decodeFileCapped(String path, int maxEdge) {
    try {
      BitmapFactory.Options probe = new BitmapFactory.Options();
      probe.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(path, probe);
      if (probe.outWidth <= 0 || probe.outHeight <= 0) return null;
      int sample = 1;
      while (probe.outWidth / sample > maxEdge || probe.outHeight / sample > maxEdge) {
        sample *= 2;
      }
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = sample;
      Bitmap raw = BitmapFactory.decodeFile(path, options);
      if (raw == null) return null;
      int longEdge = Math.max(raw.getWidth(), raw.getHeight());
      if (longEdge > maxEdge) {
        float factor = (float) maxEdge / (float) longEdge;
        Bitmap scaled =
            Bitmap.createScaledBitmap(
                raw,
                Math.round(raw.getWidth() * factor),
                Math.round(raw.getHeight() * factor),
                true);
        return scaled == null ? raw : scaled;
      }
      return raw;
    } catch (Exception ignored) {
      return null;
    }
  }

  /** Press flash: the darkened twin (0.55) of the key art — computed once, cached. */
  public static Bitmap pressTwin(Context context, String path) {
    String cacheKey = "press|" + KEY_ASPECT + "|" + path;
    synchronized (CACHE) {
      Bitmap cached = CACHE.get(cacheKey);
      if (cached != null) return cached;
    }
    Bitmap up = keyArt(context, path);
    Bitmap press = up == null ? null : darkened(up);
    if (press == null) return null;
    synchronized (CACHE) {
      Bitmap first = CACHE.get(cacheKey);
      if (first != null) return first; // concurrent producer wins
      CACHE.put(cacheKey, press);
    }
    return press;
  }

  /** Keyboard background: sampled + capped, no crop (cover-fit happens at draw). */
  public static Bitmap background(Context context, String path) {
    String cacheKey = "bg|" + path;
    synchronized (CACHE) {
      Bitmap cached = CACHE.get(cacheKey);
      if (cached != null) return cached;
    }
    Bitmap decoded = decodeCapped(context, path, BG_MAX_EDGE);
    if (decoded == null) return null;
    synchronized (CACHE) {
      Bitmap first = CACHE.get(cacheKey);
      if (first != null) return first;
      CACHE.put(cacheKey, decoded);
    }
    return decoded;
  }

  // ------------------------------------------------------------------ pipeline

  private static Bitmap art(
      Context context, String path, String variant, float aspect, int maxEdge) {
    if (path == null || path.length() == 0) return null;
    String cacheKey = variant + "|" + aspect + "|" + path;
    synchronized (CACHE) {
      Bitmap cached = CACHE.get(cacheKey);
      if (cached != null) return cached;
    }
    // Decode above the target only far enough that the aspect crop never starves.
    int decodeCap = Math.min(BG_MAX_EDGE, maxEdge * 2);
    Bitmap decoded = decodeCapped(context, path, decodeCap);
    if (decoded == null) return null;
    Bitmap fitted = fitAspect(decoded, aspect, maxEdge);
    if (needsShaping(fitted)) fitted = shape3D(fitted);
    Bitmap out = fitted;
    synchronized (CACHE) {
      Bitmap first = CACHE.get(cacheKey);
      if (first != null) return first; // concurrent producer wins
      CACHE.put(cacheKey, out);
    }
    return out;
  }

  /**
   * Decodes an asset bitmap with bounded memory: bounds probe first, then
   * inSampleSize so the long edge lands near maxEdge, then an exact scale when the
   * power-of-two sample still overshoots. Never upscales.
   */
  private static Bitmap decodeCapped(Context context, String path, int maxEdge) {
    try {
      BitmapFactory.Options probe = new BitmapFactory.Options();
      probe.inJustDecodeBounds = true;
      InputStream measure = context.getAssets().open(path);
      try {
        BitmapFactory.decodeStream(measure, null, probe);
      } finally {
        measure.close();
      }
      if (probe.outWidth <= 0 || probe.outHeight <= 0) return null;
      int sample = 1;
      while (probe.outWidth / sample > maxEdge || probe.outHeight / sample > maxEdge) {
        sample *= 2;
      }
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = sample;
      InputStream in = context.getAssets().open(path);
      Bitmap raw;
      try {
        raw = BitmapFactory.decodeStream(in, null, options);
      } finally {
        in.close();
      }
      if (raw == null) return null;
      int longEdge = Math.max(raw.getWidth(), raw.getHeight());
      if (longEdge > maxEdge) {
        float factor = (float) maxEdge / (float) longEdge;
        Bitmap scaled =
            Bitmap.createScaledBitmap(
                raw,
                Math.round(raw.getWidth() * factor),
                Math.round(raw.getHeight() * factor),
                true);
        return scaled == null ? raw : scaled;
      }
      return raw;
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Center-crops to the exact target aspect — no distortion: surplus pixels fall
   * away, nothing is stretched — then caps the long edge.
   */
  private static Bitmap fitAspect(Bitmap src, float aspect, int maxEdge) {
    int w = src.getWidth();
    int h = src.getHeight();
    int cropW = w;
    int cropH = h;
    float current = (float) w / (float) h;
    if (current > aspect) cropW = Math.round((float) h * aspect);
    else if (current < aspect) cropH = Math.round((float) w / aspect);
    Bitmap cropped =
        Bitmap.createBitmap(src, (w - cropW) / 2, (h - cropH) / 2, cropW, cropH);
    int longEdge = Math.max(cropped.getWidth(), cropped.getHeight());
    if (longEdge > maxEdge) {
      float factor = (float) maxEdge / (float) longEdge;
      Bitmap scaled =
          Bitmap.createScaledBitmap(
              cropped,
              Math.round(cropped.getWidth() * factor),
              Math.round(cropped.getHeight() * factor),
              true);
      if (scaled != null) return scaled;
    }
    return cropped;
  }

  /**
   * True when the art is an OPAQUE rectangle (photo-like, "unsuitable shape") and
   * needs the 3D button treatment. Art with transparent rounded corners — pre-made
   * buttons like the owner's references — passes through with size fitting only.
   */
  private static boolean needsShaping(Bitmap src) {
    int w = src.getWidth();
    int h = src.getHeight();
    if (w < 4 || h < 4) return false;
    int[] corners = new int[4];
    src.getPixels(corners, 0, 1, 1, 1, 1, 1);
    src.getPixels(corners, 1, 1, w - 2, 1, 1, 1);
    src.getPixels(corners, 2, 1, 1, h - 2, 1, 1);
    src.getPixels(corners, 3, 1, w - 2, h - 2, 1, 1);
    for (int i = 0; i < 4; i++) {
      if (((corners[i] >>> 24) & 0xff) < 200) return false;
    }
    return true;
  }

  /**
   * The 3D button treatment for flat art: rounded-corner clip, a soft top gloss
   * that fades out by 45% of the height, a bottom depth shade over the last 18%,
   * and a thin bright inner rim — the same visual grammar as the owner's glossy
   * button references, drawn OVER the art without hiding it.
   */
  private static Bitmap shape3D(Bitmap src) {
    int w = src.getWidth();
    int h = src.getHeight();
    float radius = Math.min(w, h) * 0.18f;
    Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    Path clip = new Path();
    clip.addRoundRect(new RectF(0, 0, w, h), radius, radius, Path.Direction.CW);
    canvas.clipPath(clip);
    canvas.drawBitmap(src, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
    Paint gloss = new Paint();
    gloss.setShader(
        new LinearGradient(
            0, 0, 0, h * 0.45f, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, w, h * 0.45f, gloss);
    Paint depth = new Paint();
    depth.setShader(
        new LinearGradient(
            0, h * 0.82f, 0, h, 0x00000000, 0x4D000000, Shader.TileMode.CLAMP));
    canvas.drawRect(0, h * 0.82f, w, h, depth);
    Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    rim.setStyle(Paint.Style.STROKE);
    rim.setStrokeWidth(Math.max(1f, w / 48f));
    rim.setColor(0x33FFFFFF);
    canvas.drawRoundRect(
        new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), radius, radius, rim);
    return out;
  }

  /** Press-flash twin: every channel x0.55, alpha untouched. Fresh bitmap. */
  private static Bitmap darkened(Bitmap src) {
    int w = src.getWidth();
    int h = src.getHeight();
    int[] pixels = new int[w * h];
    src.getPixels(pixels, 0, w, 0, 0, w, h);
    for (int i = 0; i < pixels.length; i++) {
      int p = pixels[i];
      if (((p >>> 24) & 0xff) == 0) continue;
      pixels[i] =
          (p & 0xff000000)
              | ((((p >>> 16) & 0xff) * 55 / 100) << 16)
              | ((((p >>> 8) & 0xff) * 55 / 100) << 8)
              | ((p & 0xff) * 55 / 100);
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
  }
}
