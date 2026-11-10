# Configuración de AdMob — Cálculo Estructural

La app tiene integrado Google AdMob con:

- `play-services-ads` **25.5.0**
- `user-messaging-platform` (UMP) **4.0.0**
- Banner adaptativo + intersticial con límite de 3 minutos
- Consentimiento RGPD/EE. UU. antes de inicializar MobileAds

## 1. App ID (configurado)

```xml
<string name="admob_app_id">ca-app-pub-5141499161332805~5700393084</string>
```

La app se publica con el paquete `com.diamon.civil`, que es el vinculado a esta
app de AdMob (aparece en el panel como "Structural Analysis FEA 3D").

## 2. Bloques de anuncios (reales, configurados)

En `app/src/main/java/com/diamon/civil/ads/AdConfig.java`:

```java
public static final String BANNER_AD_UNIT_ID = "ca-app-pub-5141499161332805/9369971905";
public static final String INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5141499161332805/8693304153";
```

Corresponden a los bloques **Banner** e **Intersticial** de la app en AdMob.

## 3. Pruebas

- Para probar sin riesgo de clics inválidos, registrar el dispositivo en
  **AdMob → Configuración → Dispositivos de prueba** en lugar de volver a los
  IDs de prueba de Google.

## 4. Licencia GPLv3

Cálculo Estructural se distribuye bajo **GNU GPL v3.0** por integrar CalculiX
2.23. La GPL permite el uso comercial y la monetización con anuncios siempre
que se mantenga disponible el código fuente y se conserven las libertades del
software libre. No hay compras internas ni suscripciones.

## 5. Google Play

- La app declara `INTERNET`, `ACCESS_NETWORK_STATE` y
  `com.google.android.gms.permission.AD_ID`.
- En **Data safety** hay que declarar el uso de identificadores de publicidad.
- La opción **Privacy Options / Opciones de privacidad** aparece en el menú solo
  cuando UMP la considera obligatoria.
- La app no tiene compras internas ni suscripciones.
