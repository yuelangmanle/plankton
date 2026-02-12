export default function TopBar({
  titlePrefix,
  onTitlePrefixChange,
  onOpenSettings,
  onOpenWetWeightLibrary,
}: {
  titlePrefix: string;
  onTitlePrefixChange: (value: string) => void;
  onOpenSettings: () => void;
  onOpenWetWeightLibrary: () => void;
}) {
  return (
    <header className="topbar">
      <div className="brand">
        <div className="brand-title">浮游动物计算与导出</div>
        <div className="brand-sub">本地保存 · 可导出 Excel · 双 API 辅助查湿重</div>
      </div>

      <div className="topbar-actions">
        <label className="field">
          <div className="label">标题前缀（可空）</div>
          <input
            className="input"
            value={titlePrefix}
            placeholder="例如：2025年10月大坳水库"
            onChange={(e) => onTitlePrefixChange(e.target.value)}
          />
        </label>

        <button className="btn" onClick={onOpenWetWeightLibrary}>
          湿重库
        </button>

        <button className="btn" onClick={onOpenSettings}>
          设置
        </button>
      </div>
    </header>
  );
}
