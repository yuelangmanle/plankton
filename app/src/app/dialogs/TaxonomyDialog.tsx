import { useState } from "react";
import type { Species, Taxonomy } from "../../lib/types";
import DialogFrame from "../components/DialogFrame";

export default function TaxonomyDialog({
  species,
  onClose,
  onApply,
}: {
  species: Species;
  onClose: () => void;
  onApply: (next: { nameLatin: string; taxonomy: Taxonomy }) => void;
}) {
  const [latin, setLatin] = useState(species.nameLatin);
  const [taxonomy, setTaxonomy] = useState<Taxonomy>(species.taxonomy);

  return (
    <DialogFrame title={`编辑分类：${species.nameCn || "未命名物种"}`} onClose={onClose}>
      <div className="hint">分类字段沿用示例表（5 列）。你可以自由填写或留空。</div>

      <label className="field">
        <div className="label">拉丁名</div>
        <input className="input" value={latin} onChange={(e) => setLatin(e.target.value)} />
      </label>

      <div className="grid-2">
        <label className="field">
          <div className="label">分类1</div>
          <input className="input" value={taxonomy.lvl1} onChange={(e) => setTaxonomy((t) => ({ ...t, lvl1: e.target.value }))} />
        </label>
        <label className="field">
          <div className="label">分类2</div>
          <input className="input" value={taxonomy.lvl2} onChange={(e) => setTaxonomy((t) => ({ ...t, lvl2: e.target.value }))} />
        </label>
        <label className="field">
          <div className="label">分类3</div>
          <input className="input" value={taxonomy.lvl3} onChange={(e) => setTaxonomy((t) => ({ ...t, lvl3: e.target.value }))} />
        </label>
        <label className="field">
          <div className="label">分类4</div>
          <input className="input" value={taxonomy.lvl4} onChange={(e) => setTaxonomy((t) => ({ ...t, lvl4: e.target.value }))} />
        </label>
        <label className="field" style={{ gridColumn: "1 / -1" }}>
          <div className="label">分类5</div>
          <input className="input" value={taxonomy.lvl5} onChange={(e) => setTaxonomy((t) => ({ ...t, lvl5: e.target.value }))} />
        </label>
      </div>

      <div className="row gap-sm">
        <button className="btn primary" onClick={() => onApply({ nameLatin: latin, taxonomy })}>
          保存
        </button>
      </div>
    </DialogFrame>
  );
}

