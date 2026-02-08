import { useEffect, useState } from "react";
import { fetchAllFamilyMembers, FamilyMemberResponse } from "../../shared/api/familyMembers";
import {
  getMemberWalletBalance,
  getMemberActiveSavingsGoals,
  getMemberTransactionHistory,
  WalletBalanceResponse,
  SavingsGoalResponse,
  WalletTransactionResponse,
} from "../../shared/api/wallet";
import { GiveAllowanceDialog } from "./GiveAllowanceDialog";

type ViewKey = "dashboard" | "todos" | "schedule" | "chores" | "familymembers";

type ChildrenWalletViewProps = {
  onNavigate?: (view: ViewKey) => void;
};

type ChildWalletData = {
  member: FamilyMemberResponse;
  balance: WalletBalanceResponse | null;
  savingsGoals: SavingsGoalResponse[];
  recentTransactions: WalletTransactionResponse[];
  loading: boolean;
  error: string | null;
};

export function ChildrenWalletView({ onNavigate }: ChildrenWalletViewProps) {
  const [children, setChildren] = useState<FamilyMemberResponse[]>([]);
  const [childrenWalletData, setChildrenWalletData] = useState<Map<string, ChildWalletData>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [allowanceDialogMember, setAllowanceDialogMember] = useState<{ id: string; name: string } | null>(null);

  useEffect(() => {
    void load();
  }, []);

  const load = async () => {
    try {
      setLoading(true);
      const members = await fetchAllFamilyMembers();
      const childrenMembers = members.filter((m) => m.role === "CHILD" || m.role === "ASSISTANT");
      setChildren(childrenMembers);

      // Load wallet data for all children in parallel
      const walletDataMap = new Map<string, ChildWalletData>();

      const childDataPromises = childrenMembers.map(async (child) => {
        try {
          const [balance, goals, transactions] = await Promise.all([
            getMemberWalletBalance(child.id).catch(() => null),
            getMemberActiveSavingsGoals(child.id).catch(() => []),
            getMemberTransactionHistory(child.id, 5).catch(() => []),
          ]);

          return {
            childId: child.id,
            data: {
              member: child,
              balance,
              savingsGoals: goals,
              recentTransactions: transactions,
              loading: false,
              error: null,
            },
          };
        } catch (e) {
          return {
            childId: child.id,
            data: {
              member: child,
              balance: null,
              savingsGoals: [],
              recentTransactions: [],
              loading: false,
              error: "Kunde inte ladda wallet-data",
            },
          };
        }
      });

      const childDataResults = await Promise.all(childDataPromises);
      for (const result of childDataResults) {
        walletDataMap.set(result.childId, result.data);
      }

      setChildrenWalletData(walletDataMap);
    } catch (e) {
      console.error("Error loading children wallet data:", e);
      setError("Kunde inte ladda data.");
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: "20px" }}>
        <p>Laddar...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: "20px" }}>
        <p style={{ color: "#c53030" }}>{error}</p>
      </div>
    );
  }

  return (
    <div style={{ padding: "20px", minHeight: "100vh" }}>
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
              <h2 className="view-title" style={{ margin: 0 }}>💰 Barnens Ekonomi</h2>
            </div>
          </div>
        </div>
      </div>

      {/* Children List */}
      {children.length === 0 ? (
        <section className="card">
          <p style={{ color: "#6b6b6b" }}>Inga barn i familjen ännu</p>
        </section>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
          {children.map((child) => {
            const walletData = childrenWalletData.get(child.id);
            if (!walletData) return null;

            return (
              <section key={child.id} className="card">
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "16px" }}>
                  <h3 style={{ margin: 0, fontSize: "1.25rem", fontWeight: 600 }}>
                    👤 {child.name}
                  </h3>
                  <button
                    type="button"
                    onClick={() => setAllowanceDialogMember({ id: child.id, name: child.name })}
                    style={{
                      padding: "8px 16px",
                      background: "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
                      color: "white",
                      border: "none",
                      borderRadius: "8px",
                      fontSize: "0.875rem",
                      fontWeight: 600,
                      cursor: "pointer",
                    }}
                  >
                    Ge pengar
                  </button>
                </div>

                {walletData.error ? (
                  <p style={{ color: "#c53030", fontSize: "0.875rem" }}>{walletData.error}</p>
                ) : (
                  <>
                    {/* Balance */}
                    <div
                      style={{
                        textAlign: "center",
                        padding: "16px",
                        background: "linear-gradient(135deg, rgba(72, 187, 120, 0.1) 0%, rgba(56, 161, 105, 0.1) 100%)",
                        borderRadius: "12px",
                        marginBottom: "16px",
                      }}
                    >
                      <p style={{ margin: "0 0 4px", fontSize: "0.875rem", color: "#6b6b6b" }}>Saldo</p>
                      <p style={{ margin: 0, fontSize: "2rem", fontWeight: 700, color: "#2d5a2d" }}>
                        {walletData.balance?.balance || 0} kr
                      </p>
                    </div>

                    {/* Active Savings Goals */}
                    {walletData.savingsGoals.length > 0 && (
                      <div style={{ marginBottom: "16px" }}>
                        <h4 style={{ margin: "0 0 12px", fontSize: "1rem", fontWeight: 600 }}>
                          🎯 Aktiva Sparmål
                        </h4>
                        {walletData.savingsGoals.map((goal) => (
                          <div
                            key={goal.id}
                            style={{
                              marginBottom: "12px",
                              padding: "12px",
                              background: "rgba(72, 187, 120, 0.05)",
                              borderRadius: "8px",
                            }}
                          >
                            <div
                              style={{
                                display: "flex",
                                justifyContent: "space-between",
                                marginBottom: "4px",
                                fontSize: "0.875rem",
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
                                height: "20px",
                                background: "rgba(200, 190, 180, 0.2)",
                                borderRadius: "10px",
                                overflow: "hidden",
                                marginBottom: "4px",
                              }}
                            >
                              <div
                                style={{
                                  width: `${goal.progressPercentage}%`,
                                  height: "100%",
                                  background: "linear-gradient(90deg, rgba(72, 187, 120, 0.8) 0%, rgba(56, 161, 105, 1) 100%)",
                                  borderRadius: "10px",
                                  transition: "width 0.3s ease",
                                }}
                              />
                            </div>
                            <p style={{ margin: 0, fontSize: "0.75rem", color: "#6b6b6b", textAlign: "right" }}>
                              {goal.remainingAmount} kr kvar
                            </p>
                          </div>
                        ))}
                      </div>
                    )}

                    {/* Recent Transactions */}
                    {walletData.recentTransactions.length > 0 && (
                      <div>
                        <h4 style={{ margin: "0 0 12px", fontSize: "1rem", fontWeight: 600 }}>
                          📊 Senaste transaktioner
                        </h4>
                        <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                          {walletData.recentTransactions.slice(0, 5).map((transaction) => {
                            const isExpense = transaction.amount < 0;
                            const date = new Date(transaction.createdAt).toLocaleDateString("sv-SE", {
                              month: "short",
                              day: "numeric",
                            });

                            return (
                              <div
                                key={transaction.id}
                                style={{
                                  padding: "8px",
                                  background: isExpense ? "rgba(254, 202, 202, 0.1)" : "rgba(184, 230, 184, 0.1)",
                                  borderRadius: "6px",
                                  fontSize: "0.875rem",
                                  display: "flex",
                                  justifyContent: "space-between",
                                }}
                              >
                                <span style={{ color: isExpense ? "#c53030" : "#48bb78" }}>
                                  {isExpense ? "-" : "+"}
                                  {Math.abs(transaction.amount)} kr
                                </span>
                                <span style={{ color: "#6b6b6b" }}>{date}</span>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    )}
                  </>
                )}
              </section>
            );
          })}
        </div>
      )}

      {/* Give Allowance Dialog */}
      {allowanceDialogMember && (
        <GiveAllowanceDialog
          childName={allowanceDialogMember.name}
          childMemberId={allowanceDialogMember.id}
          onClose={() => setAllowanceDialogMember(null)}
          onSuccess={() => {
            setAllowanceDialogMember(null);
            void load();
          }}
        />
      )}
    </div>
  );
}
