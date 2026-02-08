import { useState, useEffect } from "react";
import {
  getWalletBalance,
  getActiveSavingsGoals,
  WalletBalanceResponse,
  SavingsGoalResponse,
} from "../../shared/api/wallet";
import { RecordExpenseDialog } from "./RecordExpenseDialog";

type WalletOverviewProps = {
  onShowDetails?: () => void;
  onNavigate?: (view: "wallet") => void;
};

export function WalletOverview({ onShowDetails, onNavigate }: WalletOverviewProps) {
  const [balance, setBalance] = useState<WalletBalanceResponse | null>(null);
  const [savingsGoals, setSavingsGoals] = useState<SavingsGoalResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showExpenseDialog, setShowExpenseDialog] = useState(false);

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);
        const [balanceData, goalsData] = await Promise.all([
          getWalletBalance().catch((e) => {
            console.error("Error fetching wallet balance:", e);
            // Return a default balance if API fails
            return { id: "", memberId: "", balance: 0 };
          }),
          getActiveSavingsGoals().catch((e) => {
            console.error("Error fetching savings goals:", e);
            // Return empty array if API fails
            return [];
          }),
        ]);
        setBalance(balanceData);
        setSavingsGoals(goalsData);
      } catch (e) {
        console.error("Error loading wallet data:", e);
        setError("Kunde inte ladda plånboksdata");
        // Set defaults to prevent crash
        setBalance({ id: "", memberId: "", balance: 0 });
        setSavingsGoals([]);
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, []);

  const refresh = async () => {
    try {
      const [balanceData, goalsData] = await Promise.all([
        getWalletBalance(),
        getActiveSavingsGoals(),
      ]);
      setBalance(balanceData);
      setSavingsGoals(goalsData);
    } catch (e) {
      console.error("Error refreshing wallet data:", e);
    }
  };

  if (loading) {
    return (
      <div style={{
        background: "white",
        borderRadius: "16px",
        padding: "20px",
        marginBottom: "20px",
        boxShadow: "0 4px 12px rgba(0, 0, 0, 0.1)",
      }}>
        <p style={{ margin: 0, color: "#6b6b6b" }}>Laddar plånbok...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{
        background: "white",
        borderRadius: "16px",
        padding: "20px",
        marginBottom: "20px",
        boxShadow: "0 4px 12px rgba(0, 0, 0, 0.1)",
      }}>
        <p style={{ margin: 0, color: "#c53030" }}>{error}</p>
      </div>
    );
  }

  return (
    <div style={{
      background: "white",
      borderRadius: "16px",
      padding: "20px",
      marginBottom: "20px",
      boxShadow: "0 4px 12px rgba(0, 0, 0, 0.1)",
    }}>
      {/* Header */}
      <div style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: "16px",
      }}>
        <h3 style={{ margin: 0, fontSize: "1.25rem", fontWeight: 700, color: "#2d3748" }}>
          💰 Min Plånbok
        </h3>
      </div>

      {/* Balance */}
      <div style={{
        textAlign: "center",
        marginBottom: "20px",
        padding: "16px",
        background: "linear-gradient(135deg, rgba(72, 187, 120, 0.1) 0%, rgba(56, 161, 105, 0.1) 100%)",
        borderRadius: "12px",
      }}>
        <p style={{ margin: "0 0 4px", fontSize: "0.875rem", color: "#6b6b6b" }}>
          Saldo
        </p>
        <p style={{ margin: 0, fontSize: "2rem", fontWeight: 700, color: "#2d5a2d" }}>
          {balance?.balance || 0} kr
        </p>
      </div>

      {/* Active Savings Goals */}
      {savingsGoals.length > 0 && (
        <div style={{ marginBottom: "20px" }}>
          <h4 style={{
            margin: "0 0 12px",
            fontSize: "1rem",
            fontWeight: 600,
            color: "#2d3748",
          }}>
            🎯 Sparmål
          </h4>
          {savingsGoals.map((goal) => (
            <div
              key={goal.id}
              style={{
                marginBottom: "12px",
                padding: "12px",
                background: "rgba(72, 187, 120, 0.05)",
                borderRadius: "8px",
              }}
            >
              <div style={{
                display: "flex",
                justifyContent: "space-between",
                marginBottom: "4px",
                fontSize: "0.875rem",
                fontWeight: 600,
                color: "#2d3748",
              }}>
                <span>
                  {goal.emoji ? `${goal.emoji} ` : ""}
                  {goal.name}
                </span>
                <span>{goal.currentAmount} / {goal.targetAmount} kr</span>
              </div>
              {/* Progress bar */}
              <div style={{
                width: "100%",
                height: "20px",
                background: "rgba(200, 190, 180, 0.2)",
                borderRadius: "10px",
                overflow: "hidden",
                marginBottom: "4px",
              }}>
                <div style={{
                  width: `${goal.progressPercentage}%`,
                  height: "100%",
                  background: "linear-gradient(90deg, rgba(72, 187, 120, 0.8) 0%, rgba(56, 161, 105, 1) 100%)",
                  borderRadius: "10px",
                  transition: "width 0.3s ease",
                }} />
              </div>
              <p style={{
                margin: 0,
                fontSize: "0.75rem",
                color: "#6b6b6b",
                textAlign: "right",
              }}>
                {goal.remainingAmount} kr kvar
              </p>
            </div>
          ))}
        </div>
      )}

      {/* Action buttons */}
      <div style={{
        display: "flex",
        gap: "12px",
      }}>
        <button
          type="button"
          onClick={() => setShowExpenseDialog(true)}
          style={{
            flex: 1,
            padding: "12px",
            background: "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
            color: "white",
            border: "none",
            borderRadius: "8px",
            fontSize: "1rem",
            fontWeight: 600,
            cursor: "pointer",
            boxShadow: "0 2px 8px rgba(72, 187, 120, 0.3)",
          }}
        >
          Köp
        </button>
        {(onShowDetails || onNavigate) && (
          <button
            type="button"
            onClick={() => {
              if (onNavigate) {
                onNavigate("wallet");
              } else if (onShowDetails) {
                onShowDetails();
              }
            }}
            style={{
              flex: 1,
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
            Visa mer
          </button>
        )}
      </div>

      {/* Record Expense Dialog */}
      {showExpenseDialog && balance && (
        <RecordExpenseDialog
          currentBalance={balance.balance}
          onClose={() => setShowExpenseDialog(false)}
          onSuccess={() => {
            setShowExpenseDialog(false);
            void refresh();
          }}
        />
      )}
    </div>
  );
}
