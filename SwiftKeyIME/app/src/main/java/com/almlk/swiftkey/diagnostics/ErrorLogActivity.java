package com.almlk.swiftkey.diagnostics;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.almlk.swiftkey.R;

/** User-facing local crash report viewer. */
public final class ErrorLogActivity extends AppCompatActivity {
  private TextView statusView;
  private TextView logView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_error_log);

    statusView = (TextView) findViewById(R.id.error_status);
    logView = (TextView) findViewById(R.id.error_log_text);

    findViewById(R.id.error_back)
        .setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                finish();
              }
            });
    findViewById(R.id.error_copy)
        .setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                copyLog();
              }
            });
    findViewById(R.id.error_share)
        .setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                shareLog();
              }
            });
    findViewById(R.id.error_clear)
        .setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                ErrorTracker.clear(ErrorLogActivity.this);
                refresh();
                Toast.makeText(
                        ErrorLogActivity.this, R.string.error_log_cleared, Toast.LENGTH_SHORT)
                    .show();
              }
            });
    refresh();
  }

  @Override
  protected void onResume() {
    super.onResume();
    refresh();
  }

  private void refresh() {
    String log = ErrorTracker.read(this);
    boolean empty = log.trim().length() == 0;
    statusView.setText(empty ? R.string.error_status_clean : R.string.error_status_found);
    logView.setText(empty ? getString(R.string.error_log_empty) : log);
  }

  private void copyLog() {
    String log = ErrorTracker.read(this);
    if (log.trim().length() == 0) {
      Toast.makeText(this, R.string.error_log_empty, Toast.LENGTH_SHORT).show();
      return;
    }
    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard != null) {
      clipboard.setPrimaryClip(ClipData.newPlainText("Almlk error log", log));
      Toast.makeText(this, R.string.error_log_copied, Toast.LENGTH_SHORT).show();
    }
  }

  private void shareLog() {
    String log = ErrorTracker.read(this);
    if (log.trim().length() == 0) {
      Toast.makeText(this, R.string.error_log_empty, Toast.LENGTH_SHORT).show();
      return;
    }
    Intent share = new Intent(Intent.ACTION_SEND);
    share.setType("text/plain");
    share.putExtra(Intent.EXTRA_SUBJECT, "Almlk Keyboard error report");
    share.putExtra(Intent.EXTRA_TEXT, log);
    startActivity(Intent.createChooser(share, getString(R.string.error_share_title)));
  }
}
