package com.example.fishingnote.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

fun hasApproxLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** 現在地を取れなかった理由。利用者への案内を分けるために使う */
enum class LocationFailure { NO_PERMISSION, LOCATION_OFF, NOT_FOUND }

sealed interface LocationResult {
    data class Found(val location: Location) : LocationResult
    data class Failed(val reason: LocationFailure) : LocationResult
}

/** 端末の位置情報の設定がオンか */
fun isLocationOn(context: Context): Boolean =
    context.getSystemService(LocationManager::class.java)?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

/** おおよその現在地を1回だけ取り、取れなかったときは理由を返す */
suspend fun findApproxLocation(context: Context, timeoutMs: Long = 10_000, maxAgeMs: Long = 2 * 60_000): LocationResult = when {
    !hasApproxLocation(context) -> LocationResult.Failed(LocationFailure.NO_PERMISSION)
    !isLocationOn(context) -> LocationResult.Failed(LocationFailure.LOCATION_OFF)
    else -> currentApproxLocation(context, timeoutMs, maxAgeMs)?.let { LocationResult.Found(it) }
        ?: LocationResult.Failed(LocationFailure.NOT_FOUND)
}

/**
 * おおよその現在地を1回だけ取る。取れなければ null（権限なし・位置情報オフ・時間切れ）。
 * 値はその場で地図アプリに渡すだけで、保存しない。
 * maxAgeMs 以内に取れた位置があれば、待たずにそれを使う（0 なら必ず新しく取る）。
 */
suspend fun currentApproxLocation(context: Context, timeoutMs: Long = 10_000, maxAgeMs: Long = 2 * 60_000): Location? {
    if (!hasApproxLocation(context)) return null
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    if (!LocationManagerCompat.isLocationEnabled(lm)) return null
    // API 30 以前は、おおよその位置の権限で GPS を指定できないため、Wi-Fi・基地局（network）だけを使う
    val providers = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.GPS_PROVIDER)
    }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

    // 新しさは端末の時計ではなく起動からの経過時間で比べる（時計を戻した端末で、古い位置を新しいと誤らないため）
    val now = SystemClock.elapsedRealtimeNanos()
    val recent = if (maxAgeMs <= 0) null else providers
        .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        .filter { val age = now - it.elapsedRealtimeNanos; age in 0..maxAgeMs * 1_000_000 }
        .maxByOrNull { it.elapsedRealtimeNanos }
    if (recent != null) return recent

    if (providers.isEmpty()) return null
    // 省電力の取り方（Wi-Fi・基地局）が使えない海辺でも取れるよう、使える取り方すべてに同時に問い合わせ、
    // 最初に届いた位置を使う。届いたら残りの問い合わせは止める（GPS を併用できるのは API 31 以降）
    return withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val signals = providers.map { CancellationSignal() }
            fun cancelAll() = signals.forEach { it.cancel() }
            cont.invokeOnCancellation { cancelAll() }
            // 呼び出し元のスレッドと、結果を受けるメインスレッドの両方から数えるため AtomicInteger にする
            val pending = java.util.concurrent.atomic.AtomicInteger(providers.size)
            val main = ContextCompat.getMainExecutor(context)
            providers.forEachIndexed { i, provider ->
                try {
                    LocationManagerCompat.getCurrentLocation(lm, provider, signals[i], main) { loc ->
                        val left = pending.decrementAndGet()
                        synchronized(cont) {
                            if (!cont.isActive) return@getCurrentLocation
                            if (loc != null) { cancelAll(); cont.resume(loc) }
                            else if (left == 0) cont.resume(null)
                        }
                    }
                } catch (e: Exception) {
                    // 権限の取り消しや、取り方が直前に使えなくなった場合。その取り方だけ諦めて残りを待つ
                    if (pending.decrementAndGet() == 0) synchronized(cont) { if (cont.isActive) cont.resume(null) }
                }
            }
        }
    }
}
