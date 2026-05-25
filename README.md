# Práctica G7 – Sistemas Inteligentes: Detección de Fraude Bancario

Sistema multiagente implementado con JADE para la detección de patrones fraudulentos en flujos de transacciones bancarias en tiempo real. El sistema identifica dos tipos de fraude: **ciclos de blanqueo de capitales** (mediante el algoritmo de Tarjan sobre un grafo dirigido) y **cuentas fan-in de alto riesgo** (mediante una red neuronal de grafos).

---

## Índice

1. [Arquitectura del sistema](#arquitectura-del-sistema)
2. [Dependencias e instalación](#dependencias-e-instalación)
3. [Instrucciones de ejecución](#instrucciones-de-ejecución)
4. [Datos de ejemplo](#datos-de-ejemplo)
5. [Declaración de uso de IA](#declaración-de-uso-de-ia)

---

## Arquitectura del sistema

```
                    ┌─────────────────────────┐
                    │  SimuladorTransacciones  │
                    │  (proceso independiente) │
                    │  Lee transactions.csv    │
                    │  1 línea cada 10 ms      │
                    └───────────┬─────────────┘
                                │ escribe línea a línea
                                ▼
                   data/transactions_live.csv
                                │
                                ▼
               ┌────────────────────────────┐
               │       AgentPerception      │
               │  Ticker cada 1 s           │
               │  Lee líneas nuevas del CSV │
               └──────────────┬─────────────┘
                              │ ACL INFORM
                              │ conversationId="transaccion-bancaria"
                              │ TX_ID;SENDER;RECEIVER;TYPE;AMOUNT;TIMESTAMP
                              ▼
               ┌────────────────────────────┐
               │      AgentConstructor      │
               │  Construye grafo dirigido  │
               │  con JGraphT               │
               │  Cada EDGE_THRESHOLD=10    │
               │  aristas nuevas notifica   │
               └──────────┬─────────────────┘
              ┌───────────┴──────────────┐
              │ ACL INFORM               │ ACL INFORM
              │ conversationId=          │ conversationId=
              │ "grafo-listo"            │ "grafo-scoring"
              ▼                          ▼
┌─────────────────────┐    ┌──────────────────────────┐
│    AgentAnalyst     │    │      AgentScoring         │
│  Algoritmo Tarjan   │    │  Red neuronal de grafos   │
│  Detecta ciclos de  │    │  (GNN, score_fanin_gnn.py)│
│  blanqueo           │    │  Detecta cuentas fan-in   │
└──────────┬──────────┘    └────────────┬──────────────┘
           │ ACL INFORM                 │ ACL INFORM
           │ conversationId=            │ conversationId=
           │ "alerta-fraude"            │ "alerta-fraude"
           └───────────────┬────────────┘
                           ▼
            ┌──────────────────────────┐
            │         AgentUI          │
            │  Monitor Swing dark-mode │
            │  Muestra ciclos y        │
            │  cuentas sospechosas     │
            └──────────────────────────┘
```

### Agentes y responsabilidades

| Agente | Tipo de servicio DF | Rol |
|---|---|---|
| `AgentPerception` | — | Percepción: lee el CSV en tiempo real y distribuye transacciones |
| `AgentConstructor` | `construccion-grafo` | Construcción del grafo dirigido acumulado con JGraphT |
| `AgentAnalyst` | `analisis-fraude` | Análisis: detecta ciclos con Tarjan y alerta a la UI |
| `AgentScoring` | `scoring-fraude` | Scoring: clasifica cuentas fan-in con una GNN en Python |
| `AgentUI` | `interfaz-visualizacion` | Interfaz: muestra alertas en tiempo real con Swing |

### Contratos de mensajes ACL

| Emisor | Receptor | `conversationId` | Contenido |
|---|---|---|---|
| AgentPerception | AgentConstructor | `transaccion-bancaria` | `TX_ID;SENDER;RECEIVER;TYPE;AMOUNT;TIMESTAMP` |
| AgentConstructor | AgentAnalyst | `grafo-listo` | Todas las transacciones acumuladas, una por línea |
| AgentConstructor | AgentScoring | `grafo-scoring` | Ídem |
| AgentAnalyst | AgentUI | `alerta-fraude` | `SENDER->RECEIVER:AMOUNT;...` (cadena del ciclo) |
| AgentScoring | AgentUI | `alerta-fraude` | `fan-in;ACCOUNT_ID;SCORE` |

---

## Dependencias e instalación

### Requisitos previos

- **Java 17+** (probado con Java 26 en IntelliJ IDEA)
- **Python 3.8+** con PyTorch y PyTorch Geometric (solo para AgentScoring / GNN)
- **IntelliJ IDEA** (recomendado) o cualquier IDE compatible con proyectos Java sin Maven

### JARs necesarios

El proyecto usa JARs locales en la carpeta `lib/`. No requiere Maven ni Gradle.

| Librería | Versión | Descarga |
|---|---|---|
| JADE | 4.6.0 | https://jade.tilab.com/download/jade/license/jade-all-4-6-0.zip |
| JGraphT core | 1.5.2 | https://github.com/jgrapht/jgrapht/releases/download/v1.5.2/jgrapht-1.5.2-libs.zip |
| commons-codec | 1.3 | Incluido en el ZIP de JADE |

Del ZIP de JADE extrae: `jade.jar` y `commons-codec-1.3.jar`.  
Del ZIP de JGraphT extrae: `jgrapht-core-1.5.2.jar`.

Copia los tres archivos en la carpeta `lib/` del proyecto.

### Configurar dependencias en IntelliJ IDEA

1. `File → Project Structure` (`Ctrl+Alt+Shift+S`)
2. `Modules → Dependencies → +` → `JARs or Directories`
3. Selecciona los 3 JARs de `lib/`. Comprueba que el scope es **Compile**.
4. `Apply → OK`

### Dependencias Python (solo AgentScoring)

```bash
pip install torch torch-geometric numpy pandas
```

> Si usas Anaconda, activa el entorno adecuado antes de lanzar JADE. La ruta al intérprete se configura en `AgentScoring.java` con la constante `PYTHON_CMD`.

---

## Instrucciones de ejecución

### 1. Compilar el proyecto

En IntelliJ: `Build → Build Project` (`Ctrl+F9`).

Asegúrate de que el directorio de salida es `out/` y que los tres JARs están en el classpath.

### 2. Lanzar la plataforma JADE con todos los agentes

`Run → Edit Configurations` → clase principal `jade.Boot`, argumentos de programa:

```
-gui percepcion:agents.AgentPerception constructor:agents.AgentConstructor analista:agents.AgentAnalyst scoring:agents.AgentScoring ui:agents.AgentUI
```

> Si no necesitas el módulo de scoring (GNN), puedes omitir `scoring:agents.AgentScoring`.

> **Importante:** el agente Constructor debe llamarse exactamente `constructor` (minúscula), porque `AgentPerception` tiene ese nombre hardcodeado como destino ACL.

### 3. Lanzar el simulador (terminal separada)

```bash
# Windows
java -cp "lib/jade.jar;out" simulator.SimuladorTransacciones

# Linux / Mac
java -cp "lib/jade.jar:out" simulator.SimuladorTransacciones
```

El simulador leerá `data/transactions.csv` y escribirá una transacción cada 10 ms en `data/transactions_live.csv`. La UI comenzará a recibir alertas en cuanto se acumulen suficientes aristas.

### Parámetros configurables

En `AgentConstructor.java`:

| Constante | Valor por defecto | Descripción |
|---|---|---|
| `EDGE_THRESHOLD` | `10` | Aristas nuevas necesarias para disparar una notificación a los agentes de análisis |
| `COOLDOWN_MS` | `0` | Milisegundos mínimos entre dos notificaciones consecutivas (0 = sin cooldown) |

En `AgentScoring.java`:

| Constante | Valor por defecto | Descripción |
|---|---|---|
| `RISK_THRESHOLD` | `0.5` | Probabilidad mínima de la GNN para generar una alerta fan-in |
| `PYTHON_CMD` | ruta local | Ruta al intérprete Python con PyTorch instalado |
| `SCORE_SCRIPT` | `data/score_fanin_gnn.py` | Script de inferencia GNN |
| `PYTHON_TIMEOUT_SEC` | `30` | Tiempo máximo de ejecución del script Python |

---

## Datos de ejemplo

El archivo `data/transactions.csv` incluido tiene el siguiente formato:

```
TX_ID,SENDER_ACCOUNT_ID,RECEIVER_ACCOUNT_ID,TX_TYPE,TX_AMOUNT,TIMESTAMP,IS_FRAUD,ALERT_ID
1,6456,9069,TRANSFER,465.05,0,False,-1
2,7516,9543,TRANSFER,564.64,0,False,-1
664370,4282,5009,TRANSFER,570258.25,101,True,1
664437,307,4732,TRANSFER,839527.25,101,True,2
```

> El simulador omite automáticamente las transacciones con `TIMESTAMP <= 100` para evitar reemitir datos de histórico antiguo.  
> Las transacciones con `TX_AMOUNT <= 0` son descartadas por `CSVUtils`.

### Cómo se ve una alerta en la UI

Cuando el AgentAnalyst detecta un ciclo, el AgentUI muestra una tarjeta como:

```
⚠  Ciclo #1                        14:32:07
4282 → 5009 → 4282
570.258,25 € en circulación
```

Con un mini-grafo dirigido a la derecha que muestra los nodos y los importes en cada arista.

---

## Declaración de uso de IA

Este proyecto ha utilizado **Claude (Anthropic)** como asistente de programación a lo largo de todo el desarrollo. El grupo tenía experiencia previa en Python pero no en Java, por lo que la IA fue especialmente útil para trasladar la lógica que ya conocíamos a la sintaxis, patrones y APIs del ecosistema Java: estructuras de clases, herencia, interfaces de JADE (`Agent`, `Behaviour`, `ACLMessage`), el API de JGraphT y la construcción de interfaces Swing.

Concretamente, se usó para:

- Entender y aplicar los patrones de JADE (`CyclicBehaviour`, `TickerBehaviour`, registro en el DF, filtros `MessageTemplate`) sin experiencia previa en el framework.
- Generar esqueletos iniciales de agentes y behaviours que luego fueron adaptados e integrados manualmente.
- Resolver dudas de sintaxis Java equivalentes a construcciones que el grupo ya dominaba en Python.
- Redactar este README a partir del código fuente del proyecto.

Todo el código ha sido revisado, depurado e integrado manualmente por los miembros del grupo. Las decisiones de arquitectura, los contratos de mensajes ACL y la lógica de negocio (detección de ciclos, scoring fan-in) fueron definidas por el equipo.
