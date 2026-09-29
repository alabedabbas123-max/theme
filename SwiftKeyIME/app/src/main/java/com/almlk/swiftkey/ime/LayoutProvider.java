package com.almlk.swiftkey.ime;

import com.almlk.swiftkey.model.*;
import java.util.*;

public final class LayoutProvider {
  private static List<KeySpec> row(KeySpec... k) {
    return Arrays.asList(k);
  }

  private static KeySpec k(String l, String sub, String alt) {
    return KeySpec.c(l, sub, alt);
  }

  private static KeySpec s(String l, int c, float w) {
    return KeySpec.s(l, c, w);
  }

  public static KeyboardLayout arabic() {
    return arabic("text");
  }

  public static KeyboardLayout arabic(String context, String variant) {
    if ("ARABIC_ORIGINAL".equals(variant)) {
      return arabicRows(context, 1, "123");
    }
    if ("ARABIC_PC".equals(variant)) {
      return arabicRows(context, 2, "123");
    }
    if ("ARABIC_123".equals(variant)) {
      return arabicRows(context, 0, "\u0661\u0662\u0663\u061F");
    }
    if ("ARABIC_102".equals(variant)) {
      return arabic102Rows(context);
    }
    if ("ARABIC_ORIGINAL_DIGITS".equals(variant)) {
      return arabicRows(context, 1, "123");
    }
    return arabic(context);
  }

  public static KeyboardLayout arabic102() {
    return arabic102Rows("text");
  }

  public static KeyboardLayout arabic(String context) {
    return arabicRows(context, 0, "\u0661\u0662\u0663\u061F");
  }

  /**
   * Shared builder for the three Arabic arrangements from the reference screenshots:
   * kind 0 = bare 123 (home row ends with ط، third row ذ ء ؤ ر ى ة و ز ظ د)،
   * kind 1 = الأصلية (home row ends with ة، third row ء ظ ط ذ د ز و ر ى ـَ), and
   * kind 2 = كمبيوتر شخصي (top row keeps د، home ends with ز، third row ئ ء ؤ ر ى ة و ز ط ظ).
   */
  private static KeyboardLayout arabicRows(String context, int kind, String modeLabel) {
    List<List<KeySpec>> r = new ArrayList<List<KeySpec>>();
    List<KeySpec> top = new ArrayList<KeySpec>();
    String topDigits = "1234567890";
    if (modeLabel.indexOf('\u0661') == 0) {
      topDigits = "\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669\u0660";
    }
    String topLetters = "\u0636\u0635\u062b\u0642\u0641\u063a\u0639\u0647\u062e\u062d";
    for (int i = 0; i < topLetters.length() && i < topDigits.length(); i++) {
      top.add(k(String.valueOf(topLetters.charAt(i)), String.valueOf(topDigits.charAt(i)), ""));
    }
    top.add(k("\u062c", "\u0686", "\u062c \u0686"));
    if (kind == 2) {
      top.add(k("\u062f", "\u0630", "\u062f \u0630"));
    }
    r.add(top);
    String[] homeLetters = {
      "\u0634", "\u0633", "\u064a", "\u0628", "\u0644", "\u0627", "\u062a", "\u0646", "\u0645", "\u0643"
    };
    // trails pair with letters one-for-one: ش@ س# ي& بپ ل-«لا» أ= ت( ن م) كگ — «لا» is one label
    String[] homeTrails = {
      "@", "#", "&", "\u067e", "\u0644\u0627", "\u0623", "=", "(", ")", "\u06af"
    };
    String homeTail = kind == 1 ? "\u0629" : kind == 2 ? "\u0632" : "\u0637";
    List<KeySpec> home = new ArrayList<KeySpec>();
    for (int i = 0; i < homeLetters.length && i < homeTrails.length; i++) {
      home.add(k(homeLetters[i], homeTrails[i], ""));
    }
    home.add(k(homeTail, kind == 1 ? "\u061b" : "*", ""));
    r.add(home);
    List<KeySpec> third = new ArrayList<KeySpec>();
    if (kind == 0) {
      third.add(k("\u0630", "*", ""));
      third.add(k("\u0621", "~", ""));
      third.add(k("\u0624", "-", ""));
      third.add(k("\u0631", "+", ""));
      third.add(k("\u0649", ":", ""));
      third.add(k("\u0629", "\u061b", ""));
      third.add(k("\u0648", "/", ""));
      third.add(k("\u0632", "\"", ""));
      third.add(k("\u0638", "!", ""));
      third.add(k("\u062f", "\u0630", "\u062f \u0630"));
    } else if (kind == 1) {
      third.add(k("\u0621", "~", ""));
      third.add(k("\u0638", "!", ""));
      third.add(k("\u0637", "\u061F", ""));
      third.add(k("\u0630", "*", ""));
      third.add(k("\u062f", "\u0630", "\u062f \u0630"));
      third.add(k("\u0632", "\"", ""));
      third.add(k("\u0648", "/", ""));
      third.add(k("\u0631", "+", ""));
      third.add(k("\u0649", ":", ""));
      third.add(k("\u0640\u064E", "", ""));
    } else {
      third.add(k("\u0626", "\u200d", ""));
      third.add(k("\u0621", "~", ""));
      third.add(k("\u0624", "-", ""));
      third.add(k("\u0631", "+", ""));
      third.add(k("\u0649", ":", ""));
      third.add(k("\u0629", "\u061b", ""));
      third.add(k("\u0648", "/", ""));
      third.add(k("\u0632", "\"", ""));
      third.add(k("\u0637", "\u061F", ""));
      third.add(k("\u0638", "!", ""));
    }
    third.add(s("", KeySpec.DELETE, 1.35f));
    r.add(third);
    r.add(
        row(
            s(modeLabel, KeySpec.MODE, 1.35f),
            s("", KeySpec.EMOJI, 1.15f),
            s("\u060c", KeySpec.MIC, 1.1f),
            s("\u0627\u0644\u0639\u0631\u0628\u064a\u0629", KeySpec.SPACE, 4.5f),
            contextKey(context, true),
            s("", KeySpec.ENTER, 1.4f)));
    return new KeyboardLayout(r, true, 1);
  }

/**
   * The EXACT reference-screenshot arrangement (xkb symbols/ara, Arabic PC 102):
   * ض ص ث ق ف غ ع ه خ ح ج د / ش س ي ب ل ا ت ن م ك ط ذ / ئ ء ؤ ر ﻻ ى ة و ز ظ + delete,
   * small key-top hints are the real shift layer (◌َ ً ◌ُ ◌ٌ ڤ إ ` ÷ × ؛ ذ) and the popups
   * carry FUTO's alternates. Mirrors kbd_arabic_102.xml 1:1 for the no-XML fallback.
   */
  private static KeyboardLayout arabic102Rows(String context) {
    List<List<KeySpec>> r = new ArrayList<List<KeySpec>>();
    r.add(row(
        k("\u0636", "\u0640\u064E", ""), k("\u0635", "\u0640\u064B", ""),
        k("\u062B", "\u0640\u064F", ""), k("\u0642", "\u0640\u064C", "\u06a8"),
        k("\u0641", "\u06a4", "\u06a4\u06a2\u06a5"), k("\u063a", "\u0625", ""),
        k("\u0639", "`", ""), k("\u0647", "\u00f7", ""), k("\u062e", "\u00d7", ""),
        k("\u062d", ";", ""), k("\u062c", "\u0686", "\u0686"), k("\u062f", "\u0630", "\u0630")));
    r.add(row(
        k("\u0634", "\u0640\u0650", "\u069c"), k("\u0633", "\u0640\u064d", ""),
        k("\u064a", "]", "\u0626 \u0649"), k("\u0628", "[", "\u067e"),
        k("\u0644", "\ufefb", "\u0644\u0623 \u0644\u0622"),
        k("\u0627", "\u0623", "\u0622 \u0621 \u0625 \u0671"),
        k("\u062a", "\u0640", ""), k("\u0646", "\u060c", "\u066b"),
        k("\u0645", "/", ""), k("\u0643", "\u06af", "\u06a9"),
        k("\u0637", "\"\u0640", ""), k("\u0630", "\u0640\u0651", "\u066a")));
    r.add(row(
        k("\u0626", "~", "\u00bb"), k("\u0621", "\u0640\u0652", "\u00ab"),
        k("\u0624", "}", ""), k("\u0631", "{", ""), k("\ufefb", "", "\u0644\u0623"),
        k("\u0649", "\u0622", ""), k("\u0629", "'", ""), k("\u0648", ",", "\u066c"),
        k("\u0632", ".", "\u0698"), k("\u0638", "\u061f", ""),
        s("", KeySpec.DELETE, 1.35f)));
    r.add(row(
        s("123", KeySpec.MODE, 1.35f),
        s("", KeySpec.EMOJI, 1.15f),
        s("\u060c", KeySpec.MIC, 1.1f),
        s("\u0627\u0644\u0639\u0631\u0628\u064a\u0629", KeySpec.SPACE, 4.5f),
        contextKey(context, true),
        s("", KeySpec.ENTER, 1.4f)));
    return new KeyboardLayout(r, true, 0, 12);
  }

  public static KeyboardLayout english(boolean shifted) {
    return english(shifted, "text", "QWERTY");
  }

  public static KeyboardLayout english(boolean shifted, String context) {
    return english(shifted, context, "QWERTY");
  }

  public static KeyboardLayout english(boolean shifted, String context, String variant) {
    String top = "qwertyuiop", middle = "asdfghjkl", bottom = "zxcvbnm";
    if ("AZERTY".equals(variant)) {
      top = "azertyuiop";
      middle = "qsdfghjklm";
      bottom = "wxcvbn'";
    } else if ("DVORAK".equals(variant)) {
      top = "'pyfgcrl";
      middle = "aoeuidhtns";
      bottom = "qjkxbmwvz";
    } else if ("QWERTZ".equals(variant)) {
      top = "qwertzuiop";
      middle = "asdfghjkl";
      bottom = "yxcvbnm";
    } else if ("COLEMAK".equals(variant)) {
      top = "qwfpgjluy";
      middle = "arstdhneio";
      bottom = "zxcvbkm";
    } else if ("QZERTY".equals(variant)) {
      top = "qzertyuiop";
      middle = "asdfghjklm";
      bottom = "wxcvbn";
    }
    List<List<KeySpec>> r = new ArrayList<List<KeySpec>>();
    r.add(chars(top, shifted));
    r.add(chars(middle, shifted));
    List<KeySpec> third = new ArrayList<KeySpec>();
    third.add(s("", KeySpec.SHIFT, 1.35f));
    third.addAll(chars(bottom, shifted));
    third.add(s("", KeySpec.DELETE, 1.35f));
    r.add(third);
    r.add(
        row(
            s("123", KeySpec.MODE, 1.35f),
            s("", KeySpec.EMOJI, 1.15f),
            s(",", KeySpec.MIC, 1.1f),
            s("English (US)", KeySpec.SPACE, 4.5f),
            contextKey(context, false),
            s("", KeySpec.ENTER, 1.4f)));
    return new KeyboardLayout(r, false, 0);
  }

  private static KeySpec contextKey(String context, boolean ar) {
    if ("email".equals(context)) return k("@", ".", ".com");
    if ("url".equals(context)) return k("/", ".", ".com");
    return ar ? k("؟", "!", "؟ !") : k(".", "?", ". ?");
  }

  public static KeyboardLayout numeric(boolean phone) {
    List<List<KeySpec>> r = new ArrayList<List<KeySpec>>();
    r.add(
        row(
            s("1", '1', 1.58f),
            s("2", '2', 1.58f),
            s("3", '3', 1.58f),
            k("(", "", ""),
            k(")", "", ""),
            k("،", "", "")));
    r.add(
        row(
            s("4", '4', 1.58f),
            s("5", '5', 1.58f),
            s("6", '6', 1.58f),
            k("+", "", ""),
            k("-", "", ""),
            k("؛", "", "")));
    r.add(
        row(
            s("7", '7', 1.58f),
            s("8", '8', 1.58f),
            s("9", '9', 1.58f),
            k("/", "", ""),
            k(phone ? "N" : "−", "", ""),
            s("", KeySpec.DELETE, 1.05f)));
    r.add(
        row(
            s("*", '*', 1.58f),
            s("0", '0', 1.58f),
            s("#", '#', 1.58f),
            k(".", "", ""),
            s("", 0, 1f),
            s("", KeySpec.ENTER, 1.08f)));
    return new KeyboardLayout(r, false, -1);
  }

  private static List<KeySpec> chars(String value, boolean upper) {
    List<KeySpec> out = new ArrayList<KeySpec>();
    for (char ch : value.toCharArray()) {
      String x = String.valueOf(ch), l = upper ? x.toUpperCase(Locale.US) : x;
      out.add(k(l, secondary(x), accent(x)));
    }
    return out;
  }

  private static String secondary(String value) {
    if ("q".equals(value)) return "%";
    if ("w".equals(value)) return "^";
    if ("e".equals(value)) return "~";
    if ("r".equals(value)) return "|";
    if ("t".equals(value)) return "[";
    if ("y".equals(value)) return "]";
    if ("u".equals(value)) return "<";
    if ("i".equals(value)) return ">";
    if ("o".equals(value)) return "{";
    if ("p".equals(value)) return "}";
    if ("a".equals(value)) return "@";
    if ("s".equals(value)) return "#";
    if ("d".equals(value)) return "&";
    if ("f".equals(value)) return "*";
    if ("g".equals(value)) return "-";
    if ("h".equals(value)) return "+";
    if ("j".equals(value)) return "=";
    if ("k".equals(value)) return "(";
    if ("l".equals(value)) return ")";
    if ("z".equals(value)) return "_";
    if ("x".equals(value)) return "$";
    if ("c".equals(value)) return "\"";
    if ("v".equals(value)) return "'";
    if ("b".equals(value)) return ":";
    if ("n".equals(value)) return ";";
    if ("m".equals(value)) return "/";
    return "";
  }

  private static String accent(String x) {
    if (x.equals("a")) return "a á à â ä æ";
    if (x.equals("e")) return "e é è ê ë";
    if (x.equals("i")) return "i í ì î ï";
    if (x.equals("o")) return "o ó ò ô ö œ";
    if (x.equals("u")) return "u ú ù û ü";
    if (x.equals("n")) return "n ñ";
    return "";
  }

  public static KeyboardLayout symbols(boolean second, String language) {
    List<List<KeySpec>> r = new ArrayList<List<KeySpec>>();
    if (!second) {
      r.add(chars("1234567890", false));
      r.add(
          row(
              k("@", "", ""),
              k("#", "", ""),
              k("$", "", ""),
              k("&", "", ""),
              k("_", "", ""),
              k("-", "", ""),
              k("(", "", ""),
              k(")", "", ""),
              k("=", "", ""),
              k("%", "", "")));
      r.add(
          row(
              s("{&=}", KeySpec.PAGE, 1.25f),
              k("\"", "", ""),
              k("*", "", ""),
              k("'", "", ""),
              k(":", "", ""),
              k("/", "", ""),
              k("!", "", ""),
              k("?", "", ""),
              k("+", "", ""),
              s("", KeySpec.DELETE, 1.25f)));
      r.add(
          row(
              s("ar".equals(language) ? "ابت" : "ABC", KeySpec.MODE, 1.35f),
              k("،", "", ""),
              s("ar".equals(language) ? "العربية" : "English (US)", KeySpec.SPACE, 4.4f),
              k(".", "", ""),
              s("", KeySpec.ENTER, 1.4f)));
    } else {
      r.add(
          row(
              k("£", "", ""),
              k("€", "", ""),
              k("¥", "", ""),
              k("﷼", "", ""),
              k("©", "", ""),
              k("®", "", ""),
              k("™", "", ""),
              k("~", "", ""),
              k("؛", "", "")));
      r.add(
          row(
              s("", KeySpec.TAB, 1.25f),
              k("[", "", ""),
              k("]", "", ""),
              k("{", "", ""),
              k("}", "", ""),
              k("<", "", ""),
              k(">", "", ""),
              k("^", "", ""),
              k("!", "", "")));
      r.add(
          row(
              s("123", KeySpec.PAGE, 1.25f),
              k("`", "", ""),
              k("،", "", ""),
              k("÷", "", ""),
              k("\\", "", ""),
              k("|", "", ""),
              k("¦", "", ""),
              k("¬", "", ""),
              s("", KeySpec.DELETE, 1.25f)));
      r.add(
          row(
              s("ar".equals(language) ? "ابت" : "ABC", KeySpec.MODE, 1.3f),
              s("ar".equals(language) ? "العربية" : "English (US)", KeySpec.SPACE, 3.9f),
              k("×", "", ""),
              k("§", "", ""),
              k("¶", "", ""),
              k("°", "", ""),
              s("", KeySpec.ENTER, 1.3f)));
    }
    return new KeyboardLayout(r, false, -1);
  }
}
