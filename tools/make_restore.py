#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يولّد «حزمة الاستدعاء» AlmlkKeyboard-restore.py: ملف بايثون واحد مكتفٍ بذاته،
يحمل المشروع كاملًا (base64+zip مضمّن) + بصمة sha256، تفكّه أي محادثة جديدة بأمر
  python3 AlmlkKeyboard-restore.py --test
فيحصل المساعد هناك على الشجرة كاملة والخطة المرفقة داخلها ثم يشغّل الـsuite تلقائيًا.
شغّله من جذر المشروع بعد كل جولة تحديث:  python3 tools/make_restore.py
"""
import base64
import hashlib
import io
import os
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, ".."))
WORKSPACE = os.path.abspath(os.path.join(ROOT, ".."))
OUT = os.path.join(WORKSPACE, "AlmlkKeyboard-restore.py")
SKIP_DIRS = {"__pycache__", ".git", "build", ".gradle", "keys", ".arena", ".cache"}

PREAMBLE = '''#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AlmlkKeyboard-restore.py — حزمة استدعاء مشروع «كيبورد الملك» كاملة في ملف واحد.

التشغيل (في أي بيئة فيها python3 فقط):
    python3 AlmlkKeyboard-restore.py           # يفكّ المشروع إلى ./SwiftKeyIME
    python3 AlmlkKeyboard-restore.py --test    # ثم يشغّل الـsuite ويطلب OK

عقد العمل مع هذا المشروع (ثوابت المالك — لا تُنقض إلا بقرار صريح منه):
  1) صفر Kotlin. 2) لا بناء APK إطلاقًا: يُسلَّم مصدر + زيبات فقط.
  3) طبقة التحجيم = مستطيل محتوى اللوحة (شريط أدوات+اقتراحات+مفاتيح) بالضبط، أي فرع
     fullScreen/fullSheet محظور حتى بالاسم، ولا مناطق فارغة داخل الكيبورد أبدًا.
  4) المقبض يُثبَّت على خط أعلى المحتوى الحقيقي (مسح tool_bar/tool_bar_scroll/keyboard)،
     والكبسولة أيقونات فقط. 5) القناة الحية: onComputeInsets بوسيط واحد +
     changeHeightSmoothly، الحفظ prefs عند الإفلات فقط.
  6) قاعدة «بدون زيادة الارتفاع»: تبديل صف الأرقام يعيد توزيع نفس الكتلة على 4/5 صفوف.
  7) بعد كل تعديل: python3 -m unittest discover -s tests -q يجب أن ينتهي OK
     (144 اختبارًا يحمي كل ما فوق). التفاصيل الكاملة: SwiftKeyIME/NEWCHAT_CONTEXT.md
     وREADME.md (تذييله = تاريخ الجولات).
"""
'''

RUNNER = '''
import base64, hashlib, io, os, subprocess, sys, zipfile

def main():
    args = set(sys.argv[1:])
    data = base64.b64decode(PAYLOAD)
    got = hashlib.sha256(data).hexdigest()
    if got != SHA256:
        sys.exit("BROKEN-PACKAGE sha256 mismatch: %s" % got[:16])
    if os.path.isdir("SwiftKeyIME") and os.listdir("SwiftKeyIME") and "--force" not in args:
        sys.exit("SwiftKeyIME/ موجود وغير فارغ — احذفه أو أضف --force (تحذير: سيُبْسَط فوقه)")
    zipfile.ZipFile(io.BytesIO(data)).extractall(".")
    n = sum(len(fs) for _, _, fs in os.walk("SwiftKeyIME"))
    print("RESTORED ok sha256:%s... files=%d" % (got[:16], n))
    if "--test" in args:
        r = subprocess.run([sys.executable, "-m", "unittest", "discover", "-s", "tests", "-q"],
                           cwd="SwiftKeyIME", capture_output=True, text=True)
        tail = ((r.stdout or "") + (r.stderr or "")).strip().splitlines()[-1:]
        print("SUITE:", tail[0] if tail else "(no output)", "| rc=", r.returncode)
        if r.returncode != 0:
            sys.exit("الـsuite حمراء بعد فكّ الحزمة — أبلغ المالك فورًا ولا تبنِ عليها.")
    print("NEXT: اقرأ SwiftKeyIME/NEWCHAT_CONTEXT.md قبل أي تعديل.")

if __name__ == "__main__":
    main()
'''


def walk_root(base, arc_prefix):
    entries = []
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
        rel = os.path.relpath(dirpath, base).replace(os.sep, "/")
        for f in sorted(filenames):
            if f.endswith((".pyc", ".zip")):
                continue
            src = os.path.join(dirpath, f)
            entries.append((src, arc_prefix + "/" + (f if rel == "." else rel + "/" + f)))
    return entries


def collect():
    # the suite's layout-contract tests read keyboard-preview/ as a SIBLING of SwiftKeyIME/
    # (tests/test_layouts_contract.py: PREVIEW = ROOT.parent / "keyboard-preview"), so the
    # restore package must carry both trees side by side or the green suite turns red abroad.
    entries = walk_root(ROOT, "SwiftKeyIME")
    preview = os.path.join(WORKSPACE, "keyboard-preview")
    if os.path.isdir(preview):
        entries += walk_root(preview, "keyboard-preview")
    return sorted(entries, key=lambda t: t[1])


def main():
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for src, arc in collect():
            z.write(src, arc)
    data = buf.getvalue()
    sha = hashlib.sha256(data).hexdigest()
    b64 = base64.b64encode(data).decode("ascii")
    script = (PREAMBLE + "\nSHA256 = %r\nPAYLOAD = \"\"\"\\\n%s\"\"\"\n" % (sha, b64) + RUNNER)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(script)
    os.chmod(OUT, 0o755)
    print("WROTE", OUT, os.path.getsize(OUT), "bytes  payload-sha256:" + sha[:16])


if __name__ == "__main__":
    main()
