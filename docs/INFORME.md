# RabbitGlance 0.1.0 · Primera prueba de Black Hole

Fecha: 16 de octubre de 2026.

## Resultado y alcance

Aplicación independiente `com.isaaclisi.rabbitglance`, con un AppWidget real, configuración por instancia, selección de tamaño, intensidad y velocidad, resplandor, reloj digital/analógico, segundos y estrellas opcionales. Vista previa con reproducción, pausa y reinicio; diagnóstico copiable. No se migran otros temas en esta entrega.

**No hay una validación física en Motorola Edge 50 Ultra / Android 17.** La aceptación del documento, la animación sostenida en Moto App Launcher, su política de visibilidad, FPS y consumo deben comprobarse allí. El informe distingue código, pruebas locales y observación del teléfono.

## Rescate comprobado

Fuente: ZIP de Google Drive `RabbitGlance_codigo_fuente_reloj_3.8.2.zip`, de la carpeta RabbitGlance / 03 - Codigo y documentacion. Commit original: `29d06ad9b1b43a2e6b2df3dcb3f2a1a200f3cf52`.

Se verificaron los SHA-256 de los 94 archivos textuales del ZIP contra su manifiesto. Se leyeron `BlackHoleArt.kt`, `CosmicSceneRenderer.kt`, `ClockThemeRenderer.kt`, `PlatformOriginalClock.kt`, `OriginalWidgetArt.kt`, `CosmicStarField.kt`, preferencias, geometría, reloj y archivos de lente/wallpaper. Se conservan copias intactas de las fuentes principales en `reference/original`.

`ClockThemeRenderer.shapes` devuelve una lista vacía para los temas de escena. Por tanto, BLACK_HOLE **no usaba los pétalos ni las órbitas de discos de Ubuntu**. Añadirlos habría inventado una versión anterior que no existió.

### Componentes recuperados

**Horizonte y silueta:** radio normalizado `.255 + .04 × masa`; esfera opaca con gradiente `#030306 → #020204 → #000001`, centro de luz desplazado, y ribete fino. Se conservan la geometría, el recubrimiento y el orden de capas.

**Disco:** radio externo `.505`, borde interno ligado al horizonte, inclinación `.3` y giro `25°` por defecto. Son parámetros originales, no una elipse genérica añadida. Se conserva la fórmula de turbulencia espiral, remolinos, filamentos elevados a quinta potencia, grano irregular, perfil térmico y caída de los bordes.

**Luz y materiales:** beaming D³ con velocidad artística `.42`, acotado a `.2–2.8`, mezcla térmica hacia blanco, paleta original naranja/ámbar, transparencia exponencial, neblina y resplandor. El arco asimétrico del lado lejano usa su misma deformación y filamentos; el anillo fotónico usa el perfil estrecho original. No se presenta esa aproximación artística como simulación relativista.

**Movimiento de acreción:** 18 estelas por mitad, seis radios, fase áurea `2.399963`, velocidades diferentes según radio, máscaras seno distintas delante/detrás y alfa Doppler dependiente del coseno. Se conserva `BlackHoleArt.nativeFlow` sin sustituirlo por puntos que orbitan un círculo. El código nativo original dibuja cada estela mediante dos segmentos; la variante Canvas usaba un arco de 16°. RabbitGlance conserva la variante nativa que realmente correspondía al widget.

**Profundidad:** ambiente, disco/arco trasero y estelas traseras, esfera central opaca, disco y estelas delanteras, estrellas opcionales, indicadores y reloj. No se rota toda la imagen plana para fingir profundidad.

**Partículas:** el original comprobado contiene estrellas de posición determinista y titileo, además de estelas de disco. Las estrellas estaban desactivadas por defecto. No se encontraron chorros polares, partículas que caigan al horizonte ni un sistema físico de polvo en el widget; no se atribuyen al diseño previo.

**Reloj:** puntos diarios del mes, punto del día, estela de minuto con suavizado, dirección alterna según paridad del minuto, halos/radio/brillo por punto. Reloj digital con formato del sistema, segundos opcionales, esfera analógica y fecha. Se conserva la geometría y el material de estos componentes.

**Rotaciones/oscilaciones:** las estelas rotan a velocidades distintas dentro del plano inclinado y su visibilidad oscila al pasar entre ambas mitades. No se encontró bamboleo global del agujero negro en `CosmicSceneRenderer`; su rotación y respiración global correspondían a STELLAR_NURSERY. No se añaden como supuesto movimiento original.

## Lente gravitacional: separación necesaria

El arco brillante y el anillo fotónico sí estaban dentro de BLACK_HOLE y están preservados.

La deformación de una fotografía o del fondo mediante `gravitational_lens.agsl`, `RuntimeShader` o `drawBitmapMesh` se ejecutaba en `CosmicWallpaperRenderer`, junto a `CosmicWallpaperService` y su vista específica de previsualización. **Ese comportamiento no era parte del AppWidget original.** `lensStrength` no se consume en `BlackHoleArt.nativeFlow` ni en su material de disco.

Esta entrega no incorpora el wallpaper, no captura la pantalla de inicio y no deforma píxeles de aplicaciones ajenas. AOSP sí contiene `ShaderData` y un backend `RuntimeShader`, pero `RemoteComposePlayer` de Android 16 rechaza shaders por defecto mediante su `ShaderControl`; ese controlador no se habilita desde la API pública de `RemoteViews` usada aquí. Por eso no basta con que un shader funcione dentro de una Activity ni con que exista su opcode. La política exacta del firmware Android 17 de Motorola permanece pendiente. Una futura prueba podría deformar sólo un fondo propio del documento y estrellas propias, manteniendo la restricción de widget auténtico. Sería una mejora nueva y quedaría claramente identificada.

## Adaptación a RemoteCompose

Se conserva el serializador heredado de protocolo de plataforma V6 y se cambia su descripción accesible a RabbitGlance. El acceso público al framework es `RemoteViews(DrawInstructions.Builder(listOf(bytes)).build())`; no se llaman clases ocultas ni se usa reflexión en producción.

La app no incorpora un player de AndroidX más nuevo que el del teléfono. La biblioteca AndroidX evoluciona por separado y su versión más reciente no demuestra que Moto App Launcher entienda todas sus operaciones. El subconjunto elegido conserva compatibilidad con la ruta anterior y se prueba operación por operación.

Las curvas y los materiales del disco se conservan como **dos texturas procedurales estáticas de 512×512**, calculadas una vez al generar el documento; las otras dos texturas representan ambiente y esfera central. Este era el planteamiento original del widget: la turbulencia fina del material no cambiaba por fotograma. Las estelas vectoriales sí se animan en el host. No se reduce la escena a una única imagen ni se promete una turbulencia de shader por píxel que el widget anterior no ejecutaba.

El alfa Doppler nativo conserva las fórmulas de la versión anterior, incluida su aproximación de la constante relativista. La previsualización usa esa misma variante nativa, evitando discrepancias con la versión Canvas. Los halos de estrellas mantienen la aproximación nativa por círculos y no el gradiente radial de su renderer Canvas.

La vista previa crea el mismo documento mediante `RemoteViews.apply`. Pausar captura una sola imagen de ese reproductor, lo retira de la jerarquía y muestra la captura; reproducir/reiniciar vuelve a adjuntar un documento. Este bitmap existe únicamente para la pausa manual de la vista previa, no para animar el escritorio.

La paleta inicial es la variante térmica fija original. No se ha recuperado una copia de las preferencias individuales del antiguo widget ni los colores exactos extraídos de su wallpaper. No se afirma que esa elección reproduzca una configuración personal desconocida.

## Rendimiento y responsabilidad de cada proceso

**Host, sin intervención por fotograma de RabbitGlance:** ángulos de estelas, máscara anterior/posterior, Doppler/alfa, rotación y escala del plano, titileo opcional, puntos/halos, manos y hora civil. Se usan expresiones float y referencias temporales de plataforma. `ANIMATION_SECONDS` impulsa la fase; la referencia a segundos continuos mantiene la dependencia de repintado del player V6. La velocidad de la figura no altera la hora civil.

**Aplicación, trabajo finito:** cálculo inicial del material y compresión PNG, construcción y envío del documento al añadir, editar, redimensionar o recibir eventos de hora/zona/idioma/calendario. Actualización periódica diaria de respaldo. El calendario puede actualizarse tarde por Doze; no se agregan alarmas exactas para evitarlo.

No hay `Service`, foreground service, bucle de fotogramas, temporizador de bitmaps, overlay, live wallpaper, permiso de red, accesibilidad ni wakelock. Se usa un ejecutor de trabajo bajo demanda para preparar materiales fuera del hilo de UI y un debounce de 180 ms para los controles. Un hilo Java de un ejecutor no es un servicio que mantenga vivo el proceso: Android puede terminar la app normalmente.

Se evitan dependencias de segundos continuos de las luces al desactivar ese movimiento. Las manos analógicas conservan su cálculo temporal original. El contenido solicita 30 FPS en la cabecera; no es una medición ni una garantía de 30 FPS en Motorola. Las cuatro imágenes decodificadas suman aproximadamente 4 MiB por documento, más el coste del player, caché de materiales y buffers temporales; no es una estimación del RSS total.

El encoder impone menos de 1000 identificadores por documento. El cache de disco está acotado a 8 MiB. La visibilidad/bloqueo del widget depende del host. El player AOSP retira callbacks al separar la vista de la ventana; falta comprobar si Moto App Launcher conserva vistas adjuntas en páginas ocultas. Una escala global de animación cero se aplica al reconstruir el documento; cambiarla con la app cerrada no reescribe instantáneamente todos los widgets.

## Diagnóstico incluido

Muestra fabricante/modelo, versión y API Android, build, launcher resuelto, versión de DrawInstructions, escala de animación, tamaño del documento y pruebas locales por píxeles: círculos, líneas/alfa, expresiones float, rotación/escala con save/restore, PNG, color interpolado y texto/números/medida.

Registra errores síncronos de publicación, la aceptación por AppWidgetManager y una observación manual opcional por instancia. Aceptación no equivale a reproducción: un error posterior del launcher puede no devolverse al proveedor. **No etiqueta automáticamente como admitidas las operaciones del Motorola basándose sólo en la presencia de una clase.**

## Validación de esta compilación

Compilación debug completada con Gradle 8.13, AGP 8.7.3, Kotlin 2.0.21 y JDK 21. Las fuentes generan bytecode 17. Lint terminó sin errores; quedaron 13 advertencias de localización, backup, anotación redundante, overdraw del cartel de error y versión de dependencia de pruebas. Firma APK verificada mediante `apksigner`; aplicación instalable independiente, mínimo/target API 35, sin permisos declarados ni servicios.

Cuatro pruebas de Robolectric con **framework API 36 y gráficos nativos**, todas aprobadas: documento serializado en Parcel y restituido que cambia sus estelas sin republicación; movimiento reducido que conserva la escena fija; escena completa y estrellas en cuatro tamaños, incluido mes de 31 días; apertura de configuración y preferencias independientes por instancia. La variante analógica se incluye en la comprobación de tamaños.

La prueba de movimiento desactiva reloj y animación de puntos, para que una variación de hora no pueda hacerse pasar por movimiento de acreción. Se inspeccionaron las capturas antes/después y la escena con reloj. También pasaron las pruebas locales de las siete familias de operaciones utilizadas. Esta evidencia **no mide FPS, batería ni ejecución del launcher Motorola**.

Capturas de la simulación (estrellas en densidad rica para la prueba de carga): [sin reloj antes](evidence/black-hole-before.png), [sin reloj después](evidence/black-hole-after.png), [con reloj y estrellas](evidence/black-hole-clock.png).

## Prueba física propuesta

1. Instalar y comprobar que no reemplaza el reloj anterior. Abrir la app y verificar la vista previa, pausa/reproducción, velocidad `.2× / 1× / 2×` y los tres tamaños.
2. Añadir el widget desde el escritorio o el diálogo de anclaje. Salir de RabbitGlance y observar 30 segundos: las estelas deben avanzar por el disco, con ocultación y atenuación coherentes; el reloj debe seguir la hora.
3. Comparar vista previa y escritorio con la misma configuración. Si difieren, copiar diagnóstico y aportar captura o grabación breve del escritorio.
4. Probar redimensionamiento ancho/alto, segundos, esfera analógica, estrellas y dos widgets con ajustes diferentes.
5. Cambiar de página, apagar/bloquear la pantalla y volver. Observar continuidad, velocidad y si el host deja de solicitar cuadros al ocultarse. No confundir fase reiniciada al reconstruir el documento con un servicio activo.
6. Revisar consumo durante un período comparable con/sin el widget y registrar modo de batería, brillo, tiempo de pantalla y frecuencia seleccionada. CPU/GPU/FPS no se dan por medidos en esta entrega.

## Próximas mejoras, distintas de la recuperación

Validar primero en Motorola. Después: medir presupuesto de cuadros y energía, decidir perfiles de FPS según los resultados y ampliar pruebas de operaciones. Considerar lente sobre un fondo propio del widget y evolución temporal de los filamentos sólo si el reproductor real lo admite y el coste medido es razonable. No migrar todavía Estudiantes, Ubuntu, Estrella, Nebulosa, River o Boca.

## Fuentes técnicas primarias

- [RemoteViews y restricciones de DrawInstructions](https://developer.android.com/reference/android/widget/RemoteViews).
- [DrawInstructions](https://developer.android.com/reference/android/widget/RemoteViews.DrawInstructions) y [Builder](https://developer.android.com/reference/android/widget/RemoteViews.DrawInstructions.Builder).
- [Remote Compose AndroidX, estado de versiones](https://developer.android.com/jetpack/androidx/releases/compose-remote).
- [Header AOSP Android 16](https://android.googlesource.com/platform/frameworks/base/+/android16-release/core/java/com/android/internal/widget/remotecompose/core/operations/Header.java).
- [RemoteComposeState: listeners temporales](https://android.googlesource.com/platform/frameworks/base/+/android16-release/core/java/com/android/internal/widget/remotecompose/core/RemoteComposeState.java).
- [RemoteComposeCanvas: Choreographer, FPS y detach](https://android.googlesource.com/platform/frameworks/base/+/android16-release/core/java/com/android/internal/widget/remotecompose/player/platform/RemoteComposeCanvas.java).

- [RemoteComposePlayer: política de shaders por defecto](https://android.googlesource.com/platform/frameworks/base/+/android16-release/core/java/com/android/internal/widget/remotecompose/player/RemoteComposePlayer.java).

## Integridad del APK entregado

Archivo: `RabbitGlance-0.1.0-Black-Hole.apk`. Tamaño: 2664918 bytes. SHA-256: `db7b02acb1b03224bfb65bd7bbc7b30f4b9b42a744ec552e5fb66af5a0cf5e0f`. Certificado de prueba SHA-256: `8ad891c5f7e8c2dd199bc5680d854ff3578b90ae456c8f223fce4cbfe2b0a137`.
