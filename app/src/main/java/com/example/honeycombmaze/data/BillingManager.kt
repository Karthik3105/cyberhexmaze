package com.example.honeycombmaze.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*

class BillingManager(
    private val context: Context,
    private val prefsManager: PreferencesManager,
    private val onHoneyPurchased: (Int) -> Unit = {},
    private val onModeLevelsUnlocked: (com.example.honeycombmaze.game.GameMode) -> Unit = {}
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    var isConnected = false
        private set

    init {
        startConnection()
    }

    fun startConnection(onSuccess: (() -> Unit)? = null) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isConnected = true
                    Log.d("BillingManager", "Billing setup successful.")
                    queryPurchases()
                    queryPurchaseHistory()
                    onSuccess?.invoke()
                } else {
                    Log.e("BillingManager", "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnected = false
                Log.w("BillingManager", "Billing service disconnected. Will retry on next purchase.")
            }
        })
    }

    /**
     * Query active products and launch the Play Billing purchase sheet.
     */
    fun launchPurchaseFlow(activity: Activity, productId: String, productType: String = BillingClient.ProductType.INAPP) {
        if (!isConnected) {
            startConnection {
                launchPurchaseFlowInternal(activity, productId, productType)
            }
            return
        }
        launchPurchaseFlowInternal(activity, productId, productType)
    }

    private fun launchPurchaseFlowInternal(activity: Activity, productId: String, productType: String) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val flowParams = BillingFlowParams.newBuilder()
                    .setObfuscatedAccountId(prefsManager.getOrCreateUserGuid())
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()

                billingClient.launchBillingFlow(activity, flowParams)
            } else {
                Log.e("BillingManager", "Failed to query product details: ${billingResult.debugMessage}")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Product not available in Play Console yet", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Log.d("BillingManager", "Item already owned. Consuming purchase token so user can purchase again...")
            consumeUnlockAllLevels {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Purchase reset on Google Play! Please tap UNLOCK ALL LEVELS to purchase.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d("BillingManager", "User canceled the purchase.")
        } else {
            Log.e("BillingManager", "Purchase failed with code: ${billingResult.responseCode}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            prefsManager.markPurchaseTokenProcessed(purchase.purchaseToken)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                for (productId in purchase.products) {
                    val mode = getModeForProductId(productId)
                    if (mode != null) {
                        prefsManager.setModeLevelsUnlocked(mode.id, true)
                        onModeLevelsUnlocked(mode)
                        consumePurchase(purchase)
                        acknowledgePurchase(purchase)
                        android.widget.Toast.makeText(context, "Success! All 100 Levels Unlocked for ${mode.title}! 🔓🎉", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        when (productId) {
                            PRODUCT_HONEY_1000 -> {
                                prefsManager.honey += 1000
                                onHoneyPurchased(1000)
                                consumePurchase(purchase)
                                android.widget.Toast.makeText(context, "Success! 1000 Coins Added! 🍯", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            PRODUCT_HONEY_500 -> {
                                prefsManager.honey += 500
                                onHoneyPurchased(500)
                                consumePurchase(purchase)
                                android.widget.Toast.makeText(context, "Success! 500 Coins Added! 🍯", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            PRODUCT_HONEY_100 -> {
                                prefsManager.honey += 100
                                onHoneyPurchased(100)
                                consumePurchase(purchase)
                                android.widget.Toast.makeText(context, "Success! 100 Coins Added! 🍯", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            PRODUCT_REMOVE_ADS -> {
                                prefsManager.isRemoveAdsPurchased = true
                                acknowledgePurchase(purchase)
                                android.widget.Toast.makeText(context, "Success! Ads Removed! 🚫", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                acknowledgePurchase(purchase)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("BillingManager", "Purchase consumed successfully.")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingManager", "Purchase acknowledged successfully.")
                }
            }
        }
    }

    /**
     * Restore existing active purchases (e.g. Remove Ads)
     */
    fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasActiveRemoveAds = purchases?.any { purchase ->
                    purchase.products.contains(PRODUCT_REMOVE_ADS) &&
                    purchase.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED
                } == true

                prefsManager.isRemoveAdsPurchased = hasActiveRemoveAds

                if (purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
        }
    }

    /**
     * Restore historical coin purchases across reinstalls or new devices using Google Play Purchase History!
     */
    fun queryPurchaseHistory() {
        val params = QueryPurchaseHistoryParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchaseHistoryAsync(params) { billingResult, historyList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && historyList != null) {
                for (record in historyList) {
                    for (productId in record.products) {
                        if (productId != PRODUCT_REMOVE_ADS) {
                            val mode = getModeForProductId(productId)
                            if (mode != null) {
                                prefsManager.setModeLevelsUnlocked(mode.id, true)
                                onModeLevelsUnlocked(mode)
                                prefsManager.markPurchaseTokenProcessed(record.purchaseToken)
                            }
                        }
                    }
                }
            }
        }
    }

    fun consumeUnlockAllLevels(onComplete: (() -> Unit)? = null) {
        prefsManager.isRemoveAdsPurchased = false
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                val unlockPurchases = purchases.filter { p -> p.products.any { ALL_UNLOCK_PRODUCT_IDS.contains(it) } }
                if (unlockPurchases.isEmpty()) {
                    onComplete?.invoke()
                    return@queryPurchasesAsync
                }
                var pending = unlockPurchases.size
                for (purchase in unlockPurchases) {
                    val consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.consumeAsync(consumeParams) { _, _ ->
                        pending--
                        if (pending <= 0) {
                            onComplete?.invoke()
                        }
                    }
                }
            } else {
                onComplete?.invoke()
            }
        }
    }

    companion object {
        const val PRODUCT_HONEY_1000 = "honeypack1000"
        const val PRODUCT_HONEY_500 = "honeypack500"
        const val PRODUCT_HONEY_100 = "honeypack100"
        const val PRODUCT_REMOVE_ADS = "removeads"
        
        const val PRODUCT_UNLOCK_CLASSIC = "unlockalllevels"
        const val PRODUCT_UNLOCK_CHASER = "unlockchaser"
        const val PRODUCT_UNLOCK_TRAP = "unlocktrap"
        const val PRODUCT_UNLOCK_DARKNESS = "unlockdarkness"
        const val PRODUCT_UNLOCK_LAVA = "unlocklava"
        const val PRODUCT_UNLOCK_ICESLIDE = "unlockiceslide"
        const val PRODUCT_UNLOCK_TIME = "unlocktime"

        fun getProductIdForMode(mode: com.example.honeycombmaze.game.GameMode): String {
            return when (mode) {
                com.example.honeycombmaze.game.GameMode.CLASSIC -> PRODUCT_UNLOCK_CLASSIC
                com.example.honeycombmaze.game.GameMode.CHASERS -> PRODUCT_UNLOCK_CHASER
                com.example.honeycombmaze.game.GameMode.TRAPS -> PRODUCT_UNLOCK_TRAP
                com.example.honeycombmaze.game.GameMode.DARKNESS -> PRODUCT_UNLOCK_DARKNESS
                com.example.honeycombmaze.game.GameMode.LAVA_FLOOR -> PRODUCT_UNLOCK_LAVA
                com.example.honeycombmaze.game.GameMode.ICE_SLIDE -> PRODUCT_UNLOCK_ICESLIDE
                com.example.honeycombmaze.game.GameMode.TIME_RUSH -> PRODUCT_UNLOCK_TIME
            }
        }

        fun getModeForProductId(productId: String): com.example.honeycombmaze.game.GameMode? {
            return when (productId) {
                PRODUCT_UNLOCK_CLASSIC -> com.example.honeycombmaze.game.GameMode.CLASSIC
                PRODUCT_UNLOCK_CHASER -> com.example.honeycombmaze.game.GameMode.CHASERS
                PRODUCT_UNLOCK_TRAP -> com.example.honeycombmaze.game.GameMode.TRAPS
                PRODUCT_UNLOCK_DARKNESS -> com.example.honeycombmaze.game.GameMode.DARKNESS
                PRODUCT_UNLOCK_LAVA -> com.example.honeycombmaze.game.GameMode.LAVA_FLOOR
                PRODUCT_UNLOCK_ICESLIDE -> com.example.honeycombmaze.game.GameMode.ICE_SLIDE
                PRODUCT_UNLOCK_TIME -> com.example.honeycombmaze.game.GameMode.TIME_RUSH
                else -> null
            }
        }

        val ALL_UNLOCK_PRODUCT_IDS = setOf(
            PRODUCT_REMOVE_ADS,
            PRODUCT_UNLOCK_CLASSIC,
            PRODUCT_UNLOCK_CHASER,
            PRODUCT_UNLOCK_TRAP,
            PRODUCT_UNLOCK_DARKNESS,
            PRODUCT_UNLOCK_LAVA,
            PRODUCT_UNLOCK_ICESLIDE,
            PRODUCT_UNLOCK_TIME
        )
    }
}
