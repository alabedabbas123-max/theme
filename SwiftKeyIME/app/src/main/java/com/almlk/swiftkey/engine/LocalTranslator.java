package com.almlk.swiftkey.engine;

import java.util.*;

public final class LocalTranslator {
  private static final Map<String, Map<String, String>>
      SOURCE = new HashMap<String, Map<String, String>>(),
      TARGET = new HashMap<String, Map<String, String>>();

  static {
    addSource(
        "ar",
        "مرحبا=hello|السلام=peace|انا=i|أنا=i|انت=you|أنت=you|كيف=how|شكرا=thank|نعم=yes|لا=no|هذا=this|هذه=this|اليوم=today|جميل=beautiful|لوحة=keyboard|المفاتيح=keyboard|اللغة=language|العربية=arabic|احبك=love|أحبك=love");
    addSource(
        "en",
        "hello=hello|hi=hello|i=i|you=you|how=how|are=are|thank=thank|yes=yes|no=no|this=this|today=today|beautiful=beautiful|keyboard=keyboard|language=language|love=love|peace=peace");
    addSource(
        "zh",
        "你好=hello|我=i|你=you|谢谢=thank|是=yes|不=no|今天=today|漂亮=beautiful|键盘=keyboard|语言=language|爱=love|和平=peace");
    addTarget(
        "ar",
        "hello=مرحبا|i=أنا|you=أنت|how=كيف|are=تكون|thank=شكرا|yes=نعم|no=لا|this=هذا|today=اليوم|beautiful=جميل|keyboard=لوحة"
            + " المفاتيح|language=اللغة|love=أحب|peace=السلام");
    addTarget(
        "en",
        "hello=hello|i=I|you=you|how=how|are=are|thank=thank"
            + " you|yes=yes|no=no|this=this|today=today|beautiful=beautiful|keyboard=keyboard|language=language|love=love|peace=peace");
    addTarget(
        "zh",
        "hello=你好|i=我|you=你|how=怎么|are=是|thank=谢谢|yes=是|no=不|this=这个|today=今天|beautiful=漂亮|keyboard=键盘|language=语言|love=爱|peace=和平");
    addTarget(
        "fr",
        "hello=bonjour|i=je|you=vous|how=comment|are=êtes|thank=merci|yes=oui|no=non|this=ceci|today=aujourd’hui|beautiful=beau|keyboard=clavier|language=langue|love=aimer|peace=paix");
    addTarget(
        "es",
        "hello=hola|i=yo|you=tú|how=cómo|are=eres|thank=gracias|yes=sí|no=no|this=esto|today=hoy|beautiful=hermoso|keyboard=teclado|language=idioma|love=amor|peace=paz");
    addTarget(
        "tr",
        "hello=merhaba|i=ben|you=sen|how=nasıl|are=olmak|thank=teşekkürler|yes=evet|no=hayır|this=bu|today=bugün|beautiful=güzel|keyboard=klavye|language=dil|love=sevgi|peace=barış");
    addTarget(
        "de",
        "hello=hallo|i=ich|you=du|how=wie|are=bist|thank=danke|yes=ja|no=nein|this=dies|today=heute|beautiful=schön|keyboard=tastatur|language=sprache|love=liebe|peace=frieden");
  }

  private static void addSource(String code, String values) {
    SOURCE.put(code, parse(values));
  }

  private static void addTarget(String code, String values) {
    TARGET.put(code, parse(values));
  }

  private static Map<String, String> parse(String value) {
    HashMap<String, String> m = new HashMap<String, String>();
    for (String pair : value.split("\\|")) {
      int i = pair.indexOf('=');
      if (i > 0) m.put(pair.substring(0, i), pair.substring(i + 1));
    }
    return m;
  }

  public static String detect(String text) {
    if (text != null && text.matches(".*[\\u0600-\\u06FF].*")) return "ar";
    if (text != null && text.matches(".*[\\u3400-\\u9FFF].*")) return "zh";
    return "en";
  }

  private static String phrase(String text, String from, String to) {
    String q = text.trim().toLowerCase(Locale.ROOT), pair = from + "|" + to;
    if (pair.equals("en|ar")) {
      if (q.equals("well im okay") || q.equals("well i'm okay")) return "حسناً، أنا بخير";
      if (q.equals("i am okay") || q.equals("i'm okay") || q.equals("im okay")) return "أنا بخير";
      if (q.equals("hello how are you")) return "مرحبا، كيف حالك";
      if (q.equals("thank you")) return "شكرا لك";
    }
    if (pair.equals("ar|en")) {
      if (q.equals("مرحبا كيف حالك")) return "hello, how are you";
      if (q.equals("انا بخير") || q.equals("أنا بخير")) return "I am okay";
      if (q.equals("شكرا لك")) return "thank you";
      if (q.equals("السلام عليكم")) return "peace be upon you";
    }
    if (pair.equals("ar|zh")) {
      if (q.equals("مرحبا")) return "你好";
      if (q.equals("انا بخير") || q.equals("أنا بخير")) return "我很好";
    }
    if (pair.equals("en|zh")) {
      if (q.equals("hello")) return "你好";
      if (q.equals("i am okay")) return "我很好";
    }
    return null;
  }

  public static String translate(String text, String from, String to) {
    String phrase = phrase(text, from, to);
    if (phrase != null) return phrase;
    if (text == null || text.isEmpty() || from.equals(to)) return text == null ? "" : text;
    Map<String, String> source = SOURCE.containsKey(from) ? SOURCE.get(from) : SOURCE.get("en"),
        target = TARGET.containsKey(to) ? TARGET.get(to) : TARGET.get("en");
    StringBuilder out = new StringBuilder();
    for (String token : text.split("(?=[\\s،,.!?؟؛])|(?<=[\\s،,.!?؟؛])")) {
      String lower = token.toLowerCase(Locale.ROOT),
          pivot =
              source.containsKey(token)
                  ? source.get(token)
                  : source.containsKey(lower) ? source.get(lower) : lower;
      out.append(target.containsKey(pivot) ? target.get(pivot) : pivot);
    }
    return out.toString();
  }

  public interface TranslationCallback {
    void onResult(String translated);
  }

  public static void translateAsync(
      final String text, final String from, final String to, final TranslationCallback callback) {
    final String fallback = translate(text, from, to);
    callback.onResult(fallback);
    if (text == null || text.trim().isEmpty() || from.equals(to)) return;
    new Thread(
            new Runnable() {
              public void run() {
                java.net.HttpURLConnection connection = null;
                try {
                  String source = "zh".equals(from) ? "zh-CN" : from,
                      target = "zh".equals(to) ? "zh-CN" : to;
                  String address =
                      "https://api.mymemory.translated.net/get?q="
                          + java.net.URLEncoder.encode(text, "UTF-8")
                          + "&langpair="
                          + java.net.URLEncoder.encode(source + "|" + target, "UTF-8");
                  connection =
                      (java.net.HttpURLConnection) new java.net.URL(address).openConnection();
                  connection.setConnectTimeout(5000);
                  connection.setReadTimeout(7000);
                  connection.setRequestProperty("Accept", "application/json");
                  java.io.BufferedReader reader =
                      new java.io.BufferedReader(
                          new java.io.InputStreamReader(connection.getInputStream(), "UTF-8"));
                  StringBuilder json = new StringBuilder();
                  String line;
                  while ((line = reader.readLine()) != null) json.append(line);
                  reader.close();
                  final String value =
                      new org.json.JSONObject(json.toString())
                          .getJSONObject("responseData")
                          .optString("translatedText", fallback);
                  new android.os.Handler(android.os.Looper.getMainLooper())
                      .post(
                          new Runnable() {
                            public void run() {
                              callback.onResult(value);
                            }
                          });
                } catch (Exception ignored) {
                } finally {
                  if (connection != null) connection.disconnect();
                }
              }
            },
            "AlmlkTranslator")
        .start();
  }

  private LocalTranslator() {}
}
