package com.diamon.civil.ads;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.diamon.civil.R;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;

/**
 * Carga y muestra un anuncio nativo compacto dentro de un {@link FrameLayout}.
 *
 * <p>Se usa una instancia por pantalla (Cálculo Estructural y Sólidos 3D)
 * aunque compartan el mismo ID de unidad de AdMob: un {@link NativeAd} solo
 * puede estar asociado a un {@link NativeAdView} a la vez.</p>
 *
 * <p>El contenedor permanece oculto hasta que AdMob confirma la carga, de modo
 * que nunca queda un hueco vacío en la interfaz. Si no hay relleno se reintenta
 * unas pocas veces y, si el usuario aún no ha resuelto el consentimiento UMP,
 * la petición espera a que esté disponible.</p>
 */
public class NativeAdHelper {

    private static final String TAG = "NativeAdHelper";

    /** Espera entre reintentos cuando AdMob no devuelve relleno. */
    private static final long RETRY_MS = 45 * 1000L;
    private static final int MAX_RETRIES = 3;

    private final FrameLayout container;
    private final int layoutResId;
    @Nullable
    private final View spacer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private NativeAd nativeAd;
    private NativeAdView adView;
    private boolean loading = false;
    private boolean loaded = false;
    private boolean destroyed = false;
    private boolean waitingForConsent = false;
    private int retries = 0;

    private final Runnable retryRunnable = this::load;

    /** Se ejecuta cuando el consentimiento UMP queda resuelto. */
    private final Runnable consentListener = () -> {
        waitingForConsent = false;
        if (!destroyed) {
            load();
        }
    };

    public NativeAdHelper(@NonNull FrameLayout container) {
        this(container, R.layout.layout_native_ad, null);
    }

    /**
     * @param container  contenedor donde se inserta el anuncio
     * @param layoutResId layout del anuncio (compacto o grande)
     * @param spacer     vista de relleno que se oculta mientras el anuncio está
     *                   visible (opcional)
     */
    public NativeAdHelper(@NonNull FrameLayout container, int layoutResId, @Nullable View spacer) {
        this.container = container;
        this.layoutResId = layoutResId;
        this.spacer = spacer;
        this.container.setVisibility(View.GONE);
    }

    /** Pide el anuncio si aún no se ha cargado. Es seguro llamarlo varias veces. */
    public void load() {
        if (destroyed || loading || loaded) {
            return;
        }

        final Activity activity = activity();
        if (activity == null) {
            return;
        }

        if (!ConsentManager.isResolved()) {
            if (waitingForConsent) {
                return;
            }
            // No se lanza el flujo UMP desde aquí: lo inicia MainActivity
            // cuando termina la carga de recursos. Solo se espera el aviso.
            waitingForConsent = true;
            ConsentManager.addOnConsentResolvedListener(consentListener);
            return;
        }

        if (!ConsentManager.canRequestAds()) {
            hideContainer();
            return;
        }

        loading = true;
        AdLoader adLoader = new AdLoader.Builder(activity, AdConfig.NATIVE_AD_UNIT_ID)
                .forNativeAd(ad -> {
                    loading = false;
                    if (destroyed) {
                        ad.destroy();
                        return;
                    }
                    retries = 0;
                    if (nativeAd != null) {
                        nativeAd.destroy();
                    }
                    nativeAd = ad;
                    populate(activity, ad);
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        loading = false;
                        Log.w(TAG, "Nativo sin relleno: código " + loadAdError.getCode()
                                + " - " + loadAdError.getMessage());
                        hideContainer();
                        scheduleRetry();
                    }
                })
                .withNativeAdOptions(new NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build())
                .build();
        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void scheduleRetry() {
        if (destroyed || retries >= MAX_RETRIES) {
            return;
        }
        retries++;
        mainHandler.removeCallbacks(retryRunnable);
        mainHandler.postDelayed(retryRunnable, RETRY_MS * retries);
    }

    private void populate(@NonNull Activity activity, @NonNull NativeAd ad) {
        container.removeAllViews();

        View root = activity.getLayoutInflater()
                .inflate(layoutResId, container, false);
        adView = root.findViewById(R.id.nativeAdView);

        TextView headlineView = adView.findViewById(R.id.ad_headline);
        TextView bodyView = adView.findViewById(R.id.ad_body);
        TextView advertiserView = adView.findViewById(R.id.ad_advertiser);
        Button ctaView = adView.findViewById(R.id.ad_call_to_action);
        ImageView iconView = adView.findViewById(R.id.ad_app_icon);
        com.google.android.gms.ads.nativead.MediaView mediaView =
                root.findViewById(R.id.ad_media);

        adView.setHeadlineView(headlineView);
        adView.setBodyView(bodyView);
        adView.setAdvertiserView(advertiserView);
        adView.setCallToActionView(ctaView);
        adView.setIconView(iconView);

        if (mediaView != null) {
            adView.setMediaView(mediaView);
            // Sin contenido multimedia no se deja un hueco vacío.
            mediaView.setVisibility(ad.getMediaContent() == null ? View.GONE : View.VISIBLE);
        }

        if (headlineView != null) {
            headlineView.setText(ad.getHeadline());
        }

        if (bodyView != null) {
            if (ad.getBody() == null) {
                bodyView.setVisibility(View.GONE);
            } else {
                bodyView.setVisibility(View.VISIBLE);
                bodyView.setText(ad.getBody());
            }
        }

        if (advertiserView != null) {
            if (ad.getAdvertiser() == null) {
                advertiserView.setVisibility(View.GONE);
            } else {
                advertiserView.setVisibility(View.VISIBLE);
                advertiserView.setText(ad.getAdvertiser());
            }
        }

        if (ctaView != null) {
            if (ad.getCallToAction() == null) {
                ctaView.setVisibility(View.INVISIBLE);
            } else {
                ctaView.setVisibility(View.VISIBLE);
                ctaView.setText(ad.getCallToAction());
            }
        }

        if (iconView != null) {
            if (ad.getIcon() == null || ad.getIcon().getDrawable() == null) {
                iconView.setVisibility(View.GONE);
            } else {
                iconView.setVisibility(View.VISIBLE);
                iconView.setImageDrawable(ad.getIcon().getDrawable());
            }
        }

        adView.setNativeAd(ad);
        container.addView(root);
        if (spacer != null) {
            spacer.setVisibility(View.GONE);
        }
        container.setVisibility(View.VISIBLE);
        loaded = true;
        Log.d(TAG, "Nativo cargado.");
    }

    /** Oculta el anuncio y devuelve el espacio flexible a la pantalla. */
    private void hideContainer() {
        container.setVisibility(View.GONE);
        if (spacer != null) {
            spacer.setVisibility(View.VISIBLE);
        }
    }

    @Nullable
    private Activity activity() {
        if (container.getContext() instanceof Activity) {
            Activity activity = (Activity) container.getContext();
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                return activity;
            }
        }
        return null;
    }

    /** Libera el anuncio y cancela reintentos. Llamar desde onDestroyView(). */
    public void destroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        ConsentManager.removeOnConsentResolvedListener(consentListener);
        hideContainer();
        container.removeAllViews();
        adView = null;
        if (nativeAd != null) {
            nativeAd.destroy();
            nativeAd = null;
        }
        loaded = false;
        loading = false;
    }
}
