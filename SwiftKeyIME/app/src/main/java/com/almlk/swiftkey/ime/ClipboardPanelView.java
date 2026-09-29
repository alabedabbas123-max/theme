package com.almlk.swiftkey.ime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.data.ClipboardRepository;
import com.almlk.swiftkey.theme.KeyboardTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Two-column clipboard with pinning, ellipsis, drag ordering and full-swipe deletion. */
public final class ClipboardPanelView extends LinearLayout {
  public interface Callback {
    void onInsert(String text);

    void onClose();
  }

  private final ClipboardRepository repository;
  private final ClipAdapter adapter = new ClipAdapter();
  private RecyclerView recycler;
  private TextView empty;
  private TextView title;
  private Callback callback;
  private KeyboardTheme theme = KeyboardTheme.from("light");
  private boolean orderDirty;
  private int refreshGeneration;
  private PopupWindow transientWindow;

  public ClipboardPanelView(Context context, AttributeSet attrs) {
    super(context, attrs);
    repository = ClipboardRepository.get(context);
    initialize();
  }

  public ClipboardPanelView(Context context) {
    super(context);
    repository = ClipboardRepository.get(context);
    initialize();
  }

  private void initialize() {
    setOrientation(VERTICAL);
    setPadding(dp(7), dp(3), dp(7), dp(8));

    LinearLayout header = new LinearLayout(getContext());
    header.setGravity(Gravity.CENTER_VERTICAL);
    if (android.os.Build.VERSION.SDK_INT >= 17)
      header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    title = new TextView(getContext());
    title.setText("الحافظة");
    title.setTextSize(20);
    title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    ImageButton back = new ImageButton(getContext());
    back.setImageResource(R.drawable.ic_chevron_right);
    back.setBackgroundColor(Color.TRANSPARENT);
    back.setContentDescription("إغلاق الحافظة");
    back.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            if (callback != null) callback.onClose();
          }
        });
    header.addView(title, new LayoutParams(0, dp(44), 1f));
    header.addView(back, new LayoutParams(dp(46), dp(44)));

    FrameLayout body = new FrameLayout(getContext());
    recycler = new RecyclerView(getContext());
    recycler.setLayoutManager(new GridLayoutManager(getContext(), 2));
    recycler.setAdapter(adapter);
    recycler.setOverScrollMode(OVER_SCROLL_NEVER);
    recycler.setItemAnimator(null);
    empty = new TextView(getContext());
    empty.setText("لا توجد عناصر محفوظة في الحافظة");
    empty.setTextSize(15);
    empty.setGravity(Gravity.CENTER);
    body.addView(
        recycler,
        new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    body.addView(
        empty, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));
    addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

    attachTouchOrganizer();
    applyTheme();
  }

  private void attachTouchOrganizer() {
    ItemTouchHelper.SimpleCallback touch =
        new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP
                | ItemTouchHelper.DOWN
                | ItemTouchHelper.LEFT
                | ItemTouchHelper.RIGHT,
            ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
          public boolean isLongPressDragEnabled() {
            return true;
          }

          public boolean isItemViewSwipeEnabled() {
            return true;
          }

          public float getSwipeThreshold(RecyclerView.ViewHolder holder) {
            return .92f;
          }

          public boolean onMove(
              RecyclerView view, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            int from = source.getAdapterPosition();
            int to = target.getAdapterPosition();
            if (from < 0 || to < 0 || from >= adapter.items.size() || to >= adapter.items.size())
              return false;
            if (adapter.items.get(from).pinned != adapter.items.get(to).pinned) return false;
            Collections.swap(adapter.items, from, to);
            adapter.notifyItemMoved(from, to);
            orderDirty = true;
            return true;
          }

          public void clearView(RecyclerView view, RecyclerView.ViewHolder holder) {
            super.clearView(view, holder);
            if (holder instanceof Holder) ((Holder) holder).resetSwipe();
            if (orderDirty) {
              orderDirty = false;
              final ArrayList<ClipboardRepository.Item> snapshot =
                  new ArrayList<ClipboardRepository.Item>(adapter.items);
              new Thread(
                      new Runnable() {
                        public void run() {
                          repository.reorder(snapshot);
                        }
                      },
                      "Almlk-Clipboard-Reorder")
                  .start();
            }
          }

          public void onSwiped(RecyclerView.ViewHolder holder, int direction) {
            int position = holder.getAdapterPosition();
            if (position < 0 || position >= adapter.items.size()) return;
            final ClipboardRepository.Item item = adapter.items.remove(position);
            adapter.notifyItemRemoved(position);
            empty.setVisibility(adapter.items.isEmpty() ? VISIBLE : GONE);
            new Thread(
                    new Runnable() {
                      public void run() {
                        repository.delete(item.id);
                      }
                    },
                    "Almlk-Clipboard-SwipeDelete")
                .start();
          }

          public void onChildDraw(
              Canvas canvas,
              RecyclerView view,
              RecyclerView.ViewHolder holder,
              float dx,
              float dy,
              int actionState,
              boolean active) {
            if (!(holder instanceof Holder)) {
              super.onChildDraw(canvas, view, holder, dx, dy, actionState, active);
              return;
            }
            Holder clip = (Holder) holder;
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
              float limit = holder.itemView.getWidth() * .96f;
              float clamped = Math.max(-limit, Math.min(limit, dx));
              clip.showDeleteUnderlay(clamped);
              clip.foreground.setTranslationX(clamped);
            } else {
              clip.foreground.setTranslationX(0f);
              clip.deleteUnderlay.setVisibility(GONE);
              super.onChildDraw(canvas, view, holder, dx, dy, actionState, active);
            }
          }
        };
    new ItemTouchHelper(touch).attachToRecyclerView(recycler);
  }

  public void setCallback(Callback value) {
    callback = value;
  }

  public void setTheme(KeyboardTheme value) {
    if (value == null) return;
    theme = value;
    applyTheme();
    adapter.notifyDataSetChanged();
  }

  public void releaseTransientState() {
    refreshGeneration++;
    orderDirty = false;
    if (transientWindow != null) {
      transientWindow.dismiss();
      transientWindow = null;
    }
    if (recycler != null) recycler.stopScroll();
    adapter.items.clear();
    adapter.notifyDataSetChanged();
    clearAnimation();
    setVisibility(GONE);
  }

  public void refresh() {
    final int generation = ++refreshGeneration;
    new Thread(
            new Runnable() {
              public void run() {
                final List<ClipboardRepository.Item> clips = repository.list();
                post(
                    new Runnable() {
                      public void run() {
                        if (generation != refreshGeneration || getVisibility() != VISIBLE) return;
                        adapter.items.clear();
                        adapter.items.addAll(clips);
                        adapter.notifyDataSetChanged();
                        empty.setVisibility(clips.isEmpty() ? VISIBLE : GONE);
                      }
                    });
              }
            },
            "Almlk-Clipboard-Refresh")
        .start();
  }

  private void runRepositoryAction(final Runnable action) {
    new Thread(
            new Runnable() {
              public void run() {
                action.run();
                post(
                    new Runnable() {
                      public void run() {
                        if (getVisibility() == VISIBLE) refresh();
                      }
                    });
              }
            },
            "Almlk-Clipboard-Action")
        .start();
  }

  private void applyTheme() {
    // Round 52: خلفية الثيم نفسها على لوحة الحافظة كالكيبورد
    com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(this, getContext(), theme);
    if (recycler != null)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(recycler, getContext(), theme);
    int uiColor = interfaceTextColor();
    if (empty != null) {
      empty.setTextColor(blend(uiColor, theme.surface, .35f));
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(empty, getContext(), theme);
    }
    if (title != null) title.setTextColor(uiColor);
    if (getChildCount() > 0 && getChildAt(0) instanceof ViewGroup) {
      ViewGroup header = (ViewGroup) getChildAt(0);
      for (int index = 0; index < header.getChildCount(); index++) {
        View child = header.getChildAt(index);
        if (child instanceof ImageButton) ((ImageButton) child).setColorFilter(uiColor);
      }
    }
  }

  private int interfaceTextColor() {
    return com.almlk.swiftkey.theme.ThemeChipArt.textColor(getContext(), theme);
  }

  private int blend(int first, int second, float amount) {
    float keep = 1f - amount;
    return android.graphics.Color.rgb(
        Math.round(android.graphics.Color.red(first) * keep + android.graphics.Color.red(second) * amount),
        Math.round(android.graphics.Color.green(first) * keep + android.graphics.Color.green(second) * amount),
        Math.round(android.graphics.Color.blue(first) * keep + android.graphics.Color.blue(second) * amount));
  }

  private GradientDrawable cardBackground(boolean pinned) {
    GradientDrawable background = new GradientDrawable();
    background.setColor(theme.key);
    background.setStroke(dp(pinned ? 2 : 1), pinned ? theme.accent : (theme.sub & 0x55ffffff));
    background.setCornerRadius(dp(16));
    return background;
  }

  private GradientDrawable deleteBackground() {
    GradientDrawable background = new GradientDrawable();
    background.setColor(0xffd63a3a);
    background.setCornerRadius(dp(16));
    return background;
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + .5f);
  }

  private final class ClipAdapter extends RecyclerView.Adapter<Holder> {
    final ArrayList<ClipboardRepository.Item> items = new ArrayList<ClipboardRepository.Item>();

    public Holder onCreateViewHolder(ViewGroup parent, int type) {
      FrameLayout swipeFrame = new FrameLayout(getContext());
      swipeFrame.setClipChildren(true);
      RecyclerView.LayoutParams params =
          new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, dp(116));
      params.setMargins(dp(4), dp(4), dp(4), dp(4));
      swipeFrame.setLayoutParams(params);
      return new Holder(swipeFrame);
    }

    public void onBindViewHolder(Holder holder, int position) {
      holder.bind(items.get(position));
    }

    public int getItemCount() {
      return items.size();
    }
  }

  private final class Holder extends RecyclerView.ViewHolder {
    final FrameLayout swipeFrame;
    final ImageView deleteUnderlay;
    final FrameLayout foreground;
    final TextView message;
    final ImageButton dots;
    final ImageView pinnedBadge;

    Holder(FrameLayout root) {
      super(root);
      swipeFrame = root;
      deleteUnderlay = new ImageView(getContext());
      deleteUnderlay.setImageResource(R.drawable.ic_trash);
      deleteUnderlay.setColorFilter(Color.WHITE);
      deleteUnderlay.setPadding(dp(13), dp(13), dp(13), dp(13));
      deleteUnderlay.setVisibility(GONE);
      root.addView(
          deleteUnderlay,
          new FrameLayout.LayoutParams(
              dp(54), LayoutParams.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

      foreground = new FrameLayout(getContext());
      message = new TextView(getContext());
      message.setGravity(Gravity.RIGHT | Gravity.TOP);
      if (android.os.Build.VERSION.SDK_INT >= 17)
        message.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
      message.setTextSize(16);
      message.setMaxLines(4);
      message.setEllipsize(TextUtils.TruncateAt.END);
      message.setPadding(dp(12), dp(33), dp(12), dp(9));
      dots = new ImageButton(getContext());
      dots.setImageResource(R.drawable.ic_more);
      dots.setBackgroundColor(Color.TRANSPARENT);
      dots.setContentDescription("خيارات العنصر");
      pinnedBadge = new ImageView(getContext());
      pinnedBadge.setImageResource(R.drawable.ic_pin);
      pinnedBadge.setPadding(dp(7), dp(7), dp(7), dp(7));

      foreground.addView(
          message,
          new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      foreground.addView(
          dots, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.TOP | Gravity.LEFT));
      foreground.addView(
          pinnedBadge, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP | Gravity.RIGHT));
      root.addView(
          foreground,
          new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    void bind(final ClipboardRepository.Item item) {
      resetSwipe();
      foreground.setBackground(cardBackground(item.pinned));
      message.setText(item.text);
      message.setTextColor(theme.text);
      dots.setColorFilter(theme.text);
      pinnedBadge.setColorFilter(theme.accent);
      pinnedBadge.setVisibility(item.pinned ? VISIBLE : GONE);
      foreground.setOnClickListener(
          new OnClickListener() {
            public void onClick(View view) {
              if (callback != null) callback.onInsert(item.text);
            }
          });
      dots.setOnClickListener(
          new OnClickListener() {
            public void onClick(View view) {
              showActionMenu(view, item);
            }
          });
    }

    void showDeleteUnderlay(float dx) {
      deleteUnderlay.setVisibility(VISIBLE);
      deleteUnderlay.setBackground(deleteBackground());
      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) deleteUnderlay.getLayoutParams();
      params.gravity = (dx >= 0 ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL;
      deleteUnderlay.setLayoutParams(params);
    }

    void resetSwipe() {
      foreground.setTranslationX(0f);
      deleteUnderlay.setVisibility(GONE);
    }
  }

  private void showActionMenu(View anchor, final ClipboardRepository.Item item) {
    LinearLayout menu = new LinearLayout(getContext());
    menu.setOrientation(VERTICAL);
    menu.setPadding(dp(6), dp(6), dp(6), dp(6));
    GradientDrawable background = new GradientDrawable();
    background.setColor(theme.surface);
    background.setStroke(dp(1), theme.accent);
    background.setCornerRadius(dp(14));
    menu.setBackground(background);

    if (transientWindow != null) transientWindow.dismiss();
    final PopupWindow window = new PopupWindow(menu, dp(196), LayoutParams.WRAP_CONTENT, true);
    transientWindow = window;
    window.setOnDismissListener(
        new PopupWindow.OnDismissListener() {
          public void onDismiss() {
            if (transientWindow == window) transientWindow = null;
          }
        });
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    window.setOutsideTouchable(true);
    if (android.os.Build.VERSION.SDK_INT >= 21) window.setElevation(dp(8));

    View copy = menuRow(R.drawable.ic_clipboard, "نسخ");
    View pin =
        menuRow(
            item.pinned ? R.drawable.ic_unpin : R.drawable.ic_pin,
            item.pinned ? "إلغاء التثبيت" : "تثبيت");
    View edit = menuRow(R.drawable.ic_edit, "تعديل");
    View remove = menuRow(R.drawable.ic_trash, "حذف");
    menu.addView(copy);
    menu.addView(pin);
    menu.addView(edit);
    menu.addView(remove);

    copy.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            ClipboardManager manager =
                (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null)
              manager.setPrimaryClip(ClipData.newPlainText("clipboard", item.text));
            window.dismiss();
          }
        });
    pin.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            window.dismiss();
            runRepositoryAction(
                new Runnable() {
                  public void run() {
                    repository.pin(item.id, !item.pinned);
                  }
                });
          }
        });
    edit.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            window.dismiss();
            openEditor(item);
          }
        });
    remove.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            window.dismiss();
            runRepositoryAction(
                new Runnable() {
                  public void run() {
                    repository.delete(item.id);
                  }
                });
          }
        });
    window.showAsDropDown(anchor, -dp(154), -dp(20));
  }

  private View menuRow(int icon, String label) {
    LinearLayout row = new LinearLayout(getContext());
    row.setGravity(Gravity.CENTER_VERTICAL);
    if (android.os.Build.VERSION.SDK_INT >= 17) row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    ImageView image = new ImageView(getContext());
    image.setImageResource(icon);
    image.setColorFilter(interfaceTextColor());
    image.setPadding(dp(9), dp(9), dp(9), dp(9));
    TextView text = new TextView(getContext());
    text.setText(label);
    text.setTextColor(interfaceTextColor());
    text.setTextSize(16);
    text.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
    row.addView(image, new LayoutParams(dp(46), dp(46)));
    row.addView(text, new LayoutParams(0, dp(46), 1f));
    return row;
  }

  private void openEditor(final ClipboardRepository.Item item) {
    LinearLayout box = new LinearLayout(getContext());
    box.setOrientation(VERTICAL);
    box.setPadding(dp(12), dp(10), dp(12), dp(10));
    box.setBackground(cardBackground(false));
    final EditText input = new EditText(getContext());
    input.setText(item.text);
    input.setTextColor(theme.text);
    TextView save = new TextView(getContext());
    save.setText("حفظ التعديل");
    save.setTextColor(theme.accent);
    save.setGravity(Gravity.CENTER);
    box.addView(input, new LayoutParams(dp(260), dp(74)));
    box.addView(save, new LayoutParams(dp(260), dp(42)));
    if (transientWindow != null) transientWindow.dismiss();
    final PopupWindow window = new PopupWindow(box, dp(284), dp(138), true);
    transientWindow = window;
    window.setOnDismissListener(
        new PopupWindow.OnDismissListener() {
          public void onDismiss() {
            if (transientWindow == window) transientWindow = null;
          }
        });
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    window.setOutsideTouchable(true);
    window.showAtLocation(this, Gravity.CENTER, 0, 0);
    save.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            final String changed = input.getText().toString();
            window.dismiss();
            runRepositoryAction(
                new Runnable() {
                  public void run() {
                    repository.edit(item.id, changed);
                  }
                });
          }
        });
  }
}
