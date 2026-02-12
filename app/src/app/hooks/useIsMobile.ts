import { useEffect, useState } from "react";

export default function useIsMobile(maxWidthPx = 900): boolean {
  const [isMobile, setIsMobile] = useState(() => {
    if (typeof window === "undefined") return false;
    return window.matchMedia(`(max-width: ${maxWidthPx}px)`).matches;
  });

  useEffect(() => {
    const mql = window.matchMedia(`(max-width: ${maxWidthPx}px)`);
    const handler = () => setIsMobile(mql.matches);
    handler();
    mql.addEventListener("change", handler);
    return () => mql.removeEventListener("change", handler);
  }, [maxWidthPx]);

  return isMobile;
}
