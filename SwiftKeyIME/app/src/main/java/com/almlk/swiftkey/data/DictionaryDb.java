package com.almlk.swiftkey.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** Ranked local unigram, bigram and trigram store. */
public final class DictionaryDb extends SQLiteOpenHelper {
  private static final int VERSION = 6;
  private static final ReentrantLock WRITE_LOCK = new ReentrantLock(true);
  private static DictionaryDb instance;
  private final ThreadLocal<SQLiteDatabase> bulkDatabase = new ThreadLocal<SQLiteDatabase>();

  public static synchronized DictionaryDb getInstance(Context context) {
    if (instance == null) {
      instance = new DictionaryDb(context.getApplicationContext());
    }
    return instance;
  }

  private DictionaryDb(Context context) {
    super(context, "dictionary.db", null, VERSION);
    if (android.os.Build.VERSION.SDK_INT >= 16) {
      setWriteAheadLoggingEnabled(true);
    }
  }

  @Override
  public void onConfigure(SQLiteDatabase database) {
    super.onConfigure(database);
    try {
      database.execSQL("PRAGMA busy_timeout=2500");
    } catch (Exception ignored) {
      // Some vendor SQLite builds do not expose this pragma.
    }
  }

  @Override
  public void onCreate(SQLiteDatabase database) {
    database.execSQL(
        "CREATE TABLE words("
            + "word TEXT NOT NULL,normalized TEXT NOT NULL,lang TEXT NOT NULL,"
            + "freq INTEGER NOT NULL DEFAULT 1,learned INTEGER NOT NULL DEFAULT 0,"
            + "typed INTEGER NOT NULL DEFAULT 0,"
            + "last_used INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(word,lang))");
    database.execSQL("CREATE INDEX idx_words_lang_word ON words(lang,word)");
    database.execSQL("CREATE INDEX idx_words_normalized ON words(lang,normalized)");
    database.execSQL("CREATE INDEX idx_words_rank ON words(lang,learned,typed,freq)");
    database.execSQL(
        "CREATE TABLE bigrams("
            + "previous TEXT NOT NULL,word TEXT NOT NULL,lang TEXT NOT NULL,"
            + "freq INTEGER NOT NULL DEFAULT 1,last_used INTEGER NOT NULL DEFAULT 0,"
            + "PRIMARY KEY(previous,word,lang))");
    database.execSQL("CREATE INDEX idx_bigrams_lookup ON bigrams(lang,previous,freq)");
    database.execSQL(
        "CREATE TABLE trigrams("
            + "previous2 TEXT NOT NULL,previous TEXT NOT NULL,word TEXT NOT NULL,"
            + "lang TEXT NOT NULL,freq INTEGER NOT NULL DEFAULT 1,"
            + "last_used INTEGER NOT NULL DEFAULT 0,"
            + "PRIMARY KEY(previous2,previous,word,lang))");
    database.execSQL(
        "CREATE INDEX idx_trigrams_lookup ON " + "trigrams(lang,previous2,previous,freq)");
    database.execSQL(
        "CREATE TABLE blocked_words(word TEXT NOT NULL,lang TEXT NOT NULL,"
            + "PRIMARY KEY(word,lang))");
  }

  @Override
  public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
    if (oldVersion < 2) {
      safeExec(
          database,
          "CREATE TABLE IF NOT EXISTS bigrams("
              + "previous TEXT NOT NULL,word TEXT NOT NULL,lang TEXT NOT NULL,"
              + "freq INTEGER NOT NULL DEFAULT 1,PRIMARY KEY(previous,word,lang))");
    }
    if (oldVersion < 3) {
      safeExec(database, "ALTER TABLE words ADD COLUMN typed INTEGER NOT NULL DEFAULT 0");
      safeExec(database, "ALTER TABLE words ADD COLUMN last_used INTEGER NOT NULL DEFAULT 0");
      safeExec(database, "ALTER TABLE bigrams ADD COLUMN last_used INTEGER NOT NULL DEFAULT 0");
    }
    if (oldVersion < 4) {
      safeExec(
          database,
          "CREATE TABLE IF NOT EXISTS trigrams("
              + "previous2 TEXT NOT NULL,previous TEXT NOT NULL,word TEXT NOT NULL,"
              + "lang TEXT NOT NULL,freq INTEGER NOT NULL DEFAULT 1,"
              + "last_used INTEGER NOT NULL DEFAULT 0,"
              + "PRIMARY KEY(previous2,previous,word,lang))");
      safeExec(
          database,
          "CREATE INDEX IF NOT EXISTS idx_words_rank " + "ON words(lang,learned,typed,freq)");
      safeExec(
          database,
          "CREATE INDEX IF NOT EXISTS idx_bigrams_lookup " + "ON bigrams(lang,previous,freq)");
      safeExec(
          database,
          "CREATE INDEX IF NOT EXISTS idx_trigrams_lookup "
              + "ON trigrams(lang,previous2,previous,freq)");
    }
    if (oldVersion < 5) {
      safeExec(database, "ALTER TABLE words ADD COLUMN normalized TEXT NOT NULL DEFAULT ''");
      safeExec(database, "UPDATE words SET normalized=lower(word) WHERE normalized=''");
      safeExec(
          database,
          "CREATE INDEX IF NOT EXISTS idx_words_normalized " + "ON words(lang,normalized)");
    }
    if (oldVersion < 6) {
      safeExec(
          database,
          "CREATE TABLE IF NOT EXISTS blocked_words("
              + "word TEXT NOT NULL,lang TEXT NOT NULL,PRIMARY KEY(word,lang))");
    }
  }

  public void beginBulk() {
    WRITE_LOCK.lock();
    boolean started = false;
    try {
      SQLiteDatabase database = getWritableDatabase();
      database.beginTransaction();
      bulkDatabase.set(database);
      started = true;
    } finally {
      if (!started) {
        WRITE_LOCK.unlock();
      }
    }
  }

  public void finishBulk(boolean successful) {
    SQLiteDatabase database = bulkDatabase.get();
    if (database == null) {
      return;
    }
    try {
      if (successful) {
        database.setTransactionSuccessful();
      }
    } finally {
      try {
        database.endTransaction();
      } finally {
        bulkDatabase.remove();
        WRITE_LOCK.unlock();
      }
    }
  }

  private boolean acquireStandaloneWrite() {
    if (bulkDatabase.get() != null) {
      return false;
    }
    WRITE_LOCK.lock();
    return true;
  }

  private void releaseStandaloneWrite(boolean acquired) {
    if (acquired) {
      WRITE_LOCK.unlock();
    }
  }

  private SQLiteDatabase currentWriteDatabase() {
    SQLiteDatabase database = bulkDatabase.get();
    return database == null ? getWritableDatabase() : database;
  }

  public void putWord(String word, String normalized, String lang, int frequency, boolean learned) {
    boolean acquired = acquireStandaloneWrite();
    try {
      SQLiteDatabase database = currentWriteDatabase();
      long now = System.currentTimeMillis() / 1000L;
      database.execSQL(
          "INSERT OR IGNORE INTO words"
              + "(word,normalized,lang,freq,learned,typed,last_used) "
              + "VALUES(?,?,?,?,?,?,?)",
          new Object[] {
            word, normalized, lang, Math.max(1, frequency), learned ? 1 : 0, 0, learned ? now : 0
          });
      if (learned) {
        database.execSQL(
            "UPDATE words SET normalized=?,learned=1,typed=typed+1,"
                + "freq=MAX(freq,8),last_used=? WHERE word=? AND lang=?",
            new Object[] {normalized, now, word, lang});
      } else {
        database.execSQL(
            "UPDATE words SET normalized=?,freq=MAX(freq,?) " + "WHERE word=? AND lang=?",
            new Object[] {normalized, Math.max(1, frequency), word, lang});
      }
    } catch (android.database.sqlite.SQLiteException error) {
      if (!acquired) {
        throw error;
      }
    } finally {
      releaseStandaloneWrite(acquired);
    }
  }

  public void importBigram(String previous, String word, String lang, int frequency) {
    boolean acquired = acquireStandaloneWrite();
    try {
      SQLiteDatabase database = currentWriteDatabase();
      database.execSQL(
          "INSERT OR IGNORE INTO bigrams" + "(previous,word,lang,freq,last_used) VALUES(?,?,?,?,0)",
          new Object[] {previous, word, lang, Math.max(1, frequency)});
      database.execSQL(
          "UPDATE bigrams SET freq=MAX(freq,?) " + "WHERE previous=? AND word=? AND lang=?",
          new Object[] {Math.max(1, frequency), previous, word, lang});
    } catch (android.database.sqlite.SQLiteException error) {
      if (!acquired) throw error;
    } finally {
      releaseStandaloneWrite(acquired);
    }
  }

  public void importTrigram(
      String previous2, String previous, String word, String lang, int frequency) {
    boolean acquired = acquireStandaloneWrite();
    try {
      SQLiteDatabase database = currentWriteDatabase();
      database.execSQL(
          "INSERT OR IGNORE INTO trigrams"
              + "(previous2,previous,word,lang,freq,last_used) VALUES(?,?,?,?,?,0)",
          new Object[] {previous2, previous, word, lang, Math.max(1, frequency)});
      database.execSQL(
          "UPDATE trigrams SET freq=MAX(freq,?) WHERE "
              + "previous2=? AND previous=? AND word=? AND lang=?",
          new Object[] {Math.max(1, frequency), previous2, previous, word, lang});
    } catch (android.database.sqlite.SQLiteException error) {
      if (!acquired) throw error;
    } finally {
      releaseStandaloneWrite(acquired);
    }
  }

  public void learnSequence(
      String previous2, String previous, String word, String normalized, String lang) {
    boolean acquired = acquireStandaloneWrite();
    try {
      putWord(word, normalized, lang, 8, true);
      if (previous == null || previous.length() == 0) {
        return;
      }
      long now = System.currentTimeMillis() / 1000L;
      SQLiteDatabase database = getWritableDatabase();
      database.execSQL(
          "INSERT OR IGNORE INTO bigrams" + "(previous,word,lang,freq,last_used) VALUES(?,?,?,0,?)",
          new Object[] {previous, word, lang, now});
      database.execSQL(
          "UPDATE bigrams SET freq=freq+1,last_used=? " + "WHERE previous=? AND word=? AND lang=?",
          new Object[] {now, previous, word, lang});
      if (previous2 != null && previous2.length() > 0) {
        database.execSQL(
            "INSERT OR IGNORE INTO trigrams"
                + "(previous2,previous,word,lang,freq,last_used) VALUES(?,?,?,?,0,?)",
            new Object[] {previous2, previous, word, lang, now});
        database.execSQL(
            "UPDATE trigrams SET freq=freq+1,last_used=? "
                + "WHERE previous2=? AND previous=? AND word=? AND lang=?",
            new Object[] {now, previous2, previous, word, lang});
      }
    } catch (android.database.sqlite.SQLiteException ignored) {
      // Local learning must never interrupt typing.
    } finally {
      releaseStandaloneWrite(acquired);
    }
  }

  public String exactWord(String word, String lang) {
    List<String> values =
        wordsQuery(
            "SELECT word FROM words WHERE lang=? AND lower(word)=? LIMIT 1",
            new String[] {lang, word.toLowerCase(java.util.Locale.ROOT)});
    return values.size() == 0 ? "" : values.get(0);
  }

  public String canonicalWord(String normalized, String lang) {
    List<String> values =
        wordsQuery(
            "SELECT word FROM words WHERE lang=? AND normalized=? "
                + "ORDER BY learned DESC,typed DESC,freq DESC,last_used DESC LIMIT 1",
            new String[] {lang, normalized});
    return values.size() == 0 ? "" : values.get(0);
  }

  public List<String> prefix(String prefix, String lang, int limit) {
    return wordsQuery(
        "SELECT word FROM words WHERE lang=? AND normalized LIKE ? "
            + "ORDER BY learned DESC,typed DESC,freq DESC,last_used DESC LIMIT ?",
        new String[] {lang, prefix + "%", String.valueOf(limit)});
  }

  public List<String> fuzzyPool(String lang, int minimumLength, int maximumLength, int limit) {
    return wordsQuery(
        "SELECT word FROM words WHERE lang=? AND length(normalized)>=? "
            + "AND length(normalized)<=? ORDER BY learned DESC,typed DESC,freq DESC LIMIT ?",
        new String[] {
          lang, String.valueOf(minimumLength), String.valueOf(maximumLength), String.valueOf(limit)
        });
  }

  public List<String> gesturePool(String lang, String firstLetter, String lastLetter, int limit) {
    return wordsQuery(
        "SELECT word FROM words WHERE lang=? AND normalized LIKE ? "
            + "AND normalized LIKE ? AND length(normalized)>=2 AND length(normalized)<=40 "
            + "ORDER BY learned DESC,typed DESC,freq DESC,last_used DESC LIMIT ?",
        new String[] {
          lang, firstLetter + "%", "%" + lastLetter, String.valueOf(Math.max(100, limit))
        });
  }

  public List<String> gestureFirstPool(String lang, String firstLetter, int limit) {
    return wordsQuery(
        "SELECT word FROM words WHERE lang=? AND normalized LIKE ? "
            + "AND length(normalized)>=2 AND length(normalized)<=40 "
            + "ORDER BY learned DESC,typed DESC,freq DESC,last_used DESC LIMIT ?",
        new String[] {lang, firstLetter + "%", String.valueOf(Math.max(100, limit))});
  }

  public List<String> next(String previous2, String previous, String lang, int limit) {
    LinkedHashSet<String> result = new LinkedHashSet<String>();
    result.addAll(nextTrigram(previous2, previous, lang, limit));
    if (result.size() < limit) {
      result.addAll(nextBigram(previous, lang, limit));
    }
    ArrayList<String> output = new ArrayList<String>(result);
    return output.subList(0, Math.min(limit, output.size()));
  }

  /** Learned trigrams table: two context words (previous2, previous). Tier 2 evidence. */
  public List<String> nextTrigram(String previous2, String previous, String lang, int limit) {
    if (previous2 == null
        || previous2.length() == 0
        || previous == null
        || previous.length() == 0) {
      return new ArrayList<String>();
    }
    return wordsQuery(
        "SELECT word FROM trigrams WHERE lang=? "
            + "AND previous2=? AND previous=? "
            + "ORDER BY (last_used>0) DESC,freq DESC,last_used DESC LIMIT ?",
        new String[] {lang, previous2, previous, String.valueOf(limit)});
  }

  /** Learned bigrams table: one context word (previous). Tier 1 evidence. */
  public List<String> nextBigram(String previous, String lang, int limit) {
    if (previous == null || previous.length() == 0) {
      return new ArrayList<String>();
    }
    return wordsQuery(
        "SELECT word FROM bigrams WHERE lang=? AND previous=? "
            + "ORDER BY (last_used>0) DESC,freq DESC,last_used DESC LIMIT ?",
        new String[] {lang, previous, String.valueOf(limit)});
  }

  public List<String> between(String previous, String following, String lang, int limit) {
    LinkedHashSet<String> result = new LinkedHashSet<String>();
    if (following == null || following.length() == 0) return new ArrayList<String>();
    if (previous != null && previous.length() > 0) {
      result.addAll(
          wordsQuery(
              "SELECT previous FROM trigrams WHERE lang=? AND previous2=? AND word=? "
                  + "ORDER BY (last_used>0) DESC,freq DESC,last_used DESC LIMIT ?",
              new String[] {lang, previous, following, String.valueOf(limit)}));
    }
    if (result.size() < limit) {
      result.addAll(
          wordsQuery(
              "SELECT previous FROM bigrams WHERE lang=? AND word=? "
                  + "ORDER BY (last_used>0) DESC,freq DESC,last_used DESC LIMIT ?",
              new String[] {lang, following, String.valueOf(limit)}));
    }
    ArrayList<String> output = new ArrayList<String>(result);
    return output.subList(0, Math.min(limit, output.size()));
  }

  public List<String> candidates(String lang, int limit) {
    return wordsQuery(
        "SELECT word FROM words WHERE lang=? "
            + "ORDER BY learned DESC,typed DESC,last_used DESC,freq DESC LIMIT ?",
        new String[] {lang, String.valueOf(limit)});
  }

  public List<String> dictionaryWords(String lang, String normalizedQuery, int limit, int offset) {
    String query = normalizedQuery == null ? "" : normalizedQuery;
    if (query.length() == 0) {
      return wordsQuery(
          "SELECT w.word FROM words w WHERE w.lang=? AND NOT EXISTS "
              + "(SELECT 1 FROM blocked_words b WHERE b.lang=w.lang AND b.word=w.word) "
              + "ORDER BY w.learned DESC,w.typed DESC,w.freq DESC,w.word ASC LIMIT ? OFFSET ?",
          new String[] {lang, String.valueOf(limit), String.valueOf(offset)});
    }
    return wordsQuery(
        "SELECT w.word FROM words w WHERE w.lang=? AND w.normalized LIKE ? AND NOT EXISTS "
            + "(SELECT 1 FROM blocked_words b WHERE b.lang=w.lang AND b.word=w.word) "
            + "ORDER BY w.learned DESC,w.typed DESC,w.freq DESC,w.word ASC LIMIT ? OFFSET ?",
        new String[] {lang, query + "%", String.valueOf(limit), String.valueOf(offset)});
  }

  public void addUserWord(String word, String normalized, String lang) {
    if (word == null || word.length() == 0) return;
    WRITE_LOCK.lock();
    SQLiteDatabase database = null;
    boolean transaction = false;
    try {
      database = getWritableDatabase();
      database.beginTransaction();
      transaction = true;
      database.delete("blocked_words", "word=? AND lang=?", new String[] {word, lang});
      ContentValues values = new ContentValues();
      values.put("word", word);
      values.put("normalized", normalized);
      values.put("lang", lang);
      values.put("freq", 200);
      values.put("learned", 1);
      values.put("typed", 1);
      values.put("last_used", System.currentTimeMillis() / 1000L);
      database.insertWithOnConflict("words", null, values, SQLiteDatabase.CONFLICT_REPLACE);
      database.setTransactionSuccessful();
    } catch (android.database.sqlite.SQLiteException ignored) {
    } finally {
      if (transaction && database != null) database.endTransaction();
      WRITE_LOCK.unlock();
    }
  }

  public void deleteWord(String word, String lang) {
    WRITE_LOCK.lock();
    SQLiteDatabase database = null;
    boolean transaction = false;
    try {
      database = getWritableDatabase();
      database.beginTransaction();
      transaction = true;
      database.execSQL(
          "INSERT OR REPLACE INTO blocked_words(word,lang) VALUES(?,?)", new Object[] {word, lang});
      database.delete("words", "word=? AND lang=?", new String[] {word, lang});
      database.delete(
          "bigrams", "lang=? AND (word=? OR previous=?)", new String[] {lang, word, word});
      database.delete(
          "trigrams",
          "lang=? AND (word=? OR previous=? OR previous2=?)",
          new String[] {lang, word, word, word});
      database.setTransactionSuccessful();
    } catch (android.database.sqlite.SQLiteException ignored) {
    } finally {
      if (transaction && database != null) database.endTransaction();
      WRITE_LOCK.unlock();
    }
  }

  public void editWord(String oldWord, String newWord, String normalized, String lang) {
    if (newWord == null || newWord.length() == 0 || oldWord.equals(newWord)) return;
    WRITE_LOCK.lock();
    SQLiteDatabase database = null;
    boolean transaction = false;
    try {
      database = getWritableDatabase();
      database.beginTransaction();
      transaction = true;
      database.execSQL(
          "INSERT OR REPLACE INTO blocked_words(word,lang) VALUES(?,?)",
          new Object[] {oldWord, lang});
      database.delete("blocked_words", "word=? AND lang=?", new String[] {newWord, lang});
      int frequency = 8;
      Cursor cursor =
          database.rawQuery(
              "SELECT freq FROM words WHERE word=? AND lang=?", new String[] {oldWord, lang});
      if (cursor.moveToFirst()) frequency = Math.max(8, cursor.getInt(0));
      cursor.close();
      database.delete("words", "word=? AND lang=?", new String[] {oldWord, lang});
      database.delete(
          "bigrams", "lang=? AND (word=? OR previous=?)", new String[] {lang, oldWord, oldWord});
      database.delete(
          "trigrams",
          "lang=? AND (word=? OR previous=? OR previous2=?)",
          new String[] {lang, oldWord, oldWord, oldWord});
      ContentValues values = new ContentValues();
      values.put("word", newWord);
      values.put("normalized", normalized);
      values.put("lang", lang);
      values.put("freq", frequency);
      values.put("learned", 1);
      values.put("typed", 1);
      values.put("last_used", System.currentTimeMillis() / 1000L);
      database.insertWithOnConflict("words", null, values, SQLiteDatabase.CONFLICT_REPLACE);
      database.setTransactionSuccessful();
    } catch (android.database.sqlite.SQLiteException ignored) {
    } finally {
      if (transaction && database != null) database.endTransaction();
      WRITE_LOCK.unlock();
    }
  }

  public boolean isBlocked(String word, String lang) {
    Cursor cursor = null;
    try {
      cursor =
          getReadableDatabase()
              .rawQuery(
                  "SELECT 1 FROM blocked_words WHERE word=? AND lang=? LIMIT 1",
                  new String[] {word, lang});
      return cursor.moveToFirst();
    } catch (android.database.sqlite.SQLiteException ignored) {
      return false;
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  /** Clears only the block-list so previously banned words can return to suggestions. */
  public void clearBlocked() {
    WRITE_LOCK.lock();
    try {
      getWritableDatabase().delete("blocked_words", null, null);
    } finally {
      WRITE_LOCK.unlock();
    }
  }

  public void clearLearned() {
    WRITE_LOCK.lock();
    SQLiteDatabase database = null;
    boolean transaction = false;
    try {
      database = getWritableDatabase();
      database.beginTransaction();
      transaction = true;
      database.delete("bigrams", null, null);
      database.delete("trigrams", null, null);
      database.delete("blocked_words", null, null);
      database.delete("words", "learned=1 AND freq<100", null);
      database.execSQL("UPDATE words SET learned=0,typed=0,last_used=0");
      database.setTransactionSuccessful();
    } catch (android.database.sqlite.SQLiteException ignored) {
      // Settings can retry later; never take down the IME process.
    } finally {
      if (transaction && database != null) {
        database.endTransaction();
      }
      WRITE_LOCK.unlock();
    }
  }

  private List<String> wordsQuery(String sql, String[] args) {
    ArrayList<String> output = new ArrayList<String>();
    Cursor cursor = null;
    try {
      cursor = getReadableDatabase().rawQuery(sql, args);
      while (cursor.moveToNext()) {
        output.add(cursor.getString(0));
      }
    } catch (android.database.sqlite.SQLiteException ignored) {
      // A dictionary refresh is optional; never crash the IME when SQLite is busy.
      output.clear();
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    return output;
  }

  private static void safeExec(SQLiteDatabase database, String sql) {
    try {
      database.execSQL(sql);
    } catch (Exception ignored) {
      // Migration may be partially applied by an interrupted older build.
    }
  }
}
