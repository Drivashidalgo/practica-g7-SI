"""
evaluate_streaming.py
---------------------
Evalúa el modelo GNN entrenado (model_fanin.pt) sobre un dataset no visto
durante el entrenamiento (streaming/).

Cumple dos objetivos:

  A. Métricas honestas del modelo sobre datos nuevos
     → classification_report, matriz de confusión, precisión operacional.

  B. Calibración del threshold de producción
     → tabla precision/recall/F1 a distintos cortes de probabilidad,
       más threshold óptimo según F1.

El threshold elegido alimenta AgentScoring.RISK_THRESHOLD en el sistema JADE.

Estructura esperada:
    streaming/transactions.csv   (mismo esquema que train/transactions.csv)
    streaming/accounts.csv       (mismo esquema que train/accounts.csv)
    model_fanin.pt               (pesos del modelo entrenado)
"""

import pandas as pd
import torch
import torch.nn.functional as F
from torch_geometric.data import Data
from torch_geometric.nn import SAGEConv
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import classification_report, confusion_matrix


# ── 1. CARGAR DATOS STREAMING ─────────────────────────────────
print("Cargando datos de streaming...")
tx       = pd.read_csv('streaming/transactions.csv')
accounts = pd.read_csv('streaming/accounts.csv')

# IS_FRAUD ya viene como booleano nativo
tx['IS_FRAUD'] = tx['IS_FRAUD'].astype(bool)

# TIMESTAMP: detección automática del formato.
# Si es ISO ('2022-01-01T00:00:00Z') → convertir a Unix segundos.
# Si ya es entero → dejar como está.
if tx['TIMESTAMP'].dtype == object:
    tx['TIMESTAMP'] = pd.to_datetime(tx['TIMESTAMP'], utc=True).astype('int64') // 10**9

tx['SENDER_ACCOUNT_ID']   = tx['SENDER_ACCOUNT_ID'].astype(str)
tx['RECEIVER_ACCOUNT_ID'] = tx['RECEIVER_ACCOUNT_ID'].astype(str)
accounts['ACCOUNT_ID']    = accounts['ACCOUNT_ID'].astype(str)

print(f"Transacciones: {len(tx):,}  Cuentas: {len(accounts):,}")

# ── 2. MAPEAR IDs A ÍNDICES ───────────────────────────────────
account_ids = accounts['ACCOUNT_ID'].tolist()
id_to_idx   = {aid: i for i, aid in enumerate(account_ids)}
n_nodes     = len(account_ids)

# Filtrar transacciones a cuentas conocidas
tx = tx[tx['SENDER_ACCOUNT_ID'].isin(id_to_idx) & tx['RECEIVER_ACCOUNT_ID'].isin(id_to_idx)]

# ── 3. ARISTAS ────────────────────────────────────────────────
src = [id_to_idx[s] for s in tx['SENDER_ACCOUNT_ID']]
dst = [id_to_idx[d] for d in tx['RECEIVER_ACCOUNT_ID']]
edge_index = torch.tensor([src, dst], dtype=torch.long)

scaler_edge    = StandardScaler()
amounts_scaled = scaler_edge.fit_transform(tx['TX_AMOUNT'].values.reshape(-1, 1))
edge_attr      = torch.tensor(amounts_scaled, dtype=torch.float)

# ── 4. LABELS DE NODO ─────────────────────────────────────────
sar_receivers = set(tx[tx['IS_FRAUD'] == True]['RECEIVER_ACCOUNT_ID'])
y = torch.tensor(
    [1 if aid in sar_receivers else 0 for aid in account_ids],
    dtype=torch.long
)
n_pos = y.sum().item()
n_neg = n_nodes - n_pos
print(f"\nNodos fraude: {n_pos:,} ({n_pos/n_nodes*100:.2f}%)")
print(f"Nodos normales: {n_neg:,} ({n_neg/n_nodes*100:.2f}%)")

# ── 5. FEATURES DE NODO (mismas 3 que en entrenamiento) ───────
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

data = Data(x=x, edge_index=edge_index, edge_attr=edge_attr, y=y)

# ── 6. CARGAR MODELO ──────────────────────────────────────────
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

device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
model  = FanInGNN(in_channels=x.shape[1]).to(device)
model.load_state_dict(torch.load('model_fanin.pt', map_location=device))
model.eval()
data = data.to(device)

# ── 7. INFERENCIA — PROBABILIDADES, NO CLASES ─────────────────
print("\nEjecutando inferencia sobre streaming...")
with torch.no_grad():
    out   = model(data.x, data.edge_index)
    probs = F.softmax(out, dim=1)[:, 1]   # probabilidad de la clase Fan-in

y_true     = data.y.cpu().numpy()
probs_np   = probs.cpu().numpy()

# ════════════════════════════════════════════════════════════════
# PAPEL A — Métricas honestas a threshold por defecto (0.5)
# ════════════════════════════════════════════════════════════════
print("\n" + "=" * 60)
print("PAPEL A — Métricas a threshold = 0.5 (referencia)")
print("=" * 60)

y_pred_default = (probs_np >= 0.5).astype(int)

print(classification_report(
    y_true, y_pred_default,
    target_names=['Normal', 'Fan-in'],
    zero_division=0,
    digits=4
))

cm = confusion_matrix(y_true, y_pred_default)
print("Matriz de confusión:")
print(f"  TN={cm[0,0]:>7,}  FP={cm[0,1]:>7,}")
print(f"  FN={cm[1,0]:>7,}  TP={cm[1,1]:>7,}")

n_alerts = int(y_pred_default.sum())
print(f"\nAlertas generadas: {n_alerts:,}")
print(f"Fraude real:       {n_pos:,}")
if n_alerts > 0:
    op_prec = cm[1,1] / n_alerts * 100
    print(f"Precisión operacional: {op_prec:.1f}% de las alertas son fraude real")

# ════════════════════════════════════════════════════════════════
# PAPEL B — Tabla de thresholds para calibrar
# ════════════════════════════════════════════════════════════════
print("\n" + "=" * 60)
print("PAPEL B — Calibración de threshold")
print("=" * 60)

thresholds_to_test = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.75,
                      0.8, 0.85, 0.9, 0.95, 0.99]

print(f"\n{'Thresh':>7} | {'Prec':>7} | {'Recall':>7} | "
      f"{'F1':>7} | {'Alerts':>7} | {'TP':>6} | {'FP':>6} | {'FN':>6}")
print("-" * 75)

best_f1, best_thresh = 0.0, 0.5
results = []

for t in thresholds_to_test:
    pred_t = (probs_np >= t).astype(int)
    tp = int(((pred_t == 1) & (y_true == 1)).sum())
    fp = int(((pred_t == 1) & (y_true == 0)).sum())
    fn = int(((pred_t == 0) & (y_true == 1)).sum())

    prec = tp / max(tp + fp, 1)
    rec  = tp / max(tp + fn, 1)
    f1   = 2 * prec * rec / max(prec + rec, 1e-9)
    n_al = tp + fp

    results.append((t, prec, rec, f1, n_al))

    if f1 > best_f1:
        best_f1, best_thresh = f1, t

    print(f"{t:>7.2f} | {prec:>7.4f} | {rec:>7.4f} | "
          f"{f1:>7.4f} | {n_al:>7,} | {tp:>6,} | {fp:>6,} | {fn:>6,}")

# ── Threshold recomendado por F1 ──────────────────────────────
print("\n" + "=" * 60)
print(f"RECOMENDACIÓN — threshold óptimo por F1: {best_thresh:.2f}")
print("=" * 60)

pred_best = (probs_np >= best_thresh).astype(int)
print(classification_report(
    y_true, pred_best,
    target_names=['Normal', 'Fan-in'],
    zero_division=0,
    digits=4
))

cm_best = confusion_matrix(y_true, pred_best)
print(f"  TN={cm_best[0,0]:>7,}  FP={cm_best[0,1]:>7,}")
print(f"  FN={cm_best[1,0]:>7,}  TP={cm_best[1,1]:>7,}")

print(f"\n→ Usa este valor en AgentScoring.java:")
print(f"    public static final double RISK_THRESHOLD = {best_thresh};")

# ── Sugerencias alternativas según prioridad ──────────────────
print("\nAlternativas según prioridad:")

# Threshold con máxima precision (≥0.9) y recall razonable (≥0.5)
candidates_prec = [r for r in results if r[1] >= 0.9 and r[2] >= 0.5]
if candidates_prec:
    best_p = max(candidates_prec, key=lambda r: r[1])
    print(f"  Si priorizas precisión (menos falsos positivos):")
    print(f"    threshold={best_p[0]:.2f}  prec={best_p[1]:.3f}  recall={best_p[2]:.3f}")

# Threshold con máxima recall (≥0.95) y precision razonable (≥0.3)
candidates_rec = [r for r in results if r[2] >= 0.95 and r[1] >= 0.3]
if candidates_rec:
    best_r = min(candidates_rec, key=lambda r: r[0])  # el threshold más bajo que cumple
    print(f"  Si priorizas recall (no escapar ningún fraude):")
    print(f"    threshold={best_r[0]:.2f}  prec={best_r[1]:.3f}  recall={best_r[2]:.3f}")