import { useMemo, useState } from "react";
import type { WetWeightEntry } from "../../lib/wetweights";
import DialogFrame from "../components/DialogFrame";
import { useWetWeights } from "../contexts/WetWeightsContext";
import { parseNullableNumber } from "../utils";

type EditorState =
  | { mode: "new" }
  | {
      mode: "edit";
      originalNameCn: string;
    };

function makeDraft(entry?: WetWeightEntry) {
  return {
    nameCn: entry?.nameCn ?? "",
    nameLatin: entry?.nameLatin ?? "",
    wetWeightMg: entry?.wetWeightMg != null ? String(entry.wetWeightMg) : "",
    group: entry?.taxonomy.group ?? "",
    sub: entry?.taxonomy.sub ?? "",
  };
}

export default function WetWeightLibraryDialog({ onClose }: { onClose: () => void }) {
  const { customEntries, upsertCustom, deleteCustom } = useWetWeights();
  const [q, setQ] = useState("");
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [draft, setDraft] = useState(() => makeDraft());
  const [error, setError] = useState<string | null>(null);

  const filtered = useMemo(() => {
    const query = q.trim();
    if (!query) return [...customEntries].sort((a, b) => a.nameCn.localeCompare(b.nameCn, "zh-Hans-CN"));
    const qLower = query.toLowerCase();
    const hits = customEntries.filter((e) => {
      if (e.nameCn.includes(query)) return true;
      if ((e.nameLatin ?? "").toLowerCase().includes(qLower)) return true;
      if ((e.taxonomy.group ?? "").includes(query)) return true;
      if ((e.taxonomy.sub ?? "").includes(query)) return true;
      return false;
    });
    hits.sort((a, b) => a.nameCn.localeCompare(b.nameCn, "zh-Hans-CN"));
    return hits;
  }, [q, customEntries]);

  function startNew(): void {
    setError(null);
    setEditor({ mode: "new" });
    setDraft(makeDraft());
  }

  function startEdit(entry: WetWeightEntry): void {
    setError(null);
    setEditor({ mode: "edit", originalNameCn: entry.nameCn });
    setDraft(makeDraft(entry));
  }

  function save(): void {
    setError(null);
    const nameCn = draft.nameCn.trim();
    if (!nameCn) {
      setError("中文名不能为空。");
      return;
    }
    const wetWeightMg = parseNullableNumber(draft.wetWeightMg);
    if (wetWeightMg == null || !Number.isFinite(wetWeightMg) || wetWeightMg <= 0) {
      setError("平均湿重需为大于 0 的数值（单位 mg/个）。");
      return;
    }

    const entry: WetWeightEntry = {
      nameCn,
      nameLatin: draft.nameLatin.trim() ? draft.nameLatin.trim() : null,
      wetWeightMg,
      taxonomy: {
        group: draft.group.trim() ? draft.group.trim() : null,
        sub: draft.sub.trim() ? draft.sub.trim() : null,
      },
    };

    if (editor?.mode === "edit" && editor.originalNameCn !== nameCn) {
      deleteCustom(editor.originalNameCn);
    }
    upsertCustom(entry);
    setEditor(null);
  }

  return (
    <DialogFrame title="自定义湿重库" onClose={onClose}>
      <div className="hint">
        这里的条目会保存在本机浏览器数据库中，可用于“从表三添加/自动补齐”。如新增的中文名与表三同名，会优先使用你的自定义值。
      </div>

      <div className="row gap-sm wrap">
        <input className="input" value={q} placeholder="搜索自定义条目…" onChange={(e) => setQ(e.target.value)} />
        <button className="btn primary" onClick={startNew}>
          新增条目
        </button>
      </div>

      {editor && (
        <div className="card">
          <div className="row space-between">
            <div className="strong">{editor.mode === "new" ? "新增条目" : "编辑条目"}</div>
            <button className="btn" onClick={() => setEditor(null)}>
              取消
            </button>
          </div>

          {error && <div className="error">{error}</div>}

          <div className="grid-2">
            <label className="field">
              <div className="label">中文名（唯一）</div>
              <input className="input" value={draft.nameCn} onChange={(e) => setDraft((d) => ({ ...d, nameCn: e.target.value }))} />
            </label>
            <label className="field">
              <div className="label">拉丁名（可空）</div>
              <input
                className="input"
                value={draft.nameLatin}
                onChange={(e) => setDraft((d) => ({ ...d, nameLatin: e.target.value }))}
              />
            </label>
            <label className="field">
              <div className="label">平均湿重（mg/个）</div>
              <input
                className="input"
                inputMode="decimal"
                value={draft.wetWeightMg}
                placeholder="例如：0.0005"
                onChange={(e) => setDraft((d) => ({ ...d, wetWeightMg: e.target.value }))}
              />
            </label>
            <label className="field">
              <div className="label">分类（大类，可空）</div>
              <input className="input" value={draft.group} onChange={(e) => setDraft((d) => ({ ...d, group: e.target.value }))} />
            </label>
            <label className="field">
              <div className="label">分类（亚类，可空）</div>
              <input className="input" value={draft.sub} onChange={(e) => setDraft((d) => ({ ...d, sub: e.target.value }))} />
            </label>
          </div>

          <div className="row gap-sm">
            <button className="btn primary" onClick={save}>
              保存
            </button>
          </div>
        </div>
      )}

      <div className="divider" />

      <div className="row space-between">
        <div className="strong">自定义条目（{customEntries.length}）</div>
      </div>

      {filtered.length === 0 ? (
        <div className="muted">暂无匹配条目。</div>
      ) : (
        <ul className="list">
          {filtered.map((e) => (
            <li key={e.nameCn} className="list-item">
              <div className="row space-between">
                <div>
                  <div className="strong">
                    {e.nameCn} <span className="muted">· {e.wetWeightMg} mg/个</span>
                  </div>
                  <div className="muted small">
                    {e.nameLatin ?? ""}
                    {(e.taxonomy.group || e.taxonomy.sub) && (
                      <>
                        {" "}
                        · {e.taxonomy.group ?? ""}
                        {e.taxonomy.sub ? ` / ${e.taxonomy.sub}` : ""}
                      </>
                    )}
                  </div>
                </div>
                <div className="row gap-sm">
                  <button className="btn" onClick={() => startEdit(e)}>
                    编辑
                  </button>
                  <button
                    className="btn danger"
                    onClick={() => {
                      if (!confirm(`确定删除 “${e.nameCn}” 吗？`)) return;
                      deleteCustom(e.nameCn);
                    }}
                  >
                    删除
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </DialogFrame>
  );
}

