import { useMemo, useState } from "react";
import type { Settings, Species } from "../../lib/types";
import DialogFrame from "../components/DialogFrame";
import { parseNullableNumber } from "../utils";

function extractNumbers(text: string): number[] {
  const matches = text.match(/-?\d+(?:\.\d+)?(?:e-?\d+)?/gi);
  if (!matches) return [];
  const nums: number[] = [];
  for (const m of matches) {
    const n = Number(m);
    if (Number.isFinite(n)) nums.push(n);
  }
  return nums;
}

async function callChatCompletion(api: Settings["api1"], prompt: string): Promise<string> {
  const res = await fetch(api.baseUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(api.apiKey ? { Authorization: `Bearer ${api.apiKey}` } : {}),
    },
    body: JSON.stringify({
      model: api.model,
      temperature: 0,
      messages: [
        { role: "system", content: "你是生态学助手。请尽量给出单一数值答案，并标注单位。" },
        { role: "user", content: prompt },
      ],
    }),
  });

  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  type ChatCompletionResponse = {
    choices?: Array<{ message?: { content?: string } }>;
  };
  const json = (await res.json()) as ChatCompletionResponse;
  const content = json.choices?.[0]?.message?.content;
  if (typeof content !== "string") throw new Error("响应格式不符合预期");
  return content;
}

export default function WetWeightDialog({
  settings,
  species,
  onClose,
  onApply,
}: {
  settings: Settings;
  species: Species;
  onClose: () => void;
  onApply: (wetWeightMg: number) => void;
}) {
  const [manual, setManual] = useState("");
  const [loading, setLoading] = useState(false);
  const [api1Text, setApi1Text] = useState("");
  const [api2Text, setApi2Text] = useState("");
  const [error, setError] = useState<string | null>(null);

  const prompt = `请查询并给出“${species.nameCn}”的平均湿重，单位 mg/个。只需输出一个数值（可用科学计数法）。如果不确定请说明不确定。`;

  const api1Nums = useMemo(() => extractNumbers(api1Text), [api1Text]);
  const api2Nums = useMemo(() => extractNumbers(api2Text), [api2Text]);

  return (
    <DialogFrame title={`查湿重：${species.nameCn}`} onClose={onClose}>
      <div className="hint">你决定是否采用 API 的结果；也可以直接手动输入。</div>

      <div className="card subtle">
        <div className="strong">Prompt</div>
        <pre className="pre">{prompt}</pre>
      </div>

      <div className="row gap-sm">
        <button
          className="btn primary"
          disabled={loading || !settings.api1.baseUrl || !settings.api2.baseUrl}
          onClick={async () => {
            setLoading(true);
            setError(null);
            try {
              const [t1, t2] = await Promise.all([
                callChatCompletion(settings.api1, prompt),
                callChatCompletion(settings.api2, prompt),
              ]);
              setApi1Text(t1);
              setApi2Text(t2);
            } catch (e) {
              setError(e instanceof Error ? e.message : String(e));
            } finally {
              setLoading(false);
            }
          }}
        >
          同时调用 API 1 & 2
        </button>
        {loading && <span className="muted">查询中…</span>}
      </div>

      {error && <div className="error">错误：{error}</div>}

      <div className="grid-2">
        <div className="card">
          <div className="strong">{settings.api1.name || "API 1"} 输出</div>
          <pre className="pre">{api1Text || "（暂无）"}</pre>
          {api1Nums.length > 0 && (
            <div className="row wrap gap-xs">
              {api1Nums.slice(0, 6).map((n) => (
                <button key={`a1-${n}`} className="btn tiny" onClick={() => onApply(n)}>
                  使用 {n}
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="card">
          <div className="strong">{settings.api2.name || "API 2"} 输出</div>
          <pre className="pre">{api2Text || "（暂无）"}</pre>
          {api2Nums.length > 0 && (
            <div className="row wrap gap-xs">
              {api2Nums.slice(0, 6).map((n) => (
                <button key={`a2-${n}`} className="btn tiny" onClick={() => onApply(n)}>
                  使用 {n}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="divider" />

      <div className="row gap-sm">
        <label className="field" style={{ flex: 1 }}>
          <div className="label">手动输入湿重（mg/个）</div>
          <input
            className="input"
            value={manual}
            onChange={(e) => setManual(e.target.value)}
            placeholder="例如：0.0005"
          />
        </label>
        <button
          className="btn primary"
          onClick={() => {
            const n = parseNullableNumber(manual);
            if (n == null) return;
            onApply(n);
          }}
        >
          使用手动值
        </button>
      </div>
    </DialogFrame>
  );
}
