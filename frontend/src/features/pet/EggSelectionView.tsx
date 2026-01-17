import { useState, useEffect } from "react";
import { selectEgg, getAvailableEggTypes, fetchPetHistory, PetResponse } from "../../shared/api/pets";
import { PetImage } from "./PetImage";
import { EggImage } from "./EggImage";

type EggSelectionViewProps = {
  onEggSelected: (pet: PetResponse) => void;
};

// Emoji mapping for egg types (for visual representation)
const EGG_EMOJIS: Record<string, string> = {
  blue_egg: "🔵",
  green_egg: "🟢",
  red_egg: "🔴",
  yellow_egg: "🟡",
  purple_egg: "🟣",
  orange_egg: "🟠",
  brown_egg: "🟤",
  black_egg: "⚫",
  gray_egg: "⚪",
  teal_egg: "🔷",
};

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
};

// Color mapping for each egg type
const EGG_COLORS: Record<string, { base: string; spots: string; glow: string }> = {
  blue_egg: { base: "#a8d5e2", spots: "#4a90a4", glow: "#ffd700" },
  green_egg: { base: "#c8e6c9", spots: "#66bb6a", glow: "#ffd700" },
  red_egg: { base: "#ffccbc", spots: "#ef5350", glow: "#ffd700" },
  yellow_egg: { base: "#fff9c4", spots: "#f9a825", glow: "#ffd700" },
  purple_egg: { base: "#e1bee7", spots: "#ab47bc", glow: "#ffd700" },
  orange_egg: { base: "#ffe0b2", spots: "#ff9800", glow: "#ffd700" },
  brown_egg: { base: "#d7ccc8", spots: "#8d6e63", glow: "#ffd700" },
  black_egg: { base: "#bdbdbd", spots: "#424242", glow: "#ffd700" },
  gray_egg: { base: "#e0e0e0", spots: "#757575", glow: "#ffd700" },
  teal_egg: { base: "#b2dfdb", spots: "#26a69a", glow: "#ffd700" },
};

// Map egg types to pet types (must match backend)
const EGG_TO_PET: Record<string, string> = {
  blue_egg: "dragon",
  green_egg: "cat",
  red_egg: "dog",
  yellow_egg: "bird",
  purple_egg: "rabbit",
  orange_egg: "bear",
  brown_egg: "snake",
  black_egg: "panda",
  gray_egg: "slot",
  teal_egg: "hydra",
};

// Pet names in Swedish
const PET_NAMES: Record<string, string> = {
  dragon: "Drake",
  cat: "Katt",
  dog: "Hund",
  bird: "Fågel",
  rabbit: "Kanin",
  bear: "Björn",
  snake: "Orm",
  panda: "Panda",
  slot: "Sengångare",
  hydra: "Hydra",
};

type HatchingStage = "idle" | "selecting" | "eggStage2" | "eggStage3" | "eggStage4" | "eggStage5" | "showingPet" | "namingPet";

export function EggSelectionView({ onEggSelected }: EggSelectionViewProps) {
  const [availableEggs, setAvailableEggs] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [selecting, setSelecting] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedEggType, setSelectedEggType] = useState<string | null>(null);
  const [petName, setPetName] = useState("");
  const [hatchingStage, setHatchingStage] = useState<HatchingStage>("idle");

  useEffect(() => {
    const loadEggs = async () => {
      try {
        setLoading(true);
        // Load both available eggs and pet history in parallel
        const [allEggs, petHistory] = await Promise.all([
          getAvailableEggTypes(),
          fetchPetHistory().catch(() => []) // If history fails, just use empty array
        ]);
        
        // Get all egg types that have been selected before
        const usedEggTypes = new Set(
          petHistory.map(history => history.selectedEggType)
        );
        
        // Filter out eggs that have been used before
        const availableEggs = allEggs.filter(eggType => !usedEggTypes.has(eggType));
        
        setAvailableEggs(availableEggs);
      } catch (e) {
        console.error("Error loading available eggs:", e);
        setError("Kunde inte ladda ägg. Försök igen.");
      } finally {
        setLoading(false);
      }
    };

    void loadEggs();
  }, []);

  const handleSelectEgg = (eggType: string) => {
    setSelectedEggType(eggType);
    setError(null);
  };

  const handleConfirmSelection = () => {
    if (!selectedEggType) return;
    
    setSelecting(selectedEggType);
    setError(null);
    setHatchingStage("eggStage2");

    // Animation sequence: eggStage2 -> eggStage3 -> eggStage4 -> eggStage5 -> showingPet -> namingPet
    // Slower timing for a more satisfying hatching experience
    setTimeout(() => setHatchingStage("eggStage3"), 800);
    setTimeout(() => setHatchingStage("eggStage4"), 1600);
    setTimeout(() => setHatchingStage("eggStage5"), 2400);
    // Fade to show pet
    setTimeout(() => setHatchingStage("showingPet"), 3200);
    // Show naming interface
    setTimeout(() => setHatchingStage("namingPet"), 4000);
  };

  const handleNameConfirm = async () => {
    if (!selectedEggType) return;
    
    try {
      const name = petName.trim() || null;
      const pet = await selectEgg(selectedEggType, name);
      onEggSelected(pet);
    } catch (e) {
      console.error("Error selecting egg:", e);
      setError("Kunde inte välja ägg. Försök igen.");
      setSelecting(null);
      setHatchingStage("idle");
    }
  };

  const handleCancelSelection = () => {
    setSelectedEggType(null);
    setPetName("");
    setHatchingStage("idle");
    setSelecting(null);
  };

  if (loading) {
    return (
      <div className="egg-selection-view" style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "20px",
        background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
      }}>
        <div className="card" style={{ textAlign: "center", padding: "40px" }}>
          <div style={{ fontSize: "4rem", marginBottom: "16px" }}>🥚</div>
          <p>Laddar ägg...</p>
        </div>
      </div>
    );
  }

  if (error && !availableEggs.length) {
    return (
      <div className="egg-selection-view" style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "20px",
        background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
      }}>
        <div className="card" style={{ textAlign: "center", padding: "40px" }}>
          <p className="error-text">{error}</p>
        </div>
      </div>
    );
  }

  const renderEgg = (eggType: string, isSelected: boolean = false, stage: HatchingStage = "idle") => {
    const colors = EGG_COLORS[eggType] || EGG_COLORS.blue_egg;
    const eggName = EGG_NAMES[eggType] || eggType;
    
    // Determine crack pattern based on stage
    const getCrackPattern = () => {
      if (stage === "cracking1") return "crack-pattern-1";
      if (stage === "cracking2" || stage === "hatching") return "crack-pattern-2";
      if (stage === "hatched") return "crack-pattern-3";
      return "";
    };

    return (
      <div
        key={eggType}
        style={{
          position: "relative",
          width: "160px",
          height: "200px",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "flex-end",
        }}
      >
        {/* Glowing base */}
        <div
          style={{
            position: "absolute",
            bottom: "10px",
            width: "140px",
            height: "60px",
            background: `radial-gradient(ellipse, ${colors.glow} 0%, transparent 70%)`,
            borderRadius: "50%",
            filter: "blur(15px)",
            opacity: isSelected || stage !== "idle" ? 0.8 : 0.5,
            animation: isSelected || stage !== "idle" ? "glow-pulse 2s ease-in-out infinite" : "none",
            zIndex: 0,
          }}
        />
        
        {/* Egg container */}
        <div
          style={{
            position: "relative",
            width: "120px",
            height: "160px",
            background: colors.base,
            borderRadius: "50% 50% 50% 50% / 60% 60% 40% 40%",
            boxShadow: "0 8px 16px rgba(0, 0, 0, 0.2), inset 0 -10px 20px rgba(0, 0, 0, 0.1)",
            zIndex: 1,
            transform: isSelected ? "scale(1.1)" : "scale(1)",
            transition: "transform 0.3s ease",
            overflow: "hidden",
          }}
        >
          {/* Spots pattern */}
          <div
            style={{
              position: "absolute",
              top: "15%",
              left: "20%",
              width: "20px",
              height: "25px",
              background: colors.spots,
              borderRadius: "50%",
              opacity: 0.7,
            }}
          />
          <div
            style={{
              position: "absolute",
              top: "35%",
              right: "25%",
              width: "15px",
              height: "20px",
              background: colors.spots,
              borderRadius: "50%",
              opacity: 0.7,
            }}
          />
          <div
            style={{
              position: "absolute",
              bottom: "30%",
              left: "30%",
              width: "18px",
              height: "22px",
              background: colors.spots,
              borderRadius: "50%",
              opacity: 0.7,
            }}
          />
          <div
            style={{
              position: "absolute",
              bottom: "45%",
              right: "20%",
              width: "12px",
              height: "18px",
              background: colors.spots,
              borderRadius: "50%",
              opacity: 0.7,
            }}
          />
          
          {/* Cracks overlay */}
          {stage !== "idle" && (
            <div
              className={getCrackPattern()}
              style={{
                position: "absolute",
                top: 0,
                left: 0,
                width: "100%",
                height: "100%",
                pointerEvents: "none",
              }}
            >
              <svg width="120" height="160" viewBox="0 0 120 160" style={{ position: "absolute", top: 0, left: 0 }}>
                {stage === "cracking1" && (
                  <>
                    <path d="M 40 30 Q 50 45, 60 35" stroke="#2d3748" strokeWidth="2" fill="none" opacity="0.6" />
                    <path d="M 70 25 Q 75 40, 80 30" stroke="#2d3748" strokeWidth="2" fill="none" opacity="0.6" />
                  </>
                )}
                {(stage === "cracking2" || stage === "hatching") && (
                  <>
                    <path d="M 40 30 Q 50 45, 60 35 Q 65 50, 55 65" stroke="#2d3748" strokeWidth="2.5" fill="none" opacity="0.7" />
                    <path d="M 70 25 Q 75 40, 80 30 Q 85 45, 75 60" stroke="#2d3748" strokeWidth="2.5" fill="none" opacity="0.7" />
                    <path d="M 50 50 Q 55 65, 45 75" stroke="#2d3748" strokeWidth="2" fill="none" opacity="0.6" />
                    <path d="M 75 55 Q 80 70, 70 80" stroke="#2d3748" strokeWidth="2" fill="none" opacity="0.6" />
                  </>
                )}
                {stage === "hatched" && (
                  <>
                    <path d="M 40 30 Q 50 45, 60 35 Q 65 50, 55 65 Q 50 80, 40 90" stroke="#2d3748" strokeWidth="3" fill="none" opacity="0.8" />
                    <path d="M 70 25 Q 75 40, 80 30 Q 85 45, 75 60 Q 70 75, 60 85" stroke="#2d3748" strokeWidth="3" fill="none" opacity="0.8" />
                    <path d="M 50 50 Q 55 65, 45 75 Q 40 85, 35 95" stroke="#2d3748" strokeWidth="2.5" fill="none" opacity="0.7" />
                    <path d="M 75 55 Q 80 70, 70 80 Q 65 90, 55 100" stroke="#2d3748" strokeWidth="2.5" fill="none" opacity="0.7" />
                    <path d="M 60 40 Q 65 55, 55 70" stroke="#2d3748" strokeWidth="2" fill="none" opacity="0.6" />
                  </>
                )}
              </svg>
            </div>
          )}
        </div>
        
        {/* Egg name label */}
        {!isSelected && (
          <div style={{
            marginTop: "16px",
            fontSize: "1rem",
            fontWeight: 600,
            color: "#2d3748",
            textAlign: "center",
          }}>
            {eggName}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="egg-selection-view" style={{
      minHeight: "100vh",
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      justifyContent: "center",
      padding: "20px",
      background: "radial-gradient(ellipse at top, #fff8e1 0%, #ffe0b2 50%, #ffcc80 100%)",
      position: "relative",
      overflow: "hidden",
    }}>
      {/* Sparkle particles in background */}
      <div style={{
        position: "absolute",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        pointerEvents: "none",
        opacity: 0.3,
      }}>
        {Array.from({ length: 20 }).map((_, i) => (
          <div
            key={i}
            style={{
              position: "absolute",
              left: `${Math.random() * 100}%`,
              top: `${Math.random() * 100}%`,
              width: "4px",
              height: "4px",
              background: "#ffd700",
              borderRadius: "50%",
              animation: `sparkle ${2 + Math.random() * 2}s ease-in-out infinite`,
              animationDelay: `${Math.random() * 2}s`,
            }}
          />
        ))}
      </div>

      {selectedEggType === null ? (
        <>
          <div style={{ textAlign: "center", marginBottom: "48px", maxWidth: "600px", position: "relative", zIndex: 1 }}>
            <h1 style={{ 
              fontSize: "2.5rem", 
              margin: "0 0 16px",
              color: "#2d3748",
              fontWeight: 700,
            }}>
              Välj ditt ägg! 🥚
            </h1>
            <p style={{ 
              fontSize: "1.2rem", 
              color: "#4a5568",
              margin: 0,
            }}>
              Välj ett ägg för denna månaden. Det kommer att kläckas och bli ett djur som växer medan du gör dina sysslor!
            </p>
          </div>

          {error && (
            <div className="card" style={{ 
              marginBottom: "24px", 
              background: "#fee",
              border: "1px solid #fcc",
              position: "relative",
              zIndex: 1,
            }}>
              <p className="error-text" style={{ margin: 0 }}>{error}</p>
            </div>
          )}

          <div style={{
            display: "flex",
            flexWrap: "wrap",
            gap: "40px",
            justifyContent: "center",
            alignItems: "flex-end",
            maxWidth: "900px",
            width: "100%",
            position: "relative",
            zIndex: 1,
          }}>
            {availableEggs.map((eggType) => {
              const petType = EGG_TO_PET[eggType] || "dragon";
              return (
                <button
                  key={eggType}
                  type="button"
                  onClick={() => handleSelectEgg(eggType)}
                  disabled={selecting !== null}
                  style={{
                    background: "transparent",
                    border: "none",
                    cursor: selecting !== null ? "wait" : "pointer",
                    opacity: selecting !== null ? 0.5 : 1,
                    padding: 0,
                    transition: "opacity 0.3s ease",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                  }}
                >
                  <div style={{
                    position: "relative",
                    width: "160px",
                    height: "200px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}>
                    <EggImage 
                      petType={petType} 
                      eggStage={1} 
                      size={160} 
                    />
                  </div>
                </button>
              );
            })}
          </div>
        </>
      ) : (
        <div className="card" style={{
          maxWidth: "500px",
          width: "100%",
          padding: "40px",
          textAlign: "center",
          position: "relative",
          zIndex: 1,
        }}>
          {/* Show egg during hatching animation */}
          {(hatchingStage === "idle" || hatchingStage === "eggStage2" || hatchingStage === "eggStage3" || hatchingStage === "eggStage4" || hatchingStage === "eggStage5") && (
            <div style={{
              display: "flex",
              justifyContent: "center",
              marginBottom: "32px",
              opacity: hatchingStage === "eggStage5" ? 0 : 1,
              transition: hatchingStage === "eggStage5" ? "opacity 0.5s ease-out" : "none",
            }}>
              <EggImage 
                petType={EGG_TO_PET[selectedEggType] || "dragon"} 
                eggStage={
                  hatchingStage === "idle" ? 1 :
                  hatchingStage === "eggStage2" ? 2 :
                  hatchingStage === "eggStage3" ? 3 :
                  hatchingStage === "eggStage4" ? 4 : 5
                } 
                size={200} 
              />
            </div>
          )}

          {/* Show pet after hatching with fade-in */}
          {(hatchingStage === "showingPet" || hatchingStage === "namingPet") && (
            <div style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              marginBottom: "32px",
              opacity: hatchingStage === "showingPet" ? 0 : 1,
              animation: hatchingStage === "namingPet" ? "fadeIn 0.6s ease-in" : "none",
            }}>
              <div style={{
                marginBottom: "16px",
                transform: "scale(0)",
                animation: "popIn 0.5s ease-out 0.2s forwards",
              }}>
                <PetImage petType={EGG_TO_PET[selectedEggType] || "dragon"} growthStage={1} size={160} />
              </div>
              <h3 style={{
                margin: 0,
                fontSize: "1.5rem",
                fontWeight: 700,
                color: "#2d3748",
              }}>
                En {PET_NAMES[EGG_TO_PET[selectedEggType]] || "vän"} har kläckts!
              </h3>
            </div>
          )}
          
          {hatchingStage === "idle" && (
            <>
              <h2 style={{
                margin: "0 0 32px",
                fontSize: "1.8rem",
                fontWeight: 700,
                color: "#2d3748",
              }}>
                Ditt ägg kommer att kläckas!
              </h2>

              <div style={{
                display: "flex",
                gap: "16px",
                justifyContent: "center",
              }}>
                <button
                  type="button"
                  className="button-secondary"
                  onClick={handleCancelSelection}
                  disabled={selecting !== null}
                  style={{
                    padding: "14px 28px",
                    fontSize: "1rem",
                    fontWeight: 600,
                  }}
                >
                  Tillbaka
                </button>
                <button
                  type="button"
                  className="button-primary"
                  onClick={handleConfirmSelection}
                  disabled={selecting !== null}
                  style={{
                    padding: "14px 28px",
                    fontSize: "1rem",
                    fontWeight: 600,
                  }}
                >
                  Välj detta ägg!
                </button>
              </div>
            </>
          )}

          {(hatchingStage === "eggStage2" || hatchingStage === "eggStage3" || hatchingStage === "eggStage4" || hatchingStage === "eggStage5" || hatchingStage === "showingPet") && (
            <div>
              <h2 style={{
                margin: "24px 0",
                fontSize: "1.8rem",
                fontWeight: 700,
                color: "#2d3748",
              }}>
                {hatchingStage === "eggStage5" || hatchingStage === "showingPet" ? "Grattis! Ditt ägg har kläckts! 🎉" : "Kläcker..."}
              </h2>
            </div>
          )}

          {hatchingStage === "namingPet" && (
            <>
              <div style={{ marginBottom: "24px" }}>
                <label htmlFor="pet-name" style={{
                  display: "block",
                  marginBottom: "12px",
                  fontSize: "1.1rem",
                  fontWeight: 600,
                  color: "#4a5568",
                  textAlign: "left",
                }}>
                  Vad ska ditt {PET_NAMES[EGG_TO_PET[selectedEggType]]?.toLowerCase() || "djur"} heta? (valfritt)
                </label>
                <input
                  id="pet-name"
                  type="text"
                  value={petName}
                  onChange={(e) => setPetName(e.target.value)}
                  placeholder="T.ex. Fluff, Pelle, Luna..."
                  maxLength={100}
                  autoFocus
                  style={{
                    width: "100%",
                    padding: "16px",
                    fontSize: "1.1rem",
                    border: "2px solid #e2e8f0",
                    borderRadius: "12px",
                    outline: "none",
                    transition: "border-color 0.2s",
                  }}
                  onFocus={(e) => {
                    e.currentTarget.style.borderColor = "#667eea";
                  }}
                  onBlur={(e) => {
                    e.currentTarget.style.borderColor = "#e2e8f0";
                  }}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      handleNameConfirm();
                    }
                  }}
                />
              </div>

              <div style={{
                display: "flex",
                gap: "16px",
                justifyContent: "center",
              }}>
                <button
                  type="button"
                  className="button-primary"
                  onClick={handleNameConfirm}
                  style={{
                    padding: "14px 28px",
                    fontSize: "1rem",
                    fontWeight: 600,
                  }}
                >
                  Klar!
                </button>
              </div>
            </>
          )}

          {error && (
            <div style={{
              marginTop: "24px",
              padding: "12px",
              background: "#fee",
              border: "1px solid #fcc",
              borderRadius: "8px",
            }}>
              <p className="error-text" style={{ margin: 0 }}>{error}</p>
            </div>
          )}
        </div>
      )}

      <style>{`
        @keyframes glow-pulse {
          0%, 100% { opacity: 0.5; transform: scale(1); }
          50% { opacity: 0.8; transform: scale(1.1); }
        }
        
        @keyframes sparkle {
          0%, 100% { opacity: 0; transform: scale(0); }
          50% { opacity: 1; transform: scale(1); }
        }

        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }

        @keyframes popIn {
          0% { transform: scale(0); opacity: 0; }
          50% { transform: scale(1.2); opacity: 0.8; }
          100% { transform: scale(1); opacity: 1; }
        }
      `}</style>
    </div>
  );
}
