<script setup lang="ts">
/**
 * v3.4：接口文档。数据来自后端运行时生成的 /api/openapi.json（OpenAPI 3.0），
 * 每个接口带权限要求（x-permission）与字段级权限标记（x-field-policy）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/client'

interface SchemaObj {
  type?: string
  format?: string
  $ref?: string
  items?: SchemaObj
  properties?: Record<string, SchemaObj>
  required?: string[]
  description?: string
  default?: unknown
  'x-aliases'?: string[]
}
interface ParamObj {
  name: string
  in: string
  required: boolean
  schema: SchemaObj
}
interface Operation {
  tags?: string[]
  operationId?: string
  summary?: string
  parameters?: ParamObj[]
  requestBody?: { required?: boolean; content?: Record<string, { schema: SchemaObj }> }
  responses?: Record<string, { description?: string; content?: Record<string, { schema: SchemaObj }> }>
  'x-permission'?: { module: string; moduleName?: string; access: string; action?: string }
  'x-field-policy'?: { module: string; mode: string }
}
interface OpenApiDoc {
  info: { title: string; version: string; description?: string }
  tags: { name: string; description?: string }[]
  paths: Record<string, Record<string, Operation>>
  components: { schemas: Record<string, SchemaObj> }
}
interface OpRow {
  key: string
  method: string
  path: string
  tag: string
  op: Operation
}

const loading = ref(false)
const doc = ref<OpenApiDoc | null>(null)
const keyword = ref('')
const activeTag = ref('')
const selectedKey = ref('')

async function load() {
  loading.value = true
  try {
    const res = await http.get<any, { data: OpenApiDoc }>('/openapi.json')
    doc.value = (res as unknown as { data: OpenApiDoc }).data ?? (res as unknown as OpenApiDoc)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

const rows = computed<OpRow[]>(() => {
  const d = doc.value
  if (!d) return []
  const out: OpRow[] = []
  for (const [path, ops] of Object.entries(d.paths)) {
    for (const [method, op] of Object.entries(ops)) {
      out.push({ key: `${method} ${path}`, method: method.toUpperCase(), path, tag: op.tags?.[0] ?? '', op })
    }
  }
  return out
})

const tagNames = computed<Record<string, string>>(() => {
  const m: Record<string, string> = {}
  for (const t of doc.value?.tags ?? []) m[t.name] = t.description || t.name
  return m
})

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value.filter((r) => {
    if (activeTag.value && r.tag !== activeTag.value) return false
    if (!kw) return true
    return (
      r.path.toLowerCase().includes(kw) ||
      r.method.toLowerCase() === kw ||
      (tagNames.value[r.tag] || '').toLowerCase().includes(kw) ||
      (r.op.operationId || '').toLowerCase().includes(kw)
    )
  })
})

const tagOptions = computed(() => {
  const counts: Record<string, number> = {}
  for (const r of rows.value) counts[r.tag] = (counts[r.tag] ?? 0) + 1
  return Object.keys(counts)
    .sort((a, b) => (tagNames.value[a] || a).localeCompare(tagNames.value[b] || b, 'zh-CN'))
    .map((t) => ({ value: t, label: `${tagNames.value[t] || t}（${counts[t]}）` }))
})

const selected = computed(() => rows.value.find((r) => r.key === selectedKey.value) ?? filtered.value[0] ?? null)

function resolve(s?: SchemaObj): SchemaObj | undefined {
  if (!s) return s
  if (s.$ref) {
    const name = s.$ref.replace('#/components/schemas/', '')
    return doc.value?.components.schemas[name]
  }
  return s
}

function typeText(s?: SchemaObj): string {
  if (!s) return '-'
  if (s.$ref) return s.$ref.replace('#/components/schemas/', '')
  if (s.type === 'array') return `${typeText(s.items)}[]`
  return s.format ? `${s.type}(${s.format})` : s.type || 'object'
}

interface FieldRow {
  name: string
  type: string
  required: boolean
  aliases: string
  note: string
}

function fieldsOf(s?: SchemaObj): FieldRow[] {
  const r = resolve(s)
  if (!r?.properties) return []
  return Object.entries(r.properties).map(([name, p]) => ({
    name,
    type: typeText(p),
    required: !!r.required?.includes(name),
    aliases: (p['x-aliases'] ?? []).join('、'),
    note: p.description ?? '',
  }))
}

const bodySchema = computed(() => {
  const c = selected.value?.op.requestBody?.content
  if (!c) return undefined
  return (c['application/json'] ?? Object.values(c)[0])?.schema
})
const bodyIsMultipart = computed(() => !!selected.value?.op.requestBody?.content?.['multipart/form-data'])
const dataSchema = computed(() => {
  const s = selected.value?.op.responses?.['200']?.content?.['application/json']?.schema
  return s?.properties?.data ?? s
})

function sample(s?: SchemaObj, depth = 0): unknown {
  const r = resolve(s)
  if (!r || depth > 3) return null
  if (r.type === 'array') return [sample(r.items, depth + 1)]
  if (r.type === 'integer' || r.type === 'number') return 0
  if (r.type === 'boolean') return false
  if (r.type === 'string') return r.format === 'date' ? '2026-01-01' : r.format === 'date-time' ? '2026-01-01T00:00:00' : ''
  if (r.properties) {
    const o: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(r.properties)) {
      if (r.required?.includes(k) || depth === 0) o[k] = sample(v, depth + 1)
    }
    return o
  }
  return {}
}

const curl = computed(() => {
  const s = selected.value
  if (!s) return ''
  const origin = window.location.origin
  const query = (s.op.parameters ?? []).filter((p) => p.in === 'query' && p.required).map((p) => `${p.name}=`)
  const url = `${origin}${s.path}${query.length ? '?' + query.join('&') : ''}`
  const lines = [`curl -X ${s.method} '${url}' \\`, `  -H 'Authorization: Bearer <个人访问令牌>'`]
  if (bodySchema.value && !bodyIsMultipart.value) {
    lines[lines.length - 1] += ' \\'
    lines.push(`  -H 'Content-Type: application/json' \\`)
    lines.push(`  -d '${JSON.stringify(sample(bodySchema.value))}'`)
  } else if (bodyIsMultipart.value) {
    lines[lines.length - 1] += ' \\'
    lines.push(`  -F 'file=@data.csv'`)
  }
  return lines.join('\n')
})

async function copyCurl() {
  try {
    await navigator.clipboard.writeText(curl.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.warning('浏览器不允许访问剪贴板，请手动复制')
  }
}

function methodType(m: string): 'success' | 'primary' | 'warning' | 'danger' | 'info' {
  return m === 'GET' ? 'success' : m === 'POST' ? 'primary' : m === 'PUT' || m === 'PATCH' ? 'warning' : m === 'DELETE' ? 'danger' : 'info'
}

const ACCESS_TEXT: Record<string, string> = { read: '读', write: '写' }
const ACTION_TEXT: Record<string, string> = {
  create: '新建',
  update: '编辑',
  delete: '删除（需显式授予）',
  import: '导入',
  export: '导出',
  approve: '审批',
}
const FIELD_MODE_TEXT: Record<string, string> = {
  MASK: '响应按字段权限剔除隐藏字段',
  CREATE: '受限字段有值即拒绝（403）',
  UPDATE: '改动受限字段即拒绝（403），原样回传不算改动',
  PRESENCE: '请求中出现受限字段即拒绝（403）',
  REQUIRE: '整个接口依赖某字段的权限',
}

onMounted(load)
</script>

<template>
  <div class="page-card api-docs" v-loading="loading">
    <div class="page-toolbar">
      <div class="api-docs-title">
        <span>{{ doc?.info.title || '接口文档' }}</span>
        <el-tag v-if="doc" size="small" type="info" effect="plain">v{{ doc.info.version }}</el-tag>
        <span v-if="doc" class="muted">共 {{ rows.length }} 个接口</span>
      </div>
      <el-select v-model="activeTag" clearable filterable placeholder="全部模块" style="width: 220px">
        <el-option v-for="t in tagOptions" :key="t.value" :value="t.value" :label="t.label" />
      </el-select>
      <el-input v-model="keyword" clearable placeholder="搜索路径、方法或模块" style="width: 260px" />
      <el-button tag="a" href="/api/openapi.json" target="_blank">下载 openapi.json</el-button>
    </div>
    <p v-if="doc?.info.description" class="muted api-docs-desc">{{ doc.info.description }}</p>

    <div class="api-docs-body">
      <div class="api-docs-list">
        <div
          v-for="r in filtered"
          :key="r.key"
          class="api-row"
          :class="{ active: selected?.key === r.key }"
          @click="selectedKey = r.key"
        >
          <el-tag :type="methodType(r.method)" size="small" effect="dark" class="api-method">{{ r.method }}</el-tag>
          <span class="api-path">{{ r.path }}</span>
        </div>
        <el-empty v-if="!loading && !filtered.length" description="没有匹配的接口" :image-size="60" />
      </div>

      <div v-if="selected" class="api-docs-detail">
        <div class="api-detail-head">
          <el-tag :type="methodType(selected.method)" effect="dark">{{ selected.method }}</el-tag>
          <code class="api-detail-path">{{ selected.path }}</code>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="模块">{{ tagNames[selected.tag] || selected.tag }}</el-descriptions-item>
          <el-descriptions-item label="所需权限">
            <template v-if="selected.op['x-permission']">
              {{ selected.op['x-permission'].moduleName }}（{{ selected.op['x-permission'].module }}）·
              {{ ACCESS_TEXT[selected.op['x-permission'].access] }}
              <template v-if="selected.op['x-permission'].action">
                · {{ ACTION_TEXT[selected.op['x-permission'].action] || selected.op['x-permission'].action }}
              </template>
            </template>
            <span v-else>登录即可</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="selected.op['x-field-policy']" label="字段级权限">
            {{ FIELD_MODE_TEXT[selected.op['x-field-policy'].mode] || selected.op['x-field-policy'].mode }}
          </el-descriptions-item>
          <el-descriptions-item label="实现">{{ selected.op.operationId }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="selected.op.parameters?.length">
          <div class="api-section-title">参数</div>
          <el-table :data="selected.op.parameters" size="small" border>
            <el-table-column prop="name" label="名称" min-width="140" />
            <el-table-column label="位置" width="80">
              <template #default="{ row }">{{ row.in === 'path' ? '路径' : '查询' }}</template>
            </el-table-column>
            <el-table-column label="类型" width="130">
              <template #default="{ row }">{{ typeText(row.schema) }}</template>
            </el-table-column>
            <el-table-column label="必填" width="70">
              <template #default="{ row }">{{ row.required ? '是' : '' }}</template>
            </el-table-column>
            <el-table-column label="默认值" min-width="90">
              <template #default="{ row }">{{ row.schema?.default ?? '' }}</template>
            </el-table-column>
          </el-table>
        </template>

        <template v-if="bodySchema">
          <div class="api-section-title">
            请求体 <span class="muted">{{ bodyIsMultipart ? 'multipart/form-data' : typeText(bodySchema) }}</span>
          </div>
          <el-table v-if="fieldsOf(bodySchema).length" :data="fieldsOf(bodySchema)" size="small" border>
            <el-table-column prop="name" label="字段" min-width="150" />
            <el-table-column prop="type" label="类型" min-width="140" />
            <el-table-column label="必填" width="70">
              <template #default="{ row }">{{ row.required ? '是' : '' }}</template>
            </el-table-column>
            <el-table-column prop="aliases" label="兼容别名" min-width="160" show-overflow-tooltip />
            <el-table-column prop="note" label="说明" min-width="140" show-overflow-tooltip />
          </el-table>
        </template>

        <div class="api-section-title">
          响应 <span class="muted">{code, msg, data}，data：{{ typeText(dataSchema) }}</span>
        </div>
        <el-table v-if="fieldsOf(dataSchema).length" :data="fieldsOf(dataSchema)" size="small" border>
          <el-table-column prop="name" label="字段" min-width="150" />
          <el-table-column prop="type" label="类型" min-width="140" />
        </el-table>

        <div class="api-section-title">
          调用示例
          <el-button link type="primary" size="small" @click="copyCurl">复制</el-button>
        </div>
        <pre class="api-curl">{{ curl }}</pre>
      </div>
    </div>
  </div>
</template>

<style scoped>
.api-docs-title {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.muted {
  color: var(--el-text-color-secondary);
  font-weight: 400;
  font-size: 12px;
}
.api-docs-desc {
  margin: 0 0 12px;
}
.api-docs-body {
  display: flex;
  gap: 16px;
  min-height: 560px;
}
.api-docs-list {
  width: 380px;
  flex-shrink: 0;
  max-height: calc(100vh - 240px);
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}
.api-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.api-row:hover {
  background: var(--el-fill-color-light);
}
.api-row.active {
  background: var(--el-color-primary-light-9);
}
.api-method {
  width: 58px;
  justify-content: center;
}
.api-path {
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
  word-break: break-all;
}
.api-docs-detail {
  flex: 1;
  min-width: 0;
}
.api-detail-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.api-detail-path {
  font-size: 15px;
  word-break: break-all;
}
.api-section-title {
  margin: 16px 0 8px;
  font-weight: 600;
}
.api-curl {
  margin: 0;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
@media (max-width: 900px) {
  .api-docs-body {
    flex-direction: column;
  }
  .api-docs-list {
    width: 100%;
    max-height: 320px;
  }
}
</style>
