"""Contracts: parent must be ViewGroup, bridge must avoid API-23 symbols, sync files exist."""
import io
import os
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main")


def read(*parts):
  with io.open(os.path.join(SRC, *parts), encoding="utf-8") as handle:
    return handle.read()


def read_layout(name):
  with io.open(os.path.join(SRC, "res", "layout", name), encoding="utf-8") as f:
    return f.read()


class RuntimeSyncTest(unittest.TestCase):

  def test_resize_overlay_only_mirrors_the_sibling_block(self):
    # the scrim may override onMeasure ONLY to mirror the visible sibling's measured height
    # (that is what keeps the IME window from inflating a full-screen backdrop band) — any
    # self-invented geometry (heightForCurrent/baseline capture/pinned ceiling) stays banned
    overlay = read("java", "com", "almlk", "swiftkey", "ime", "KeyboardResizeOverlay.java")
    self.assertIn("protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {", overlay)
    self.assertIn("height = panel.getMeasuredHeight();", overlay)  # panel-EXACT by construction
    self.assertNotIn("box.getChildAt", overlay)
    self.assertIn("setMeasuredDimension(width, height);", overlay)
    frame_xml = read_layout("main_keyboard_frame.xml")
    self.assertNotIn("KeyboardResizeOverlay", frame_xml)  # scrim lives in the root, mirroring
    for banned in ("measureVisibleContent", "android.view.ViewGroup parent =", "heightForCurrent",
                   "pinnedHeightPx", "baseChromePx", "gridPxFor"):
      self.assertNotIn(banned, overlay)
    self.assertIn("invalidate();", overlay)  # the repaint rides vsync through View.invalidate
    # the pre-layout fallback is a CONSTANT dp estimate; it never measures the keyboard and no
    # value ever travels from it into the committed height (drag math lives in the controller)
    import re
    code = re.sub(r"//[^\n]*", "", overlay)
    code = re.sub(r"/\*.*?\*/", "", code, flags=re.S)
    self.assertIn("private int currentBlockHeightPx() {", code)
    self.assertIn("KeyboardResizeModel.DEFAULT_KEY_ROW_DP", code)
    self.assertNotIn("pendingRowHeightDp", code[code.index("private int currentBlockHeightPx"):code.index("private int currentBlockHeightPx") + 400])

  def test_bridge_has_no_raw_api23_calls(self):
    bridge = read("java", "com", "almlk", "swiftkey", "settings", "VoicePermissionActivity.java")
    self.assertIn('getMethod("checkSelfPermission", String.class)', bridge)
    self.assertIn('getMethod("requestPermissions", String[].class, int.class)', bridge)
    self.assertNotIn("super.onRequestPermissionsResult", bridge)
    self.assertNotIn("requestPermissions(new String[]", bridge)
    self.assertNotIn("|| checkSelfPermission(", bridge)

  def test_guarded_runtime_call_sites_keep_sdk_int_guard(self):
    base = read("java", "com", "almlk", "swiftkey", "ime", "AlmlkImeRuntimeBase.java")
    self.assertIn("android.os.Build.VERSION.SDK_INT >= 23", base)
    voice = read("java", "com", "almlk", "swiftkey", "settings", "VoiceSettingsActivity.java")
    self.assertIn("Build.VERSION.SDK_INT < 23", voice)

  def test_sync_critical_files_exist_with_their_api(self):
    normalizer = read("java", "com", "almlk", "swiftkey", "engine", "TextNormalizer.java")
    for method in ["public static String clean(String", "public static String lookup(String",
                   "public static String foldForComparison(String",
                   "public static String languageOf(String"]:
      self.assertIn(method, normalizer)
    shortcuts = read("java", "com", "almlk", "swiftkey", "data", "ShortcutRepository.java")
    for method in ["public synchronized List<Item> all()", "public synchronized void save(",
                   "public synchronized String expansion(", "public synchronized List<String> suggestions("]:
      self.assertIn(method, shortcuts)

  def test_manifest_declares_the_three_activities(self):
    manifest = read("AndroidManifest.xml")
    for name in ["LayoutsSettingsActivity", "ClipboardSettingsActivity", "VoicePermissionActivity"]:
      self.assertIn("com.almlk.swiftkey.settings." + name, manifest)

  def test_no_html_entities_in_any_java_file(self):
    offenders = []
    for dir_path, _dirs, files in os.walk(os.path.join(SRC, "java")):
      for name in files:
        if name.endswith(".java"):
          with io.open(os.path.join(dir_path, name), encoding="utf-8") as handle:
            if "&lt;" in handle.read() or "&amp;" in handle.read():
              offenders.append(name)
    self.assertEqual([], offenders)


if __name__ == "__main__":
  unittest.main()


class ParamShadowingTest(unittest.TestCase):
  """javac rejects redeclaring a method parameter as a local; guard the whole tree."""

  def scan(self):
    import re
    offenders = []
    java_root = os.path.join(SRC, "java")
    method_re = re.compile(
        r"(?:public|protected|private)[\w\s]*\b(\w+)\s*\(([^)]*)\)\s*\{")
    for dir_path, _dirs, files in os.walk(java_root):
      for name in files:
        if not name.endswith(".java"):
          continue
        path = os.path.join(dir_path, name)
        with io.open(path, encoding="utf-8") as handle:
          text = handle.read()
        for match in method_re.finditer(text):
          params = match.group(2)
          if not params.strip():
            continue
          names = set()
          for part in params.split(","):
            tokens = part.strip().split()
            if len(tokens) >= 2:
              names.add(tokens[-1])
          depth = 1
          index = match.end()
          body_end = len(text)
          while index < len(text):
            char = text[index]
            if char == "{":
              depth += 1
            elif char == "}":
              depth -= 1
              if depth == 0:
                body_end = index
                break
            index += 1
          body = text[match.end():body_end]
          for param in names:
            for decl in re.finditer(r"\b(?:final\s+)?[\w.<>\[\]]+\s+" + re.escape(param) + r"\s*(?:=|;)", body):
              snippet = decl.group(0)
              if snippet.strip().endswith(param + ";") or "=" in snippet:
                offenders.append("%s::%s redeclares param '%s'" % (name, match.group(1), param))
                break
    return offenders

  def test_no_method_redeclares_its_own_parameters(self):
    # javac is the authoritative scope checker: "variable x is already defined in method"
    # appears even with the Android jar absent, because it never needs symbol lookup.
    import shutil
    import subprocess
    if not shutil.which("javac"):
      self.skipTest("javac unavailable")
    sources = []
    java_root = os.path.join(SRC, "java")
    for dir_path, _dirs, files in os.walk(java_root):
      for name in files:
        if name.endswith(".java"):
          sources.append(os.path.join(dir_path, name))
    self.assertGreater(len(sources), 60)
    proc = subprocess.Popen(
        ["javac", "-encoding", "UTF-8", "-proc:none", "-nowarn", "-d", "/tmp/scope-probe"]
        + sources,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT)
    output = proc.communicate()[0].decode("utf-8", "replace")
    if not os.path.isdir("/tmp/scope-probe"):
      os.makedirs("/tmp/scope-probe")
    scope_errors = [line for line in output.splitlines() if "already defined in" in line]
    self.assertEqual([], scope_errors)

  def test_layouts_branch_stays_shadow_free(self):
    import io as _io
    path = os.path.join(SRC, "java", "com", "almlk", "swiftkey", "ime",
                        "AlmlkImeRuntimePart2C.java")
    with _io.open(path, encoding="utf-8") as handle:
        src = handle.read()
    # the branch now launches the picker; whatever it does, it must never redeclare `key`
    self.assertNotIn('String key = arabic', src)
    self.assertNotIn('String key = ', src[src.index('"layouts".equals'):])
