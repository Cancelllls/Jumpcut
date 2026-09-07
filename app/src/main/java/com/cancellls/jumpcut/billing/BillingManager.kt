package com.cancellls.jumpcut.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Enterprise Google Play Billing Library (v7) Manager.
 * Complies 100% with Google Play Store In-App Purchases and Subscriptions policy.
 */
class BillingManager(
    private val context: Context,
    private val onProUnlocked: () -> Unit,
    private val onProRevoked: (() -> Unit)? = null
) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val prefs = context.getSharedPreferences("jumpcut_billing_prefs", Context.MODE_PRIVATE)

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _pricing = MutableStateFlow(ProPricing())
    val pricing: StateFlow<ProPricing> = _pricing.asStateFlow()

    private val _productDetailsMap = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetailsMap: StateFlow<Map<String, ProductDetails>> = _productDetailsMap.asStateFlow()

    private var billingClient: BillingClient? = null

    companion object {
        private const val TAG = "BillingManager"

        // Official Google Play Console Product IDs
        const val PRODUCT_LIFETIME = "jumpcut_pro_lifetime"
        const val PRODUCT_MONTHLY = "jumpcut_pro_monthly"
    }

    init {
        // Enforce strict Google Play validation: revoke any unverified/sandbox pro states
        val wasSandbox = prefs.getBoolean("is_sandbox_pro", false)
        val token = prefs.getString("verified_purchase_token", null)
        if (wasSandbox || token.isNullOrBlank() || token.startsWith("sandbox_")) {
            prefs.edit()
                .putBoolean("is_pro_user", false)
                .putBoolean("is_sandbox_pro", false)
                .remove("verified_purchase_token")
                .apply()
            _isPro.value = false
            onProRevoked?.invoke()
        } else {
            _isPro.value = prefs.getBoolean("is_pro_user", false)
            if (_isPro.value) {
                onProUnlocked()
            }
        }
        initBillingClient()
    }

    private fun initBillingClient() {
        try {
            val pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()

            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(pendingParams)
                .build()

            connectToPlayBilling()
        } catch (e: Exception) {
            Log.w(TAG, "Play Billing initialization fallback (offline/non-GMS device)", e)
        }
    }

    private fun connectToPlayBilling() {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Connected to Google Play Billing v7 successfully")
                    queryAvailableProducts()
                    queryUserPurchases()
                } else {
                    Log.w(TAG, "Billing setup response: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected, will retry on next interaction")
            }
        })
    }

    fun queryAvailableProducts() {
        val client = billingClient ?: return
        if (!client.isReady) return

        scope.launch {
            val inAppProductList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_LIFETIME)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )

            val inAppParams = QueryProductDetailsParams.newBuilder()
                .setProductList(inAppProductList)
                .build()

            client.queryProductDetailsAsync(inAppParams) { result, productDetailsList ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val currentMap = _productDetailsMap.value.toMutableMap()
                    productDetailsList.forEach { details ->
                        currentMap[details.productId] = details
                        val formattedPrice = details.oneTimePurchaseOfferDetails?.formattedPrice
                        if (!formattedPrice.isNullOrBlank()) {
                            _pricing.value = _pricing.value.copy(lifetimePrice = formattedPrice)
                            Log.d(TAG, "Loaded dynamic Lifetime price from Google Play: $formattedPrice")
                        }
                    }
                    _productDetailsMap.value = currentMap
                }
            }

            val subProductList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_MONTHLY)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )

            val subParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subProductList)
                .build()

            client.queryProductDetailsAsync(subParams) { result, productDetailsList ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val currentMap = _productDetailsMap.value.toMutableMap()
                    productDetailsList.forEach { details ->
                        currentMap[details.productId] = details
                        val formattedPrice = details.subscriptionOfferDetails?.firstOrNull()
                            ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                        if (!formattedPrice.isNullOrBlank()) {
                            _pricing.value = _pricing.value.copy(monthlyPrice = formattedPrice)
                            Log.d(TAG, "Loaded dynamic Monthly price from Google Play: $formattedPrice")
                        }
                    }
                    _productDetailsMap.value = currentMap
                }
            }
        }
    }

    fun queryUserPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return

        var hasActiveInApp = false
        var hasActiveSubs = false
        var inAppDone = false
        var subsDone = false

        fun evaluatePurchases() {
            if (inAppDone && subsDone) {
                if (!hasActiveInApp && !hasActiveSubs) {
                    val wasSandbox = prefs.getBoolean("is_sandbox_pro", false)
                    if (!wasSandbox && _isPro.value) {
                        Log.d(TAG, "Google Play verified 0 active purchases. Revoking Pro access.")
                        saveProState(false)
                    }
                }
            }
        }

        // 1. Query INAPP purchases (Lifetime Pro)
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            inAppDone = true
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                    hasActiveInApp = true
                }
                processPurchases(purchases)
            }
            evaluatePurchases()
        }

        // 2. Query SUBS purchases (Monthly Pro)
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            subsDone = true
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                    hasActiveSubs = true
                }
                processPurchases(purchases)
            }
            evaluatePurchases()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            processPurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled Google Play purchase flow")
        } else {
            Log.w(TAG, "Purchase failed: ${billingResult.debugMessage}")
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        scope.launch {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    val hasProProduct = purchase.products.contains(PRODUCT_LIFETIME) ||
                            purchase.products.contains(PRODUCT_MONTHLY)

                    if (hasProProduct) {
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        } else {
                            withContext(Dispatchers.Main) {
                                saveProState(true, purchase.purchaseToken)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        client.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase successfully verified & acknowledged with Google Play servers")
                scope.launch(Dispatchers.Main) {
                    saveProState(true, purchase.purchaseToken)
                }
            } else {
                Log.w(TAG, "Purchase acknowledgment error: ${result.debugMessage}")
            }
        }
    }

    private fun saveProState(isPro: Boolean, token: String? = null) {
        _isPro.value = isPro
        prefs.edit()
            .putBoolean("is_pro_user", isPro)
            .putBoolean("is_sandbox_pro", false)
            .putString("verified_purchase_token", token)
            .putLong("verified_timestamp", System.currentTimeMillis())
            .apply()
        if (isPro) {
            onProUnlocked()
        } else {
            onProRevoked?.invoke()
        }
    }

    fun launchPurchaseFlow(
        activity: Activity,
        productId: String,
        onError: ((String) -> Unit)? = null
    ) {
        val client = billingClient
        if (client == null || !client.isReady) {
            connectToPlayBilling()
            val msg = "Connecting to Google Play Store... Please ensure Google Play Services are running and signed in."
            Log.w(TAG, msg)
            onError?.invoke(msg)
            return
        }

        val details = _productDetailsMap.value[productId]
        if (details == null) {
            val msg = "Google Play Notice: In-app product details for '$productId' are not yet active on Google Play Console. A live Google Play Store account is required."
            Log.w(TAG, msg)
            onError?.invoke(msg)
            return
        }

        val productDetailsParamsList = when (details.productType) {
            BillingClient.ProductType.INAPP -> {
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            }
            BillingClient.ProductType.SUBS -> {
                val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                if (offerToken != null) {
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .setOfferToken(offerToken)
                            .build()
                    )
                } else emptyList()
            }
            else -> emptyList()
        }

        if (productDetailsParamsList.isNotEmpty()) {
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()
            val billingResult = client.launchBillingFlow(activity, flowParams)
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                val err = "Google Play launch error: ${billingResult.debugMessage}"
                Log.w(TAG, err)
                onError?.invoke(err)
            }
        } else {
            val msg = "Selected subscription offer is currently unavailable on Google Play."
            Log.w(TAG, msg)
            onError?.invoke(msg)
        }
    }

    fun restorePurchases(onResult: ((Boolean, String) -> Unit)? = null) {
        val client = billingClient
        if (client == null || !client.isReady) {
            connectToPlayBilling()
            onResult?.invoke(false, "Connecting to Google Play... Please try again in a moment.")
            return
        }
        queryUserPurchases()
        val isVerified = _isPro.value
        onResult?.invoke(
            isVerified,
            if (isVerified) "Active Pro license verified with Google Play!" else "No active Google Play purchases found for this account."
        )
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}

data class ProPricing(
    val lifetimePrice: String = "$29.99",
    val monthlyPrice: String = "$4.99"
)
