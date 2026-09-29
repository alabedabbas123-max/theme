package com.almlk.swiftkey.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Round 69: خلفية شارة الاقتراحات من فن الزر المختار — قصّ متمركز للوسط مع
 * زوايا مدورة، فتحمل الشارة رسم الزر نفسه كأنها زر كيبورد صغير. هكذا يتطابق
 * شريط الاقتراحات (لوناً وصورةً) مع الكيبورد لحظة اختيار أي زر، ولون حروف
 * الشارة هو نفسه لون حروف المفاتيح — لون واحد في كل الواجهات.
 */
public final class ThemeChipArt extends Drawable {

  private final Bitmap art;
  private final float radius;
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
  private final Path clip = new Path();
  private final RectF dest = new RectF();

  private ThemeChipArt(Bitmap art, float radiusDp, float density) {
    this.art = art;
    // نصف قطر الرقاقة مقيد كالعادة (Round 52) كي يبقى مقياس الشارة مقروءاً
    this.radius = Math.min(14f, radiusDp) * density;
  }

  /**
   * خلفية الشارة من فن الإطار المفعّل (المحمّل من الإنترنت أولاً ثم المدمج)،
   * أو null إن لم يكن إطار مفعّل — فيبقى التدرج الكلاسيكي مكانه.
   */
  public static ThemeChipArt forTheme(Context context, KeyboardTheme theme) {
    Bitmap art = frameArt(context, theme);
    if (art == null || art.isRecycled()) return null;
    return new ThemeChipArt(
        art, theme.keyRadiusDp, context.getResources().getDisplayMetrics().density);
  }

  /**
   * لون حروف الشارة: لون حروف فن الزر نفسه إن كان إطار مفعّل، وإلا لون
   * الاقتراحات الاعتيادي — التطابق مع حروف المفاتيح أولوية قصوى.
   */
  public static int textColor(Context context, KeyboardTheme theme) {
    return theme == null ? 0xff172033 : theme.suggestionTextColor();
  }

  /** Round 69: كاش صغير لفن الإطار المدمج — الشارات كثيرة وفك واحد يكفي. */
  private static final java.util.HashMap<String, Bitmap> BUILTIN_CACHE =
      new java.util.HashMap<String, Bitmap>();

  private static Bitmap frameArt(Context context, KeyboardTheme theme) {
    if (theme == null) return null;
    // Round 74: أزرار الاقتراحات عريضة، لذلك تستخدم فن زر المسافة أولاً
    // ليظهر نفس امتداد space بدل قص زر الحروف الطولي داخل الشارة.
    if (theme.keySpaceFrameUri.length() > 0) {
      Bitmap space = KeyArtProcessor.fileSpaceArt(theme.keySpaceFrameUri);
      if (space != null) return space;
    }
    // إطار الإنترنت أولوية ثانية (Round 51) — مخزّن في كاش KeyArtProcessor نفسه
    if (theme.keyFrameUri.length() > 0) {
      Bitmap online = KeyArtProcessor.fileKeyArt(theme.keyFrameUri);
      if (online != null) return online;
    }
    // ثم الإطار المدمج (Round 50) — كاش بمفتاح الإطار والشكل
    if (theme.keyFrame > 0 && context != null) {
      String key = theme.keyFrame + "|" + Math.round(theme.keyRadiusDp);
      Bitmap cached = BUILTIN_CACHE.get(key);
      if (cached != null) return cached;
      String name = KeyFrames.artName(theme.keyFrame, theme.keyRadiusDp);
      if (name.length() > 0) {
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        if (id != 0) {
          Bitmap art = BitmapFactory.decodeResource(context.getResources(), id);
          if (art != null && BUILTIN_CACHE.size() < 24) BUILTIN_CACHE.put(key, art);
          return art;
        }
      }
    }
    return null;
  }

  @Override
  public void draw(Canvas canvas) {
    Rect bounds = getBounds();
    if (bounds.isEmpty() || art.isRecycled()) return;
    // قصّ متمركز للوسط: أكبر تغطية للرقاقة بلا تغيير نسبة الفن
    float scale =
        Math.max(
            bounds.width() / (float) art.getWidth(),
            bounds.height() / (float) art.getHeight());
    float w = art.getWidth() * scale;
    float h = art.getHeight() * scale;
    dest.set(
        bounds.centerX() - w / 2f,
        bounds.centerY() - h / 2f,
        bounds.centerX() + w / 2f,
        bounds.centerY() + h / 2f);
    clip.reset();
    clip.addRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius, radius,
        Path.Direction.CW);
    int save = canvas.save();
    canvas.clipPath(clip);
    canvas.drawBitmap(art, null, dest, paint);
    canvas.restoreToCount(save);
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
