"""
Lógica de negocio del nodo de ventas.
Procesa los sub-comandos SALES y genera respuestas legibles para el chat.
"""
import struct
from storage import Storage


class SalesEngine:
    def __init__(self, storage: Storage):
        self.storage = storage

    def handle_register(self, data: bytes) -> str:
        """data: name_utf8 + 0x00 + contact_utf8"""
        try:
            parts   = data.split(b'\x00', 1)
            name    = parts[0].decode('utf-8').strip()
            contact = parts[1].decode('utf-8').strip() if len(parts) > 1 else ''
            cid, is_new = self.storage.register_client(name, contact)
            if is_new:
                return f"✅ Cliente '{name}' registrado con ID #{cid}"
            return f"ℹ️ Cliente '{name}' ya existe (ID #{cid})"
        except Exception as e:
            return f"❌ Error al registrar: {e}"

    def handle_order(self, data: bytes) -> tuple[bool, int, str]:
        """
        data: client_id(2B) + item_utf8 + 0x00 + qty(4B big-endian) + price_cents(8B big-endian)
        Retorna (ok, order_id, mensaje)
        """
        try:
            client_id  = struct.unpack('>H', data[:2])[0]
            rest       = data[2:]
            null_pos   = rest.index(b'\x00')
            item       = rest[:null_pos].decode('utf-8')
            after_null = rest[null_pos + 1:]
            qty         = struct.unpack('>I', after_null[:4])[0]
            price_cents = struct.unpack('>q', after_null[4:12])[0]

            order = self.storage.create_order(client_id, item, qty, price_cents)
            receipt = (
                f"📄 COMPROBANTE #{order['id']}\n"
                f"   Cliente ID : #{client_id}\n"
                f"   Artículo   : {item}\n"
                f"   Cantidad   : {qty}\n"
                f"   Precio/u   : S/. {price_cents / 100:.2f}\n"
                f"   TOTAL      : S/. {order['total_cents'] / 100:.2f}\n"
                f"   Estado     : {order['estado']}"
            )
            return True, order['id'], receipt
        except Exception as e:
            return False, -1, f"❌ Error al procesar pedido: {e}"

    def handle_query(self, data: bytes) -> str:
        """data: client_id(2B)"""
        try:
            client_id = struct.unpack('>H', data[:2])[0]
            orders    = self.storage.get_orders_by_client(client_id)
            if not orders:
                return f"📭 No hay pedidos registrados para cliente #{client_id}"
            lines = [f"📋 Pedidos de cliente #{client_id}:"]
            for o in orders:
                lines.append(
                    f"  #{o['id']} — {o['articulo']} x{o['cantidad']}"
                    f" = S/. {o['total_cents'] / 100:.2f} [{o['estado']}]"
                )
            return '\n'.join(lines)
        except Exception as e:
            return f"❌ Error al consultar: {e}"

    def handle_report(self) -> str:
        r = self.storage.get_report()
        return (
            f"📊 REPORTE DE VENTAS\n"
            f"   Clientes registrados : {r['clientes_registrados']}\n"
            f"   Pedidos totales      : {r['pedidos_totales']}\n"
            f"   Ingresos totales     : S/. {r['ingresos_soles']:.2f}"
        )
