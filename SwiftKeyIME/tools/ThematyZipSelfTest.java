import com.almlk.swiftkey.settings.ZipRangeReader;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;

/**
 * Round 67: اختبار ذاتي حقيقي لقارئ الزيب عن بُعد — يعمل على الجهاز
 * الافتراضي هنا فقط (ليس جزءاً من التطبيق). يختبر مسارين:
 * 1) ملف محلي: تحليل الفهرس وقراءة ملفات منه (بلا شبكة).
 * 2) الرابط الفعلي: نفس المسار عبر HTTP Range على رابط ثيماتي في GitHub.
 *
 * الاستخدام: java ThematyZipSelfTest <مسار-الزيب-المحلي> [رابط-الاختبار]
 */
public class ThematyZipSelfTest {

  static final class LocalFetcher implements ZipRangeReader.Fetcher {
    private final RandomAccessFile file;
    private final long size;

    LocalFetcher(String path) throws Exception {
      file = new RandomAccessFile(path, "r");
      size = file.length();
    }

    public long totalSize() {
      return size;
    }

    public byte[] range(long start, int length) throws Exception {
      file.seek(start);
      byte[] buffer = new byte[length];
      int read = file.read(buffer);
      if (read != length) throw new IllegalStateException("قراءة ناقصة " + read + "/" + length);
      return buffer;
    }
  }

  static int failures = 0;

  static void check(boolean condition, String label) {
    System.out.println((condition ? "PASS " : "FAIL ") + label);
    if (!condition) failures++;
  }

  public static void main(String[] args) throws Exception {
    // ---------- مسار الملف المحلي ----------
    ZipRangeReader local = new ZipRangeReader(new LocalFetcher(args[0]));
    local.loadIndex();
    check(local.count() == 636, "محلي: 636 مدخلاً (فعلي: " + local.count() + ")");
    check(local.totalSize() == 9912029L, "محلي: الحجم 9912029 (فعلي: " + local.totalSize() + ")");

    String effectsJson = "thematy/public/downloads/json/effects.json";
    check(local.has(effectsJson), "محلي: effects.json في الفهرس");
    byte[] effects = local.readFile(effectsJson);
    String text = new String(effects, "UTF-8");
    check(text.contains("\"items\"") && text.contains("شفاه قبلة"),
        "محلي: effects.json مفكوك وسليم (" + effects.length + " بايت)");
    check(text.startsWith("{"), "محلي: JSON يبدأ بقوس");

    String button = "thematy/public/assets/buttons/btn-glossy-01.png";
    byte[] png = local.readFile(button);
    check(png.length == 17268, "محلي: btn-glossy-01.png حجماً (فعلي: " + png.length + ")");
    check(png[0] == (byte) 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G',
        "محلي: توقيع PNG صحيح");

    String background = "thematy/public/assets/backgrounds/bg-islamic-01.jpg";
    byte[] jpg = local.readFile(background);
    check((jpg[0] & 0xff) == 0xff && (jpg[1] & 0xff) == 0xd8, "محلي: توقيع JPEG صحيح");

    // Round 68: themes.json هو مصدر بيانات الثيمات (زر + خلفية + ألوان)
    String themesJson = "thematy/public/downloads/json/themes.json";
    byte[] themes = local.readFile(themesJson);
    String themesText = new String(themes, "UTF-8");
    check(themesText.contains("\"items\""), "محلي: themes.json فيه items");
    check(themesText.contains("\"count\": 193"), "محلي: themes.json فيه 193 ثيماً");
    check(
        themesText.contains("\"base\": \"#efc1c4\"") && themesText.contains("\"text\": \"#4c3d3e\""),
        "محلي: themes.json فيه ألوان colors{}");

    boolean refused = false;
    try {
      local.readFile("thematy/public/غير-موجود.png");
    } catch (Exception expected) {
      refused = true;
    }
    check(refused, "محلي: ملف غائب يُرفض بوضوح");

    // ---------- مسار الرابط الفعلي عبر HTTP Range ----------
    if (args.length > 1) {
      ZipRangeReader remote =
          new ZipRangeReader(
              new ZipRangeReader.HttpFetcher(
                  args[1], "AlmlkKeyboard/1.9 (Android keyboard; custom themes)"));
      remote.loadIndex();
      check(remote.count() == 636, "عن بعد: 636 مدخلاً (فعلي: " + remote.count() + ")");
      byte[] remoteEffects = remote.readFile(effectsJson);
      check(
          new String(remoteEffects, "UTF-8").contains("شفاه قبلة"),
          "عن بعد: effects.json عبر النطاقات سليم");
      byte[] remoteThemes = remote.readFile(themesJson);
      check(
          new String(remoteThemes, "UTF-8").contains("\"setAr\""),
          "عن بعد: themes.json عبر النطاقات سليم");
      byte[] remotePng = remote.readFile(button);
      check(remotePng.length == 17268, "عن بعد: الزر PNG حجماً (فعلي: " + remotePng.length + ")");
      List<String> buttons = remote.namesContaining("/assets/buttons/");
      check(buttons.size() == 193, "عن بعد: 193 زراً في الفهرس (فعلي: " + buttons.size() + ")");
    } else {
      System.out.println("SKIP عن بعد: لم يُعطَ رابط اختبار");
    }

    System.out.println(failures == 0 ? "== كل الاختبارات ناجحة ==" : "== فشل " + failures + " ==");
    if (failures > 0) System.exit(1);
  }
}
