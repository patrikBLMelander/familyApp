import { useState, useEffect } from "react";
import {
  recordExpenseForMember,
  getMemberWalletBalance,
  getMemberActiveSavingsGoals,
  getExpenseCategories,
  ExpenseCategoryResponse,
  SavingsGoalResponse,
  SavingsGoalAllocationRequest,
} from "../../shared/api/wallet";

type RecordChildExpenseDialogProps = {
  childMemberId: string;
  childName: string;
  onClose: () => void;
  onSuccess: () => void;
};

export function RecordChildExpenseDialog({
  childMemberId,
  childName,
  onClose,
  onSuccess,
}: RecordChildExpenseDialogProps) {
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [categories, setCategories] = useState<ExpenseCategoryResponse[]>([]);
  const [savingsGoals, setSavingsGoals] = useState<SavingsGoalResponse[]>([]);
  const [goalAllocations, setGoalAllocations] = useState<Map<string, string>>(new Map());
  const [currentBalance, setCurrentBalance] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        const [categoriesData, goalsData, balanceData] = await Promise.all([
          getExpenseCategories(),
          getMemberActiveSavingsGoals(childMemberId),
          getMemberWalletBalance(childMemberId),
        ]);
        setCategories(categoriesData);
        setSavingsGoals(goalsData);
        setCurrentBalance(balanceData.balance);
        if (categoriesData.length > 0) setCategoryId(categoriesData[0].id);
      } catch (e) {
        setError("Kunde inte ladda data");
      } finally {
        setLoadingData(false);
      }
    };
    void load();
  }, [childMemberId]);

  const amountNum = parseInt(amount) || 0;
  const totalAllocated = Array.from(goalAllocations.values()).reduce((sum, v) => sum + (parseInt(v) || 0), 0);
  const remaining = amountNum - totalAllocated;

  const handleGoalAllocationChange = (goalId: string, value: string) => {
    const numValue = value.replace(/\D/g, "");
    const next = new Map(goalAllocations);
    if (numValue === "") next.delete(goalId); else next.set(goalId, numValue);
    setGoalAllocations(next);
  };

  const handleSubmit = async () => {
    setError(null);
    if (amountNum <= 0) { setError("Beloppet måste vara större än 0"); return; }
    if (currentBalance !== null && amountNum > currentBalance) {
      setError(`${childName} har inte tillräckligt med pengar (${currentBalance} kr).`);
      return;
    }
    if (!categoryId) { setError("Välj en kategori"); return; }
    if (totalAllocated > amountNum) { setError("Summan av sparmål kan inte överstiga köpbeloppet"); return; }

    const allocations: SavingsGoalAllocationRequest[] = [];
    goalAllocations.forEach((value, goalId) => {
      const a = parseInt(value) || 0;
      if (a > 0) allocations.push({ savingsGoalId: goalId, amount: a });
    });

    setLoading(true);
    try {
      await recordExpenseForMember(childMemberId, amountNum, description || null, categoryId, allocations.length > 0 ? allocations : null);
      onSuccess();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Kunde inte registrera köpet");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{ position: "fixed", inset: 0, backgroundColor: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: "16px", boxSizing: "border-box" }}
      onClick={onClose}
    >
      <div
        style={{ backgroundColor: "white", borderRadius: "12px", padding: "24px", maxWidth: "500px", width: "100%", maxHeight: "calc(100vh - 32px)", boxShadow: "0 4px 20px rgba(0,0,0,0.15)", overflowY: "auto", boxSizing: "border-box" }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ marginTop: 0, marginBottom: "4px", fontSize: "1.2rem", fontWeight: 600 }}>
          Registrera köp
        </h3>
        <p style={{ margin: "0 0 20px", fontSize: "0.9rem", color: "#6b6b6b" }}>
          {childName} · {currentBalance !== null ? `${currentBalance} kr kvar` : "laddar..."}
        </p>

        {loadingData ? (
          <p style={{ color: "#6b6b6b" }}>Laddar...</p>
        ) : (
          <>
            {error && (
              <div style={{ padding: "12px", background: "#fee", borderRadius: "8px", marginBottom: "16px", color: "#c53030", fontSize: "0.875rem" }}>
                {error}
              </div>
            )}

            {/* Amount */}
            <div style={{ marginBottom: "16px" }}>
              <label htmlFor="child-expense-amount" style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>Belopp (kr) *</label>
              <input
                id="child-expense-amount"
                type="text"
                inputMode="numeric"
                value={amount}
                onChange={(e) => setAmount(e.target.value.replace(/\D/g, ""))}
                disabled={loading}
                placeholder="0"
                style={{ width: "100%", padding: "12px", borderRadius: "8px", border: "1px solid #ddd", fontSize: "1rem", boxSizing: "border-box" }}
              />
            </div>

            {/* Category */}
            <div style={{ marginBottom: "16px" }}>
              <label htmlFor="child-expense-category" style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>Kategori *</label>
              <select
                id="child-expense-category"
                value={categoryId || ""}
                onChange={(e) => setCategoryId(e.target.value || null)}
                disabled={loading}
                style={{ width: "100%", padding: "12px", borderRadius: "8px", border: "1px solid #ddd", fontSize: "1rem", boxSizing: "border-box" }}
              >
                {categories.map((cat) => (
                  <option key={cat.id} value={cat.id}>{cat.emoji ? `${cat.emoji} ` : ""}{cat.name}</option>
                ))}
              </select>
            </div>

            {/* Description */}
            <div style={{ marginBottom: "16px" }}>
              <label htmlFor="child-expense-desc" style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>Beskrivning (valfritt)</label>
              <input
                id="child-expense-desc"
                type="text"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                disabled={loading}
                placeholder="T.ex. Choklad, Leksak..."
                style={{ width: "100%", padding: "12px", borderRadius: "8px", border: "1px solid #ddd", fontSize: "1rem", boxSizing: "border-box" }}
              />
            </div>

            {/* Savings Goals */}
            {savingsGoals.length > 0 && amountNum > 0 && (
              <div style={{ marginBottom: "16px" }}>
                <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>Spara till mål (valfritt)</label>
                {savingsGoals.map((goal) => {
                  const allocationValue = goalAllocations.get(goal.id) || "";
                  const allocationAmount = parseInt(allocationValue) || 0;
                  const maxAmount = Math.min(goal.remainingAmount, amountNum - (totalAllocated - allocationAmount));
                  return (
                    <div key={goal.id} style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px", padding: "8px", background: "rgba(72,187,120,0.05)", borderRadius: "8px" }}>
                      <input type="checkbox" checked={allocationValue !== ""} onChange={(e) => handleGoalAllocationChange(goal.id, e.target.checked ? "0" : "")} disabled={loading || maxAmount <= 0} />
                      <span style={{ flex: 1, fontSize: "0.875rem" }}>{goal.emoji ? `${goal.emoji} ` : ""}{goal.name}</span>
                      {allocationValue !== "" && (
                        <input type="text" inputMode="numeric" value={allocationValue} onChange={(e) => handleGoalAllocationChange(goal.id, e.target.value)} disabled={loading} style={{ width: "80px", padding: "6px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "0.875rem" }} />
                      )}
                      <span style={{ fontSize: "0.75rem", color: "#6b6b6b" }}>(max {maxAmount} kr)</span>
                    </div>
                  );
                })}
                {amountNum > 0 && (
                  <div style={{ marginTop: "8px", padding: "8px", background: "rgba(72,187,120,0.1)", borderRadius: "8px", fontSize: "0.875rem" }}>
                    <div style={{ display: "flex", justifyContent: "space-between" }}><span>Totalt sparat:</span><span style={{ fontWeight: 600 }}>{totalAllocated} kr</span></div>
                    <div style={{ display: "flex", justifyContent: "space-between", marginTop: "4px" }}><span>Kvar att betala:</span><span style={{ fontWeight: 600 }}>{remaining} kr</span></div>
                  </div>
                )}
              </div>
            )}

            <div style={{ display: "flex", gap: "12px", marginTop: "8px" }}>
              <button type="button" onClick={onClose} disabled={loading}
                style={{ flex: 1, padding: "12px", background: "white", color: "#2d3748", border: "2px solid #e2e8f0", borderRadius: "8px", fontSize: "1rem", fontWeight: 600, cursor: loading ? "not-allowed" : "pointer" }}>
                Avbryt
              </button>
              <button type="button" onClick={() => void handleSubmit()}
                disabled={loading || amountNum <= 0 || !categoryId || (currentBalance !== null && amountNum > currentBalance)}
                style={{ flex: 1, padding: "12px", background: loading || amountNum <= 0 || !categoryId || (currentBalance !== null && amountNum > currentBalance) ? "#cbd5e0" : "linear-gradient(135deg, #48bb78 0%, #38a169 100%)", color: "white", border: "none", borderRadius: "8px", fontSize: "1rem", fontWeight: 600, cursor: "pointer" }}>
                {loading ? "Sparar..." : "Betala"}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
