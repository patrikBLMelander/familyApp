import { WalletNotificationResponse } from "../../shared/api/wallet";

type AllowanceNotificationPopupProps = {
  notification: WalletNotificationResponse;
  onClose: () => void;
};

export function AllowanceNotificationPopup({
  notification,
  onClose,
}: AllowanceNotificationPopupProps) {
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
          borderRadius: "16px",
          padding: "32px",
          maxWidth: "400px",
          width: "100%",
          boxShadow: "0 8px 32px rgba(0, 0, 0, 0.2)",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          textAlign: "center",
          boxSizing: "border-box",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Celebration emoji */}
        <div style={{ fontSize: "4rem", marginBottom: "16px" }}>🎉</div>

        {/* Title */}
        <h3 style={{
          margin: "0 0 8px",
          fontSize: "1.5rem",
          fontWeight: 700,
          color: "#2d3748",
        }}>
          Du fick pengar!
        </h3>

        {/* Amount */}
        <div style={{
          fontSize: "2.5rem",
          fontWeight: 700,
          color: "#48bb78",
          marginBottom: "8px",
        }}>
          +{notification.amount} kr
        </div>

        {/* Description */}
        {notification.description && (
          <p style={{
            margin: "0 0 24px",
            fontSize: "1rem",
            color: "#6b6b6b",
          }}>
            {notification.description}
          </p>
        )}

        {/* OK button */}
        <button
          type="button"
          onClick={onClose}
          style={{
            padding: "12px 32px",
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
          OK
        </button>
      </div>
    </div>
  );
}
