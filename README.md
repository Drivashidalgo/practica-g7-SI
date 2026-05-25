# Práctica G7 – Sistemas Inteligentes: Detección de Fraude Bancario

> **Repositorio GitHub:** [https://github.com/Drivashidalgo/practica-g7-SI](https://github.com/Drivashidalgo/practica-g7-SI)

Sistema multiagente implementado con JADE para la detección de patrones fraudulentos en flujos de transacciones bancarias en tiempo real. El sistema identifica dos tipos de fraude: **ciclos de blanqueo de capitales** (mediante el algoritmo de Tarjan sobre un grafo dirigido) y **cuentas fan-in de alto riesgo** (mediante una red neuronal de grafos, GNN).

---

## Índice

1. [Arquitectura del sistema](#arquitectura-del-sistema)
2. [Dependencias e instalación](#dependencias-e-instalación)
3. [Instrucciones de ejecución](#instrucciones-de-ejecución)
4. [Datos de ejemplo](#datos-de-ejemplo)
5. [Declaración de uso de IA](#declaración-de-uso-de-ia)

---

## Arquitectura del sistema

El sistema sigue una arquitectura de pipeline multiagente con dos ramas de análisis paralelas. El flujo de datos va desde el simulador hasta la interfaz gráfica pasando por cinco agentes JADE con responsabilidades bien delimitadas.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         CAPA DE DATOS                                    │
│                                                                          │
│   data/transactions.csv  ──►  SimuladorTransacciones  ──►               │
│   (≈100 k transacciones)       (proceso separado,           │           │
│                                 50 ms / línea)               │           │
│                                                              ▼           │
│                                               data/transactions_live.csv │
└──────────────────────────────────────────────────────────────────────────┘
                                                              │
                                                              ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    CAPA DE PERCEPCIÓN  (JADE)                            │
│                                                                          │
│              ┌────────────────────────────────────┐                     │
│              │           AgentPerception           │                     │
│              │  Ticker cada 1 s                    │                     │
│              │  Lee líneas nuevas del CSV live     │                     │
│              │  Emite INICIO / FIN al AgentUI      │                     │
│              └──────────────────┬──────────────────┘                    │
│                                 │ ACL INFORM                             │
│                                 │ conversationId = "transaccion-bancaria"│
│                                 │ TX_ID;SENDER;RECEIVER;TYPE;AMOUNT;TS   │
└─────────────────────────────────┼────────────────────────────────────────┘
                                  │
                                  ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                 CAPA DE CONSTRUCCIÓN DEL GRAFO  (JADE)                   │
│                                                                          │
│              ┌────────────────────────────────────┐                     │
│              │          AgentConstructor           │                     │
│              │  Grafo dirigido acumulado (JGraphT) │                     │
│              │  Notifica cada EDGE_THRESHOLD=1000  │                     │
│              │  aristas nuevas                     │                     │
│              └─────────────┬──────────────┬────────┘                    │
│                            │              │                              │
│          ACL INFORM        │              │  ACL INFORM                  │
│          conversationId=   │              │  conversationId=             │
│          "grafo-listo"     │              │  "grafo-scoring"             │
└────────────────────────────┼──────────────┼──────────────────────────────┘
                             │              │
              ┌──────────────┘              └──────────────┐
              ▼                                            ▼
┌──────────────────────────┐            ┌──────────────────────────────────┐
│  ANÁLISIS DETERMINISTA   │            │    ANÁLISIS PROBABILÍSTICO       │
│                          │            │                                  │
│      AgentAnalyst        │            │          AgentScoring            │
│  Algoritmo de Tarjan     │            │  GNN (score_fanin_gnn.py)        │
│  Ciclos ≥ 3 nodos        │            │  GraphSAGE 3 capas, 64 neuronas  │
│  Importes uniformes      │            │  Umbral: score ≥ 0.99            │
│  Firma canónica (dedup)  │            │  Subproceso Python, timeout 30 s │
└──────────┬───────────────┘            └──────────────────┬───────────────┘
           │ ACL INFORM                                    │ ACL INFORM
           │ conversationId = "alerta-fraude"              │ conversationId = "alerta-fraude"
           │ SENDER→RECEIVER:AMOUNT;...                    │ fan-in;ACCOUNT_ID;SCORE
           └───────────────────────┬───────────────────────┘
                                   ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                      CAPA DE PRESENTACIÓN  (JADE)                        │
│                                                                          │
│              ┌────────────────────────────────────┐                     │
│              │             AgentUI                 │                     │
│              │  Dashboard Swing dark-mode          │                     │
│              │  Pestaña "Ciclos"  │  Pestaña "Fan-In"                   │
│              │  Tarjeta por alerta + mini-grafo    │                     │
│              └────────────────────────────────────┘                     │
└──────────────────────────────────────────────────────────────────────────┘
```

### Agentes y responsabilidades

| Agente | Tipo de servicio DF | Rol |
|---|---|---|
| `AgentPerception` | — | Percepción: lee el CSV live con ticker de 1 s y distribuye transacciones |
| `AgentConstructor` | `construccion-grafo` | Construye el grafo dirigido acumulado con JGraphT; notifica a los analizadores |
| `AgentAnalyst` | `analisis-fraude` | Detecta ciclos de blanqueo con Tarjan; filtra y deduplica por firma canónica |
| `AgentScoring` | `scoring-fraude` | Clasifica cuentas fan-in mediante GNN (subproceso Python + PyTorch Geometric) |
| `AgentUI` | `interfaz-visualizacion` | Muestra alertas de ambos detectores en tiempo real (Swing, tema dark) |

### Contratos de mensajes ACL

| Emisor | Receptor | `conversationId` | Contenido |
|---|---|---|---|
| `AgentPerception` | `AgentUI` | `estado-stream` | `INICIO` o `FIN` |
| `AgentPerception` | `AgentConstructor` | `transaccion-bancaria` | `TX_ID;SENDER;RECEIVER;TYPE;AMOUNT;TIMESTAMP` |
| `AgentConstructor` | `AgentAnalyst` | `grafo-listo` | Todas las transacciones acumuladas, una por línea |
| `AgentConstructor` | `AgentScoring` | `grafo-scoring` | Ídem |
| `AgentAnalyst` | `AgentUI` | `alerta-fraude` | `SENDER->RECEIVER:AMOUNT;...` (cadena del ciclo) |
| `AgentScoring` | `AgentUI` | `alerta-fraude` | `fan-in;ACCOUNT_ID;SCORE` |

---

## Dependencias e instalación

### Requisitos previos

| Requisito | Versión mínima | Notas |
|---|---|---|
| Java JDK | 17 | Probado con Java 26 en IntelliJ IDEA |
| Python | 3.10 | Solo para el módulo GNN (`AgentScoring`) |
| IntelliJ IDEA | cualquier versión | Recomendado; no se usa Maven ni Gradle |

### Dependencias Java — JARs locales (`lib/`)

El proyecto referencia JARs directamente desde la carpeta `lib/`. No requiere Maven ni Gradle.

| Librería | Versión | Cómo obtenerla |
|---|---|---|
| **JADE** | 4.6.0 | Descarga [jade-all-4-6-0.zip](https://jade.tilab.com/download/jade/license/jade-all-4-6-0.zip) → extrae `jade.jar` |
| **JGraphT core** | 1.5.3 | Descarga [jgrapht-1.5.3-libs.zip](https://github.com/jgrapht/jgrapht/releases/download/v1.5.3/jgrapht-1.5.3-libs.zip) → extrae `jgrapht-core-1.5.3.jar` |
| **JGraphT I/O** | 1.5.3 | Mismo ZIP de JGraphT → extrae `jgrapht-io-1.5.3.jar` |
| **JHeaps** | 0.14 | Dependencia transitiva de JGraphT; incluida en el ZIP |
| **commons-codec** | 1.3 | Incluida en el ZIP de JADE |

Copia los cinco archivos en `lib/` antes de compilar.

#### Configurar JARs en IntelliJ IDEA

1. `File → Project Structure` (`Ctrl+Alt+Shift+S`)
2. `Modules → Dependencies → +` → `JARs or Directories`
3. Selecciona todos los JARs de `lib/`. Comprueba que el scope es **Compile**.
4. `Apply → OK`

### Dependencias Python (`requirements.txt`)
Solo necesarias para el agente `AgentScoring` (inferencia GNN).

#### Instalación Entorno Python - Desde Carpeta Raíz (obligatorio)
```bash
python -m venv venv_si
venv_si\Scripts\activate
pip install "numpy<2"
pip install torch==2.2.0 --index-url https://download.pytorch.org/whl/cpu
pip install torch-geometric pandas scikit-learn
```
> ⚠️ **Una vez creado el entorno, copia la ruta del intérprete Python (`which python` en Mac/Linux o `where python` en Windows) y pégala en la línea **41** del archivo.**

---

## Instrucciones de ejecución

### 1. Compilar el proyecto

En IntelliJ: `Build → Build Project` (`Ctrl+F9`).

Asegúrate de que el directorio de salida es `out/` y de que los cinco JARs están en el classpath (`File → Project Structure → Modules → Dependencies`).

### 2. Lanzar la plataforma JADE con todos los agentes

Crea una configuración de ejecución en IntelliJ:

- **Main class:** `jade.Boot`
- **Program arguments:**

```
-gui percepcion:agents.AgentPerception;constructor:agents.AgentConstructor;analista:agents.AgentAnalyst;scoring:agents.AgentScoring;ui:agents.AgentUI
```

> **Importante:** el agente constructor debe llamarse exactamente `constructor` (minúscula), porque `AgentPerception` tiene ese nombre hardcodeado como destino ACL.

### 3. Lanzar el simulador (IntelliJ)

1. Ve a **Run > Edit Configurations…**
2. Pulsa **+** y selecciona **Application**
3. Llámala **`Simulador`**
4. En **Main class** escribe: `simulator.SimuladorTransacciones`
5. Pulsa **OK** y ejecuta la configuración

El simulador leerá `data/transactions.csv` y escribirá una transacción cada 50 ms en `data/transactions_live.csv`. La interfaz recibirá alertas en cuanto se acumulen suficientes aristas (umbral por defecto: 1 000).

### Parámetros configurables

**`src/agents/AgentConstructor.java`**

| Constante | Valor por defecto | Descripción |
|---|---|---|
| `EDGE_THRESHOLD` | `1000` | Aristas nuevas necesarias para notificar a los agentes de análisis |
| `COOLDOWN_MS` | `0` | Milisegundos mínimos entre dos notificaciones consecutivas (0 = sin cooldown) |

**`src/agents/AgentScoring.java`**

| Constante | Valor por defecto | Descripción |
|---|---|---|
| `RISK_THRESHOLD` | `0.999` | Probabilidad mínima de la GNN para generar una alerta fan-in |
| `PYTHON_CMD` | ruta local | Ruta al intérprete Python con PyTorch instalado |
| `SCORE_SCRIPT` | `data/score_fanin_gnn.py` | Script de inferencia GNN |
| `GRAPH_TMP_FILE` | `data/grafo_tmp.txt` | Fichero temporal para volcado del grafo |
| `PYTHON_TIMEOUT_SEC` | `30` | Tiempo máximo de ejecución del subproceso Python |

**`src/agents/AgentAnalyst.java`**

| Constante | Valor por defecto | Descripción |
|---|---|---|
| `MIN_CICLO_LONGITUD` | `3` | Longitud mínima de ciclo (los de 2 nodos son reversiones normales, no fraude) |
| `EPSILON_IMPORTE` | `0.01` | Tolerancia para comparar importes en coma flotante |

---

## Datos de ejemplo

### Formato de `data/transactions.csv`

El archivo incluido contiene aproximadamente 100 000 transacciones (mezcla de legítimas y fraudulentas):

```
TX_ID,SENDER_ACCOUNT_ID,RECEIVER_ACCOUNT_ID,TX_TYPE,TX_AMOUNT,TIMESTAMP,IS_FRAUD,ALERT_ID
1,6456,9069,TRANSFER,465.05,0,False,-1
2,7516,9543,TRANSFER,564.64,0,False,-1
82,6976,9739,TRANSFER,4.85,0,True,193
664370,4282,5009,TRANSFER,570258.25,101,True,1
664437,307,4732,TRANSFER,839527.25,101,True,2
664438,4732,307,TRANSFER,839527.25,101,True,2
```
 
> Las transacciones con `TX_AMOUNT <= 0` son descartadas por `CSVUtils`.

### Formato de `data/accounts.csv`

```
ACCOUNT_ID,CUSTOMER_ID,INIT_BALANCE,COUNTRY,ACCOUNT_TYPE,IS_FRAUD,TX_BEHAVIOR_ID
0,C_0,184.44,US,I,false,1
1,C_1,175.80,US,I,false,1
4282,C_4282,12500.00,US,B,true,7
5009,C_5009,3200.75,US,B,true,7
```

Usado por el script GNN (`score_fanin_gnn.py`) para obtener el saldo inicial de cada cuenta como feature del nodo.

### Ejemplo de alerta de ciclo en la UI

Cuando `AgentAnalyst` detecta un ciclo de blanqueo, `AgentUI` muestra una tarjeta similar a:

```
⚠  Ciclo #1                                    14:32:07
──────────────────────────────────────────────────────
  4282 → 5009 → 4282
  570.258,25 € en circulación

  [mini-grafo con nodos y etiquetas de importe en aristas]
```

### Ejemplo de alerta fan-in en la UI

Cuando `AgentScoring` detecta una cuenta fan-in de alto riesgo:

```
⚠  Fan-In  ALTA                                14:33:15
──────────────────────────────────────────────────────
  Cuenta: 5009
  Score GNN: 0.9987
  Severidad: ALTA  (score ≥ 0.99)
```

### Ejemplo de volcado de grafo temporal (`data/grafo_tmp.txt`)

Generado automáticamente por `AgentScoring` antes de invocar el subproceso Python:

```
664370;4282;5009;TRANSFER;570258.25;101
664437;307;4732;TRANSFER;839527.25;101
664438;4732;307;TRANSFER;839527.25;101
```

---

## Declaración de uso de IA

Este proyecto ha utilizado **Claude (Anthropic)** como asistente de programación a lo largo de todo el desarrollo. El grupo tenía experiencia previa en Python pero no en Java, por lo que la IA fue especialmente útil para trasladar la lógica que ya conocíamos a la sintaxis, patrones y APIs del ecosistema Java.

Concretamente, se usó para:

- Generar esqueletos iniciales de agentes y behaviours que luego fueron adaptados e integrados manualmente.
- Resolver dudas de sintaxis Java equivalentes a construcciones que el grupo ya dominaba en Python.

Las decisiones de arquitectura, los contratos de mensajes ACL y la lógica de negocio (detección de ciclos, scoring fan-in) fueron definidas por el equipo.
