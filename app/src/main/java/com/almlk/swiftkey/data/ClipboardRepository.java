package com.almlk.swiftkey.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Local clipboard history with persistent pinning and manual ordering. */
public final class ClipboardRepository extends SQLiteOpenHelper {
  private static ClipboardRepository instance;
  private final android.content.Context appContext;

  public static final class Item {
    public long id, created;
    public int position;
    public String text;
    public boolean pinned;
  }

  public static synchronized ClipboardRepository get(Context context) {
    if (instance == null) {
      instance = new ClipboardRepository(context.getApplicationContext());
    }
    return instance;
  }

  private ClipboardRepository(Context context) {
    super(context, "clipboard.db", null, 2);
    appContext = context.getApplicationContext();
    if (android.os.Build.VERSION.SDK_INT >= 16) setWriteAheadLoggingEnabled(true);
  }

  /** Cap configured in the clipboard's own settings page (10..100). */
  private int maxItems() {
    return com.almlk.swiftkey.util.FeatureSettings.number(
        appContext, com.almlk.swiftkey.util.FeatureSettings.CLIPBOARD_MAX_ITEMS, 100, 10, 100);
  }

  /** Auto-purge window in days configured in the clipboard's own settings page. */
  private void pruneStale() {
    int days =
        com.almlk.swiftkey.util.FeatureSettings.number(
            appContext, com.almlk.swiftkey.util.FeatureSettings.CLIPBOARD_PURGE_DAYS, 0, 0, 365);
    if (days <= 0) return;
    long cutoff = System.currentTimeMillis() - days * 86400000L;
    getWritableDatabase()
        .delete("clips", "pinned=0 AND created<?", new String[] {String.valueOf(cutoff)});
  }

  public synchronized void clearAll() {
    getWritableDatabase().delete("clips", null, null);
  }

  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE clips(id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "text TEXT NOT NULL UNIQUE,created INTEGER NOT NULL,"
            + "pinned INTEGER NOT NULL DEFAULT 0,sort_order INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE INDEX idx_clips_order ON clips(pinned,sort_order,created)");
  }

  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    if (oldVersion < 2) {
      db.execSQL("ALTER TABLE clips ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0");
      db.execSQL(
          "UPDATE clips SET sort_order=(SELECT COUNT(*) FROM clips c2 WHERE c2.id>clips.id)");
      db.execSQL("DROP INDEX IF EXISTS idx_clips_order");
      db.execSQL("CREATE INDEX idx_clips_order ON clips(pinned,sort_order,created)");
    }
  }

  public synchronized void add(String text) {
    if (text == null) return;
    text = text.trim();
    if (text.length() == 0 || text.length() > 12000) return;
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.execSQL("UPDATE clips SET sort_order=sort_order+1 WHERE pinned=0");
      ContentValues values = new ContentValues();
      values.put("text", text);
      values.put("created", System.currentTimeMillis());
      values.put("sort_order", 0);
      long inserted =
          db.insertWithOnConflict("clips", null, values, SQLiteDatabase.CONFLICT_IGNORE);
      if (inserted < 0) {
        ContentValues recent = new ContentValues();
        recent.put("created", System.currentTimeMillis());
        recent.put("sort_order", 0);
        db.update("clips", recent, "text=?", new String[] {text});
      }
      db.execSQL(
          "DELETE FROM clips WHERE id NOT IN (SELECT id FROM clips "
              + "ORDER BY pinned DESC,sort_order ASC,created DESC LIMIT "
              + maxItems()
              + ")");
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized List<Item> list() {
    ArrayList<Item> result = new ArrayList<Item>();
    pruneStale();
    Cursor cursor =
        getReadableDatabase()
            .rawQuery(
                "SELECT id,text,created,pinned,sort_order FROM clips "
                    + "ORDER BY pinned DESC,sort_order ASC,created DESC LIMIT "
                    + maxItems(),
                null);
    try {
      while (cursor.moveToNext()) {
        Item item = new Item();
        item.id = cursor.getLong(0);
        item.text = cursor.getString(1);
        item.created = cursor.getLong(2);
        item.pinned = cursor.getInt(3) != 0;
        item.position = cursor.getInt(4);
        result.add(item);
      }
    } finally {
      cursor.close();
    }
    return result;
  }

  public synchronized void pin(long id, boolean value) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.execSQL(
          "UPDATE clips SET sort_order=sort_order+1 WHERE pinned=?", new Object[] {value ? 1 : 0});
      ContentValues values = new ContentValues();
      values.put("pinned", value ? 1 : 0);
      values.put("sort_order", 0);
      db.update("clips", values, "id=?", new String[] {String.valueOf(id)});
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized void reorder(List<Item> ordered) {
    if (ordered == null) return;
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      int pinnedPosition = 0;
      int normalPosition = 0;
      for (Item item : ordered) {
        ContentValues values = new ContentValues();
        int position = item.pinned ? pinnedPosition++ : normalPosition++;
        item.position = position;
        values.put("sort_order", position);
        db.update("clips", values, "id=?", new String[] {String.valueOf(item.id)});
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public synchronized void edit(long id, String text) {
    if (text == null || text.trim().length() == 0) return;
    ContentValues values = new ContentValues();
    values.put("text", text.trim());
    values.put("created", System.currentTimeMillis());
    getWritableDatabase().update("clips", values, "id=?", new String[] {String.valueOf(id)});
  }

  public synchronized void delete(long id) {
    getWritableDatabase().delete("clips", "id=?", new String[] {String.valueOf(id)});
  }
}
