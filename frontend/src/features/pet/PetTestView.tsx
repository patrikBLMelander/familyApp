import { PetVisualization } from "./PetVisualization";
import { EggImage } from "./EggImage";

const PET_TYPES = ["dragon", "cat", "dog", "bird", "rabbit", "bear"];
const PET_NAMES: Record<string, string> = {
  dragon: "Drake",
  cat: "Katt",
  dog: "Hund",
  bird: "Fågel",
  rabbit: "Kanin",
  bear: "Björn",
};

export function PetTestView() {
  return (
    <div style={{
      padding: "20px",
      maxWidth: "1400px",
      margin: "0 auto",
      background: "linear-gradient(to bottom, #f5f5f5 0%, #e0e0e0 100%)",
      minHeight: "100vh",
    }}>
      <h1 style={{
        textAlign: "center",
        marginBottom: "40px",
        color: "#2d3748",
        fontSize: "2.5rem",
      }}>
        Test-vy: Alla djur och ägg
      </h1>

      {PET_TYPES.map((petType) => (
        <div
          key={petType}
          style={{
            background: "white",
            borderRadius: "20px",
            padding: "30px",
            marginBottom: "40px",
            boxShadow: "0 4px 12px rgba(0, 0, 0, 0.1)",
          }}
        >
          <h2 style={{
            fontSize: "2rem",
            marginBottom: "30px",
            color: "#2d3748",
            textAlign: "center",
          }}>
            {PET_NAMES[petType]}
          </h2>

          {/* Egg Stages */}
          <div style={{
            marginBottom: "50px",
          }}>
            <h3 style={{
              fontSize: "1.5rem",
              marginBottom: "20px",
              color: "#4a5568",
              textAlign: "center",
            }}>
              Ägg-stages (för hatching-animation)
            </h3>
            <div style={{
              display: "grid",
              gridTemplateColumns: "repeat(5, 1fr)",
              gap: "20px",
              alignItems: "center",
            }}>
              {[1, 2, 3, 4, 5].map((stage) => (
                <div
                  key={`egg-${stage}`}
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    padding: "20px",
                    background: "#fff9e6",
                    borderRadius: "15px",
                    border: "2px solid #ffe0b2",
                  }}
                >
                  <div style={{
                    marginBottom: "15px",
                    minHeight: "150px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}>
                    <EggImage
                      petType={petType}
                      eggStage={stage}
                      size={120}
                    />
                  </div>
                  <div style={{
                    fontSize: "1.1rem",
                    fontWeight: 600,
                    color: "#4a5568",
                  }}>
                    Egg Stage {stage}
                  </div>
                  <div style={{
                    fontSize: "0.85rem",
                    color: "#718096",
                    marginTop: "5px",
                  }}>
                    {stage === 1 && "Vid val"}
                    {stage >= 2 && stage <= 5 && "Hatching"}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Pet Stages */}
          <div>
            <h3 style={{
              fontSize: "1.5rem",
              marginBottom: "20px",
              color: "#4a5568",
              textAlign: "center",
            }}>
              Pet-stages (för evolution)
            </h3>
            <div style={{
              display: "grid",
              gridTemplateColumns: "repeat(5, 1fr)",
              gap: "20px",
              alignItems: "center",
            }}>
              {[1, 2, 3, 4, 5].map((stage) => (
                <div
                  key={`pet-${stage}`}
                  style={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    padding: "20px",
                    background: "#f9f9f9",
                    borderRadius: "15px",
                    border: "2px solid #e0e0e0",
                  }}
                >
                  <div style={{
                    marginBottom: "15px",
                    minHeight: "150px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}>
                    <PetVisualization
                      petType={petType}
                      growthStage={stage}
                      size="medium"
                    />
                  </div>
                  <div style={{
                    fontSize: "1.1rem",
                    fontWeight: 600,
                    color: "#4a5568",
                  }}>
                    Pet Stage {stage}
                  </div>
                  <div style={{
                    fontSize: "0.85rem",
                    color: "#718096",
                    marginTop: "5px",
                  }}>
                    {stage === 1 && "Bebis"}
                    {stage === 2 && "Ung"}
                    {stage === 3 && "Tonåring"}
                    {stage === 4 && "Vuxen"}
                    {stage === 5 && "Majestätisk"}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      ))}

      <div style={{
        marginTop: "40px",
        padding: "20px",
        background: "white",
        borderRadius: "20px",
        textAlign: "center",
      }}>
        <p style={{ color: "#718096", fontSize: "1.1rem" }}>
          Denna vy är endast för testning. Stäng denna flik när du är klar.
        </p>
        <button
          type="button"
          onClick={() => window.location.href = "/"}
          style={{
            marginTop: "20px",
            padding: "12px 24px",
            fontSize: "1rem",
            fontWeight: 600,
            background: "#4CAF50",
            color: "white",
            border: "none",
            borderRadius: "8px",
            cursor: "pointer",
          }}
        >
          Tillbaka till appen
        </button>
      </div>
    </div>
  );
}
