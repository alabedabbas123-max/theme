package com.almlk.swiftkey.ime;

import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.almlk.swiftkey.R;
import com.almlk.swiftkey.engine.*;
import com.almlk.swiftkey.model.*;
import java.util.*;

public abstract class AlmlkImeRuntimePart2B extends AlmlkImeRuntimePart2A {
  protected void showResizeOverlay() {
    if (resizeOverlay == null) return;
    closeTransientInterfaces(false);
    if (clipboardPanel != null) clipboardPanel.setVisibility(View.GONE);
    if (moreToolsPanel != null) moreToolsPanel.setVisibility(View.GONE);
    toolMore.setImageResource(R.drawable.ic_more);
    translationMode = false;
    emojiSearchMode = false;
    translationPanel.setVisibility(View.GONE);
    emojiPanel.setVisibility(View.GONE);
    emojiSearchHeader.setVisibility(View.GONE);
    standardKeyboardPanel.setVisibility(View.VISIBLE);
    normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
    setToolbarVisibility(inputNumeric ? View.GONE : View.VISIBLE);
    // The resize panel is a 13%-black dimmer riding ON TOP of the live keyboard: the real
    // keys stay visible under it (round 39-1: the user kept the translucent scrim). The drag
    // is LIVE — every display frame the panel rows grow/shrink through onLiveRowHeight ->
    // setKeyHeightDp and the window follows via the insets channel — and the release commits
    // the same value. The root keeps its normal opaque theme background: no interface may
    // peek above the keyboard's bounds.
    resizeOverlay.show(keyboard.getKeyHeightDp(), keyboard.getHeightReferenceRowCount());
    changeHeightSmoothly(imePanelRootView == null ? 0 : imePanelRootView.getMeasuredHeight());
    // opening the sheet grows the WINDOW (headroom for the drag), never the editor: insets keep
    // the app pinned under the keyboard's current top edge
  }

  protected void hideResizeOverlay() {
    // Called from onStartInput before the input view tree may exist at all; without an
    // overlay there is nothing to release, and touching half-built views crashes the service.
    if (resizeOverlay == null) return;
    resizeOverlay.releaseTransientState();
    if (keyboard != null) keyboard.requestLayout();
    changeHeightSmoothly(imePanelRootView == null ? 0 : imePanelRootView.getMeasuredHeight());
    // the sheet is gone: root collapses back to the panel column and the window wraps the
    // keyboard — taps above it reach the app again with no custom region maths anywhere
  }

  protected void showClipboard() {
    closeTransientInterfaces(false);
    translationMode = false;
    emojiSearchMode = false;
    translationPanel.setVisibility(View.GONE);
    emojiPanel.setVisibility(View.GONE);
    emojiSearchHeader.setVisibility(View.GONE);
    moreToolsPanel.setVisibility(View.GONE);
    standardKeyboardPanel.setVisibility(View.GONE);
    capturePrimaryClip();
    clipboardPanel.setVisibility(View.VISIBLE);
    setToolbarVisibility(View.VISIBLE);
    clipboardPanel.refresh();
  }

  protected void hideClipboard() {
    if (clipboardPanel == null) return;
    clipboardPanel.releaseTransientState();
    standardKeyboardPanel.setVisibility(View.VISIBLE);
    normalSuggestionRow.setVisibility(inputNumeric ? View.GONE : View.VISIBLE);
    if (!inputNumeric) setToolBarVisible(toolBarVisible);
    keyboard.requestLayout();
  }
}
