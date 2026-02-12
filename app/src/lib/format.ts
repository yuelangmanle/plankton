import type { BiomassCell } from "./calc";

export function biomassCellToDisplay(value: BiomassCell): string {
  if (value === "MISSING_WET_WEIGHT") return "未查到湿重";
  if (value == null) return "";
  return String(value);
}

export function biomassCellToExcel(value: BiomassCell): number | string | null {
  if (value === "MISSING_WET_WEIGHT") return "未查到湿重";
  return value;
}

export function numberOrBlank(value: number | null): number | null {
  return value == null ? null : value;
}

