import { useState, useEffect } from "react";
import {
  getWalletBalance,
  getSavingsGoals,
  getTransactionHistory,
  getExpenseCategories,
  createSavingsGoal,
  deleteSavingsGoal,
  WalletBalanceResponse,
  SavingsGoalResponse,
  WalletTransactionResponse,
  ExpenseCategoryResponse,
} from "../../shared/api/wallet";
import { RecordExpenseDialog } from "./RecordExpenseDialog";
import { CreateSavingsGoalDialog } from "./CreateSavingsGoalDialog";
import { AllocateToGoalsDialog } from "./AllocateToGoalsDialog";

type ViewKey = "dashboard" | "xp" | "pethistory";

type WalletDetailViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

type TabType = "overview" | "goals" | "transactions";

export function WalletDetailView({ onNavigate }: WalletDetailViewProps) {
  const [activeTab, setActiveTab] = useState<TabType>("overview");
  const [balance, setBalance] = useState<WalletBalanceResponse | null>(null);
  const [savingsGoals, setSavingsGoals] = useState<SavingsGoalResponse[]>([]);
  const [transactions, setTransactions] = useState<WalletTransactionResponse[]>([]);
  const [categories, setCategories] = useState<ExpenseCategoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showExpenseDialog, setShowExpenseDialog] = useState(false);
  const [showCreateGoalDialog, setShowCreateGoalDialog] = useState(false);
  const [showAllocateDialog, setShowAllocateDialog] = useState(false);

  useEffect(() => {
    void loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [balanceData, goalsData, transactionsData, categoriesData] = await Promise.all([
        getWalletBalance(),
        getSavingsGoals(),
        getTransactionHistory(50),
        getExpenseCategories(),
      ]);
      setBalance(balanceData);
      setSavingsGoals(goalsData);
      setTransactions(transactionsData);
      setCategories(categoriesData);
    } catch (e) {
      console.error("Error loading wallet data:", e);
      setError("Kunde inte ladda plånboksdata");
    } finally {
      setLoading(false);
    }
  };

  const handleCreateGoal = async (name: string, targetAmount: number, emoji: string | null) => {
    try {
      await createSavingsGoal(name, targetAmount, emoji);
      await loadData();
      setShowCreateGoalDialog(false);
    } catch (e) {
      console.error("Error creating goal:", e);
      alert(e instanceof Error ? e.message : "Kunde inte skapa sparmål");
    }
  };

  const handleDeleteGoal = async (goalId: string) => {
    if (!confirm("Är du säker på att du vill ta bort detta sparmål?")) {
      return;
    }
    try {
      await deleteSavingsGoal(goalId);
      await loadData();
    } catch (e) {
      console.error("Error deleting goal:", e);
      alert(e instanceof Error ? e.message : "Kunde inte ta bort sparmål");
    }
  };

  const getCategoryName = (categoryId: string | null) => {
    if (!categoryId) return null;
    const category = categories.find((c) => c.id === categoryId);
    return category ? `${category.emoji || ""} ${category.name}`.trim() : null;
  };

  if (loading) {
    return (
      <div className="wallet-detail-view" style={{ padding: "20px" }}>
        <p>Laddar...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="wallet-detail-view" style={{ padding: "20px" }}>
        <p style={{ color: "#c53030" }}>{error}</p>
      </div>
    );
  }

  return (
    <div className="wallet-detail-view" style={{ padding: "20px", minHeight: "100vh" }}>
      {/* Header */}
      <div className="daily-tasks-header" style={{ marginBottom: "20px" }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
            {onNavigate && (
              <button
                type="button"
                className="back-button"
                onClick={() => onNavigate("dashboard")}
                aria-label="Tillbaka"
              >
                ←
              </button>
            )}
            <div style={{ flex: 1 }}>
              <h2 className="view-title" style={{ margin: 0 }}>💰 Min Plånbok</h2>
            </div>
          </div>
        </div>
      </div>

      {/* Tab Navigation */}
      <div
        style={{
          display: "flex",
          borderBottom: "2px solid #e2e8f0",
          marginBottom: "20px",
          gap: "8px",
        }}
      >
        <button
          type="button"
          onClick={() => setActiveTab("overview")}
          style={{
            flex: 1,
            padding: "12px 16px",
            border: "none",
            background: "transparent",
            color: activeTab === "overview" ? "#2d3748" : "#718096",
            fontWeight: activeTab === "overview" ? 600 : 400,
            fontSize: "1rem",
            cursor: "pointer",
            borderBottom: activeTab === "overview" ? "3px solid #4299e1" : "3px solid transparent",
            transition: "all 0.2s ease",
          }}
        >
          Översikt
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("goals")}
          style={{
            flex: 1,
            padding: "12px 16px",
            border: "none",
            background: "transparent",
            color: activeTab === "goals" ? "#2d3748" : "#718096",
            fontWeight: activeTab === "goals" ? 600 : 400,
            fontSize: "1rem",
            cursor: "pointer",
            borderBottom: activeTab === "goals" ? "3px solid #4299e1" : "3px solid transparent",
            transition: "all 0.2s ease",
          }}
        >
          Sparmål
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("transactions")}
          style={{
            flex: 1,
            padding: "12px 16px",
            border: "none",
            background: "transparent",
            color: activeTab === "transactions" ? "#2d3748" : "#718096",
            fontWeight: activeTab === "transactions" ? 600 : 400,
            fontSize: "1rem",
            cursor: "pointer",
            borderBottom: activeTab === "transactions" ? "3px solid #4299e1" : "3px solid transparent",
            transition: "all 0.2s ease",
          }}
        >
          Transaktioner
        </button>
      </div>

      {/* Tab Content */}
      {activeTab === "overview" && (
        <div>
          {/* Balance Card */}
          <section className="card" style={{ marginBottom: "20px" }}>
            <h3 style={{ marginTop: 0, marginBottom: "16px" }}>Saldo</h3>
            <div
              style={{
                textAlign: "center",
                padding: "24px",
                background: "linear-gradient(135deg, rgba(72, 187, 120, 0.1) 0%, rgba(56, 161, 105, 0.1) 100%)",
                borderRadius: "12px",
              }}
            >
              <p style={{ margin: "0 0 8px", fontSize: "1rem", color: "#6b6b6b" }}>Totalt saldo</p>
              <p style={{ margin: 0, fontSize: "3rem", fontWeight: 700, color: "#2d5a2d" }}>
                {balance?.balance || 0} kr
              </p>
            </div>
            <button
              type="button"
              onClick={() => setShowExpenseDialog(true)}
              style={{
                width: "100%",
                marginTop: "16px",
                padding: "12px",
                background: "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
                color: "white",
                border: "none",
                borderRadius: "8px",
                fontSize: "1rem",
                fontWeight: 600,
                cursor: "pointer",
              }}
            >
              Registrera köp
            </button>
          </section>

          {/* Active Savings Goals */}
          <section className="card">
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
              <h3 style={{ margin: 0 }}>Aktiva Sparmål</h3>
            </div>
            {savingsGoals.filter((g) => g.isActive && !g.isCompleted).length === 0 ? (
              <p style={{ color: "#6b6b6b" }}>Inga aktiva sparmål</p>
            ) : (
              savingsGoals
                .filter((g) => g.isActive && !g.isCompleted)
                .map((goal) => (
                  <div
                    key={goal.id}
                    style={{
                      marginBottom: "16px",
                      padding: "16px",
                      background: "rgba(72, 187, 120, 0.05)",
                      borderRadius: "8px",
                    }}
                  >
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        marginBottom: "8px",
                        fontSize: "1rem",
                        fontWeight: 600,
                      }}
                    >
                      <span>
                        {goal.emoji ? `${goal.emoji} ` : ""}
                        {goal.name}
                      </span>
                      <span>
                        {goal.currentAmount} / {goal.targetAmount} kr
                      </span>
                    </div>
                    <div
                      style={{
                        width: "100%",
                        height: "24px",
                        background: "rgba(200, 190, 180, 0.2)",
                        borderRadius: "12px",
                        overflow: "hidden",
                        marginBottom: "4px",
                      }}
                    >
                      <div
                        style={{
                          width: `${goal.progressPercentage}%`,
                          height: "100%",
                          background: "linear-gradient(90deg, rgba(72, 187, 120, 0.8) 0%, rgba(56, 161, 105, 1) 100%)",
                          borderRadius: "12px",
                          transition: "width 0.3s ease",
                        }}
                      />
                    </div>
                    <p style={{ margin: 0, fontSize: "0.875rem", color: "#6b6b6b", textAlign: "right" }}>
                      {goal.remainingAmount} kr kvar
                    </p>
                  </div>
                ))
            )}
          </section>
        </div>
      )}

      {activeTab === "goals" && (
        <div>
          {/* Allocate to Goals Button */}
          {balance && balance.balance > 0 && (
            <section className="card" style={{ marginBottom: "20px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <h3 style={{ margin: "0 0 4px", fontSize: "1rem", fontWeight: 600 }}>Fördela pengar</h3>
                  <p style={{ margin: 0, fontSize: "0.875rem", color: "#6b6b6b" }}>
                    Du har {balance.balance} kr på kontot
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setShowAllocateDialog(true)}
                  style={{
                    padding: "10px 20px",
                    background: "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
                    color: "white",
                    border: "none",
                    borderRadius: "8px",
                    fontSize: "0.875rem",
                    fontWeight: 600,
                    cursor: "pointer",
                  }}
                >
                  Fördela till mål
                </button>
              </div>
            </section>
          )}

          <section className="card" style={{ marginBottom: "20px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
              <h3 style={{ margin: 0 }}>Mina Sparmål</h3>
              <button
                type="button"
                onClick={() => setShowCreateGoalDialog(true)}
                style={{
                  padding: "8px 16px",
                  background: "#4299e1",
                  color: "white",
                  border: "none",
                  borderRadius: "8px",
                  fontSize: "0.875rem",
                  fontWeight: 600,
                  cursor: "pointer",
                }}
              >
                + Skapa nytt mål
              </button>
            </div>
            {savingsGoals.length === 0 ? (
              <p style={{ color: "#6b6b6b" }}>Inga sparmål ännu</p>
            ) : (
              savingsGoals.map((goal) => (
                <div
                  key={goal.id}
                  style={{
                    marginBottom: "16px",
                    padding: "16px",
                    background: goal.isCompleted ? "rgba(72, 187, 120, 0.1)" : "rgba(72, 187, 120, 0.05)",
                    borderRadius: "8px",
                    border: goal.isPurchased ? "2px solid #48bb78" : "1px solid #e2e8f0",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "8px" }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "4px" }}>
                        <span style={{ fontSize: "1.25rem" }}>{goal.emoji || "🎯"}</span>
                        <span style={{ fontSize: "1rem", fontWeight: 600 }}>{goal.name}</span>
                        {goal.isCompleted && (
                          <span
                            style={{
                              padding: "2px 8px",
                              background: "#48bb78",
                              color: "white",
                              borderRadius: "4px",
                              fontSize: "0.75rem",
                              fontWeight: 600,
                            }}
                          >
                            ✓ Klar
                          </span>
                        )}
                        {goal.isPurchased && (
                          <span
                            style={{
                              padding: "2px 8px",
                              background: "#2d5a2d",
                              color: "white",
                              borderRadius: "4px",
                              fontSize: "0.75rem",
                              fontWeight: 600,
                            }}
                          >
                            🛒 Köpt
                          </span>
                        )}
                      </div>
                      <p style={{ margin: "4px 0", fontSize: "0.875rem", color: "#6b6b6b" }}>
                        {goal.currentAmount} / {goal.targetAmount} kr ({goal.progressPercentage}%)
                      </p>
                    </div>
                    {!goal.isPurchased && (
                      <button
                        type="button"
                        onClick={() => handleDeleteGoal(goal.id)}
                        style={{
                          padding: "4px 8px",
                          background: "#fee",
                          color: "#c53030",
                          border: "1px solid #fcc",
                          borderRadius: "4px",
                          fontSize: "0.75rem",
                          cursor: "pointer",
                        }}
                      >
                        Ta bort
                      </button>
                    )}
                  </div>
                  {!goal.isCompleted && (
                    <div
                      style={{
                        width: "100%",
                        height: "20px",
                        background: "rgba(200, 190, 180, 0.2)",
                        borderRadius: "10px",
                        overflow: "hidden",
                      }}
                    >
                      <div
                        style={{
                          width: `${goal.progressPercentage}%`,
                          height: "100%",
                          background: "linear-gradient(90deg, rgba(72, 187, 120, 0.8) 0%, rgba(56, 161, 105, 1) 100%)",
                          borderRadius: "10px",
                        }}
                      />
                    </div>
                  )}
                </div>
              ))
            )}
          </section>
        </div>
      )}

      {activeTab === "transactions" && (
        <div>
          <section className="card">
            <h3 style={{ marginTop: 0, marginBottom: "16px" }}>Transaktionshistorik</h3>
            {transactions.length === 0 ? (
              <p style={{ color: "#6b6b6b" }}>Inga transaktioner ännu</p>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                {transactions.map((transaction) => {
                  const isExpense = transaction.amount < 0;
                  const isSavingsAllocation = transaction.transactionType === "SAVINGS_ALLOCATION";
                  const categoryName = getCategoryName(transaction.categoryId);
                  const date = new Date(transaction.createdAt).toLocaleDateString("sv-SE", {
                    year: "numeric",
                    month: "short",
                    day: "numeric",
                  });
                  const time = new Date(transaction.createdAt).toLocaleTimeString("sv-SE", {
                    hour: "2-digit",
                    minute: "2-digit",
                  });

                  // Use different color for savings allocation (blue-ish)
                  const bgColor = isSavingsAllocation
                    ? "rgba(66, 153, 225, 0.1)"
                    : isExpense
                    ? "rgba(254, 202, 202, 0.1)"
                    : "rgba(184, 230, 184, 0.1)";
                  const borderColor = isSavingsAllocation
                    ? "#4299e1"
                    : isExpense
                    ? "#c53030"
                    : "#48bb78";
                  const textColor = isSavingsAllocation
                    ? "#4299e1"
                    : isExpense
                    ? "#c53030"
                    : "#48bb78";

                  return (
                    <div
                      key={transaction.id}
                      style={{
                        padding: "12px",
                        background: bgColor,
                        borderRadius: "8px",
                        borderLeft: `4px solid ${borderColor}`,
                      }}
                    >
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "4px" }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "4px" }}>
                            <span style={{ fontSize: "1.25rem", fontWeight: 700, color: textColor }}>
                              {isSavingsAllocation ? "🎯" : isExpense ? "-" : "+"}
                              {Math.abs(transaction.amount)} kr
                            </span>
                            {categoryName && <span style={{ fontSize: "0.875rem", color: "#6b6b6b" }}>{categoryName}</span>}
                          </div>
                          {transaction.description && (
                            <p style={{ margin: "4px 0", fontSize: "0.875rem", color: "#4a5568" }}>{transaction.description}</p>
                          )}
                        </div>
                        <div style={{ textAlign: "right", fontSize: "0.75rem", color: "#6b6b6b" }}>
                          <div>{date}</div>
                          <div>{time}</div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      )}

      {/* Dialogs */}
      {showExpenseDialog && balance && (
        <RecordExpenseDialog
          currentBalance={balance.balance}
          onClose={() => setShowExpenseDialog(false)}
          onSuccess={() => {
            setShowExpenseDialog(false);
            void loadData();
          }}
        />
      )}

      {showCreateGoalDialog && (
        <CreateSavingsGoalDialog
          onClose={() => setShowCreateGoalDialog(false)}
          onSuccess={handleCreateGoal}
        />
      )}
      {showAllocateDialog && balance && (
        <AllocateToGoalsDialog
          currentBalance={balance.balance}
          onClose={() => setShowAllocateDialog(false)}
          onSuccess={() => {
            setShowAllocateDialog(false);
            void loadData();
          }}
        />
      )}
    </div>
  );
}
