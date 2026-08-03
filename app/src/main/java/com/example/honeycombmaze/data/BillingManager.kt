package com.example.honeycombmaze.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*

class BillingManager(
    private val context: Context,
    private val prefsManager: PreferencesManager,
    private val onHoneyPurchased: (Int) -> Unit = {},
    private val onAllLevelsUnlocked: () -> Unit = {}
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

                activity.runOnUiThread {
                    billingClient.launchBillingFlow(activity, flowParams)
                }
            } else {
                val errorMsg = if (productDetailsList.isEmpty() && billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    "Product '$productId' not found in Play Console. Ensure it is Activated in Play Console and test account is registered under License Testing."
                } else {
                    "Billing Error: ${billingResult.debugMessage} (Code: ${billingResult.responseCode})"
                }
                Log.e("BillingManager", errorMsg)
                activity.runOnUiThread {
                    android.widget.Toast.makeText(activity, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
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
                    when (productId) {
                        PRODUCT_HONEY_1000 -> {
                            prefsManager.honey += 1000
                            onHoneyPurchased(1000)
                            consumePurchase(purchase)
                            android.widget.Toast.makeText(context, "Success! 1000 Honey Coins Added! 🍯", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        PRODUCT_HONEY_500 -> {
                            prefsManager.honey += 500
                            onHoneyPurchased(500)
                            consumePurchase(purchase)
                            android.widget.Toast.makeText(context, "Success! 500 Honey Coins Added! 🍯", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        PRODUCT_HONEY_100 -> {
                            prefsManager.honey += 100
                            onHoneyPurchased(100)
                            consumePurchase(purchase)
                            android.widget.Toast.makeText(context, "Success! 100 Honey Coins Added! 🍯", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        PRODUCT_REMOVE_ADS -> {
                            prefsManager.isRemoveAdsPurchased = true
                            acknowledgePurchase(purchase)
                            android.widget.Toast.makeText(context, "Success! Ads Removed! 🚫", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        PRODUCT_UNLOCK_ALL_LEVELS -> {
                            prefsManager.isAllLevelsUnlocked = true
                            onAllLevelsUnlocked()
                            acknowledgePurchase(purchase)
                            android.widget.Toast.makeText(context, "Success! All Levels Unlocked! 🔓🎉", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            // Default fallback: acknowledge purchase
                            acknowledgePurchase(purchase)
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
                for (purchase in purchases) {
                    handlePurchase(purchase)
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
                        if (productId == PRODUCT_REMOVE_ADS) {
                            prefsManager.isRemoveAdsPurchased = true
                            prefsManager.markPurchaseTokenProcessed(record.purchaseToken)
                        } else if (productId == PRODUCT_UNLOCK_ALL_LEVELS) {
                            prefsManager.isAllLevelsUnlocked = true
                            onAllLevelsUnlocked()
                            prefsManager.markPurchaseTokenProcessed(record.purchaseToken)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val PRODUCT_HONEY_1000 = "honeypack1000"
        const val PRODUCT_HONEY_500 = "honeypack500"
        const val PRODUCT_HONEY_100 = "honeypack100"
        const val PRODUCT_REMOVE_ADS = "removeads"
        const val PRODUCT_UNLOCK_ALL_LEVELS = "unlock_all_levels"
    }
}
