# Validación de mejoras AVIX

La suite automatizada cubre o prepara cobertura para:

- guardado local sin esperar una petición HTTP;
- idempotencia del mismo UUID;
- rechazo de un UUID reutilizado con contenido diferente;
- aislamiento de historial y cola al cambiar de operador;
- error HTTP 400 como registro que requiere revisión;
- error HTTP 503 como reintento temporal;
- error HTTP 401 como sesión expirada;
- migración SQLite 1 → 2 sin pérdida del registro anterior;
- filtros de placa y fechas;
- restauración del borrador y de su UUID;
- parser de placas difíciles y variantes fonéticas;
- VAD adaptativo.

## Pruebas manuales necesarias

El reconocimiento de voz, el overlay y la interacción con el motor SpeechRecognizer dependen del dispositivo. Antes de distribuir, validar en teléfonos reales:

- ambiente silencioso;
- ruido medio;
- ruido fuerte;
- pantalla apagada/encendida;
- cambio de app con burbuja activa;
- permiso de overlay revocado;
- permiso de micrófono revocado;
- reconexión luego de modo avión.

## Registros antiguos

Las filas de Room versión 1 no contienen operador/plaza/servidor verificables. La migración las conserva con esas columnas en NULL.

No deben asignarse masivamente al usuario activo. Una recuperación administrativa debe contrastar el UUID con SIGO y con el operador real del turno/dispositivo.
