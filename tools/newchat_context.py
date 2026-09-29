#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يولّد ملف استهلال المحادثة الجديدة (NEWCHAT_CONTEXT.md) من الشجرة الحالية.
شغّله من جذر المشروع:  python3 tools/newchat_context.py
يشمل: هوية المشروع، القيود الصارمة، البنية، الحقائق التقنية المضمونة، حالة آخر
جولة، وبصمات التسليم — فيُقرأ ويلصق كاملة في بداية أي محادثة جديدة مع الزيب.
"""
import hashlib
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
WORKSPACE = os.path.abspath(os.path.join(ROOT, ".."))


def sha16(path):
    try:
        with open(path, "rb") as f:
            return "sha256:" + hashlib.sha256(f.read()).hexdigest()[:16]
    except OSError:
        return "غير موجود"


def count_files():
    total = 0
    for _, _, files in os.walk(ROOT):
        total += len([f for f in files if not f.endswith((".pyc", ".zip"))])
    return total


def gradle_version():
    try:
        g = open(os.path.join(ROOT, "app/build.gradle"), encoding="utf-8").read()
        vc = re.search(r"versionCode\s+(\d+)", g)
        vn = re.search(r"versionName\s+'([^']+)'", g)
        return "vc%s / %s" % (vc.group(1) if vc else "?", vn.group(1) if vn else "?")
    except OSError:
        return "؟"


def test_state():
    try:
        r = subprocess.run(
            [sys.executable, "-m", "unittest", "discover", "-s", "tests", "-q"],
            cwd=ROOT, capture_output=True, text=True, timeout=120)
        out = (r.stdout or "") + (r.stderr or "")
        n = re.search(r"Ran (\d+) tests", out)
        return ("%s اختبار، %s" % (n.group(1), "OK ✅" if "OK" in out else "أحمر ❌ — أصلح قبل أي طلب")) if n else "لم تُشغَّل"
    except Exception:
        return "لم تُشغَّل (شغّل يدويًا: python3 -m unittest discover -s tests -q)"


def last_readme_sections(k=3):
    try:
        rd = open(os.path.join(ROOT, "README.md"), encoding="utf-8").read()
        secs = re.findall(r"^## (.+)$", rd, re.M)
        return "\n".join("  - " + s for s in secs[-k:]) or "  - ؟"
    except OSError:
        return "  - ؟"


def kotlin_count():
    total = 0
    for _, _, files in os.walk(ROOT):
        total += len([f for f in files if f.endswith(".kt")])
    return total


CORE = """# سياق الاستهلال — مشروع «كيبورد الملك» (AlmlkKeyboard)
> الصق هذا الملف كاملًا في أول رسالة من محادثة جديدة + أرفق AlmlkKeyboard-Complete.zip.
> هذه عقوده وحقائقه المضمونة — لا تُنقض أي فقرة منها إلا بتعليمية صريحة مني أنا (المالك).

## الهوية والبنية
- تطبيق لوحة مفاتيح أندرويد عربية، الحزمة com.almlk.swiftkey، **صافي جافا بلا أي Kotlin**،
  الجذر SwiftKeyIME/، بواجهة إعدادات اسمها الشاشات الرئيسية «كيبورد الملك».
- المحرك: AlmlkImeService + سلسلة Runtime (AlmlkImeRuntimeBase + Part1..Part5 + ServiceCore).
- الرسم والقياس: SmartKeyboardView وحده مصدر معادلة الارتفاع rows*(key+gap)+8dp.
- طبقة التحجيم: KeyboardResizeOverlay (OnTouchListener وحده model اللمس كله) + KeyboardResizeModel
  (clamp 28..96dp وحفظ prefs عند الإفلات فقط).
- القناة الحية الرسمية: onComputeInsets بوسيط واحد (android-30 لا ثنائي الوسائط إطلاقًا —
  ثابت ومثبت من constant-pool، لا تجرب مجددا) + changeHeightSmoothly + bindInsetsToPanel.
- إعدادات (20 شاشة) + محركات: SuggestionEngine/WordComposer/TextNormalizer/GestureTrace،
  قاموس DictionaryDb، ترجمة LocalTranslator، حافظة/إيموجي/ستيكرز/اختصارات/لوحات،
  سمات KeyboardTheme/ThemeRepository، تشخيص ErrorTracker/ErrorLogActivity.
- تخطيطات res/xml (19): عربي 6 مدارس (pc/102/mac/azerty/original/أساسي مطابق xkb الرسمي)،
  qwerty/qwertz/qzerty/azerty/colemak/dvorak، رموز×2، أرقام، هاتف، أفعال، قالب popup، method.xml.

## قيود صارمة سارية (خرقها = رفض)
1. لا إنشاء APK إطلاقًا — يُسلَّم مصدر + زيبات فقط. البناء عندي في AIDE.
2. صفر ملفات Kotlin (حارس اختبار). لا حذف لصورة المقبض (kbd_resize_grip.png) ولا لشريط الأدوات.
3. طبقة التحجيم = مستطيل محتوى اللوحة (شريط أدوات + اقتراحات + مفاتيح) بالضبط؛ أي فرع
   «ملء الشاشة/fullSheet» محظور وجوده حتى بالاسم؛ لا شريط/منطقة فارغة داخل الكيبورد أبدًا.
4. الكرة والشريط والخط المنقّط تُثبَّت على **خط أعلى المحتوى الحقيقي** (مسح حي:
   أدنى قمة بين tool_bar وtool_bar_scroll وkeyboard) — لا على أعلى المفاتيح ولا فوق اللوحة،
   ولا تُرسم أي خلفية معتمة خارج مستطيل اللوحة. القياس يفضّل main_keyboard_frame من الشجرة
   على targetView (مقاومة توصيلات قديمة).
5. السحب حي وسلس: كل خطوة MOVE رسم صافٍ + reportLiveRow مجمَّع (قيمة واحدة لكل تغيّر)،
   الحفظ prefs عند الإفلات فقط، الإلغاء يعكس كل شيء. الكبسولة أيقونات فقط (↺ | ✓) بلا نصوص.
6. قاعدة «بدون زيادة الارتفاع»: إظهار/إخفاء صف الأرقام يعيد توزيع نفس الكتلة على 4/5 صفوف
   (مرساة blockAnchorRows/blockAnchorKeyPx في SmartKeyboardView + حارس عدم التثبيت عند إطار
   بلا تخطيط layout!=null && layout.rows!=null).
7. ممنوعات نصية في الملفات: applyKeyboardLayoutParams، onComputeInsets(.*,.*touched.*)،
   أسماء دوال تُخفي حجمًا مثل sync*/Grow*.
8. بعد كل تعديل: python3 -m unittest discover -s tests -q يجب أن ينتهي OK، وأعد توليد
   الزيبات مع asserts داخلية، وحدّث README (قسم «تحديث …») وPROJECT_FILES.txt.
9. للتحقق من أن عندي النسخة الصحيحة: versionName في app/build.gradle (يرتفع كل جولة) +
   grep -c fullSheet على KeyboardResizeOverlay.java = 0.

## حقائق أدوات (لا تُعَد تجريبها من الصفر)
- فحص الجافا الكامل: يحتاج android-30 (platform-30_r03.zip، android.jar داخل */android.jar) +
  aapt2 (build-tools_r30.0.3) + مكتبات maven (appcompat 1.3.1/material 1.4.0/core 1.6.0/…)؛
  link ينجح فقط بعد aapt2 compile لكل res/ مستخرج من aars، والجافا تعمل بـ javac بفصل
  النقطتين للفهرس بينما R8/d8 يقبلان مسافات؛ javap غير مثبت — افحص الـconstant-pool بـpython.
- فتحزيم AIDE عند المالك قد يبني شجرة قديمة إن فُكّ الزيب متداخلًا: أي إصلاح يُسلَّم أيضًا
  كـ«باتش مسطّح» بملف واحد من جذر المشروع (نمط AlmlkKeyboard-PatchNN.zip).

## التسليم المتوقع في كل جولة
README محدّث + PROJECT_FILES.txt + زيبان (كامل + باتش مسطّح اختياري) — كلها مع بصمات sha256،
وبدون أي APK.
"""


def main():
    out = []
    out.append(CORE.strip())
    out.append("")
    out.append("## لقطة آلية من الشجرة (يولّدها tools/newchat_context.py)")
    out.append("- الإصدار: %s | ملفات المشروع: %d | ملفات .kt: %d" %
               (gradle_version(), count_files(), kotlin_count()))
    out.append("- حالة الاختبارات الآن: %s" % test_state())
    out.append("- أحدث أقسام README:")
    out.append(last_readme_sections())
    out.append("- بصمات التسليم: %s" % ", ".join(
        "%s(%s)" % (z.split(".")[0].replace("AlmlkKeyboard-", ""), sha16(os.path.join(WORKSPACE, z)))
        for z in ("AlmlkKeyboard-Complete.zip", "Floris-Resize-Java-Port.zip")
        if os.path.exists(os.path.join(WORKSPACE, z))) or "لم تُولَّد بعد")
    patches = sorted(f for f in os.listdir(WORKSPACE) if f.startswith("AlmlkKeyboard-Patch"))
    if patches:
        out.append("- باتش مسطّح حالي: %s (%s)" % (patches[-1], sha16(os.path.join(WORKSPACE, patches[-1]))))
    dst = os.path.join(WORKSPACE, "NEWCHAT_CONTEXT.md")
    with open(dst, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("WROTE", dst, os.path.getsize(dst), "bytes")


if __name__ == "__main__":
    main()
