package dev.jjtech.eapakaclient.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.collections.iterator

private fun getAllIPAddresses(): List<String> {
    val result = mutableListOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (iface in interfaces) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    result.add("${iface.displayName}: ${addr.hostAddress}")
                }
            }
        }
    } catch (e: Exception) {
        result.add("Error: ${e.message}")
    }
    return if (result.isEmpty()) listOf("No active network interfaces found.") else result
}

@Composable
fun StatusDisplay(permission: Boolean) {
    val ipAddresses = getAllIPAddresses()
    Column(modifier = Modifier.padding(16.dp)) {
        Spacer(Modifier.height(100.dp))
        Text(text="AppOp Permission Granted? $permission", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(text = "IP Addresses:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        for (ip in ipAddresses) {
            Text(
                text = ip,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}