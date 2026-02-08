import { useState, useEffect } from "react";
import {
  recordExpense,
  getExpenseCategories,
  getActiveSavingsGoals,
  ExpenseCategoryResponse,
  SavingsGoalResponse,
  SavingsGoalAllocationRequest,
} from "../../shared/api/wallet";

type RecordExpenseDialogProps = {
  currentBalance: number;
  onClose: () => void;
  onSuccess: () => void;
};

export function RecordExpenseDialog({
  currentBalance,
  onClose,
  onSuccess,
}: RecordExpenseDialogProps) {
  const [amount, setAmount] = useState<string>("");
  const [description, setDescription] = useState<string>("");
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [categories, setCategories] = useState<ExpenseCategoryResponse[]>([]);
  const [savingsGoals, setSavingsGoals] = useState<SavingsGoalResponse[]>([]);
  const [goalAllocations, setGoalAllocations] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadingData, setLoadingData] = useState(true);

  useEffect(() => {
    const load = async () => {
      try {
        const [categoriesData, goalsData] = await Promise.all([
          getExpenseCategories(),
          getActiveSavingsGoals(),
        ]);
        setCategories(categoriesData);
        setSavingsGoals(goalsData);
        // Set first category as default
        if (categoriesData.length > 0) {
          setCategoryId(categoriesData[0].id);
        }
      } catch (e) {
        console.error("Error loading data:", e);
        setError("Kunde inte ladda data");
      } finally {
        setLoadingData(false);
      }
    };
    void load();
  }, []);

  const handleAmountChange = (value: string) => {
    // Only allow positive integers
    const numValue = value.replace(/\D/g, "");
    setAmount(numValue);
  };

  const handleGoalAllocationChange = (goalId: string, value: string) => {
    const numValue = value.replace(/\D/g, "");
    const newAllocations = new Map(goalAllocations);
    if (numValue === "") {
      newAllocations.delete(goalId);
    } else {
      newAllocations.set(goalId, numValue);
    }
    setGoalAllocations(newAllocations);
  };

  const calculateTotals = () => {
    const amountNum = parseInt(amount) || 0;
    const totalAllocated = Array.from(goalAllocations.values()).reduce(
      (sum, val) => sum + (parseInt(val) || 0),
      0
    );
    const remaining = amountNum - totalAllocated;
    return { totalAllocated, remaining };
  };

  const handleSubmit = async () => {
    setError(null);
    const amountNum = parseInt(amount) || 0;

    if (amountNum <= 0) {
      setError("Beloppet måste vara större än 0");
      return;
    }

    if (amountNum > currentBalance) {
      setError(`Du har inte tillräckligt med pengar. Du har ${currentBalance} kr.`);
      return;
    }

    if (!categoryId) {
      setError("Välj en kategori");
      return;
    }

    const { totalAllocated, remaining } = calculateTotals();
    if (totalAllocated > amountNum) {
      setError("Summan av sparmål kan inte överstiga köpbeloppet");
      return;
    }

    // Build allocations array
    const allocations: SavingsGoalAllocationRequest[] = [];
    goalAllocations.forEach((value, goalId) => {
      const allocationAmount = parseInt(value) || 0;
      if (allocationAmount > 0) {
        allocations.push({
          savingsGoalId: goalId,
          amount: allocationAmount,
        });
      }
    });

    setLoading(true);
    try {
      await recordExpense(
        amountNum,
        description || null,
        categoryId,
        allocations.length > 0 ? allocations : null
      );
      onSuccess();
      onClose();
    } catch (e) {
      console.error("Error recording expense:", e);
      setError(e instanceof Error ? e.message : "Kunde inte registrera köpet");
    } finally {
      setLoading(false);
    }
  };

  const { totalAllocated, remaining } = calculateTotals();
  const amountNum = parseInt(amount) || 0;

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: "rgba(0, 0, 0, 0.5)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
        padding: "16px",
        boxSizing: "border-box",
      }}
      onClick={onClose}
    >
      <div
        style={{
          backgroundColor: "white",
          borderRadius: "12px",
          padding: "24px",
          maxWidth: "500px",
          width: "100%",
          maxHeight: "calc(100vh - 32px)",
          boxShadow: "0 4px 20px rgba(0, 0, 0, 0.15)",
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          boxSizing: "border-box",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ marginTop: 0, marginBottom: "20px", fontSize: "1.25rem", fontWeight: 600 }}>
          Registrera köp
        </h3>

        {loadingData ? (
          <p style={{ color: "#6b6b6b" }}>Laddar...</p>
        ) : (
          <>
            {error && (
              <div
                style={{
                  padding: "12px",
                  background: "#fee",
                  borderRadius: "8px",
                  marginBottom: "16px",
                  color: "#c53030",
                  fontSize: "0.875rem",
                }}
              >
                {error}
              </div>
            )}

            {/* Amount */}
            <div style={{ marginBottom: "16px" }}>
              <label
                htmlFor="amount"
                style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}
              >
                Belopp (kr) *
              </label>
              <input
                id="amount"
                type="text"
                inputMode="numeric"
                value={amount}
                onChange={(e) => handleAmountChange(e.target.value)}
                disabled={loading}
                style={{
                  width: "100%",
                  padding: "12px",
                  borderRadius: "8px",
                  border: "1px solid #ddd",
                  fontSize: "1rem",
                  boxSizing: "border-box",
                }}
                placeholder="0"
              />
            </div>

            {/* Category */}
            <div style={{ marginBottom: "16px" }}>
              <label
                htmlFor="category"
                style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}
              >
                Kategori *
              </label>
              <select
                id="category"
                value={categoryId || ""}
                onChange={(e) => setCategoryId(e.target.value || null)}
                disabled={loading}
                style={{
                  width: "100%",
                  padding: "12px",
                  borderRadius: "8px",
                  border: "1px solid #ddd",
                  fontSize: "1rem",
                  boxSizing: "border-box",
                }}
              >
                {categories.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.emoji ? `${cat.emoji} ` : ""}
                    {cat.name}
                  </option>
                ))}
              </select>
            </div>

            {/* Description */}
            <div style={{ marginBottom: "16px" }}>
              <label
                htmlFor="description"
                style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}
              >
                Beskrivning (valfritt)
              </label>
              <input
                id="description"
                type="text"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                disabled={loading}
                style={{
                  width: "100%",
                  padding: "12px",
                  borderRadius: "8px",
                  border: "1px solid #ddd",
                  fontSize: "1rem",
                  boxSizing: "border-box",
                }}
                placeholder="T.ex. Choklad, Leksak..."
              />
            </div>

            {/* Savings Goals */}
            {savingsGoals.length > 0 && amountNum > 0 && (
              <div style={{ marginBottom: "16px" }}>
                <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
                  Spara till mål (valfritt)
                </label>
                {savingsGoals.map((goal) => {
                  const allocationValue = goalAllocations.get(goal.id) || "";
                  const allocationAmount = parseInt(allocationValue) || 0;
                  const maxAmount = Math.min(
                    goal.remainingAmount,
                    amountNum - (totalAllocated - allocationAmount)
                  );

                  return (
                    <div
                      key={goal.id}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "8px",
                        marginBottom: "8px",
                        padding: "8px",
                        background: "rgba(72, 187, 120, 0.05)",
                        borderRadius: "8px",
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={allocationValue !== ""}
                        onChange={(e) => {
                          if (e.target.checked) {
                            handleGoalAllocationChange(goal.id, "0");
                          } else {
                            handleGoalAllocationChange(goal.id, "");
                          }
                        }}
                        disabled={loading || maxAmount <= 0}
                        style={{ marginRight: "4px" }}
                      />
                      <span style={{ flex: 1, fontSize: "0.875rem" }}>
                        {goal.emoji ? `${goal.emoji} ` : ""}
                        {goal.name}
                      </span>
                      {allocationValue !== "" && (
                        <input
                          type="text"
                          inputMode="numeric"
                          value={allocationValue}
                          onChange={(e) => handleGoalAllocationChange(goal.id, e.target.value)}
                          disabled={loading || maxAmount <= 0}
                          style={{
                            width: "80px",
                            padding: "6px",
                            borderRadius: "6px",
                            border: "1px solid #ddd",
                            fontSize: "0.875rem",
                          }}
                          placeholder="0"
                        />
                      )}
                      <span style={{ fontSize: "0.75rem", color: "#6b6b6b" }}>
                        (max {maxAmount} kr)
                      </span>
                    </div>
                  );
                })}
                {amountNum > 0 && (
                  <div
                    style={{
                      marginTop: "8px",
                      padding: "8px",
                      background: "rgba(72, 187, 120, 0.1)",
                      borderRadius: "8px",
                      fontSize: "0.875rem",
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between" }}>
                      <span>Totalt sparat:</span>
                      <span style={{ fontWeight: 600 }}>{totalAllocated} kr</span>
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", marginTop: "4px" }}>
                      <span>Kvar att betala:</span>
                      <span style={{ fontWeight: 600 }}>{remaining} kr</span>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Buttons */}
            <div style={{ display: "flex", gap: "12px", marginTop: "8px" }}>
              <button
                type="button"
                onClick={onClose}
                disabled={loading}
                style={{
                  flex: 1,
                  padding: "12px",
                  background: "white",
                  color: "#2d3748",
                  border: "2px solid #e2e8f0",
                  borderRadius: "8px",
                  fontSize: "1rem",
                  fontWeight: 600,
                  cursor: loading ? "not-allowed" : "pointer",
                }}
              >
                Avbryt
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={loading || amountNum <= 0 || amountNum > currentBalance || !categoryId}
                style={{
                  flex: 1,
                  padding: "12px",
                  background:
                    loading || amountNum <= 0 || amountNum > currentBalance || !categoryId
                      ? "#cbd5e0"
                      : "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
                  color: "white",
                  border: "none",
                  borderRadius: "8px",
                  fontSize: "1rem",
                  fontWeight: 600,
                  cursor:
                    loading || amountNum <= 0 || amountNum > currentBalance || !categoryId
                      ? "not-allowed"
                      : "pointer",
                }}
              >
                {loading ? "Sparar..." : "Betala"}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
