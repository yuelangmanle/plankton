import { useMemo, useState } from "react";
import { useWetWeights } from "../contexts/WetWeightsContext";

export default function SpeciesLibrary({ onAdd }: { onAdd: (nameCn: string) => void }) {
  const [q, setQ] = useState("");
  const { search } = useWetWeights();
  const results = useMemo(() => search(q, 20), [q, search]);

  return (
    <div className="card">
      <h3>从表三添加物种（平均湿重库）</h3>
      <div className="row gap-sm">
        <input
          className="input"
          value={q}
          placeholder="搜索中文名/拉丁名/科属..."
          onChange={(e) => setQ(e.target.value)}
        />
      </div>
      <div className="library">
        {results.map((r) => (
          <button
            key={r.nameCn}
            className="library-item"
            onClick={() => onAdd(r.nameCn)}
            title={r.nameLatin ?? ""}
          >
            <div className="strong">{r.nameCn}</div>
            <div className="muted small">
              {(r.nameLatin ?? "").slice(0, 40)}
              {r.nameLatin && r.nameLatin.length > 40 ? "…" : ""}
            </div>
          </button>
        ))}
      </div>
      <div className="hint">点击条目即可添加到本次数据集。找不到时可在顶部“湿重库”里新增自定义条目。</div>
    </div>
  );
}
