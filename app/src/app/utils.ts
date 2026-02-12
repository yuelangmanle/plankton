import type { Dataset, Species } from "../lib/types";

export function clampNonNegativeInt(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.max(0, Math.floor(value));
}

export function parseNullableNumber(raw: string): number | null {
  const v = raw.trim();
  if (!v) return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

export function nextDefaultPointLabel(points: { label: string }[]): string {
  const used = new Set(points.map((p) => p.label));
  let base = String(points.length + 1);
  while (used.has(base)) base = `${base}-1`;
  return base;
}

export function anyCountPositive(species: Species): boolean {
  for (const v of Object.values(species.countsByPointId)) {
    if ((v ?? 0) > 0) return true;
  }
  return false;
}

export function defaultFileName(prefix: string, base: string): string {
  const safePrefix = prefix.trim();
  if (!safePrefix) return base;
  return `${safePrefix}${base}`;
}

export function datasetHasAnyData(dataset: Dataset): boolean {
  if (dataset.species.length > 0) return true;
  if (dataset.points.length > 1) return true;
  return dataset.points.some((p) => p.vConcMl != null && p.vConcMl !== 0) || dataset.points.some((p) => p.vOrigL !== 20);
}
