"""
Protocolo binario de Dog Messenger.
Header: 9 bytes big-endian
  payload_len (4B) | type_code (1B) | sender_id (2B) | receiver_id (2B)
"""
import struct

HEADER_FMT  = '>iBHH'   # int + byte + ushort + ushort
HEADER_SIZE = 9

# Tipos de mensaje (deben coincidir con MessageType.java)
AUTH        = 1
TEXT        = 2
FILE_START  = 3
FILE_CHUNK  = 4
FILE_END    = 5
GROUP       = 6
QR          = 7
SALES       = 8
METRICS     = 9
ERROR       = 10
DH_EXCHANGE = 11

# Sub-comandos SALES
SALES_REGISTER = 0x01  # [0x01] name\0contact_utf8
SALES_ORDER    = 0x02  # [0x02] client_id(2B) item_utf8\0 qty(4B) price_cents(8B)
SALES_QUERY    = 0x03  # [0x03] client_id(2B)
SALES_REPORT   = 0x04  # [0x04]  sin datos adicionales

SERVER_ID   = 0
BROADCAST   = 0xFFFF


def recv_exact(sock, n: int) -> bytes:
    """Lee exactamente n bytes del socket (TCP puede fragmentar)."""
    buf = b''
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("Conexión cerrada por el servidor")
        buf += chunk
    return buf


def read_message(sock):
    """Retorna (type_code, sender_id, receiver_id, payload_bytes)."""
    raw = recv_exact(sock, HEADER_SIZE)
    payload_len, type_code, sender_id, receiver_id = struct.unpack(HEADER_FMT, raw)
    payload = recv_exact(sock, payload_len) if payload_len > 0 else b''
    return type_code, sender_id, receiver_id, payload


def send_message(sock, type_code: int, sender_id: int, receiver_id: int, payload: bytes = b''):
    """Envía un mensaje completo con header + payload."""
    header = struct.pack(HEADER_FMT, len(payload), type_code, sender_id, receiver_id)
    sock.sendall(header + payload)
