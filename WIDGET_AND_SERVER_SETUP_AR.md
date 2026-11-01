# Arabic Jarvis AI — الويدجت والربط

## الويدجت
الويدجت الحقيقي 4×2 في شاشة Android الرئيسية. زر «تحدث» يفتح طلب التعرف على الكلام بالعربية (بعد منح إذن الميكروفون)، وزر «محادثة» يفتح التطبيق. لا يلتقط الصوت في الخلفية ولا يدعي أن السيرفر متصل قبل التحقق منه.

## تحديث GitHub من Termux
```bash
cd ~/ArabicJarvisAI
unzip -o ~/storage/downloads/ArabicJarvisAI-v1-widget.zip -d ~/jarvis-widget-update
cp -a ~/jarvis-widget-update/ArabicJarvisAI-v1-widget/. .
git add -A && git commit -m "Add Arabic home screen widget" && git push origin main
gh run list --limit 3
gh run watch
```

## سيرفر Ubuntu (يجب تثبيت OpenJarvis واختبار `jarvis ask` أولاً)
```bash
cd /path/to/ArabicJarvisAI-v1-widget
python3 -m venv .venv
.venv/bin/pip install -r server/requirements.txt
jarvis doctor
jarvis ask 'أجب بالعربية: مرحبا'
export JARVIS_BRIDGE_TOKEN="$(openssl rand -hex 32)"
.venv/bin/uvicorn server.bridge:app --host 127.0.0.1 --port 8765
```
استخدم HTTPS بشهادة موثوقة واسم نطاق قابل للوصول من الهاتف، عبر Caddy أو reverse proxy؛ لا تعرض منفذ 8765 مباشرة للإنترنت. احتفظ بالرمز بشكل آمن ولا تضعه في GitHub. ضع رابط HTTPS والرمز في التطبيق. تحقق من `https://YOUR_DOMAIN/health` (هذا يفحص الجسر فقط، وليس جاهزية النموذج).

## الاختبارات
```bash
python3 -m venv .venv
.venv/bin/pip install -r server/requirements.txt
.venv/bin/pip install pytest httpx
PYTHONPATH=. .venv/bin/pytest server/tests -q
```
بعد بناء APK في GitHub Actions، ثبّته وافتح شاشة One UI الرئيسية > ضغط مطوّل > الأدوات (Widgets) > جارفيس العربي > إضافة. اختبر الزرين والصلاحيات ثم اتصال HTTPS. 

## قيود
واجهة Android لا تشغّل محرك OpenJarvis داخليًا. يعمل المحرك على Ubuntu. قد تختلف أوامر CLI باختلاف إصدار OpenJarvis؛ اختبر `jarvis ask` على السيرفر. الويدجت لا يعرض حالة سيرفر حقيقية أو محادثة مباشرة داخل الويدجت في هذا الإصدار.
