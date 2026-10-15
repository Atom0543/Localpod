# LocalPod

แอปเล่นเพลงเฉพาะไฟล์ในเครื่อง (Android, Kotlin + Jetpack Compose + Media3)

## เปิดโปรเจกต์
1. Android Studio > File > Open > เลือกโฟลเดอร์ `LocalPod`
2. รอ Gradle sync (ครั้งแรกจะโหลดของนานหน่อย) ถ้า Studio เสนอให้อัปเกรด AGP/Kotlin กดตกลงได้
3. ต่อมือถือ (เปิด USB debugging) แล้วกด Run
4. ได้ไฟล์ APK: Build > Build App Bundle(s) / APK(s) > Build APK(s)

## ไฟล์
- `Library.kt` สแกนเพลง + เก็บรายการที่ซ่อน (ไม่ลบไฟล์จริง)
- `PlaybackService.kt` เล่นเพลงเบื้องหลัง + ปุ่มบน notification
- `App.kt` โหมดปกติ, หน้าที่ซ่อนไว้, ปัดสลับหน้า
- `Ipod.kt` โหมด iPod: เมนู + วงล้อ (`stepDegrees` = ความไวของวงล้อ)
