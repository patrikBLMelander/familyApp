import { useState, useEffect } from "react";
import { fetchPetHistory, PetHistoryResponse } from "../../shared/api/pets";
import { fetchXpHistory, XpHistoryResponse } from "../../shared/api/xp";
import { PetVisualization } from "./PetVisualization";
import { getIntegratedPetImagePath, checkIntegratedImageExists, getPetNameSwedish } from "./petImageUtils";
import { getPetFoodEmoji, getPetFoodName } from "./petFoodUtils";

type ViewKey = "dashboard";

type ChildPetHistoryViewProps = {
  onNavigate?: (view: ViewKey) => void;
  childName?: string;
};

const monthNames = [
  "Januari", "Februari", "Mars", "April", "Maj", "Juni",
  "Juli", "Augusti", "September", "Oktober", "November", "December"
];

// Friendly names for egg types (in Swedish)
const EGG_NAMES: Record<string, string> = {
  blue_egg: "Blått ägg",
  green_egg: "Grönt ägg",
  red_egg: "Rött ägg",
  yellow_egg: "Gult ägg",
  purple_egg: "Lila ägg",
  orange_egg: "Orange ägg",
  brown_egg: "Brunt ägg",
  black_egg: "Svart ägg",
  gray_egg: "Grått ägg",
  teal_egg: "Turkost ägg",
  pink_egg: "Rosa ägg",
  cyan_egg: "Cyan ägg",
};

function getEggName(eggType: string): string {
  return EGG_NAMES[eggType] || eggType.replace("_egg", "").replace("_", " ");
}

export function ChildPetHistoryView({ onNavigate, childName }: ChildPetHistoryViewProps) {
  const [petHistory, setPetHistory] = useState<PetHistoryResponse[]>([]);
  const [xpHistory, setXpHistory] = useState<XpHistoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [hasIntegratedImages, setHasIntegratedImages] = useState<Map<string, boolean>>(new Map());

  useEffect(() => {
    const load = async () => {
      try {
        setLoading(true);
        setError(null);

        const [pets, xp] = await Promise.all([
          fetchPetHistory().catch(() => []),
          fetchXpHistory().catch(() => [])
        ]);

        setPetHistory(pets);
        setXpHistory(xp);

        // Check for integrated images for all pets
        const imageChecks = await Promise.all(
          pets.map(async (pet) => {
            const key = `${pet.petType}-${pet.finalGrowthStage}`;
            const exists = await checkIntegratedImageExists(pet.petType, pet.finalGrowthStage);
            return [key, exists] as [string, boolean];
          })
        );

        const imageMap = new Map<string, boolean>(imageChecks);
        setHasIntegratedImages(imageMap);
      } catch (e) {
        console.error("Error loading pet history:", e);
        setError("Kunde inte ladda historiken. Försök igen.");
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  if (loading) {
    return (
      <div className="dashboard child-dashboard" style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
      }}>
        <section className="card">
          <p>Laddar dina tidigare djur...</p>
        </section>
      </div>
    );
  }

  if (error) {
    return (
      <div className="dashboard child-dashboard" style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
      }}>
        <section className="card">
          <p className="error-text">{error}</p>
          {onNavigate && (
            <button
              type="button"
              className="button-primary"
              onClick={() => onNavigate("dashboard")}
              style={{ marginTop: "16px" }}
            >
              Tillbaka
            </button>
          )}
        </section>
      </div>
    );
  }

  // Combine pet history with XP history
  const combinedHistory = petHistory.map((pet) => {
    const matchingXp = xpHistory.find(
      (xp) => xp.year === pet.year && xp.month === pet.month
    );
    return { pet, xp: matchingXp };
  });

  // Sort by year and month (newest first)
  combinedHistory.sort((a, b) => {
    if (a.pet.year !== b.pet.year) {
      return b.pet.year - a.pet.year;
    }
    return b.pet.month - a.pet.month;
  });

  return (
    <div className="dashboard child-dashboard" style={{
      background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
      minHeight: "100vh",
      padding: "20px",
    }}>
      {/* Header */}
      <div style={{ 
        marginBottom: "24px", 
        display: "flex", 
        justifyContent: "space-between", 
        alignItems: "flex-start" 
      }}>
        <div>
          <h2 style={{ margin: "0 0 4px", fontSize: "1.5rem", fontWeight: 700, color: "#2d3748" }}>
            {childName ? `Hej ${childName}! 👋` : "Dina tidigare djur"}
          </h2>
          <p style={{ margin: 0, fontSize: "1rem", color: "#4a5568" }}>
            Här kan du se alla djur du har haft tidigare
          </p>
        </div>
        {onNavigate && (
          <button
            type="button"
            className="button-secondary"
            onClick={() => onNavigate("dashboard")}
            style={{ fontSize: "0.85rem", padding: "8px 16px" }}
          >
            Tillbaka
          </button>
        )}
      </div>

      {combinedHistory.length === 0 ? (
        <section className="card" style={{
          textAlign: "center",
          padding: "40px",
        }}>
          <div style={{ fontSize: "4rem", marginBottom: "16px" }}>🐾</div>
          <h3 style={{ margin: "0 0 8px", fontSize: "1.2rem", color: "#2d3748" }}>
            Inga tidigare djur ännu
          </h3>
          <p style={{ margin: 0, color: "#718096", fontSize: "0.95rem" }}>
            När du har haft djur i tidigare månader kommer de att visas här!
          </p>
        </section>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
          {combinedHistory.map(({ pet, xp }) => {
            const imageKey = `${pet.petType}-${pet.finalGrowthStage}`;
            const hasIntegratedImage = hasIntegratedImages.get(imageKey) || false;
            const foodEmoji = getPetFoodEmoji(pet.petType);
            const foodName = getPetFoodName(pet.petType);
            const monthName = monthNames[pet.month - 1];

            return (
              <section
                key={`${pet.year}-${pet.month}`}
                className="card"
                style={{
                  padding: 0,
                  borderRadius: "20px",
                  boxShadow: "0 4px 12px rgba(0, 0, 0, 0.1)",
                  overflow: "hidden",
                  backgroundColor: "white",
                }}
              >
                {/* Pet Image Section */}
                <div
                  style={{
                    backgroundImage: hasIntegratedImage
                      ? `url(${getIntegratedPetImagePath(pet.petType, pet.finalGrowthStage)})`
                      : undefined,
                    backgroundSize: "cover",
                    backgroundPosition: "center",
                    backgroundRepeat: "no-repeat",
                    backgroundColor: hasIntegratedImage ? "white" : "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
                    width: "100%",
                    aspectRatio: "3 / 2",
                    position: "relative",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    padding: "20px",
                  }}
                >
                  {!hasIntegratedImage && (
                    <PetVisualization
                      petType={pet.petType}
                      growthStage={pet.finalGrowthStage}
                      size="large"
                    />
                  )}
                </div>

                {/* Pet Info Section */}
                <div style={{
                  padding: "24px",
                  borderTop: "1px solid rgba(0, 0, 0, 0.1)",
                }}>
                  <div style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "flex-start",
                    marginBottom: "16px",
                  }}>
                    <div>
                      <h3 style={{
                        margin: "0 0 8px",
                        fontSize: "1.3rem",
                        fontWeight: 700,
                        color: "#2d3748",
                      }}>
                        {getPetNameSwedish(pet.petType)}
                      </h3>
                      <p style={{
                        margin: 0,
                        fontSize: "1rem",
                        color: "#4a5568",
                        fontWeight: 600,
                      }}>
                        {monthName} {pet.year}
                      </p>
                    </div>
                    <div style={{
                      textAlign: "right",
                      background: "rgba(72, 187, 120, 0.1)",
                      padding: "12px 16px",
                      borderRadius: "12px",
                      minWidth: "100px",
                    }}>
                      <div style={{
                        fontSize: "0.85rem",
                        color: "#2d5a2d",
                        fontWeight: 600,
                        marginBottom: "4px",
                      }}>
                        Växtsteg {pet.finalGrowthStage}
                      </div>
                      {xp && (
                        <>
                          <div style={{
                            fontSize: "0.9rem",
                            fontWeight: 700,
                            color: "#2d5a2d",
                            marginBottom: "4px",
                          }}>
                            Level {xp.finalLevel}
                          </div>
                          <div style={{
                            fontSize: "0.8rem",
                            color: "#4a5568",
                          }}>
                            {xp.finalXp} {foodEmoji}
                          </div>
                        </>
                      )}
                    </div>
                  </div>

                  {xp && (
                    <div style={{
                      padding: "12px",
                      background: "rgba(240, 240, 240, 0.5)",
                      borderRadius: "12px",
                      fontSize: "0.9rem",
                      color: "#4a5568",
                    }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                        <span>✅</span>
                        <span>{xp.totalTasksCompleted} sysslor klara</span>
                      </div>
                    </div>
                  )}

                  <div style={{
                    marginTop: "12px",
                    padding: "12px",
                    background: "rgba(102, 126, 234, 0.1)",
                    borderRadius: "12px",
                    fontSize: "0.85rem",
                    color: "#4a5568",
                    display: "flex",
                    alignItems: "center",
                    gap: "8px",
                  }}>
                    <span>🥚</span>
                    <span>Från {getEggName(pet.selectedEggType)}</span>
                  </div>
                </div>
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
