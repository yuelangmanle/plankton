export type Id = string;

export type Point = {
  id: Id;
  label: string;
  vConcMl: number | null;
  vOrigL: number;
};

export type Taxonomy = {
  lvl1: string;
  lvl2: string;
  lvl3: string;
  lvl4: string;
  lvl5: string;
};

export type Species = {
  id: Id;
  nameCn: string;
  nameLatin: string;
  taxonomy: Taxonomy;
  avgWetWeightMg: number | null;
  countsByPointId: Record<Id, number>;
};

export type Dataset = {
  id: Id;
  titlePrefix: string;
  createdAt: string;
  updatedAt: string;
  points: Point[];
  species: Species[];
};

export type ApiConfig = {
  name: string;
  baseUrl: string;
  apiKey: string;
  model: string;
};

export type Settings = {
  defaultVOrigL: number;
  api1: ApiConfig;
  api2: ApiConfig;
};

