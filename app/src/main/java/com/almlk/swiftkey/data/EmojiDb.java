package com.almlk.swiftkey.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import com.almlk.swiftkey.diagnostics.ErrorTracker;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Persistent indexed emoji catalogue. Seeding and search never run on the IME UI thread. */
public final class EmojiDb extends SQLiteOpenHelper {
  public interface SearchCallback {
    void onResult(String query, List<String> values);
  }

  private static EmojiDb instance;
  private final Context context;
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile boolean ready;
  private volatile boolean seeding;

  public static synchronized EmojiDb getInstance(Context context) {
    if (instance == null) instance = new EmojiDb(context.getApplicationContext());
    return instance;
  }

  private EmojiDb(Context value) {
    super(value, "emoji.db", null, 3);
    context = value;
    ready =
        context
            .getSharedPreferences("emoji_database", Context.MODE_PRIVATE)
            .getBoolean("seed_v3", false);
    if (android.os.Build.VERSION.SDK_INT >= 16) setWriteAheadLoggingEnabled(true);
    ensureSeededAsync();
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    super.onConfigure(db);
    try {
      db.execSQL("PRAGMA busy_timeout=2500");
    } catch (Exception ignored) {
    }
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE emoji(emoji TEXT PRIMARY KEY,category TEXT NOT NULL,"
            + "keywords TEXT NOT NULL DEFAULT '',usage INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE INDEX idx_emoji_category ON emoji(category)");
    db.execSQL("CREATE INDEX idx_emoji_usage ON emoji(usage)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 3) {
      db.delete("emoji", null, null);
    }
  }

  public void ensureSeededAsync() {
    if (ready || seeding) return;
    synchronized (this) {
      if (ready || seeding) return;
      seeding = true;
    }
    Thread worker =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                SQLiteDatabase db = null;
                try {
                  db = getWritableDatabase();
                  db.beginTransaction();
                  BufferedReader data =
                      new BufferedReader(
                          new InputStreamReader(
                              context.getAssets().open("emoji/emoji_data.tsv"), "UTF-8"));
                  String line;
                  while ((line = data.readLine()) != null) {
                    String[] part = line.split("\\t", 3);
                    if (part.length < 3) continue;
                    ContentValues values = new ContentValues();
                    values.put("emoji", part[1]);
                    values.put("category", part[0]);
                    values.put("keywords", normalize(part[2]));
                    db.insertWithOnConflict("emoji", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                  }
                  data.close();
                  BufferedReader lexicon =
                      new BufferedReader(
                          new InputStreamReader(
                              context.getAssets().open("emoji/emoji_search_ar_en.tsv"), "UTF-8"));
                  while ((line = lexicon.readLine()) != null) {
                    String[] part = line.split("\\t", 3);
                    if (part.length < 3) continue;
                    db.execSQL(
                        "UPDATE emoji SET keywords=keywords||' '||?||' '||? WHERE emoji=?",
                        new Object[] {normalize(part[1]), normalize(part[2]), part[0]});
                  }
                  lexicon.close();
                  db.setTransactionSuccessful();
                  context
                      .getSharedPreferences("emoji_database", Context.MODE_PRIVATE)
                      .edit()
                      .putBoolean("seed_v3", true)
                      .apply();
                  ready = true;
                } catch (Exception error) {
                  ErrorTracker.record(context, "Emoji database seed", error);
                } finally {
                  if (db != null && db.inTransaction()) db.endTransaction();
                  seeding = false;
                }
              }
            },
            "Almlk-Emoji-Seed");
    worker.setPriority(Thread.MIN_PRIORITY);
    worker.start();
  }

  public void searchAsync(final String query, final int limit, final SearchCallback callback) {
    ensureSeededAsync();
    final String clean = normalize(query);
    Thread worker =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                if (!ready) {
                  for (int i = 0; i < 200 && !ready && seeding; i++) {
                    try {
                      Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                      break;
                    }
                  }
                }
                final List<String> result = search(clean, limit);
                main.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        callback.onResult(query, result);
                      }
                    });
              }
            },
            "Almlk-Emoji-Search");
    worker.setPriority(Thread.NORM_PRIORITY - 1);
    worker.start();
  }

  private List<String> search(String query, int limit) {
    ArrayList<String> output = new ArrayList<String>();
    Cursor cursor = null;
    try {
      ArrayList<String> args = new ArrayList<String>();
      StringBuilder where = new StringBuilder();
      String[] tokens = query.split("\\s+");
      for (String token : tokens) {
        if (token.length() == 0) continue;
        if (where.length() > 0) where.append(" AND ");
        where.append("(keywords LIKE ? OR emoji=?)");
        args.add("%" + token + "%");
        args.add(token);
      }
      String sql =
          "SELECT emoji FROM emoji"
              + (where.length() == 0 ? "" : " WHERE " + where)
              + " ORDER BY usage DESC,rowid ASC LIMIT ?";
      args.add(String.valueOf(Math.max(1, limit)));
      cursor = getReadableDatabase().rawQuery(sql, args.toArray(new String[args.size()]));
      while (cursor.moveToNext()) output.add(cursor.getString(0));
    } catch (Exception error) {
      ErrorTracker.record(context, "Emoji database search", error);
    } finally {
      if (cursor != null) cursor.close();
    }
    return output;
  }

  public void remember(final String emoji) {
    Thread worker =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  getWritableDatabase()
                      .execSQL(
                          "UPDATE emoji SET usage=usage+1 WHERE emoji=?", new Object[] {emoji});
                } catch (Exception ignored) {
                }
              }
            },
            "Almlk-Emoji-Usage");
    worker.setPriority(Thread.MIN_PRIORITY);
    worker.start();
  }

  private static String normalize(String value) {
    if (value == null) return "";
    String result = value.trim().toLowerCase(Locale.ROOT);
    result = result.replace("ـ", "");
    result = result.replaceAll("[\\u064B-\\u065F\\u0670]", "");
    result = result.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا');
    result = result.replace('ى', 'ي').replace('ئ', 'ي').replace('ی', 'ي');
    result = result.replace('ؤ', 'و').replace('ة', 'ه').replace('ک', 'ك');
    return result;
  }
}
