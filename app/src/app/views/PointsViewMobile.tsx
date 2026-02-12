import { useEffect, useMemo, useRef, useState } from "react";
import { createBlankPoint } from "../../lib/dataset";
import { newId } from "../../lib/id";
import type { Dataset, Id, Settings } from "../../lib/types";
import { nextDefaultPointLabel, parseNullableNumber } from "../utils";

export default function PointsViewMobile({
  dataset,
  settings,
  onChange,
}: {
  dataset: Dataset;
  settings: Settings;
  onChange: (updater: (d: Dataset) => Dataset) => void;
}) {
  const [activePointId, setActivePointId] = useState<Id>(() => dataset.points[0]?.id ?? "");
  const effectiveActivePointId = useMemo(() => {
    if (dataset.points.some((p) => p.id === activePointId)) return activePointId;
    return dataset.points[0]?.id ?? "";
  }, [dataset.points, activePointId]);

  const activeIndex = useMemo(
    () => dataset.points.findIndex((p) => p.id === effectiveActivePointId),
    [dataset.points, effectiveActivePointId],
  );
  const activePoint = dataset.points[activeIndex] ?? dataset.points[0];

  const vConcRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    vConcRef.current?.focus();
  }, [effectiveActivePointId]);

  function updatePoint(pointId: Id, updater: (p: Dataset["points"][number]) => Dataset["points"][number]): void {
    onChange((d) => ({
      ...d,
      points: d.points.map((p) => (p.id === pointId ? updater(p) : p)),
    }));
  }

  function addPointAndGo(next: boolean): void {
    const id = newId();
    onChange((current) => {
      const label = nextDefaultPointLabel(current.points);
      const point = createBlankPoint(settings, label, id);
      const nextSpecies = current.species.map((s) => ({
        ...s,
        countsByPointId: { ...s.countsByPointId, [point.id]: 0 },
      }));
      return { ...current, points: [...current.points, point], species: nextSpecies };
    });
    if (next) setActivePointId(id);
  }

  function goPrev(): void {
    if (activeIndex <= 0) return;
    setActivePointId(dataset.points[activeIndex - 1].id);
  }

  function goNext(autoCreate: boolean): void {
    if (activeIndex < dataset.points.length - 1) {
      setActivePointId(dataset.points[activeIndex + 1].id);
      return;
    }
    if (autoCreate) addPointAndGo(true);
  }

  function deletePoint(pointId: Id): void {
    onChange((current) => {
      if (current.points.length <= 1) return current;
      const idx = current.points.findIndex((p) => p.id === pointId);
      const points = current.points.filter((p) => p.id !== pointId);
      const species = current.species.map((s) => {
        const countsByPointId = { ...s.countsByPointId };
        delete countsByPointId[pointId];
        return { ...s, countsByPointId };
      });

      const nextActive = points[Math.max(0, Math.min(points.length - 1, idx))]?.id ?? points[0]?.id ?? "";
      queueMicrotask(() => setActivePointId(nextActive));
      return { ...current, points, species };
    });
  }

  if (!activePoint) return null;

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>采样点</h2>
        <div className="row gap-sm">
          <button className="btn" onClick={goPrev} disabled={activeIndex <= 0}>
            上一个
          </button>
          <button className="btn primary" onClick={() => goNext(true)}>
            下一条
          </button>
        </div>
      </div>

      <div className="hint">
        建议：按顺序录入每条采样点的“浓缩体积（mL）”。输入后可按键盘“下一步/Enter”或点“下一条”跳到下一条（末尾会自动新增）。点位名称可自定义；原水体积默认 20 L，可修改。
      </div>

      <div className="card">
        <div className="row space-between">
          <div className="strong">
            第 {Math.max(1, activeIndex + 1)} / {dataset.points.length} 条
          </div>
          <button className="btn" onClick={() => addPointAndGo(true)}>
            新增一条
          </button>
        </div>

        <div className="grid-2">
          <label className="field">
            <div className="label">名称</div>
            <input
              className="input"
              value={activePoint.label}
              onChange={(e) => updatePoint(activePoint.id, (p) => ({ ...p, label: e.target.value }))}
            />
          </label>

          <label className="field">
            <div className="label">原水体积（L）</div>
            <input
              className="input"
              type="number"
              inputMode="decimal"
              value={activePoint.vOrigL}
              onChange={(e) => {
                const n = Number(e.target.value);
                updatePoint(activePoint.id, (p) => ({ ...p, vOrigL: Number.isFinite(n) ? n : p.vOrigL }));
              }}
            />
          </label>
        </div>

        <label className="field">
          <div className="label">浓缩体积（mL）</div>
          <input
            ref={vConcRef}
            className="input"
            type="number"
            inputMode="decimal"
            enterKeyHint="next"
            placeholder="例如：49"
            value={activePoint.vConcMl ?? ""}
            onChange={(e) => {
              const n = parseNullableNumber(e.target.value);
              updatePoint(activePoint.id, (p) => ({ ...p, vConcMl: n }));
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter") goNext(true);
            }}
          />
        </label>

        <div className="row space-between">
          <button className="btn danger" disabled={dataset.points.length <= 1} onClick={() => deletePoint(activePoint.id)}>
            删除这条
          </button>
          <div className="row gap-sm">
            <button className="btn" onClick={goPrev} disabled={activeIndex <= 0}>
              上一个
            </button>
            <button className="btn primary" onClick={() => goNext(true)}>
              下一条
            </button>
          </div>
        </div>
      </div>

      <div className="card subtle">
        <div className="strong">快速切换</div>
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
      </div>
    </section>
  );
}
