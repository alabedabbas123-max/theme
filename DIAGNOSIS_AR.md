# تقرير تشخيص المشروع — AlmlkKeyboard (SwiftKeyIME)

تاريخ الفحص: 2026-09-29 · الفرع: `arena/01a0e0c1-theme`

---

## ١. ما تم استيراده

| البند | القيمة |
|---|---|
| مصدر الأرشيف | `alabedabbas123-max/theme` → `SwiftKeyIME.zip` (24.7 MB) |
| ملفات الأرشيف | 4245 |
| الملفات المستوردة | 626 (استُبعدت `app/build/` و`.gradle/` و`local.properties`) |
| ملفات Java | 94 · **25,782 سطر** |
| اختبارات Python | 48 ملفًا · **557 اختبارًا** |
| اسم مشروع Gradle | `AlmlkKeyboard` · الحزمة `com.almlk.swiftkey` |
| الإصدار | `versionCode 45` · `versionName 1.9-round45` |

---

## ٢. 🔴 ثغرة أمنية حرجة — أولوية قصوى

`local.properties` في الأرشيف كان يحتوي:

```
THEMATY_GITHUB_TOKEN=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
```

و`app/build.gradle` يحقنه في الـ APK:

```groovy
buildConfigField 'String', 'THEMATY_GITHUB_TOKEN', '"' + escapedThematyGithubToken + '"'
```

**لماذا هذا خطير:** أي رمز داخل `BuildConfig` يصبح نصًا صريحًا في `classes.dex`. من يملك الـ APK يستخرجه بـ `apktool` أو `strings` خلال ثوانٍ. رموز `ghp_` الكلاسيكية تمنح صلاحية `repo` الكاملة على **كل** مستودعاتك.

**ما فعلته:** استبعدت `local.properties` من الاستيراد، وأضفته إلى `.gitignore`. الرمز لم يدخل المستودع.

**ما يجب أن تفعله أنت:**
1. ألغِ الرمز فورًا: https://github.com/settings/tokens
2. ألغِ أيضًا `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`
3. لا تعد حقن أي رمز في `BuildConfig`. البديل: وسيط خادمي (proxy) يحمل الرمز، والتطبيق ينادي الوسيط.

---

## ٣. نتيجة الاختبارات

```
Ran 557 tests
FAILED (failures=43, errors=5, skipped=1)
```

### التصنيف

#### أ) 4 أخطاء — مجلد خارجي مفقود من الأرشيف

الاختبارات تبحث عن `../keyboard-preview/` بجوار جذر المشروع:

| الاختبار | الملف المطلوب |
|---|---|
| `test_layouts_contract.test_picker_page_mirrors_the_android_screen` | `layouts.html` |
| `test_layouts_contract.test_languages_page_links_the_picker...` | `languages.html` |
| `test_layouts_contract.test_live_preview_keyboard_honours_saved_variants` | `english-layout-v4.html` |
| `test_settings_wiring.test_preview_mirrors_digit_choice` | `languages.html` |

المجلد **غير موجود في `SwiftKeyIME.zip`** — لم أجد أي ملف `.html` في الأرشيف كله.
**ليس خطأ برمجيًا** — نقص في التحزيم. يلزم إضافة `keyboard-preview/` إلى الأرشيف أو نقله داخل المشروع.

#### ب) 5 إخفاقات — اختبارات قديمة تحرس `versionCode 36`

`test_build_config_untouched` في 5 ملفات يؤكد `versionCode 36`، بينما القيمة الفعلية `45`:

`test_resize_round39` · `test_resize_round39_2` · `test_suggestion_inline_guard` · `test_themes_round40` · `test_typed_echo_slot`

**تقادُم اختبارات، لا عيب في الكود.** الحل: تحديث الحارس ليقرأ الإصدار ديناميكيًا بدل ترميزه.

#### ج) 🟠 انحدار حقيقي — `ensureTrailingSpace` ميتة

في `app/src/main/java/com/almlk/swiftkey/engine/WordComposer.java`:

```java
/** Guarantees that the cursor is after a visible word boundary in restrictive editors. */
private static void ensureTrailingSpace(InputConnection connection) { ... }
```

الدالة **مُعرَّفة ولا تُستدعى في أي مكان** — تحققت بـ `grep` على كامل `app/src`.

الاختبارات تتوقع استدعاءها مرتين داخل `replaceCurrent`:
- `test_suggestion_inline_guard.test_replace_current_sanitizes_every_insertion`
- `test_settings_wiring.test_suggestion_choice_finishes_with_a_boundary`

**الأثر السلوكي:** ضمان الحد الفاصل بعد اختيار اقتراح فُقد. في المحررات المقيّدة قد تلتصق الكلمة المختارة بما بعدها. يبدو أن تعديل "Round 70" (الذي أزال `finishComposingText`) أسقط الاستدعاءين سهوًا.

**بالإضافة:** دالة `private` غير مستخدمة تولّد تحذير مترجم، وقد يحذفها بعض المحسّنات.

#### د) 🟠 انحدار — توقيع `applyOnlineFrame` تغيّر

`test_round69` يبحث عن `private void applyOnlineFrame(String path) {` ويرمي `ValueError`.
الموجود فعليًا في `CustomThemeActivity.java:2048`:

```java
private void applyOnlineFrame(String path, String spacePath)
```

أُضيف معامل `spacePath`. إمّا أن الاختبار يحتاج تحديثًا، أو أن العقد المقصود انكسر. يلزم قرار منك.

#### هـ) 🟠 تعارض عقد — `LayoutsSettingsActivity`

`test_layouts_contract.test_toolbar_tool_opens_the_in_keyboard_layouts_panel` يشترط ألا يظهر `LayoutsSettingsActivity` في `AlmlkImeRuntimePart2C.java` (المبدأ: منتقي التخطيطات يُفتح داخل اللوحة لا كشاشة خارجية).

لكن "Round 70" أضاف:

```java
} else if ("layouts_settings".equals(key)) {
  Intent intent = new Intent(this, com.almlk.swiftkey.settings.LayoutsSettingsActivity.class);
  ...
}
```

المفتاح `"layouts"` ما زال يفتح اللوحة الداخلية بشكل صحيح؛ المفتاح الجديد `"layouts_settings"` هو ما يخالف الحارس. الأرجح أن الحارس يحتاج تضييقًا لا الكود حذفًا.

#### و) 32 إخفاقًا متبقيًا — تحتاج فحصًا فرديًا

أكبر التجمعات:

| المجموعة | العدد | الملفات |
|---|---|---|
| تغيير الحجم (Resize) | 12 | `test_resize_bottom_lock` `test_keyboard_resize_drag` `test_resize_round39` `test_resize_round39_2` |
| الجولات 67–69 | 10 | `test_round67` `test_round68` `test_round69` |
| المتجر الإلكتروني | 3 | `test_online_store_round51` |
| إطارات المفاتيح | 2 | `test_key_frames_round50` |
| الصفوف العربية | 2 | `test_arabic_rows` |

---

## ٤. ملاحظات على إعداد البناء

| البند | القيمة الحالية | الملاحظة |
|---|---|---|
| Android Gradle Plugin | `4.1.3` | صدر 2021؛ لا يعمل مع JDK 11+ بسلاسة |
| Gradle Wrapper | **غير موجود** | لا `gradlew` ولا `gradle/wrapper/` — البناء غير قابل لإعادة الإنتاج |
| `compileSdkVersion` | 30 | |
| `buildToolsVersion` | `33.0.0` | أحدث من compileSdk — تركيبة غير معتادة |
| `targetSdkVersion` | **26** | Google Play يشترط 34+ منذ 2024؛ النشر مستحيل بهذه القيمة |
| `minSdkVersion` | 16 | يمنع كثيرًا من واجهات AndroidX الحديثة |
| `sourceCompatibility` | **Java 1.6** | مقصود للتوافق مع AIDE؛ يمنع lambdas وdiamond operator |
| AndroidX | `core:1.0.0` `appcompat:1.0.0` | إصدارات 2018 |
| `buildTypes` / ProGuard | محذوفة عمدًا | موثّق في `INSTALL_NOTES_AR.txt` |

`AndroidManifest.xml` سليم: الخدمة تحمل `BIND_INPUT_METHOD` و`android.view.InputMethod` و`meta-data` باسم `android.view.im` — كلها صحيحة.

---

## ٥. قيود بيئة العمل

متوفر: Python 3.11 · Node 22 · git · gh — **الاختبارات الـ557 تعمل هنا فورًا.**

غير مثبت: JDK · Gradle · Android SDK · Kotlin. يمكن تثبيت JDK 8 + `cmdline-tools` لبناء APK فعلي (~10 دقائق).
غير ممكن: محاكي أندرويد (لا KVM، 3.8 GB RAM).

---

## ٦. الخطوات المقترحة

1. **فورًا:** إلغاء الرمزين المكشوفين.
2. إزالة `THEMATY_GITHUB_TOKEN` من `BuildConfig` نهائيًا.
3. إصلاح `ensureTrailingSpace` — انحدار سلوكي حقيقي يمسّ تجربة الكتابة.
4. تحديث حرّاس `versionCode` لتقرأ ديناميكيًا (يُسقط 5 إخفاقات دفعة واحدة).
5. إضافة `keyboard-preview/` أو تعليم اختباراتها `skipUnless` (يُسقط 4 أخطاء).
6. فحص مجموعة Resize (12 إخفاقًا) — أكبر تجمّع مترابط.
7. إضافة Gradle Wrapper وتثبيت JDK لبناء APK حقيقي والتحقق بـ `javac`.
