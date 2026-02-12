import { useState } from "react";
import { DEFAULT_SETTINGS } from "../../lib/constants";
import type { ApiConfig, Settings } from "../../lib/types";
import DialogFrame from "../components/DialogFrame";

export default function SettingsDialog({
  value,
  onChange,
  onClose,
}: {
  value: Settings;
  onChange: (next: Settings) => void;
  onClose: () => void;
}) {
  const [draft, setDraft] = useState<Settings>(value ?? DEFAULT_SETTINGS);

  return (
    <DialogFrame
      title="设置"
      onClose={() => {
        onChange(draft);
        onClose();
      }}
    >
      <div className="grid-2">
        <label className="field">
          <div className="label">默认原水体积（L）</div>
          <input
            className="input"
            inputMode="decimal"
            value={draft.defaultVOrigL}
            onChange={(e) => setDraft((s) => ({ ...s, defaultVOrigL: Number(e.target.value) }))}
          />
        </label>
      </div>

      <div className="divider" />

      <h3>API 1</h3>
      <ApiEditor value={draft.api1} onChange={(api1) => setDraft((s) => ({ ...s, api1 }))} />

      <div className="divider" />

      <h3>API 2</h3>
      <ApiEditor value={draft.api2} onChange={(api2) => setDraft((s) => ({ ...s, api2 }))} />

      <div className="hint">
        按 OpenAI Chat Completions 形式请求（可能会遇到 CORS 限制）。如果接口无法在浏览器直接调用，可先手动输入湿重。
      </div>
    </DialogFrame>
  );
}

function ApiEditor({ value, onChange }: { value: ApiConfig; onChange: (next: ApiConfig) => void }) {
  return (
    <div className="grid-2">
      <label className="field">
        <div className="label">名称</div>
        <input className="input" value={value.name} onChange={(e) => onChange({ ...value, name: e.target.value })} />
      </label>
      <label className="field">
        <div className="label">Model</div>
        <input className="input" value={value.model} onChange={(e) => onChange({ ...value, model: e.target.value })} />
      </label>
      <label className="field" style={{ gridColumn: "1 / -1" }}>
        <div className="label">Base URL（完整接口地址）</div>
        <input
          className="input"
          value={value.baseUrl}
          placeholder="例如：https://api.openai.com/v1/chat/completions"
          onChange={(e) => onChange({ ...value, baseUrl: e.target.value })}
        />
      </label>
      <label className="field" style={{ gridColumn: "1 / -1" }}>
        <div className="label">API Key</div>
        <input className="input" value={value.apiKey} onChange={(e) => onChange({ ...value, apiKey: e.target.value })} />
      </label>
    </div>
  );
}

