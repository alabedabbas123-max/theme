package com.almlk.swiftkey.settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Round 67: قارئ ملف ZIP عن بُعد عبر طلبات النطاق الجزئية (HTTP Range) —
 * يقرأ فهرس الأرشيف من نهايته فقط (ذيل ~70KB) ثم يجلب أي ملف وحده من
 * داخل الأرشيف دون تنزيله كاملاً. بهذا تُقرأ مكتبة «ثيماتي» (9.9MB)
 * بصورة مقتصدة: الفهرس والفهارس النصية فقط (~0.3MB) ثم كل صورة يختارها
 * المستخدم تُجلب وحدها وتُحفظ في الذاكرة الداخلية — بلا أي زيادة في حجم
 * التطبيق.
 *
 * جافا نقية بلا أي اعتماد على أندرويد — قابلة للاختبار الحقيقي خارج
 * الجهاز (أداة tools/ThematyZipSelfTest).
 */
public final class ZipRangeReader {

  /** مصدر نطاقات: يعيد حجم الأرشيف الكلي ويجلب مقطعاً محدداً منه. */
  public interface Fetcher {
    long totalSize() throws Exception;

    byte[] range(long start, int length) throws Exception;
  }

  /** مدخل واحد في فهرس الأرشيف المركزي. */
  public static final class Entry {
    public final String name;
    public final int method; // 0=مخزّن، 8=مضغوط deflate
    public final int compressedSize;
    public final int uncompressedSize;
    public final long offset; // إزاحة رأس الملف المحلي داخل الأرشيف

    Entry(String name, int method, int compressedSize, int uncompressedSize, long offset) {
      this.name = name;
      this.method = method;
      this.compressedSize = compressedSize;
      this.uncompressedSize = uncompressedSize;
      this.offset = offset;
    }
  }

  /** جالب نطاقات عبر HTTP — يعمل مع أي خادم يدعم Accept-Ranges. */
  public static final class HttpFetcher implements Fetcher {
    private final String url;
    private final String userAgent;
    private final String bearerToken;

    public HttpFetcher(String url, String userAgent) {
      this(url, userAgent, null);
    }

    public HttpFetcher(String url, String userAgent, String bearerToken) {
      this.url = url;
      this.userAgent = userAgent;
      this.bearerToken = bearerToken;
    }

    public long totalSize() throws Exception {
      HttpURLConnection link = open("bytes=0-0");
      try {
        int status = link.getResponseCode();
        if (status != 206) throw new IOException("HTTP " + status + " — لا يدعم النطاقات");
        String contentRange = link.getHeaderField("Content-Range"); // bytes 0-0/9912029
        if (contentRange == null) throw new IOException("بلا Content-Range");
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0) throw new IOException("Content-Range غريب: " + contentRange);
        return Long.parseLong(contentRange.substring(slash + 1).trim());
      } finally {
        link.disconnect();
      }
    }

    public byte[] range(long start, int length) throws Exception {
      Exception last = null;
      for (int attempt = 0; attempt < 2; attempt++) {
        if (attempt > 0) {
          try {
            Thread.sleep(1200L);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
          }
        }
        try {
          HttpURLConnection link = open("bytes=" + start + "-" + (start + length - 1));
          try {
            int status = link.getResponseCode();
            if (status != 206) throw new IOException("HTTP " + status);
            InputStream stream = link.getInputStream();
            try {
              ByteArrayOutputStream buffer = new ByteArrayOutputStream();
              byte[] chunk = new byte[8192];
              int read;
              while ((read = stream.read(chunk)) > 0) buffer.write(chunk, 0, read);
              return buffer.toByteArray();
            } finally {
              stream.close();
            }
          } finally {
            link.disconnect();
          }
        } catch (Exception error) {
          last = error;
        }
      }
      throw last;
    }

    private HttpURLConnection open(String range) throws Exception {
      HttpURLConnection link = (HttpURLConnection) new URL(url).openConnection();
      link.setConnectTimeout(12000);
      link.setReadTimeout(25000);
      link.setInstanceFollowRedirects(true);
      link.setRequestProperty("User-Agent", userAgent);
      link.setRequestProperty("Range", range);
      if (bearerToken != null && bearerToken.length() > 0) {
        link.setRequestProperty("Authorization", "Bearer " + bearerToken);
      }
      return link;
    }
  }

  /** سقف بحجم ملف واحد يجلب من الأرشيف — كل ملفات المكتبة أصغر بكثير. */
  private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
  /** حجم الذيل الأولي لالتقاط EOCD والفهرس المركزي (~60KB لـ636 ملفاً). */
  private static final int TAIL_HINT = 70000;

  private final Fetcher fetcher;
  private final Map<String, Entry> entries = new HashMap<String, Entry>();
  private long totalSize = -1;

  public ZipRangeReader(Fetcher fetcher) {
    this.fetcher = fetcher;
  }

  /** حجم الأرشيف الكلي بالبايت (بعد loadIndex). */
  public long totalSize() {
    return totalSize;
  }

  /** عدد مدخلات الفهرس المفكوكة. */
  public int count() {
    return entries.size();
  }

  /** هل يوجد ملف بهذا الاسم في الأرشيف؟ */
  public boolean has(String name) {
    return entries.containsKey(name);
  }

  /** أسماء المدخلات التي تحوي جزءاً معيناً (للتشخيص والاختبار). */
  public List<String> namesContaining(String part) {
    List<String> found = new ArrayList<String>();
    for (Entry entry : entries.values()) {
      if (entry.name.contains(part)) found.add(entry.name);
    }
    return found;
  }

  /**
   * يحمّل الفهرس: حجم الأرشيف ثم الذيل ثم EOCD ثم الفهرس المركزي.
   * يكفي استدعاؤه مرة — الخريطة بعدها للقراءة فقط وآمنة بين الخيوط.
   */
  public void loadIndex() throws Exception {
    if (!entries.isEmpty()) return;
    synchronized (this) {
      if (!entries.isEmpty()) return;
      totalSize = fetcher.totalSize();
      long tailLength = Math.min(TAIL_HINT, totalSize);
      byte[] tail = fetcher.range(totalSize - tailLength, (int) tailLength);
      long[] eocd = findEocd(tail);
      if (eocd == null) throw new IOException("لم يُعثر على نهاية الفهرس (EOCD)");
      long cdSize = eocd[0];
      long cdOffset = eocd[1];
      if (cdSize <= 0 || cdSize > MAX_FILE_BYTES) throw new IOException("حجم فهرس مرفوض: " + cdSize);
      if (cdOffset < 0 || cdOffset >= totalSize) throw new IOException("إزاحة فهرس مرفوضة: " + cdOffset);
      byte[] directory = tail;
      // موضع بداية الفهرس داخل الذيل: إزاحته المطلقة مطروحاً منها بداية الذيل
      int directoryStart = (int) (tail.length - (totalSize - cdOffset));
      if (directoryStart < 0 || directoryStart + cdSize > tail.length) {
        // الفهرس أكبر من الذيل الملتقط — يُجلب وحده بنطاق مضبوط
        directory = fetcher.range(cdOffset, (int) cdSize);
        directoryStart = 0;
      }
      Map<String, Entry> parsed =
          parseCentralDirectory(directory, directoryStart, (int) (directoryStart + cdSize));
      if (parsed.isEmpty()) throw new IOException("فهرس مركزي فارغ");
      entries.putAll(parsed);
    }
  }

  /**
   * يجلب ملفاً واحداً من داخل الأرشيف: رأسه المحلي أولاً (لاستنتاج إزاحة
   * البيانات الفعلية) ثم بياناته المضغوطة ثم فك الضغط.
   */
  public byte[] readFile(String name) throws Exception {
    Entry entry = entries.get(name);
    if (entry == null) throw new IOException("ليس في الأرشيف: " + name);
    if (entry.compressedSize <= 0 || entry.compressedSize > MAX_FILE_BYTES)
      throw new IOException("حجم مضغوط مرفوض: " + entry.compressedSize);
    byte[] header = fetcher.range(entry.offset, 30);
    if (le16(header, 0) != 0x4b50 || le16(header, 2) != 0x0403) // توقيع PK\x03\x04
      throw new IOException("رأس محلي تالف عند " + entry.offset);
    int nameLength = le16(header, 26);
    int extraLength = le16(header, 28);
    long dataStart = entry.offset + 30 + nameLength + extraLength;
    byte[] compressed = fetcher.range(dataStart, entry.compressedSize);
    if (compressed.length != entry.compressedSize)
      throw new IOException("قراءة ناقصة: " + compressed.length + "/" + entry.compressedSize);
    return inflate(compressed, entry.method, entry.uncompressedSize);
  }

  // ------------------------------------------------------------ تحليل ثابت

  /**
   * يبحث من النهاية عن توقيع EOCD (PK\x05\x06) — يعيد {حجم الفهرس،
   * إزاحته، عدد المدخلات} أو null. لا يتعامل مع ZIP64 (أرشيفنا أصغر
   * بكثير من 4GB فيرفضه صراحة).
   */
  static long[] findEocd(byte[] buffer) {
    for (int index = buffer.length - 22; index >= 0; index--) {
      if (buffer[index] != 'P' || buffer[index + 1] != 'K') continue;
      if (buffer[index + 2] != 5 || buffer[index + 3] != 6) continue;
      int entryCount = le16(buffer, index + 10);
      long cdSize = le32(buffer, index + 12) & 0xffffffffL;
      long cdOffset = le32(buffer, index + 16) & 0xffffffffL;
      if (cdSize == 0xffffffffL || cdOffset == 0xffffffffL || entryCount == 0xffff)
        return null; // ZIP64 — خارج النطاق
      return new long[] {cdSize, cdOffset, entryCount};
    }
    return null;
  }

  /** يفك مدخلات الفهرس المركزي (PK\x01\x02) داخل النطاق المحدد. */
  static Map<String, Entry> parseCentralDirectory(byte[] directory, int start, int end) {
    Map<String, Entry> parsed = new HashMap<String, Entry>();
    int position = start;
    while (position + 46 <= end) {
      if (le16(directory, position) != 0x4b50 || le16(directory, position + 2) != 0x0201) break;
      int method = le16(directory, position + 10);
      int compressedSize = le32(directory, position + 20);
      int uncompressedSize = le32(directory, position + 24);
      int nameLength = le16(directory, position + 28);
      int extraLength = le16(directory, position + 30);
      int commentLength = le16(directory, position + 32);
      long localOffset = le32(directory, position + 42) & 0xffffffffL;
      if (position + 46 + nameLength > end) break;
      String name = new String(directory, position + 46, nameLength, java.nio.charset.Charset.forName("UTF-8"));
      parsed.put(
          name,
          new Entry(name, method, compressedSize, uncompressedSize, localOffset));
      position += 46 + nameLength + extraLength + commentLength;
    }
    return parsed;
  }

  /** يفك الضغط: المخزّن يعود كما هو، وdeflate عبر Inflater خام. */
  static byte[] inflate(byte[] compressed, int method, int expectedSize) throws IOException {
    if (expectedSize < 0 || expectedSize > MAX_FILE_BYTES)
      throw new IOException("حجم مفكوك مرفوض: " + expectedSize);
    if (method == 0) return compressed;
    if (method != 8) throw new IOException("طريقة ضغط غير مدعومة: " + method);
    Inflater inflater = new Inflater(true); // deflate خام (بلا ترويسة zlib)
    try {
      inflater.setInput(compressed);
      ByteArrayOutputStream output = new ByteArrayOutputStream(
          Math.max(256, Math.min(expectedSize, MAX_FILE_BYTES)));
      byte[] chunk = new byte[16384];
      while (!inflater.finished()) {
        int written = inflater.inflate(chunk);
        if (written == 0) {
          if (inflater.needsInput() || inflater.needsDictionary())
            throw new IOException("بيانات deflate ناقصة");
        } else {
          output.write(chunk, 0, written);
          if (output.size() > MAX_FILE_BYTES) throw new IOException("تفكيك يتجاوز السقف");
        }
      }
      if (expectedSize > 0 && output.size() != expectedSize)
        throw new IOException(
            "حجم مفكوك مختلف: " + output.size() + " بدل " + expectedSize);
      return output.toByteArray();
    } catch (DataFormatException bad) {
      throw new IOException("بيانات تالفة: " + bad);
    } finally {
      inflater.end();
    }
  }

  /** قراءة عدد 16-بت little-endian. */
  static int le16(byte[] buffer, int offset) {
    return (buffer[offset] & 0xff) | ((buffer[offset + 1] & 0xff) << 8);
  }

  /** قراءة عدد 32-بت little-endian (كـ int قد يكون سالباً — قنّعه عند الحاجة). */
  static int le32(byte[] buffer, int offset) {
    return (buffer[offset] & 0xff)
        | ((buffer[offset + 1] & 0xff) << 8)
        | ((buffer[offset + 2] & 0xff) << 16)
        | ((buffer[offset + 3] & 0xff) << 24);
  }
}
