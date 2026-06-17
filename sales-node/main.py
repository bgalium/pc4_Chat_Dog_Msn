"""
Nodo de Ventas — Dog Messenger
Lenguaje: Python 3.10+  (LP3)
Concurrencia: threading.Thread para el receptor (patrón Thread-per-role)

Flujo:
  1. Se conecta al servidor Dog Messenger como cliente con username "ventas"
  2. El servidor le asigna un user_id y lo registra en UserRegistry
  3. Los usuarios del chat envían mensajes SALES a receiver_id = VENTAS_ID
  4. Este nodo procesa los pedidos y responde vía TEXT al usuario solicitante
"""

import socket
import sys
import threading
import time
import json

from protocol import (
    read_message, send_message,
    AUTH, TEXT, SALES, METRICS, ERROR,
    SALES_REGISTER, SALES_ORDER, SALES_QUERY, SALES_REPORT,
    SERVER_ID, BROADCAST
)
from storage      import Storage
from sales_engine import SalesEngine

SALES_USERNAME = "ventas"
DEFAULT_HOST   = "localhost"
DEFAULT_PORT   = 8080


class SalesNode:
    """
    Nodo especializado que actúa como cliente del servidor Dog Messenger
    y automatiza la gestión de ventas.

    Usa un hilo daemon para recibir mensajes (ReceiverThread pattern)
    y el hilo principal como loop de control.
    """

    def __init__(self, host: str, port: int):
        self.host    = host
        self.port    = port
        self.sock    = None
        self.user_id = -1
        self.engine  = SalesEngine(Storage())
        self.running = False
        self._lock   = threading.Lock()   # protege escrituras al socket

    # ── Conexión y autenticación ────────────────────────────────────────────

    def connect(self):
        print(f"[Sales] Conectando a {self.host}:{self.port}...")
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.connect((self.host, self.port))
        self._authenticate()

    def _authenticate(self):
        payload = SALES_USERNAME.encode('utf-8') + b'\x00'
        self._send(AUTH, SERVER_ID, BROADCAST, payload)

        type_code, _, _, resp = read_message(self.sock)
        if type_code != AUTH:
            raise RuntimeError(f"Respuesta inesperada al AUTH: tipo={type_code}")

        status        = resp[0]
        self.user_id  = (resp[1] << 8) | resp[2]
        message       = resp[3:].decode('utf-8')

        if status != 0:
            raise RuntimeError(f"Auth rechazado: {message}")
        print(f"[Sales] ✅ Autenticado como '{SALES_USERNAME}' → ID #{self.user_id}")
        print(f"[Sales] Los usuarios deben enviar mensajes SALES al ID #{self.user_id}")

    # ── Loop principal ──────────────────────────────────────────────────────

    def start(self):
        self.running = True
        t = threading.Thread(target=self._receive_loop, name="sales-receiver", daemon=True)
        t.start()
        print("[Sales] Nodo activo. Ctrl+C para detener.\n")
        try:
            while self.running:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n[Sales] Deteniendo nodo...")
        finally:
            self.running = False
            if self.sock:
                self.sock.close()

    # ── Recepción de mensajes ───────────────────────────────────────────────

    def _receive_loop(self):
        """Hilo daemon: escucha mensajes del servidor continuamente."""
        while self.running:
            try:
                type_code, sender_id, receiver_id, payload = read_message(self.sock)
                self._dispatch(type_code, sender_id, payload)
            except ConnectionError as e:
                if self.running:
                    print(f"[Sales] ❌ Conexión perdida: {e}")
                self.running = False
            except Exception as e:
                if self.running:
                    print(f"[Sales] Error procesando mensaje: {e}")

    def _dispatch(self, type_code: int, sender_id: int, payload: bytes):
        if type_code == SALES:
            self._handle_sales(sender_id, payload)
        elif type_code == METRICS:
            self._handle_metrics(sender_id)
        elif type_code == TEXT:
            text = payload.decode('utf-8', errors='replace')
            print(f"[Sales] Mensaje de #{sender_id}: {text}")
        elif type_code == ERROR:
            code = payload[0] if payload else -1
            msg  = payload[1:].decode('utf-8', errors='replace') if len(payload) > 1 else '?'
            print(f"[Sales] ⚠️  Error del servidor (código {code}): {msg}")

    # ── Handlers de SALES ───────────────────────────────────────────────────

    def _handle_sales(self, sender_id: int, payload: bytes):
        if not payload:
            return
        cmd  = payload[0]
        data = payload[1:]

        if cmd == SALES_REGISTER:
            response = self.engine.handle_register(data)
            self._send_text(sender_id, response)

        elif cmd == SALES_ORDER:
            ok, order_id, response = self.engine.handle_order(data)
            self._send_text(sender_id, response)
            if ok:
                print(f"[Sales] Pedido #{order_id} confirmado para usuario #{sender_id}")

        elif cmd == SALES_QUERY:
            response = self.engine.handle_query(data)
            self._send_text(sender_id, response)

        elif cmd == SALES_REPORT:
            response = self.engine.handle_report()
            self._send_text(sender_id, response)

        else:
            self._send_text(sender_id, f"❌ Sub-comando desconocido: 0x{cmd:02X}")

    def _handle_metrics(self, sender_id: int):
        report  = self.engine.storage.get_report()
        payload = json.dumps(report).encode('utf-8')
        self._send(METRICS, self.user_id, sender_id, payload)

    # ── Envío ───────────────────────────────────────────────────────────────

    def _send_text(self, recipient_id: int, text: str):
        payload = text.encode('utf-8')
        self._send(TEXT, self.user_id, recipient_id, payload)
        print(f"[Sales] → #{recipient_id}: {text[:60]}{'...' if len(text) > 60 else ''}")

    def _send(self, type_code: int, sender_id: int, receiver_id: int, payload: bytes = b''):
        """Thread-safe: solo un hilo escribe al socket a la vez."""
        with self._lock:
            send_message(self.sock, type_code, sender_id, receiver_id, payload)


# ── Entry point ─────────────────────────────────────────────────────────────

if __name__ == '__main__':
    host = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_HOST
    port = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_PORT

    node = SalesNode(host, port)
    try:
        node.connect()
        node.start()
    except Exception as e:
        print(f"[Sales] ❌ Error fatal: {e}")
        sys.exit(1)
