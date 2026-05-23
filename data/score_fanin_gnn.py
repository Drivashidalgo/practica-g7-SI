"""
score_fanin_gnn.py
------------------
Script de inferencia del modelo GNN, invocado por CalcularScoringBehaviour
desde JADE (AgentScoring).

Uso:
    python score_fanin_gnn.py <graph_file> [threshold]

Args:
    graph_file  Ruta al volcado del grafo (formato:
                TX_ID;SENDER;RECEIVER;TX_TYPE;AMOUNT;TIMESTAMP por línea)
    threshold   Probabilidad mínima para alertar (default 0.5)

Salida (stdout):
    Una línea por cuenta alertada con formato:  account_id;score
    Si no hay alertas:                          NONE
    En caso de error:                           ERROR: <descripción>

Archivos requeridos en cwd:
    data/accounts.csv     Mismo CSV usado en entrenamiento (para balances)
    model_fanin.pt        Pesos del modelo entrenado
"""

import sys
import os
import pandas as pd
import torch
import torch.nn.functional as F
from torch_geometric.data import Data
from torch_geometric.nn import SAGEConv
from sklearn.preprocessing import StandardScaler


# ── 0. ARGS ───────────────────────────────────────────────────
if len(sys.argv) < 2:
    print("ERROR: usage: python score_fanin_gnn.py <graph_file> [threshold]")
    sys.exit(1)

GRAPH_FILE = sys.argv[1]
THRESHOLD  = float(sys.argv[2]) if len(sys.argv) > 2 else 0.5

ACCOUNTS_FILE = "data/accounts.csv"
MODEL_FILE    = "data/model_fanin.pt"


# ── 1. MODELO (debe coincidir con gnn_fanin.py) ───────────────
class FanInGNN(torch.nn.Module):
    def __init__(self, in_channels, hidden=64, out_channels=2):
        super().__init__()
        self.conv1   = SAGEConv(in_channels, hidden)
        self.conv2   = SAGEConv(hidden, hidden)
        self.conv3   = SAGEConv(hidden, out_channels)
        self.dropout = torch.nn.Dropout(0.3)

    def forward(self, x, edge_index):
        x = F.relu(self.conv1(x, edge_index))
        x = self.dropout(x)
        x = F.relu(self.conv2(x, edge_index))
        x = self.dropout(x)
        x = self.conv3(x, edge_index)
        return x


try:
    # ── 2. CARGAR CUENTAS ─────────────────────────────────────
    if not os.path.exists(ACCOUNTS_FILE):
        print(f"ERROR: no se encuentra {ACCOUNTS_FILE}")
        sys.exit(1)

    accounts = pd.read_csv(ACCOUNTS_FILE)
    accounts['ACCOUNT_ID'] = accounts['ACCOUNT_ID'].astype(str)

    account_ids = accounts['ACCOUNT_ID'].tolist()
    id_to_idx   = {aid: i for i, aid in enumerate(account_ids)}
    n_nodes     = len(account_ids)

    # ── 3. PARSEAR GRAFO RECIBIDO DE JADE ─────────────────────
    if not os.path.exists(GRAPH_FILE):
        print(f"ERROR: no se encuentra {GRAPH_FILE}")
        sys.exit(1)

    src, dst, amounts = [], [], []
    skipped_unknown   = 0

    with open(GRAPH_FILE, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue

            # Formato: TX_ID;SENDER;RECEIVER;TX_TYPE;AMOUNT;TIMESTAMP
            parts = line.split(';')
            if len(parts) < 5:
                continue

            sender   = parts[1].strip()
            receiver = parts[2].strip()
            try:
                amount = float(parts[4].strip())
            except ValueError:
                continue

            # Solo cuentas conocidas
            if sender not in id_to_idx or receiver not in id_to_idx:
                skipped_unknown += 1
                continue

            src.append(id_to_idx[sender])
            dst.append(id_to_idx[receiver])
            amounts.append(amount)

    if not src:
        print("NONE")
        sys.exit(0)

    # ── 4. CONSTRUIR TENSORES ─────────────────────────────────
    edge_index = torch.tensor([src, dst], dtype=torch.long)

    # Edge features: importes estandarizados.
    # Reajustamos el scaler sobre los importes recibidos — los del entrenamiento
    # no se guardaron. Para StandardScaler sobre una variable continua bien
    # distribuida el impacto es marginal.
    scaler_edge    = StandardScaler()
    amounts_scaled = scaler_edge.fit_transform(
        torch.tensor(amounts).reshape(-1, 1).numpy()
    )
    edge_attr = torch.tensor(amounts_scaled, dtype=torch.float)

    # Node features: in/out degree calculados del grafo recibido + balance.
    in_degree  = torch.zeros(n_nodes)
    out_degree = torch.zeros(n_nodes)
    for s, d in zip(src, dst):
        out_degree[s] += 1
        in_degree[d]  += 1

    balances        = accounts['INIT_BALANCE'].fillna(0).values.reshape(-1, 1)
    scaler_node     = StandardScaler()
    balances_scaled = scaler_node.fit_transform(balances)

    x = torch.cat([
        torch.tensor(balances_scaled, dtype=torch.float),
        in_degree.unsqueeze(1),
        out_degree.unsqueeze(1),
    ], dim=1)

    data = Data(x=x, edge_index=edge_index, edge_attr=edge_attr)

    # ── 5. CARGAR MODELO E INFERIR ────────────────────────────
    if not os.path.exists(MODEL_FILE):
        print(f"ERROR: no se encuentra {MODEL_FILE}")
        sys.exit(1)

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    model  = FanInGNN(in_channels=x.shape[1]).to(device)
    model.load_state_dict(torch.load(MODEL_FILE, map_location=device))
    model.eval()
    data = data.to(device)

    with torch.no_grad():
        out   = model(data.x, data.edge_index)
        probs = F.softmax(out, dim=1)[:, 1]   # prob de la clase Fan-in

    # ── 6. APLICAR THRESHOLD Y FILTRAR A RECEIVERS ACTIVOS ────
    # Solo nos interesan cuentas que aparecen como receiver en el grafo
    # actual (las demás no han recibido nada, no pueden ser fan-in).
    receiver_indices = set(dst)
    probs_np = probs.cpu().numpy()

    alerts = []
    for idx in receiver_indices:
        score = float(probs_np[idx])
        if score >= THRESHOLD:
            alerts.append((account_ids[idx], score))

    # Ordenar por score descendente — las más sospechosas primero
    alerts.sort(key=lambda r: r[1], reverse=True)

    # ── 7. SALIDA POR STDOUT ──────────────────────────────────
    if not alerts:
        print("NONE")
    else:
        for account_id, score in alerts:
            print(f"{account_id};{score:.4f}")

except Exception as e:
    print(f"ERROR: {type(e).__name__}: {e}")
    sys.exit(1)
