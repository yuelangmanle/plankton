import type { Dataset, Id, Point, Species } from "./types";

export type BiomassCell = number | null | "MISSING_WET_WEIGHT";

export type PointIndex = {
  pointId: Id;
  label: string;
  vConcMl: number | null;
  vOrigL: number;
  totalCount: number;
  speciesCountS: number;
  H: number | null;
  J: number | null;
  D: number | null;
};

export type PerPointSpeciesCalc = {
  count: number;
  density: number | null;
  biomass: BiomassCell;
  p: number | null;
  pLnP: number | null;
  Y: number | null;
  isDominant: boolean | null;
};

export type DatasetCalc = {
  pointIndexById: Record<Id, PointIndex>;
  fiBySpeciesId: Record<Id, number>;
  perSpeciesByPoint: Record<Id, Record<Id, PerPointSpeciesCalc>>;
};

export function safeNumber(value: unknown): number | null {
  if (typeof value !== "number") return null;
  if (Number.isNaN(value)) return null;
  if (!Number.isFinite(value)) return null;
  return value;
}

export function calcDensityIndPerL(
  count: number,
  vConcMl: number | null,
  vOrigL: number,
): number | null {
  if (count <= 0) return 0;
  const conc = safeNumber(vConcMl);
  if (conc == null) return null;
  if (vOrigL <= 0) return null;
  return (count / 1.3) * (conc / vOrigL);
}

export function calcBiomassMgPerL(
  count: number,
  density: number | null,
  wetWeightMg: number | null,
): BiomassCell {
  if (count <= 0) return 0;
  if (density == null) return null;
  if (wetWeightMg == null) return "MISSING_WET_WEIGHT";
  return density * wetWeightMg;
}

export function calcFiBySpeciesId(dataset: Dataset): Record<Id, number> {
  const pointCount = dataset.points.length;
  const result: Record<Id, number> = {};
  for (const sp of dataset.species) {
    if (pointCount <= 0) {
      result[sp.id] = 0;
      continue;
    }
    let present = 0;
    for (const point of dataset.points) {
      const count = sp.countsByPointId[point.id] ?? 0;
      if (count > 0) present += 1;
    }
    result[sp.id] = present / pointCount;
  }
  return result;
}

export function calcPointTotals(dataset: Dataset, point: Point): { totalCount: number; S: number } {
  let totalCount = 0;
  let S = 0;
  for (const sp of dataset.species) {
    const count = sp.countsByPointId[point.id] ?? 0;
    totalCount += count;
    if (count > 0) S += 1;
  }
  return { totalCount, S };
}

export function calcH(dataset: Dataset, point: Point, totalCount: number): number | null {
  if (totalCount <= 0) return null;
  let sum = 0;
  for (const sp of dataset.species) {
    const n = sp.countsByPointId[point.id] ?? 0;
    if (n <= 0) continue;
    const p = n / totalCount;
    if (p <= 0) continue;
    sum += p * Math.log(p);
  }
  return -sum;
}

export function calcJ(H: number | null, S: number): number | null {
  if (H == null) return null;
  if (S <= 1) return null;
  return H / Math.log(S);
}

export function calcD(totalCount: number, S: number): number | null {
  if (S <= 1) return null;
  if (totalCount <= 1) return null;
  return (S - 1) / Math.log(totalCount);
}

export function calcDataset(dataset: Dataset): DatasetCalc {
  const pointIndexById: Record<Id, PointIndex> = {};
  const fiBySpeciesId = calcFiBySpeciesId(dataset);

  const perSpeciesByPoint: Record<Id, Record<Id, PerPointSpeciesCalc>> = {};
  for (const sp of dataset.species) perSpeciesByPoint[sp.id] = {};

  // Precompute per-point totals to avoid recompute.
  const totalsByPointId: Record<Id, { totalCount: number; S: number }> = {};
  for (const point of dataset.points) totalsByPointId[point.id] = calcPointTotals(dataset, point);

  for (const point of dataset.points) {
    const { totalCount, S } = totalsByPointId[point.id];
    const H = calcH(dataset, point, totalCount);
    const J = calcJ(H, S);
    const D = calcD(totalCount, S);

    pointIndexById[point.id] = {
      pointId: point.id,
      label: point.label,
      vConcMl: point.vConcMl,
      vOrigL: point.vOrigL,
      totalCount,
      speciesCountS: S,
      H,
      J,
      D,
    };
  }

  for (const sp of dataset.species) {
    const fi = fiBySpeciesId[sp.id] ?? 0;
    for (const point of dataset.points) {
      const pointTotals = totalsByPointId[point.id];
      const count = sp.countsByPointId[point.id] ?? 0;
      const density = calcDensityIndPerL(count, point.vConcMl, point.vOrigL);
      const biomass = calcBiomassMgPerL(count, density, sp.avgWetWeightMg);

      const totalCount = pointTotals.totalCount;
      const p = totalCount > 0 ? count / totalCount : null;
      const pLnP = p != null && p > 0 ? p * Math.log(p) : p === 0 ? 0 : null;

      const Y = totalCount > 0 && count > 0 ? (count / totalCount) * fi : null;
      const isDominant = Y != null ? Y > 0.02 : null;

      perSpeciesByPoint[sp.id][point.id] = {
        count,
        density,
        biomass,
        p,
        pLnP,
        Y,
        isDominant,
      };
    }
  }

  return { pointIndexById, fiBySpeciesId, perSpeciesByPoint };
}

export function taxonomyColumnsForSpecies(species: Species): string[] {
  const { taxonomy } = species;
  return [taxonomy.lvl1, taxonomy.lvl2, taxonomy.lvl3, taxonomy.lvl4, taxonomy.lvl5, species.nameCn];
}

