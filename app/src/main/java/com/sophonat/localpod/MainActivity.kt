package com.sophonat.localpod

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var themeMode by mutableStateOf("system") // system / light / dark
    private var permission by mutableStateOf("waiting") // waiting / granted / denied

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = ThemePrefs.load(this)

        val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        val asks = if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPerm, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(audioPerm)

        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[audioPerm] == true) {
                permission = "granted"
            } else {
                permission = "denied"
            }
        }
        if (ContextCompat.checkSelfPermission(this, audioPerm) == PackageManager.PERMISSION_GRANTED) {
            permission = "granted"
        } else {
            launcher.launch(asks)
        }

        connect()

        setContent {
            val dark = isDark(themeMode)
            // ไอคอนแถบสถานะ/แถบนำทางต้องตัดกับพื้นหลังตามธีม
            SideEffect {
                val bar = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            LocalPodTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val c = controller
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        if (permission == "denied") {
                            Text("ต้องอนุญาตให้อ่านไฟล์เพลงก่อน (ไปที่ ตั้งค่า > แอป > LocalPod)", Modifier.padding(24.dp))
                        } else if (permission == "waiting" || c == null) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        } else {
                            App(c, themeMode) {
                                themeMode = ThemePrefs.next(themeMode)
                                ThemePrefs.save(this@MainActivity, themeMode)
                            }
                        }
                    }
                }
            }
        }
    }

    // ต่อกับตัวเล่นเพลง (PlaybackService) ใหม่ทุกครั้งที่กลับเข้าแอป และเมื่อการเชื่อมต่อหลุด
    // เพราะหลังกดผ่าน widget การเชื่อมต่อเก่าอาจเสียสถานะ (เมนูกดได้แต่คำสั่งเล่นเพลงไม่ถึงตัวเล่น)
    // ตัวใหม่มาแทนตัวเก่าโดยไม่ล้างค่า controller เป็น null จึงไม่ทำให้หน้าจอ/เมนูที่เปิดอยู่รีเซ็ต
    private var connecting = false

    private fun connect() {
        if (connecting) return
        connecting = true
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@MainActivity.controller === controller) {
                        connecting = false
                        connect()
                    }
                }
            })
            .buildAsync()
        future.addListener({
            try {
                val old = controller
                controller = future.get()
                // ปล่อยตัวเก่าหลังหน่วงสั้นๆ ให้หน้าจอเลิกฟังตัวเก่าก่อน
                if (old != null && old !== controller) Handler(Looper.getMainLooper()).postDelayed({ old.release() }, 1000)
            } catch (e: Exception) {
                // ต่อไม่ได้ จะลองใหม่ตอนกลับเข้าแอป (onStart)
            }
            connecting = false
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStart() {
        super.onStart()
        connect()
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}
