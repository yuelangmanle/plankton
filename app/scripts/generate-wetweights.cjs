const fs = require("node:fs/promises");
const path = require("node:path");
const ExcelJS = require("exceljs");

const ROOT_DIR = path.resolve(__dirname, "..", "..");
const INPUT_XLSX = path.join(ROOT_DIR, "表三.xlsx");
const OUTPUT_JSON = path.join(__dirname, "..", "src", "data", "wetweights.json");

function toText(value) {
  if (value == null) return "";
  if (typeof value === "string") return value.trim();
  if (typeof value === "number") return String(value);
  if (typeof value === "object" && typeof value.text === "string") return value.text.trim();
  if (typeof value === "object" && typeof value.richText === "object") {
    return value.richText.map((t) => t.text ?? "").join("").trim();
  }
  return String(value).trim();
}

function isSubHeading(nameCn) {
  return nameCn.endsWith("科") || nameCn.endsWith("属") || nameCn.endsWith("目");
}

function isGroupHeading(nameCn) {
  // In 表三.xlsx, group headings are typically non-numeric rows that are not
  // taxonomy sub-headings (like "xx科/xx属/xx目"), e.g. "原生动物/轮虫/枝角类/桡足类".
  return !isSubHeading(nameCn);
}

async function main() {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.readFile(INPUT_XLSX);
  const sheet = workbook.worksheets[0];
  if (!sheet) throw new Error("No worksheet found in 表三.xlsx");

  let currentGroup = "";
  let currentSub = "";

  /** @type {{nameCn: string, nameLatin: string|null, wetWeightMg: number, taxonomy: {group: string|null, sub: string|null}}[]} */
  const entries = [];

  sheet.eachRow({ includeEmpty: false }, (row, rowNumber) => {
    if (rowNumber < 3) return;

    const nameCn = toText(row.getCell(1).value);
    const nameLatin = toText(row.getCell(2).value) || null;
    const weightCell = row.getCell(3).value;

    if (!nameCn) return;

    const wetWeightMg = typeof weightCell === "number" ? weightCell : null;
    if (wetWeightMg == null || Number.isNaN(wetWeightMg)) {
      if (isGroupHeading(nameCn)) {
        currentGroup = nameCn;
        currentSub = "";
        return;
      }
      if (isSubHeading(nameCn)) {
        currentSub = nameCn;
        return;
      }
      // Unknown heading type, keep as-is without changing hierarchy.
      return;
    }

    entries.push({
      nameCn,
      nameLatin,
      wetWeightMg,
      taxonomy: {
        group: currentGroup || null,
        sub: currentSub || null,
      },
    });
  });

  entries.sort((a, b) => a.nameCn.localeCompare(b.nameCn, "zh-Hans-CN"));

  await fs.mkdir(path.dirname(OUTPUT_JSON), { recursive: true });
  await fs.writeFile(OUTPUT_JSON, JSON.stringify({ version: 1, entries }, null, 2), "utf8");

  console.log(`Wrote ${entries.length} entries to ${path.relative(ROOT_DIR, OUTPUT_JSON)}`);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
