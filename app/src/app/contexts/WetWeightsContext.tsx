/* eslint-disable react-refresh/only-export-components */
import type { ReactNode } from "react";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { loadCustomWetWeights, saveCustomWetWeights } from "../../lib/storage";
import type { WetWeightEntry } from "../../lib/wetweights";
import { builtinWetWeights } from "../../lib/wetweights";

export type WetWeightsContextValue = {
  customEntries: WetWeightEntry[];
  combinedEntries: WetWeightEntry[];
  findByNameCn: (nameCn: string) => WetWeightEntry | null;
  search: (query: string, limit?: number) => WetWeightEntry[];
  upsertCustom: (entry: WetWeightEntry) => void;
  deleteCustom: (nameCn: string) => void;
};

const WetWeightsContext = createContext<WetWeightsContextValue | null>(null);

function scoreEntry(entry: WetWeightEntry, q: string, qLower: string): number {
  const cn = entry.nameCn;
  const latin = (entry.nameLatin ?? "").toLowerCase();
  let score = 0;
  if (cn === q) score += 100;
  if (cn.includes(q)) score += 50;
  if (latin.includes(qLower)) score += 20;
  if (entry.taxonomy.group?.includes(q)) score += 10;
  if (entry.taxonomy.sub?.includes(q)) score += 8;
  return score;
}

function searchInEntries(entries: WetWeightEntry[], query: string, limit: number): WetWeightEntry[] {
  const q = query.trim();
  if (!q) return entries.slice(0, Math.min(limit, entries.length));
  const qLower = q.toLowerCase();

  const hits: { score: number; entry: WetWeightEntry }[] = [];
  for (const entry of entries) {
    const score = scoreEntry(entry, q, qLower);
    if (score > 0) hits.push({ score, entry });
  }

  hits.sort((a, b) => b.score - a.score || a.entry.nameCn.localeCompare(b.entry.nameCn, "zh-Hans-CN"));
  return hits.slice(0, limit).map((h) => h.entry);
}

function normalizeEntry(input: WetWeightEntry): WetWeightEntry {
  const nameCn = input.nameCn.trim();
  const nameLatin = input.nameLatin?.trim() || null;
  const group = input.taxonomy.group?.trim() || null;
  const sub = input.taxonomy.sub?.trim() || null;
  return {
    nameCn,
    nameLatin,
    wetWeightMg: input.wetWeightMg,
    taxonomy: { group, sub },
  };
}

export function WetWeightsProvider({ children }: { children: ReactNode }) {
  const [customEntries, setCustomEntries] = useState<WetWeightEntry[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    void (async () => {
      const entries = await loadCustomWetWeights();
      setCustomEntries(entries);
      setLoaded(true);
    })();
  }, []);

  useEffect(() => {
    if (!loaded) return;
    const handle = setTimeout(() => void saveCustomWetWeights(customEntries), 400);
    return () => clearTimeout(handle);
  }, [customEntries, loaded]);

  const combinedByNameCn = useMemo(() => {
    const map = new Map<string, WetWeightEntry>();
    for (const e of builtinWetWeights) map.set(e.nameCn, e);
    for (const e of customEntries) map.set(e.nameCn, e);
    return map;
  }, [customEntries]);

  const combinedEntries = useMemo(() => {
    const customByNameCn = new Map<string, WetWeightEntry>();
    for (const e of customEntries) customByNameCn.set(e.nameCn, e);

    const customSorted = [...customByNameCn.values()].sort((a, b) => a.nameCn.localeCompare(b.nameCn, "zh-Hans-CN"));
    const builtinFiltered = builtinWetWeights.filter((e) => !customByNameCn.has(e.nameCn));

    return [...customSorted, ...builtinFiltered];
  }, [customEntries]);

  const findByNameCn = useCallback(
    (nameCn: string) => {
      if (!nameCn) return null;
      return combinedByNameCn.get(nameCn) ?? null;
    },
    [combinedByNameCn],
  );

  const search = useCallback(
    (query: string, limit = 50) => searchInEntries(combinedEntries, query, limit),
    [combinedEntries],
  );

  const upsertCustom = useCallback((raw: WetWeightEntry) => {
    const next = normalizeEntry(raw);
    if (!next.nameCn) return;
    if (!Number.isFinite(next.wetWeightMg)) return;

    setCustomEntries((current) => {
      const byNameCn = new Map<string, WetWeightEntry>();
      for (const e of current) byNameCn.set(e.nameCn, e);
      byNameCn.set(next.nameCn, next);
      return [...byNameCn.values()];
    });
  }, []);

  const deleteCustom = useCallback((nameCn: string) => {
    const key = nameCn.trim();
    if (!key) return;
    setCustomEntries((current) => current.filter((e) => e.nameCn !== key));
  }, []);

  const value = useMemo<WetWeightsContextValue>(
    () => ({
      customEntries,
      combinedEntries,
      findByNameCn,
      search,
      upsertCustom,
      deleteCustom,
    }),
    [customEntries, combinedEntries, findByNameCn, search, upsertCustom, deleteCustom],
  );

  return <WetWeightsContext.Provider value={value}>{children}</WetWeightsContext.Provider>;
}

export function useWetWeights(): WetWeightsContextValue {
  const ctx = useContext(WetWeightsContext);
  if (!ctx) throw new Error("useWetWeights must be used within WetWeightsProvider");
  return ctx;
}
