<script setup lang="ts">
/**
 * 监控设置：告警派单策略、监控模板（CMDB → Prometheus 目标下发规则）、告警规则（只读，来自 Prometheus）、接入说明。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  JOB_LABEL,
  SEVERITY_LABEL,
  SEVERITY_TAG,
  createProfile,
  deleteProfile,
  fetchAlertRules,
  fetchMonitoringSettings,
  fetchProfiles,
  previewProfile,
  saveMonitoringSettings,
  updateProfile,
} from '@/api/monitoring'
import type { AlertRule, MonitorProfile, MonitoringSettingsResp, ProfilePayload } from '@/api/monitoring'

const auth = useAuthStore()
const isAdmin = computed(() => !!auth.user?.isAdmin)
const tab = ref('policy')

/* ---------------- 派单策略 ---------------- */
const meta = ref<MonitoringSettingsResp | null>(null)
const policy = reactive<Record<string, string>>({})
const STATUS_OPTIONS = ['在用(计费)', '维修中', '停用中', '待验收', '待上架', '待退役']
const statuses = ref<string[]>([])
const savingPolicy = ref(false)

async function loadSettings() {
  try {
    meta.value = await fetchMonitoringSettings()
    for (const v of meta.value.values) policy[v.key] = v.value
    statuses.value = (policy.monitored_statuses || '').split(',').map((s) => s.trim()).filter(Boolean)
  } catch {
    // ignore
  }
}

async function savePolicy() {
  savingPolicy.value = true
  try {
    await saveMonitoringSettings({
      auto_ticket_severity: policy.auto_ticket_severity,
      auto_ticket_delay_min: String(policy.auto_ticket_delay_min ?? '5'),
      ticket_priority_critical: policy.ticket_priority_critical,
      ticket_priority_warning: policy.ticket_priority_warning,
      resolve_comment: policy.resolve_comment,
      monitored_statuses: statuses.value.join(','),
      prometheus_url: policy.prometheus_url || '',
    })
    ElMessage.success('已保存（30 秒内生效）')
    void loadSettings()
  } catch {
    // ignore
  } finally {
    savingPolicy.value = false
  }
}

/* ---------------- 监控模板 ---------------- */
const profiles = ref<MonitorProfile[]>([])
const profDialog = ref(false)
const profEditing = ref<number | null>(null)
const prof = reactive<ProfilePayload>({ job: 'icmp', categoryPattern: '%', ipField: 'biz_ip', port: null, enabled: true, remark: '' })

async function loadProfiles() {
  try {
    profiles.value = await fetchProfiles()
  } catch {
    // ignore
  }
}

function openProf(p?: MonitorProfile) {
  profEditing.value = p?.id ?? null
  Object.assign(prof, p ? { ...p } : { job: 'icmp', categoryPattern: '%', ipField: 'biz_ip', port: null, enabled: true, remark: '' })
  profDialog.value = true
}

async function saveProf() {
  try {
    const body: ProfilePayload = { ...prof, port: prof.port || null }
    if (profEditing.value) await updateProfile(profEditing.value, body)
    else await createProfile(body)
    ElMessage.success('已保存，Prometheus 将在 1 分钟内刷新目标')
    profDialog.value = false
    void loadProfiles()
  } catch {
    // ignore
  }
}

async function toggleProf(p: MonitorProfile) {
  try {
    await updateProfile(p.id, { ...p, enabled: !p.enabled })
    void loadProfiles()
  } catch {
    // ignore
  }
}

async function delProf(p: MonitorProfile) {
  try {
    await ElMessageBox.confirm(`删除模板 ${p.job} / ${p.categoryPattern}？`, '删除', { type: 'warning' })
    await deleteProfile(p.id)
    void loadProfiles()
  } catch {
    // 取消
  }
}

const preview = ref<{ job: string; total: number; targets: { targets: string[]; labels: Record<string, string> }[] } | null>(null)
async function doPreview(job: string) {
  try {
    const r = await previewProfile(job)
    preview.value = { job, ...r }
  } catch {
    // ignore
  }
}

/* ---------------- 告警规则 ---------------- */
const rules = ref<AlertRule[]>([])
const rulesError = ref('')
function dur(sec?: number) {
  if (!sec) return '立即'
  return sec < 60 ? `${sec} 秒` : sec % 60 === 0 ? `${sec / 60} 分` : `${Math.round(sec / 6) / 10} 分`
}
/** 把 Prometheus 模板变量渲染成可读占位：{{ $labels.asset_name }} → [asset_name]，{{ $value }} → [值] */
function tpl(s?: string) {
  return (s || '')
    .replace(/\{\{\s*\$labels\.([A-Za-z0-9_]+)\s*\}\}/g, '[$1]')
    .replace(/\{\{[^}]*\$value[^}]*\}\}/g, '[值]')
    .replace(/\{\{[^}]*\}\}/g, '…')
}

async function loadRules() {
  rulesError.value = ''
  try {
    rules.value = await fetchAlertRules()
  } catch (e: any) {
    rulesError.value = e?.message || '无法从 Prometheus 读取规则'
  }
}

/* ---------------- 接入说明 ---------------- */
const origin = window.location.origin
const sdExample = computed(() =>
  (meta.value?.sdJobs || ['icmp'])
    .map(
      (j) => `  - job_name: ${j}
    http_sd_configs:
      - url: ${origin}/api/monitoring/sd/${j}
        refresh_interval: 1m
        authorization:
          credentials_file: /etc/prometheus/secrets/monitoring_token`,
    )
    .join('\n'),
)
const amExample = computed(
  () => `receivers:
  - name: dcim
    webhook_configs:
      - url: ${origin}/api/monitoring/ingest/alertmanager
        send_resolved: true
        http_config:
          authorization:
            credentials_file: /etc/alertmanager/secrets/monitoring_token`,
)
const genericExample = computed(
  () => `curl -X POST '${origin}/api/monitoring/ingest/generic?source=zabbix' \\
  -H 'Authorization: Bearer <MONITORING_TOKEN>' -H 'Content-Type: application/json' \\
  -d '{"alertname":"磁盘空间不足","severity":"warning","status":"firing",
       "instance":"10.0.0.12","summary":"/data 使用率 92%","id":"zbx-12345"}'`,
)

function copy(text: string) {
  navigator.clipboard?.writeText(text).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败，请手工选择复制'),
  )
}

onMounted(() => {
  void loadSettings()
  void loadProfiles()
  void loadRules()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">监控设置</div>
    <el-tabs v-model="tab">
      <!-- 派单策略 -->
      <el-tab-pane label="告警派单" name="policy">
        <el-form label-width="160px" style="max-width: 720px" :disabled="!isAdmin">
          <el-form-item label="自动派单级别">
            <el-select v-model="policy.auto_ticket_severity" style="width: 240px">
              <el-option label="仅严重（推荐）" value="critical" />
              <el-option label="警告及以上" value="warning" />
              <el-option label="全部" value="info" />
              <el-option label="不自动派单" value="none" />
            </el-select>
          </el-form-item>
          <el-form-item label="派单延迟（分钟）">
            <el-input-number v-model="policy.auto_ticket_delay_min as any" :min="0" :max="1440" />
            <span class="hint">告警持续超过该时长仍未恢复/未确认才派单，过滤瞬时抖动</span>
          </el-form-item>
          <el-form-item label="严重告警工单优先级">
            <el-radio-group v-model="policy.ticket_priority_critical">
              <el-radio-button value="高">高</el-radio-button>
              <el-radio-button value="中">中</el-radio-button>
              <el-radio-button value="低">低</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="警告告警工单优先级">
            <el-radio-group v-model="policy.ticket_priority_warning">
              <el-radio-button value="高">高</el-radio-button>
              <el-radio-button value="中">中</el-radio-button>
              <el-radio-button value="低">低</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="恢复时写入工单评论">
            <el-switch v-model="policy.resolve_comment" active-value="true" inactive-value="false" />
          </el-form-item>
          <el-form-item label="纳入监控的资产状态">
            <el-select v-model="statuses" multiple filterable allow-create style="width: 100%">
              <el-option v-for="s in STATUS_OPTIONS" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item label="Prometheus 地址">
            <el-input v-model="policy.prometheus_url" placeholder="留空则使用部署配置 PROMETHEUS_URL" />
            <div class="hint" style="margin-left: 0">当前生效：{{ meta?.effectivePrometheusUrl || '未配置' }}</div>
          </el-form-item>
          <el-form-item v-if="isAdmin">
            <el-button type="primary" :loading="savingPolicy" @click="savePolicy">保存</el-button>
          </el-form-item>
          <el-form-item v-else>
            <span class="hint" style="margin-left: 0">仅管理员可修改</span>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 监控模板 -->
      <el-tab-pane label="监控模板" name="profiles">
        <el-alert type="info" :closable="false" style="margin-bottom: 12px">
          监控模板决定 CMDB 中哪些资产、用哪个 IP、以什么方式被 Prometheus 采集。资产按分类路径匹配（如 /SRV/% 表示服务器类，% 表示全部）；
          在资产扩展属性中设置 monitor=false 可单独排除。
        </el-alert>
        <div class="page-toolbar">
          <div style="flex: 1"></div>
          <el-button v-if="isAdmin" type="primary" :icon="'Plus'" @click="openProf()">新增模板</el-button>
        </div>
        <el-table :data="profiles" border size="small">
          <el-table-column label="采集任务" width="180">
            <template #default="{ row }">{{ JOB_LABEL[row.job] || row.job }} <span class="hint">{{ row.job }}</span></template>
          </el-table-column>
          <el-table-column prop="categoryPattern" label="分类匹配" width="140" />
          <el-table-column label="IP 字段" width="120">
            <template #default="{ row }">{{ row.ipField === 'biz_ip' ? '业务 IP' : '管理/带外 IP' }}</template>
          </el-table-column>
          <el-table-column label="端口" width="80">
            <template #default="{ row }">{{ row.port || '-' }}</template>
          </el-table-column>
          <el-table-column label="启用" width="80">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled" :disabled="!isAdmin" @change="toggleProf(row)" />
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button link type="primary" @click="doPreview(row.job)">预览目标</el-button>
              <template v-if="isAdmin">
                <el-button link type="primary" @click="openProf(row)">编辑</el-button>
                <el-button link type="danger" @click="delProf(row)">删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 告警规则 -->
      <el-tab-pane label="告警规则" name="rules">
        <el-alert v-if="rulesError" type="warning" :closable="false" :title="rulesError" style="margin-bottom: 12px" />
        <div class="hint" style="margin: 0 0 8px">
          规则文件位于部署目录 deploy/monitoring/rules/*.yml，修改后执行 <code>./dcimctl monitoring reload</code> 生效。
        </div>
        <el-table :data="rules" border size="small">
          <el-table-column prop="group" label="分组" width="120" />
          <el-table-column prop="name" label="规则" width="200" />
          <el-table-column label="级别" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.severity" :type="SEVERITY_TAG[row.severity] || 'info'" size="small">{{ SEVERITY_LABEL[row.severity] || row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="持续" width="80">
            <template #default="{ row }">{{ dur(row.duration) }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ tpl(row.summary) }}</template>
          </el-table-column>
          <el-table-column prop="query" label="表达式" min-width="260" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.firing" type="danger" size="small">触发 {{ row.firing }}</el-tag>
              <el-tag v-else-if="row.health !== 'ok'" type="warning" size="small">{{ row.health }}</el-tag>
              <el-tag v-else type="success" size="small">正常</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 接入说明 -->
      <el-tab-pane label="接入说明" name="access">
        <el-descriptions :column="1" border size="small" style="max-width: 900px; margin-bottom: 16px">
          <el-descriptions-item label="接入令牌">
            <el-tag v-if="meta?.tokenConfigured" type="success" size="small">已配置</el-tag>
            <el-tag v-else type="danger" size="small">未配置（服务发现与告警接入接口将返回 503）</el-tag>
            <span class="hint">令牌为部署环境变量 MONITORING_TOKEN，安装脚本自动生成，并写入 deploy/monitoring/secrets/monitoring_token</span>
          </el-descriptions-item>
          <el-descriptions-item label="启用的采集任务">{{ (meta?.sdJobs || []).join('、') || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sec">1. Prometheus 服务发现（从 CMDB 拉取监控目标）<el-button link type="primary" @click="copy(sdExample)">复制</el-button></h4>
        <pre class="code">{{ sdExample }}</pre>
        <h4 class="sec">2. Alertmanager 告警回调<el-button link type="primary" @click="copy(amExample)">复制</el-button></h4>
        <pre class="code">{{ amExample }}</pre>
        <h4 class="sec">3. 其他监控系统（Zabbix / 夜莺 / 自研脚本）通用接入<el-button link type="primary" @click="copy(genericExample)">复制</el-button></h4>
        <pre class="code">{{ genericExample }}</pre>
        <div class="hint" style="margin: 0">
          通用接入字段：alertname、severity（critical/warning/info）、status（firing/resolved）、instance（IP 或主机名，自动关联资产）、
          asset_no（可选，直接指定资产）、summary、description、id（同一告警的唯一标识，用于去重与恢复）。
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="profDialog" :title="profEditing ? '编辑监控模板' : '新增监控模板'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="采集任务">
          <el-select v-model="prof.job" filterable allow-create style="width: 100%">
            <el-option v-for="(l, k) in JOB_LABEL" :key="k" :label="`${l}（${k}）`" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类匹配">
          <el-input v-model="prof.categoryPattern" placeholder="如 /SRV/%，% 表示全部" />
        </el-form-item>
        <el-form-item label="IP 字段">
          <el-radio-group v-model="prof.ipField">
            <el-radio value="biz_ip">业务 IP</el-radio>
            <el-radio value="mgmt_ip">管理/带外 IP</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="prof.port as any" :min="1" :max="65535" :controls="false" placeholder="ICMP 留空" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="prof.enabled" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="prof.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profDialog = false">取消</el-button>
        <el-button type="primary" @click="saveProf">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog :model-value="!!preview" :title="`目标预览：${preview?.job}（共 ${preview?.total ?? 0} 个）`" width="760px" @close="preview = null">
      <el-table :data="preview?.targets || []" size="small" max-height="460" border>
        <el-table-column label="目标" width="180">
          <template #default="{ row }">{{ row.targets.join(', ') }}</template>
        </el-table-column>
        <el-table-column label="资产">
          <template #default="{ row }">{{ row.labels.asset_no }} · {{ row.labels.asset_name }}</template>
        </el-table-column>
        <el-table-column label="位置" width="160">
          <template #default="{ row }">{{ row.labels.room }} / {{ row.labels.rack }}</template>
        </el-table-column>
        <el-table-column label="分类" width="140">
          <template #default="{ row }">{{ row.labels.category }}</template>
        </el-table-column>
      </el-table>
      <div v-if="(preview?.total ?? 0) > 200" class="hint" style="margin: 8px 0 0">仅显示前 200 条</div>
      <div v-if="preview && !preview.total" class="hint" style="margin: 8px 0 0">
        该任务没有已启用的模板，或没有资产满足条件（分类匹配、对应 IP 已填写、资产状态在监控范围内）。
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 8px;
}
.sec {
  margin: 16px 0 8px;
  font-size: 14px;
}
.code {
  background: var(--el-fill-color-light);
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 12px;
  overflow-x: auto;
  max-width: 900px;
  white-space: pre;
}
</style>
