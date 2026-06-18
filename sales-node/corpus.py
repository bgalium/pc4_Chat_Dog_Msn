"""
Corpus de entrenamiento para el bot de ventas — Tienda de Mascotas Dog Messenger.
Sin importaciones externas. Todo el vocabulario está embebido directamente.

Estructura:
  STOPWORDS   — palabras vacías del español (archivo externo + set manual)
  PRODUCTS    — catálogo de productos de tienda de mascotas con sinónimos
  INTENTS     — frases de entrenamiento por intención (muchas variaciones)
"""
import os as _os

def _load_stopwords_file() -> set:
    """Carga stopwords-es.txt si existe en data/. Retorna set vacío si no."""
    path = _os.path.join(_os.path.dirname(__file__), 'data', 'stopwords-es.txt')
    if not _os.path.exists(path):
        return set()
    with open(path, encoding='utf-8') as f:
        loaded = {line.strip().lower() for line in f if line.strip()}
    print(f'[Corpus] Stopwords externas cargadas: {len(loaded)} palabras')
    return loaded

# ── Stop words español ────────────────────────────────────────────────────────
# Base manual + stopwords-iso/stopwords-es (stopwords_es.txt en data/)
# hola NO está en ninguno de los dos: activa la intención AYUDA
_BASE_STOPWORDS = {
    # artículos
    'el','la','los','las','un','una','unos','unas','lo',
    # preposiciones
    'a','ante','bajo','con','contra','de','desde','durante','en','entre',
    'hacia','hasta','mediante','para','por','segun','sin','sobre','tras',
    'al','del','mediante','excepto','salvo','incluso','mas',
    # conjunciones
    'y','e','o','u','pero','sino','que','porque','pues','aunque','si',
    'cuando','donde','como','ni','mientras','aunque','pese','sea',
    # pronombres personales
    'yo','tu','el','ella','nosotros','vosotros','ellos','ellas','usted','ustedes',
    'me','te','se','nos','os','le','les','mio','mia','tuyo','tuya','suyo','suya',
    # pronombres demostrativos
    'este','ese','aquel','esta','esa','aquella','estos','esos','aquellos',
    'estas','esas','aquellas','esto','eso','aquello',
    # pronombres posesivos
    'mi','mis','tu','tus','su','sus','nuestro','nuestra','nuestros','nuestras',
    # verbos auxiliares y cópulas
    'es','son','esta','estan','hay','soy','somos','eres','sois',
    'era','eras','eramos','eran','fue','fui','fuimos','fueron',
    'ser','estar','haber','tener','tengo','tienes','tiene','tenemos','tienen',
    'he','has','ha','hemos','han','habia','habian',
    # verbos modales y comunes
    'puede','puedo','puedes','pueden','podemos','poder',
    'debo','debe','debes','deben','debemos',
    'ir','voy','vas','va','vamos','van','iba','ibas',
    'hacer','hago','haces','hace','hacemos','hacen',
    'ver','veo','ves','vemos','ven',
    'dar','doy','das','da','damos','dan',
    'saber','se','sabes','sabe','sabemos','saben',
    'poner','pongo','pones','pone','ponemos','ponen',
    'venir','vengo','vienes','viene','venimos','vienen',
    'decir','digo','dices','dice','decimos','dicen',
    # adverbios comunes
    'muy','mas','tambien','asi','bien','mal','ya','aun','todavia',
    'aqui','alli','aca','alla','cerca','lejos','antes','despues',
    'siempre','nunca','jamas','quizas','tal','vez','solo','solamente',
    'apenas','casi','todo','nada','algo','alguien','nadie','cada',
    'mismo','propio','otro','otra','otros','otras',
    # saludos y muletillas (no semánticas para intención)
    'ok','okay','bueno','bien','claro','obvio','exacto','correcto',
    'si','no','quizas','maybe','entonces','pues','bueno','vale',
    'hey','eh','ah','oh','uh','hmm','mm',
    # tiempo
    'hoy','ayer','manana','ahora','pronto','despues','luego','tarde',
    'temprano','lunes','martes','miercoles','jueves','viernes','sabado','domingo',
    # numerales comunes como palabras
    'uno','dos','tres','cuatro','cinco','seis','siete','ocho','nueve','diez',
    'primero','segundo','tercero','ultimo','siguiente','anterior',
    # cortesía (hola NO es stop word: activa la intención AYUDA)
    'favor','gracias','porfavor','porfa','disculpa','perdona','permiso',
    'bienvenido','adios','bye','chau','hasta',
}

# Fusión: base manual + archivo externo - palabras clave que necesitamos
_FILE_STOPWORDS = _load_stopwords_file()
STOPWORDS = (_BASE_STOPWORDS | _FILE_STOPWORDS) - {
    'hola',          # activa AYUDA
    'registrar',     # activa REGISTRAR
    'pedido',        # activa PEDIDO
    'comprar',       # activa PEDIDO
    'consultar',     # activa CONSULTAR
    'reporte',       # activa REPORTE
    'ayuda',         # activa AYUDA
}

# ── Catálogo de productos — Tienda de Mascotas ────────────────────────────────
# Usado para enriquecer el corpus de ORDER y entender productos específicos
PRODUCTS = {
    'alimentos_perro': [
        'croquetas','pienso','kibble','balanceado','alimento','comida','seco',
        'humedo','lata','pouch','sobre','sachet','barf','raw','natural',
        'premium','gourmet','light','senior','cachorro','adulto','giant','small',
        'royal','canin','hills','science','purina','proplan','eukanuba','pedigree',
        'acana','orijen','iams','advance','josera','taste','wild',
        'snack','premio','golosina','galleta','hueso','palo','jerky','treat',
        'carne','pollo','salmon','res','cordero','pavo','atun','higado','tripas',
    ],
    'alimentos_gato': [
        'whiskas','felix','gatarina','friskies','fancy','feast','iams','gatos',
        'gatito','kitten','feline','felino','tuna','sardina','camaron','camarones',
        'pate','terrina','mouse','mousse','croqueta','pienso',
    ],
    'alimentos_otros': [
        'alpiste','semilla','mezcla','pajaro','ave','perico','loro','periquito',
        'hamster','conejo','cobayo','cuy','pellet','heno','alfalfa','zanahoria',
        'pez','peces','acuario','escamas','flake','granulo',
    ],
    'accesorios_perro': [
        'correa','collar','arnés','arnes','pechera','bozal','tag','placa',
        'identificacion','gps','rastreador','tracker','extension','retractil',
        'cama','colchon','manta','cobija','almohada','cubil','cueva','cucha',
        'cesta','canasta','hamaca','sofa','sillon',
        'transportadora','transportin','jaula','kennel','perrera','canil',
        'bolso','mochila','carrito','cochecito','stroller',
        'ropa','sueter','sweater','abrigo','impermeable','botas','zapatos',
        'pijama','camiseta','vestido','disfraz','bandana','pañuelo',
    ],
    'juguetes': [
        'pelota','peluche','cuerda','frisbee','mordedor','kong','chew',
        'interactivo','puzzle','rompecabezas','dispensador','snuffle','mat',
        'laser','pluma','raton','ratoncito','pajaro','tunnel','cueva',
        'tobogan','columpio','rueda','hamster','pelota','sonido','chillon',
        'flotante','agua','piscina','aspersor','sprinkler',
    ],
    'higiene': [
        'shampoo','champu','acondicionador','conditioner','perfume','colonia',
        'desodorante','toalla','secador','cepillo','peine','cardador','furminator',
        'cortauñas','tijeras','maquinilla','afeitadora','clipper','trimmer',
        'dental','cepillo dientes','pasta dental','enjuague','bucal',
        'orejeras','limpiador oidos','gotas','toallitas','wet wipes',
        'pañal','banda','bolsa','deposiciones','poop bag',
        'antipulgas','garrapata','antipiojos','pipeta','collar','antiparasitario',
        'spray','locion','repelente',
    ],
    'salud_veterinaria': [
        'vacuna','vacunacion','desparasitacion','desparasitante','vermifugo',
        'antiparasitario','pastilla','comprimido','tableta','capsula','syrup',
        'jarabe','suplemento','vitamina','omega','probiotico','prebiotico',
        'articulacion','glucosamina','condroitina','colageno',
        'antibiotico','antiinflamatorio','analgesico','calmante','sedante',
        'cicatrizante','pomada','crema','gel','vendaje','gasa','curita',
        'consulta','atencion','emergencia','urgencia','hospitalizacion',
        'cirugia','esterilizacion','castracion','vacunas','chip','microchip',
    ],
    'servicios': [
        'bano','banio','grooming','peluqueria','estilismo','spa',
        'corte','pelo','unas','garras','limpieza',
        'paseo','caminata','ejercicio','adiestramiento','entrenamiento',
        'guarderia','hotel','hospedaje','cuidado','daycare',
        'fotografia','foto','sesion','retrato',
        'transporte','delivery','envio','despacho','domicilio',
    ],
    'accesorios_hogar': [
        'bebedero','comedero','plato','tazón','tazon','fuente','dispensador',
        'agua','automatico','doble','triple','elevado','antideslizante',
        'arenero','arena','arcilla','silice','bentonita','cristal',
        'rascador','poste','arbol','cat tree','hamaca','cama gato',
        'puerta','barrera','valla','cerca','separador','gate',
        'limpiador','quitamanchas','enzimas','orina','olor',
    ],
}

# Vocabulario plano de todos los productos (para lookup rápido)
ALL_PRODUCTS = {w for words in PRODUCTS.values() for w in words}

# ── Intenciones — frases de entrenamiento ─────────────────────────────────────
# Cada lista tiene muchas variaciones para maximizar cobertura de similitud coseno
INTENTS = {

    # ── REGISTRAR ────────────────────────────────────────────────────────────
    'REGISTRAR': [
        'quiero registrarme como cliente nuevo',
        'necesito crear una cuenta en la tienda',
        'como me registro para poder comprar',
        'deseo ser cliente de la tienda',
        'me gustaria darme de alta como usuario',
        'quisiera inscribirme para hacer pedidos',
        'como puedo crear mi perfil de cliente',
        'soy nuevo y quiero registrarme',
        'quiero abrir una cuenta para comprar',
        'necesito registrar mis datos como cliente',
        'quiero ser parte de la tienda',
        'como me puedo afiliar a la tienda',
        'deseo crear cuenta nueva cliente',
        'inscribir mis datos en el sistema',
        'quiero registrar mi nombre y contacto',
        'dame de alta como cliente por favor',
        'alta cliente tienda mascotas registro',
        'nuevo usuario registro cuenta tienda',
        'quiero empezar comprar necesito cuenta',
        'primera vez quiero registrarme sistema',
        'como creo mi cuenta para pedir productos',
        'quisiera registrar mis datos personales',
        'quiero guardar mis datos para compras',
        'registrar nombre telefono correo cliente',
        'como me uno como cliente tienda',
        'quiero unirme al sistema de ventas',
        'necesito mi numero de cliente para pedir',
        'no tengo cuenta quiero registrarme',
        'nueva cuenta cliente mascotas',
        'me podrias registrar como cliente',
        'quiero ser miembro tienda mascotas',
        'inscripcion cliente nueva tienda perros',
    ],

    # ── PEDIDO ───────────────────────────────────────────────────────────────
    'PEDIDO': [
        'quiero comprar croquetas para mi perro',
        'necesito hacer un pedido de comida para mascotas',
        'quisiera ordenar accesorios para mi gato',
        'me gustaria pedir una correa y collar',
        'quiero adquirir alimento balanceado premium',
        'necesito comprar shampoo para perro',
        'deseo hacer un pedido de juguetes',
        'quiero solicitar vacunas para mi mascota',
        'me interesa comprar una cama para perro',
        'quiero pedir comida humeda para gato',
        'quisiera comprar croquetas royal canin',
        'necesito ordenar accesorios para cachorro',
        'quiero hacer un pedido de snacks y premios',
        'deseo comprar transportadora para mascota',
        'me gustan las croquetas premium quiero pedir',
        'quiero comprar collar antipulgas para perro',
        'necesito pedir suplementos vitaminas mascota',
        'quisiera ordenar peluche y juguetes perro',
        'quiero adquirir comedero bebedero automatico',
        'necesito comprar arena para gato arenero',
        'deseo pedir servicio baño grooming perro',
        'quiero ordenar correa retractil y arnés',
        'me gustaria comprar ropa para mi perro',
        'necesito pedir antiparasitario pipeta pulgas',
        'quiero solicitar servicio paseo perros',
        'quisiera ordenar pelota frisbee juguetes',
        'necesito comprar pañales perro pequeño',
        'quiero pedir varios productos mascotas',
        'deseo comprar kit higiene dental perro',
        'necesito hacer pedido urgente comida gato',
        'quiero ordenar alimento natural barf perro',
        'me gustaria comprar mochila transportadora',
        'necesito pedir cortauñas cepillo grooming',
        'quiero comprar hamaca cama gato rascador',
        'deseo ordenar snacks carne seca perro',
        'quiero adquirir pienso light senior perro',
        'necesito comprar repelente antigarrapatas',
        'quisiera pedir periquito alpiste semillas',
        'quiero hacer pedido camas cobijas mascotas',
        'necesito ordenar productos higiene bano',
        'quiero comprar accesorios nuevos mascota',
        'deseo pedir comida premium alta calidad',
        'necesito uno dos tres unidades producto',
        'quiero pedir cantidad precio unidad total',
        'dame croquetas perro adulto grande bolsa',
        'enviame shampoo antiparasitario mascotas',
        'mandame juguetes mordedor cachorro pequeño',
        'trae collar correa arnés paseo perro',
        'quiero tres bolsas croquetas precio mejor',
        'necesito cinco unidades snacks premios',
    ],

    # ── CONSULTAR ────────────────────────────────────────────────────────────
    'CONSULTAR': [
        'cuales son mis pedidos anteriores',
        'quiero ver el historial de mis compras',
        'que pedidos tengo registrados',
        'como esta mi ultimo pedido',
        'cuanto tiempo tarda mi pedido en llegar',
        'donde esta mi pedido que hice antes',
        'quiero revisar el estado de mi compra',
        'me puedes mostrar mis pedidos recientes',
        'cuantos pedidos he hecho hasta ahora',
        'que compré la semana pasada',
        'quiero saber que pedí anteriormente',
        'consultar mis pedidos por favor',
        'ver historial compras tienda mascotas',
        'rastrear seguimiento pedido numero',
        'cual es el estado actual mi pedido',
        'mis pedidos estan confirmados o pendientes',
        'cuando llega lo que pedi',
        'quiero informacion sobre mis compras',
        'buscar pedido cliente historial',
        'cual fue el ultimo producto que compre',
        'mostrarme lista todos mis pedidos',
        'pedidos musica ayer semana pasada mes',
        'cuales mis pedidos recientes anteriores',
        'estado entrega pedido numero cliente',
        'ver compras realizadas historial completo',
        'que productos compre cuanto gaste total',
        'mis compras resumen detalle pedidos',
        'buscar pedido por fecha producto',
        'confirmar recepcion pedido anterior',
        'seguimiento envio despacho pedido',
        'cuando enviaron mi pedido',
        'tracking rastreo mi compra',
        'ver factura comprobante pedido',
        'detalle pedido anterior cliente',
        'lista compras productos adquiridos',
    ],

    # ── REPORTE ──────────────────────────────────────────────────────────────
    'REPORTE': [
        'dame el reporte general de ventas',
        'quiero ver las estadisticas de la tienda',
        'cuantos clientes hay registrados en total',
        'cual es el total de pedidos realizados',
        'cuanto se ha vendido hasta ahora',
        'dame un informe completo de ventas',
        'cuales son los ingresos totales tienda',
        'me puedes dar un resumen de las ventas',
        'quiero ver los numeros de la tienda',
        'estadisticas generales tienda mascotas',
        'reporte ventas ingresos clientes total',
        'cuanto dinero se ha generado ventas',
        'balance general resultados tienda',
        'metricas rendimiento ventas mascotas',
        'resumen ejecutivo ventas periodo',
        'informe estadistico clientes pedidos',
        'datos generales sistema ventas',
        'cuantas ventas hay en el sistema',
        'total facturado ingresos soles ventas',
        'reporte completo ganancias tienda',
        'estadisticas clientes nuevos pedidos',
        'ver todos los numeros ventas totales',
        'informame sobre las ventas totales',
        'quiero saber cuanto se vendio total',
        'dame cifras ventas clientes registrados',
        'resumen negocio tienda mascotas total',
        'kpis metricas negocio ventas tienda',
        'como van las ventas en total general',
    ],

    # ── AYUDA / MENU ─────────────────────────────────────────────────────────
    'AYUDA': [
        'hola buenas en que me puedes ayudar',
        'que puedes hacer por mi',
        'como funciona este sistema',
        'cuales son las opciones disponibles',
        'necesito ayuda no se como empezar',
        'me puedes explicar que servicios tienes',
        'que productos venden en la tienda',
        'que tipo de mascotas atienden',
        'cuales son sus categorias de productos',
        'menu principal opciones sistema ventas',
        'inicio comenzar empezar sistema bot',
        'que haces como te uso bot ventas',
        'explicame como funciona la tienda',
        'que debo hacer para comprar',
        'guia ayuda instrucciones bot tienda',
        'como se usa este sistema de ventas',
        'bienvenido tienda mascotas dog messenger',
        'cuentes que puedes hacer',
        'informame sobre los servicios disponibles',
        'quiero saber que ofrece la tienda',
        'que tipo de productos tienen disponibles',
        'tienen todo para mascotas que necesito',
        'listado servicios productos tienda',
        'instrucciones uso bot ventas mascotas',
        'primera vez aqui como funciona',
        'no entiendo como comprar ayudame',
        'guiame proceso compra registro pedido',
    ],
}

# ── Respuestas naturales por intención ────────────────────────────────────────
# El bot elige una al azar para parecer más natural
RESPONSES_REGISTRO_INICIO = [
    "¡Claro! Para registrarte necesito tu nombre completo. ¿Cuál es?",
    "¡Con gusto te registro! ¿Cómo te llamas?",
    "¡Perfecto! Empecemos. ¿Cuál es tu nombre completo?",
    "¡Bienvenido a Dog Messenger Store! ¿Tu nombre completo, por favor?",
]

RESPONSES_PEDIDO_INICIO = [
    "¡Excelente elección! ¿Cuál es tu ID de cliente? (Si no tienes, escribe 'registrar')",
    "¡Con gusto proceso tu pedido! ¿Tu número de cliente?",
    "¡Perfecto! Para continuar necesito tu ID de cliente.",
    "¡Vamos a hacer tu pedido! ¿Cuál es tu ID de cliente?",
]

RESPONSES_CONSULTA_INICIO = [
    "Claro, voy a buscar tus pedidos. ¿Cuál es tu ID de cliente?",
    "¡Por supuesto! Dame tu número de cliente para ver tu historial.",
    "Enseguida consulto tus pedidos. ¿Tu ID de cliente?",
]

RESPONSES_NO_ENTENDIDO = [
    "No entendí bien tu consulta. Puedo ayudarte a registrarte, hacer pedidos, consultar tus compras o darte un reporte. ¿Qué necesitas?",
    "Hmm, no capté eso. ¿Quieres registrarte, hacer un pedido, ver tus pedidos o un reporte de ventas?",
    "No estoy seguro de entender. Prueba con: 'registrar', 'pedido', 'mis pedidos' o 'reporte'.",
    "Disculpa, no te entendí. Escribe 'menu' para ver qué puedo hacer por ti.",
]

MENU_TEXT = (
    "🐾 Dog Messenger Store — Bot de Ventas\n"
    "─────────────────────────────────────\n"
    "Soy el asistente de la tienda de mascotas.\n"
    "Puedo ayudarte con:\n\n"
    "  1️⃣  Registrarte como cliente\n"
    "  2️⃣  Hacer un pedido (croquetas, accesorios,\n"
    "      juguetes, servicios y más)\n"
    "  3️⃣  Consultar tus pedidos anteriores\n"
    "  4️⃣  Reporte general de ventas\n\n"
    "Escribe en lenguaje natural o el número de opción."
)
