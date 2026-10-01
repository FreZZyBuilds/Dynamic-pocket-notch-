package com.frezzybuilds.devnotch.feature.billing

import android.app.Activity
import android.app.Application
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore

/** Ein kaufbares Paket, wie es die Paywall anzeigt. */
data class PaywallPackage(
    val id: String,
    val title: String,
    val price: String,
    val note: String?,
    internal val rcPackage: Package?
)

sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

/** Abstraktion über RevenueCat – die Paywall und Tests hängen nicht direkt am SDK. */
interface BillingClient {
    suspend fun loadPackages(): List<PaywallPackage>
    suspend fun purchase(activity: Activity, pkg: PaywallPackage): PurchaseOutcome
    suspend fun restore(): PurchaseOutcome
}

/**
 * RevenueCat-Anbindung. [configure] läuft in Application.onCreate; mit dem Platzhalter-Key
 * startet das SDK, Angebote und Käufe schlagen aber erwartungsgemäß fehl – die Paywall zeigt
 * dann einen Hinweis statt Preise.
 */
class RevenueCatBilling(private val proAccess: ProAccess) : BillingClient {

    fun configure(app: Application, apiKey: String, debug: Boolean) {
        if (Purchases.isConfigured) return
        try {
            if (debug) Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(app, apiKey).build())
            Purchases.sharedInstance.updatedCustomerInfoListener =
                com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener(::apply)
        } catch (e: Exception) {
            // Ohne Billing (z. B. Geräte ohne Play Store) bleibt die App im Free-Modus nutzbar.
            Log.w(TAG, "RevenueCat konnte nicht gestartet werden", e)
        }
    }

    private fun apply(info: CustomerInfo) {
        proAccess.onEntitlementChanged(info.entitlements[ProPlan.ENTITLEMENT_ID]?.isActive == true)
    }

    suspend fun refresh() {
        if (!Purchases.isConfigured) return
        runCatching { Purchases.sharedInstance.awaitCustomerInfo() }.onSuccess(::apply)
    }

    override suspend fun loadPackages(): List<PaywallPackage> {
        if (!Purchases.isConfigured) return emptyList()
        val offering = Purchases.sharedInstance.awaitOfferings().current ?: return emptyList()
        return offering.availablePackages
            .sortedBy { order(it.packageType) }
            .map { pkg ->
                PaywallPackage(
                    id = pkg.identifier,
                    title = title(pkg.packageType),
                    price = pkg.product.price.formatted,
                    note = if (pkg.packageType == PackageType.ANNUAL) "Bester Preis" else null,
                    rcPackage = pkg
                )
            }
    }

    override suspend fun purchase(activity: Activity, pkg: PaywallPackage): PurchaseOutcome {
        val rcPackage = pkg.rcPackage ?: return PurchaseOutcome.Failed("Paket nicht verfügbar")
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, rcPackage).build())
            apply(result.customerInfo)
            PurchaseOutcome.Success
        } catch (e: PurchasesTransactionException) {
            if (e.userCancelled) PurchaseOutcome.Cancelled else PurchaseOutcome.Failed(e.message ?: "Kauf fehlgeschlagen")
        }
    }

    override suspend fun restore(): PurchaseOutcome = try {
        val info = Purchases.sharedInstance.awaitRestore()
        apply(info)
        if (info.entitlements[ProPlan.ENTITLEMENT_ID]?.isActive == true) PurchaseOutcome.Success
        else PurchaseOutcome.Failed("Kein aktiver Pro-Kauf gefunden")
    } catch (e: PurchasesException) {
        PurchaseOutcome.Failed(e.message ?: "Wiederherstellen fehlgeschlagen")
    }

    private fun order(type: PackageType) = when (type) {
        PackageType.ANNUAL -> 0
        PackageType.MONTHLY -> 1
        PackageType.LIFETIME -> 2
        else -> 3
    }

    private fun title(type: PackageType) = when (type) {
        PackageType.ANNUAL -> "Jährlich"
        PackageType.MONTHLY -> "Monatlich"
        PackageType.LIFETIME -> "Einmalig"
        PackageType.WEEKLY -> "Wöchentlich"
        else -> "Pro"
    }

    companion object {
        private const val TAG = "DevNotchBilling"

        /** Platzhalter aus build.gradle.kts bzw. kein Google-Play-Key. */
        fun isPlaceholderKey(key: String): Boolean = key.contains("REPLACE") || !key.startsWith("goog_")
    }
}
