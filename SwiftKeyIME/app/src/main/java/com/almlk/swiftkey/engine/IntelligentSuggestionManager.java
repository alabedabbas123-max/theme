package com.almlk.swiftkey.engine;

import android.content.Context;
import com.almlk.swiftkey.data.DictionaryDb;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exact phrase context, keyboard-aware spell checking and curated emoji mapping. */
public final class IntelligentSuggestionManager {
  private static IntelligentSuggestionManager instance;

  /**
   * Longest history window considered for next-word prediction: the trailing four words (4-gram
   * context), backing off to 3, 2 and finally 1 word when nothing longer matches.
   */
  private static final int MAX_CONTEXT_WORDS = 4;

  private static final class PhraseNode {
    final HashMap<String, PhraseNode> children = new HashMap<String, PhraseNode>();
    final ArrayList<String> suggestions = new ArrayList<String>();
  }

  private static final class RankedContextWord {
    final String word;
    final int score;

    RankedContextWord(String value, int valueScore) {
      word = value;
      score = valueScore;
    }
  }

  private static final class Correction {
    final String word;
    final int cost;
    final int gap;
    final boolean explicit;

    Correction(String value, int valueCost, int valueGap, boolean fromExplicit) {
      word = value;
      cost = valueCost;
      gap = valueGap;
      explicit = fromExplicit;
    }
  }

  private final DictionaryDb database;
  private final HashMap<String, PhraseNode> phraseRoots = new HashMap<String, PhraseNode>();
  private final HashMap<String, ArrayList<String>> contextIndex =
      new HashMap<String, ArrayList<String>>();
  private final HashMap<String, Integer> contextFrequency = new HashMap<String, Integer>();
  private final HashMap<String, String> explicitCorrections = new HashMap<String, String>();
  private final HashMap<String, ArrayList<String>> exactEmoji =
      new HashMap<String, ArrayList<String>>();
  private String lastCorrectionKey = "";
  private Correction lastCorrection;

  public static synchronized IntelligentSuggestionManager getInstance(Context context) {
    if (instance == null)
      instance = new IntelligentSuggestionManager(context.getApplicationContext());
    return instance;
  }

  private IntelligentSuggestionManager(Context context) {
    database = DictionaryDb.getInstance(context);
    phraseRoots.put("ar", new PhraseNode());
    phraseRoots.put("en", new PhraseNode());
    loadContexts(context);
    loadCorrections(context);
    loadEmoji(context);
  }

  public List<String> getNextWordSuggestions(List<String> previousWords) {
    String language = "ar";
    if (previousWords != null && previousWords.size() > 0) {
      language = TextNormalizer.languageOf(previousWords.get(previousWords.size() - 1));
    }
    return getNextWordSuggestions(previousWords, language);
  }

  public List<String> getNextWordSuggestions(List<String> previousWords, String language) {
    if (previousWords == null || previousWords.size() == 0) return Collections.emptyList();
    // Longest context match first: 4 words, then 3, 2 and finally 1.
    // A shorter context is only considered when no longer context exists.
    // That is how المولى breaks the tie between الوكيل and النصير after ونعم.
    int size = matchedContextLength(previousWords, language);
    if (size <= 0) return Collections.emptyList();
    String key = contextKey(previousWords, previousWords.size() - size, size, language);
    ArrayList<String> candidates = contextIndex.get(key);
    if (candidates == null || candidates.size() == 0) return Collections.emptyList();
    ArrayList<RankedContextWord> ranked = new ArrayList<RankedContextWord>();
    for (int index = 0; index < candidates.size(); index++) {
      String word = candidates.get(index);
      Integer frequency = contextFrequency.get(language + "\u0001" + word);
      int frequencyScore = Math.min(25000, (frequency == null ? 1 : frequency.intValue()) * 250);
      int orderScore = Math.max(0, 5000 - index * 250);
      // Cheap deterministic formula: 4-gram 400k, trigram 300k, bigram 200k, unigram 100k.
      // The 100k tier gap dwarfs frequency (25k) plus order (5k), so a longer match can
      // never lose to a frequent shorter one.
      ranked.add(new RankedContextWord(word, size * 100000 + frequencyScore + orderScore));
    }
    Collections.sort(
        ranked,
        new java.util.Comparator<RankedContextWord>() {
          public int compare(RankedContextWord left, RankedContextWord right) {
            if (left.score != right.score) return right.score - left.score;
            return left.word.compareTo(right.word);
          }
        });
    LinkedHashSet<String> unique = new LinkedHashSet<String>();
    for (RankedContextWord candidate : ranked) unique.add(candidate.word);
    return new ArrayList<String>(unique);
  }

  /**
   * Length (4 down to 0) of the longest indexed context matching the trailing history. At most four
   * hash lookups, so the engine can rank a short asset fallback below longer evidence from any
   * other source without re-scanning the phrase table.
   */
  public int matchedContextLength(List<String> previousWords, String language) {
    if (previousWords == null || previousWords.size() == 0) return 0;
    int maximum = Math.min(MAX_CONTEXT_WORDS, previousWords.size());
    for (int size = maximum; size >= 1; size--) {
      String key = contextKey(previousWords, previousWords.size() - size, size, language);
      ArrayList<String> candidates = contextIndex.get(key);
      if (candidates != null && candidates.size() > 0) return size;
    }
    return 0;
  }

  public String autocorrectAndSpellcheck(String inputWord) {
    return autocorrectAndSpellcheck(inputWord, TextNormalizer.languageOf(inputWord));
  }

  public String explicitCorrection(String inputWord, String language) {
    String folded = TextNormalizer.foldForComparison(inputWord, language);
    String value = explicitCorrections.get(language + "\u0001" + folded);
    return value == null ? inputWord : value;
  }

  public String autocorrectAndSpellcheck(String inputWord, String language) {
    return correction(inputWord, language).word;
  }

  public synchronized String cachedAutocorrection(String inputWord, String language) {
    String key = language + "\u0001" + TextNormalizer.lookup(inputWord);
    return key.equals(lastCorrectionKey) && lastCorrection != null
        ? lastCorrection.word
        : inputWord;
  }

  public boolean isConfidentAutocorrection(
      String inputWord, String corrected, List<String> previousWords, String language) {
    if (inputWord == null || corrected == null || inputWord.equals(corrected)) return false;
    List<String> contextual = getNextWordSuggestions(previousWords, language);
    String inputFold = TextNormalizer.foldForComparison(inputWord, language);
    for (String candidate : contextual) {
      if (TextNormalizer.foldForComparison(candidate, language).equals(inputFold)) return false;
    }
    Correction result = correction(inputWord, language);
    if (!result.word.equals(corrected)) return false;
    if (result.explicit) return true;
    if (TextNormalizer.foldForComparison(inputWord, language)
        .equals(TextNormalizer.foldForComparison(corrected, language))) return true;
    int maximum = inputFold.length() <= 4 ? 8 : Math.min(20, 8 + inputFold.length() * 2);
    return result.cost <= maximum && (result.cost <= 4 || result.gap >= 4);
  }

  public List<String> getEmojiSuggestions(String inputWord) {
    String key = TextNormalizer.foldForComparison(inputWord, "ar");
    ArrayList<String> values = exactEmoji.get(key);
    return values == null ? Collections.<String>emptyList() : new ArrayList<String>(values);
  }

  private synchronized Correction correction(String inputWord, String language) {
    String key = language + "\u0001" + TextNormalizer.lookup(inputWord);
    if (key.equals(lastCorrectionKey) && lastCorrection != null) return lastCorrection;
    Correction result = computeCorrection(inputWord, language);
    lastCorrectionKey = key;
    lastCorrection = result;
    return result;
  }

  private Correction computeCorrection(String inputWord, String language) {
    String clean = TextNormalizer.clean(inputWord);
    String folded = TextNormalizer.foldForComparison(clean, language);
    if (folded.length() < 2) return new Correction(clean, 0, 0, false);
    String explicit = explicitCorrections.get(language + "\u0001" + folded);
    if (explicit != null) return new Correction(explicit, 0, 100, true);

    String exact = database.exactWord(TextNormalizer.lookup(clean), language);
    if (exact.length() > 0) return new Correction(clean, 0, 100, false);
    String canonical = database.canonicalWord(folded, language);
    if (canonical.length() > 0) return new Correction(canonical, 0, 100, false);
    if (folded.length() < 3) return new Correction(clean, 1000, 0, false);

    List<String> pool =
        database.fuzzyPool(
            language, Math.max(2, folded.length() - 2), Math.min(40, folded.length() + 2), 260);
    String bestWord = clean;
    int bestCost = Integer.MAX_VALUE;
    int secondCost = Integer.MAX_VALUE;
    for (String candidate : pool) {
      String candidateFold = TextNormalizer.foldForComparison(candidate, language);
      int cost = weightedDamerau(folded, candidateFold, language);
      if (cost < bestCost) {
        secondCost = bestCost;
        bestCost = cost;
        bestWord = candidate;
      } else if (cost < secondCost) {
        secondCost = cost;
      }
    }
    int gap = secondCost == Integer.MAX_VALUE ? 100 : secondCost - bestCost;
    int maximum = folded.length() <= 4 ? 8 : Math.min(20, 8 + folded.length() * 2);
    if (bestCost > maximum || (bestCost > 4 && gap < 4)) {
      return new Correction(clean, bestCost, gap, false);
    }
    return new Correction(bestWord, bestCost, gap, false);
  }

  private String contextKey(List<String> words, int start, int count, String language) {
    StringBuilder key = new StringBuilder(language);
    for (int index = start; index < start + count; index++) {
      key.append('\u0001').append(contextToken(words.get(index), language));
    }
    return key.toString();
  }

  private String contextToken(String value, String language) {
    String token = TextNormalizer.foldForComparison(value, language);
    if (!"ar".equals(language)) return token;
    // Match Arabic with or without the definite article while preserving the displayed word.
    if (token.startsWith("وال") && token.length() > 4) return token.substring(3);
    if (token.startsWith("ال") && token.length() > 3) return token.substring(2);
    return token;
  }

  private void loadContexts(Context context) {
    try {
      JSONObject root = new JSONObject(readAsset(context, "suggestions/context_phrases.json"));
      JSONArray contexts = root.getJSONArray("contexts");
      for (int index = 0; index < contexts.length(); index++) {
        JSONObject item = contexts.getJSONObject(index);
        String language = item.getString("language");
        PhraseNode node = phraseRoots.get(language);
        if (node == null) continue;
        JSONArray previous = item.getJSONArray("previous");
        ArrayList<String> contextWords = new ArrayList<String>();
        for (int word = 0; word < previous.length(); word++) {
          contextWords.add(previous.getString(word));
        }
        for (int word = previous.length() - 1; word >= 0; word--) {
          String token = TextNormalizer.foldForComparison(previous.getString(word), language);
          PhraseNode child = node.children.get(token);
          if (child == null) {
            child = new PhraseNode();
            node.children.put(token, child);
          }
          node = child;
        }
        JSONArray suggestions = item.getJSONArray("suggestions");
        for (int value = 0; value < suggestions.length(); value++) {
          String suggestion = suggestions.getString(value);
          if (!node.suggestions.contains(suggestion)) node.suggestions.add(suggestion);
          String frequencyKey = language + "\u0001" + suggestion;
          Integer frequency = contextFrequency.get(frequencyKey);
          contextFrequency.put(
              frequencyKey, Integer.valueOf(frequency == null ? 1 : frequency.intValue() + 1));
        }
        int maximum = Math.min(MAX_CONTEXT_WORDS, contextWords.size());
        for (int size = 1; size <= maximum; size++) {
          String key = contextKey(contextWords, contextWords.size() - size, size, language);
          ArrayList<String> indexed = contextIndex.get(key);
          if (indexed == null) {
            indexed = new ArrayList<String>();
            contextIndex.put(key, indexed);
          }
          for (int value = 0; value < suggestions.length(); value++) {
            String suggestion = suggestions.getString(value);
            if (!indexed.contains(suggestion)) indexed.add(suggestion);
          }
        }
      }
    } catch (Exception ignored) {
    }
  }

  private void loadCorrections(Context context) {
    try {
      JSONObject root =
          new JSONObject(readAsset(context, "suggestions/autocorrect_dictionary.json"));
      String[] languages = {"ar", "en"};
      for (String language : languages) {
        JSONObject values = root.getJSONObject(language);
        java.util.Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
          String key = keys.next();
          explicitCorrections.put(
              language + "\u0001" + TextNormalizer.foldForComparison(key, language),
              values.getString(key));
        }
      }
    } catch (Exception ignored) {
    }
  }

  private void loadEmoji(Context context) {
    try {
      JSONObject values = new JSONObject(readAsset(context, "emoji/emoji_exact_ar.json"));
      java.util.Iterator<String> keys = values.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        JSONArray source = values.getJSONArray(key);
        String foldedKey = TextNormalizer.foldForComparison(key, "ar");
        LinkedHashSet<String> unique = new LinkedHashSet<String>();
        ArrayList<String> existing = exactEmoji.get(foldedKey);
        if (existing != null) unique.addAll(existing);
        for (int index = 0; index < source.length(); index++) unique.add(source.getString(index));
        exactEmoji.put(foldedKey, new ArrayList<String>(unique));
      }
    } catch (Exception ignored) {
    }
  }

  private static String readAsset(Context context, String path) throws Exception {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(context.getAssets().open(path), "UTF-8"));
    StringBuilder result = new StringBuilder();
    char[] buffer = new char[2048];
    int count;
    while ((count = reader.read(buffer)) >= 0) result.append(buffer, 0, count);
    reader.close();
    return result.toString();
  }

  private static int weightedDamerau(String source, String target, String language) {
    int[][] values = new int[source.length() + 1][target.length() + 1];
    for (int row = 0; row <= source.length(); row++) values[row][0] = row * 10;
    for (int column = 0; column <= target.length(); column++) values[0][column] = column * 10;
    for (int row = 1; row <= source.length(); row++) {
      for (int column = 1; column <= target.length(); column++) {
        char left = source.charAt(row - 1);
        char right = target.charAt(column - 1);
        int substitution = left == right ? 0 : adjacent(left, right, language) ? 4 : 10;
        int best = Math.min(values[row - 1][column] + 10, values[row][column - 1] + 10);
        best = Math.min(best, values[row - 1][column - 1] + substitution);
        if (row > 1
            && column > 1
            && source.charAt(row - 1) == target.charAt(column - 2)
            && source.charAt(row - 2) == target.charAt(column - 1)) {
          best = Math.min(best, values[row - 2][column - 2] + 6);
        }
        values[row][column] = best;
      }
    }
    return values[source.length()][target.length()];
  }

  private static boolean adjacent(char left, char right, String language) {
    String rows =
        "ar".equals(language)
            ? "ضصثقفغعهخحجد|شسيبلاتنمكط|ئءؤرلاىةوزظ"
            : "qwertyuiop|asdfghjkl|zxcvbnm";
    String[] keyboardRows = rows.split("\\|");
    for (int row = 0; row < keyboardRows.length; row++) {
      int leftIndex = keyboardRows[row].indexOf(left);
      if (leftIndex < 0) continue;
      int rightIndex = keyboardRows[row].indexOf(right);
      if (rightIndex >= 0 && Math.abs(leftIndex - rightIndex) <= 1) return true;
      if (row > 0
          && nearInOtherRow(leftIndex, keyboardRows[row].length(), right, keyboardRows[row - 1])) {
        return true;
      }
      if (row + 1 < keyboardRows.length
          && nearInOtherRow(leftIndex, keyboardRows[row].length(), right, keyboardRows[row + 1])) {
        return true;
      }
    }
    return false;
  }

  private static boolean nearInOtherRow(int index, int sourceLength, char target, String otherRow) {
    int projected = Math.round(index * (otherRow.length() - 1f) / Math.max(1, sourceLength - 1));
    int targetIndex = otherRow.indexOf(target);
    return targetIndex >= 0 && Math.abs(projected - targetIndex) <= 1;
  }
}
