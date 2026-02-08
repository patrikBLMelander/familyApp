import { useState, useEffect } from "react";
import {
  addAllowance,
  getMemberActiveSavingsGoals,
  SavingsGoalResponse,
  SavingsGoalAllocationRequest,
} from "../../shared/api/wallet";

type GiveAllowanceDialogProps = {
  childName: string;
  childMemberId: string;
  onClose: () => void;
  onSuccess: () => void;
};

export function GiveAllowanceDialog({
  childName,
  childMemberId,
  onClose,
  onSuccess,
}: GiveAllowanceDialogProps) {
  const [amount, setAmount] = useState<string>("");
  const [description, setDescription] = useState<string>("");
  const [savingsGoals, setSavingsGoals] = useState<SavingsGoalResponse[]>([]);
  const [goalAllocations, setGoalAllocations] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        const goalsData = await getMemberActiveSavingsGoals(childMemberId);
        setSavingsGoals(goalsData);
      } catch (e) {
        console.error("Error loading savings goals:", e);
        // Don't show error, just continue without goals
        setSavingsGoals([]);
      } finally {
        setLoadingData(false);
      }
    };
    void load();
  }, [childMemberId]);

  const handleAmountChange = (value: string) => {
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

    const { totalAllocated } = calculateTotals();
    if (totalAllocated > amountNum) {
      setError("Summan av sparmål kan inte överstiga beloppet");
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
      await addAllowance(
        childMemberId,
        amountNum,
        description || "Månadspeng",
        allocations.length > 0 ? allocations : null
      );
      onSuccess();
      onClose();
    } catch (e) {
      console.error("Error giving allowance:", e);
      setError(e instanceof Error ? e.message : "Kunde inte ge pengar");
    } finally {
      setLoading(false);
    }
  };

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
          display: "flex",
          flexDirection: "column",
          overflowY: "auto",
          boxSizing: "border-box",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ marginTop: 0, marginBottom: "20px", fontSize: "1.25rem", fontWeight: 600 }}>
          Ge pengar till {childName}
        </h3>

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
          <label htmlFor="allowance-amount" style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
            Belopp (kr) *
          </label>
          <input
            id="allowance-amount"
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

        {/* Description */}
        <div style={{ marginBottom: "16px" }}>
          <label htmlFor="allowance-description" style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
            Beskrivning (valfritt)
          </label>
          <input
            id="allowance-description"
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
            placeholder="T.ex. Månadspeng, Veckopeng..."
          />
        </div>

        {/* Savings Goals Allocation */}
        {!loadingData && savingsGoals.length > 0 && (
          <div style={{ marginBottom: "20px" }}>
            <label style={{ display: "block", marginBottom: "12px", fontWeight: 500 }}>
              Fördela till sparmål (valfritt)
            </label>
            <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              {savingsGoals.map((goal) => {
                const allocationValue = goalAllocations.get(goal.id) || "";
                const remaining = goal.targetAmount - goal.currentAmount;
                const canAllocate = remaining > 0;

                return (
                  <div
                    key={goal.id}
                    style={{
                      padding: "12px",
                      background: "rgba(72, 187, 120, 0.05)",
                      borderRadius: "8px",
                      border: "1px solid rgba(72, 187, 120, 0.2)",
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "8px" }}>
                      <span style={{ fontWeight: 600, fontSize: "0.875rem" }}>
                        {goal.emoji ? `${goal.emoji} ` : ""}
                        {goal.name}
                      </span>
                      <span style={{ fontSize: "0.875rem", color: "#6b6b6b" }}>
                        {goal.currentAmount} / {goal.targetAmount} kr
                      </span>
                    </div>
                    {canAllocate ? (
                      <>
                        <input
                          type="text"
                          inputMode="numeric"
                          value={allocationValue}
                          onChange={(e) => handleGoalAllocationChange(goal.id, e.target.value)}
                          disabled={loading}
                          placeholder="0"
                          style={{
                            width: "100%",
                            padding: "8px",
                            borderRadius: "6px",
                            border: "1px solid #ddd",
                            fontSize: "0.875rem",
                            boxSizing: "border-box",
                          }}
                        />
                        <p style={{ margin: "4px 0 0", fontSize: "0.75rem", color: "#6b6b6b" }}>
                          {remaining} kr kvar till målet
                        </p>
                      </>
                    ) : (
                      <p style={{ margin: 0, fontSize: "0.75rem", color: "#48bb78", fontWeight: 600 }}>
                        ✓ Målet är uppnått!
                      </p>
                    )}
                  </div>
                );
              })}
            </div>
            {(() => {
              const { totalAllocated, remaining } = calculateTotals();
              const amountNum = parseInt(amount) || 0;
              if (amountNum > 0) {
                return (
                  <div style={{ marginTop: "12px", padding: "8px", background: "#f7fafc", borderRadius: "6px" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.875rem" }}>
                      <span>Totalt till sparmål:</span>
                      <span style={{ fontWeight: 600 }}>{totalAllocated} kr</span>
                    </div>
                    <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.875rem", marginTop: "4px" }}>
                      <span>Kvar på kontot:</span>
                      <span style={{ fontWeight: 600, color: remaining >= 0 ? "#48bb78" : "#c53030" }}>
                        {remaining} kr
                      </span>
                    </div>
                  </div>
                );
              }
              return null;
            })()}
          </div>
        )}

        {/* Buttons */}
        <div style={{ display: "flex", gap: "12px" }}>
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
            disabled={loading || parseInt(amount) <= 0}
            style={{
              flex: 1,
              padding: "12px",
              background: loading || parseInt(amount) <= 0 ? "#cbd5e0" : "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
              color: "white",
              border: "none",
              borderRadius: "8px",
              fontSize: "1rem",
              fontWeight: 600,
              cursor: loading || parseInt(amount) <= 0 ? "not-allowed" : "pointer",
            }}
          >
            {loading ? "Sparar..." : "Ge pengar"}
          </button>
        </div>
      </div>
    </div>
  );
}
