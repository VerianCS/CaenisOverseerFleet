# Operación de Caenis

## Instalación local en Windows

Requisitos: PowerShell 7, Docker Desktop con contenedores Linux. Para empaquetar el
plugin y el gestor también necesitas JDK 21. Node no es necesario para levantar
la consola mediante Docker. Si usas Python para aprovisionar NiFi, usa Conda.

Desde la raíz:

```powershell
pwsh ./scripts/Initialize-Caenis.ps1
pwsh ./scripts/Start-Caenis.ps1
```

La inicialización crea `.env` y restringe su acceso al usuario actual. No imprime
contraseñas ni modifica un archivo existente. La cuenta inicial es `admin`;
su contraseña está en `CAENIS_ADMIN_PASSWORD`. Abre
[Caenis local](http://localhost:3000), inicia sesión y registra una instancia.

Estos comandos son de instalación/arranque y no se han ejecutado durante esta
entrega. Docker ensambla sus imágenes cuando el operador solicita el arranque.
No hay validaciones, pruebas ni CI programadas por esta entrega.

## Conectar un servidor Paper

1. Empaqueta los tres artefactos JVM:
   `pwsh ./scripts/Package-Caenis.ps1`.
2. Coloca `dist/caenis-overseer.jar` en `plugins/` del servidor Paper 1.21.11.
3. En Fleet, registra un ID como `srv-survival-01`, guarda la clave y descarga
   `config.yml`. Colócalo en `plugins/CaenisOverseer/config.yml`.
4. Si Paper está en otra máquina, cambia `backend.url` a la dirección HTTPS
   alcanzable del core. `localhost` siempre identifica la máquina del plugin.
5. Reinicia Paper. El agente se inscribe con su clave y renueva su token de
   quince minutos. Las métricas se envían cada cinco segundos.
6. Activa RCON en `server.properties`:
   `enable-rcon=true`, `rcon.port=25575` y una `rcon.password` propia.
   Reinicia Paper y guarda esos datos en los ajustes de la instancia.
   RCON debe permanecer en una red privada/VPN; el protocolo no cifra el tráfico.

La clave del agente también puede venir de `CAENIS_AGENT_SECRET`; el ID, de
`CAENIS_INSTANCE_ID`; la URL, de `CAENIS_BACKEND_URL`. No hay una clave global
que permita a una instancia hacerse pasar por otra.

## Arranque y parada de Paper

RCON puede operar un proceso vivo, pero no arrancar uno detenido. Para el ciclo
de vida se incluye `caenis-manager.jar`, un gestor que ejecuta únicamente
directorios y JAR predefinidos.

- Crea, por ejemplo, `servers/survival/paper.jar`.
- Lee y acepta tú mismo la EULA de Minecraft en `servers/survival/eula.txt`.
  Caenis no la acepta automáticamente.
- Copia `docker/manager/application-manager.example.yml` a
  `runtime/application-manager.yml` y ajusta las instancias.
- Ejecuta `pwsh ./scripts/Start-Manager.ps1`.
- Si el core está en Docker Desktop, el gestor debe escuchar en una interfaz
  privada accesible al contenedor: usa `-Bind <IP-privada>`, configura esa URL
  en la consola y permite 8090 exclusivamente desde el core en el firewall.
  `host.docker.internal` resuelve el host desde Docker Desktop.
- Guarda la clave `CAENIS_MANAGER_KEY` en la conexión de esa instancia.
- Usa Start / Stop / Restart en la página de la instancia.

Las órdenes son argumentos de ProcessBuilder, sin shell. El gestor no permite
cargar ejecutables ni directorios desde una petición web. La parada envía
`stop` por stdin y espera el guardado normal. Si tarda más de 35 segundos,
reporta que continúa; no mata el proceso ni presenta un reinicio como terminado.
Los registros del juego permanecen en el directorio `logs/` de Paper.

El gestor mantiene la propiedad del proceso en memoria y un marcador con PID y
fecha de inicio en el directorio de la instancia. Después de una caída del gestor,
rechaza un segundo arranque mientras aquel proceso siga vivo. Puede detenerse
desde RCON antes de recuperar el control normal. No adopta procesos arbitrarios.

## Roles

| Rol | Acceso |
| --- | --- |
| SuperAdmin | Flota, mapa, RCON sin plantillas, ciclo de vida, usuarios, conexiones, agentes y entrenamiento de baselines. |
| Moderator | Salud y jugadores; mapa; mitigaciones predefinidas; historial de comandos. |
| Analyst / Observer | Mapa, alertas y contadores. UUID/nombre de jugador sustituidos por un alias estable. Sin roster, RCON ni ajustes. |

La API consulta la sesión y el rol vigente para cada petición. La protección de
rutas de Next.js es una primera barrera adicional. Las sesiones usan cookies
HttpOnly, SameSite=Strict, duración de ocho horas y revocación persistida.
Los cambios de cuenta revocan sesiones previas. Las mutaciones web requieren CSRF.

## Detección y contención

El plugin mide desde BlockDamageEvent hasta BlockBreakEvent y descarta la
fiabilidad temporal si cambian herramienta, efectos o estado del jugador.
No interpreta el intervalo entre dos bloques diferentes como tiempo de minería.
Los bloques desconocidos no reciben una dureza inventada.

La oclusión considera las seis caras sin cargar chunks vecinos. La topología
incompleta no alimenta ese detector. La ventana contiene hasta 25 minerales
valiosos de quince minutos, con un mínimo de diez muestras y umbral inicial 75%.

La referencia nativa de velocidad que expone Paper limita conservadoramente el
umbral físico cuando intervienen atributos del juego. El plugin captura esa
referencia; la comparación y la decisión permanecen en el core.

Las baselines son modelos estadísticos Beta-Binomial por instancia/jugador/mundo.
Se entrenan desde la consola sobre un período explícitamente revisado como
legítimo, con al menos cien muestras. Persisten después de reiniciar. No se
incluye un clasificador supervisado preentrenado ni se afirma una precisión
medida: no se suministraron datos etiquetados.

La contención automática está desactivada inicialmente. Al activarla exige tres
avisos críticos de minería rápida en un minuto; limita cada jugador a una acción
cada cinco minutos y cada instancia a cinco jugadores por minuto. La oclusión
nunca activa contención por sí sola. Los eventos con más de quince segundos de
antigüedad tampoco activan contención al recuperarse de una interrupción. Freeze dura como máximo cinco minutos y
desaparece al reiniciar el plugin.

## Entrega, pérdidas y retención

- Ring buffer SPSC: capacidad predeterminada 8.192. Si se llena, se descarta el
  evento nuevo y aumenta el contador de descartes.
- El único trabajador del agente serializa, escribe al spool y realiza HTTP.
  Ninguna llamada de red o escritura del spool ocurre en el tick.
- Spool local: 128 MiB por defecto, configurable hasta 1 GiB. Borra primero los
  lotes más antiguos al alcanzar el límite. Edad máxima de envío: 24 horas.
- Reintentos exponenciales entre 1 y 30 segundos. El nonce y los IDs de evento
  se conservan. Respuestas permanentes inválidas van a una cuarentena acotada.
- En una terminación abrupta puede perderse la cola aún no escrita. No se
  promete entrega exactamente una vez; la persistencia deduplica reintentos.
- Métricas históricas: siete días. Eventos crudos: treinta días. Recibos:
  siete días. Alertas y auditoría no se eliminan por mantenimiento automático.
- La retención y el detector de latidos son tareas funcionales de la plataforma,
  no tareas de validación.

NiFi es opcional. Véase [su procedimiento](../docker/nifi/README.md). Las firmas
se verifican antes de la admisión a la cola principal. La firma original vuelve a
comprobarse al persistir. Los bytes del JSON se conservan sin QueryRecord ni
transformaciones que invaliden la firma.

## RCON y auditoría

Las conexiones son persistentes, con una orden en vuelo por nodo y una cola
global acotada. La respuesta se delimita con una segunda solicitud RCON y se
limita a 256 KiB; no se corta silenciosamente en el primer paquete.

Estados registrados: QUEUED, DISPATCHING, SUCCEEDED, REJECTED o UNCERTAIN.
Un timeout no demuestra que el comando no se ejecutara. Consulta el servidor
antes de repetir. La terminal recibe fragmentos por STOMP y recupera el resultado
final desde el historial si se pierde la conexión web.

El usuario de aplicación carece de UPDATE/DELETE sobre la auditoría; un trigger
también rechaza UPDATE/DELETE/TRUNCATE. Un administrador de PostgreSQL conserva
autoridad sobre su base: esta protección no es una certificación externa WORM.

## HTTPS

Para publicar en un dominio propio:

1. Configura DNS hacia tu host y permite 80/443.
2. En `.env`, define:
   `CAENIS_PUBLIC_ORIGIN=https://tu-dominio`,
   `CAENIS_HOST=tu-dominio`, `CAENIS_SECURE_COOKIES=true`,
   `CAENIS_BIND=0.0.0.0`, `CAENIS_HTTP_PORT=80` y
   `ACME_EMAIL=tu-correo`.
3. Ejecuta
   `docker compose -f docker-compose.yml -f docker-compose.https.yml up --build -d`.
4. Para usar el conduit en esa misma dirección, añade `--profile nifi` antes
   de `up` y configura `https://tu-dominio/telemetry` como URL de telemetría.

Traefik conserva certificados en un volumen y dirige /ws al core.
El despliegue publicado no se ha realizado aquí: no se proporcionó dominio,
host de destino ni instancias reales.

## Vault

RCON puede usar una contraseña cifrada AES-256-GCM o una referencia a Vault KV v2.
Para Vault, configura `VAULT_ADDR=https://vault...` y `VAULT_TOKEN_FILE` en el
core, monta el archivo del token como solo lectura y guarda en la consola un
path como `secret/data/caenis/survival`. El secreto debe contener
`data.data.password`. Cada comando obtiene el valor actual y abre una nueva
conexión, por lo que la rotación aplicada en Vault/Paper se usa de inmediato.
Vault no convierte el RCON estándar en credenciales efímeras: su rotación debe
coordinarse con la configuración y reinicio de Paper.

## Métricas OpenTelemetry

El core incluye el registro Micrometer OTLP. Para conectarlo a tu collector,
añade a su entorno `OTEL_METRICS_ENABLED=true` y
`OTEL_METRICS_URL=http://<collector-privado>:4318/v1/metrics`.
El exportador está desactivado hasta disponer de un destino real. El endpoint
de métricas de Actuator requiere una sesión SuperAdmin y no se publica por separado.

## Copias y recuperación

`pwsh ./scripts/Backup-Caenis.ps1` crea un dump binario en
`runtime/backups/`. Protege aparte `.env`, sobre todo
`CAENIS_ENCRYPTION_KEY`, los volúmenes de NiFi y los mundos Paper.

Para restaurar una copia en una base nueva, detenida para escrituras:

```powershell
docker compose stop core web
docker compose cp ./runtime/backups/TU-COPIA.dump postgres:/tmp/restore.dump
docker compose exec -T postgres pg_restore -U caenis_owner -d caenis_overseer --no-owner /tmp/restore.dump
docker compose up -d core web
```

Usa una base vacía; no se incluyen flags destructivos de limpieza. La copia de
PostgreSQL no contiene los mundos, el spool local ni los certificados.

## Límites de esta entrega

El código y los procedimientos están entregados, pero no se han ejecutado
compilaciones, pruebas, validaciones visuales, escaneos ni despliegues.
No existen resultados de rendimiento o conformidad de producción.
El diseño utiliza un único core activo y admite hasta 256 instancias registradas.
La disponibilidad de una red real, las credenciales y la aceptación de EULA
pertenecen a la instalación del operador.
