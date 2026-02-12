import { DEFAULT_SETTINGS, DEFAULT_TAXONOMY } from "./constants";
import { newId } from "./id";
import type { Dataset, Point, Settings, Species } from "./types";

export function nowIso(): string {
  return new Date().toISOString();
}

export function createDefaultDataset(): Dataset {
  const createdAt = nowIso();
  const point1: Point = {
    id: newId(),
    label: "1",
    vConcMl: null,
    vOrigL: DEFAULT_SETTINGS.defaultVOrigL,
  };

  return {
    id: newId(),
    titlePrefix: "",
    createdAt,
    updatedAt: createdAt,
    points: [point1],
    species: [],
  };
}

export function touchDataset(dataset: Dataset): Dataset {
  return { ...dataset, updatedAt: nowIso() };
}

export function createBlankSpecies(pointIds: string[]): Species {
  const countsByPointId: Record<string, number> = {};
  for (const pointId of pointIds) countsByPointId[pointId] = 0;

  return {
    id: newId(),
    nameCn: "",
    nameLatin: "",
    taxonomy: { ...DEFAULT_TAXONOMY },
    avgWetWeightMg: null,
    countsByPointId,
  };
}

export function createBlankPoint(settings: Settings, nextLabel: string, id: string = newId()): Point {
  return {
    id,
    label: nextLabel,
    vConcMl: null,
    vOrigL: settings.defaultVOrigL,
  };
}
