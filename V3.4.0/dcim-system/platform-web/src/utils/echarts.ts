/**
 * ECharts 按需引入（控制包体）：仅注册本项目用到的 图表/组件/渲染器，
 * 各报表页统一从这里 import，等价 echarts 全量入口但体积小得多。
 */
import * as echarts from 'echarts/core'
import { BarChart, GaugeChart, GraphChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  GaugeChart,
  GraphChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer,
])

export default echarts

/** 读取当前主题下的 Element Plus 颜色变量（暗色模式自动切换） */
function cssVar(name: string, fallback: string): string {
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
    return v || fallback
  } catch {
    return fallback
  }
}

/** 坐标轴文字颜色 */
export function axisColor(): string {
  return cssVar('--el-text-color-regular', '#606266')
}

/** 与卡片背景一致的描边色（饼图扇区间隔等） */
export function surfaceColor(): string {
  return cssVar('--el-bg-color', '#ffffff')
}

/** ECharts 实例类型 */
export type EChartsType = ReturnType<typeof echarts.init>

/** 统一配色（与 Element Plus 主色协调） */
export const CHART_COLORS = [
  '#409eff',
  '#67c23a',
  '#e6a23c',
  '#f56c6c',
  '#909399',
  '#8d6ceb',
  '#00c1ce',
  '#f79c42',
]
