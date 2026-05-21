"""
score_fanin.py
--------------
Recibe el grafo acumulado como líneas de transacciones,
extrae features de fan-in por nodo receiver y devuelve los nodos de alto riesgo.

Uso:
    python score_fanin.py "ruta/al/grafo.txt"

Salida (stdout), una línea por nodo de alto riesgo:
    RECEIVER;score
"""

import sys
import warnings
import joblib
import numpy as np
import networkx as nx
import pandas as pd

warnings.filterwarnings("ignore")

THRESHOLD = 0.2

def build_graph(lines):
    G = nx.DiGraph()
    for line in lines:
        line = line.strip()
        if not line:
            continue
        parts = line.split(";")
        if len(parts) < 6:
            continue
        sender   = parts[1].strip()
        receiver = parts[2].strip()
        try:
            amount = float(parts[4])
            ts     = float(parts[5]) if parts[5] not in ("", "0") else 0.0
        except ValueError:
            continue

        if G.has_edge(sender, receiver):
            G[sender][receiver]["count"]      += 1
            G[sender][receiver]["total"]      += amount
            G[sender][receiver]["timestamps"].append(ts)
        else:
            G.add_edge(sender, receiver,
                       count=1,
                       total=amount,
                       timestamps=[ts])
    return G

def extract_features(G, node, feature_names):
    predecessors = list(G.predecessors(node))
    in_degree    = len(predecessors)
    if in_degree == 0:
        return None

    amounts  = [G[p][node]["total"] for p in predecessors]
    counts   = [G[p][node]["count"] for p in predecessors]
    all_ts   = [t for p in predecessors for t in G[p][node]["timestamps"]]

    feat = {
        "in_degree":                 in_degree,
        "unique_senders":            in_degree,
        "total_received":            sum(amounts),
        "avg_amount_per_sender":     np.mean(amounts),
        "std_amount_per_sender":     np.std(amounts) if len(amounts) > 1 else 0,
        "max_amount_per_sender":     max(amounts),
        "total_tx_count":            sum(counts),
        "avg_tx_per_sender":         np.mean(counts),
        "timestamp_spread":          max(all_ts) - min(all_ts) if len(all_ts) > 1 else 0,
        "out_degree":                G.out_degree(node),
        "out_in_ratio":              G.out_degree(node) / max(in_degree, 1),
    }

    return pd.DataFrame([feat], columns=feature_names)

def main():
    if len(sys.argv) < 2:
        print("ERROR;sin_argumentos")
        sys.exit(1)

    filepath = sys.argv[1]
    try:
        with open(filepath, "r") as f:
            lines = f.readlines()
    except FileNotFoundError:
        print(f"ERROR;archivo_no_encontrado:{filepath}")
        sys.exit(1)

    model         = joblib.load("data/fanin_model.pkl")
    feature_names = joblib.load("data/fanin_features.pkl")

    G = build_graph(lines)

    results = []
    for node in G.nodes():
        X = extract_features(G, node, feature_names)
        if X is None:
            continue
        prob = model.predict_proba(X)[0][1]
        if prob >= THRESHOLD:
            results.append(f"{node};{prob:.4f}")

    if results:
        print("\n".join(results))
    else:
        print("NONE")

if __name__ == "__main__":
    main()