# Protocolo TCP — Dog Messenger

> Todos los nodos deben implementar este protocolo exactamente.
> Usar solo sockets nativos del lenguaje (java.net, socket de Python, net de Go).

---

## Header (9 bytes, big-endian)

```
┌──────────────────────┬────────────┬──────────────────┬────────────────────┐
│  payload_len  (4 B)  │ type  (1B) │ sender_id  (2 B) │ receiver_id  (2 B) │
│  unsigned int        │  ver tabla │  short           │ 0xFFFF = broadcast  │
└──────────────────────┴────────────┴──────────────────┴────────────────────┘
```

- **payload_len**: longitud en bytes del payload que sigue al header.
- **type**: tipo de mensaje (ver tabla).
- **sender_id**: ID del emisor (asignado por el servidor al autenticarse). `0x0000` = no autenticado.
- **receiver_id**: ID del receptor. `0xFFFF` = broadcast/servidor.

---

## Tipos de mensaje

| Hex  | Nombre            | Quien envía  | Descripción |
|------|-------------------|--------------|-------------|
| 0x01 | AUTH_REQUEST      | Cliente      | Login: `username\0` en UTF-8 |
| 0x02 | AUTH_RESPONSE     | Servidor     | `status(1B) user_id(2B) message_utf8` — status: 0=OK 1=ERROR |
| 0x03 | TEXT_MESSAGE      | Cliente      | Texto en UTF-8 cifrado |
| 0x04 | FILE_START        | Cliente      | `filename_len(2B) filename_utf8 file_size(8B)` |
| 0x05 | FILE_CHUNK        | Cliente      | Bytes crudos del archivo (max 4096 B por chunk) |
| 0x06 | FILE_END          | Cliente      | `sha256_hex(64B ASCII)` — checksum del archivo completo |
| 0x07 | GROUP_CREATE      | Cliente      | `group_name_utf8` |
| 0x08 | GROUP_JOIN        | Cliente      | `group_id(2B)` |
| 0x09 | GROUP_MESSAGE     | Cliente      | `group_id(2B) text_utf8` cifrado |
| 0x0A | QR_CLONE_REQUEST  | Cliente      | Vacío — pide token de clonación |
| 0x0B | QR_CLONE_TOKEN    | Servidor     | `token(32B) history_json_utf8` — historial serializado |
| 0x0C | SALES_REGISTER    | Cliente      | `nombre_utf8\0contacto_utf8` |
| 0x0D | SALES_ORDER       | Cliente      | `client_id(2B) item_utf8\0qty(4B) price_cents(8B)` |
| 0x0E | SALES_RECEIPT     | Sales Node   | `order_id_utf8\0receipt_json_utf8` |
| 0x0F | METRICS_REQUEST   | Cualquiera   | Vacío |
| 0x10 | METRICS_RESPONSE  | Servidor/Sales | JSON con métricas en UTF-8 |
| 0xFF | ERROR             | Servidor     | `code(1B) message_utf8` — ver códigos abajo |

---

## Códigos de error (payload byte 0)

| Código | Descripción |
|--------|-------------|
| 0x01   | Usuario ya conectado |
| 0x02   | Usuario no encontrado / offline |
| 0x03   | Grupo no existe |
| 0x04   | Transferencia fallida (checksum inválido) |
| 0x05   | Cliente de ventas no registrado |
| 0xFF   | Error genérico |

---

## Flujo de autenticación

```
Cliente                        Servidor
  │                               │
  │── AUTH_REQUEST (sender=0) ───►│
  │   payload: "walter\0"         │
  │                               │  Asigna user_id=1
  │◄─ AUTH_RESPONSE ──────────────│
  │   status=0, user_id=1         │
  │   (a partir de aqui sender=1) │
```

## Flujo de mensaje texto 1-a-1

```
Cliente A (id=1)               Servidor              Cliente B (id=2)
  │                               │                        │
  │── TEXT_MESSAGE ──────────────►│                        │
  │   sender=1, receiver=2        │                        │
  │   payload: [cifrado AES]      │── TEXT_MESSAGE ───────►│
  │                               │   sender=1, receiver=2 │
```

## Flujo de transferencia de archivo

```
Cliente A                     Servidor                Cliente B
  │── FILE_START ────────────►│── FILE_START ─────────►│
  │── FILE_CHUNK (4KB) ───────►│── FILE_CHUNK ─────────►│  (relay inmediato)
  │── FILE_CHUNK ...           │── FILE_CHUNK ...        │
  │── FILE_END (sha256) ──────►│ verifica checksum       │
  │                            │── FILE_END ────────────►│
```

## Flujo de clonación QR

```
Dispositivo A               Servidor             Dispositivo B
  │── QR_CLONE_REQUEST ────►│                         │
  │◄─ QR_CLONE_TOKEN ───────│  (A muestra QR)         │
  │   token + historial     │                         │
  │                         │◄── QR_CLONE_REQUEST ────│
  │                         │    payload: token        │
  │◄─ [Servidor reenvia     │                         │
  │    historial a B] ──────│─────────────────────────►│
```

## Flujo ventas

```
Cliente Chat              Servidor              Sales Node (Go)
  │── SALES_ORDER ───────►│── SALES_ORDER ──────►│
  │                        │                      │  Procesa pedido
  │                        │◄─ SALES_RECEIPT ─────│
  │◄─ TEXT_MESSAGE ────────│  (comprobante como   │
  │   (comprobante)         │   mensaje de chat)   │
```

---

## Cifrado E2E

1. Al autenticarse, el servidor envía parámetros DH públicos al cliente.
2. El cliente genera su par de llaves DH y envía su llave pública al servidor.
3. El servidor reenvía la llave pública de cada cliente a sus interlocutores.
4. Cada cliente deriva localmente la llave AES-256 compartida usando SHA-256 del secreto DH.
5. El servidor **nunca** conoce la llave AES final.
6. Formato del payload cifrado: `IV(16B) || ciphertext(AES/CBC/PKCS5)`.

---

## Notas de implementación

- Usar **big-endian** para todos los enteros multibyte.
- El servidor debe usar `synchronized` (Java) / `sync.Mutex` (Go) / `threading.Lock` (Python)
  al escribir en sockets para evitar corrupción bajo escrituras concurrentes.
- El tamaño máximo de un FILE_CHUNK es **4096 bytes**.
- Un `receiver_id = 0xFFFF` indica que el mensaje va dirigido al servidor mismo.
