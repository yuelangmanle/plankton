import { saveAs } from "file-saver";
import { useEffect, useState } from "react";
import type { Dataset, Id } from "../../lib/types";
import type { DatasetCalc } from "../../lib/calc";
import { anyCountPositive, defaultFileName } from "../utils";

function ensureXlsxExt(name: string): string {
  const trimmed = name.trim() || "导出.xlsx";
  return trimmed.toLowerCase().endsWith(".xlsx") ? trimmed : `${trimmed}.xlsx`;
}

export default function PreviewView({
  dataset,
  calc,
  onOpenWetWeight,
}: {
  dataset: Dataset;
  calc: DatasetCalc;
  onOpenWetWeight: (speciesId: Id) => void;
}) {
  const missing = dataset.species.filter((s) => s.avgWetWeightMg == null && anyCountPositive(s));
  const [file1, setFile1] = useState(() => defaultFileName(dataset.titlePrefix, "表1.xlsx"));
  const [file2, setFile2] = useState(() => defaultFileName(dataset.titlePrefix, "表2.xlsx"));
  const [exporting, setExporting] = useState<null | "t1" | "t2">(null);
  const [exportError, setExportError] = useState<string | null>(null);

  useEffect(() => {
    // only set defaults when user hasn't edited
    if (file1 === "表1.xlsx") setFile1(defaultFileName(dataset.titlePrefix, "表1.xlsx"));
    if (file2 === "表2.xlsx") setFile2(defaultFileName(dataset.titlePrefix, "表2.xlsx"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dataset.titlePrefix]);

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>预览与导出</h2>
        <div className="hint">建议先确保点位、体积、计数与湿重数据完整，再导出表1/表2。</div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3>采样点汇总</h3>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>点位</th>
                  <th>浓缩体积(mL)</th>
                  <th>原水体积(L)</th>
                  <th>总个数 N</th>
                  <th>物种数 S</th>
                  <th>H'</th>
                  <th>D</th>
                  <th>J</th>
                </tr>
              </thead>
              <tbody>
                {dataset.points.map((p) => {
                  const idx = calc.pointIndexById[p.id];
                  return (
                    <tr key={p.id}>
                      <td>{p.label}</td>
                      <td>{p.vConcMl ?? ""}</td>
                      <td>{p.vOrigL}</td>
                      <td>{idx?.totalCount ?? ""}</td>
                      <td>{idx?.speciesCountS ?? ""}</td>
                      <td>{idx?.H ?? ""}</td>
                      <td>{idx?.D ?? ""}</td>
                      <td>{idx?.J ?? ""}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        <div className="card">
          <h3>缺失湿重</h3>
          <div className="hint">当某物种有计数但没有平均湿重时，生物量会显示“未查到湿重”。</div>
          <ul className="list">
            {missing.map((s) => (
              <li key={s.id} className="list-item">
                <div className="row space-between">
                  <div>
                    <div className="strong">{s.nameCn || "（未命名物种）"}</div>
                    <div className="muted">{s.nameLatin}</div>
                  </div>
                  <button className="btn" onClick={() => onOpenWetWeight(s.id)}>
                    查湿重
                  </button>
                </div>
              </li>
            ))}
            {missing.length === 0 && <li className="muted">暂无。</li>}
          </ul>
        </div>
      </div>

      <div className="card">
        <h3>导出 Excel</h3>
        <div className="hint">导出的保存路径由浏览器/系统对话框决定，你只需在这里填写文件名。</div>

        {exportError && <div className="error">导出失败：{exportError}</div>}

        <div className="grid-2">
          <div className="card subtle">
            <div className="strong">表1（计数/密度/生物量/H'）</div>
            <label className="field">
              <div className="label">文件名</div>
              <input className="input" value={file1} onChange={(e) => setFile1(e.target.value)} />
            </label>
            <button
              className="btn primary"
              disabled={exporting != null}
              onClick={async () => {
                setExporting("t1");
                setExportError(null);
                try {
                  const { exportTable1 } = await import("../../lib/exportExcel");
                  const res = await exportTable1(dataset);
                  saveAs(new Blob([res.bytes], { type: res.mime }), ensureXlsxExt(file1));
                } catch (e) {
                  setExportError(e instanceof Error ? e.message : String(e));
                } finally {
                  setExporting(null);
                }
              }}
            >
              {exporting === "t1" ? "导出中…" : "导出表1.xlsx"}
            </button>
          </div>

          <div className="card subtle">
            <div className="strong">表2（分布图/统计/优势度/多样性）</div>
            <label className="field">
              <div className="label">文件名</div>
              <input className="input" value={file2} onChange={(e) => setFile2(e.target.value)} />
            </label>
            <button
              className="btn primary"
              disabled={exporting != null}
              onClick={async () => {
                setExporting("t2");
                setExportError(null);
                try {
                  const { exportTable2 } = await import("../../lib/exportExcel");
                  const res = await exportTable2(dataset);
                  saveAs(new Blob([res.bytes], { type: res.mime }), ensureXlsxExt(file2));
                } catch (e) {
                  setExportError(e instanceof Error ? e.message : String(e));
                } finally {
                  setExporting(null);
                }
              }}
            >
              {exporting === "t2" ? "导出中…" : "导出表2.xlsx"}
            </button>
          </div>
        </div>
      </div>
    </section>
  );
}
