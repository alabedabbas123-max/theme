package com.almlk.swiftkey.ime;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.engine.*;
import com.almlk.swiftkey.model.*;
import com.almlk.swiftkey.theme.KeyboardTheme;
import com.almlk.swiftkey.theme.ThemeSkinBar;
import java.util.*;

public abstract class AlmlkImeRuntimePart2A extends AlmlkImeRuntimePart1 {

  protected void bindToolButton(View root, int id, final String key) {
    if (root == null) return;
    View button = root.findViewById(id);
    if (button != null) {
      button.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              handleToolAction(key);
            }
          });
    }
  }

  protected void bindMoreTools() {
    if (moreToolsPanel == null) return;
    moreToolsPanel.setCallback(
        new MoreToolsPanelView.Callback() {
          @Override
          public void onToolAction(String key) {
            handleToolAction(key);
          }

          @Override
          public void onToolbarConfigurationChanged() {
            applyToolbarConfiguration();
          }
        });
  }

  protected void bindResizeOverlay() {
    if (resizeOverlay == null) return;
    // The overlay owns preview + persistence; the runtime applies every height — live and
    // final — through the insets channel (onComputeInsets + updateInputViewShown).
    resizeOverlay.setCallback(
        new KeyboardResizeOverlay.Callback() {
          @Override
          public void onResizeCommitted(float rowHeightDp) {
            if (keyboard != null) {
              keyboard.setKeyHeightDp(rowHeightDp);
              changeHeightSmoothly(imePanelRootView == null ? 0 : imePanelRootView.getMeasuredHeight());
            }
          }

          @Override
          public void onLiveRowHeight(float rowHeightDp) {
            if (keyboard != null) {
              keyboard.setKeyHeightDp(rowHeightDp);

              changeHeightSmoothly(imePanelRootView == null ? 0 : imePanelRootView.getMeasuredHeight());
            }
          }

          @Override
          public void onConfirm() {
            hideResizeOverlay();
          }

          @Override
          public void onReset() {
            // لم تعد هناك واجهة إعادة ضبط عائمة في وضع تغيير الحجم.
          }
        });
  }

  /** Applies the active theme to suggestion chips from this superclass layer for AIDE. */
  protected void applySuggestionTheme() {
    if (suggestions == null || currentTheme == null) return;
    // Round 69: لون حروف الشارات = لون حروف الزر المفعّل نفسه — لون واحد
    // مع المفاتيح وشريط الأدوات وكل الواجهات.
    final int chipColor =
        com.almlk.swiftkey.theme.ThemeChipArt.textColor(this, currentTheme);
    int count = suggestions.getChildCount();
    for (int index = 0; index < count; index++) {
      View child = suggestions.getChildAt(index);
      if (!(child instanceof TextView)) continue;
      TextView chip = (TextView) child;
      chip.setTextColor(
          android.content.res.ColorStateList.valueOf(
              chip.length() == 0 ? Color.TRANSPARENT : chipColor));
      chip.setAlpha(1f);
      chip.setEnabled(true);
      if (chip instanceof SuggestionChipView) {
        ((SuggestionChipView) chip).setBestColor(chipColor);
      }
      chip.setBackground(suggestionBackground());
    }
    suggestions.invalidate();
  }

  protected void applyLiveSettings() {
    if (keyboard == null || prefs == null) return;

    KeyboardTheme nextTheme = KeyboardTheme.load(this, prefs.theme());
    if (nextTheme == null) return;
    int nextSignature = themeSignature(nextTheme);
    boolean themeChanged = nextSignature != appliedThemeSignature;
    currentTheme = nextTheme;
    if (themeChanged) {
      appliedThemeSignature = nextSignature;
      keyboard.setTheme(currentTheme);
    }

    if (emojiPanel != null) emojiPanel.setTheme(currentTheme);
    if (translationPanel != null) translationPanel.setTheme(currentTheme);
    if (moreToolsPanel != null) moreToolsPanel.setTheme(currentTheme);
    if (clipboardPanel != null) clipboardPanel.setTheme(currentTheme);
    if (voiceInputPanel != null) voiceInputPanel.setTheme(currentTheme);
    if (resizeOverlay != null) resizeOverlay.setTheme(currentTheme);
    if (stickerStorePanel != null) stickerStorePanel.setTheme(currentTheme);
    if (layoutsPanel != null) layoutsPanel.setTheme(currentTheme);

    // Round 52: خلفية الثيم نفسها على كل اللوحات — الإيموجي والحافظة والأدوات
    // الإضافية والترجمة والصوت ولوحة التخطيطات: استمرارية بصرية كاملة، وبلا
    // صورة يبقى لون السطح كما كان (تطبيق مرتّب بعد setTheme الداخلية).
    if (emojiPanel != null)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(emojiPanel, this, currentTheme);
    if (translationPanel != null)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(translationPanel, this, currentTheme);
    if (moreToolsPanel != null)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(moreToolsPanel, this, currentTheme);
    if (clipboardPanel != null)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(clipboardPanel, this, currentTheme);
    if (voiceInputPanel != null)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(voiceInputPanel, this, currentTheme);
    if (layoutsPanel != null)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(layoutsPanel, this, currentTheme);
    if (stickerStorePanel != null)
      com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(stickerStorePanel, this, currentTheme);

    if (inputRoot != null) inputRoot.setBackgroundColor(currentTheme.background);
    if (languageIndicator != null) languageIndicator.setBackgroundColor(currentTheme.accent);
    if (standardKeyboardPanel != null)
      standardKeyboardPanel.setBackgroundColor(currentTheme.background);
    // Round 44: photo/asset themes paint the suggestion strip with their wide bar
    // art — the new button shape covers the strip along with the keyboard.
    // Round 52: وبلا جلد تُرسم صورة الخلفية نفسها على الشريط — فاختيار الخلفية
    // يطبّق على الاقتراحات وشريط الأدوات وكل الواجهات، لا على المفاتيح وحدها.
    if (normalSuggestionRow != null) {
      android.graphics.drawable.Drawable rowArt = barSurfaceArt();
      if (rowArt != null) normalSuggestionRow.setBackgroundDrawable(rowArt);
      else normalSuggestionRow.setBackgroundColor(currentTheme.surface);
    }
    applySuggestionTheme();

    // Round 44: لون واحد لكل أيقونات/نصوص الواجهة مشتق من لون الحروف المختار.
    // لا نعود إلى accent أو surfaceText حتى يبقى سهم الطي/الرجوع وكل الأدوات
    // متزامنة فور تغيير لون الحروف من محرر الثيم.
    int toolbarIconColor =
        currentTheme.animationStyle == KeyboardTheme.ANIMATION_NONE
            ? com.almlk.swiftkey.theme.ThemeChipArt.textColor(this, currentTheme)
            : com.almlk.swiftkey.theme.ThemeChipArt.textColor(this, currentTheme);

    if (inputRoot != null) {
      View spacer = inputRoot.findViewById(R.id.suggestion_left_spacer);
      View toggle = inputRoot.findViewById(R.id.tool_bar_toggle);
      View toolbarSurface = inputRoot.findViewById(R.id.tool_bar_scroll);

      // Round 44: the toolbar chrome carries the same bar art; family themes keep
      // their flat surface color. Round 52: وبلا جلد — صورة الخلفية نفسها.
      if (spacer != null) {
        android.graphics.drawable.Drawable spacerArt = barSurfaceArt();
        if (spacerArt != null) spacer.setBackgroundDrawable(spacerArt);
        else spacer.setBackgroundColor(currentTheme.surface);
      }
      if (toolbarSurface != null) {
        android.graphics.drawable.Drawable scrollArt = barSurfaceArt();
        if (scrollArt != null) toolbarSurface.setBackgroundDrawable(scrollArt);
        else toolbarSurface.setBackgroundColor(currentTheme.surface);
      }
      if (toggle != null) {
        toggle.setBackgroundColor(Color.TRANSPARENT);
        if (toggle instanceof ImageButton) {
          // Round 76: سهم الرجوع/إخفاء شريط الأدوات يتبع لون الحروف المختار
          // مثل بقية أيقونات الواجهة، لا لون shiftKey القديم.
          ((ImageButton) toggle).setColorFilter(toolbarIconColor);
        }
      }
    }

    if (toolBar != null) {
      if (toolBar instanceof ThemeDecorationBarLayout) {
        ((ThemeDecorationBarLayout) toolBar).setTheme(currentTheme);
        // Round 44: the decoration bar itself rides on the theme's bar art.
        // Round 52: أو صورة الخلفية إن لم يكن جلد — نفس أولوية بقية الأشرطة.
        android.graphics.drawable.Drawable barArt = barSurfaceArt();
        if (barArt != null) toolBar.setBackgroundDrawable(barArt);
      } else {
        toolBar.setBackgroundColor(currentTheme.surface);
      }
      int childCount = toolBar.getChildCount();
      for (int i = 0; i < childCount; i++) {
        View child = toolBar.getChildAt(i);
        if (child instanceof ImageButton) {
          ((ImageButton) child).setColorFilter(toolbarIconColor);
          child.setBackgroundColor(Color.TRANSPARENT);
        }
      }
    }

    if (keyboard != null) {
      keyboard.setKeyHeightDp(KeyboardResizeModel.loadRowHeightDp(getApplicationContext()));
      changeHeightSmoothly(imePanelRootView == null ? 0 : imePanelRootView.getMeasuredHeight());
    }

    applyToolbarConfiguration();
    applyLayout();
    refreshSuggestions();
  }

  /**
   * Round 52: أولوية خلفية الأشرطة — جلد المسطرة (round44) أولاً، ثم صورة الخلفية
   * نفسها (round52)، ولا شيء للثيمات اللونية (لون السطح المسطح كما كان). كل
   * استدعاء يعيد دروابل جديدة لأن bounds لكل واجهة على حدة.
   */
  private android.graphics.drawable.Drawable barSurfaceArt() {
    android.graphics.drawable.Drawable skin = ThemeSkinBar.forTheme(this, currentTheme);
    if (skin != null) return skin;
    return com.almlk.swiftkey.theme.ThemeSurfacePaint.forTheme(this, currentTheme);
  }

  private int themeSignature(KeyboardTheme theme) {
    int result = 17;
    result = 31 * result + theme.background;
    result = 31 * result + theme.key;
    result = 31 * result + theme.keyPressed;
    result = 31 * result + theme.text;
    result = 31 * result + theme.sub;
    result = 31 * result + theme.accent;
    result = 31 * result + Float.floatToIntBits(theme.keyOpacity);
    result = 31 * result + Float.floatToIntBits(theme.keyRadiusDp);
    result = 31 * result + Float.floatToIntBits(theme.mainTextSizeDp);
    result = 31 * result + Float.floatToIntBits(theme.subTextSizeDp);
    result = 31 * result + theme.imageUri.hashCode();
    // Round 52: التوقيع يشمل الآن كل الحقول البصرية — تغيير الإطار أو الجلد
    // أو التعتيم أو الخط أو ألوان الأزرار الخاصة يصل الكيبورد الحي فوراً
    // (كانت تضيع حتى يتبدل حقل آخر لأنها خارج التوقيع).
    result = 31 * result + theme.fontStyle;
    result = 31 * result + Float.floatToIntBits(theme.backgroundDim);
    result = 31 * result + theme.surfaceOverride;
    result = 31 * result + theme.bottomKey;
    result = 31 * result + theme.spaceKey;
    result = 31 * result + theme.deleteKey;
    result = 31 * result + theme.shiftKey;
    result = 31 * result + theme.keySkin.hashCode();
    result = 31 * result + theme.keyFrame;
    result = 31 * result + theme.keyFrameUri.hashCode();
    result = 31 * result + theme.keySpaceFrameUri.hashCode();
    // Round 59: تفاعل الضغط ضمن التوقيع — اختياره يحدّث الكيبورد الحي فوراً
    result = 31 * result + theme.pressEffect;
    result = 31 * result + theme.pressEffectUri.hashCode();
    // Round 69: مقياسا عرض/ارتفاع الزر ضمن التوقيع — الشريطان يحدّثان الحي فوراً
    result = 31 * result + Float.floatToIntBits(theme.keyWidthScale);
    result = 31 * result + Float.floatToIntBits(theme.keyHeightScale);
    result = 31 * result + Float.floatToIntBits(theme.mainTextPosition);
    result = 31 * result + Float.floatToIntBits(theme.subTextPosition);
    return result;
  }
}
