package com.almlk.swiftkey.ime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.theme.KeyboardTheme;

/** The compact voice surface shown above the keys. Recognition is owned by the IME service. */
public final class VoiceInputPanelView extends LinearLayout {
  public interface Callback {
    void onClose();

    void onLanguageToggle();
  }

  private TextView status, language;
  private ImageButton close;
  private VoiceLevelView meter;
  private Callback callback;
  private KeyboardTheme theme = KeyboardTheme.from("light");

  public VoiceInputPanelView(Context context) {
    super(context);
  }

  public VoiceInputPanelView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  protected void onFinishInflate() {
    super.onFinishInflate();
    status = (TextView) findViewById(R.id.voice_status);
    language = (TextView) findViewById(R.id.voice_language);
    close = (ImageButton) findViewById(R.id.voice_close);
    meter = (VoiceLevelView) findViewById(R.id.voice_level);
    close.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            if (callback != null) callback.onClose();
          }
        });
    language.setOnClickListener(
        new OnClickListener() {
          public void onClick(View view) {
            if (callback != null) callback.onLanguageToggle();
          }
        });
    applyTheme();
  }

  public void setCallback(Callback value) {
    callback = value;
  }

  public void setTheme(KeyboardTheme value) {
    if (value != null) {
      theme = value;
      applyTheme();
    }
  }

  public void begin(String code) {
    language.setText("ar".equals(code) ? "AR" : "EN");
    status.setText("التحدث الآن");
    meter.release();
    setVisibility(VISIBLE);
  }

  public void setListening() {
    status.setText("التحدث الآن");
  }

  public void setProcessing() {
    status.setText("جارٍ التعرّف…");
  }

  public void setPartial(String value) {
    if (value != null && value.trim().length() > 0) status.setText(value.trim());
  }

  public void setLevel(float value) {
    meter.setLevel(value);
  }

  public void releaseTransientState() {
    meter.release();
    status.setText("");
    setVisibility(GONE);
    clearAnimation();
  }

  private int interfaceTextColor() {
    return com.almlk.swiftkey.theme.ThemeChipArt.textColor(getContext(), theme);
  }

  private void applyTheme() {
    if (status == null) return;
    // Round 52: خلفية الثيم نفسها على لوحة الصوت كالكيبورد
    com.almlk.swiftkey.theme.ThemeSurfacePaint.apply(this, getContext(), theme);
    int uiColor = interfaceTextColor();
    status.setTextColor(uiColor);
    language.setTextColor(uiColor);
    close.setColorFilter(uiColor);
    meter.setMeterColor(theme.accent);
    GradientDrawable pill = new GradientDrawable();
    pill.setColor(Color.TRANSPARENT);
    pill.setStroke(dp(2), uiColor);
    pill.setCornerRadius(dp(24));
    language.setBackground(pill);
  }

  private int dp(int value) {
    return (int) (value * getResources().getDisplayMetrics().density + .5f);
  }
}
