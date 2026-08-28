package com.diamon.ptc;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Gestiona la compra in-app "Quitar Anuncios" usando Google Play Billing Library 8.x.
 *
 * <p>Encapsula la conexión con BillingClient, la consulta de productos,
 * el flujo de compra, el reconocimiento (acknowledge) y la restauración
 * de compras previas. Guarda un flag local en SharedPreferences para
 * decidir rápidamente si mostrar anuncios o no.</p>
 */
public class BillingManager implements PurchasesUpdatedListener {

    private static final String TAG = "BillingManager";

    /** ID del producto configurado en Google Play Console. */
    public static final String PRODUCT_ID = "remove_ads";

    private static final String PREFS_NAME = "billing_prefs";
    private static final String KEY_ADS_REMOVED = "ads_removed";

    /** Callback que notifica cambios de estado y precio. */
    public interface BillingListener {
        void onAdsRemovedChanged(boolean adsRemoved);
        void onPriceLoaded(String formattedPrice);
    }

    private final Context context;
    private final BillingClient billingClient;
    @Nullable
    private BillingListener listener;
    @Nullable
    private ProductDetails removeAdsDetails;
    private boolean isServiceConnected = false;

    public BillingManager(@NonNull Context context, @Nullable BillingListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;

        // Billing 8.x requiere PendingPurchasesParams explícito con enableOneTimeProducts()
        billingClient = BillingClient.newBuilder(this.context)
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build()
                )
                .build();
    }

    // ──────────────────────────────────────────────────────────────
    //  Conexión
    // ──────────────────────────────────────────────────────────────

    /** Inicia la conexión con Google Play Billing. */
    public void startConnection() {
        if (billingClient.isReady()) {
            queryActivePurchases();
            queryProducts();
            return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    isServiceConnected = true;
                    Log.d(TAG, "Billing client connected.");
                    // Restaurar compras existentes y obtener detalles del producto
                    queryActivePurchases();
                    queryProducts();
                } else {
                    isServiceConnected = false;
                    Log.w(TAG, "Billing setup failed: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                isServiceConnected = false;
                Log.w(TAG, "Billing service disconnected. Will retry on next request.");
            }
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  Consulta de productos
    // ──────────────────────────────────────────────────────────────

    /** Consulta los detalles del producto remove_ads en Google Play. */
    private void queryProducts() {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, queryResult) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && queryResult != null
                    && queryResult.getProductDetailsList() != null
                    && !queryResult.getProductDetailsList().isEmpty()) {
                removeAdsDetails = queryResult.getProductDetailsList().get(0);
                Log.d(TAG, "Product details loaded: " + removeAdsDetails.getName());
                
                ProductDetails.OneTimePurchaseOfferDetails offerDetails = removeAdsDetails.getOneTimePurchaseOfferDetails();
                if (offerDetails != null && listener != null) {
                    listener.onPriceLoaded(offerDetails.getFormattedPrice());
                }
            } else {
                removeAdsDetails = null;
                Log.w(TAG, "Failed to load product details: " + billingResult.getDebugMessage());
            }
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  Flujo de compra
    // ──────────────────────────────────────────────────────────────

    /**
     * Lanza el flujo de compra de Google Play para el producto "Quitar Anuncios".
     *
     * @param activity Activity desde donde se lanza (necesaria para la UI de Play).
     * @return {@code true} si el flujo se lanzó correctamente,
     *         {@code false} si el producto no está disponible o el servicio no está listo.
     */
    public boolean launchPurchaseFlow(@NonNull Activity activity) {
        if (removeAdsDetails == null) {
            Log.w(TAG, "Product details not available. Cannot launch purchase flow.");
            return false;
        }

        BillingFlowParams.ProductDetailsParams productDetailsParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(removeAdsDetails)
                        .build();

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productDetailsParams))
                .build();

        BillingResult result = billingClient.launchBillingFlow(activity, billingFlowParams);
        return result.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    // ──────────────────────────────────────────────────────────────
    //  Procesamiento de compras
    // ──────────────────────────────────────────────────────────────

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult,
                                   @Nullable List<Purchase> purchases) {
        int responseCode = billingResult.getResponseCode();

        if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases);
        } else if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase.");
        } else if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            // El usuario ya tiene la compra — restaurar el flag
            Log.d(TAG, "Item already owned. Restoring...");
            setAdsRemoved(true);
        } else {
            Log.w(TAG, "Purchase update error: " + billingResult.getDebugMessage());
        }
    }

    /**
     * Procesa la lista de compras: si contiene remove_ads, hace acknowledge
     * (si no está reconocida) y activa el flag local.
     */
    private void handlePurchases(@NonNull List<Purchase> purchases) {
        for (Purchase purchase : purchases) {
            if (purchase.getProducts().contains(PRODUCT_ID)) {
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    if (!purchase.isAcknowledged()) {
                        acknowledgePurchase(purchase);
                    } else {
                        setAdsRemoved(true);
                    }
                } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    Log.d(TAG, "Purchase is pending. Ads will be removed once completed.");
                }
            }
        }
    }

    /**
     * Reconoce (acknowledge) una compra para que Google Play no la revoque
     * automáticamente después de 3 días.
     */
    private void acknowledgePurchase(@NonNull Purchase purchase) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.acknowledgePurchase(params, billingResult -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully.");
                setAdsRemoved(true);
            } else {
                Log.w(TAG, "Failed to acknowledge purchase: " + billingResult.getDebugMessage());
            }
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  Restauración de compras
    // ──────────────────────────────────────────────────────────────

    /**
     * Consulta las compras activas del usuario para restaurar la compra
     * en caso de reinstalación o cambio de dispositivo.
     */
    private void queryActivePurchases() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                boolean hasRemoveAds = false;
                for (Purchase purchase : purchases) {
                    if (purchase.getProducts().contains(PRODUCT_ID)
                            && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                        hasRemoveAds = true;
                        // Acknowledge si todavía no está reconocida
                        if (!purchase.isAcknowledged()) {
                            acknowledgePurchase(purchase);
                        }
                        break;
                    }
                }
                setAdsRemoved(hasRemoveAds);
            } else {
                Log.w(TAG, "Failed to query purchases: " + billingResult.getDebugMessage());
            }
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  Flag local (SharedPreferences)
    // ──────────────────────────────────────────────────────────────

    /**
     * Guarda el estado de "anuncios eliminados" en SharedPreferences
     * y notifica al listener.
     */
    private void setAdsRemoved(boolean value) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean previousValue = prefs.getBoolean(KEY_ADS_REMOVED, false);

        prefs.edit().putBoolean(KEY_ADS_REMOVED, value).apply();

        // Solo notificar si el valor cambió
        if (value != previousValue && listener != null) {
            listener.onAdsRemovedChanged(value);
        }
    }

    /**
     * Comprueba si se deben mostrar anuncios.
     *
     * @param context Contexto de la aplicación.
     * @return {@code true} si se deben mostrar anuncios (no se ha comprado remove_ads),
     *         {@code false} si el usuario ya compró.
     */
    public static boolean shouldShowAds(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !prefs.getBoolean(KEY_ADS_REMOVED, false);
    }

    /**
     * Indica si los detalles del producto están cargados y disponibles para compra.
     */
    public boolean isProductAvailable() {
        return removeAdsDetails != null;
    }

    /**
     * Indica si el servicio de billing está conectado.
     */
    public boolean isServiceConnected() {
        return isServiceConnected;
    }

    /**
     * Desconecta el BillingClient. Llamar en onDestroy de la Activity.
     */
    public void destroy() {
        if (billingClient.isReady()) {
            billingClient.endConnection();
        }
    }
}
