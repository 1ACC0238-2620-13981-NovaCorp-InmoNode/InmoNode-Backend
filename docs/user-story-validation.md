# Validación de historias de usuario del backend

Referencia: `C:\Users\becker\WebstormProjects\InmoNode-Project-Report\docs\Chapter-02.md`.
Rama de trabajo: `feature/health-check-and-api-docs`. Cambios locales, **sin commit ni push**.
Esta matriz distingue implementación, validación local y dependencias todavía pendientes; no declara completo todo el capítulo.

## Funcionalidades incorporadas y correcciones

| US | Implementación en esta rama | Estado y límites |
| --- | --- | --- |
| US-16 | Filtros de lotes por área, precio, estado y viewport; límites inclusivos, rangos y coordenadas validados; intersección JTS con el polígono. | Implementado. Área/precio/estado se filtran en PostgreSQL; viewport se comprueba con JTS después de la consulta. |
| US-17 | TEA efectiva mensual `(1+TEA/100)^(1/12)-1`, cálculo decimal, redondeo HALF_EVEN, ajuste de última cuota y tasas con cuatro decimales. | Corregido y probado en simulación y cronograma financiero. Las migraciones no recalculan cuotas históricas. |
| US-18 | `GET /api/v1/quotations/{quotationId}/download`: PDF con lote, precio, inicial, TEA, plazo, vigencia y tabla completa de amortización. | Implementado y probado. Solo el propietario BUYER; attachment y no-store; AES-256 y permisos de edición restringidos. Es generación síncrona; los permisos PDF no equivalen a una firma legal. |
| US-19 | Reserva web congela quotationId y precio aceptado; cambiar el catálogo no cambia el acuerdo. Aviso duradero de nueva separación al área financiera. | Corregida la congelación de condiciones. Pendiente perfil de comprador y Buyer canónico del capítulo. |
| US-20 | Al recibir evidencia web puntual se guarda un aviso administrativo en outbox, en la misma transacción que la evidencia y el cambio del lote. | Incorporada la notificación que faltaba. Duplicados no crean avisos nuevos; SMTP se ejecuta después y puede reintentarse. Carga real S3 pendiente de la suite con Docker. |
| US-22 | Conformidad preliminar con timestamp y aviso persistente al área legal; repetir la aceptación conserva la fecha y no duplica el aviso. | Backend probado. Conformidad y firma final son estados diferentes. El scroll/checkbox de lectura pertenece al frontend. |
| US-23 | Estado de cuenta desde emisión del contrato; total y progreso sobre cuotas, excluyendo la inicial y mora. WEB recupera las cuotas guardadas por quotationId y recalcula sus vencimientos desde emisión. | Corregido y probado. Se retiró SOLD del último pago: la firma verificada de US-56 debe producir esa transición. Pendientes pagos parciales, excedentes y contratos FIELD. |
| US-24 | Comandos y jobs independientes para próximos vencimientos y mora. Avisos por cuota con claves independientes y envío mediante outbox. | Implementado en backend; conserva reglas de mora por proyecto. Marcas de aviso indican encolado; delivered_at registra entrega SMTP. Entrega real pendiente de SMTP. |
| US-25 / US-54 | Rechazo deja reserva REJECTED, lote PENDING_VERIFICATION, ventana de sustitución de 24 h y gracia de entrega de 15 min. Sustituto puntual conserva historial y elimina el vencimiento del lote. | Corregido y probado con persistencia real. waitingUntil muestra el plazo sin gracia. Pendiente outbox de comprobantes y restitución de evidencias recibidas a tiempo pero entregadas después de una expiración. |
| US-28 | `POST /api/v1/reservations/{transactionId}/co-owner`: nombre/documento validados, reserva propia WEB, persistencia y copia inmutable al contrato. | Probado con PostgreSQL. Emisión y modificación usan el mismo bloqueo. Después de emitir responde 409 y exige adenda. El PDF contractual aún lo carga administración y debe incluir estos datos. |
| US-32 | Prevalidación de documentos/contacto, propiedad de prospectos, prospectos referenciados y lotes existentes antes de guardar. Errores 400 con índice; 201 para la sincronización completa y resultados por reserva. | Un conflicto de disponibilidad es un resultado por ítem y permite conservar los aceptados. Un fallo técnico revierte prospectos, reservas y operaciones de voucher juntos; probado provocando un fallo del listener y reintentando. Pendientes versiones del protocolo de sincronización. |
| US-34 | `GET /health`, 200 con base disponible y 503 si PostgreSQL falla. | Implementado. Se comprobó con PostgreSQL temporal activo y anteriormente con la base ausente. |
| US-35 | Respaldo diario 02:00 UTC: pg_dump, GZIP, carga a bóveda S3 externa, verificación SHA-256/tamaño y retención de siete días después de verificar la copia nueva. | Implementado, deshabilitado por defecto. Se ejecutó el dump del código y se restauró en otra base temporal. Falta probar carga/retención reales en la bóveda externa. |
| US-36 | Límites por IP: catálogo 150/min, descargas PDF 10/min y otras API 60/min; 429 y Retry-After. | Implementado y probado. No confía en X-Forwarded-For del cliente; /health permanece accesible. Configurar la IP del proxy correctamente al desplegar. |
| US-40 | Auditoría JSONL asíncrona de escrituras financieras: UUID de evento, método, ruta, IP, userId, fecha, estado y cuerpos ofuscados; cola acotada sin descartar eventos y rotación. | Incluye las rutas reales de separación y aprobación/rechazo. Cuerpos inválidos/grandes se omiten. La inmutabilidad operacional exige volumen y archivo externo con controles de escritura; el disco local no es WORM. |
| US-41 | Caffeine en consultas públicas de catálogo, TTL cinco minutos, capacidad acotada e invalidación AFTER_COMMIT. Nueva generación evita que una carga antigua repueble datos vigentes. | Probado: rollback conserva caché; commit invalida. Campo y decisiones financieras consultan PostgreSQL; FOR UPDATE recarga el agregado después de esperar el bloqueo. |
| US-42 | WebSocket `/api/v1/projects/{projectId}/events`: proyectos publicados, eventos LOT_UPDATED después del commit, sin datos del comprador; ping/pong y cierre de conexiones inactivas. | Probado en vivo: handshake 101, evento BLOCKED y cierre 1001 tras 32,78 s sin responder heartbeat. Umbral 30 s, comprobación cada 5 s. Hasta 1000 conexiones por instancia; no hay difusión entre instancias. |
| US-46 | `GET /api/v1/vouchers?page=1&limit=20`: historial propio paginado en PostgreSQL, Total-Count e índices de consulta. | BUYER/FIELD_AGENT; máximo 100 por página; orden descendente por fecha e id. Pruebas de slice pasan; integración completa de 300 recibos pendiente de Docker. |
| US-49 | Swagger/OpenAPI habilitados en desarrollo y deshabilitados en producción. | Swagger probado con base real en localhost:8081. |
| US-50 | CI para PR a main/develop, push en ambas y ejecución manual: Java 21, Maven verify, Testcontainers y reportes. | YAML revisado con actionlint. Ejecución GitHub y protección de ramas pendientes; no se hizo push. |
| US-51 | Proyecto DRAFT con nombre, ubicación, reglas financieras y etapas obligatorias, normalizadas y únicas. | Implementado, probado en Swagger y PostgreSQL. Se conservan los endpoints previos de proyectos por compatibilidad. |
| US-52 | Alta individual de lote DRAFT con proyecto/etapa, código único, área, dimensiones opcionales, precio y polígono válido. Consulta administrativa de todos los estados. | Implementado. El borrador no aparece en catálogo público ni en cartera de campo y no puede reservarse. |
| US-53 | Publicación explícita del lote en proyecto publicado; pasa de DRAFT a AVAILABLE. Repetir no reinicia lotes bloqueados, reservados ni vendidos. | Implementado y probado. El bulk import previo se conserva; usa la primera etapa del proyecto y su flujo original de publicación. |

## APIs nuevas

| Método | Ruta | Permiso / respuesta principal |
| --- | --- | --- |
| POST | /api/v1/catalog/projects | CATALOG_ADMIN; 201 proyecto DRAFT con etapas. |
| POST | /api/v1/catalog/projects/{projectId}/lots | CATALOG_ADMIN; 201 lote DRAFT; 400 datos/polígono/etapa inválidos; 409 código repetido. |
| GET | /api/v1/catalog/projects/{projectId}/lots | CATALOG_ADMIN; incluye borradores. |
| PUT | /api/v1/catalog/projects/{projectId}/publish | CATALOG_ADMIN; 422 sin lotes. |
| PUT | /api/v1/catalog/lots/{lotId}/publish | CATALOG_ADMIN; 422 si el proyecto no está publicado. |
| GET | /api/v1/quotations/{quotationId}/download | BUYER propietario; 200 application/pdf; 404 si es ajena. |
| POST | /api/v1/reservations/{transactionId}/co-owner | BUYER propietario; 200; 409 después de emisión. |
| WebSocket | /api/v1/projects/{projectId}/events | Proyecto publicado y origen CORS permitido. Mensajes del cliente: ping/pong. |

Cambio de contrato HTTP: `POST /api/v1/field-sync` responde **201** en un lote válido, aunque haya conflictos de disponibilidad por ítem. El cliente debe aceptar ese estado.

## Persistencia, configuración y seguridad

Migraciones añadidas:

- V3_3: tasas de cotización NUMERIC(7,4).
- V4_9: copropietario en reserva/contrato, quotationId y precisión de tasas financieras.
- V4_10: fecha límite de sustitución e índice.
- V4_11: etapas, etapa obligatoria del lote y estado DRAFT; datos previos se asignan a Etapa 1.
- V4_12: notification_outbox con clave única, entrega, intentos y próxima ejecución.
- V5_3: índices de historial de vouchers.

Dependencias nuevas: Caffeine, Spring WebSocket y Apache PDFBox 3.0.8. Se mantienen servicios de dominio, comandos/queries, ACL, repositorios, assemblers REST/JPA y Flyway del proyecto.

El catálogo de proyectos/lotes continúa dentro de `financial`, siguiendo la estructura actual. Las rutas nuevas no crean por sí solas el bounded context RealEstateCatalog separado. La separación arquitectónica respecto a CommercialField sigue pendiente.

Configuraciones documentadas en .env.example:

- EVIDENCE_RESUBMISSION_WINDOW=PT24H y EVIDENCE_DELIVERY_GRACE=PT15M.
- AUDIT_ENABLED y AUDIT_DIRECTORY; logs/audit queda fuera de Git.
- STAFF_FINANCE_ADMIN_EMAIL, LEGAL_NOTIFICATION_EMAIL y NOTIFICATION_DISPATCH_JOB_ENABLED.
- NOTIFICATION_DISPATCH_INTERVAL=PT1M; hasta diez fallos SMTP, con backoff. Falta destinatario: conserva el aviso y espera sin consumir intentos.
- INSTALLMENT_REMINDER_CRON a las 08:00 e INSTALLMENT_OVERDUE_CRON a las 08:05, America/Lima. INSTALLMENT_REVIEW_CRON se mantiene como fallback del recordatorio.
- Timeouts SMTP de conexión, lectura y escritura de cinco segundos.
- BACKUP_ENABLED=false, BACKUP_PG_DUMP, BACKUP_VAULT_BUCKET/PREFIX/ENDPOINT/REGION y BACKUP_CRON. Bucket externo distinto al documental; HTTPS y credenciales mediante la cadena AWS/IAM.

SMTP tiene entrega al menos una vez: una caída después del envío y antes de guardar delivered_at puede duplicar el correo. Las claves únicas evitan duplicar avisos de negocio, pero no garantizan entrega exactamente una vez.

Reservas antiguas sin quotationId conservan su camino de compatibilidad. Recuperar condiciones históricas completas para esos casos requiere una migración de datos con sus acuerdos originales; no se inventó un cronograma aceptado.

## Validación realizada

Fecha: 9 de octubre de 2026.

- Suite validada: **260 pruebas, cero fallos, cero errores y ninguna omitida**: 257 sin infraestructura y tres con PostgreSQL real. La compilación de todas las pruebas también pasó.
- Validación local adicional con PostgreSQL 18.6: **3 pruebas de integración** pasan, con ObjectStorage simulado explícitamente.
- Esquema dev: 23 migraciones aplicadas, incluida la semilla de desarrollo; esquema de pruebas: 22 sin semilla. Hibernate valida los mappings.
- Flujo persistente: catálogo DRAFT/publicación, cotización TEA, descarga PDF, separación idempotente, copropietario, evidencia, aprobación, contrato, cuenta antes de conformidad, avisos únicos, pago de doce cuotas y saldo cero sin SOLD.
- Rechazo/sustitución: REJECTED, 24 h + gracia en lote, historial de evidencias y vuelta a PENDING_VERIFICATION.
- Sincronización: teléfono inválido y lote inexistente producen 400 sin guardar; fallo del listener revierte todo y el reintento produce 201 SYNCED.
- Swagger real: login CATALOG_ADMIN, proyecto con dos etapas 201 DRAFT, lote 201 DRAFT, publicación de proyecto 200 PUBLISHED y lote 200 AVAILABLE.
- HTTP y socket reales: cotización 201 con cuota 3188,23; PDF 200 application/pdf de 2178 bytes; co-owner 200; LOT_UPDATED BLOCKED y catálogo actualizado después del commit.
- Respaldo real del adaptador: GZIP de 8091 bytes y restauración SQL sin errores en una base aislada, conservando proyecto, dos etapas y lotes.
- Verificación previa: rangos/viewport inválidos 400; exceso de IP 429 con Retry-After; health 503 cuando falta PostgreSQL.

Docker continúa sin iniciar por virtualización/WSL2. La suite completa con PostgreSQL y S3 Testcontainers **no se ejecutó**. Los tres tests locales no sustituyen la prueba de uploads S3 reales ni la bóveda externa.

Comandos reproducibles:

```powershell
# Java 21 o posterior; suite sin Docker
.\mvnw.cmd '-Dtest=*Test,!*IntegrationTest,!InmoNodeBackendApplicationTests' test

# Base PostgreSQL de pruebas previamente creada y aislada; no apuntar a producción
$env:INMONODE_VALIDATION_DB_URL='jdbc:postgresql://127.0.0.1:55432/inmonode_jpa_validation'
$env:INMONODE_VALIDATION_DB_USER='inmonode_test'
.\mvnw.cmd '-Dtest=LocalPostgresFlowIntegrationTest' test

# Suite completa cuando Docker esté disponible
.\mvnw.cmd verify
```

El PostgreSQL temporal escucha solo en 127.0.0.1:55432. Los datos y binarios están en TEMP/inmonode-postgres-validation; no se instaló un servicio ni se modificó la base habitual. Swagger de validación se abrió en http://localhost:8081/api-docs. Las credenciales usadas fueron únicamente las semillas/sesiones de prueba.

En ese servidor de validación se desactivaron por argumento los jobs de liberación, revisión de cuotas y envío de correo para mantener los casos reproducibles. En la configuración normal permanecen habilitados. Los correos de prueba quedaron encolados; no se enviaron a destinatarios reales. La compilación final de todas las pruebas pasó y git diff --check no reportó problemas.

## Trabajo que todavía falta

| US / área | Falta concreta |
| --- | --- |
| US-02, US-11, US-12 | Descarga incremental con syncToken, versiones/tombstones y resolución explícita de versiones antiguas. Hoy hay cartera completa con ETag e idempotencia de reservas. |
| US-19, US-21, US-27 | Buyer canónico por documento, perfil y evidencia de identidad, AccountLinkRequest con aprobación y consolidación WEB/FIELD. Las reservas FIELD aún no llevan el acuerdo financiero completo necesario para emitir contrato y cuenta correctamente. |
| US-23, US-55 | PaymentIntent persistido antes de llamar al proveedor; PaymentRecord único por origen/referencia; pagos parciales, asignación mora/interés/capital, excedentes y creditBalance. El pago manual existente sigue exigiendo el importe exacto de una cuota y no tiene referencia bancaria idempotente. |
| US-25 / US-54 | Outbox de recepción de comprobantes y recuperación de entrega tardía puntual después de expiración. El outbox agregado en esta rama es de avisos por correo. |
| US-26 | Certificado de no adeudo con deuda residual exacta y firma representativa autorizada. Falta la identidad/activo/certificado del representante y el flujo documental correspondiente. |
| US-28 / US-45 | Compilación contractual automática que incorpore el copropietario en el PDF legal. Por ahora administración sube ese PDF. |
| US-37 / US-45 | Broker RabbitMQ y worker HTML→PDF→S3, solicitud 202, timeout 30 s, alerta crítica y DLQ. El PDF de cotización síncrono no cierra esta historia. |
| US-35 / US-40 | Validación de bóveda externa y retención real; almacenamiento de auditoría con controles de inmutabilidad operacional. |
| US-50 | Ejecución remota de CI y check obligatorio de protección de rama; requieren operaciones remotas que no se hicieron. |
| US-55 / US-56 | Proveedores elegidos en spikes US-48/US-30, acceso a sandbox y webhooks verificados. Firma: envelopeId, verificación del documento firmado, versión/hash en S3 y transición SOLD únicamente tras firma verificada. |
| Bounded contexts | Separar RealEstateCatalog de Financial y CommercialField conforme al mapa del capítulo. |
| Aplicaciones cliente / spikes | Offline local, OCR móvil, mapas, controles de lectura, recepción de eventos y evidencia de los spikes deben validarse en sus proyectos; no se declaran completos por este backend. |

Se solicitó la elección de pasarela y proveedor de firma para completar US-55/56; no se recibió esa definición durante esta implementación. No se añadieron confirmaciones de pago/firma ficticias ni endpoints que acepten callbacks sin verificar.
