package com.almlk.swiftkey.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.util.HashMap;

/**
 * Round 52: خلفية الثيم نفسها على كل واجهات الكيبورد — شريط الاقتراحات وشريط
 * الأدوات واللوحات (الإيموجي/الحافظة/الترجمة/الأدوات الإضافية/الصوت/التخطيطات).
 *
 * طلب المالك: اختيار خلفية يجب أن يطبّق على كل شيء، لا على لوحة المفاتيح وحدها.
 * الرسم يطابق SmartKeyboardView حرفياً: قص مركزي للصورة على حدود الواجهة، ثم
 * غسل لون الخلفية نفسه (ألفا 58، و42 للثيم الملكي)، ثم تعتيم backgroundDim
 * بمعادلة round40 نفسها. بلا صورة تبقى الواجهات بلون السطح المسطح كما كانت
 * دائماً — صفر انحدار للثيمات اللونية.
 *
 * الصور مفكوكة مرة واحدة لكل مصدر ويشاركها كل الدروابلات (bounds لكل واجهة)
 * ولا يُعاد تدويرها أبداً — نفس عقد ThemeSkinBar وAssetThemeLibrary.
 */
public final class ThemeSurfacePaint extends Drawable {

  private static final HashMap<String, Bitmap> CACHE = new HashMap<String, Bitmap>();

  private final Bitmap art;
  private final KeyboardTheme theme;
  private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

  private ThemeSurfacePaint(Bitmap art, KeyboardTheme theme) {
    this.art = art;
    this.theme = theme;
  }

  /**
   * دروابل خلفية الثيم لواجهة واحدة — null إن لم يكن للثيم صورة (فتبقى
   * الواجهة بلون السطح المسطح). كل استدعاء يعيد دروابل جديدة بbounds خاصة.
   */
  public static ThemeSurfacePaint forTheme(Context context, KeyboardTheme theme) {
    if (context == null || theme == null) return null;
    String uri = theme.imageSource();
    if (uri == null || uri.length() == 0) return null;
    Bitmap bitmap;
    synchronized (CACHE) {
      bitmap = CACHE.get(uri);
    }
    if (bitmap == null) {
      bitmap = decode(context, uri);
      if (bitmap == null) return null;
      synchronized (CACHE) {
        Bitmap first = CACHE.get(uri);
        if (first != null) bitmap = first; // الفائز المتزامن يبقى
        else CACHE.put(uri, bitmap);
      }
    }
    return new ThemeSurfacePaint(bitmap, theme);
  }

  /** يضع خلفية الثيم على الواجهة إن وُجدت صورة، وإلا لون السطح كما كان. */
  public static void apply(View view, Context context, KeyboardTheme theme) {
    if (view == null) return;
    ThemeSurfacePaint art = forTheme(context, theme);
    if (art != null) view.setBackgroundDrawable(art);
    else if (theme != null) view.setBackgroundColor(theme.surface);
  }

  public void draw(Canvas canvas) {
    RectF bounds = new RectF(getBounds());
    if (bounds.isEmpty() || art == null || art.isRecycled()) return;
    // نفس القص المركزي الذي ترسم به لوحة المفاتيح خلفيتها
    float scale =
        Math.max(
            bounds.width() / (float) art.getWidth(),
            bounds.height() / (float) art.getHeight());
    float width = art.getWidth() * scale;
    float height = art.getHeight() * scale;
    RectF target =
        new RectF(
            bounds.left - (width - bounds.width()) / 2f,
            bounds.top - (height - bounds.height()) / 2f,
            bounds.left - (width - bounds.width()) / 2f + width,
            bounds.top - (height - bounds.height()) / 2f + height);
    paint.setAlpha(255);
    canvas.drawBitmap(art, null, target, paint);
    // نفس غسل الخلفية فوق الصورة (لوحة المفاتيح ترسمه حرفياً هكذا)
    paint.setColor(theme.background);
    paint.setAlpha(theme.animationStyle == KeyboardTheme.ANIMATION_ROYAL ? 42 : 58);
    canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, paint);
    paint.setAlpha(255);
    // تعتيم round40 نفسه: سالب يسودّ، موجب يبيّض، صفر لا يرسم شيئاً
    float dim = theme.backgroundDim;
    if (dim != 0f) {
      paint.setColor(dim < 0f ? Color.BLACK : Color.WHITE);
      paint.setAlpha((int) (Math.min(.85f, Math.abs(dim)) * 255f));
      canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, paint);
      paint.setAlpha(255);
    }
  }

  // ------------------------------------------------------------ فك ترميز مشترك

  private static Bitmap decode(Context context, String uri) {
    // Round 43: خلفيات الثيمات المستوردة عبر المكتبة المشتركة — مشتركة لا تُدار
    if (uri.startsWith(AssetThemeLibrary.URI_PREFIX))
      return AssetThemeLibrary.keyboardBackground(context, uri);
    java.io.InputStream stream = null;
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inSampleSize = 2;
      if (uri.indexOf(':') < 0) {
        int id = context.getResources().getIdentifier(uri, "drawable", context.getPackageName());
        return id == 0 ? null : BitmapFactory.decodeResource(context.getResources(), id, options);
      }
      stream = context.getContentResolver().openInputStream(android.net.Uri.parse(uri));
      return BitmapFactory.decodeStream(stream, null, options);
    } catch (Exception ignored) {
      return null;
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
  }

  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
  }

  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
