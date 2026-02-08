import { useState } from "react";

type CreateSavingsGoalDialogProps = {
  onClose: () => void;
  onSuccess: (name: string, targetAmount: number, emoji: string | null) => void;
};

const EMOJI_OPTIONS = ["🚲", "🎴", "🎮", "📱", "👕", "👟", "🎁", "🏆", "🎨", "📚", "🎵", "🎬"];

export function CreateSavingsGoalDialog({ onClose, onSuccess }: CreateSavingsGoalDialogProps) {
  const [name, setName] = useState("");
  const [targetAmount, setTargetAmount] = useState<string>("");
  const [selectedEmoji, setSelectedEmoji] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleAmountChange = (value: string) => {
    const numValue = value.replace(/\D/g, "");
    setTargetAmount(numValue);
  };

  const handleSubmit = () => {
    setError(null);

    if (!name.trim()) {
      setError("Namn krävs");
      return;
    }

    const amount = parseInt(targetAmount) || 0;
    if (amount <= 0) {
      setError("Målbeloppet måste vara större än 0");
      return;
    }

    onSuccess(name.trim(), amount, selectedEmoji);
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
          maxWidth: "400px",
          width: "100%",
          boxShadow: "0 4px 20px rgba(0, 0, 0, 0.15)",
          display: "flex",
          flexDirection: "column",
          boxSizing: "border-box",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ marginTop: 0, marginBottom: "20px", fontSize: "1.25rem", fontWeight: 600 }}>
          Skapa sparmål
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

        {/* Name */}
        <div style={{ marginBottom: "16px" }}>
          <label htmlFor="goal-name" style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
            Namn *
          </label>
          <input
            id="goal-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            style={{
              width: "100%",
              padding: "12px",
              borderRadius: "8px",
              border: "1px solid #ddd",
              fontSize: "1rem",
              boxSizing: "border-box",
            }}
            placeholder="T.ex. Ny cykel"
          />
        </div>

        {/* Emoji */}
        <div style={{ marginBottom: "16px" }}>
          <label style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
            Emoji (valfritt)
          </label>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
            {EMOJI_OPTIONS.map((emoji) => (
              <button
                key={emoji}
                type="button"
                onClick={() => setSelectedEmoji(selectedEmoji === emoji ? null : emoji)}
                style={{
                  width: "40px",
                  height: "40px",
                  fontSize: "1.5rem",
                  border: selectedEmoji === emoji ? "2px solid #4299e1" : "1px solid #ddd",
                  borderRadius: "8px",
                  background: selectedEmoji === emoji ? "rgba(66, 153, 225, 0.1)" : "white",
                  cursor: "pointer",
                }}
              >
                {emoji}
              </button>
            ))}
          </div>
        </div>

        {/* Target Amount */}
        <div style={{ marginBottom: "20px" }}>
          <label htmlFor="goal-amount" style={{ display: "block", marginBottom: "8px", fontWeight: 500 }}>
            Målbelopp (kr) *
          </label>
          <input
            id="goal-amount"
            type="text"
            inputMode="numeric"
            value={targetAmount}
            onChange={(e) => handleAmountChange(e.target.value)}
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

        {/* Buttons */}
        <div style={{ display: "flex", gap: "12px" }}>
          <button
            type="button"
            onClick={onClose}
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
            Avbryt
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!name.trim() || parseInt(targetAmount) <= 0}
            style={{
              flex: 1,
              padding: "12px",
              background: !name.trim() || parseInt(targetAmount) <= 0 ? "#cbd5e0" : "linear-gradient(135deg, #48bb78 0%, #38a169 100%)",
              color: "white",
              border: "none",
              borderRadius: "8px",
              fontSize: "1rem",
              fontWeight: 600,
              cursor: !name.trim() || parseInt(targetAmount) <= 0 ? "not-allowed" : "pointer",
            }}
          >
            Spara
          </button>
        </div>
      </div>
    </div>
  );
}
