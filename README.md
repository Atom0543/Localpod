# LocalPod

แอปเล่นเพลงออฟไลน์บน Android เล่นเฉพาะไฟล์เพลงในเครื่อง ไม่ต้องมีบัญชี ไม่มีสตรีมมิ่ง มีโหมด iPod (เมนู + วงล้อ) ให้ใช้ด้วย

Kotlin · Jetpack Compose · Media3 · Android 8.0+ (API 26)

[![Download APK](https://img.shields.io/badge/Download-APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Atom0543/LocalPod/releases/latest/download/LocalPod.apk)

## ติดตั้ง
1. กดปุ่ม **Download APK** ด้านบนบนมือถือ
2. เปิดไฟล์ `LocalPod.apk` ถ้ามือถือถาม ให้อนุญาต "ติดตั้งแอปที่ไม่รู้จัก" ให้เบราว์เซอร์หรือแอปจัดการไฟล์
3. เปิดแอป แล้วอนุญาตให้เข้าถึงไฟล์เพลง

ดูเวอร์ชันเก่าได้ที่หน้า [Releases](https://github.com/Atom0543/LocalPod/releases)

## ฟีเจอร์
- **สองโหมด**: หน้ารายการเพลงปกติ กับโหมด iPod ที่ใช้วงล้อ (click wheel) และเมนู
- **ซ่อนไฟล์ที่ไม่ใช่เพลง** ได้ ไฟล์จริงไม่ถูกลบ
- **เล่นเบื้องหลัง** และมีปุ่มควบคุมบน notification
- **วิดเจ็ตหน้าจอโฮม** มีสองขนาด
- **ภาพปกอัลบั้ม** ดึงจากไฟล์เพลง และครอปภาพปกเองได้
- **จัดชื่อเพลง**: ล้างคำขยะในชื่อ (Official MV, 320kbps ฯลฯ) และแยกชื่อศิลปินออกจากชื่อเพลง ถ้าสั่งเอง แอปจะเทียบชื่อกับ Deezer ทางอินเทอร์เน็ต (ใช้เน็ตเฉพาะตอนนี้)
- **ธีมสว่าง/มืด** ตามระบบ หรือสลับเองได้

## Build เอง
1. Android Studio > File > Open > เลือกโฟลเดอร์นี้
2. รอ Gradle sync
3. ต่อมือถือ (เปิด USB debugging) แล้วกด Run
4. ถ้าต้องการไฟล์ APK: Build > Build App Bundle(s) / APK(s) > Build APK(s)

ต้องใช้ JDK 17

## โครงสร้างไฟล์
อยู่ใน `app/src/main/java/com/sophonat/localpod/`
- `MainActivity.kt` จุดเริ่มแอป ขอสิทธิ์ และเชื่อมต่อ player
- `Library.kt` สแกนเพลง และเก็บรายการที่ซ่อนไว้
- `PlaybackService.kt` เล่นเพลงเบื้องหลัง และปุ่มบน notification
- `App.kt` โหมดปกติ หน้าที่ซ่อนไว้ ปัดเพื่อสลับหน้า
- `Ipod.kt` โหมด iPod: เมนูกับวงล้อ (`stepDegrees` คือความไวของวงล้อ)
- `Tidy.kt` จัดชื่อเพลงและศิลปิน
- `Cover.kt`, `Crop.kt` ภาพปก และหน้าครอปภาพ
- `Widget.kt` วิดเจ็ตหน้าจอโฮม
- `Theme.kt` สีและธีม

## License
[MIT](LICENSE)
