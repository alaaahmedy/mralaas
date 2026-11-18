package com.mralaa.studentaccounts

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.*
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.util.Base64

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val createBackup = 4101
    private val openBackup = 4102
    private val notificationPermissionRequest = 4103
    private val channelId = "monthly_payment_reminders"
    private var pendingBackup: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.addJavascriptInterface(BackupBridge(), "AndroidBackup")
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), notificationPermissionRequest)
        }
        web.webChromeClient = WebChromeClient()
        web.loadUrl("file:///android_asset/index.html")
    }

    inner class BackupBridge {
        @JavascriptInterface
        fun exportBackup(json: String, filename: String) {
            runOnUiThread {
                pendingBackup = json
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, filename)
                }
                startActivityForResult(intent, createBackup)
            }
        }

        @JavascriptInterface
        fun showMonthlyAlert(title: String, body: String) {
            runOnUiThread { postMonthlyNotification(title, body) }
        }

        @JavascriptInterface
        fun importBackup() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, openBackup)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "تذكيرات تحصيل الاشتراكات",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "تنبيه عند بداية شهر جديد بوجود طلاب لم يسددوا الاشتراك"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun postMonthlyNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        manager.notify(1201, notification)
    }

    @Deprecated("Deprecated in Android API, retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri: Uri = data.data!!
        try {
            when (requestCode) {
                createBackup -> {
                    contentResolver.openOutputStream(uri)?.use { it.write(pendingBackup.toByteArray(Charsets.UTF_8)) }
                    Toast.makeText(this, "تم تصدير النسخة الاحتياطية", Toast.LENGTH_LONG).show()
                }
                openBackup -> {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    web.evaluateJavascript("(function(){var s=atob('$encoded');var a=Uint8Array.from(s,c=>c.charCodeAt(0));window.importBackupFromAndroid(new TextDecoder('utf-8').decode(a));})();", null)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "تعذر التعامل مع ملف النسخة الاحتياطية", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
