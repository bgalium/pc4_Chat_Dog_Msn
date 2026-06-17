# CLASS_GUIDE — Dog Messenger Server

Guía de las clases del servidor explicada desde el punto de vista de
**Programación Concurrente y Sistemas Distribuidos**.

---

## Arquitectura general

```
                    ┌─────────────────────────────────┐
                    │           Server.java            │
                    │   hilo main — solo hace accept() │
                    └────────────────┬────────────────┘
                                     │ por cada cliente
              ┌──────────────────────┼─────────────────────┐
              │                      │                      │
   ┌──────────▼──────┐  ┌────────────▼────┐  ┌────────────▼────┐
   │ ClientHandler-1 │  │ ClientHandler-2  │  │ ClientHandler-3  │
   │   (Thread)      │  │   (Thread)       │  │   (Thread)       │
   └──────────┬──────┘  └────────────┬────┘  └────────────┬────┘
              │                      │                      │
              └──────────────────────▼──────────────────────┘
                              MessageRouter
                         (decide a quién reenviar)
                                     │
                              UserRegistry
                         (mapa de usuarios vivos)
```

---

## Clases del protocolo

### `protocol/MessageType.java`
**Qué es:** Enum Java con todos los tipos de mensaje del sistema.

**Por qué importa en concurrencia:** Los enums en Java son singletons thread-safe
por diseño del lenguaje. Cualquier hilo puede leer `MessageType.TEXT_MESSAGE`
sin necesidad de sincronización.

**Relación con Sistemas Distribuidos:** Define el vocabulario del protocolo.
Sin este contrato compartido, dos nodos escritos en lenguajes distintos
(Java, Python, Go) no se entenderían.

---

### `protocol/Message.java`
**Qué es:** Modelo de datos inmutable que representa un mensaje del protocolo.

**Por qué importa en concurrencia:** Al ser **inmutable** (todos los campos son
`final`, sin setters), un objeto `Message` puede pasarse entre hilos sin
ningún tipo de sincronización. No hay estado mutable que proteger.

Este es el patrón **Immutable Object** — uno de los más seguros en programación
concurrente porque elimina la posibilidad de race conditions sobre el objeto.

**Campos:**
| Campo | Tipo | Descripción |
|-------|------|-------------|
| `type` | `MessageType` | Qué tipo de mensaje es |
| `senderId` | `short` | ID del emisor (asignado al autenticarse) |
| `receiverId` | `short` | ID del receptor (`0xFFFF` = servidor) |
| `payload` | `byte[]` | Contenido del mensaje (texto, bytes de archivo, etc.) |

---

### `protocol/MessageParser.java`
**Qué es:** Clase utilitaria que serializa/deserializa mensajes según el protocolo binario.

**Por qué importa en concurrencia:** No tiene estado (`stateless`) — todos sus
métodos son estáticos y no guardan variables de instancia. Esto lo hace
intrínsecamente thread-safe: cualquier número de hilos puede llamar
`encode()` o `decode()` al mismo tiempo sin problemas.

**Detalle técnico crítico — `readFully()`:**
TCP es un protocolo de stream, no de mensajes. Cuando mandas 100 bytes,
el receptor puede recibir 40 bytes en una llamada y 60 en la siguiente.
Si usaras solo `read()`, leerías mensajes incompletos y corromperías todo.
`readFully()` sigue llamando a `read()` hasta tener exactamente los bytes
pedidos, garantizando integridad del mensaje.

```
Emisor manda:  [9 bytes header][payload]
                ↓
TCP puede entregar: [4 bytes] → [3 bytes] → [2 bytes] → [payload]
                ↓
readFully() acumula hasta tener los 9 bytes completos del header
```

---

## Clases del servidor

### `Server.java`
**Qué es:** Punto de entrada. Abre el `ServerSocket` y hace el loop de aceptación.

**Por qué importa en concurrencia:** El hilo `main` solo hace `accept()` —
nunca se bloquea atendiendo a un cliente específico. Por eso puede responder
a nuevas conexiones inmediatamente aunque haya 100 clientes conectados.

**Patrón:** Thread-per-Connection. Cada cliente obtiene su propio hilo.
Es simple de implementar y suficiente para un proyecto académico. En producción
se usaría un thread pool (ExecutorService) para limitar el número de hilos.

```java
// Lo que hace el hilo main en loop infinito:
Socket clientSocket = serverSocket.accept();  // bloquea hasta que llega alguien
new ClientHandler(clientSocket).start();       // le da su propio hilo y sigue
```

---

### `ClientHandler.java`
**Qué es:** Hilo dedicado a un cliente. Lee sus mensajes y los despacha.

**Por qué importa en concurrencia:** Hay un `ClientHandler` por cada cliente
conectado, todos corriendo en paralelo. El problema surge cuando dos
`ClientHandler` quieren escribir al mismo socket de un tercer cliente al
mismo tiempo → bytes mezclados → mensaje corrupto.

**Solución — `synchronized sendMessage()`:**
```java
public synchronized void sendMessage(Message msg) throws IOException {
    MessageParser.encode(msg, socket.getOutputStream());
}
```
El `synchronized` en el método garantiza que aunque 10 hilos llamen
`sendMessage()` al mismo tiempo sobre el mismo `ClientHandler`, solo uno
escribe a la vez. Los demás esperan en cola.

**Ciclo de vida del hilo:**
```
start() → run() → loop decode() → dispatch() → [IOException] → cleanup()
```

---

### `UserRegistry.java`
**Qué es:** Registro global (Singleton) de todos los usuarios conectados.

**Por qué importa en concurrencia:** Es **estado mutable compartido** —
el objeto más peligroso en concurrencia. Múltiples `ClientHandler` (hilos)
leen y escriben este mapa al mismo tiempo cuando usuarios se conectan o
desconectan.

**`HashMap` vs `ConcurrentHashMap`:**

| | `HashMap` | `ConcurrentHashMap` |
|---|-----------|---------------------|
| Thread-safe | ❌ NO | ✅ SÍ |
| Riesgo | Corrupción interna, bucle infinito | Ninguno |
| Rendimiento | Más rápido (un hilo) | Ligeramente más lento pero seguro |
| Bloqueo | No existe | Por segmento (no bloquea toda la tabla) |

Usar `HashMap` con múltiples hilos puede dejar la estructura interna corrupta,
incluso causar que un `get()` entre en bucle infinito. `ConcurrentHashMap`
usa bloqueos internos por segmento para permitir escrituras concurrentes
sin bloquear toda la tabla.

**`AtomicShort` para IDs:**
```java
private final AtomicShort idCounter = new AtomicShort(1);
// ...
short newId = idCounter.getAndIncrement(); // operación atómica, no hay race condition
```
Sin `AtomicShort`, dos clientes que se conecten al mismo microsegundo podrían
recibir el mismo ID.

---

### `MessageRouter.java`
**Qué es:** Singleton que decide a qué `ClientHandler` reenviar cada mensaje.

**Por qué importa en concurrencia:** El router es llamado por múltiples
`ClientHandler` simultáneamente. Su trabajo es encontrar el receptor en el
`UserRegistry` y llamar a su `sendMessage()`.

La protección contra escrituras concurrentes ya está en `ClientHandler.sendMessage()`
(que es `synchronized`), por lo que el router mismo no necesita sincronización
adicional para el envío.

**Flujo:**
```
ClientHandler-A (hilo)
    → router.route(msg)
        → registry.getHandler(receiverId)   → ClientHandler-B
        → clientHandlerB.sendMessage(msg)   → synchronized: espera si alguien más escribe
```

---

## Resumen de concurrencia por clase

| Clase | Patrón | Mecanismo |
|-------|--------|-----------|
| `Message` | Immutable Object | Campos `final`, sin setters |
| `MessageParser` | Stateless | Sin variables de instancia |
| `Server` | Thread-per-Connection | `new ClientHandler().start()` |
| `ClientHandler` | Monitor | `synchronized sendMessage()` |
| `UserRegistry` | Thread-safe Collection | `ConcurrentHashMap` + `AtomicShort` |
| `MessageRouter` | Delegate | Delega sync a `ClientHandler` |

---

## TODOs pendientes (issues del repo)

| Issue | Clase a modificar | Funcionalidad |
|-------|-------------------|---------------|
| #6  | `ClientHandler.handleAuth()` | Completar AUTH con UserRegistry |
| #9  | `MessageRouter.route()` | Routing completo con notificación offline |
| #12 | `MessageRouter` + nuevo `FileTransferManager` | Relay de archivos en chunks |
| #15 | `MessageRouter` + nuevo `GroupManager` | Grupos de chat |
| #18 | `ClientHandler` + nuevo `CryptoHelper` | Handshake Diffie-Hellman |
| #24 | Nuevo `MetricsCollector` | Contadores atómicos de rendimiento |
