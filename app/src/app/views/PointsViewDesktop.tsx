import { createBlankPoint } from "../../lib/dataset";
import type { Dataset, Id, Settings } from "../../lib/types";
import { nextDefaultPointLabel, parseNullableNumber } from "../utils";

export default function PointsViewDesktop({
  dataset,
  settings,
  onChange,
}: {
  dataset: Dataset;
  settings: Settings;
  onChange: (updater: (d: Dataset) => Dataset) => void;
}) {
  function addPoint(): void {
    onChange((current) => {
      const label = nextDefaultPointLabel(current.points);
      const point = createBlankPoint(settings, label);
      const nextSpecies = current.species.map((s) => ({
        ...s,
        countsByPointId: { ...s.countsByPointId, [point.id]: 0 },
      }));
      return { ...current, points: [...current.points, point], species: nextSpecies };
    });
  }

  function deletePoint(pointId: Id): void {
    onChange((current) => {
      if (current.points.length <= 1) return current;
      const points = current.points.filter((p) => p.id !== pointId);
      const species = current.species.map((s) => {
        const countsByPointId = { ...s.countsByPointId };
        delete countsByPointId[pointId];
        return { ...s, countsByPointId };
      });
      return { ...current, points, species };
    });
  }

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>采样点（列）</h2>
        <button className="btn primary" onClick={addPoint}>
          添加采样点
        </button>
      </div>

      <div className="hint">
        点位名称由你决定（默认 1、2、3…；也可用 1-0、1-2 或 G1…）。原水体积默认 20 L，可修改。
      </div>

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>名称</th>
              <th>浓缩体积（mL）</th>
              <th>原水体积（L）</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {dataset.points.map((p) => (
              <tr key={p.id}>
                <td>
                  <input
                    className="input"
                    value={p.label}
                    onChange={(e) =>
                      onChange((d) => ({
                        ...d,
                        points: d.points.map((pp) => (pp.id === p.id ? { ...pp, label: e.target.value } : pp)),
                      }))
                    }
                  />
                </td>
                <td>
                  <input
                    className="input"
                    inputMode="decimal"
                    placeholder="例如：49"
                    value={p.vConcMl ?? ""}
                    onChange={(e) => {
                      const n = parseNullableNumber(e.target.value);
                      onChange((d) => ({
                        ...d,
                        points: d.points.map((pp) => (pp.id === p.id ? { ...pp, vConcMl: n } : pp)),
                      }));
                    }}
                  />
                </td>
                <td>
                  <input
                    className="input"
                    inputMode="decimal"
                    value={p.vOrigL}
                    onChange={(e) => {
                      const n = Number(e.target.value);
                      onChange((d) => ({
                        ...d,
                        points: d.points.map((pp) =>
                          pp.id === p.id ? { ...pp, vOrigL: Number.isFinite(n) ? n : pp.vOrigL } : pp,
                        ),
                      }));
                    }}
                  />
                </td>
                <td className="cell-actions">
                  <button
                    className="btn danger"
                    disabled={dataset.points.length <= 1}
                    onClick={() => deletePoint(p.id)}
                  >
                    删除
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

