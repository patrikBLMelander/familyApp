import { useState, useEffect } from "react";
import {
  getActiveSavingsGoals,
  allocateToSavingsGoals,
  SavingsGoalResponse,
  SavingsGoalAllocationRequest,
} from "../../shared/api/wallet";

type AllocateToGoalsDialogProps = {
  currentBalance: number;
  onClose: () => void;
  onSuccess: () => void;
};

export function AllocateToGoalsDialog({
  currentBalance,
  onClose,
  onSuccess,
}: AllocateToGoalsDialogProps) {
  const [savingsGoals, setSavingsGoals] = useState<SavingsGoalResponse[]>([]);
  const [goalAllocations, setGoalAllocations] = useState<Map<string, string>>(new Map());
  const [loading, setLoading] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        const goalsData = await getActiveSavingsGoals();
        setSavingsGoals(goalsData.filter((g) => g.isActive && !g.isCompleted));
      } catch (e) {
        console.error("Error loading savings goals:", e);
        setError("Kunde inte ladda sparmål");
      } finally {
        setLoadingData(false);
      }
    };
    void load();
  }, []);

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
    const totalAllocated = Array.from(goalAllocations.values()).reduce(
      (sum, val) => sum + (parseInt(val) || 0),
      0
    );
    const remaining = currentBalance - totalAllocated;
    return { totalAllocated, remaining };
  };

  const handleSubmit = async () => {
    setError(null);
    const { totalAllocated } = calculateTotals();

    if (totalAllocated <= 0) {
      setError("Du måste fördela minst 1 kr");
      return;
    }

    if (totalAllocated > currentBalance) {
      setError("Du kan inte fördela mer än du har på kontot");
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
      await allocateToSavingsGoals(allocations);
      onSuccess();
      onClose();
    } catch (e) {
      console.error("Error allocating to goals:", e);
      setError(e instanceof Error ? e.message : "Kunde inte fördela pengar");
    } finally {
      setLoading(false);
    }
  };

  const { totalAllocated, remaining } = calculateTotals();

  if (loadingData) {
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
            boxShadow: "0 4px 20px rgba(0, 0, 0, 0.15)",
            display: "flex",
            flexDirection: "column",
            boxSizing: "border-box",
          }}
          onClick={(e) => e.stopPropagation()}
        >
          <p style={{ color: "#6b6b6b" }}>Laddar...</p>
        </div>
      </div>
    );
  }

  if (savingsGoals.length === 0) {
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
            boxShadow: "0 4px 20px rgba(0, 0, 0, 0.15)",
            display: "flex",
            flexDirection: "column",
            boxSizing: "border-box",
          }}
          onClick={(e) => e.stopPropagation()}
        >
          <h3 style={{ marginTop: 0, marginBottom: "16px", fontSize: "1.25rem", fontWeight: 600 }}>
            Fördela till sparmål
          </h3>
          <p style={{ color: "#6b6b6b", marginBottom: "20px" }}>
            Du har inga aktiva sparmål att fördela pengar till.
          </p>
          <button
            type="button"
            onClick={onClose}
            style={{
              width: "100%",
              padding: "12px",
              background: "white",
              color: "#2d3748",
              border: "2px solid #e2e8f0",
              borderRadius: "8px",
              fontSize: "1rem",
              fontWeight: 600,
              cursor: "pointer",
            }}
          >
            Stäng
          </button>
        </div>
      </div>
    );
  }

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
          Fördela till sparmål
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

        {/* Current Balance */}
        <div
          style={{
            padding: "12px",
            background: "rgba(72, 187, 120, 0.1)",
            borderRadius: "8px",
            marginBottom: "20px",
            textAlign: "center",
          }}
        >
          <p style={{ margin: "0 0 4px", fontSize: "0.875rem", color: "#6b6b6b" }}>Tillgängligt på kontot</p>
          <p style={{ margin: 0, fontSize: "1.5rem", fontWeight: 700, color: "#2d5a2d" }}>
            {currentBalance} kr
          </p>
        </div>

        {/* Savings Goals */}
        <div style={{ marginBottom: "20px" }}>
          <label style={{ display: "block", marginBottom: "12px", fontWeight: 500 }}>
            Välj hur mycket du vill fördela till varje mål
          </label>
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            {savingsGoals.map((goal) => {
              const allocationValue = goalAllocations.get(goal.id) || "";
              const remaining = goal.targetAmount - goal.currentAmount;
              const canAllocate = remaining > 0;
              const maxAmount = Math.min(remaining, currentBalance);

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
                        {remaining} kr kvar till målet (max {maxAmount} kr)
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
        </div>

        {/* Summary */}
        {totalAllocated > 0 && (
          <div style={{ marginBottom: "20px", padding: "12px", background: "#f7fafc", borderRadius: "8px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.875rem", marginBottom: "4px" }}>
              <span>Totalt fördelat:</span>
              <span style={{ fontWeight: 600 }}>{totalAllocated} kr</span>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", fontSize: "0.875rem" }}>
              <span>Kvar på kontot:</span>
              <span style={{ fontWeight: 600, color: remaining >= 0 ? "#48bb78" : "#c53030" }}>
                {remaining} kr
              </span>
            </div>
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
            disabled={loading || totalAllocated <= 0 || totalAllocated > currentBalance}
            style={{
              flex: 1,
              padding: "12px",
              background:
                loading || totalAllocated <= 0 || totalAllocated > currentBalance
                  ? "#cbd5e0"
                  : "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
              color: "white",
              border: "none",
              borderRadius: "8px",
              fontSize: "1rem",
              fontWeight: 600,
              cursor:
                loading || totalAllocated <= 0 || totalAllocated > currentBalance
                  ? "not-allowed"
                  : "pointer",
            }}
          >
            {loading ? "Fördelar..." : "Fördela"}
          </button>
        </div>
      </div>
    </div>
  );
}
