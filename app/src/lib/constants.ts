import type { Settings, Taxonomy } from "./types";

export const DEFAULT_TAXONOMY: Taxonomy = {
  lvl1: "",
  lvl2: "",
  lvl3: "",
  lvl4: "",
  lvl5: "",
};

export const DEFAULT_SETTINGS: Settings = {
  defaultVOrigL: 20,
  api1: {
    name: "API 1",
    baseUrl: "",
    apiKey: "",
    model: "",
  },
  api2: {
    name: "API 2",
    baseUrl: "",
    apiKey: "",
    model: "",
  },
};

