import pandas as pd
import torch
import torch.nn.functional as F
from torch_geometric.data import Data
from torch_geometric.nn import SAGEConv
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import classification_report
import numpy as np

# ── 1. CARGAR DATOS ───────────────────────────────────────────
print("Cargando datos...")
tx       = pd.read_csv('train/transactions.csv')
accounts = pd.read_csv('train/accounts.csv')

# ── 2. SUBMUESTREO: conservar todo el fraude + muestra de normales ──
TARGET_TOTAL = 100_000

tx['IS_FRAUD'] = tx['IS_FRAUD'].astype(str).str.upper() == 'TRUE'
tx_fraud       = tx[tx['IS_FRAUD'] == True]
tx_normal      = tx[tx['IS_FRAUD'] == False]

n_fraud_tx     = len(tx_fraud)
n_normal_sample = min(TARGET_TOTAL - n_fraud_tx, len(tx_normal))
tx_normal_sample = tx_normal.sample(n=n_normal_sample, random_state=42)
tx = pd.concat([tx_fraud, tx_normal_sample]).reset_index(drop=True)

print(f"Transacciones fraude (conservadas): {n_fraud_tx:,}")
print(f"Transacciones normales (muestreadas): {n_normal_sample:,}")
print(f"Total: {len(tx):,}")

# ── 3. MAPEAR IDs A ÍNDICES ───────────────────────────────────
tx['SENDER_ACCOUNT_ID']   = tx['SENDER_ACCOUNT_ID'].astype(str)
tx['RECEIVER_ACCOUNT_ID'] = tx['RECEIVER_ACCOUNT_ID'].astype(str)
accounts['ACCOUNT_ID']       = accounts['ACCOUNT_ID'].astype(str)

account_ids = accounts['ACCOUNT_ID'].tolist()
id_to_idx   = {aid: i for i, aid in enumerate(account_ids)}
n_nodes     = len(account_ids)

tx = tx[tx['SENDER_ACCOUNT_ID'].isin(id_to_idx) & tx['RECEIVER_ACCOUNT_ID'].isin(id_to_idx)]

# ── 4. ARISTAS ────────────────────────────────────────────────
src = [id_to_idx[s] for s in tx['SENDER_ACCOUNT_ID']]
dst = [id_to_idx[d] for d in tx['RECEIVER_ACCOUNT_ID']]
edge_index = torch.tensor([src, dst], dtype=torch.long)

scaler_edge    = StandardScaler()
amounts_scaled = scaler_edge.fit_transform(tx['TX_AMOUNT'].values.reshape(-1, 1))
edge_attr      = torch.tensor(amounts_scaled, dtype=torch.float)

# ── 5. LABELS DE NODO ─────────────────────────────────────────
sar_receivers = set(tx[tx['IS_FRAUD'] == True]['RECEIVER_ACCOUNT_ID'])
y = torch.tensor(
    [1 if aid in sar_receivers else 0 for aid in account_ids],
    dtype=torch.long
)
n_pos = y.sum().item()
n_neg = n_nodes - n_pos
print(f"\nNodos fraude: {n_pos:,} ({n_pos/n_nodes*100:.2f}%)")
print(f"Nodos normales: {n_neg:,} ({n_neg/n_nodes*100:.2f}%)")

# ── 6. FEATURES DE NODO ───────────────────────────────────────
in_degree  = torch.zeros(n_nodes)
out_degree = torch.zeros(n_nodes)
for s, d in zip(src, dst):
    out_degree[s] += 1
    in_degree[d]  += 1

balances = accounts['INIT_BALANCE'].fillna(0).values.reshape(-1, 1)
scaler_node    = StandardScaler()
balances_scaled = scaler_node.fit_transform(balances)

x = torch.cat([
    torch.tensor(balances_scaled, dtype=torch.float),
    in_degree.unsqueeze(1),
    out_degree.unsqueeze(1),
], dim=1)

# ── 7. TRAIN/VAL/TEST SPLIT ───────────────────────────────────
torch.manual_seed(42)
perm    = torch.randperm(n_nodes)
n_train = int(0.7 * n_nodes)
n_val   = int(0.15 * n_nodes)

train_mask = torch.zeros(n_nodes, dtype=torch.bool)
val_mask   = torch.zeros(n_nodes, dtype=torch.bool)
test_mask  = torch.zeros(n_nodes, dtype=torch.bool)

train_mask[perm[:n_train]]            = True
val_mask[perm[n_train:n_train+n_val]] = True
test_mask[perm[n_train+n_val:]]       = True

data = Data(x=x, edge_index=edge_index, edge_attr=edge_attr, y=y,
            train_mask=train_mask, val_mask=val_mask, test_mask=test_mask)
print(f"\nGrafo: {data}")

# ── 8. MODELO ─────────────────────────────────────────────────
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

# ── 9. ENTRENAMIENTO ──────────────────────────────────────────
device    = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
print(f"\nDispositivo: {device}")

model     = FanInGNN(in_channels=x.shape[1]).to(device)
data      = data.to(device)
optimizer = torch.optim.Adam(model.parameters(), lr=0.001, weight_decay=1e-4)
criterion = torch.nn.CrossEntropyLoss(
    weight=torch.tensor([1.0, n_neg / max(n_pos, 1)]).to(device)
)

def train():
    model.train()
    optimizer.zero_grad()
    out  = model(data.x, data.edge_index)
    loss = criterion(out[data.train_mask], data.y[data.train_mask])
    loss.backward()
    optimizer.step()
    return loss.item()

@torch.no_grad()
def evaluate(mask):
    model.eval()
    out    = model(data.x, data.edge_index)
    pred   = out.argmax(dim=1)
    y_true = data.y[mask].cpu().numpy()
    y_pred = pred[mask].cpu().numpy()
    acc    = (pred[mask] == data.y[mask]).sum().item() / mask.sum().item()
    return acc, y_true, y_pred

print("\nEntrenando...")
best_val_acc = 0
for epoch in range(1, 101):
    loss = train()
    if epoch % 10 == 0:
        val_acc, _, _ = evaluate(data.val_mask)
        print(f"Epoch {epoch:03d} | Loss: {loss:.4f} | Val Acc: {val_acc:.4f}")
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            torch.save(model.state_dict(), 'model_fanin.pt')

# ── 10. EVALUACIÓN FINAL ──────────────────────────────────────
model.load_state_dict(torch.load('model_fanin.pt'))
test_acc, y_true, y_pred = evaluate(data.test_mask)
print(f"\n=== RESULTADO TEST ===")
print(f"Accuracy: {test_acc:.4f}")
print(classification_report(y_true, y_pred, target_names=['Normal', 'Fan-in'], zero_division=0))
