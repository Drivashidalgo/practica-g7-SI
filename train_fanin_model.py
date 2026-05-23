"""
train_fanin_model.py
--------------------
Entrena un modelo de detección de fraude tipo fan-in usando features
estructurales del grafo de transacciones.

Uso:
    python train_fanin_model.py

Requisitos:
    pip install pandas scikit-learn imbalanced-learn joblib networkx
"""

import pandas as pd
import numpy as np
import networkx as nx
import joblib
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, confusion_matrix
from imblearn.over_sampling import SMOTE

# ---------------------------------------------------------------
# 1. Cargar datos históricos (timestamp <= 100)
# ---------------------------------------------------------------
print("Cargando datos...")
tx = pd.read_csv("data/transactions.csv")
alerts = pd.read_csv("data/alerts.csv")

# Solo histórico para entrenar
tx_hist = tx[tx["TIMESTAMP"] <= 100].copy()
alerts_fanin = alerts[alerts["ALERT_TYPE"] == "fan_in"].copy()

print(f"Transacciones históricas: {len(tx_hist)}")
print(f"Alertas fan-in totales: {len(alerts_fanin)}")

# ---------------------------------------------------------------
# 2. Construir grafo histórico con NetworkX
# ---------------------------------------------------------------
print("\nConstruyendo grafo...")
G = nx.DiGraph()

for _, row in tx_hist.iterrows():
    sender   = str(int(row["SENDER_ACCOUNT_ID"]))
    receiver = str(int(row["RECEIVER_ACCOUNT_ID"]))
    amount   = row["TX_AMOUNT"]
    ts       = row["TIMESTAMP"]

    if G.has_edge(sender, receiver):
        G[sender][receiver]["count"]      += 1
        G[sender][receiver]["total"]      += amount
        G[sender][receiver]["timestamps"].append(ts)
    else:
        G.add_edge(sender, receiver,
                   count=1,
                   total=amount,
                   timestamps=[ts])

print(f"Nodos: {G.number_of_nodes()}  Aristas: {G.number_of_edges()}")

# ---------------------------------------------------------------
# 3. Extraer features por nodo RECEIVER (fan-in = muchos → uno)
# ---------------------------------------------------------------
print("\nExtrayendo features de grafo por nodo receiver...")

def extract_receiver_features(graph, node):
    predecessors = list(graph.predecessors(node))
    in_degree    = len(predecessors)

    if in_degree == 0:
        return None

    amounts  = [graph[p][node]["total"] for p in predecessors]
    counts   = [graph[p][node]["count"] for p in predecessors]
    all_ts   = [t for p in predecessors for t in graph[p][node]["timestamps"]]

    return {
        "receiver":                  node,
        "in_degree":                 in_degree,
        "unique_senders":            in_degree,
        "total_received":            sum(amounts),
        "avg_amount_per_sender":     np.mean(amounts),
        "std_amount_per_sender":     np.std(amounts) if len(amounts) > 1 else 0,
        "max_amount_per_sender":     max(amounts),
        "total_tx_count":            sum(counts),
        "avg_tx_per_sender":         np.mean(counts),
        "timestamp_spread":          max(all_ts) - min(all_ts) if len(all_ts) > 1 else 0,
        "out_degree":                graph.out_degree(node),
        "out_in_ratio":              graph.out_degree(node) / max(in_degree, 1),
    }

rows = []
for node in G.nodes():
    feat = extract_receiver_features(G, node)
    if feat:
        rows.append(feat)

df_features = pd.DataFrame(rows)
print(f"Nodos receiver con features: {len(df_features)}")

# ---------------------------------------------------------------
# 4. Etiquetas: ¿el receiver tiene alertas fan-in?
# ---------------------------------------------------------------
fanin_receivers = set(
    alerts_fanin["RECEIVER_ACCOUNT_ID"].astype(str).str.strip()
)

df_features["is_fanin_fraud"] = df_features["receiver"].isin(fanin_receivers).astype(int)

print(f"\nDistribución etiquetas:")
print(df_features["is_fanin_fraud"].value_counts())

# ---------------------------------------------------------------
# 5. Features y etiqueta
# ---------------------------------------------------------------
FEATURES = [
    "in_degree",
    "unique_senders",
    "total_received",
    "avg_amount_per_sender",
    "std_amount_per_sender",
    "max_amount_per_sender",
    "total_tx_count",
    "avg_tx_per_sender",
    "timestamp_spread",
    "out_degree",
    "out_in_ratio",
]

X = df_features[FEATURES]
y = df_features["is_fanin_fraud"]

# ---------------------------------------------------------------
# 6. Balancear con SMOTE si hay suficientes positivos
# ---------------------------------------------------------------
if y.sum() >= 5:
    smote = SMOTE(random_state=42, k_neighbors=min(5, y.sum() - 1))
    X_bal, y_bal = smote.fit_resample(X, y)
    print(f"\nTras SMOTE: {pd.Series(y_bal).value_counts().to_dict()}")
else:
    X_bal, y_bal = X, y
    print("\nPocos positivos, entrenando sin SMOTE.")

# ---------------------------------------------------------------
# 7. Train/test split y entrenamiento
# ---------------------------------------------------------------
X_train, X_test, y_train, y_test = train_test_split(
    X_bal, y_bal, test_size=0.2, random_state=42, stratify=y_bal
)

print("\nEntrenando Random Forest para fan-in...")
model = RandomForestClassifier(
    n_estimators=100,
    max_depth=10,
    class_weight="balanced",
    random_state=42,
    n_jobs=-1
)
model.fit(X_train, y_train)

# ---------------------------------------------------------------
# 8. Evaluación
# ---------------------------------------------------------------
y_pred = model.predict(X_test)
print("\n=== RESULTADOS ===")
print(classification_report(y_test, y_pred, target_names=["Normal", "Fan-in fraude"]))
print("Matriz de confusión:")
print(confusion_matrix(y_test, y_pred))

# ---------------------------------------------------------------
# 9. Guardar modelo y features
# ---------------------------------------------------------------
joblib.dump(model,    "data/fanin_model.pkl")
joblib.dump(FEATURES, "data/fanin_features.pkl")

print("\nModelo guardado en: data/fanin_model.pkl")
print("Features guardadas en: data/fanin_features.pkl")