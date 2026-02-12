import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { exportTable1, exportTable2 } from "../src/lib/exportExcel";
import type { Dataset } from "../src/lib/types";

function toBuffer(bytes: ArrayBuffer): Buffer {
  return Buffer.from(new Uint8Array(bytes));
}

async function main() {
  const scriptDir = path.dirname(fileURLToPath(import.meta.url));
  const outDir = path.resolve(scriptDir, "..", "..", "out");
  await mkdir(outDir, { recursive: true });

  const dataset: Dataset = {
    id: "demo",
    titlePrefix: "演示数据",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    points: [
      { id: "p1", label: "1-0", vConcMl: 50, vOrigL: 20 },
      { id: "p2", label: "1-2", vConcMl: 60, vOrigL: 20 },
    ],
    species: [
      {
        id: "s1",
        nameCn: "瓶砂壳虫",
        nameLatin: "",
        taxonomy: { lvl1: "原生动物", lvl2: "根足纲", lvl3: "表壳目", lvl4: "砂壳科", lvl5: "砂壳虫属" },
        avgWetWeightMg: 0.00002,
        countsByPointId: { p1: 10, p2: 0 },
      },
      {
        id: "s2",
        nameCn: "前节晶囊轮虫",
        nameLatin: "",
        taxonomy: { lvl1: "轮虫", lvl2: "轮虫纲", lvl3: "", lvl4: "晶囊轮科", lvl5: "晶囊轮属" },
        avgWetWeightMg: null,
        countsByPointId: { p1: 5, p2: 8 },
      },
    ],
  };

  const t1 = await exportTable1(dataset);
  await writeFile(path.join(outDir, "表1-演示.xlsx"), toBuffer(t1.bytes));

  const t2 = await exportTable2(dataset);
  await writeFile(path.join(outDir, "表2-演示.xlsx"), toBuffer(t2.bytes));

  console.log("Wrote:", outDir);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
