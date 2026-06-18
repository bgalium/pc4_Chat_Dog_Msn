# Dog Messenger

Sistema de mensajería distribuida con cliente desktop (Java), móvil (Kotlin/Android) y nodo de ventas automatizado (Python).

## Arquitectura

Arquitectura cliente-servidor centralizada (estrella). El servidor Java actúa como único punto de enrutamiento: los clientes envían mensajes al servidor y este los reenvía al destinatario.

```
                    ┌─────────────────────────────────┐
                    │           Servidor              │
                    │           Java 17               │
                    └────────────────┬────────────────┘
                                      │
               ┌──────────────────────┼─────────────────────┐
               │                      │                      │
    ┌──────────▼──────┐  ┌────────────▼────┐  ┌────────────▼────┐
    │    Desktop      │  │    Android      │  │  Nodo Ventas    │
    │    Java 17      │  │    Kotlin       │  │   Python 3.10   │
    └─────────────────┘  └─────────────────┘  └─────────────────┘
```

## Tecnologías

- **Java 17** — Servidor central + cliente desktop (Swing)
- **Kotlin** — Cliente Android (Android Studio)
- **Python 3.10+** — Nodo de ventas con NLP
- **Protocolo** — Sockets TCP binarios con header de 9 bytes

## Módulos

| Módulo | Lenguaje | Descripción |
|--------|----------|-------------|
| `server/` | Java 17 | Servidor central Thread-per-Connection |
| `desktop-client/` | Java 17 | Cliente Swing con cifrado E2E |
| `mobile-client/` | Kotlin | Cliente Android |
| `sales-node/` | Python | Chatbot con NLP (similitud coseno) |
| `protocol/` | — | Especificación del protocolo binario |

## Cómo ejecutar

### 1. Servidor
```bash
cd server
mvn compile exec:java
```

### 2. Cliente desktop
```bash
cd desktop-client
mvn compile exec:java
```

### 3. Nodo de ventas
```bash
cd sales-node
pip install -r requirements.txt
python main.py
```

### 4. Cliente móvil
Abrir `mobile-client/` en Android Studio y ejecutar sobre un emulador o dispositivo físico.

## Protocolo

Header binario de 9 bytes: `[payload_len:4][type:1][sender_id:2][receiver_id:2]`.

11 tipos de mensaje: `AUTH`, `TEXT`, `FILE_START`, `FILE_CHUNK`, `FILE_END`, `GROUP`, `QR`, `SALES`, `METRICS`, `ERROR`, `DH_EXCHANGE`.

Ver `protocol/PROTOCOL.md` para detalle completo.

## Funcionalidades

- Chat en tiempo real (texto, imágenes, archivos)
- Grupos de chat (crear, unirse, enviar)
- Clonación de historial por QR (ZXing)
- Encriptación punto a punto (ECDH secp256r1 + AES-256)
- Nodo de ventas autónomo con NLP (similitud coseno)
- Métricas de rendimiento en vivo
- Transferencia de archivos con verificación SHA-256

## Integrantes

- Walter Poma Navarro
- Jesus Santa Cruz Basilio
- Max Serrano Arostegui

## Licencia

Proyecto académico — CC4P1 Programación Concurrente y Distribuida, UNI 2026-I.
