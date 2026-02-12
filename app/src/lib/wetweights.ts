import wetweightsJson from "../data/wetweights.json";

export type WetWeightEntry = {
  nameCn: string;
  nameLatin: string | null;
  wetWeightMg: number;
  taxonomy: {
    group: string | null;
    sub: string | null;
  };
};

type WetWeightsJson = {
  version: number;
  entries: WetWeightEntry[];
};

const data = wetweightsJson as WetWeightsJson;
export const builtinWetWeights: WetWeightEntry[] = data.entries;

const byNameCn = new Map<string, WetWeightEntry>();
for (const entry of data.entries) {
  byNameCn.set(entry.nameCn, entry);
}

export function findWetWeightByNameCn(nameCn: string): WetWeightEntry | null {
  if (!nameCn) return null;
  return byNameCn.get(nameCn) ?? null;
}

export function searchWetWeights(query: string, limit = 50): WetWeightEntry[] {
  const q = query.trim();
  if (!q) return data.entries.slice(0, Math.min(limit, data.entries.length));
  const qLower = q.toLowerCase();

  const hits: { score: number; entry: WetWeightEntry }[] = [];
  for (const entry of data.entries) {
    const cn = entry.nameCn;
    const latin = (entry.nameLatin ?? "").toLowerCase();
    let score = 0;
    if (cn === q) score += 100;
    if (cn.includes(q)) score += 50;
    if (latin.includes(qLower)) score += 20;
    if (entry.taxonomy.group?.includes(q)) score += 10;
    if (entry.taxonomy.sub?.includes(q)) score += 8;
    if (score > 0) hits.push({ score, entry });
  }
  hits.sort((a, b) => b.score - a.score || a.entry.nameCn.localeCompare(b.entry.nameCn, "zh-Hans-CN"));
  return hits.slice(0, limit).map((h) => h.entry);
}
