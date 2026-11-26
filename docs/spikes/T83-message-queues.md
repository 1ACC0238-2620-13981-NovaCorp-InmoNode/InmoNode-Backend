# T83 · Cola de mensajes para los PDF

- **Historia:** US-37, spike de la arquitectura de colas de mensajes (RabbitMQ).
- **Responsables:** Andy Nuñez; colabora Breithner Perez.
- **Fecha:** implementado en T84 el 2026-11-21. Este documento es del 2026-11-25.
- **Estado:** en producción. Render se conecta a CloudAMQP en cada arranque.

## Contexto

US-37 pide investigar una cola de mensajes para que generar contratos en PDF no bloquee el servidor bajo carga:

- **Escenario 1:** un esquema que muestre que, con 50 clientes pidiendo su contrato a la vez, las solicitudes se encolan, se procesan en hilos independientes y la respuesta es un 202 Accepted inmediato.
- **Escenario 2:** una solicitud que falla va a una *dead letter queue*, para que no bloquee el proceso y se pueda auditar.

T83 pide prototipar con CloudAMQP un productor y un consumidor de Spring AMQP, y documentar la configuración y los límites del plan gratuito. El prototipo pasó directamente a producción en T84 (PR #29, US-45).

## Plan gratuito de CloudAMQP (Little Lemur)

| Límite | Valor | Qué significa para InmoNode |
|---|---|---|
| Precio | Gratis | — |
| Servidor | RabbitMQ compartido, "For Development" | Sirve para el proyecto. Una operación real tendría que pasar a un plan dedicado |
| Mensajes al mes | 1 000 000 | Cada PDF es un mensaje, así que sobra |
| Conexiones | 20 | El backend abre una sola (`CachingConnectionFactory`) |
| Colas | 100 | Se usan 2: la principal y la *dead letter queue* |
| Mensajes en cola | 10 000 | Para llegar al límite tendrían que acumularse 10 000 PDF sin generar |
| Cola inactiva | 28 días | Ambas colas son durables y el backend las declara al arrancar |

## Configuración

- **Variable:** `CLOUDAMQP_URL` (`amqps://usuario:clave@host/vhost`). La leen `financial.documents.broker-url` y `spring.rabbitmq.addresses`.
  - En Render está cargada como variable de entorno y no aparece en el repositorio (US-44).
  - Sin la variable no se crea ningún bean de RabbitMQ ni se abre conexión, y la cola es un hilo dentro del backend.
- **En local:** `docker compose up inmonode-broker` levanta `rabbitmq:4.1-management-alpine`, con su consola en `http://localhost:15672`.
- **Topología:**

| Elemento | Nombre | Detalle |
|---|---|---|
| Exchange | `inmonode.documents` | `direct`, clave `pdf.requested` |
| Cola | `inmonode.documents.pdf` | Durable, con *dead letter exchange* `inmonode.documents.dlx` y clave `pdf.failed` |
| Exchange de fallos | `inmonode.documents.dlx` | `direct` |
| *Dead letter queue* | `inmonode.documents.pdf.dlq` | Durable |
| Mensaje | `DocumentGenerationMessage` | JSON con el id de la solicitud y lo necesario para generarla |

- **Consumidor:** uno solo, con prefetch de 1 y sin reencolar. La instancia gratuita de Render tiene 512 MB, y PDFBox con varios documentos a la vez la pondría en riesgo.

## Escenario 1: 50 solicitudes a la vez

```mermaid
sequenceDiagram
    autonumber
    actor C as 50 compradores
    participant API as Backend (hilos HTTP)
    participant DB as PostgreSQL
    participant MQ as CloudAMQP
    participant W as Consumidor (hilo del listener)
    participant S3 as Almacenamiento S3

    C->>API: POST /reservations/{id}/contract/document-requests (x50)
    API->>DB: Guardar la solicitud PROCESSING
    API->>MQ: Publicar en inmonode.documents (pdf.requested)
    API-->>C: 202 Accepted {id, status: PROCESSING, /api/v1/document-requests/{id}}
    loop un mensaje a la vez
        MQ->>W: Entregar el siguiente mensaje
        W->>W: Generar el PDF con PDFBox
        W->>S3: Guardar el PDF
        W->>DB: Solicitud READY
        W-->>MQ: ack
    end
    C->>API: GET /api/v1/document-requests/{id}
    API-->>C: READY con downloadUrl (válido 10 minutos)
```

- **El hilo HTTP no genera el PDF.** Solo guarda la solicitud y publica un mensaje, y responde 202 enseguida, haya 1 o 50 solicitudes.
- **Un hilo aparte genera los PDF:** el del listener de Spring AMQP, que toma los mensajes en orden.
- **Endpoints con 202:**
  - `POST /api/v1/reservations/{transactionId}/contract/document-requests`;
  - `POST /api/v1/quotations/{quotationId}/document-requests`.

  El estado se consulta con `GET /api/v1/document-requests/{id}`: `PROCESSING`, `READY` o `FAILED`.
- **Si el broker no acepta el mensaje,** lo toma un trabajador dentro del backend: un hilo con capacidad para 100 solicitudes. Así una caída de CloudAMQP no pierde solicitudes. Con la capacidad llena, la solicitud falla enseguida en lugar de esperar sin fin.

No se midió una carga real de 50 solicitudes. El esquema describe el comportamiento del código.

## Escenario 2: *dead letter queue*

```mermaid
flowchart LR
    Q[inmonode.documents.pdf] -->|mensaje| W[Consumidor]
    W -->|PDF listo: ack| OK[(Solicitud READY)]
    W -->|falla o no se puede leer: rechazo sin reencolar| DLX{{inmonode.documents.dlx}}
    DLX -->|pdf.failed| DLQ[inmonode.documents.pdf.dlq]
    W -->|falla| F[(Solicitud FAILED + aviso a finanzas)]
```

- **Rechazo:** si el PDF falla (`GENERATION_FAILED`), tarda demasiado (`GENERATION_TIMEOUT`) o el mensaje no se puede leer, el consumidor lanza `AmqpRejectAndDontRequeueException`. RabbitMQ lo mueve a la *dead letter queue*, y la cola principal sigue con el siguiente.
- **Sin reintentos automáticos:** a diferencia del escenario, que habla de "varias veces", un PDF que falla no se reintenta solo.
  - La solicitud queda `FAILED` con su código y finanzas recibe un aviso.
  - El cliente puede pedir otra solicitud.
  - El mensaje queda en la *dead letter queue* para revisarlo a mano en la consola de CloudAMQP.

## Evidencia

- **Pruebas:**
  - `RabbitDocumentQueueIntegrationTest`, con RabbitMQ en Testcontainers:
    - una solicitud viaja por el broker y su PDF se guarda;
    - un mensaje que el consumidor no puede leer termina en la *dead letter queue*;
  - `RabbitDocumentQueueTest`:
    - la solicitud se publica como JSON en el exchange;
    - si el broker no responde, la toma el trabajador interno;
    - un PDF que falla se rechaza sin reencolar, para que vaya a la *dead letter queue*.
- **Producción:** los logs de Render muestran `Created new connection: rabbitConnectionFactory` en cada arranque del 2026-11-25.

## Fuentes

- CloudAMQP, planes y precios: https://www.cloudamqp.com/pricing (consultado el 2026-11-25)
