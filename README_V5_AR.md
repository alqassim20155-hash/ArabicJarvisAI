# Arabic Jarvis AI v5 — واجهة وودجت

تحديث تدريجي فوق مشروع v4.3 الحالي. لا يمس Bridge أو رمز المصادقة أو النموذج.

## التحسينات
- واجهة أكثر اختصارًا، نصوص عالية التباين، واحترام منطقة أزرار التنقل الثلاثة عبر safeDrawing.
- ودجت 4×3 بأزرار تشغيل وإيقاف ومحادثة وصوت، وصف حالة قابل للنقر للفحص.
- أزرار الودجت تفتح التطبيق وتطلب العملية من Bridge الحالي. الإيقاف يطلب التأكيد.
- تحديث مؤشر الودجت بعد استجابة Bridge؛ لا يوجد استعلام مستمر في الخلفية.

## التثبيت في Termux
```sh
cd ~/ArabicJarvisAI
cp app/src/main/java/com/arabicjarvis/ai/MainActivity.kt ~/MainActivity-before-v5.kt
unzip -o ~/storage/downloads/ArabicJarvisAI-v5-UI-Widget-Patch.zip
 git diff --check
 git add app/src/main/java/com/arabicjarvis/ai/MainActivity.kt app/src/main/java/com/arabicjarvis/ai/JarvisWidgetProvider.kt app/src/main/res/layout/jarvis_widget.xml app/src/main/res/xml/jarvis_widget_info.xml app/src/main/res/values/jarvis_widget_styles.xml
 git commit -m "Improve Jarvis v5 UI and widget controls"
 git push origin main
```

GitHub Actions يبني APK. هذا الملف مصدر/تحديث وليس APK مثبتًا. يُنصح بإزالة الودجت القديم وإضافته مجددًا لتطبيق الحجم الجديد.

**تنبيه:** الاستماع المستمر لكلمة جارفيس غير مفعّل في هذا التحديث، ويتطلب خدمة صوت وWake Word محليًا واختبار استهلاك البطارية. كما أن الأزرار في الودجت تفتح التطبيق لتنفيذ الأمر ولا تعمل خفية في الخلفية.
