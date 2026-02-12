import { useMemo, useState } from "react";
import type { Dataset, Id, Species, Taxonomy } from "../../lib/types";
import { createBlankSpecies } from "../../lib/dataset";
import { newId } from "../../lib/id";
import SpeciesLibrary from "../components/SpeciesLibrary";
import { useWetWeights } from "../contexts/WetWeightsContext";
import { anyCountPositive, clampNonNegativeInt, parseNullableNumber } from "../utils";

export default function SpeciesViewMobile({
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
  const [activePointId, setActivePointId] = useState<Id>(() => dataset.points[0]?.id ?? "");
  const [expandedSpeciesId, setExpandedSpeciesId] = useState<Id | null>(null);
  const { findByNameCn } = useWetWeights();
  const effectiveActivePointId = useMemo(() => {
    if (dataset.points.some((p) => p.id === activePointId)) return activePointId;
    return dataset.points[0]?.id ?? "";
  }, [dataset.points, activePointId]);

  const activePoint = useMemo(
    () => dataset.points.find((p) => p.id === effectiveActivePointId) ?? dataset.points[0],
    [dataset.points, effectiveActivePointId],
  );

  function addBlankSpeciesRow(): void {
    onChange((current) => {
      const sp = createBlankSpecies(current.points.map((p) => p.id));
      return { ...current, species: [...current.species, sp] };
    });
  }

  function deleteSpecies(speciesId: Id): void {
    onChange((current) => ({ ...current, species: current.species.filter((s) => s.id !== speciesId) }));
    if (expandedSpeciesId === speciesId) setExpandedSpeciesId(null);
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

  if (!activePoint) return null;

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>物种与计数</h2>
        <button className="btn primary" onClick={addBlankSpeciesRow}>
          新增物种
        </button>
      </div>

      <div className="card subtle">
        <div className="strong">当前采样点：{activePoint.label}</div>
        <div className="chips">
          {dataset.points.map((p) => (
            <button
              key={p.id}
              className={p.id === effectiveActivePointId ? "chip active" : "chip"}
              onClick={() => setActivePointId(p.id)}
            >
              {p.label || "未命名"}
            </button>
          ))}
        </div>
        <div className="hint">在手机上建议按采样点逐个录入计数：先选点位，再对每个物种点 +/−。</div>
      </div>

      <SpeciesLibrary onAdd={addSpeciesFromLibrary} />

      {dataset.species.length === 0 && <div className="muted">还没有物种。可从表三搜索添加或新增物种。</div>}

      {dataset.species.map((s) => {
        const count = s.countsByPointId[effectiveActivePointId] ?? 0;
        const missingWetWeight = s.avgWetWeightMg == null && anyCountPositive(s);
        const expanded = expandedSpeciesId === s.id;

        return (
          <div key={s.id} className="card speciesCard">
            <div className="row space-between">
              <div>
                <div className="strong">{s.nameCn || "（未命名物种）"}</div>
                {!!s.nameLatin && <div className="muted small">{s.nameLatin}</div>}
              </div>
              <button className="btn" onClick={() => setExpandedSpeciesId(expanded ? null : s.id)}>
                {expanded ? "收起" : "编辑"}
              </button>
            </div>

            <div className="row space-between" style={{ marginTop: 10 }}>
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
                        [effectiveActivePointId]: clampNonNegativeInt(
                          (sp.countsByPointId[effectiveActivePointId] ?? 0) - 1,
                        ),
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
                        [effectiveActivePointId]: clampNonNegativeInt(Number(e.target.value)),
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
                        [effectiveActivePointId]: clampNonNegativeInt(
                          (sp.countsByPointId[effectiveActivePointId] ?? 0) + 1,
                        ),
                      },
                    }))
                  }
                >
                  +
                </button>
              </div>

              {missingWetWeight ? (
                <button className="btn" onClick={() => onOpenWetWeight(s.id)}>
                  未查到湿重 · 点击处理
                </button>
              ) : (
                <span className="badge">计数</span>
              )}
            </div>

            {expanded && (
              <>
                <div className="divider" />

                <label className="field">
                  <div className="label">物种名称</div>
                  <input
                    className="input"
                    value={s.nameCn}
                    placeholder="例如：瓶砂壳虫"
                    onChange={(e) => updateSpecies(s.id, (sp) => ({ ...sp, nameCn: e.target.value }))}
                  />
                </label>

                <label className="field">
                  <div className="label">平均湿重（mg/个）</div>
                  <input
                    className="input"
                    inputMode="decimal"
                    placeholder={missingWetWeight ? "未查到湿重" : "例如：0.0005"}
                    value={s.avgWetWeightMg ?? ""}
                    onChange={(e) =>
                      updateSpecies(s.id, (sp) => ({ ...sp, avgWetWeightMg: parseNullableNumber(e.target.value) }))
                    }
                  />
                </label>

                <div className="row space-between">
                  <div className="row gap-sm">
                    <button className="btn" onClick={() => onOpenTaxonomy(s.id)}>
                      编辑分类/拉丁名
                    </button>
                    <button className="btn" onClick={() => autofillFromTable3(s.id)}>
                      从表三补齐
                    </button>
                  </div>
                  <button className="btn danger" onClick={() => deleteSpecies(s.id)}>
                    删除物种
                  </button>
                </div>
              </>
            )}
          </div>
        );
      })}
    </section>
  );
}
