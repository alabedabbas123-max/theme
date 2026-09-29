package com.almlk.swiftkey.engine;

import android.content.Context;
import android.content.SharedPreferences;
import com.almlk.swiftkey.data.DictionaryDb;
import com.almlk.swiftkey.data.ShortcutRepository;
import com.almlk.swiftkey.diagnostics.ErrorTracker;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local completion, correction, context ranking and adaptive learning engine. */
public final class SuggestionEngine {
  /**
   * Score unit of one matched context word. Every source bonus and rank bonus combined stays far
   * below it, so a longer context always outranks a shorter one no matter how frequent the shorter
   * match is.
   */
  private static final int CONTEXT_TIER_UNIT = 10000;
  /** Learned SQLite n-grams outrank curated matches of the same context length. */
  private static final int SOURCE_LEARNED_BONUS = 1200;
  /** Curated asset and fixed-phrase matches of the same context length come next. */
  private static final int SOURCE_CURATED_BONUS = 800;
  /** Rank step inside one tier; eight rows stay inside the tier band. */
  private static final int CONTEXT_RANK_STEP = 120;

  private static volatile boolean largeDictionaryLoading;
  private static volatile boolean essentialDictionaryLoading;
  private final DictionaryDb database;
  private final Context context;
  private final IntelligentSuggestionManager intelligence;
  private final ShortcutRepository shortcuts;

  private static final class RankedWord {
    String word;
    int score;

    RankedWord(String word, int score) {
      this.word = word;
      this.score = score;
    }
  }

  public SuggestionEngine(Context value) {
    context = value.getApplicationContext();
    database = DictionaryDb.getInstance(context);
    intelligence = IntelligentSuggestionManager.getInstance(context);
    shortcuts = new ShortcutRepository(context);
    seedDictionariesAsync();
  }

  private void seedDictionariesAsync() {
    final SharedPreferences preferences =
        context.getSharedPreferences("dictionary", Context.MODE_PRIVATE);
    if (preferences.getBoolean("essential_v8", false)) {
      seedLargeDictionariesAsync();
      return;
    }
    synchronized (SuggestionEngine.class) {
      if (essentialDictionaryLoading) return;
      essentialDictionaryLoading = true;
    }
    Thread loader =
        new Thread(
            new Runnable() {
              public void run() {
                try {
                  seedEssential();
                  seedLargeDictionariesAsync();
                } finally {
                  essentialDictionaryLoading = false;
                }
              }
            },
            "Almlk-Essential-Dictionary");
    loader.setPriority(Thread.MIN_PRIORITY);
    loader.start();
  }

  public List<String> suggest(String current, String previous, String language) {
    return suggest(current, previous, "", language);
  }

  public List<String> suggest(String current, List<String> previousWords, String language) {
    return suggest(current, previousWords, Collections.<String>emptyList(), language);
  }

  public List<String> suggest(
      String current, List<String> previousWords, List<String> followingWords, String language) {
    String previous =
        previousWords == null || previousWords.size() == 0
            ? ""
            : previousWords.get(previousWords.size() - 1);
    List<String> base = suggestWithHistory(current, previousWords, language);
    LinkedHashSet<String> merged = new LinkedHashSet<String>();
    boolean emptyCurrent = TextNormalizer.clean(current).length() == 0;
    if (!emptyCurrent
        && com.almlk.swiftkey.util.FeatureSettings.enabled(
            context, com.almlk.swiftkey.util.FeatureSettings.SHORTCUT_IN_STRIP)) {
      merged.addAll(shortcuts.suggestions(current, language, 5));
    }
    if (followingWords != null && followingWords.size() > 0) {
      String following = TextNormalizer.foldForComparison(followingWords.get(0), language);
      String typed = TextNormalizer.foldForComparison(current, language);
      List<String> between =
          database.between(
              TextNormalizer.foldForComparison(previous, language), following, language, 10);
      for (String candidate : between) {
        String folded = TextNormalizer.foldForComparison(candidate, language);
        if (typed.length() == 0 || folded.startsWith(typed)) merged.add(candidate);
      }
    }
    if (com.almlk.swiftkey.util.FeatureSettings.enabled(
        context, com.almlk.swiftkey.util.FeatureSettings.DICTIONARY_IN_SUGGESTIONS)) {
      merged.addAll(base);
    }
    ArrayList<String> output = new ArrayList<String>();
    for (String candidate : merged) {
      String safe = TextNormalizer.inlineText(candidate);
      if (safe.length() == 0 || output.contains(safe)) continue;
      output.add(safe);
      if (output.size() >= 5) break;
    }
    return output;
  }

  public List<String> suggest(String current, String previous, String previous2, String language) {
    ArrayList<String> history = new ArrayList<String>();
    if (previous2 != null && previous2.length() > 0) history.add(previous2);
    if (previous != null && previous.length() > 0) history.add(previous);
    return suggestWithHistory(current, history, language);
  }

  /**
   * Ranked core over the full history window. Next-word prediction uses up to four history words;
   * completion of the word being typed ignores history by design.
   */
  private List<String> suggestWithHistory(
      String current, List<String> previousWords, String language) {
    String typed = TextNormalizer.lookup(current);
    HashMap<String, RankedWord> ranked = new HashMap<String, RankedWord>();
    if (typed.length() == 0) {
      rankNextWord(ranked, previousWords, language);
    } else {
      rankCompletions(ranked, current, typed, language);
    }
    return topRanked(ranked, typed, language);
  }

  /**
   * Next-word ranking over up to four history words. Every source (curated asset index, learned
   * SQLite n-grams, fixed phrases) competes in one map ordered by matched context length first, so
   * a short fallback can never shadow longer evidence: after "نعم المولى ونعم" the 3-word match for
   * النصير outranks the bare "ونعم" fallback for الوكيل even though الوكيل may be far more
   * frequent.
   */
  private void rankNextWord(
      Map<String, RankedWord> target, List<String> previousWords, String language) {
    ArrayList<String> trailing = new ArrayList<String>();
    if (previousWords != null) {
      int start = Math.max(0, previousWords.size() - 4);
      for (int index = start; index < previousWords.size(); index++) {
        String token = TextNormalizer.foldForComparison(previousWords.get(index), language);
        if (token.length() > 0) {
          trailing.add(token);
        }
      }
    }
    int matched = intelligence.matchedContextLength(trailing, language);
    if (matched > 0) {
      addTiered(
          target,
          intelligence.getNextWordSuggestions(trailing, language),
          matched,
          SOURCE_CURATED_BONUS);
    }
    String previous = trailing.size() >= 1 ? trailing.get(trailing.size() - 1) : "";
    String previous2 = trailing.size() >= 2 ? trailing.get(trailing.size() - 2) : "";
    addTiered(
        target, database.nextTrigram(previous2, previous, language, 8), 2, SOURCE_LEARNED_BONUS);
    addTiered(target, database.nextBigram(previous, language, 8), 1, SOURCE_LEARNED_BONUS);
    addContextDefaultsTiered(target, previous2, previous, language);
    addRanked(target, database.candidates(language, 20), 7000, 120);
    addDefaults(target, language);
  }

  /**
   * Length-dominant context score: tier = matched context words x 10000, then a small source bonus
   * plus a rank bonus. The non-length part peaks at 1200 + 8 x 120 = 2160, far below the 10000 tier
   * gap, so frequency and order can only break ties inside one tier and can never promote a shorter
   * context above a longer one.
   */
  private static void addTiered(
      Map<String, RankedWord> target, List<String> words, int contextLength, int sourceBonus) {
    if (contextLength <= 0 || words == null) {
      return;
    }
    for (int index = 0; index < words.size() && index < 8; index++) {
      int rankBonus = (8 - index) * CONTEXT_RANK_STEP;
      putScore(
          target, words.get(index), contextLength * CONTEXT_TIER_UNIT + sourceBonus + rankBonus);
    }
  }

  private void rankCompletions(
      Map<String, RankedWord> target, String current, String typed, String language) {
    String comparison = TextNormalizer.foldForComparison(current, language);
    String corrected = intelligence.explicitCorrection(current, language);
    if (corrected.length() > 0 && !corrected.equals(current)) {
      putScore(target, corrected, 25000);
    }
    List<String> prefix = database.prefix(comparison, language, 12);
    for (int index = 0; index < prefix.size(); index++) {
      String word = prefix.get(index);
      int lengthPenalty = Math.max(0, word.length() - typed.length()) * 8;
      putScore(target, word, 18000 - index * 250 - lengthPenalty);
    }

    if (typed.length() >= 3 && target.size() < 3) {
      String foldedTyped = TextNormalizer.foldForComparison(typed, language);
      int threshold = typed.length() <= 4 ? 10 : 20;
      List<String> pool =
          database.fuzzyPool(language, Math.max(2, typed.length() - 2), typed.length() + 2, 180);
      for (String candidate : pool) {
        if (target.containsKey(TextNormalizer.lookup(candidate))) {
          continue;
        }
        String foldedCandidate = TextNormalizer.foldForComparison(candidate, language);
        int cost = weightedDamerau(foldedTyped, foldedCandidate, language);
        if (cost <= threshold) {
          int commonPrefix = commonPrefix(foldedTyped, foldedCandidate);
          int score = 13000 - cost * 280 + commonPrefix * 90;
          putScore(target, candidate, score);
        }
      }
    }

    if (target.size() < 3) {
      addRanked(target, database.candidates(language, 15), 4000, 80);
    }
    if (target.size() < 3) {
      addDefaults(target, language);
    }
  }

  private List<String> topRanked(Map<String, RankedWord> target, String typed, String language) {
    ArrayList<RankedWord> values = new ArrayList<RankedWord>(target.values());
    Collections.sort(
        values,
        new Comparator<RankedWord>() {
          @Override
          public int compare(RankedWord left, RankedWord right) {
            if (left.score != right.score) {
              return right.score - left.score;
            }
            return left.word.compareTo(right.word);
          }
        });

    ArrayList<String> output = new ArrayList<String>();
    for (RankedWord value : values) {
      if (output.size() >= 5) {
        break;
      }
      if (!TextNormalizer.lookup(value.word).equals(typed)
          && (!blocklistActive() || !database.isBlocked(value.word, language))) {
        output.add(value.word);
      }
    }
    return output;
  }

  public List<String> suggestGesturePreview(
      GestureTrace gesture, String previous, String previous2, String language) {
    return suggestGestureInternal(gesture, previous, previous2, language, 900);
  }

  public List<String> suggestGesture(
      GestureTrace gesture, String previous, String previous2, String language) {
    return suggestGestureInternal(gesture, previous, previous2, language, 3000);
  }

  private List<String> suggestGestureInternal(
      GestureTrace gesture, String previous, String previous2, String language, int poolLimit) {
    if (gesture == null || gesture.size() < 2) return Collections.emptyList();
    String trace = gestureSkeleton(gesture.crossedKeys(), language);
    if (trace.length() < 2) return Collections.emptyList();

    String first = String.valueOf(trace.charAt(0));
    String last = String.valueOf(trace.charAt(trace.length() - 1));
    LinkedHashSet<String> poolSet = new LinkedHashSet<String>();
    poolSet.addAll(database.gesturePool(language, first, last, poolLimit));
    poolSet.addAll(
        database.gestureFirstPool(
            language, first, Math.min(poolLimit, poolLimit <= 1000 ? 360 : 900)));
    if (poolSet.size() < 30) {
      poolSet.addAll(database.fuzzyPool(language, 2, 40, poolLimit));
    }
    List<String> pool = new ArrayList<String>(poolSet);
    List<String> contextual =
        database.next(
            TextNormalizer.foldForComparison(previous2, language),
            TextNormalizer.foldForComparison(previous, language),
            language,
            12);
    HashMap<String, Integer> contextRanks = new HashMap<String, Integer>();
    for (int index = 0; index < contextual.size(); index++) {
      contextRanks.put(TextNormalizer.lookup(contextual.get(index)), Integer.valueOf(index));
    }

    ArrayList<RankedWord> ranked = new ArrayList<RankedWord>();
    for (int index = 0; index < pool.size(); index++) {
      String candidate = pool.get(index);
      String skeleton = gestureSkeleton(candidate, language);
      if (skeleton.length() < 2) continue;
      int coverage = orderedCoveragePermille(trace, skeleton);
      if (coverage < 650 || Math.abs(trace.length() - skeleton.length()) > 3) continue;
      int shapeCost =
          gestureShapeCost(gesture, gestureGeometrySkeleton(candidate, language), language);
      if (shapeCost == Integer.MAX_VALUE || shapeCost > 480) continue;
      int sequenceCost = gestureDistance(trace, skeleton, language);
      int score =
          36000 + coverage * 28 - shapeCost * 135 - Math.min(120, sequenceCost) * 18 - index * 2;
      Integer contextRank = contextRanks.get(TextNormalizer.lookup(candidate));
      if (contextRank != null) score += 5200 - contextRank.intValue() * 220;
      ranked.add(new RankedWord(candidate, score));
    }

    Collections.sort(
        ranked,
        new Comparator<RankedWord>() {
          public int compare(RankedWord left, RankedWord right) {
            if (left.score != right.score) return right.score - left.score;
            return left.word.compareTo(right.word);
          }
        });
    ArrayList<String> output = new ArrayList<String>();
    for (RankedWord value : ranked) {
      if (output.size() >= 5) break;
      String safe = TextNormalizer.inlineText(value.word);
      if ((blocklistActive() || !database.isBlocked(safe, language)) && !output.contains(safe)) {
        output.add(safe);
      }
    }
    return output;
  }

  public boolean isConfidentGestureCandidate(
      GestureTrace gesture, String candidate, String language) {
    if (gesture == null || candidate == null || candidate.length() < 2) return false;
    String trace = gestureSkeleton(gesture.crossedKeys(), language);
    String skeleton = gestureSkeleton(candidate, language);
    if (trace.length() < 2 || skeleton.length() < 2) return false;
    if (orderedCoveragePermille(trace, skeleton) < 780) return false;
    if (gestureDistance(trace, skeleton, language) > 34) return false;
    int shapeCost =
        gestureShapeCost(gesture, gestureGeometrySkeleton(candidate, language), language);
    if (shapeCost == Integer.MAX_VALUE || shapeCost > 230) return false;
    return database.exactWord(TextNormalizer.lookup(candidate), language).length() > 0;
  }

  public List<String> getNextWordSuggestions(List<String> previousWords, String language) {
    return intelligence.getNextWordSuggestions(previousWords, language);
  }

  public String autocorrectAndSpellcheck(String inputWord, String language) {
    return intelligence.autocorrectAndSpellcheck(inputWord, language);
  }

  public String cachedAutocorrection(String inputWord, String language) {
    return TextNormalizer.inlineText(intelligence.cachedAutocorrection(inputWord, language));
  }

  public boolean isConfidentAutocorrection(
      String inputWord, String corrected, List<String> previousWords, String language) {
    return intelligence.isConfidentAutocorrection(inputWord, corrected, previousWords, language);
  }

  public List<String> getEmojiSuggestions(String inputWord) {
    return intelligence.getEmojiSuggestions(inputWord);
  }

  public String expandShortcut(String shortcut, String language) {
    if (!com.almlk.swiftkey.util.FeatureSettings.enabled(
        context, com.almlk.swiftkey.util.FeatureSettings.SHORTCUT_ENABLED)) {
      return "";
    }
    return TextNormalizer.inlineText(shortcuts.expansion(shortcut, language));
  }

  /** The block-list only applies while the dictionaries page enables it. */
  private boolean blocklistActive() {
    return com.almlk.swiftkey.util.FeatureSettings.enabled(
        context, com.almlk.swiftkey.util.FeatureSettings.DICTIONARY_BLOCKLIST);
  }

  public void learn(String previous, String word, String language) {
    learn("", previous, word, language);
  }

  public void learn(String previous2, String previous, String word, String language) {
    String cleanWord = TextNormalizer.clean(TextNormalizer.inlineText(word)).toLowerCase(Locale.ROOT);
    if (cleanWord.length() <= 1 || cleanWord.length() > 40) {
      return;
    }
    database.learnSequence(
        TextNormalizer.foldForComparison(previous2, language),
        TextNormalizer.foldForComparison(previous, language),
        cleanWord,
        TextNormalizer.foldForComparison(cleanWord, language),
        language);
  }

  public int importStream(InputStream input, String forcedLanguage) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
    int count = 0;
    boolean successful = false;
    database.beginBulk();
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.length() == 0 || line.startsWith("#")) {
          continue;
        }
        String[] parts = line.split("\\s+");
        String word = TextNormalizer.clean(parts[0]);
        if (word.length() < 2 || word.length() > 40) {
          continue;
        }
        int frequency = 10;
        if (parts.length > 1) {
          try {
            frequency = Integer.parseInt(parts[parts.length - 1]);
          } catch (Exception ignored) {
            frequency = 10;
          }
        }
        String language = forcedLanguage == null ? TextNormalizer.languageOf(word) : forcedLanguage;
        String display = word.toLowerCase(Locale.ROOT);
        database.putWord(
            display,
            TextNormalizer.foldForComparison(display, language),
            language,
            frequency,
            false);
        count++;
      }
      successful = true;
    } finally {
      database.finishBulk(successful);
      reader.close();
    }
    return count;
  }

  public void deleteSuggestion(String word, String language) {
    database.deleteWord(word, language);
  }

  public void editSuggestion(String oldWord, String newWord, String language) {
    String clean = TextNormalizer.clean(TextNormalizer.inlineText(newWord)).toLowerCase(Locale.ROOT);
    if (clean.length() < 2 || clean.length() > 40) return;
    database.editWord(oldWord, clean, TextNormalizer.foldForComparison(clean, language), language);
  }

  public void clearLearned() {
    database.clearLearned();
    context
        .getSharedPreferences("dictionary", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("essential_v8", false)
        .apply();
    seedEssential();
  }

  private void seedEssential() {
    SharedPreferences preferences =
        context.getSharedPreferences("dictionary", Context.MODE_PRIVATE);
    if (preferences.getBoolean("essential_v8", false)) {
      return;
    }
    try {
      importStream(context.getAssets().open("dictionaries/ar_seed.txt"), "ar");
      importStream(context.getAssets().open("dictionaries/en_seed.txt"), "en");
      importBigramAsset("dictionaries/ar_bigrams.tsv", "ar");
      importBigramAsset("dictionaries/en_bigrams.tsv", "en");
      importTrigramAsset("dictionaries/ar_trigrams.tsv", "ar");
      importTrigramAsset("dictionaries/en_trigrams.tsv", "en");
      preferences.edit().putBoolean("essential_v8", true).apply();
    } catch (Exception error) {
      ErrorTracker.record(context, "Dictionary essential seed", error);
    }
  }

  private void seedLargeDictionariesAsync() {
    final SharedPreferences preferences =
        context.getSharedPreferences("dictionary", Context.MODE_PRIVATE);
    if (preferences.getBoolean("frequency_v8", false) || largeDictionaryLoading) {
      return;
    }
    largeDictionaryLoading = true;
    Thread loader =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  importStream(context.getAssets().open("dictionaries/ar_frequency_15k.txt"), "ar");
                  importStream(context.getAssets().open("dictionaries/en_frequency_15k.txt"), "en");
                  preferences.edit().putBoolean("frequency_v8", true).apply();
                } catch (Exception error) {
                  ErrorTracker.record(context, "Dictionary frequency seed", error);
                } finally {
                  largeDictionaryLoading = false;
                }
              }
            },
            "Almlk-Dictionary-Loader");
    loader.setPriority(Thread.MIN_PRIORITY);
    loader.start();
  }

  private void importBigramAsset(String asset, String language) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(context.getAssets().open(asset), "UTF-8"));
    boolean successful = false;
    database.beginBulk();
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split("\\t");
        if (parts.length < 3) {
          continue;
        }
        int frequency;
        try {
          frequency = Integer.parseInt(parts[2]);
        } catch (Exception ignored) {
          frequency = 1;
        }
        database.importBigram(
            TextNormalizer.foldForComparison(parts[0], language),
            TextNormalizer.clean(parts[1]).toLowerCase(Locale.ROOT),
            language,
            frequency);
      }
      successful = true;
    } finally {
      database.finishBulk(successful);
      reader.close();
    }
  }

  private void importTrigramAsset(String asset, String language) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(context.getAssets().open(asset), "UTF-8"));
    boolean successful = false;
    database.beginBulk();
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split("\\t");
        if (parts.length < 4) {
          continue;
        }
        int frequency;
        try {
          frequency = Integer.parseInt(parts[3]);
        } catch (Exception ignored) {
          frequency = 1;
        }
        database.importTrigram(
            TextNormalizer.foldForComparison(parts[0], language),
            TextNormalizer.foldForComparison(parts[1], language),
            TextNormalizer.clean(parts[2]).toLowerCase(Locale.ROOT),
            language,
            frequency);
      }
      successful = true;
    } finally {
      database.finishBulk(successful);
      reader.close();
    }
  }

  private static void addRanked(
      Map<String, RankedWord> target, List<String> words, int start, int step) {
    for (int index = 0; index < words.size(); index++) {
      putScore(target, words.get(index), start - index * step);
    }
  }

  /**
   * Small language-model fallback used only below learned/imported n-grams of the same length.
   * Inputs are already folded, so literals use the folded spelling. Branches conditioned on two
   * context words compete at tier 2, single-word branches at tier 1.
   */
  private static void addContextDefaultsTiered(
      Map<String, RankedWord> target, String previous2, String previous, String language) {
    if ("ar".equals(language)) {
      if ("بسم".equals(previous2) && "الله".equals(previous)) {
        putTiered(target, new String[] {"الرحمن", "الرحيم"}, 2);
      } else if ("بسم".equals(previous)) {
        putTiered(target, new String[] {"الله"}, 1);
      } else if ("الحمد".equals(previous)) {
        putTiered(target, new String[] {"لله"}, 1);
      } else if ("السلام".equals(previous)) {
        putTiered(target, new String[] {"عليكم"}, 1);
      } else if ("عليكم".equals(previous)) {
        putTiered(target, new String[] {"السلام", "ورحمة"}, 1);
      } else if ("ان".equals(previous)) {
        putTiered(target, new String[] {"شاء", "الله"}, 1);
      } else if ("شاء".equals(previous)) {
        putTiered(target, new String[] {"الله"}, 1);
      } else if ("كيف".equals(previous)) {
        putTiered(target, new String[] {"حالك", "حالكم"}, 1);
      } else if ("كل".equals(previous)) {
        putTiered(target, new String[] {"عام", "شيء"}, 1);
      } else if ("شكرا".equals(previous)) {
        putTiered(target, new String[] {"لك", "جزيلا"}, 1);
      } else if ("صباح".equals(previous)) {
        putTiered(target, new String[] {"الخير", "النور"}, 1);
      } else if ("مساء".equals(previous)) {
        putTiered(target, new String[] {"الخير", "النور"}, 1);
      } else if ("الله".equals(previous)) {
        putTiered(target, new String[] {"أكبر", "تعالى", "يحفظك"}, 1);
      }
    } else {
      if ("thank".equals(previous)) {
        putTiered(target, new String[] {"you"}, 1);
      } else if ("how".equals(previous)) {
        putTiered(target, new String[] {"are", "is"}, 1);
      } else if ("good".equals(previous)) {
        putTiered(target, new String[] {"morning", "night", "luck"}, 1);
      } else if ("i".equals(previous)) {
        putTiered(target, new String[] {"am", "have", "think"}, 1);
      } else if ("see".equals(previous)) {
        putTiered(target, new String[] {"you"}, 1);
      } else if ("nice".equals(previous)) {
        putTiered(target, new String[] {"to", "work"}, 1);
      }
    }
  }

  private static void putTiered(Map<String, RankedWord> target, String[] words, int tier) {
    for (int index = 0; index < words.length; index++) {
      putScore(
          target,
          words[index],
          tier * CONTEXT_TIER_UNIT + SOURCE_CURATED_BONUS + (8 - index) * CONTEXT_RANK_STEP);
    }
  }

  private static void addDefaults(Map<String, RankedWord> target, String language) {
    String[] defaults =
        "ar".equals(language)
            ? new String[] {"من", "في", "على", "هذا", "التي"}
            : new String[] {"the", "and", "to", "you", "that"};
    for (int index = 0; index < defaults.length; index++) {
      putScore(target, defaults[index], 3000 - index * 100);
    }
  }

  private static void putScore(Map<String, RankedWord> target, String word, int score) {
    if (word == null || word.length() == 0) {
      return;
    }
    String key = TextNormalizer.lookup(word);
    RankedWord old = target.get(key);
    if (old == null) {
      target.put(key, new RankedWord(word, score));
    } else if (score > old.score) {
      old.word = word;
      old.score = score;
    }
  }

  private static int commonPrefix(String left, String right) {
    int count = 0;
    int limit = Math.min(left.length(), right.length());
    while (count < limit && left.charAt(count) == right.charAt(count)) {
      count++;
    }
    return count;
  }

  private static int gestureShapeCost(GestureTrace gesture, String word, String language) {
    int actualCount = gesture.size();
    int idealCount = word.length();
    if (actualCount < 2 || idealCount < 2) return Integer.MAX_VALUE;
    float[] actualX = new float[actualCount];
    float[] actualY = new float[actualCount];
    for (int index = 0; index < actualCount; index++) {
      actualX[index] = gesture.x(index);
      actualY[index] = gesture.y(index);
    }
    float[] idealX = new float[idealCount];
    float[] idealY = new float[idealCount];
    for (int index = 0; index < idealCount; index++) {
      float[] center = gesture.center(word.charAt(index), language);
      if (center == null) return Integer.MAX_VALUE;
      idealX[index] = center[0];
      idealY[index] = center[1];
    }
    float[][] actual = resamplePath(actualX, actualY, 32);
    float[][] ideal = resamplePath(idealX, idealY, 32);
    float total = 0;
    for (int index = 0; index < 32; index++) {
      float dx = actual[0][index] - ideal[0][index];
      float dy = actual[1][index] - ideal[1][index];
      total += (float) Math.sqrt(dx * dx + dy * dy);
    }
    float startDx = actualX[0] - idealX[0];
    float startDy = actualY[0] - idealY[0];
    float endDx = actualX[actualCount - 1] - idealX[idealCount - 1];
    float endDy = actualY[actualCount - 1] - idealY[idealCount - 1];
    float endpointCost =
        (float) Math.sqrt(startDx * startDx + startDy * startDy)
            + (float) Math.sqrt(endDx * endDx + endDy * endDy);
    return Math.round(total * 1000f / 32f + endpointCost * 420f);
  }

  private static float[][] resamplePath(float[] x, float[] y, int outputCount) {
    float[] cumulative = new float[x.length];
    for (int index = 1; index < x.length; index++) {
      float dx = x[index] - x[index - 1];
      float dy = y[index] - y[index - 1];
      cumulative[index] = cumulative[index - 1] + (float) Math.sqrt(dx * dx + dy * dy);
    }
    float total = cumulative[cumulative.length - 1];
    float[][] output = new float[][] {new float[outputCount], new float[outputCount]};
    if (total <= .0001f) {
      for (int index = 0; index < outputCount; index++) {
        output[0][index] = x[0];
        output[1][index] = y[0];
      }
      return output;
    }
    int segment = 1;
    for (int sample = 0; sample < outputCount; sample++) {
      float target = total * sample / Math.max(1, outputCount - 1);
      while (segment < cumulative.length - 1 && cumulative[segment] < target) segment++;
      float fromDistance = cumulative[segment - 1];
      float segmentLength = Math.max(.0001f, cumulative[segment] - fromDistance);
      float fraction = (target - fromDistance) / segmentLength;
      output[0][sample] = x[segment - 1] + (x[segment] - x[segment - 1]) * fraction;
      output[1][sample] = y[segment - 1] + (y[segment] - y[segment - 1]) * fraction;
    }
    return output;
  }

  private static String gestureGeometrySkeleton(String value, String language) {
    String clean = TextNormalizer.lookup(value);
    StringBuilder output = new StringBuilder();
    StringBuilder seen = new StringBuilder();
    for (int index = 0; index < clean.length(); index++) {
      char current = clean.charAt(index);
      if (!Character.isLetter(current)) continue;
      String folded = TextNormalizer.foldForComparison(String.valueOf(current), language);
      if (folded.length() == 0 || seen.indexOf(folded) >= 0) continue;
      output.append(current);
      seen.append(folded.charAt(0));
    }
    return output.toString();
  }

  private static String gestureSkeleton(String value, String language) {
    String folded = TextNormalizer.foldForComparison(value, language);
    StringBuilder output = new StringBuilder();
    for (int index = 0; index < folded.length(); index++) {
      char current = folded.charAt(index);
      if (!Character.isLetter(current) || output.indexOf(String.valueOf(current)) >= 0) continue;
      output.append(current);
    }
    return output.toString();
  }

  private static int orderedCoveragePermille(String trace, String word) {
    if (trace.length() == 0 || word.length() == 0) return 0;
    int[] length = new int[word.length() + 1];
    for (int row = 1; row <= trace.length(); row++) {
      int diagonal = 0;
      for (int column = 1; column <= word.length(); column++) {
        int old = length[column];
        if (trace.charAt(row - 1) == word.charAt(column - 1)) {
          length[column] = diagonal + 1;
        } else {
          length[column] = Math.max(length[column], length[column - 1]);
        }
        diagonal = old;
      }
    }
    return length[word.length()] * 1000 / Math.max(trace.length(), word.length());
  }

  private static int gestureDistance(String trace, String word, String language) {
    int[][] cost = new int[trace.length() + 1][word.length() + 1];
    for (int row = 0; row <= trace.length(); row++) cost[row][0] = row * 6;
    for (int column = 0; column <= word.length(); column++) cost[0][column] = column * 8;
    for (int row = 1; row <= trace.length(); row++) {
      for (int column = 1; column <= word.length(); column++) {
        char traced = trace.charAt(row - 1);
        char expected = word.charAt(column - 1);
        int substitution = traced == expected ? 0 : adjacent(traced, expected, language) ? 5 : 12;
        int best = Math.min(cost[row - 1][column] + 6, cost[row][column - 1] + 8);
        best = Math.min(best, cost[row - 1][column - 1] + substitution);
        cost[row][column] = best;
      }
    }
    return cost[trace.length()][word.length()];
  }

  /** Cost is scaled by ten: adjacent substitutions cost 4, transposition 6. */
  private static int weightedDamerau(String source, String target, String language) {
    int rows = source.length() + 1;
    int columns = target.length() + 1;
    int[][] values = new int[rows][columns];
    for (int row = 0; row < rows; row++) {
      values[row][0] = row * 10;
    }
    for (int column = 0; column < columns; column++) {
      values[0][column] = column * 10;
    }
    for (int row = 1; row < rows; row++) {
      char left = source.charAt(row - 1);
      for (int column = 1; column < columns; column++) {
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
    return values[rows - 1][columns - 1];
  }

  private static boolean adjacent(char left, char right, String language) {
    String rows =
        "ar".equals(language)
            ? "ضصثقفغعهخحجد|شسيبلاتنمكذ|ئءؤرىةوزطظ"
            : "qwertyuiop|asdfghjkl|zxcvbnm";
    String[] keyboardRows = rows.split("\\|");
    for (int row = 0; row < keyboardRows.length; row++) {
      int leftIndex = keyboardRows[row].indexOf(left);
      if (leftIndex < 0) {
        continue;
      }
      int rightIndex = keyboardRows[row].indexOf(right);
      if (rightIndex >= 0 && Math.abs(leftIndex - rightIndex) <= 1) {
        return true;
      }
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
