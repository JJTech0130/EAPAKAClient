package dev.jjtech.eapakaclient

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.jjtech.eapakaclient.server.APIServer
import dev.jjtech.eapakaclient.server.StatusDisplay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StatusDisplay(hasIccAuthWithDeviceIdentifierPermission(this))
        }
        APIServer(this, 8080).start()
    }
}

// adb shell appops set --uid dev.jjtech.eapakaclient USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER allow
fun hasIccAuthWithDeviceIdentifierPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        "android:use_icc_auth_with_device_identifier",
        android.os.Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}