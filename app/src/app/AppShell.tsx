import { useEffect, useMemo, useState } from "react";
import { calcDataset } from "../lib/calc";
import { DEFAULT_SETTINGS } from "../lib/constants";
import { createDefaultDataset, touchDataset } from "../lib/dataset";
import { loadDataset, loadSettings, saveDataset, saveSettings } from "../lib/storage";
import type { Dataset, Id, Settings, Species, Taxonomy } from "../lib/types";
import NavTabs from "./components/NavTabs";
import TopBar from "./components/TopBar";
import SettingsDialog from "./dialogs/SettingsDialog";
import TaxonomyDialog from "./dialogs/TaxonomyDialog";
import WetWeightDialog from "./dialogs/WetWeightDialog";
import WetWeightLibraryDialog from "./dialogs/WetWeightLibraryDialog";
import { WetWeightsProvider } from "./contexts/WetWeightsContext";
import PointsView from "./views/PointsView";
import PreviewView from "./views/PreviewView";
import SpeciesView from "./views/SpeciesView";

export type View = "points" | "species" | "preview";

export default function AppShell() {
  const [view, setView] = useState<View>("points");
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS);
  const [dataset, setDataset] = useState<Dataset>(() => createDefaultDataset());

  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isWetWeightLibraryOpen, setIsWetWeightLibraryOpen] = useState(false);
  const [wetWeightSpeciesId, setWetWeightSpeciesId] = useState<Id | null>(null);
  const [taxonomySpeciesId, setTaxonomySpeciesId] = useState<Id | null>(null);

  useEffect(() => {
    void (async () => {
      const [loadedSettings, loadedDataset] = await Promise.all([loadSettings(), loadDataset()]);
      setSettings(loadedSettings);
      setDataset(loadedDataset ?? createDefaultDataset());
    })();
  }, []);

  useEffect(() => {
    const handle = setTimeout(() => void saveSettings(settings), 400);
    return () => clearTimeout(handle);
  }, [settings]);

  useEffect(() => {
    const handle = setTimeout(() => void saveDataset(dataset), 400);
    return () => clearTimeout(handle);
  }, [dataset]);

  const calc = useMemo(() => calcDataset(dataset), [dataset]);

  const wetWeightSpecies = useMemo(
    () => dataset.species.find((s) => s.id === wetWeightSpeciesId) ?? null,
    [dataset.species, wetWeightSpeciesId],
  );

  const taxonomySpecies = useMemo(
    () => dataset.species.find((s) => s.id === taxonomySpeciesId) ?? null,
    [dataset.species, taxonomySpeciesId],
  );

  function updateDataset(updater: (current: Dataset) => Dataset): void {
    setDataset((current) => touchDataset(updater(current)));
  }

  function updateSpecies(speciesId: Id, updater: (s: Species) => Species): void {
    updateDataset((current) => ({
      ...current,
      species: current.species.map((s) => (s.id === speciesId ? updater(s) : s)),
    }));
  }

  return (
    <WetWeightsProvider>
      <div className="app">
        <TopBar
          titlePrefix={dataset.titlePrefix}
          onTitlePrefixChange={(titlePrefix) => updateDataset((d) => ({ ...d, titlePrefix }))}
          onOpenWetWeightLibrary={() => setIsWetWeightLibraryOpen(true)}
          onOpenSettings={() => setIsSettingsOpen(true)}
        />

        <NavTabs value={view} onChange={setView} />

        <main className="main">
          {view === "points" && <PointsView dataset={dataset} settings={settings} onChange={updateDataset} />}
          {view === "species" && (
            <SpeciesView
              dataset={dataset}
              onChange={updateDataset}
              onOpenWetWeight={(speciesId) => setWetWeightSpeciesId(speciesId)}
              onOpenTaxonomy={(speciesId) => setTaxonomySpeciesId(speciesId)}
            />
          )}
          {view === "preview" && (
            <PreviewView dataset={dataset} calc={calc} onOpenWetWeight={(speciesId) => setWetWeightSpeciesId(speciesId)} />
          )}
        </main>

        {isSettingsOpen && (
          <SettingsDialog value={settings} onClose={() => setIsSettingsOpen(false)} onChange={(next) => setSettings(next)} />
        )}

        {isWetWeightLibraryOpen && <WetWeightLibraryDialog onClose={() => setIsWetWeightLibraryOpen(false)} />}

        {wetWeightSpecies && (
          <WetWeightDialog
            settings={settings}
            species={wetWeightSpecies}
            onClose={() => setWetWeightSpeciesId(null)}
            onApply={(wetWeightMg) => {
              updateSpecies(wetWeightSpecies.id, (s) => ({ ...s, avgWetWeightMg: wetWeightMg }));
              setWetWeightSpeciesId(null);
            }}
          />
        )}

        {taxonomySpecies && (
          <TaxonomyDialog
            species={taxonomySpecies}
            onClose={() => setTaxonomySpeciesId(null)}
            onApply={(next: { nameLatin: string; taxonomy: Taxonomy }) => {
              updateSpecies(taxonomySpecies.id, (s) => ({ ...s, ...next }));
              setTaxonomySpeciesId(null);
            }}
          />
        )}
      </div>
    </WetWeightsProvider>
  );
}
