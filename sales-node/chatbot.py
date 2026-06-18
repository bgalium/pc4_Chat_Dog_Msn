"""
Bot conversacional con NLP — similitud coseno para detectar intención.
Estado de conversación por usuario para flujos multi-turno.
Sin importaciones externas: solo stdlib + módulos locales.
"""
import random
import threading
from storage import Storage
from nlp import detect_intent
from corpus import (
    MENU_TEXT,
    RESPONSES_REGISTRO_INICIO,
    RESPONSES_PEDIDO_INICIO,
    RESPONSES_CONSULTA_INICIO,
    RESPONSES_NO_ENTENDIDO,
)

_CANCEL_TOKENS  = {'cancelar', 'cancel', 'salir', 'exit', 'volver', 'atras', 'menu', 'inicio'}
_GREET_TOKENS   = {'hola','hi','hey','buenas','ola','buenos','inicio','start',
                   'comenzar','empezar','ayuda','help','opciones','options'}


class _State:
    __slots__ = ('step', 'tmp')
    def __init__(self):
        self.step = 'IDLE'
        self.tmp  = {}


class Chatbot:
    """
    Bot de ventas para tienda de mascotas Dog Messenger.
    Usa similitud coseno sobre corpus local para entender lenguaje natural.
    Mantiene estado de conversación por sender_id (thread-safe).
    """

    def __init__(self):
        self._storage = Storage()
        self._lock    = threading.Lock()
        self._states  = {}          # sender_id (int) -> _State

    def handle(self, sender_id: int, raw: str) -> str:
        with self._lock:
            st = self._states.setdefault(sender_id, _State())
            return self._run(st, raw.strip())

    # ── Router principal ──────────────────────────────────────────────────────

    def _run(self, st: _State, text: str) -> str:
        lo = text.lower()

        # Comandos universales de cancelación
        if lo in _CANCEL_TOKENS or lo == 'menu':
            st.step, st.tmp = 'IDLE', {}
            return MENU_TEXT

        # Despachador por estado
        return {
            'IDLE':        self._idle,
            'REG_NAME':    self._reg_name,
            'REG_CONTACT': self._reg_contact,
            'ORD_CID':     self._ord_cid,
            'ORD_ITEM':    self._ord_item,
            'ORD_QTY':     self._ord_qty,
            'ORD_PRICE':   self._ord_price,
            'QRY_CID':     self._qry_cid,
        }.get(st.step, self._idle)(st, text, lo)

    # ── IDLE — detecta intención con NLP ─────────────────────────────────────

    def _idle(self, st, text, lo):
        # 1) Greetings cortos — coseno falla con 1-2 tokens
        words = set(lo.split())
        if words <= _GREET_TOKENS or lo in _GREET_TOKENS:
            return MENU_TEXT

        # 2) Opciones numéricas del menú (1/2/3/4)
        if lo in ('1',):
            st.step = 'REG_NAME'
            return random.choice(RESPONSES_REGISTRO_INICIO)
        if lo in ('4', 'reporte', 'estadisticas', 'estadísticas', 'stats', 'informe'):
            return self._report()

        # 3) Palabras clave directas — evita que NLP las confunda
        #    (palabras como "pedido" aparecen en varios corpus a la vez)
        _direct_reg = {'registrar','registro','registrarme','inscribir','alta'}
        _direct_ord = {'2','pedido','comprar','pedir','ordenar','compra','orden'}
        _direct_qry = {'3','consultar','historial','pedidos','mis pedidos','ver pedidos'}

        if lo in _direct_reg:
            st.step = 'REG_NAME'
            return random.choice(RESPONSES_REGISTRO_INICIO)
        if lo in _direct_ord:
            st.step = 'ORD_CID'
            return random.choice(RESPONSES_PEDIDO_INICIO)
        if lo in _direct_qry:
            st.step = 'QRY_CID'
            return random.choice(RESPONSES_CONSULTA_INICIO)

        # 4) NLP con similitud coseno para texto libre (frases largas)
        intent, score = detect_intent(text)

        if intent == 'REGISTRAR':
            st.step = 'REG_NAME'
            return random.choice(RESPONSES_REGISTRO_INICIO)
        if intent == 'PEDIDO':
            st.step = 'ORD_CID'
            return random.choice(RESPONSES_PEDIDO_INICIO)
        if intent == 'CONSULTAR':
            st.step = 'QRY_CID'
            return random.choice(RESPONSES_CONSULTA_INICIO)
        if intent == 'REPORTE':
            return self._report()
        if intent == 'AYUDA':
            return MENU_TEXT

        return random.choice(RESPONSES_NO_ENTENDIDO)

    # ── Registro ──────────────────────────────────────────────────────────────

    def _reg_name(self, st, text, lo):
        if len(text) < 2:
            return '¿Cuál es tu nombre completo?'
        st.tmp['name'] = text
        st.step = 'REG_CONTACT'
        return f'Nombre: {text}\n¿Tu contacto? (email o teléfono)'

    def _reg_contact(self, st, text, lo):
        name = st.tmp.get('name', '?')
        try:
            cid, is_new = self._storage.register_client(name, text)
        except Exception as e:
            st.step, st.tmp = 'IDLE', {}
            return f'❌ Error al registrar: {e}'
        st.step, st.tmp = 'IDLE', {}
        if is_new:
            return (f'✅ ¡Registrado en Dog Messenger Store!\n'
                    f'─────────────────────────────────\n'
                    f'Nombre   : {name}\n'
                    f'Contacto : {text}\n'
                    f'ID cliente: #{cid}\n\n'
                    f'Guarda ese número para hacer pedidos.\n'
                    f'Escribe "pedido" cuando quieras comprar algo 🐾')
        return (f'ℹ️ Ya estás registrado.\n'
                f'Tu ID de cliente es #{cid}.\n'
                f'Escribe "pedido" para comprar algo.')

    # ── Pedido ────────────────────────────────────────────────────────────────

    def _ord_cid(self, st, text, lo):
        # Acepta solo dígitos
        digits = ''.join(c for c in text if c.isdigit())
        if not digits:
            return ('Necesito tu número de ID de cliente (solo dígitos).\n'
                    'Ejemplo: si tu ID es #2 escribe: 2\n'
                    'Si aún no tienes ID escribe "cancelar" y luego "registrar".')
        st.tmp['cid'] = int(digits)
        st.step = 'ORD_ITEM'
        return f'ID #{digits} ✓\n¿Qué producto o servicio deseas? (ej: croquetas, shampoo, baño...)'

    def _ord_item(self, st, text, lo):
        st.tmp['item'] = text
        st.step = 'ORD_QTY'
        return f'Producto: {text}\n¿Cuántas unidades necesitas?'

    def _ord_qty(self, st, text, lo):
        digits = ''.join(c for c in text if c.isdigit())
        if not digits or int(digits) <= 0:
            return '¿Cuántas unidades? Ingresa un número mayor a 0.'
        st.tmp['qty'] = int(digits)
        st.step = 'ORD_PRICE'
        return f'Cantidad: {digits}\n¿Precio por unidad en soles? (ej: 29.90)'

    def _ord_price(self, st, text, lo):
        # Extrae primer número flotante del texto
        import re
        match = re.search(r'\d+([.,]\d+)?', text)
        if not match:
            return 'No encontré el precio. Ingresa un número (ej: 29.90).'
        price = float(match.group().replace(',', '.'))
        if price <= 0:
            return 'El precio debe ser mayor a 0.'

        cid   = st.tmp['cid']
        item  = st.tmp['item']
        qty   = st.tmp['qty']
        cents = round(price * 100)
        try:
            order = self._storage.create_order(cid, item, qty, cents)
        except Exception as e:
            st.step, st.tmp = 'IDLE', {}
            return f'❌ Error al crear pedido: {e}'

        total = price * qty
        st.step, st.tmp = 'IDLE', {}
        return (f'✅ ¡Pedido #{order["id"]} confirmado!\n'
                f'────────────────────────────────\n'
                f'Cliente  : #{cid}\n'
                f'Producto : {item} x{qty}\n'
                f'Precio/u : S/ {price:.2f}\n'
                f'Total    : S/ {total:.2f}\n'
                f'Estado   : {order["estado"]}\n\n'
                f'¡Gracias por comprar en Dog Messenger Store! 🐾')

    # ── Consulta ──────────────────────────────────────────────────────────────

    def _qry_cid(self, st, text, lo):
        digits = ''.join(c for c in text if c.isdigit())
        if not digits:
            return 'Ingresa tu número de cliente (solo dígitos).'
        cid    = int(digits)
        orders = self._storage.get_orders_by_client(cid)
        st.step, st.tmp = 'IDLE', {}
        if not orders:
            return (f'No encontré pedidos para el cliente #{cid}.\n'
                    f'¿Quizás aún no has hecho ninguno? Escribe "pedido" para empezar.')
        lines = [f'📋 Historial del cliente #{cid}:',
                 '─────────────────────────────────']
        for o in orders:
            unit  = o['precio_unit_cents'] / 100
            total = o['total_cents'] / 100
            lines.append(
                f'  #{o["id"]} — {o["articulo"]} x{o["cantidad"]}'
                f' — S/{unit:.2f} c/u — Total S/{total:.2f} [{o["estado"]}]'
            )
        return '\n'.join(lines)

    # ── Reporte ───────────────────────────────────────────────────────────────

    def _report(self) -> str:
        r = self._storage.get_report()
        return (f'📊 Reporte — Dog Messenger Store\n'
                f'─────────────────────────────────\n'
                f'Clientes registrados : {r["clientes_registrados"]}\n'
                f'Pedidos totales      : {r["pedidos_totales"]}\n'
                f'Ingresos totales     : S/ {r["ingresos_soles"]:.2f}')
