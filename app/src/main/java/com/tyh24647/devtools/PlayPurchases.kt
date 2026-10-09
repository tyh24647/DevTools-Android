package com.tyh24647.devtools

import android.app.Activity
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.*
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Play owns transactions; JavaScript and imported settings cannot grant entitlements. */
class PlayPurchases(
    private val activity: Activity,
    private val config: Configuration,
    private val changed: () -> Unit,
) : PurchasesUpdatedListener {
    var products by mutableStateOf<List<ProductDetails>>(emptyList())
        private set

    var message by mutableStateOf("Connect Google Play to load prices.")
        private set

    private var connected = false
    private var verifiedAt = 0L
    private val client =
        BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()

    fun connect() {
        if (client.isReady) {
            restore()
            return
        }
        if (connected) {
            return
        }
        connected = true
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connected = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        loadProducts()
                        restore()
                    } else {
                        message = "Google Play is unavailable: ${result.debugMessage}"
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connected = false
                    message = "Google Play disconnected. Restore to reconnect."
                }
            }
        )
    }

    private fun loadProducts() {
        // Billing rejects requests mixing subscriptions and one-time products.
        val groups =
            mapOf(
                BillingClient.ProductType.SUBS to
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(MONTHLY)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    ),
                BillingClient.ProductType.INAPP to
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(LIFETIME)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    ),
            )
        val responses = mutableMapOf<String, List<ProductDetails>>()
        for ((type, group) in groups) {
            client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(group).build()
            ) { result, response ->
                activity.runOnUiThread {
                    responses[type] =
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            response.productDetailsList
                        } else {
                            emptyList()
                        }
                    if (responses.size == groups.size) {
                        products = groups.keys.flatMap { responses.getValue(it) }
                        message =
                            if (products.isNotEmpty()) {
                                "Monthly renews until cancelled. Lifetime is a single purchase."
                            } else {
                                "Configure the two products in Play Console and install from a test track to test purchases."
                            }
                    }
                }
            }
        }
    }

    fun price(product: ProductDetails): String =
        if (product.productType == BillingClient.ProductType.SUBS) {
            baseOffer(product)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                ?: "Unavailable"
        } else {
            product.oneTimePurchaseOfferDetails?.formattedPrice ?: "Unavailable"
        }

    fun buy(product: ProductDetails) {
        if (!client.isReady) {
            connect()
            return
        }
        if (BuildConfig.BILLING_PUBLIC_KEY.isBlank()) {
            message = "Add your Play licensing public key before enabling real purchases."
            return
        }
        val detail = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        if (product.productType == BillingClient.ProductType.SUBS) {
            val offer = baseOffer(product)
            if (offer == null) {
                message = "No eligible monthly base plan."
                return
            }
            detail.setOfferToken(offer.offerToken)
        }
        val result =
            client.launchBillingFlow(
                activity,
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(detail.build()))
                    .build(),
            )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            message = result.debugMessage
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty().forEach { acknowledge(it) }
                restore()
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> message = "Purchase cancelled."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            else -> message = "Purchase not completed: ${result.debugMessage}"
        }
    }

    /**
     * Re-query active subscriptions on foreground; cancelled subscriptions retain access until Play
     * expires them.
     */
    fun restore() {
        if (!client.isReady) {
            connect()
            return
        }
        val results = mutableMapOf<String, List<Purchase>>()
        for (type in listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)) {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(type).build()
            ) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    if (
                        android.os.SystemClock.elapsedRealtime() - verifiedAt > 24 * 60 * 60 * 1000L
                    ) {
                        config.pro = BuildConfig.DEBUG && config.previewPro
                        changed()
                    }
                    message = "Could not refresh purchases: ${result.debugMessage}"
                    return@queryPurchasesAsync
                }
                results[type] = purchases
                purchases.forEach { acknowledge(it) }
                if (results.size == 2) {
                    config.billingChecked = true
                    verifiedAt = android.os.SystemClock.elapsedRealtime()
                    config.pro =
                        (BuildConfig.DEBUG && config.previewPro) ||
                            results.values.flatten().any { valid(it) }
                    changed()
                    message =
                        if (config.pro) "Full version active." else "No active Pro purchase found."
                }
            }
        }
    }

    private fun baseOffer(product: ProductDetails) =
        product.subscriptionOfferDetails?.firstOrNull { it.offerId == null }
            ?: product.subscriptionOfferDetails?.firstOrNull()

    private fun valid(purchase: Purchase): Boolean {
        if (
            purchase.purchaseState != Purchase.PurchaseState.PURCHASED ||
                purchase.products.none { it in listOf(MONTHLY, LIFETIME) }
        ) {
            return false
        }
        if (BuildConfig.BILLING_PUBLIC_KEY.isBlank()) {
            return false
        }
        return runCatching {
                val key =
                    KeyFactory.getInstance("RSA")
                        .generatePublic(
                            X509EncodedKeySpec(
                                Base64.decode(BuildConfig.BILLING_PUBLIC_KEY, Base64.DEFAULT)
                            )
                        )
                Signature.getInstance("SHA1withRSA").run {
                    initVerify(key)
                    update(purchase.originalJson.toByteArray(Charsets.UTF_8))
                    verify(Base64.decode(purchase.signature, Base64.DEFAULT))
                }
            }
            .getOrDefault(false)
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            message = "Purchase pending approval; access will unlock when completed."
            return
        }
        if (valid(purchase) && !purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    message = "Purchase acknowledgement will retry on the next refresh."
                }
            }
        }
    }

    fun close() {
        client.endConnection()
    }

    companion object {
        const val MONTHLY = "devtools_pro_monthly"
        const val LIFETIME = "devtools_pro_lifetime"
    }
}
