# -*- coding: utf-8 -*-
"""مدقق الالتقاط النهائي — يحاكي قاعدة مترجم AIDE (جافا 7).

كل متغير محلي معرَّف خارج الأصناف المجهولة ويُستخدم داخلها يجب أن يُعلن
«final» صراحةً — وإلا رفضه مترجم AIDE (وضع جافا 7) وبنى APK ناقصاً
(NoClassDefFoundError) بينما يقبله جافاك 8 (effectively final).

ماسح خطي: تتبع أعماق الأقواس مرة واحدة، وجسم الصنف المجهول = قوس «{»
يسبقه «)» وفي عبارته «new». لا regex ارتدادي — لا انفجار ذاكرة.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def strip_comments(src):
    out = []
    i, mode = 0, "code"
    while i < len(src):
        c = src[i]
        nxt = src[i + 1] if i + 1 < len(src) else ""
        if mode == "code":
            if c == '"':
                mode = "str"
                out.append(c)
            elif c == "/" and nxt == "/":
                mode = "line"
                out.append("  ")
                i += 1
            elif c == "/" and nxt == "*":
                mode = "block"
                out.append("  ")
                i += 1
            else:
                out.append(c)
        elif mode == "str":
            out.append(c)
            if c == "\\":
                out.append(src[i + 1])
                i += 1
            elif c == '"':
                mode = "code"
        elif mode == "line":
            if c == "\n":
                mode = "code"
                out.append(c)
        elif mode == "block":
            if c == "*" and nxt == "/":
                mode = "code"
                out.append("  ")
                i += 1
            elif c == "\n":
                out.append(c)
        i += 1
    return "".join(out)


def scan_blocks(src):
    """مسح خطي واحد: كتل العمق-1 (دوال/أصناف داخلية) وأجسام الأصناف المجهولة."""
    depth1 = []  # (start, end, header)
    anon = []  # (start, end)
    stack = []
    statement_start = 0
    i = 0
    while i < len(src):
        c = src[i]
        if c == "{":
            head = src[statement_start:i].rstrip()
            is_anon = head.endswith(")") and re.search(r"\bnew\s+[\w.$]+\s*\(", head) is not None
            if len(stack) == 1:
                # مستوى الدالة: قوس يفتح مباشرة داخل جسم الصنف الأعلى
                depth1.append([i, -1, head[-160:]])
            stack.append([i, is_anon])
            statement_start = i + 1
        elif c == "}":
            if stack:
                open_idx, is_anon = stack.pop()
                if is_anon:
                    anon.append((open_idx, i))
                if len(stack) == 1 and depth1 and depth1[-1][1] == -1:
                    depth1[-1][1] = i
            statement_start = i + 1
        elif c == ";":
            statement_start = i + 1
        i += 1
    return depth1, anon


def mask_own_params(body):
    """إخفاء أسماء معاملات الدوال داخل الجسم — ليست التقاطاً بل تعريفات محلية."""
    return re.sub(r"\((?:final\s+)?[\w<>\[\].]+\s+(\w+)\s*(?:,|\))",
                  lambda m: "(" + ("x" * (len(m.group(1)) + 1)), body)


def method_header_ok(header):
    """دالة حقيقية: توقيع بأقواس وليس حقلاً ولا صنفاً داخلياً."""
    return "(" in header and ")" in header and " class " not in " " + header and "=" not in header


def declarations(src):
    """التعريفات المحلية/المعاملات مع موضعها وهل هي final."""
    decls = []
    for m in re.finditer(r"(?m)^\s*(final\s+)?[\w<>\[\].]+\s+(\w+)\s*=\s*new\s", src):
        decls.append((m.start(2) if m.group(2) else m.start(), m.group(2), m.group(1) is not None))
    # معاملات الدوال غير النهائية (سطر التوقيع)
    for m in re.finditer(r"(?m)^\s{2,6}(?:public|private|protected)[\w\s<>\[\],.]*?\(([^)]*)\)\s*\{", src):
        for p in m.group(1).split(","):
            p = p.strip()
            if not p or p.startswith("final ") or "=" in p:
                continue
            nm = re.search(r"(\w+)$", p)
            if nm:
                decls.append((m.start(), nm.group(1), False))
    return decls


def audit_file(path):
    src = strip_comments(path.read_text(encoding="utf-8"))
    depth1, anon = scan_blocks(src)
    if not anon:
        return []
    # الدوال فقط (لا الأصناف الداخلية ككتل) — نطاق الالتقاط
    methods = [(s, e) for s, e, h in depth1 if e != -1 and method_header_ok(h)]
    findings = []
    for pos, name, is_final in declarations(src):
        if is_final:
            continue
        if any(a <= pos <= b for a, b in anon):
            continue
        for ms, me in methods:
            if not (ms <= pos <= me):
                continue
            for a, b in anon:
                if pos < a <= me:
                    body = mask_own_params(src[a : b + 1])
                    if re.search(r"(?<![\w.])%s(?![\w(])" % re.escape(name), body):
                        line = src[:pos].count("\n") + 1
                        findings.append((line, name))
                        break
    return findings


def main():
    java_files = sorted((ROOT / "app/src/main/java").rglob("*.java"))
    total = 0
    for path in java_files:
        try:
            for line, name in audit_file(path):
                total += 1
                print("%s:%d: «%s» غير نهائي ومُلتقط داخل صنف مجهول" % (path.name, line, name))
        except Exception as error:
            total += 1
            print("%s: فشل التحليل (%s)" % (path.name, error))
    print("---- الإجمالي: %d مشكلة في %d ملفاً" % (total, len(java_files)))
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
