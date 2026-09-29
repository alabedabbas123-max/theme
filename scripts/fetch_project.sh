#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# fetch_project.sh
#
# يسحب أرشيف المشروع (ZIP) من مستودع GitHub خاص ويفكّه داخل هذا المستودع.
#
# لماذا GitHub API وليس raw.githubusercontent.com؟
#   لأن مضيف raw.githubusercontent.com محجوب داخل بيئة التشغيل (يرجع 000)،
#   بينما api.github.com يعمل بشكل طبيعي.
#
# الاستخدام:
#   ./scripts/fetch_project.sh [OWNER/REPO] [PATH_IN_REPO] [REF]
#
# مثال:
#   ./scripts/fetch_project.sh alabedabbas123-max/themes Abbas_SwiftKeyIME.zip main
# ---------------------------------------------------------------------------
set -euo pipefail

REPO="${1:-alabedabbas123-max/themes}"
FILE_PATH="${2:-Abbas_SwiftKeyIME.zip}"
REF="${3:-main}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
ZIP="$WORK/project.zip"
trap 'rm -rf "$WORK"' EXIT

echo "==> التحقق من الوصول إلى $REPO ..."
if ! gh api "repos/$REPO" --jq '.full_name' >/dev/null 2>&1; then
  cat <<'MSG' >&2
✗ لا يوجد وصول إلى المستودع.

امنح تطبيق Arena وصولاً إليه:
  1) https://github.com/settings/installations
  2) Arena  ->  Configure
  3) Repository access  ->  أضف المستودع  ->  Save

ثم أعد تشغيل هذا السكربت.
MSG
  exit 1
fi

echo "==> تنزيل $FILE_PATH (ref=$REF) عبر GitHub API ..."
gh api "repos/$REPO/contents/$FILE_PATH?ref=$REF" \
  -H "Accept: application/vnd.github.raw" > "$ZIP"

if ! head -c 2 "$ZIP" | grep -q 'PK'; then
  echo "✗ الملف المنزَّل ليس أرشيف ZIP صالحًا." >&2
  head -c 300 "$ZIP" >&2
  exit 1
fi

echo "==> حجم الأرشيف: $(du -h "$ZIP" | cut -f1)"
echo "==> فكّ الضغط ..."
mkdir -p "$WORK/x"
unzip -q "$ZIP" -d "$WORK/x"

# إذا كان الأرشيف يحوي مجلدًا جذريًا واحدًا، ارفع محتواه مستوىً واحدًا.
SRC="$WORK/x"
if [ "$(find "$SRC" -mindepth 1 -maxdepth 1 | wc -l)" -eq 1 ] \
   && [ -d "$(find "$SRC" -mindepth 1 -maxdepth 1)" ]; then
  SRC="$(find "$SRC" -mindepth 1 -maxdepth 1)"
fi

echo "==> نسخ الملفات إلى $ROOT ..."
rm -rf "$SRC/.git"          # لا ندمج تاريخ git الخاص بالأرشيف
cp -a "$SRC"/. "$ROOT"/

echo "✓ تم. محتويات المشروع:"
ls -la "$ROOT"
