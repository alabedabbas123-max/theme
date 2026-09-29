package com.almlk.swiftkey.model;

import com.almlk.swiftkey.R;

/**
 * Registry for every keyboard layout exposed through the XML keyboardLayout attribute. Layout
 * order defines the cycle order of the toolbar switcher and the settings carousel. The Arabic
 * group mirrors the reference screenshots exactly: three key arrangements (bare 123،
 * \u0627\u0644\u0623\u0635\u0644\u064a\u0629 \u0648\u0643\u0645\u0628\u064a\u0648\u062a\u0631 \u0634\u062e\u0635\u064a) offered in both digit styles.
 */
public final class KeyboardLayouts {
  public static final int ENGLISH_US = 0;
  public static final int AZERTY = 1;
  public static final int QWERTZ = 2;
  public static final int DVORAK = 3;
  public static final int COLEMAK = 4;
  public static final int QZERTY = 5;
  public static final int ARABIC_ORIGINAL = 6;
  public static final int ARABIC_DIGITS = 7;
  public static final int ARABIC_PC = 8;
  public static final int ARABIC_123 = 9;
  public static final int ARABIC_102 = 12;   // نسخة طبق الأصل من tخطيط الصورة — الافتراضي
  public static final int ARABIC_ORIGINAL_DIGITS = 11;

  public static final class Entry {
    public final String name;
    public final String token;
    public final int id;
    public final String title;
    public final int xmlResource;
    public final boolean arabic;

    Entry(String name, String token, int id, String title, int xmlResource, boolean arabic) {
      this.name = name;
      this.token = token;
      this.id = id;
      this.title = title;
      this.xmlResource = xmlResource;
      this.arabic = arabic;
    }
  }

  private static final Entry[] ENTRIES = {
    new Entry(
        "QWERTY",
        "english_us",
        ENGLISH_US,
        "QWERTY",
        R.xml.kbd_qwerty,
        false),
    new Entry(
        "AZERTY",
        "azerty",
        AZERTY,
        "AZERTY",
        R.xml.kbd_azerty,
        false),
    new Entry(
        "QWERTZ",
        "qwertz",
        QWERTZ,
        "QWERTZ",
        R.xml.kbd_qwertz,
        false),
    new Entry(
        "DVORAK",
        "dvorak",
        DVORAK,
        "Dvorak",
        R.xml.kbd_dvorak,
        false),
    new Entry(
        "COLEMAK",
        "colemak",
        COLEMAK,
        "Colemak",
        R.xml.kbd_colemak,
        false),
    new Entry(
        "QZERTY",
        "qzerty",
        QZERTY,
        "QZERTY",
        R.xml.kbd_qzerty,
        false),
    new Entry(
        "ARABIC_123",
        "arabic_azerty",
        ARABIC_123,
        "\u0627\u0644\u0639\u0631\u0628\u064a\u0629 123",
        R.xml.kbd_arabic_azerty,
        true),
    new Entry(
        "ARABIC_ORIGINAL",
        "arabic_original",
        ARABIC_ORIGINAL,
        "\u0627\u0644\u0639\u0631\u0628\u064a\u0629 123 (\u0627\u0644\u0623\u0635\u0644\u064a\u0629)",
        R.xml.kbd_arabic_original,
        true),
    new Entry(
        "ARABIC_PC",
        "arabic_pc",
        ARABIC_PC,
        "\u0627\u0644\u0639\u0631\u0628\u064a\u0629 123 (\u0643\u0645\u0628\u064a\u0648\u062a\u0631 \u0634\u062e\u0635\u064a)",
        R.xml.kbd_arabic_pc,
        true),
    new Entry(
        "ARABIC_DIGITS",
        "arabic_digits",
        ARABIC_DIGITS,
        "\u0627\u0644\u0639\u0631\u0628\u064a\u0629 \u0661\u0662\u0663",
        R.xml.kbd_arabic,
        true),
    new Entry(
        "ARABIC_102",
        "arabic_102",
        ARABIC_102,
        "\u0627\u0644\u0639\u0631\u0628\u064a\u0629 PC (\u0646\u0633\u062e\u0629 \u0637\u0628\u0642 \u0627\u0644\u0623\u0635\u0644)",
        R.xml.kbd_arabic_102,
        true),
    new Entry(
        "ARABIC_ORIGINAL_DIGITS",
        "arabic_mac",
        ARABIC_ORIGINAL_DIGITS,
        "\u0627\u0644\u0639\u0631\u0628\u064a\u0629 \u0661\u0662\u0663 (\u0627\u0644\u0623\u0635\u0644\u064a\u0629)",
        R.xml.kbd_arabic_mac,
        true),
  };

  private KeyboardLayouts() {}

  public static Entry[] entries() {
    return ENTRIES.clone();
  }

  public static Entry byName(String name) {
    for (Entry entry : ENTRIES) {
      if (entry.name.equals(name)) {
        return entry;
      }
    }
    return null;
  }

  public static Entry byId(int id) {
    for (Entry entry : ENTRIES) {
      if (entry.id == id) {
        return entry;
      }
    }
    return null;
  }

  public static int idForToken(String token) {
    for (Entry entry : ENTRIES) {
      if (entry.token.equals(token)) {
        return entry.id;
      }
    }
    return -1;
  }

  public static String tokenForId(int id) {
    Entry entry = byId(id);
    return entry == null ? "" : entry.token;
  }

  public static String title(String name) {
    Entry entry = byName(name);
    return entry == null ? name : entry.title;
  }

  public static int xmlFor(String name, boolean rtl) {
    Entry entry = byName(name);
    if (entry != null && entry.arabic == rtl) {
      return entry.xmlResource;
    }
    return rtl ? R.xml.kbd_arabic_102 : R.xml.kbd_qwerty;
  }

  public static String[] names(boolean arabic) {
    int count = 0;
    for (Entry entry : ENTRIES) {
      if (entry.arabic == arabic) {
        count++;
      }
    }
    String[] values = new String[count];
    int index = 0;
    for (Entry entry : ENTRIES) {
      if (entry.arabic == arabic) {
        values[index++] = entry.name;
      }
    }
    return values;
  }

  /** Next layout name inside the language group; unknown names restart at the first. */
  public static String cycledName(String stored, boolean arabic) {
    String[] values = names(arabic);
    for (int i = 0; i < values.length; i++) {
      if (values[i].equals(stored)) {
        return values[(i + 1) % values.length];
      }
    }
    return values[0];
  }

  public static Entry[] forLanguage(boolean arabic) {
    Entry[] values = new Entry[names(arabic).length];
    int index = 0;
    for (Entry entry : ENTRIES) {
      if (entry.arabic == arabic) {
        values[index++] = entry;
      }
    }
    return values;
  }
}
