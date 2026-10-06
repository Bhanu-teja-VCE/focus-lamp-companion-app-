package com.focuslamp.app.data.lamp

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Finds the lamp on the Wi-Fi without typing an IP. The firmware already advertises itself
 * over mDNS as "focuslamp" with an HTTP service, so we look for that.
 */
class LampDiscovery(context: Context) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /** Returns "ip:port" (or just the ip when port is 80), or null after [timeoutMs]. */
    suspend fun find(timeoutMs: Long = 8_000L): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            var resolving = false
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

                override fun onServiceFound(info: NsdServiceInfo) {
                    if (resolving || !info.serviceName.contains("focuslamp", ignoreCase = true)) return
                    resolving = true
                    val discovery = this
                    @Suppress("DEPRECATION")
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            resolving = false
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val ip = resolved.host?.hostAddress
                            if (ip != null && cont.isActive) {
                                runCatching { nsd.stopServiceDiscovery(discovery) }
                                cont.resume(if (resolved.port == 80 || resolved.port <= 0) ip else "$ip:${resolved.port}")
                            }
                        }
                    })
                }
            }
            nsd.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
        }
    }
}
