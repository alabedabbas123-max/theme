package com.almlk.swiftkey.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.almlk.swiftkey.R;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Shared manager screen behind الاختصارات / القواميس / الحافظة: a RecyclerView of
 * rounded cards, each with a live search box, an options handle (edit, delete,
 * move up/down, plus screen-specific actions), drag reordering when the store
 * persists order, a floating ＋ button, a gear that opens that feature's own
 * settings page, and a kebab menu. Subclasses only supply entries and mutations.
 */
public abstract class EntryManagerActivity extends AppCompatActivity {

  /** One rendered card; tag carries the subclass-owned model object. */
  public static final class Entry {
    public final Object tag;
    public final String title;
    public final String subtitle;
    public final int badge;

    public Entry(Object value, String main, String detail) {
      this(value, main, detail, 0);
    }

    public Entry(Object value, String main, String detail, int badgeValue) {
      tag = value;
      title = main == null ? "" : main;
      subtitle = detail == null ? "" : detail;
      badge = badgeValue;
    }
  }

  protected final ArrayList<Entry> entries = new ArrayList<Entry>();
  protected final ArrayList<Entry> shown = new ArrayList<Entry>();
  protected String query = "";
  private final Handler ui = new Handler(Looper.getMainLooper());
  private EntryAdapter adapter;
  private RecyclerView recycler;
  private EditText searchField;
  private TextView statusView;
  private TextView emptyView;
  private boolean loadingMore;
  private final Runnable applyFilterTask =
      new Runnable() {
        public void run() {
          onSearchChanged();
        }
      };

  protected abstract int screenLayout();

  protected abstract CharSequence screenTitle();

  protected abstract int listId();

  protected abstract int searchId();

  protected abstract int fabId();

  /** 0 hides the empty-state view. */
  protected int emptyId() {
    return 0;
  }

  /** 0 hides the status line. */
  protected int statusId() {
    return 0;
  }

  /** Opens this feature's own settings page from the header gear; null hides the gear. */
  protected abstract Class<?> gearTarget();

  /** Kebab action "حذف الكل" is offered only when the screen opts in. */
  protected boolean supportsClearAll() {
    return false;
  }

  /** Fill {@link #entries} from the owning store. */
  protected abstract void rebuild();

  protected abstract void onAddEntry();

  protected abstract void onEditEntry(Entry entry);

  protected abstract void onDeleteEntry(Entry entry);

  /** Manual ordering only when the store persists it (shortcuts, clipboard). */
  protected boolean reorderAllowed() {
    return false;
  }

  /** Persist the current entries order after a drag or a move action. */
  protected void onReordered() {}

  protected void onClearAll() {}

  /** Extra per-entry option labels appended after the move actions. */
  protected String[] extraOptions(Entry entry) {
    return null;
  }

  /** Receives the label chosen from {@link #extraOptions(Entry)}. */
  protected void onOptionSelected(Entry entry, String label) {}

  /** Hook for screens with their own header widgets (clipboard clear button). */
  protected void onBindExtraViews() {}

  /** Hook to refresh whenever the screen becomes visible again. */
  protected boolean refreshOnResume() {
    return true;
  }

  /** Screens that search server-side (dictionary paging) keep the DB order. */
  protected boolean clientSideFilter() {
    return true;
  }

  protected void onSearchChanged() {
    applyFilter();
  }

  protected void onLoadMore() {}

  protected void updateStatus(int shownCount, int totalCount) {}

  protected final void finishLoadMore() {
    loadingMore = false;
  }

  protected void refresh() {
    rebuild();
    applyFilter();
  }

  protected final void applyFilter() {
    shown.clear();
    if (query.length() == 0 || !clientSideFilter()) {
      shown.addAll(entries);
    } else {
      String needle = query.toLowerCase(Locale.ROOT);
      for (Entry entry : entries) {
        if (entry.title.toLowerCase(Locale.ROOT).contains(needle)
            || entry.subtitle.toLowerCase(Locale.ROOT).contains(needle)) {
          shown.add(entry);
        }
      }
    }
    if (adapter != null) {
      adapter.notifyDataSetChanged();
    }
    if (emptyView != null) {
      emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }
    updateStatus(shown.size(), entries.size());
  }

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(screenLayout());
    ((TextView) findViewById(R.id.settings_page_title)).setText(screenTitle());
    findViewById(R.id.settings_page_back)
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                finish();
              }
            });
    View gear = findViewById(R.id.settings_page_gear);
    if (gear != null) {
      if (gearTarget() == null) {
        gear.setVisibility(View.GONE);
      } else {
        final Intent open = new Intent(this, gearTarget());
        gear.setVisibility(View.VISIBLE);
        gear.setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                startActivity(open);
              }
            });
      }
    }
    View kebab = findViewById(R.id.settings_page_more);
    if (kebab != null) {
      if (!supportsClearAll()) {
        kebab.setVisibility(View.GONE);
      } else {
        kebab.setVisibility(View.VISIBLE);
        kebab.setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                confirmClearAll();
              }
            });
      }
    }
    statusView = statusId() == 0 ? null : (TextView) findViewById(statusId());
    emptyView = emptyId() == 0 ? null : (TextView) findViewById(emptyId());
    recycler = (RecyclerView) findViewById(listId());
    adapter = new EntryAdapter();
    recycler.setLayoutManager(new LinearLayoutManager(this));
    recycler.setAdapter(adapter);
    recycler.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          public void onScrolled(RecyclerView view, int dx, int dy) {
            LinearLayoutManager layout = (LinearLayoutManager) view.getLayoutManager();
            int total = adapter.getItemCount();
            if (dy <= 0 || total == 0 || layout == null) return;
            if (layout.findLastVisibleItemPosition() >= total - 6 && !loadingMore) {
              loadingMore = true;
              onLoadMore();
            }
          }
        });
    if (reorderAllowed()) {
      attachDragHelper();
    }
    searchField = (EditText) findViewById(searchId());
    searchField.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

          public void onTextChanged(CharSequence value, int start, int before, int count) {
            ui.removeCallbacks(applyFilterTask);
            query = value == null ? "" : value.toString().trim();
            ui.postDelayed(applyFilterTask, 150);
          }

          public void afterTextChanged(Editable value) {}
        });
    findViewById(fabId())
        .setOnClickListener(
            new View.OnClickListener() {
              public void onClick(View view) {
                onAddEntry();
              }
            });
    onBindExtraViews();
  }

  protected void onResume() {
    super.onResume();
    if (refreshOnResume()) {
      refresh();
    }
  }

  protected int dp(int value) {
    float density = getResources().getDisplayMetrics().density;
    return Math.round(value * density);
  }

  protected final void setStatus(CharSequence value) {
    if (statusView != null && value != null) {
      statusView.setText(value);
    }
  }

  protected void toast(String message) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  private void attachDragHelper() {
    ItemTouchHelper.SimpleCallback callback =
        new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
          public boolean onMove(
              RecyclerView view, RecyclerView.ViewHolder from, RecyclerView.ViewHolder to) {
            int a = from.getAdapterPosition();
            int b = to.getAdapterPosition();
            if (a < 0 || b < 0 || a >= shown.size() || b >= shown.size() || a == b) {
              return a != b;
            }
            // Long-press drag is only enabled with an empty query, so shown mirrors entries.
            Entry moved = shown.get(a);
            shown.set(a, shown.get(b));
            shown.set(b, moved);
            int source = entries.indexOf(moved);
            if (source >= 0) {
              Entry displaced = entries.get(b);
              entries.set(b, moved);
              entries.set(source, displaced);
            }
            adapter.notifyItemMoved(a, b);
            return true;
          }

          public void onSwiped(RecyclerView.ViewHolder holder, int direction) {}

          public void clearView(RecyclerView view, RecyclerView.ViewHolder holder) {
            super.clearView(view, holder);
            onReordered();
            updateStatus(shown.size(), entries.size());
          }

          public boolean isLongPressDragEnabled() {
            return query.length() == 0;
          }
        };
    new ItemTouchHelper(callback).attachToRecyclerView(recycler);
  }

  protected void confirmClearAll() {
    new AlertDialog.Builder(this)
        .setTitle("حذف الكل")
        .setMessage("سيتم حذف كل عناصر هذه القائمة على هذا الجهاز. هل أنت متأكد؟")
        .setPositiveButton(
            "حذف الكل",
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                onClearAll();
                refresh();
              }
            })
        .setNegativeButton("إلغاء", null)
        .show();
  }

  protected void showEntryOptions(final Entry entry, View anchor) {
    ArrayList<String> labels = new ArrayList<String>();
    labels.add("تعديل");
    labels.add("حذف");
    final boolean canMove = reorderAllowed() && query.length() == 0;
    if (canMove) {
      labels.add("نقل لأعلى");
      labels.add("نقل لأسفل");
    }
    String[] extras = extraOptions(entry);
    if (extras != null) {
      for (int index = 0; index < extras.length; index++) labels.add(extras[index]);
    }
    final String[] options = labels.toArray(new String[labels.size()]);
    new AlertDialog.Builder(this)
        .setTitle(entry.title.length() > 48 ? entry.title.substring(0, 48) + "…" : entry.title)
        .setItems(
            options,
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                  onEditEntry(entry);
                } else if (which == 1) {
                  confirmDelete(entry);
                } else if (canMove && (which == 2 || which == 3)) {
                  moveEntry(entry, which == 2 ? -1 : 1);
                } else {
                  onOptionSelected(entry, options[which]);
                }
              }
            })
        .setNegativeButton("إغلاق", null)
        .show();
  }

  private void moveEntry(Entry entry, int delta) {
    int index = entries.indexOf(entry);
    int target = index + delta;
    if (index < 0 || target < 0 || target >= entries.size()) return;
    Entry other = entries.get(target);
    entries.set(index, other);
    entries.set(target, entry);
    shown.clear();
    shown.addAll(entries);
    adapter.notifyDataSetChanged();
    onReordered();
    updateStatus(shown.size(), entries.size());
  }

  protected void confirmDelete(final Entry entry) {
    new AlertDialog.Builder(this)
        .setTitle("حذف العنصر")
        .setMessage("هل تريد حذف «" + entry.title + "» نهائيًا؟")
        .setPositiveButton(
            "حذف",
            new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                onDeleteEntry(entry);
                refresh();
              }
            })
        .setNegativeButton("إلغاء", null)
        .show();
  }

  protected void setSearchEnabled(boolean value) {
    searchField.setEnabled(value);
  }

  private final class EntryHolder extends RecyclerView.ViewHolder {
    private final View body;
    private final TextView title;
    private final TextView subtitle;
    private final ImageView badge;

    EntryHolder(View view) {
      super(view);
      body = view.findViewById(R.id.entry_card_body);
      title = (TextView) view.findViewById(R.id.entry_title);
      subtitle = (TextView) view.findViewById(R.id.entry_subtitle);
      badge = (ImageView) view.findViewById(R.id.entry_badge);
    }
  }

  private final class EntryAdapter extends RecyclerView.Adapter<EntryHolder> {
    public EntryHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View view = getLayoutInflater().inflate(R.layout.item_entry_card, parent, false);
      return new EntryHolder(view);
    }

    public void onBindViewHolder(EntryHolder holder, int position) {
      final Entry entry = shown.get(Math.max(0, Math.min(shown.size() - 1, position)));
      holder.title.setText(entry.title);
      if (entry.subtitle.length() == 0) {
        holder.subtitle.setVisibility(View.GONE);
      } else {
        holder.subtitle.setVisibility(View.VISIBLE);
        holder.subtitle.setText(entry.subtitle);
      }
      holder.badge.setVisibility(entry.badge == 1 ? View.VISIBLE : View.GONE);
      holder.body.setOnClickListener(
          new View.OnClickListener() {
            public void onClick(View view) {
              onEditEntry(entry);
            }
          });
      ((ImageButton) holder.itemView.findViewById(R.id.entry_handle))
          .setOnClickListener(
              new View.OnClickListener() {
                public void onClick(View view) {
                  showEntryOptions(entry, view);
                }
              });
    }

    public int getItemCount() {
      return shown.size();
    }
  }
}
