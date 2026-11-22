package com.diamon.civil.ads;

/**
 * Configuración central de Google AdMob para Cálculo Estructural.
 *
 * App ID: ca-app-pub-5141499161332805~5700393084
 * (declarado en AndroidManifest.xml como @string/admob_app_id)
 *
 * Bloques reales creados en AdMob:
 * - Banner:       ca-app-pub-5141499161332805/9369971905
 * - Intersticial: ca-app-pub-5141499161332805/8693304153
 * - Nativo:       ca-app-pub-5141499161332805/4375469982 (NativvoFEA)
 */
public final class AdConfig {

    private AdConfig() {
    }

    /** Banner adaptativo anclado en la parte inferior. */
    public static final String BANNER_AD_UNIT_ID = "ca-app-pub-5141499161332805/9369971905";

    /** Intersticial con límite de frecuencia de 3 minutos. */
    public static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5141499161332805/8693304153";

    /**
     * Anuncio nativo. La misma unidad se usa en las pantallas de Cálculo
     * Estructural y Sólidos 3D, pero cada pantalla hace su propia petición y
     * muestra su propia instancia (un NativeAd solo puede estar en un
     * NativeAdView a la vez).
     */
    public static final String NATIVE_AD_UNIT_ID = "ca-app-pub-5141499161332805/4375469982";
}
