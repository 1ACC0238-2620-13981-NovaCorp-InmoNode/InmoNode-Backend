# T81 · Proveedor de firma electrónica para los contratos

- **Historia:** US-30, spike de la evaluación de proveedores de firma electrónica.
- **Responsables:** Breithner Perez; colabora Angel Rocca.
- **Fecha:** prototipo el 2026-11-22. Este documento es del 2026-11-25.
- **Estado:** decisión aplicada en T82 (PR #39, US-56). En producción (Render) firma con DocuSeal Cloud en modo de prueba desde el 2026-11-26.

## Contexto

US-30 pide dos cosas:

- **Escenario 1:** una matriz que compare proveedores por costo, facilidad de integración por API y una recomendación de arquitectura.
- **Escenario 2:** el diseño del webhook que recibe el aviso de "firmado".

El Product Backlog citaba como ejemplos a Firma Perú, DocuSign y TuFirma.

## Marco legal en Perú

La Ley N.° 27269 y su reglamento (D. S. N.° 052-2008-PCM) distinguen dos casos:

- **Firma digital.** Es la que se genera dentro de la Infraestructura Oficial de Firma Electrónica (IOFE), con un certificado de una entidad acreditada por INDECOPI, por ejemplo el DNI electrónico. Tiene la misma validez y eficacia que la firma manuscrita.
- **Firma electrónica fuera de la IOFE.** Vale según lo que pacten las partes. Si se discute su autoría, quien la usa debe probar que es auténtica.

**Consecuencia para InmoNode:** con un proveedor que no pertenece a la IOFE, el contrato necesita una cláusula en la que comprador e inmobiliaria acepten la firma electrónica. Además, el sistema debe guardar evidencia de quién firmó, qué documento y cuándo. Por eso la hoja firmada cita el hash del contrato y conserva la constancia del proveedor.

## Matriz comparativa

| Proveedor | Tipo de firma | Costo | Integración por API | Aviso de firma | Observaciones |
|---|---|---|---|---|---|
| **DocuSeal, código abierto y autoalojado** | Electrónica, con sello PAdES en el PDF | Gratis y sin límites. Solo se paga dónde alojarlo | API básica gratuita: plantillas, envíos y consulta | Webhooks firmados con HMAC-SHA256 | **Elegido.** Sin cuenta ni datos personales. La API gratuita no crea plantillas desde un PDF |
| DocuSeal Cloud | Electrónica | Plan Basic gratis, con 10 correos de solicitud al mes. Pro: US$ 20 por usuario al mes, más US$ 0,20 por documento firmado por API | API y embebido desde Pro. El sandbox de desarrollo es gratis | Webhooks firmados como en el autoalojado | **En uso en Render**, en modo de prueba. `https://docuseal.com/api/…` responde igual que un DocuSeal autoalojado, así que el mismo adaptador sirve para los dos |
| DocuSign | Electrónica | La cuenta de desarrollo es gratis, pero sus sobres no tienen validez legal. Los planes de API cuestan desde US$ 50 al mes (Starter), US$ 300 (Intermediate) y US$ 480 (Advanced) | REST y SDKs maduros | Connect (webhooks) | Descartado el 2026-11-22: abrir la cuenta pide demasiados datos personales |
| Firma Perú (Plataforma Nacional de Firma Digital) | **Digital, dentro de la IOFE** | El firmador es para ciudadanos; el comprador necesita su propio certificado, como el del DNIe | La API REST de firma desatendida es solo para entidades públicas | No aplica: firma en el escritorio del usuario | No sirve para un flujo web de una empresa privada |
| TuFirma | — | — | — | — | No se evaluó: no se encontraron precios ni API públicos para comparar |

## Recomendación de arquitectura

1. **DocuSeal autoalojado, detrás del puerto `SignatureProvider`.** Si la inmobiliaria llegara a exigir firma digital de la IOFE, se cambia el adaptador sin tocar el dominio.
2. **Hoja de firma en lugar de subir el contrato completo.** La API gratuita no crea envíos desde un PDF, así que se sube una sola vez una plantilla de una página. La hoja cita:
   - el número del contrato;
   - el comprador;
   - el lote;
   - la fecha de emisión;
   - **el SHA-256 del PDF que emitió el backend.**

   El texto del contrato sale de un solo lugar, y la hoja firmada queda ligada a él por el hash.
3. **Envío desacoplado.** Al emitir el contrato se encola una solicitud. Un job (`ContractSignatureJob`, cada minuto) la envía con hasta 8 intentos y esperas de 1, 2, 4, 8, 16, 32 y 60 minutos. Si se agotan, la solicitud queda `FAILED` y se avisa a finanzas. Sin la configuración de DocuSeal, las solicitudes esperan sin gastar intentos.
4. **El comprador firma con un enlace.** El enlace llega en `GET /reservations/{id}/contract` (`signingUrl`) y por correo desde el backend. DocuSeal no envía correos.

### Lo que mostró el prototipo (2026-11-22)

Se usó la imagen `docuseal/docuseal:3.3.5` en Docker. El script de punta a punta pasó todas sus comprobaciones:

- **Plantilla:** `POST /api/templates/pdf` y `/api/submissions/pdf` son de Pro. La plantilla se sube por la interfaz web, sus campos se fijan con `PUT /api/templates/{id}` y los envíos se crean con `POST /api/submissions`, que admite valores prellenados, `external_id` y `metadata`.
- **Webhook:** firma con `X-Docuseal-Signature: {ts}.{hex HMAC-SHA256(secreto, "{ts}.{cuerpo}")}` y 5 minutos de tolerancia. Si recibe una respuesta distinta de 2xx, reintenta con espera de 2^n minutos.
- **Idempotencia:** el cuerpo no trae un id de evento, así que la clave debe salir del tipo de evento y del envío.
- **Documento firmado:** el PDF lleva una firma PAdES y se obtiene con `GET /api/submissions/{id}`.

## Escenario 2: el webhook de firma

`POST /api/v1/webhooks/docuseal` es público, y lo autentica la firma:

| Aspecto | Diseño |
|---|---|
| Autenticación | `X-Docuseal-Signature` verificada con `DOCUSEAL_WEBHOOK_SECRET`, con 5 minutos de tolerancia |
| Eventos | `form.completed` y `submission.completed` firman. `form.declined` deja el contrato `SIGNATURE_DECLINED` con su motivo y avisa a finanzas |
| Idempotencia | La clave es `{event_type}:{submission_id}`, registrada en `signature_provider_events`. Llegan los dos eventos de completado: el primero firma y el segundo responde `DUPLICATE` |
| Correlación | Se consulta el envío en DocuSeal. Debe estar `completed` y traer el `external_id` del contrato |
| Efecto | Fuera de transacción se descarga el PDF firmado, se guarda y se calcula su hash. Luego, con el lote y el contrato bloqueados, se registra la firma, el lote pasa a `SOLD` y se avisa a legal. El resultado se registra en esa misma transacción (corrección de T91) |
| Reenvío manual | `POST /api/v1/contracts/{contractId}/signature-request` (`FINANCE_ADMIN`) vuelve a encolar un envío fallido o rechazado |

**Respuestas:**
- **400 `INVALID_WEBHOOK_SIGNATURE`:** la firma no es de DocuSeal.
- **200:** todo evento auténtico, también el ignorado, con su resultado: `SIGNED`, `DUPLICATE`, `DECLINED`, `REJECTED` o `IGNORED`.
- **502 `SIGNATURE_PROVIDER_ERROR`:** DocuSeal no pudo confirmar el envío, y lo reenvía más tarde.
- **503 `SIGNATURES_UNAVAILABLE`:** no hay secreto configurado.

## Evidencia

- **T82 (PR #39):**
  - pruebas unitarias y de integración;
  - recorrido local con Docker Compose y un DocuSeal real: 27 de 27, y 4 de 4 con DocuSeal caído.
- **T91 (PR #40):** los dos eventos de completado y un reenvío llegan a la vez. El contrato se firma y el lote se vende una sola vez.

- **Producción, el 2026-11-26.** Render apunta a DocuSeal Cloud en modo de prueba:
  - `DOCUSEAL_URL` y `DOCUSEAL_PUBLIC_URL` valen `https://docuseal.com`;
  - la hoja de firma es la plantilla 7162054, creada con `POST /api/templates/pdf`;
  - el webhook escucha `form.completed`, `form.declined` y `submission.completed`.

  Se volvió a enviar a firma el contrato 1 de la prueba T92, con `POST /contracts/1/signature-request`:

| Hora (UTC) | Paso | Resultado |
|---|---|---|
| 00:46:02 | El job lo envía a DocuSeal | Envío 14099823, con los cinco campos llenos y el mismo SHA-256 del contrato. El contrato queda `SIGNATURE_REQUESTED` con `signingUrl` en `docuseal.com/s/…` |
| 00:56:14 | El comprador firma en el enlace | — |
| 00:56:17.989 | Llega `form.completed` | `SIGNED`. El contrato queda firmado y su PDF firmado (79 166 bytes, con firma PAdES) se guarda en el almacenamiento |
| 00:56:18.191 | Llega `submission.completed`, 202 ms después | `DUPLICATE`: no cambia nada |

  Después, el catálogo público muestra el lote `E2E-02` como `SOLD`.

## Pendiente

- **Cuenta de producción de DocuSeal.** El modo de prueba es para desarrollo. Una operación real necesita el plan Pro, a US$ 20 por usuario al mes más US$ 0,20 por documento firmado por API, o un DocuSeal autoalojado.
- **Cláusula de aceptación** de la firma electrónica en el modelo de contrato, por el marco legal de arriba.

## Fuentes

- Ley N.° 27269 y D. S. N.° 052-2008-PCM: https://sovos.com/es/blog/iva/que-dice-la-ley-de-firma-electronica-en-peru/ y https://www.forvismazars.com/pe/es/insights/tax-alerts/validez-juridica-firmas-medios-electronicos
- Firma Perú para entidades públicas: https://www.gob.pe/22275 y https://www.gob.pe/22271
- DocuSeal, precios y edición de código abierto: https://www.docuseal.com/pricing y https://www.docuseal.com/on-premises
- DocuSign, planes de desarrollador: https://ecom.docusign.com/plans-and-pricing/developer

Precios consultados el 2026-11-25.
