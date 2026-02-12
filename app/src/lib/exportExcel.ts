import ExcelJS from "exceljs";
import { calcDataset, taxonomyColumnsForSpecies, type BiomassCell } from "./calc";
import { biomassCellToExcel } from "./format";
import type { Dataset, Point, Species } from "./types";

const MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

export type ExportResult = {
  bytes: ArrayBuffer;
  mime: string;
};

function titleWithPrefix(prefix: string, suffix: string): string {
  const p = prefix.trim();
  return p ? `${p}${suffix}` : suffix;
}

function setColumnWidths(ws: ExcelJS.Worksheet, pointCount: number) {
  // A-F taxonomy + points
  const widths = [14, 14, 14, 14, 14, 16];
  for (let i = 1; i <= 6; i++) ws.getColumn(i).width = widths[i - 1];
  for (let i = 0; i < pointCount; i++) ws.getColumn(7 + i).width = 10;
}

function styleTitle(ws: ExcelJS.Worksheet, lastCol: number, title: string) {
  ws.mergeCells(1, 1, 1, lastCol);
  const cell = ws.getCell(1, 1);
  cell.value = title;
  cell.font = { bold: true, size: 14 };
  cell.alignment = { horizontal: "center", vertical: "middle" };
  ws.getRow(1).height = 24;
}

function styleHeaderRow(ws: ExcelJS.Worksheet, row: number, lastCol: number) {
  const r = ws.getRow(row);
  r.height = 18;
  for (let c = 1; c <= lastCol; c++) {
    const cell = ws.getCell(row, c);
    cell.font = { bold: true };
    cell.alignment = { horizontal: "center", vertical: "middle", wrapText: true };
    cell.fill = {
      type: "pattern",
      pattern: "solid",
      fgColor: { argb: "FFF8FAFC" },
    };
  }
}

function mergeNameBlock(ws: ExcelJS.Worksheet, row: number) {
  ws.mergeCells(row, 1, row, 6);
  ws.getCell(row, 1).alignment = { horizontal: "center", vertical: "middle" };
  ws.getCell(row, 1).font = { bold: true };
}

function addPointsHeader(ws: ExcelJS.Worksheet, points: Point[], row: number) {
  mergeNameBlock(ws, row);
  ws.getCell(row, 1).value = "名称";
  for (let i = 0; i < points.length; i++) {
    ws.getCell(row, 7 + i).value = points[i].label;
  }
}

function addConcRow(ws: ExcelJS.Worksheet, points: Point[], row: number) {
  mergeNameBlock(ws, row);
  ws.getCell(row, 1).value = "浓缩体积（ml）";
  for (let i = 0; i < points.length; i++) {
    const v = points[i].vConcMl;
    ws.getCell(row, 7 + i).value = v == null ? null : v;
  }
}

function addOrigRow(ws: ExcelJS.Worksheet, points: Point[], row: number) {
  mergeNameBlock(ws, row);
  ws.getCell(row, 1).value = "原水体积（L）";
  for (let i = 0; i < points.length; i++) {
    ws.getCell(row, 7 + i).value = points[i].vOrigL;
  }
}

function addSpeciesRows(
  ws: ExcelJS.Worksheet,
  dataset: Dataset,
  points: Point[],
  startRow: number,
  valueFor: (sp: Species, point: Point) => number | string | null,
) {
  const rows = dataset.species.map((sp) => taxonomyColumnsForSpecies(sp));

  for (let i = 0; i < dataset.species.length; i++) {
    const rowIndex = startRow + i;
    const sp = dataset.species[i];
    const cols = rows[i];
    for (let c = 0; c < 6; c++) ws.getCell(rowIndex, 1 + c).value = cols[c] || null;
    for (let pi = 0; pi < points.length; pi++) {
      ws.getCell(rowIndex, 7 + pi).value = valueFor(sp, points[pi]);
    }
  }

  // Merge taxonomy columns (A-E) to mimic example layout.
  for (let col = 1; col <= 5; col++) {
    let start = 0;
    while (start < rows.length) {
      const val = rows[start][col - 1];
      if (!val) {
        start += 1;
        continue;
      }
      let end = start;
      while (end + 1 < rows.length) {
        const next = rows[end + 1];
        if (next[col - 1] !== val) break;
        let samePrev = true;
        for (let prev = 0; prev < col - 1; prev++) {
          if (next[prev] !== rows[start][prev]) {
            samePrev = false;
            break;
          }
        }
        if (!samePrev) break;
        end += 1;
      }
      if (end > start) ws.mergeCells(startRow + start, col, startRow + end, col);
      start = end + 1;
    }
  }

  // Alignment for taxonomy columns
  for (let i = 0; i < dataset.species.length; i++) {
    const r = startRow + i;
    for (let c = 1; c <= 6; c++) {
      ws.getCell(r, c).alignment = { horizontal: "center", vertical: "middle", wrapText: true };
    }
  }
}

function writeTotalRow(
  ws: ExcelJS.Worksheet,
  row: number,
  points: Point[],
  valueForPoint: (point: Point, pointIndex: number) => number | string | null,
) {
  ws.mergeCells(row, 1, row, 6);
  const cell = ws.getCell(row, 1);
  cell.value = "总计";
  cell.font = { bold: true };
  cell.alignment = { horizontal: "center", vertical: "middle" };

  for (let i = 0; i < points.length; i++) {
    ws.getCell(row, 7 + i).value = valueForPoint(points[i], i);
  }
}

function sumNumbers(values: (number | string | null)[]): number | null {
  let sum = 0;
  let has = false;
  for (const v of values) {
    if (typeof v === "number" && Number.isFinite(v)) {
      sum += v;
      has = true;
    }
  }
  return has ? sum : null;
}

function biomassTotalForPoint(cells: BiomassCell[]): number | string | null {
  if (cells.some((c) => c === "MISSING_WET_WEIGHT")) return "未查到湿重";
  const numeric = cells.filter((c): c is number => typeof c === "number" && Number.isFinite(c));
  if (numeric.length === 0) return null;
  return numeric.reduce((a, b) => a + b, 0);
}

export async function exportTable1(dataset: Dataset): Promise<ExportResult> {
  const wb = new ExcelJS.Workbook();
  const points = dataset.points;
  const calc = calcDataset(dataset);
  const lastCol = 6 + points.length;

  const wsCount = wb.addWorksheet("浮游动物计数");
  const wsDensity = wb.addWorksheet("浮游动物密度");
  const wsBiomass = wb.addWorksheet("浮游动物生物量");
  const wsH = wb.addWorksheet("浮游动物多样性指数H'表");

  for (const ws of [wsCount, wsDensity, wsBiomass, wsH]) setColumnWidths(ws, points.length);

  styleTitle(wsCount, lastCol, titleWithPrefix(dataset.titlePrefix, "浮游动物计数"));
  styleTitle(wsDensity, lastCol, titleWithPrefix(dataset.titlePrefix, "浮游动物密度（ind/L）"));
  styleTitle(wsBiomass, lastCol, titleWithPrefix(dataset.titlePrefix, "浮游动物生物量（mg/L）"));
  styleTitle(wsH, lastCol, titleWithPrefix(dataset.titlePrefix, "浮游动物香农多样性指数H"));

  // Common headers
  addPointsHeader(wsCount, points, 3);
  addConcRow(wsCount, points, 4);
  addOrigRow(wsCount, points, 5); // only first sheet includes original volume (per requirement)
  styleHeaderRow(wsCount, 3, lastCol);

  addPointsHeader(wsDensity, points, 3);
  addConcRow(wsDensity, points, 4);
  styleHeaderRow(wsDensity, 3, lastCol);

  addPointsHeader(wsBiomass, points, 3);
  addConcRow(wsBiomass, points, 4);
  styleHeaderRow(wsBiomass, 3, lastCol);

  addPointsHeader(wsH, points, 3);
  addConcRow(wsH, points, 4);
  styleHeaderRow(wsH, 3, lastCol);

  const startCount = 6;
  const startOther = 5;

  // Species rows
  addSpeciesRows(wsCount, dataset, points, startCount, (sp, point) => sp.countsByPointId[point.id] ?? 0);

  addSpeciesRows(wsDensity, dataset, points, startOther, (sp, point) => {
    const v = calc.perSpeciesByPoint[sp.id]?.[point.id]?.density;
    return v == null ? null : v;
  });

  addSpeciesRows(wsBiomass, dataset, points, startOther, (sp, point) => {
    const v = calc.perSpeciesByPoint[sp.id]?.[point.id]?.biomass;
    return biomassCellToExcel(v);
  });

  addSpeciesRows(wsH, dataset, points, startOther, (sp, point) => {
    const v = calc.perSpeciesByPoint[sp.id]?.[point.id]?.pLnP;
    return v == null ? null : v;
  });

  // Total rows
  const totalRowCount = startCount + dataset.species.length;
  writeTotalRow(wsCount, totalRowCount, points, (p) => calc.pointIndexById[p.id]?.totalCount ?? null);

  const totalRowOther = startOther + dataset.species.length;
  writeTotalRow(wsDensity, totalRowOther, points, (p) => {
    if (p.vConcMl == null || p.vOrigL <= 0) return null;
    const values = dataset.species.map((sp) => calc.perSpeciesByPoint[sp.id]?.[p.id]?.density ?? null);
    return sumNumbers(values);
  });

  writeTotalRow(wsBiomass, totalRowOther, points, (p) => {
    const cells = dataset.species.map((sp) => calc.perSpeciesByPoint[sp.id]?.[p.id]?.biomass ?? null);
    return biomassTotalForPoint(cells);
  });

  writeTotalRow(wsH, totalRowOther, points, (p) => calc.pointIndexById[p.id]?.H ?? null);

  // Freeze panes
  wsCount.views = [{ state: "frozen", xSplit: 6, ySplit: 5 }];
  wsDensity.views = [{ state: "frozen", xSplit: 6, ySplit: 4 }];
  wsBiomass.views = [{ state: "frozen", xSplit: 6, ySplit: 4 }];
  wsH.views = [{ state: "frozen", xSplit: 6, ySplit: 4 }];

  const bytes = (await wb.xlsx.writeBuffer()) as ArrayBuffer;
  return { bytes, mime: MIME_XLSX };
}

export async function exportTable2(dataset: Dataset): Promise<ExportResult> {
  const wb = new ExcelJS.Workbook();
  const points = dataset.points;
  const calc = calcDataset(dataset);

  const wsDist = wb.addWorksheet("浮游动物采样点物种分布图");
  const wsDensity = wb.addWorksheet("浮游动物密度统计表");
  const wsBiomass = wb.addWorksheet("浮游动物生物量统计表");
  const wsY = wb.addWorksheet("浮游动物优势度");
  const wsDiv = wb.addWorksheet("浮游动物多样性表");

  // --- 分布图 ---
  const distPointStartCol = 5; // E
  wsDist.getCell(1, 1).value = "种类";
  wsDist.getCell(1, distPointStartCol).value = "采样点位（点位-水深（m））";
  wsDist.mergeCells(1, distPointStartCol, 1, distPointStartCol + points.length - 1);
  for (let i = 0; i < points.length; i++) wsDist.getCell(2, distPointStartCol + i).value = points[i].label;
  // per requirement: only first sheet contains original volume
  wsDist.getCell(3, 1).value = "原水体积（L）";
  for (let i = 0; i < points.length; i++) wsDist.getCell(3, distPointStartCol + i).value = points[i].vOrigL;

  // taxonomy columns A-D
  wsDist.getColumn(1).width = 14;
  wsDist.getColumn(2).width = 14;
  wsDist.getColumn(3).width = 14;
  wsDist.getColumn(4).width = 18;
  for (let i = 0; i < points.length; i++) wsDist.getColumn(distPointStartCol + i).width = 10;

  const distStartRow = 4;
  for (let i = 0; i < dataset.species.length; i++) {
    const r = distStartRow + i;
    const sp = dataset.species[i];
    wsDist.getCell(r, 1).value = sp.taxonomy.lvl1 || null;
    wsDist.getCell(r, 2).value = sp.taxonomy.lvl2 || null;
    wsDist.getCell(r, 3).value = sp.taxonomy.lvl4 || sp.taxonomy.lvl3 || null;
    wsDist.getCell(r, 4).value = sp.nameCn || null;
    for (let pi = 0; pi < points.length; pi++) {
      const count = sp.countsByPointId[points[pi].id] ?? 0;
      wsDist.getCell(r, distPointStartCol + pi).value = count > 0 ? "+" : null;
      wsDist.getCell(r, distPointStartCol + pi).alignment = { horizontal: "center", vertical: "middle" };
    }
  }

  // --- 密度/生物量/优势度 ---
  function buildStatSheet(
    ws: ExcelJS.Worksheet,
    titleA: string,
    valueFor: (sp: Species, point: Point) => number | string | null,
    totalForPoint?: (point: Point) => number | string | null,
  ) {
    ws.getCell(1, 1).value = titleA;
    ws.getCell(1, 2).value = "类别";
    for (let i = 0; i < points.length; i++) ws.getCell(1, 3 + i).value = points[i].label;

    ws.getColumn(1).width = 14;
    ws.getColumn(2).width = 20;
    for (let i = 0; i < points.length; i++) ws.getColumn(3 + i).width = 12;

    styleHeaderRow(ws, 1, 2 + points.length);

    const startRow = 2;
    for (let i = 0; i < dataset.species.length; i++) {
      const r = startRow + i;
      const sp = dataset.species[i];
      ws.getCell(r, 1).value = sp.taxonomy.lvl1 || null;
      ws.getCell(r, 2).value = sp.nameCn || null;
      for (let pi = 0; pi < points.length; pi++) {
        ws.getCell(r, 3 + pi).value = valueFor(sp, points[pi]);
      }
    }

    const totalRow = startRow + dataset.species.length;
    ws.getCell(totalRow, 1).value = "总计";
    ws.getCell(totalRow, 1).font = { bold: true };
    for (let pi = 0; pi < points.length; pi++) {
      ws.getCell(totalRow, 3 + pi).value = totalForPoint ? totalForPoint(points[pi]) : null;
    }
  }

  buildStatSheet(
    wsDensity,
    "门",
    (sp, p) => calc.perSpeciesByPoint[sp.id]?.[p.id]?.density ?? null,
    (p) => {
      if (p.vConcMl == null || p.vOrigL <= 0) return null;
      const values = dataset.species.map((sp) => calc.perSpeciesByPoint[sp.id]?.[p.id]?.density ?? null);
      return sumNumbers(values);
    },
  );

  buildStatSheet(
    wsBiomass,
    "门",
    (sp, p) => biomassCellToExcel(calc.perSpeciesByPoint[sp.id]?.[p.id]?.biomass ?? null),
    (p) => {
      const cells = dataset.species.map((sp) => calc.perSpeciesByPoint[sp.id]?.[p.id]?.biomass ?? null);
      return biomassTotalForPoint(cells);
    },
  );

  buildStatSheet(
    wsY,
    "门",
    (sp, p) => {
      const v = calc.perSpeciesByPoint[sp.id]?.[p.id]?.Y;
      return v == null ? null : v;
    },
    () => null,
  );

  // Highlight dominant species (Y>0.02)
  for (let i = 0; i < dataset.species.length; i++) {
    const sp = dataset.species[i];
    const row = 2 + i;
    for (let pi = 0; pi < points.length; pi++) {
      const cell = wsY.getCell(row, 3 + pi);
      const isDom = calc.perSpeciesByPoint[sp.id]?.[points[pi].id]?.isDominant ?? false;
      if (isDom && typeof cell.value === "number") {
        cell.fill = { type: "pattern", pattern: "solid", fgColor: { argb: "FFFFFBEB" } };
        cell.font = { bold: true, color: { argb: "FF92400E" } };
      }
    }
  }

  // --- 多样性表 ---
  wsDiv.getCell(1, 1).value = "指数";
  for (let i = 0; i < points.length; i++) wsDiv.getCell(1, 2 + i).value = points[i].label;
  styleHeaderRow(wsDiv, 1, 1 + points.length);

  const rows: { label: string; get: (p: Point) => number | null }[] = [
    { label: "H'", get: (p) => calc.pointIndexById[p.id]?.H ?? null },
    { label: "D", get: (p) => calc.pointIndexById[p.id]?.D ?? null },
    { label: "J", get: (p) => calc.pointIndexById[p.id]?.J ?? null },
  ];
  for (let r = 0; r < rows.length; r++) {
    wsDiv.getCell(2 + r, 1).value = rows[r].label;
    for (let i = 0; i < points.length; i++) wsDiv.getCell(2 + r, 2 + i).value = rows[r].get(points[i]);
  }

  wsDiv.getColumn(1).width = 12;
  for (let i = 0; i < points.length; i++) wsDiv.getColumn(2 + i).width = 14;

  const bytes = (await wb.xlsx.writeBuffer()) as ArrayBuffer;
  return { bytes, mime: MIME_XLSX };
}
