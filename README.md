# 浮游动物计算与报表导出（Web → 可打包 APK）

本仓库包含：
- 需求参考文件：`浮游动物开发指南.docx`、`表1.xlsx`、`表2.xlsx`、`表三.xlsx`
- 前端应用：`app/`（Vite + React + TypeScript）

## 运行
- `cd app`
- `npm install`
- `npm run generate:wetweights`（把根目录的 `表三.xlsx` 转成前端可用的 `app/src/data/wetweights.json`）
- `npm run dev`

## 导出 Excel
在「3. 预览与导出」里：
- 导出 `表1.xlsx`：计数 / 密度 / 生物量 / H'（表1首表包含原水体积行）
- 导出 `表2.xlsx`：分布图 / 密度统计 / 生物量统计 / 优势度 / 多样性（表2首表包含原水体积行）

生物量中若某物种有计数但缺失湿重，会写入 `未查到湿重`，且该点位的总计也会写入 `未查到湿重`。

## 开发自检（可选）
- `npm run dev:export`：在 `d:/plankton/out/` 生成两份演示导出文件，便于用 Excel/WPS 核对格式与数值。

## 打包 APK（建议后续）
当前实现为 Web 应用（更快完成和迭代），后续可用 Capacitor/Cordova 等方式打包为 Android APK。
如需我把 Capacitor 的 Android 工程也一起生成到仓库里，告诉我你的 `appId`（例如 `com.example.plankton`）与应用名。

