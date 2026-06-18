"""
Motor NLP minimalista — similitud coseno con bag-of-words.
Solo usa stdlib: math, re, collections.
Sin pip, sin numpy, sin scikit-learn.
"""
import math
import re
from collections import Counter
from corpus import STOPWORDS, INTENTS

# ── Normalización ─────────────────────────────────────────────────────────────
_ACCENT_MAP = str.maketrans(
    'áéíóúüñÁÉÍÓÚÜÑ',
    'aeiouunAEIOUUN'
)

def normalize(text: str) -> str:
    """Quita acentos y pasa a minúsculas."""
    return text.translate(_ACCENT_MAP).lower()


def tokenize(text: str) -> list:
    """
    Normaliza → minúsculas → quita puntuación → filtra stopwords y tokens cortos.
    Devuelve lista de tokens significativos.
    """
    text = normalize(text)
    text = re.sub(r'[^\w\s]', ' ', text)    # quita puntuación
    text = re.sub(r'\d+', ' NUM ', text)     # normaliza números
    tokens = text.split()
    return [t for t in tokens if len(t) > 1 and t not in STOPWORDS]


# ── Vectores bag-of-words ─────────────────────────────────────────────────────

def build_vector(tokens: list) -> Counter:
    return Counter(tokens)


def cosine_sim(v1: Counter, v2: Counter) -> float:
    """Similitud coseno entre dos vectores Counter."""
    if not v1 or not v2:
        return 0.0
    common = set(v1) & set(v2)
    dot    = sum(v1[k] * v2[k] for k in common)
    mag1   = math.sqrt(sum(v * v for v in v1.values()))
    mag2   = math.sqrt(sum(v * v for v in v2.values()))
    if mag1 == 0 or mag2 == 0:
        return 0.0
    return dot / (mag1 * mag2)


# ── Índice de intenciones (se construye una vez al importar) ──────────────────
# Para cada intención, pre-tokenizamos todas las frases y construimos un
# vector agregado (suma de frecuencias). Más eficiente que comparar frase a frase.

def _build_intent_index() -> dict:
    """Construye {intent: Counter_agregado} desde INTENTS del corpus."""
    index = {}
    for intent, phrases in INTENTS.items():
        agg = Counter()
        for phrase in phrases:
            agg.update(build_vector(tokenize(phrase)))
        index[intent] = agg
    return index

_INTENT_INDEX = _build_intent_index()


# ── Detección de intención ────────────────────────────────────────────────────

def detect_intent(text: str, threshold: float = 0.10) -> tuple:
    """
    Retorna (intent, score) con la mejor intención detectada.
    Si ninguna supera el umbral retorna ('DESCONOCIDO', 0.0).

    threshold=0.10 es bajo a propósito: el español tiene muchas stopwords
    y las frases cortas producen vectores dispersos.
    """
    tokens = tokenize(text)
    if not tokens:
        return ('DESCONOCIDO', 0.0)

    user_vec = build_vector(tokens)
    best_intent, best_score = 'DESCONOCIDO', 0.0

    for intent, corpus_vec in _INTENT_INDEX.items():
        score = cosine_sim(user_vec, corpus_vec)
        if score > best_score:
            best_score = score
            best_intent = intent

    if best_score < threshold:
        return ('DESCONOCIDO', best_score)
    return (best_intent, best_score)


def top_intents(text: str, n: int = 3) -> list:
    """Retorna los n intents con mayor similitud (para debug)."""
    tokens  = tokenize(text)
    user_vec = build_vector(tokens)
    scores  = [
        (intent, cosine_sim(user_vec, vec))
        for intent, vec in _INTENT_INDEX.items()
    ]
    return sorted(scores, key=lambda x: x[1], reverse=True)[:n]
