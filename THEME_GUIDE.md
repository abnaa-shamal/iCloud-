# دليل تغيير الشكل والصوت والاهتزاز — أبناء الشمال

هذا الملف لك أنت: كيف تغيّر شكل التطبيق أو صوته أو اهتزازه **بدون ما تخرّب البناء**.
كل المسارات داخل `app/src/main/java/jo/shajaraone/app/` إلا إذا ذُكر غير ذلك.

## 1) الملفات المسموح تعديلها

| الملف | وظيفته |
|---|---|
| `Theme.kt` | **الأهم.** الألوان (الكتلة `Brand` فوق الملف)، ألوان Material (`NightColors`)، الخطوط، زوايا الأشكال، الخلفية المتحركة (نجوم وكرات). |
| `Components.kt` | القطع المشتركة: الأزرار، البطاقات الزجاجية، الشرائح، مقاعد السيارة، بطاقة الرحلة، الشريط السفلي، ترويسة الصفحة. |
| `Logo.kt` | رسمة الشعار (جبلان وطريق ونجمة). |
| `Splash.kt` | شاشة البداية (ثانيتان)، اسم التطبيق `APP_DISPLAY_NAME`، عبارة الترحيب، العنوان المتوهج. |
| `Feedback.kt` | الصوت والاهتزاز (انظر القسم 4). |
| `app/src/main/res/values/strings.xml` | اسم التطبيق الظاهر تحت الأيقونة. |

## 2) ملفات الشاشات (ترتيب كل شاشة) — عدّلها فقط لتغيير الترتيب، وبحذر
`MainActivity.kt` (الترويسة والقائمة) · `AuthScreens.kt` (الدخول وإنشاء الحساب) · `Onboarding.kt` (التعريف) ·
`PassengerScreen.kt` (واجهة الراكب) · `CaptainScreen.kt` (واجهة الكابتن) · `LiveTrack.kt` (تتبّع الكابتن).

الثيم **واحد للتطبيقين** (الراكب والكابتن)، وواجهة الراكب والكابتن كلٌ بملفه.

## 3) ممنوع تلمسه (وظائف التطبيق)
`Repo.kt` · `Models.kt` · `Util.kt` · `Location.kt` · `LiveShareService.kt` · `Places.kt` · `AppRole.kt` · `Alerts.kt`
وكل ملفات `build.gradle.kts` و`google-services.json` و`firestore.rules`. تعديلها يغيّر عمل التطبيق، مش شكله.

## القاعدة الذهبية
لا تغيّر **أسماء** الدوال أو الثوابت العامة ولا **قيمها** (parameters) ولا ترتيبها. غيّر ما بداخلها فقط (ألوان، أحجام، زوايا، تأثيرات)،
وتقدر تضيف دوال جديدة. لو تغيّر اسم دالة، باقي التطبيق يظل ينادي عليها بالاسم القديم ويفشل البناء.

أسماء لا تغيّرها:
`ShajaraTheme, AppBackground, Brand, PillChip, ChoiceChips, ScrollChips, Stepper, SectionLabel, Pill, PricePill, SeatShape, SeatState,
SeatsRow, RouteTimeline, Avatar, RoundAction, CallButton, WhatsAppButton, TimeBlock, RoadSign, TripInfo, AppCard, HeroHeader,
EmptyState, LoadingBox, BigButton, ReasonDialog, VerifyBar, navBarColor, navItemColors, AppLogo, SplashScreen, GlowTitle,
APP_DISPLAY_NAME, WELCOME_LINE, SPLASH_MILLIS, AppFeedback`

## 4) الصوت والاهتزاز (`Feedback.kt`)
- **الاهتزاز:** مفتاح `HAPTICS_ENABLED` (شغّال افتراضياً): خفيف عند ضغط الأزرار الرئيسية، أقوى عند نجاح الحجز، ونبضة للكابتن عند وصول طلب جديد.
- **الأصوات:** مفتاح `SOUNDS_ENABLED` (مقفل افتراضياً). لتشغيلها: ضع ملفات `.mp3` أو `.ogg` في المجلد `app/src/main/res/raw/` بهذه الأسماء بالضبط
  (أحرف صغيرة بدون مسافات)، ثم غيّر المفتاح إلى `true`:
  - `fb_tap` عند الضغط · `fb_success` عند النجاح · `fb_error` عند الخطأ · `fb_request` للكابتن عند طلب جديد.
- ملف ناقص = يُتجاهل بهدوء، لا يسبب خطأ.

## 5) نص جاهز تلصقه لأي بوت (شات جي بي تي / جيميني ...)
```
أنا أعدّل شكل (ثيم) تطبيق أندرويد Kotlin / Jetpack Compose. مسموح لك تعدّل فقط هذه الملفات:
Theme.kt, Components.kt, Logo.kt, Splash.kt, Feedback.kt
ولا تلمس أي ملف آخر ولا تقترح تعديلاً على ملفات أخرى.

قواعد صارمة لتجنّب كسر البناء:
1. لا تغيّر اسم أي دالة أو كلاس أو ثابت عام، ولا ترتيب القيم ولا أنواعها ولا قيمها الافتراضية
   (مثل: ShajaraTheme, AppBackground, Brand, BigButton, AppCard, TripInfo, SeatShape, AppLogo, SplashScreen, AppFeedback ...).
2. لا تحذف أي دالة. تقدر تغيّر داخلها وتضيف دوال جديدة.
3. الألوان الرئيسية تتغير من الكتلة Brand فقط في Theme.kt.
4. أرجع لي الملف كاملاً جاهزاً للصق، وليس أجزاء.
5. لا تضف مكتبات ولا تعديلات على build.gradle.
6. التطبيق عربي RTL ومظلم دائماً، حافظ على ذلك.
7. اشرح لي بسطرين ما غيّرته.

هذه الملفات: (ألصق الملفات هنا)
```

## 6) اختبار آمن قبل التثبيت
1. أنشئ **فرعاً جديداً** على GitHub (مثلاً `theme-test`) من هذا الفرع. لا تلمس `main`.
2. ألصق الملفات المعدّلة في الفرع الجديد.
3. شغّل الـ Actions: `Design screenshots` (يعطيك صور حقيقية للشاشات بدون بناء كامل) ثم `build` إذا أردت ملف التطبيق.
4. إذا فشل البناء: ارجع للنسخة السابقة من الملف (GitHub يحفظ كل نسخة) وأرسل رسالة الخطأ للبوت.
