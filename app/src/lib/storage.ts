import localforage from "localforage";
import { DEFAULT_SETTINGS } from "./constants";
import type { Dataset, Settings } from "./types";
import type { WetWeightEntry } from "./wetweights";

const STORE_NAME = "plankton";
localforage.config({ name: STORE_NAME });

const KEY_DATASET = "dataset.v1";
const KEY_SETTINGS = "settings.v1";
const KEY_CUSTOM_WETWEIGHTS = "wetweights.custom.v1";

type CustomWetWeightsStoreV1 = { version: 1; entries: WetWeightEntry[] };

export async function loadSettings(): Promise<Settings> {
  const value = await localforage.getItem<Settings>(KEY_SETTINGS);
  return value ?? DEFAULT_SETTINGS;
}

export async function saveSettings(settings: Settings): Promise<void> {
  await localforage.setItem(KEY_SETTINGS, settings);
}

export async function loadDataset(): Promise<Dataset | null> {
  return await localforage.getItem<Dataset>(KEY_DATASET);
}

export async function saveDataset(dataset: Dataset): Promise<void> {
  await localforage.setItem(KEY_DATASET, dataset);
}

function sanitizeWetWeightEntry(raw: unknown): WetWeightEntry | null {
  if (typeof raw !== "object" || raw == null) return null;
  const rec = raw as Record<string, unknown>;

  const nameCn = typeof rec.nameCn === "string" ? rec.nameCn.trim() : "";
  if (!nameCn) return null;

  const wetWeightMg = typeof rec.wetWeightMg === "number" ? rec.wetWeightMg : Number.NaN;
  if (!Number.isFinite(wetWeightMg)) return null;

  const nameLatin = typeof rec.nameLatin === "string" ? rec.nameLatin : null;

  const taxonomyRaw = rec.taxonomy;
  const taxonomyRec =
    typeof taxonomyRaw === "object" && taxonomyRaw != null ? (taxonomyRaw as Record<string, unknown>) : {};
  const group = typeof taxonomyRec.group === "string" ? taxonomyRec.group : null;
  const sub = typeof taxonomyRec.sub === "string" ? taxonomyRec.sub : null;

  return {
    nameCn,
    nameLatin: nameLatin && nameLatin.trim() ? nameLatin.trim() : null,
    wetWeightMg,
    taxonomy: {
      group: group && group.trim() ? group.trim() : null,
      sub: sub && sub.trim() ? sub.trim() : null,
    },
  };
}

function sanitizeWetWeightEntries(raw: unknown): WetWeightEntry[] {
  if (!Array.isArray(raw)) return [];
  const out: WetWeightEntry[] = [];
  for (const item of raw) {
    const entry = sanitizeWetWeightEntry(item);
    if (entry) out.push(entry);
  }
  return out;
}

export async function loadCustomWetWeights(): Promise<WetWeightEntry[]> {
  const value = await localforage.getItem<unknown>(KEY_CUSTOM_WETWEIGHTS);
  if (Array.isArray(value)) return sanitizeWetWeightEntries(value);
  if (typeof value === "object" && value != null) {
    const rec = value as Record<string, unknown>;
    const version = rec.version;
    const entries = rec.entries;
    if (version === 1) return sanitizeWetWeightEntries(entries);
  }
  return [];
}

export async function saveCustomWetWeights(entries: WetWeightEntry[]): Promise<void> {
  const store: CustomWetWeightsStoreV1 = { version: 1, entries };
  await localforage.setItem(KEY_CUSTOM_WETWEIGHTS, store);
}
