"""
Persistencia en archivos JSON.
threading.Lock garantiza que no dos hilos corrompan el archivo al mismo tiempo.
"""
import json
import os
import threading

DATA_DIR = os.path.join(os.path.dirname(__file__), 'data')


class Storage:
    def __init__(self):
        os.makedirs(DATA_DIR, exist_ok=True)
        self._lock     = threading.Lock()
        self._clients  = self._load('clients.json')
        self._orders   = self._load('orders.json')
        self._next_cid = max((int(k) for k in self._clients), default=0) + 1
        self._next_oid = max((int(k) for k in self._orders),  default=0) + 1

    def _load(self, filename: str) -> dict:
        path = os.path.join(DATA_DIR, filename)
        if os.path.exists(path):
            with open(path, 'r', encoding='utf-8') as f:
                return json.load(f)
        return {}

    def _save(self, filename: str, data: dict):
        path = os.path.join(DATA_DIR, filename)
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)

    def register_client(self, name: str, contact: str) -> tuple[int, bool]:
        """Registra cliente. Retorna (client_id, es_nuevo)."""
        with self._lock:
            for cid, c in self._clients.items():
                if c['nombre'] == name:
                    return int(cid), False
            cid = self._next_cid
            self._next_cid += 1
            self._clients[str(cid)] = {
                'id': cid, 'nombre': name, 'contacto': contact, 'pedidos': []
            }
            self._save('clients.json', self._clients)
            return cid, True

    def create_order(self, client_id: int, item: str, qty: int, price_cents: int) -> dict:
        with self._lock:
            oid   = self._next_oid
            self._next_oid += 1
            total = qty * price_cents
            order = {
                'id': oid, 'client_id': client_id, 'articulo': item,
                'cantidad': qty, 'precio_unit_cents': price_cents,
                'total_cents': total, 'estado': 'confirmado'
            }
            self._orders[str(oid)] = order
            if str(client_id) in self._clients:
                self._clients[str(client_id)]['pedidos'].append(oid)
                self._save('clients.json', self._clients)
            self._save('orders.json', self._orders)
            return order

    def get_orders_by_client(self, client_id: int) -> list:
        with self._lock:
            return [o for o in self._orders.values() if o['client_id'] == client_id]

    def get_report(self) -> dict:
        with self._lock:
            total_rev = sum(o['total_cents'] for o in self._orders.values())
            return {
                'clientes_registrados': len(self._clients),
                'pedidos_totales':      len(self._orders),
                'ingresos_soles':       round(total_rev / 100, 2)
            }
