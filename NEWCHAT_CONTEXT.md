# سياق الاستهلال — مشروع «كيبورد الملك» (AlmlkKeyboard)
> هذا الملف هو ذاكرة المشروع الدائمة. يُقرأ كاملاً قبل أي تعديل في محادثة جديدة.
> هذه عقوده وحقائقه المضمونة — لا تُنقض أي فقرة إلا بتعليمية صريحة من المالك.

## الحالة الحالية (بعد جولة 69)
- **آخر تسليم: `SwiftKeyIME-round69.zip`** — 620 ملفاً.
- الاختبارات: **557/557 OK** (منها test_round69.py بـ25 حارساً) و**javac كامل صفر أخطاء (464 class)** وThematyZipSelfTest **17/17 حية** (يفحص themes.json عبر الرابط).
- `AlmlkKeyboard-restore.py` محدَّث لجولة 69 — تحقق `--test` سليم.
- بانتظار نتيجة بناء المالك لجولة 69 في AIDE.
- قاعدة المساحة الرشيقة: في /home/user يبقى **زيب الجولة الأخيرة + round55.zip + restore.py فقط** (حُذفت 57–68).
- **/tmp يرتد لحالة قديمة عند كل رسالة مستخدم جديدة** — بيئة البناء تُعاد بسرعة عبر `bash /home/user/tools/rebuild_build_env.sh` (ينزّل android.jar+androidx ويولّد R.java بـ`tools/gen_r.py`).

## الهوية والبنية
- لوحة مفاتيح أندرويد عربية، الحزمة `com.almlk.swiftkey`، **صافي جافا بلا Kotlin ولا Lambda** (Thread+Handler)، الجذر `SwiftKeyIME/`، واجهة عربية بالكامل، **بلا build.gradle** — البناء عند المالك في AIDE (لا نُنتج APK إطلاقاً).
- **versionCode 36** في AndroidManifest.xml (هذا مرجع النسخة، لا يوجد build.gradle).
- المحرك: AlmlkImeService + سلسلة Runtime (AlmlkImeRuntimeBase + Part1..Part5 + ServiceCore).
- الرسم والقياس: SmartKeyboardView وحده مصدر معادلة الارتفاع `rows*(key+gap)+8dp`.
- طبقة التحجيم: KeyboardResizeOverlay (OnTouchListener وحده نموذج اللمس كله) + KeyboardResizeModel (clamp 28..96dp، حفظ prefs عند الإفلات فقط).
- القناة الحية الرسمية: onComputeInsets **بوسيط واحد** (لا ثنائي الوسائط — ثابت ومثبت) + changeHeightSmoothly + bindInsetsToPanel.
- الإعدادات: 20 شاشة + محركات (SuggestionEngine/WordComposer/TextNormalizer/GestureTrace/DictionaryDb/LocalTranslator/حافظة/إيموجي/ستيكرز/اختصارات) + سمات KeyboardTheme/ThemeRepository + تشخيص ErrorTracker/ErrorLogActivity.
- تخطيطات res/xml (19): عربي 6 مدارس + qwerty/qwertz/qzerty/azerty/colemak/dvorak + رموز×2 + أرقام + هاتف + أفعال + popup + method.xml.

## قيود صارمة سارية (خرقها = رفض)
1. لا APK — مصدر + زيب فقط. البناء عند المالك في AIDE.
2. صفر Kotlin وصرح Lambda — حارس اختبار دائم. لا حذف لصورة المقبض (kbd_resize_grip.png) ولا لشريط الأدوات.
3. طبقة التحجيم = مستطيل محتوى اللوحة (أدوات + اقتراحات + مفاتيح) بالضبط؛ أي فرع «fullSheet» محظور حتى بالاسم (grep -c fullSheet على KeyboardResizeOverlay.java = 0)؛ لا شريط فارغ داخل الكيبورد.
4. الكرة والشريط والخط المنقّط على **خط أعلى المحتوى الحقيقي** (أدنى قمة بين tool_bar وtool_bar_scroll وkeyboard)؛ القياس يفضّل main_keyboard_frame من الشجرة على targetView.
5. السحب حي وسلس: كل MOVE رسم صافٍ + reportLiveRow مجمَّع، الحفظ عند الإفلات فقط، الكبسولة أيقونات فقط (↺ | ✓).
6. «بدون زيادة الارتفاع»: صف الأرقام يعيد توزيع نفس الكتلة على 4/5 صفوف (blockAnchorRows/blockAnchorKeyPx + حارس layout!=null && layout.rows!=null).
7. ممنوعات نصية: applyKeyboardLayoutParams، onComputeInsets(.*,.*touched.*)، أسماء مثل sync*/Grow*.
8. ج69: لستات أقسام الأزرار والخلفيات **بطاقات أفقية صغيرة أنيقة** (frameCardHeight = width×0.63+18dp، الفن FIT_CENTER بشكله الحقيقي)؛ شاشة الثيمات كلها شرائط أفقية (سماتي/الافتراضية/أقسام المكتبة) بنفس تصميم بطاقة item_theme_strip؛ بوابة onCreate للأنشطة؛ الأنشطة الثلاث (الثيم/الخلفية/المخصص) final.
9. بعد كل تعديل: pytest كاملة OK، ثم زيب بفحص سلامة، فتحقق معزول في /tmp (مع keyboard-preview مجاوراً)، ثم tools/make_restore.py وتحقق `--test`، ثم تسليم الزيب كملف تنزيل. حدّث README (قسم الجولة) وPROJECT_FILES.txt.
10. رسائل قصيرة وأقل عدد استدعاءات؛ تعديلات النصوص بـpython مع assert count==1.
11. استثناءات PROJECT_FILES/الزيب: `__pycache__ .pytest_cache .git build .gradle keys .arena .cache`.

## عقود الجولات الحديثة (55–69)
- **69 — الزر الحقيقي والتطبيق الشامل وإصلاح الانهيار:** (1) **إصلاح انهيار الاستوديو**: fontPresets ضاع من bindViews في إعادة هيكلة ج68 → NPE فوري عند الفتح — القاعدة: كل حقل حاوية يُستخدم في rebuildControls يجب أن يظهر في bindViews (حارس test_round69). (2) **شكل الزر الحقيقي بلا زوائد**: KeyArtProcessor — الفن الجاهز (زوايا شفافة=زر مشكَّل) لا يُقصّ إطلاقاً (fitSize تحديد حجم فقط)؛ المعتم يُقصّ لنسبة المفتاح الحقيقية **w/h=1/KEY_ASPECT** (الباج بKEY_ASPECT نفسها h/w كان انقلاب دلالة يشطب زخرفة حدّي زر 448×616). SmartKeyboardView: زر الإنترنت يُرسم **fitCenterRect بنسبته الأصلية** داخل خانة الوجه (بلا مطّ/تقطيع — المسطرة زر واحد متمركز)؛ frameTextColour تفوّض لKeyArtProcessor.frameTextColor المشتركة. (3) **مقاييس الزر**: keyWidthScale/keyHeightScale (0.4..1) حقلا KeyboardTheme (+withKeySize، باني 25 معاملاً، حفظ/تحميل) + شريطا «عرض الزر/ارتفاع الزر» في قسم الخط (40..100٪، إفلات→persistNow) + keyFaceRect للإطارات والوجه الكلاسيكي (الجلد يبقى ممتلئاً) + ضمن themeSignature الحي. (4) **التطبيق على كل العناصر**: ThemeChipArt (جديد) — شارات الاقتراحات تحمل فن الزر (قصّ متمركز بزوايا مدورة، كاش مدمج) ولون حروفها/عناصر الأدوات/المعاينة = ThemeChipArt.textColor (لون حروف الزر)؛ اختيار زر (مدمج/أونلاين) يضبط text=لون حروف الزر وsub مشتقاً (renderBuiltinFrames/applyOnlineFrame). (5) **بطاقات أنيقة**: أفقية صغيرة، فن FIT_CENTER بشكله الحقيقي. (6) **شاشة الثيمات**: الافتراضية شريط أفقي (default_strip، بلا GridView/ThemeAdapter) بنفس بطاقات سماتي؛ بطاقات مكتبة ثيماتي **كيبوردات مصغّرة** (ThemeThumbnailView.setTheme(libraryPreviewTheme)+setFrameArtOverride من الكاش المتوازي) لا صور فارغة؛ ThemeThumbnailView يرسم فن الزر fit-center بلون حروفه؛ أقسام التحميل بترويسات بيضاء 15sp وأشرطة 152dp كسماتي. (7) أدوات جديدة خارج الزيب: /home/user/tools/rebuild_build_env.sh + gen_r.py (إعادة بناء بيئة javac بعد محو /tmp). **الدرسان: انقلاب دلالة النسبة (h/w مقابل w/h) يشوه بصمت؛ وكل حقل findViewById يجب أن يُعيَّن قبل أول استخدام — الاختبارات الساكنة لا تكشفه.**
- **55 — المتجر:** OnlineAssetStore: API كومنز `action=query&generator=search&gsrnamespace=6&gsrlimit=18&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=1024` + USER_AGENT `AlmlkKeyboard/1.9 (Android keyboard; custom themes)`؛ التحميل من thumburl (1024px)؛ smallThumb يعيد كتابة `/NNNpx-` إلى 320px؛ فلترة jpeg/png؛ MAX_BYTES 8MB؛ محاولتان مع 1.5s بينهما؛ صفر نتائج ≠ خطأ اتصال؛ النتائج dp88.
- **56 — تفاعل الضغط:** pressFx (نوع int + رابط String) في KeyboardTheme والاستوديو.
- **57 — شبكات:** 3 أعمدة في كل اللستات؛ بطاقات استعلام dp72؛ بطاقات لا شريط أفقي؛ بوابة onCreate.
- **58 — الفاصلة:** مفتاح «،» نقرة=فاصلة، مطول=الحافظة.
- **59 — اللستات الموحدة والفورية:** `onlineFrameQueries=framePresets; onlineFrameGrid=framePresets; pressFxQueries=pressFxPresets; pressFxOnlineGrid=pressFxPresets; onlineQueries=onlineGrid`؛ مُصيّر موحد: مدمجة dp(84) → addListHeader بspec(0,3) → استعلامات dp(72) → ملاحظة حالة/نتائج dp(88)؛ العرض=(w−24−16)/3؛ تفويض build*Queries/render* → build*Presets (بلا addView في أجسامها)؛ الحالات: تحميل «جارٍ تحميل مكتبة التحميلات…»، بحث «جارٍ البحث في الموقع…»، خطأ «تعذر الاتصال بالموقع (shortError) — انقر للإعادة»، «لا نتائج — جرّب عنواناً آخر»؛ **الفوري**: persistNow(){title من name (افتراضي «سمة مخصصة»)، save، setTheme} ≥10 استدعاءات؛ **المشغّل**: استماع حي ل prefs "custom_themes" (register/unregister)؛ themeSignature يشمل `31*result+theme.pressEffect` و`+theme.pressEffectUri.hashCode()`؛ **AppGate.connected()**: SDK23+ NetworkCapabilities (NET_CAPABILITY_INTERNET، null→legacy، caps==null→true «لا نزعج المتصل»)، <23 getActiveNetworkInfo.
- **60 — ثلاثة مصادر مجانية بلا مفاتيح:** search: كومنز ← Openverse ← Wallhaven (أول مصدر يُخلف نتيجة يكفي؛ الخطأ فقط إن فشلت الثلاثة). `API_OPENVERSE="https://api.openverse.org/v1/images/?page_size=18&q="` و`API_WALLHAVEN="https://wallhaven.cc/api/v1/search?categories=100&purity=100&sorting=relevance&atleast=512x512&q="`. المحللات: searchCommons (الأصلي حرفياً)، searchOpenverse (results[]: thumbnail+url، بادئة ov-، عنوان فارغ→«صورة حرة»)، searchWallhaven (data[]: path+thumbs.small، بادئة wh-، «خلفية + الدقة»). البدائل بـgetOnce (قراءة واحدة) — والمصدر الأساسي بمحاولتين. لا apikey/client_id إطلاقاً. (المواقع للتصفح: commons.wikimedia.org وopenverse.org وwallhaven.cc — الثلاثة متحقق منها حياً.)
- **68 — إعادة تصميم الثيمات والتصميم المخصص:** الاستوديو **4 أقسام حصراً** (الخلفيات/Auto=التفاعلات/الأزرار/الخط) — قسم الأزرار تراكب ثابت `section_keys_fixed` فوق `content_scroll` (لا ScrollView داخل ScrollView): شريط الشفافية + شريط رقائق الأقسام (الأشكال/المدمج/أقسام ثيماتي/↻) **ثابتان في الأعلى** و`button_elements_grid` (4 أعمدة) تتمرر تحتهما؛ `renderButtonElements()` يوزّع حسب `buttonsSection`. الخلفيات: شريط ألوان (يسقط الصورة) + المتحركة + خلفيات المكتبة (رقائق+شبكة 3). الخط: + شريطا لون نص المفاتيح والحروف الصغيرة. **الثيمات من themes.json** (المصدر الوحيد — أُلغي theme-sets.json): قسم «كل الثيمات» في واجهة الثيمات، كل قسم شريط أفقي RTL، بطاقة بألوان الثيم + شارة ↓/✓، النقر يثبّت (tb-/tg-) ثم يبني KeyboardTheme بألوانه ويطبّق فوراً. **السرعة**: LruCache صور 16MB في ThematyStore + `newFixedThreadPool(4)` بدل خيط لكل طلب؛ previewSource يسلّم من الكاش فوراً. **لا بقايا تحميلات**: removeAllViews قبل كل رسم + حرّاس تجاهل (onlineFrameSet/designerBgSection/allThemesGeneration + strip.getChildCount>0). **المنزلقات تطبّق عند الإفلات** (onStopTrackingTouch→persistNow). حُذفت: أقسام الألوان/التأثيرات/الصوت، background_hue/key_hue، ثيمات ثيماتي من الاستوديو (انتقلت لواجهة الثيمات).
- **67 — مكتبة ثيماتي مصدراً وحيداً:** الزيب (9.9MB على GitHub raw) يُقرأ عن بُعد بطلبات Range فقط (ذيل ~70KB للفهرس + ملفات الفهارس ~0.3MB مرة بالجلسة)؛ العنصر المختار يُجلب وحده ويُحفظ داخلياً. ZipRangeReader (جافا نقية، قابل للاختبار بtools/ThematyZipSelfTest — 14/14 حية) + ThematyStore (أقسام buttons/effects/theme-sets) + OnlineAssetStore صار ذاكرة فقط (saveBytes). **أُزيلت الطريقة القديمة كلياً**: بحث كومنز/Openverse/Wallhaven + DownloadCatalog + assets/theme_downloads + FALLBACK queries. الأزرار→تبويب المفاتيح، التفاعلات→تاغاتها، الخلفيات→المنتقي، والثيمات الكاملة (زر+خلفية) حلّت محل الحزم في تبويب تلقائي. **الدرس: raw.githubusercontent يدعم Range (206) — يمكن قراءة ZIP عن بُعد بلا تنزيله.**
- **66 — أعمدة شبكة الإطارات:** انهيار دخول الاستوديو `column indices (start + span) mustn't exceed the column count` سببه تعارض XML(3 أعمدة لframe_presets) مع كود ج62 (امتداد 4 + عرض ÷4). الإصلاح: XML=4 + اشتقاق كل امتدادات الصف الكامل من `getColumnCount()` عبر Math.min (addListHeader + onlineFrameNote) + تحديث 4 حرّاس قدامى كانت تحرس 3 القديمة. **الدرس: عند تغيير عدد الأعمدة حدّث XML والكود والحرّاس معاً.**
- **65 — ضبط final (انهيار واجهة التصميم):** NoClassDefFoundError لCustomThemeActivity سببه التقاط غير final في الأصناف المجهولة (r63: art/frame) — AIDE يعمل بقواعد جافا 7 فيبني APK ناقصاً. **القاعدة الدائمة: كل متغير محلي يُستخدم داخل صنف مجهول = final صراحةً** والتحقق الرسمي بـ `javac -source 7 -target 7` (= قواعد AIDE) — ومدقق `tools/final_capture_audit.py` (مسح خطي بنطاق الدالة) حارس دائم في test_round65.
- **64 — إصلاح سجل الأخطاء:** frameTextColour: قيوس Math.min(w-1/h-1) + try/catch → 0xff172033 (كان y=2 يعطي getHeight() خارج الحدود — انهيار كل إطار إنترنت). **صفر recycle() في SmartKeyboardView وThemeThumbnailView** (كان يكسر الرسم العتادي وعند إعادة توصيل العرض) — الإسقاط والGC فقط + حارس `themeImage.isRecycled()` في drawThemeBackground (معاد تدويرها = خلفية لون). المانيفست يقفل INTERNET وACCESS_NETWORK_STATE (بنيات قديمة كانت بلا الثانية → SecurityException) وAppGate كله catch(Throwable).
- **63 — أدلة التحميل وضبط التجمّد:** شارة موحدة `OnlineAssetStore.statusBadge(ctx, stored, applied, appliedBg, appliedText, sizePx)`: «↓» أزرق 0xff168fe5 لغير المحمَّل، «✓» أخضر 0xff2e9e4f للمحمَّل مسبقاً، «✓» بلون التطبيق للمطبَّق — في الإطارات/التفاعلات (كهرماني 0xffffd51a/0xff202124/dp18) والمنتقي (أزرق 0xff168fe5/أبيض/dp24). حلقة تحميل `loadingRing` (ProgressBar indeterminate dp34 وسط الفن) تُضاف عند النقر وتُزال عند الانتهاء + حارس `instanceof android.widget.ProgressBar) return;` ضد التحميل المزدوج. **ضبط التجمّد**: `warmArt(type, path)` على خيط التحميل قبل postDownload — fileKeyArt للإطارات و`fileSprite` (≤256px، كاش sprite|) للتفاعلات؛ المحرك: `pressSprite = KeyArtProcessor.fileSprite(pressEffectUri)` (لا decodeFile خام)؛ الأنشطة والكيبورد عملية واحدة (لا android:process) فالكاش المسخَّن يصل للمحرك فوراً.
- **62 — الأزرار الحقيقية والربط بالممتلكات:** كل إطار محمّل يُعالَج عبر KeyArtProcessor — نفس خط بيانات الممتلكات: `fileKeyArt(String)` (فكّ ملف محدود الذاكرة + قصّ 0.63 + معالجة زر ثلاثية الأبعاد: حواف مدورة/لمعة/عمق/حافة مضيئة؛ الجاهز الشفاف الزوايا يمر كما هو) و`shapeButton(Bitmap)` للمعاينات في البطاقات (ScaleType.CENTER لا CROP). الرسم على المفاتيح: ممطوط على وجه الزر كالمدمج (لا BitmapShader) والمسطرة بتقطيع ثلاثي والضغط غلالة داكنة بإزاحة. `buttons.json` إصدار 2 باستعلامات أزرار حقيقية (button icon / round button icon / gold button / key icon / ornate frame / decorative border + خامات تُشكَّل أزراراً). لستة الإطارات: 4 أعمدة (العرض=(w−24−30)/4) وبطاقات طولية `frameCardHeight(width)=Math.round(width/KeyArtProcessor.KEY_ASPECT)+dp(20)` للمدمجة والعناوين والنتائج؛ الفاصل وملاحظات الإطارات span 4 (`addListHeader(grid,title,columns)`)؛ التفاعلات والمنتقي بقوا 3 أعمدة dp84/72/88 كما هما.
- **61 — إصلاح خطأ البناء:** الباني الرئيسي KeyboardTheme صار 23 معاملاً (…, skin, frame, frameUri, effect, effectUri) منذ 51/56 بينما استدعاءا التفويظ ظلا يمرران 21 → أُكملا: `..., skin, 0, "", 0, "");` و`..., skin, frame, "", 0, "");`. وSmartKeyboardView سطرا 608/611: `dp((float) (54 + Math.random() * 48))` و`dp((float) (58 + Math.random() * 60))`. حرّاس جدد: كل this(...) يطابق عدد معاملات بانيٍ في ملفه؛ ولا Math.random داخل dp() بلا صرْح (float).

## وصفة التحقق بجافاك (جديدة من 61 — استخدمها بعد كل تعديل جافا)
1. android.jar: `curl -o p.zip https://dl.google.com/android/repository/platform-33_r02.zip` ثم فك `*/android.jar`.
2. androidx من `https://dl.google.com/android/maven2/...` (استخرج classes.jar من كل AAR): appcompat-1.6.1, recyclerview-1.3.0, core-1.9.0, fragment-1.5.7, activity-1.6.1, lifecycle-{runtime,viewmodel,livedata-core,viewmodel-savedstate}-2.5.1, lifecycle-common-2.5.1(jar), savedstate-1.2.0, annotation-1.5.0(jar), vectordrawable-1.1.0, vectordrawable-animated-1.1.0, customview-1.1.0, interpolator-1.0.0, arch-core-common-2.1.0(jar), startup-runtime-1.1.1, drawerlayout-1.1.1. (versionedparcel ليس لازماً.)
3. ولّد R.java (حزمة com.almlk.swiftkey) من res/: layouts (أسماء الملفات)، ids من `@\+id/` في layouts + `<item type="id">`، string/color/dimen/array/style من values*. مراجع `android.R.*` سليمة لا تُحسب.
4. `javac -encoding UTF-8 -source 7 -target 7 -nowarn -cp android.jar:ax/*.jar -d out $(find app/src/main/java -name '*.java') gen/.../R.java`
5. النتيجة المضمونة حتى 68: **صفر أخطاء، 459 class من 91 ملفاً** — والمصدر 7 (لا 8) إلزامي: يحاكي قواعد AIDE (الالتقاط غير النهائي خطأ). **انتبه: fragment-1.5.7.jar وليس 1.5.5**، وكعب R في /tmp/build67/gen (403 رمزاً — يُجدَّد بمسح مراجع R.x.y في جافا عند إضافة معرفات).

## تصميم مرجعي
- بطاقات استعلام المنتقي: 0xffcfe8fb / حافة-نص 0xff0b5a8f / زاوية 0xff168fe5، زوايا dp(7).
- بطاقات استعلام الاستوديو: 0xffffe9a8 / 0xff8a5a00 / 0xffffc400، زوايا dp(12).
- البطاقات المدمجة: بيضاء، زوايا dp(12)، حافة 0xffd8dce3 / مختارة 0xffffc400.

## أخطاء ومآزق — لا تُكرر
- **لا تُعِد كتابة شبكة OnlineAssetStore** — تعمل حياً؛ مشكلة المالك كانت `&amp;` من نسخ HTML، وحُلت بالبدائل الثلاثة (60).
- SettingsActivity بلا EXTRA_BACKGROUND — لا مسار تطبيق خارج الاستوديو (معروف ومقبول).
- أدوات تحرير النص بـpython: method_body يقصّ السطر الأخير `}`؛ replace_method لا يشمل المُغلق النهائي؛ عند فشل تأكيد تحرير اطبع النص الفعلي أولاً (سبب فشل سابق: بانيان في tuple لا واحد)؛ دائماً assert count==1.
- لقطات المالك: تُقرأ بـOCR عبر `pip install rapidocr-onnxruntime` (يعمل بلا صلاحيات root) — أبقى النص والأسطر الحمراء تُرصد بمسح البكسلات (r>170,g<110,b<110).
- الخطة 1548 مرفوضة قديماً؛ لا تجرب onComputeInsets ثنائي الوسائط.

## خريطة مساحة العمل (/home/user)
- `SwiftKeyIME/` — المشروع (618 ملفاً، 532/532). README فيه أقسام كل الجولات (آخرها 68). PROJECT_FILES.txt = الجرد (618).
- `SwiftKeyIME-round68.zip` — **التسليم الحالي**. `SwiftKeyIME-round55.zip` — يُبقى (قاعدة المساحة).
- `AlmlkKeyboard-restore.py` — مولّد الاستعادة (68). `AlmlkKey.zip` — المصدر الأصلي (جولة 43).
- `keyboard-preview/` — لازم مجاوراً للتحقق المعزول. `tools/make_restore.py` — مولد المولد.
- `ROUND51..57_REPORT.md` — تقارير تاريخية. `uploads/` — لقطات المالك.
- الملف المعتمد للسياق: هذا الملف (النسخة في جذر /home/user مجرد مؤشر).

## بروتوكول الجولة القادمة
1. اقرأ هذا الملف كاملاً + آخر قسمين في README.
2. نفّذ طلب المالك بأقل تعديلات؛ لا Lambda؛ عربية UI؛ versionCode 36 ثابت.
3. `python3 -m pytest tests/ -q -p no:cacheprovider` → OK كاملة.
4. **javac كامل** (الوصفة أعلاه) → صفر أخطاء.
5. README قسم الجولة + PROJECT_FILES.txt ثم زيب + تحقق معزول + make_restore + `--test`.
6. قدّم الزيب برسالة قصيرة (جدول: ملفات/حجم/sha256/اختبارات).
7. احذف زيبات الجولات الوسيقة (يبقى الأخير + 55 + restore.py).
