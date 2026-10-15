# AVIX — registro de incidencias por voz

Aplicación Android para registrar eventos **FUGA / DERIVADO**, vía y placa. Incluye reconocimiento de voz, burbuja flotante, almacenamiento local Room y sincronización con SIGO.

## Compilar y verificar

Requisitos principales:

- JDK 21
- Android SDK con `platforms;android-36.1`
- Build Tools 36.0.0

Configure `local.properties` con `sdk.dir=/ruta/al/Android/sdk` o use `ANDROID_HOME`.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

El Wrapper fija Gradle 9.3.1 y verifica el SHA-256 de la distribución. La compilación debug usa la firma estándar de Android; no necesita un keystore privado.

GitHub Actions ejecuta pruebas, lint y compilación del APK debug.

## Guardado y sincronización

AVIX trabaja con enfoque **offline-first**:

- Confirmar un registro significa que quedó guardado en Room.
- La UI no espera a que SIGO responda.
- Cada borrador conserva un UUID. Repetir la misma operación usa el mismo identificador.
- Los estados visibles son:
  - **Guardado en dispositivo**
  - **Enviando**
  - **Confirmado por SIGO**
  - **Requiere revisión**
- WorkManager procesa la cola en segundo plano.
- Errores temporales (red, 408, 429, 5xx) se reintentan.
- Rechazos permanentes, como 400/409, quedan en **Requiere revisión**.
- Un 401 invalida la sesión y pausa la cola hasta autenticarse otra vez.

El backend debe respetar idempotencia por UUID: reenviar el mismo UUID con el mismo contenido debe devolver el registro existente.

## Separación por operador

Cada incidencia local conserva:

- operador autenticado;
- plaza;
- servidor de origen.

El historial y la cola se filtran por esa identidad. Un operador no ve ni envía registros locales de otro.

Los registros creados por versiones anteriores no tenían propietario verificable. La migración Room 1 → 2 los conserva, pero no los atribuye al siguiente usuario ni los sincroniza automáticamente.

## Seguridad de sesión

El token se almacena con Android Keystore mediante `EncryptedSharedPreferences`.

Si Keystore no está disponible:

- no se guarda el token en texto plano;
- la sesión permanece solo en memoria.

Las preferencias de sesión se excluyen de Android Backup y de la transferencia entre dispositivos.

## Borradores y doble pulsación

La pantalla de confirmación usa `RegistrationViewModel` + `SavedStateHandle` para conservar:

- UUID;
- hora del evento;
- placa;
- vía;
- acción;
- texto original;
- propietario esperado.

Mientras se realiza la escritura local, una segunda pulsación del botón es ignorada.

La burbuja aplica la misma garantía con un UUID estable por operación y bloqueo durante el guardado.

## Historial

El historial permite:

- buscar por placa;
- filtrar por fecha inicial/final;
- separar FUGA y DERIVADO;
- ver pendientes;
- ver registros que requieren revisión.

## Voz

AVIX conserva dos capas:

- **Dictado original:** primera transcripción real devuelta por Android.
- **Interpretación AVIX:** resultado normalizado y fusionado usado para acción, vía y placa.

La app también conserva confianza por posición de placa para resaltar caracteres dudosos.

En ruido alto, AVIX puede usar un segundo intento con:

- `VOICE_RECOGNITION`;
- `NoiseSuppressor`;
- calibración del ruido;
- VAD adaptativo;
- cierre automático por silencio.

## Migraciones

La base local usa Room versión 2. La migración 1 → 2 agrega las columnas de propiedad sin borrar las incidencias antiguas.

No se usa `fallbackToDestructiveMigration`.

## Estructura principal

- `data/`: Room, repositorio y sincronización.
- `domain/`: propiedad de incidencias y filtros de historial.
- `core/security/`: sesión segura.
- `ui/registration/`: borrador persistente y confianza de placa.
- `voice/` y `parser/`: reconocimiento, VAD y parser contextual.
- `worker/`: cola WorkManager.

## Validación antes de distribuir

Además de CI, probar en un teléfono Android real:

1. micrófono y permisos de overlay;
2. ruido medio y ruido alto;
3. modo avión y recuperación de red;
4. expiración de sesión;
5. cambio de operador;
6. doble pulsación al guardar;
7. cierre/reinicio de la app;
8. migración desde una instalación anterior;
9. comportamiento de SIGO ante reenvío del mismo UUID.
