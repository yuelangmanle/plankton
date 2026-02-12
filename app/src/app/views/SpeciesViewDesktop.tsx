import type { Dataset, Id, Species, Taxonomy } from "../../lib/types";
import { createBlankSpecies } from "../../lib/dataset";
import { newId } from "../../lib/id";
import SpeciesLibrary from "../components/SpeciesLibrary";
import { useWetWeights } from "../contexts/WetWeightsContext";
import { anyCountPositive, clampNonNegativeInt, parseNullableNumber } from "../utils";

export default function SpeciesViewDesktop({
  dataset,
  onChange,
  onOpenWetWeight,
  onOpenTaxonomy,
}: {
  dataset: Dataset;
  onChange: (updater: (d: Dataset) => Dataset) => void;
  onOpenWetWeight: (speciesId: Id) => void;
  onOpenTaxonomy: (speciesId: Id) => void;
}) {
  const { findByNameCn } = useWetWeights();

  function addBlankSpecies(): void {
    onChange((current) => {
      const sp = createBlankSpecies(current.points.map((p) => p.id));
      return { ...current, species: [...current.species, sp] };
    });
  }

  function deleteSpecies(speciesId: Id): void {
    onChange((current) => ({ ...current, species: current.species.filter((s) => s.id !== speciesId) }));
  }

  function updateSpecies(speciesId: Id, updater: (s: Species) => Species): void {
    onChange((current) => ({
      ...current,
      species: current.species.map((s) => (s.id === speciesId ? updater(s) : s)),
    }));
  }

  function addSpeciesFromLibrary(nameCn: string): void {
    const entry = findByNameCn(nameCn);
    if (!entry) return;

    onChange((current) => {
      if (current.species.some((s) => s.nameCn === entry.nameCn)) return current;

      const countsByPointId: Record<Id, number> = {};
      for (const p of current.points) countsByPointId[p.id] = 0;

      const taxonomy: Taxonomy = {
        lvl1: entry.taxonomy.group ?? "",
        lvl2: "",
        lvl3: "",
        lvl4: entry.taxonomy.sub ?? "",
        lvl5: "",
      };

      const sp: Species = {
        id: newId(),
        nameCn: entry.nameCn,
        nameLatin: entry.nameLatin ?? "",
        taxonomy,
        avgWetWeightMg: entry.wetWeightMg,
        countsByPointId,
      };

      return { ...current, species: [...current.species, sp] };
    });
  }

  function autofillFromTable3(speciesId: Id): void {
    const sp = dataset.species.find((s) => s.id === speciesId);
    if (!sp?.nameCn) return;
    const entry = findByNameCn(sp.nameCn);
    if (!entry) return;
    updateSpecies(speciesId, (s) => ({
      ...s,
      nameLatin: s.nameLatin || (entry.nameLatin ?? ""),
      avgWetWeightMg: s.avgWetWeightMg ?? entry.wetWeightMg,
      taxonomy: {
        ...s.taxonomy,
        lvl1: s.taxonomy.lvl1 || (entry.taxonomy.group ?? ""),
        lvl4: s.taxonomy.lvl4 || (entry.taxonomy.sub ?? ""),
      },
    }));
  }

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>物种与计数</h2>
        <div className="row">
          <button className="btn" onClick={addBlankSpecies}>
            新增空白物种
          </button>
        </div>
      </div>

      <SpeciesLibrary onAdd={addSpeciesFromLibrary} />

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th style={{ minWidth: 180 }}>物种名称</th>
              <th style={{ minWidth: 150 }}>平均湿重（mg/个）</th>
              {dataset.points.map((p) => (
                <th key={p.id} className="center">
                  {p.label}
                </th>
              ))}
              <th />
            </tr>
          </thead>
          <tbody>
            {dataset.species.length === 0 && (
              <tr>
                <td colSpan={3 + dataset.points.length} className="muted">
                  还没有物种。可在上方搜索表三并添加，或新增空白物种后手动填写。
                </td>
              </tr>
            )}

            {dataset.species.map((s) => {
              const missingWetWeight = s.avgWetWeightMg == null && anyCountPositive(s);
              return (
                <tr key={s.id}>
                  <td>
                    <div className="col">
                      <input
                        className="input"
                        value={s.nameCn}
                        placeholder="例如：瓶砂壳虫"
                        onChange={(e) => updateSpecies(s.id, (sp) => ({ ...sp, nameCn: e.target.value }))}
                      />
                      <div className="row gap-xs">
                        <button className="link" onClick={() => onOpenTaxonomy(s.id)}>
                          编辑分类/拉丁名
                        </button>
                        <button className="link" onClick={() => autofillFromTable3(s.id)}>
                          从表三自动补齐
                        </button>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div className="col">
                      <input
                        className="input"
                        inputMode="decimal"
                        placeholder={missingWetWeight ? "未查到湿重" : "例如：0.0005"}
                        value={s.avgWetWeightMg ?? ""}
                        onChange={(e) =>
                          updateSpecies(s.id, (sp) => ({ ...sp, avgWetWeightMg: parseNullableNumber(e.target.value) }))
                        }
                      />
                      {missingWetWeight && (
                        <div className="row gap-xs">
                          <span className="badge warn">未查到湿重</span>
                          <button className="link" onClick={() => onOpenWetWeight(s.id)}>
                            查湿重（双 API）
                          </button>
                        </div>
                      )}
                    </div>
                  </td>

                  {dataset.points.map((p) => {
                    const count = s.countsByPointId[p.id] ?? 0;
                    return (
                      <td key={p.id} className="center">
                        <div className="counter">
                          <button
                            className="btn countBtn"
                            type="button"
                            aria-label="减 1"
                            onClick={() =>
                              updateSpecies(s.id, (sp) => ({
                                ...sp,
                                countsByPointId: {
                                  ...sp.countsByPointId,
                                  [p.id]: clampNonNegativeInt((sp.countsByPointId[p.id] ?? 0) - 1),
                                },
                              }))
                            }
                          >
                            −
                          </button>
                          <input
                            className="input countInput center"
                            type="number"
                            inputMode="numeric"
                            value={count}
                            onChange={(e) =>
                              updateSpecies(s.id, (sp) => ({
                                ...sp,
                                countsByPointId: {
                                  ...sp.countsByPointId,
                                  [p.id]: clampNonNegativeInt(Number(e.target.value)),
                                },
                              }))
                            }
                          />
                          <button
                            className="btn countBtn"
                            type="button"
                            aria-label="加 1"
                            onClick={() =>
                              updateSpecies(s.id, (sp) => ({
                                ...sp,
                                countsByPointId: {
                                  ...sp.countsByPointId,
                                  [p.id]: clampNonNegativeInt((sp.countsByPointId[p.id] ?? 0) + 1),
                                },
                              }))
                            }
                          >
                            +
                          </button>
                        </div>
                      </td>
                    );
                  })}

                  <td className="cell-actions">
                    <button className="btn danger" onClick={() => deleteSpecies(s.id)}>
                      删除
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
