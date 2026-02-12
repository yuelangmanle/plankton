import type { View } from "../AppShell";

export default function NavTabs({ value, onChange }: { value: View; onChange: (v: View) => void }) {
  return (
    <nav className="nav">
      <button className={value === "points" ? "tab active" : "tab"} onClick={() => onChange("points")}>
        1. 采样点与体积
      </button>
      <button className={value === "species" ? "tab active" : "tab"} onClick={() => onChange("species")}>
        2. 物种与计数
      </button>
      <button className={value === "preview" ? "tab active" : "tab"} onClick={() => onChange("preview")}>
        3. 预览与导出
      </button>
    </nav>
  );
}

