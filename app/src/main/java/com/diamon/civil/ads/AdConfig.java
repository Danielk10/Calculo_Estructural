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
 */
public final class AdConfig {

    private AdConfig() {
    }

    /** Banner adaptativo anclado en la parte inferior. */
    public static final String BANNER_AD_UNIT_ID = "ca-app-pub-5141499161332805/9369971905";

    /** Intersticial con límite de frecuencia de 3 minutos. */
    public static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5141499161332805/8693304153";
}
