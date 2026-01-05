import { useState, useEffect } from "react";

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

export function usePwaInstall() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstallable, setIsInstallable] = useState(false);
  const [isInstalled, setIsInstalled] = useState(false);
  const [isIOS, setIsIOS] = useState(false);
  const [isStandalone, setIsStandalone] = useState(false);

  useEffect(() => {
    // Check if already installed (standalone mode)
    const isStandaloneMode = window.matchMedia("(display-mode: standalone)").matches ||
      (window.navigator as any).standalone === true;
    setIsStandalone(isStandaloneMode);
    setIsInstalled(isStandaloneMode);

    // Check if iOS
    const iOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !(window as any).MSStream;
    setIsIOS(iOS);
    
    // If not installed and not iOS, we can still show install button with instructions
    // (iOS always shows instructions, other browsers might have beforeinstallprompt)

    // Listen for beforeinstallprompt event (Chrome, Edge, etc.)
    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
      setIsInstallable(true);
    };

    window.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);

    // Check if app was just installed
    window.addEventListener("appinstalled", () => {
      setIsInstalled(true);
      setIsInstallable(false);
      setDeferredPrompt(null);
    });

    return () => {
      window.removeEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
    };
  }, []);

  const handleInstallClick = async () => {
    if (deferredPrompt) {
      // Show the install prompt
      await deferredPrompt.prompt();
      const { outcome } = await deferredPrompt.userChoice;
      
      if (outcome === "accepted") {
        setIsInstalled(true);
        setIsInstallable(false);
      }
      
      setDeferredPrompt(null);
    } else if (isIOS) {
      // For iOS, show instructions
      alert(
        "För att installera appen på iOS:\n\n" +
        "1. Tryck på delningsknappen (fyrkant med pil) längst ner\n" +
        "2. Välj \"Lägg till på hemskärmen\"\n" +
        "3. Tryck på \"Lägg till\""
      );
    } else {
      // Fallback: show browser-specific instructions
      const userAgent = navigator.userAgent.toLowerCase();
      if (userAgent.includes("chrome") || userAgent.includes("edge")) {
        alert(
          "För att installera appen:\n\n" +
          "1. Klicka på install-ikonen i adressfältet (höger sida)\n" +
          "2. Eller gå till menyn (tre prickar) → \"Installera FamilyApp\""
        );
      } else if (userAgent.includes("firefox")) {
        alert(
          "För att installera appen:\n\n" +
          "1. Klicka på install-ikonen i adressfältet\n" +
          "2. Eller gå till menyn → \"Installera\""
        );
      } else {
        alert("Din webbläsare stöder inte PWA-installation. Prova Chrome, Edge eller Firefox.");
      }
    }
  };

  return {
    isInstallable,
    isInstalled,
    isIOS,
    isStandalone,
    handleInstallClick
  };
}

