package com.diamon.civil.ads;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowMetrics;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/**
 * Gestor de publicidad de Cálculo Estructural.
 *
 * Integra:
 * - Banner adaptativo anclado en la parte inferior.
 * - Anuncio intersticial con límite de frecuencia (3 minutos).
 * - Consentimiento UMP antes de inicializar MobileAds o pedir anuncios.
 *
 * La app es 100% gratuita y no tiene compras integradas: la publicidad es el
 * único mecanismo de financiación.
 */
public class AdsManager {

    private static final String TAG = "AdsManager";

    /** Tiempo mínimo entre dos intersticiales. */
    private static final long INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L;
    /** Los anuncios cargados se descartan a la hora. */
    private static final long AD_TTL_MS = 60 * 60 * 1000L;
    /** Si se pide un intersticial y aún no ha cargado, se espera como máximo esto. */
    private static final long INTERSTITIAL_PENDING_TIMEOUT_MS = 20 * 1000L;

    private static volatile boolean mobileAdsInitialized = false;

    private final Activity activity;
    private final FrameLayout bannerContainer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AdView adView;
    private boolean bannerLoading = false;
    private boolean bannerLoaded = false;
    private long bannerLoadedAt = 0;

    private InterstitialAd interstitialAd;
    private boolean interstitialLoading = false;
    private long interstitialLoadedAt = 0;
    private long lastInterstitialShownAt = 0;
    private boolean interstitialShowPending = false;
    private long interstitialPendingSince = 0;

    private boolean destroyed = false;
    private boolean activityPaused = false;
    private boolean canRequestAds = false;

    public AdsManager(Activity activity, FrameLayout bannerContainer) {
        this.activity = activity;
        this.bannerContainer = bannerContainer;
        this.bannerContainer.setVisibility(View.GONE);

        createBanner();

        ConsentManager.requestConsent(activity, canRequest -> {
            if (destroyed) {
                return;
            }
            canRequestAds = canRequest;
            activity.invalidateOptionsMenu();

            if (canRequest) {
                initializeMobileAds();
                loadBanner();
            } else {
                hideBanner();
            }
        });
    }

    // ------------------------------------------------------------------
    // Banner
    // ------------------------------------------------------------------

    private void createBanner() {
        adView = new AdView(activity);
        adView.setAdUnitId(AdConfig.BANNER_AD_UNIT_ID);
        adView.setAdSize(getAdaptiveBannerAdSize(activity));
        adView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                bannerLoading = false;
                bannerLoaded = true;
                bannerLoadedAt = System.currentTimeMillis();
                if (!destroyed && bannerContainer != null) {
                    bannerContainer.setVisibility(View.VISIBLE);
                }
                Log.d(TAG, "Banner cargado.");
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                bannerLoading = false;
                bannerLoaded = false;
                Log.w(TAG, "Error cargando banner: código " + loadAdError.getCode()
                        + " - " + loadAdError.getMessage());
            }
        });

        if (bannerContainer != null) {
            bannerContainer.addView(adView);
        }
    }

    private void loadBanner() {
        if (destroyed || !canRequestAds || adView == null || bannerLoading) {
            return;
        }
        if (bannerLoaded && bannerLoadedAt > 0
                && System.currentTimeMillis() - bannerLoadedAt < AD_TTL_MS) {
            if (bannerContainer != null) {
                bannerContainer.setVisibility(View.VISIBLE);
            }
            return;
        }
        bannerLoading = true;
        adView.loadAd(new AdRequest.Builder().build());
    }

    private void hideBanner() {
        if (bannerContainer != null) {
            bannerContainer.setVisibility(View.GONE);
        }
    }

    @SuppressWarnings("deprecation")
    private AdSize getAdaptiveBannerAdSize(Activity activity) {
        try {
            int adWidthPixels;
            DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
                adWidthPixels = windowMetrics.getBounds().width();
            } else {
                adWidthPixels = displayMetrics.widthPixels;
            }
            int adWidth = (int) (adWidthPixels / displayMetrics.density);
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo calcular el banner adaptativo; se usa BANNER.", e);
            return AdSize.BANNER;
        }
    }

    // ------------------------------------------------------------------
    // Intersticial
    // ------------------------------------------------------------------

    /**
     * Muestra un intersticial si ya hay uno cargado y ha pasado el tiempo
     * mínimo desde el último. Si no hay ninguno, lo solicita y lo muestra en
     * cuanto llegue (con un límite de espera de 20 segundos).
     */
    public void maybeShowInterstitial() {
        if (destroyed || activityPaused || !canRequestAds) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastInterstitialShownAt < INTERSTITIAL_COOLDOWN_MS) {
            return;
        }

        if (isInterstitialFresh()) {
            lastInterstitialShownAt = now;
            interstitialAd.show(activity);
            return;
        }

        interstitialShowPending = true;
        interstitialPendingSince = now;
        loadInterstitial();
    }

    private void loadInterstitial() {
        if (destroyed || !canRequestAds || interstitialLoading || isInterstitialFresh()) {
            return;
        }

        interstitialLoading = true;
        InterstitialAd.load(
                activity,
                AdConfig.INTERSTITIAL_AD_UNIT_ID,
                new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        interstitialAd = ad;
                        interstitialLoadedAt = System.currentTimeMillis();
                        interstitialLoading = false;

                        interstitialAd.setFullScreenContentCallback(
                                new FullScreenContentCallback() {
                                    @Override
                                    public void onAdDismissedFullScreenContent() {
                                        interstitialAd = null;
                                        interstitialLoadedAt = 0;
                                    }

                                    @Override
                                    public void onAdFailedToShowFullScreenContent(
                                            @NonNull AdError adError) {
                                        Log.w(TAG, "Fallo al mostrar intersticial: "
                                                + adError.getMessage());
                                        interstitialAd = null;
                                        interstitialLoadedAt = 0;
                                    }
                                });

                        if (interstitialShowPending) {
                            long elapsed = System.currentTimeMillis() - interstitialPendingSince;
                            interstitialShowPending = false;
                            if (elapsed <= INTERSTITIAL_PENDING_TIMEOUT_MS
                                    && !activityPaused && !activity.isFinishing()
                                    && !activity.isDestroyed()) {
                                lastInterstitialShownAt = System.currentTimeMillis();
                                ad.show(activity);
                            }
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        interstitialAd = null;
                        interstitialLoadedAt = 0;
                        interstitialLoading = false;
                        interstitialShowPending = false;
                        Log.w(TAG, "Error cargando intersticial: código "
                                + loadAdError.getCode() + " - " + loadAdError.getMessage());
                    }
                });
    }

    private boolean isInterstitialFresh() {
        return interstitialAd != null
                && interstitialLoadedAt > 0
                && System.currentTimeMillis() - interstitialLoadedAt < AD_TTL_MS;
    }

    // ------------------------------------------------------------------
    // Privacidad
    // ------------------------------------------------------------------

    public boolean isPrivacyOptionsRequired() {
        return ConsentManager.isPrivacyOptionsRequired();
    }

    public void showPrivacyOptions() {
        ConsentManager.showPrivacyOptionsForm(activity, error -> {
            canRequestAds = ConsentManager.canRequestAds();
            activity.invalidateOptionsMenu();
            if (canRequestAds) {
                initializeMobileAds();
                loadBanner();
            } else {
                hideBanner();
            }
        });
    }

    // ------------------------------------------------------------------
    // Ciclo de vida
    // ------------------------------------------------------------------

    public void onResume() {
        activityPaused = false;
        if (adView != null) {
            adView.resume();
        }
        if (canRequestAds && !bannerLoaded) {
            loadBanner();
        }
    }

    public void onPause() {
        activityPaused = true;
        if (adView != null) {
            adView.pause();
        }
    }

    public void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (adView != null) {
            adView.destroy();
            adView = null;
        }
        interstitialAd = null;
        interstitialLoadedAt = 0;
    }

    private void initializeMobileAds() {
        if (mobileAdsInitialized) {
            return;
        }
        mobileAdsInitialized = true;
        MobileAds.initialize(activity, initializationStatus ->
                Log.d(TAG, "MobileAds inicializado."));
    }
}
