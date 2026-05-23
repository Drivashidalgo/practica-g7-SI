# Práctica G7 – Sistemas Inteligentes: Detección de Fraude Bancario

Sistema multiagente con JADE para detección de ciclos sospechosos en transacciones bancarias.

---

## Arquitectura

```
SimuladorTransacciones
        │  escribe línea a línea (1 tx/segundo)
        ▼
data/transactions_live.csv
        │
        ▼
[AgentPerception]  ──── ACL INFORM "transaccion-bancaria" ────►  [AgentConstructor]
                                                                         │
                                                            Construye grafo dirigido (JGraphT)
                                                            Cada N aristas nuevas:
                                                                         │
                                                            ACL INFORM "grafo-listo"
                                                                         │
                                                                         ▼
                                                                [AgentAnalyst]
                                                            Algoritmo de Tarjan → ciclos
                                                                         │
                                                                         ▼
                                                                  [AgentUI] Swing
```

`AgentCoordinator` orquesta el arranque consultando el DF.

---

## Dependencias

El proyecto usa JARs locales en la carpeta `lib/`. No requiere Maven ni Gradle.

### JARs necesarios

| Librería | Versión | Descarga |
|---|---|---|
| JADE | 4.6.0 | https://jade.tilab.com/download/jade/license/jade-all-4-6-0.zip |
| JGraphT core | 1.5.2 | https://github.com/jgrapht/jgrapht/releases/download/v1.5.2/jgrapht-1.5.2-libs.zip |

Del ZIP de JADE extrae: `jade.jar` y `commons-codec-1.3.jar`.  
Del ZIP de JGraphT extrae: `jgrapht-core-1.5.2.jar`.

### Añadir JARs en IntelliJ IDEA

1. Copia los JARs en la carpeta `lib/` del proyecto.
2. `File → Project Structure` (`Ctrl+Alt+Shift+S`)
3. `Modules → Dependencies → +` → `JARs or Directories`
4. Selecciona los 3 JARs. Comprueba que el scope es **Compile**.
5. `Apply → OK`

---

## Instrucciones de ejecución

### 1. Lanzar la plataforma JADE con todos los agentes

En IntelliJ: `Run → Edit Configurations` → clase principal `jade.Boot`, argumentos:

```
-gui percepcion:agents.AgentPerception constructor:agents.AgentConstructor analista:agents.AgentAnalyst ui:agents.AgentUI
```

> **Importante:** el agente Constructor debe llamarse exactamente `constructor` (minúscula),
> porque AgentPerception tiene ese nombre hardcodeado como destino.

### 2. Lanzar el simulador (terminal separada)

```bash
java -cp lib/jade.jar:out simulator.SimuladorTransacciones
```

El simulador lee `data/transactions.csv` y escribe una línea por segundo en `data/transactions_live.csv`.

---

## Datos de ejemplo

El archivo `data/transactions.csv` ya incluido tiene el formato:

```
TX_ID,SENDER_ACCOUNT_ID,RECEIVER_ACCOUNT_ID,TX_TYPE,TX_AMOUNT,TIMESTAMP,IS_FRAUD,ALERT_ID
1,6456,9069,TRANSFER,465.05,0,False,-1
2,7516,9543,TRANSFER,564.64,0,False,-1
...
```

> Las transacciones con `TX_AMOUNT = 0.0` son descartadas automáticamente por `CSVUtils`.

---

## Parámetros configurables

En `AgentConstructor.java`:

| Constante | Valor por defecto | Descripción |
|---|---|---|
| `EDGE_THRESHOLD` | `30` | Aristas nuevas necesarias para notificar al Analista |
| `COOLDOWN_MS` | `10000` | Milisegundos mínimos entre dos notificaciones consecutivas |

---

## Declaración de uso de IA

Este proyecto ha utilizado Claude (Anthropic) como asistente de programación para:
- Generar el esqueleto de `AgentConstructor` y `ConstruirGrafoBehaviour`.
- Revisar la compatibilidad con `AgentPerception` y `CSVUtils` ya implementados.
- Elaborar parte de este README.

Todo el código ha sido revisado, adaptado e integrado manualmente por los miembros del grupo.