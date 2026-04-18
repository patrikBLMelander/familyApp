import { useState, useEffect } from "react";
import { PetVisualization } from "./PetVisualization";
import { PetImage } from "./PetImage";
import { EggImage } from "./EggImage";
import {
  getSeasonalBackgroundPath,
  getCurrentSeason,
  getIntegratedPetImagePath,
  checkStandaloneImageExists,
} from "./petImageUtils";

const ALL_PETS: { type: string; name: string; egg: string }[] = [
  { type: "dragon",   name: "Drake",       egg: "purple_egg" },
  { type: "cat",      name: "Katt",        egg: "striped_egg" },
  { type: "dog",      name: "Hund",        egg: "spotted_egg" },
  { type: "bird",     name: "Fågel",       egg: "blue_egg" },
  { type: "rabbit",   name: "Kanin",       egg: "pink_egg" },
  { type: "bear",     name: "Björn",       egg: "brown_egg" },
  { type: "snake",    name: "Orm",         egg: "green_egg" },
  { type: "panda",    name: "Panda",       egg: "black_egg" },
  { type: "slot",     name: "Sengångare",  egg: "gray_egg" },
  { type: "hydra",    name: "Hydra",       egg: "teal_egg" },
  { type: "unicorn",  name: "Enhörning",   egg: "rainbow_egg" },
  { type: "kapybara", name: "Kapybara",    egg: "orange_egg" },
  { type: "shark",    name: "Haj",         egg: "white_egg" },
  { type: "lion",     name: "Lejon",       egg: "golden_egg" },
];

const STAGE_LABELS = ["Bebis", "Ung", "Tonåring", "Vuxen", "Majestätisk"];

const seasonBg = getSeasonalBackgroundPath();
const season = getCurrentSeason();
const seasonLabel: Record<string, string> = {
  spring: "Vår", summer: "Sommar", autumn: "Höst", winter: "Vinter",
};

function useStandaloneMap() {
  const [map, setMap] = useState<Record<string, boolean>>({});
  useEffect(() => {
    const checks = ALL_PETS.flatMap(({ type }) =>
      [1, 2, 3, 4, 5].map(async (stage) => {
        const exists = await checkStandaloneImageExists(type, stage);
        setMap((prev) => ({ ...prev, [`${type}-${stage}`]: exists }));
      })
    );
    void Promise.all(checks);
  }, []);
  return map;
}

function PetCard({ type, name, egg, standaloneMap }: {
  type: string;
  name: string;
  egg: string;
  standaloneMap: Record<string, boolean>;
}) {
  const [stage, setStage] = useState(1);
  const [showEgg, setShowEgg] = useState(false);

  const hasStandalone = standaloneMap[`${type}-${stage}`] ?? false;
  const bgImage = hasStandalone
    ? `url(${seasonBg})`
    : `url(${getIntegratedPetImagePath(type, stage)})`;

  return (
    <div style={{
      background: "white",
      borderRadius: "24px",
      overflow: "hidden",
      boxShadow: "0 4px 16px rgba(0,0,0,0.10)",
      display: "flex",
      flexDirection: "column",
    }}>
      {/* Header */}
      <div style={{
        padding: "14px 18px 10px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
      }}>
        <span style={{ fontWeight: 700, fontSize: "1.1rem", color: "#1e293b" }}>
          {name}
          <span style={{ fontWeight: 400, color: "#94a3b8", fontSize: "0.8rem", marginLeft: "6px" }}>
            ({type})
          </span>
        </span>
        <button
          onClick={() => setShowEgg((v) => !v)}
          style={{
            background: showEgg ? "#fef3c7" : "#f1f5f9",
            border: "none",
            borderRadius: "12px",
            padding: "4px 12px",
            fontSize: "0.75rem",
            color: "#64748b",
            cursor: "pointer",
            fontWeight: 600,
          }}
        >
          {showEgg ? "Djur" : "Ägg"}
        </button>
      </div>

      {/* Image area */}
      {showEgg ? (
        <div style={{
          height: "300px",
          background: "#fff9e6",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          gap: "8px",
        }}>
          <EggImage petType={type} eggStage={stage} size={180} />
          <span style={{ fontSize: "0.8rem", color: "#92400e", fontWeight: 600 }}>
            {egg} · Ägg {stage}
          </span>
        </div>
      ) : (
        <div style={{
          height: "300px",
          backgroundImage: bgImage,
          backgroundSize: "cover",
          backgroundPosition: "center",
          backgroundColor: "white",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}>
          {hasStandalone && (
            <PetImage petType={type} growthStage={stage} size={220} />
          )}
        </div>
      )}

      {/* Stage navigator */}
      <div style={{
        padding: "12px 18px",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        borderTop: "1px solid #f1f5f9",
      }}>
        <button
          onClick={() => setStage((s) => Math.max(1, s - 1))}
          disabled={stage === 1}
          style={{
            width: "40px",
            height: "40px",
            borderRadius: "50%",
            border: "none",
            background: stage === 1 ? "#f1f5f9" : "#e2e8f0",
            color: stage === 1 ? "#cbd5e1" : "#475569",
            fontSize: "1.2rem",
            cursor: stage === 1 ? "default" : "pointer",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontWeight: 700,
            lineHeight: 1,
          }}
        >
          ‹
        </button>

        <div style={{ textAlign: "center" }}>
          <div style={{
            display: "flex",
            gap: "6px",
            justifyContent: "center",
            marginBottom: "4px",
          }}>
            {[1,2,3,4,5].map((s) => (
              <button
                key={s}
                onClick={() => setStage(s)}
                style={{
                  width: "8px",
                  height: "8px",
                  borderRadius: "50%",
                  border: "none",
                  padding: 0,
                  cursor: "pointer",
                  background: s === stage ? "#6366f1" : "#e2e8f0",
                  transition: "background 0.2s",
                }}
              />
            ))}
          </div>
          <span style={{ fontSize: "0.85rem", color: "#475569", fontWeight: 600 }}>
            Lv {stage} · {STAGE_LABELS[stage - 1]}
          </span>
          <span style={{ fontSize: "0.7rem", color: "#94a3b8", marginLeft: "6px" }}>
            {hasStandalone ? "🌿 säsong" : "🖼 integrerad"}
          </span>
        </div>

        <button
          onClick={() => setStage((s) => Math.min(5, s + 1))}
          disabled={stage === 5}
          style={{
            width: "40px",
            height: "40px",
            borderRadius: "50%",
            border: "none",
            background: stage === 5 ? "#f1f5f9" : "#e2e8f0",
            color: stage === 5 ? "#cbd5e1" : "#475569",
            fontSize: "1.2rem",
            cursor: stage === 5 ? "default" : "pointer",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontWeight: 700,
            lineHeight: 1,
          }}
        >
          ›
        </button>
      </div>
    </div>
  );
}

export function PetTestView() {
  const standaloneMap = useStandaloneMap();

  return (
    <div style={{
      padding: "24px 16px 48px",
      maxWidth: "900px",
      margin: "0 auto",
      minHeight: "100vh",
      background: "#f1f5f9",
      fontFamily: "system-ui, sans-serif",
    }}>
      <div style={{ textAlign: "center", marginBottom: "32px" }}>
        <h1 style={{ fontSize: "2rem", fontWeight: 700, color: "#1e293b", margin: "0 0 8px" }}>
          Djurtest
        </h1>
        <p style={{ color: "#64748b", margin: 0 }}>
          Säsong: <strong>{seasonLabel[season]}</strong> · {ALL_PETS.length} djur · tryck på pilarna för att byta nivå
        </p>
      </div>

      <div style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
        gap: "20px",
      }}>
        {ALL_PETS.map(({ type, name, egg }) => (
          <PetCard
            key={type}
            type={type}
            name={name}
            egg={egg}
            standaloneMap={standaloneMap}
          />
        ))}
      </div>

      <div style={{ textAlign: "center", padding: "24px 0 0", color: "#94a3b8", fontSize: "0.85rem" }}>
        Endast synlig i dev-läge · <a href="/" style={{ color: "#64748b" }}>Tillbaka till appen</a>
      </div>
    </div>
  );
}
