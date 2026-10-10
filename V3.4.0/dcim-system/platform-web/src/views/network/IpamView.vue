<script setup lang="ts">
/**
 * IP 地址管理：网段（利用率 / 下一个可用 / 地址明细 / 分配与保留）、VLAN、冲突检测、IP 反查。
 * 资产的业务 IP / 管理 IP 是设备地址的唯一来源，平台定时同步；给资产分配地址会直接写回资产。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AssetPicker from '@/components/AssetPicker.vue'
import {
  ADDR_STATUS_LABEL,
  PURPOSE_LABEL,
  allocateAddress,
  createPrefix,
  createVlan,
  deletePrefix,
  deleteVlan,
  fetchConflicts,
  fetchNextFree,
  fetchPrefixAddresses,
  fetchPrefixes,
  fetchVlans,
  lookupIp,
  releaseAddress,
  syncIpam,
  updatePrefix,
  updateVlan,
} from '@/api/network'
import type { IpAddress, Prefix, Vlan } from '@/api/network'

const auth = useAuthStore()
/** v3.4：删除需角色显式授予（后端同样校验） */
const canDelete = computed(() => auth.canDelete('network'))
const canWrite = computed(() => auth.canWrite('network'))
const tab = ref('prefixes')

/* ---------------- 网段 ---------------- */
const q = reactive({ keyword: '', purpose: '' })
const prefixes = ref<Prefix[]>([])
const loading = ref(false)
const current = ref<Prefix | null>(null)

async function loadPrefixes() {
  loading.value = true
  try {
    prefixes.value = await fetchPrefixes({ keyword: q.keyword || undefined, purpose: q.purpose || undefined })
    if (current.value) {
      const c = prefixes.value.find((p) => p.id === current.value!.id)
      current.value = c || null
    }
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

function pctColor(p: number | null) {
  if (p == null) return '#c0c4cc'
  return p >= 90 ? '#f56c6c' : p >= 75 ? '#e6a23c' : '#409eff'
}

const syncing = ref(false)
async function doSync() {
  syncing.value = true
  try {
    const r = await syncIpam()
    ElMessage.success(`同步完成：新增 ${r.inserted}，移除 ${r.deleted}`)
    void loadPrefixes()
    if (current.value) void loadAddresses()
  } catch {
    // ignore
  } finally {
    syncing.value = false
  }
}

/* 网段编辑 */
const pDialog = ref(false)
const pEditing = ref<number | null>(null)
const pForm = reactive({ prefix: '', name: '', purpose: 'BIZ', vlanRef: null as number | null, roomCode: '', gateway: '', dns: '', remark: '' })
const vlans = ref<Vlan[]>([])
function openPrefix(p?: Prefix) {
  pEditing.value = p?.id ?? null
  Object.assign(pForm, p
    ? { prefix: p.prefix, name: p.name, purpose: p.purpose, vlanRef: p.vlanRef ?? null, roomCode: p.roomCode || '', gateway: p.gateway || '', dns: p.dns || '', remark: p.remark || '' }
    : { prefix: '', name: '', purpose: 'BIZ', vlanRef: null, roomCode: '', gateway: '', dns: '', remark: '' })
  pDialog.value = true
}
async function savePrefix() {
  try {
    const body = { ...pForm, vlanRef: pForm.vlanRef || null }
    if (pEditing.value) await updatePrefix(pEditing.value, body)
    else await createPrefix(body)
    ElMessage.success('已保存')
    pDialog.value = false
    void loadPrefixes()
  } catch {
    // ignore
  }
}
async function removePrefix(p: Prefix) {
  try {
    await ElMessageBox.confirm(`删除网段 ${p.prefix}？（资产 IP 不受影响）`, '删除', { type: 'warning' })
    await deletePrefix(p.id)
    if (current.value?.id === p.id) current.value = null
    void loadPrefixes()
  } catch {
    // 取消
  }
}

/* 地址明细 */
const addrQ = reactive({ status: '', page: 1, size: 50 })
const addresses = ref<IpAddress[]>([])
const addrTotal = ref(0)
const nextFree = ref<string[]>([])
async function selectPrefix(p: Prefix) {
  current.value = p
  addrQ.page = 1
  await loadAddresses()
}
async function loadAddresses() {
  if (!current.value) return
  try {
    const r = await fetchPrefixAddresses(current.value.id, { status: addrQ.status || undefined, page: addrQ.page, size: addrQ.size })
    addresses.value = r.records
    addrTotal.value = r.total
    nextFree.value = current.value.family === 4 && current.value.masklen >= 16 ? await fetchNextFree(current.value.id, 5) : []
  } catch {
    nextFree.value = []
  }
}

/* 分配 */
const aDialog = ref(false)
const aForm = reactive({ mode: 'asset' as 'asset' | 'reserve', ip: '', assetNo: undefined as string | undefined, ipRole: 'biz', status: 'RESERVED', hostname: '', remark: '' })
function openAllocate(ip?: string) {
  Object.assign(aForm, { mode: 'asset', ip: ip || '', assetNo: undefined, ipRole: 'biz', status: 'RESERVED', hostname: '', remark: '' })
  aDialog.value = true
}
async function saveAllocate() {
  try {
    const r = await allocateAddress(
      aForm.mode === 'asset'
        ? { ip: aForm.ip || undefined, prefixId: current.value?.id, assetNo: aForm.assetNo, ipRole: aForm.ipRole, status: 'USED', hostname: aForm.hostname || undefined, remark: aForm.remark || undefined }
        : { ip: aForm.ip || undefined, prefixId: current.value?.id, status: aForm.status, hostname: aForm.hostname || undefined, remark: aForm.remark || undefined },
    )
    ElMessage.success(`已分配 ${r.ip}`)
    aDialog.value = false
    void loadPrefixes()
    void loadAddresses()
  } catch {
    // ignore
  }
}
async function release(a: IpAddress) {
  try {
    if (a.source === 'ASSET') {
      await ElMessageBox.confirm(`${a.ip} 是资产 ${a.assetNo} 的${a.ipRole === 'biz' ? '业务' : '管理'} IP，释放将清空资产上的该字段，确认？`, '释放地址', { type: 'warning' })
      await releaseAddress(a.id, true)
    } else {
      await ElMessageBox.confirm(`释放 ${a.ip}？`, '释放地址', { type: 'warning' })
      await releaseAddress(a.id)
    }
    ElMessage.success('已释放')
    void loadPrefixes()
    void loadAddresses()
  } catch {
    // 取消
  }
}

/* ---------------- VLAN ---------------- */
async function loadVlans() {
  try {
    vlans.value = await fetchVlans()
  } catch {
    // ignore
  }
}
const vDialog = ref(false)
const vEditing = ref<number | null>(null)
const vForm = reactive({ vlanId: 100, name: '', roomCode: '', purpose: '', remark: '' })
function openVlan(v?: Vlan) {
  vEditing.value = v?.id ?? null
  Object.assign(vForm, v
    ? { vlanId: v.vlanId, name: v.name, roomCode: v.roomCode || '', purpose: v.purpose || '', remark: v.remark || '' }
    : { vlanId: 100, name: '', roomCode: '', purpose: '', remark: '' })
  vDialog.value = true
}
async function saveVlan() {
  try {
    const body = { ...vForm, roomCode: vForm.roomCode || undefined }
    if (vEditing.value) await updateVlan(vEditing.value, body)
    else await createVlan(body)
    ElMessage.success('已保存')
    vDialog.value = false
    void loadVlans()
  } catch {
    // ignore
  }
}
async function removeVlan(v: Vlan) {
  try {
    await ElMessageBox.confirm(`删除 VLAN ${v.vlanId}？关联网段将解除关联`, '删除', { type: 'warning' })
    await deleteVlan(v.id)
    void loadVlans()
  } catch {
    // 取消
  }
}

/* ---------------- 冲突 / 反查 ---------------- */
const conflicts = ref<any>(null)
async function loadConflicts() {
  try {
    conflicts.value = await fetchConflicts()
  } catch {
    // ignore
  }
}
const lookupText = ref('')
const lookupResult = ref<any>(null)
async function doLookup() {
  if (!lookupText.value.trim()) return
  try {
    lookupResult.value = await lookupIp(lookupText.value.trim())
  } catch {
    lookupResult.value = null
  }
}

function onTab(name: string | number) {
  if (name === 'conflicts' && !conflicts.value) void loadConflicts()
}

onMounted(() => {
  void loadPrefixes()
  void loadVlans()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">IP 地址管理</div>
    <el-tabs v-model="tab" @tab-change="onTab">
      <el-tab-pane label="网段与地址" name="prefixes">
        <div class="page-toolbar">
          <el-input v-model="q.keyword" placeholder="网段 / 名称 / 输入 IP 查所属网段" clearable style="width: 260px" @keyup.enter="loadPrefixes" @clear="loadPrefixes" />
          <el-select v-model="q.purpose" placeholder="用途" clearable style="width: 120px" @change="loadPrefixes">
            <el-option v-for="(l, k) in PURPOSE_LABEL" :key="k" :label="l" :value="k" />
          </el-select>
          <el-button type="primary" plain @click="loadPrefixes">查询</el-button>
          <div style="flex: 1"></div>
          <el-button v-if="canWrite" :loading="syncing" @click="doSync">同步资产 IP</el-button>
          <el-button v-if="canWrite" type="primary" :icon="'Plus'" @click="openPrefix()">新增网段</el-button>
        </div>
        <el-row :gutter="16">
          <el-col :xs="24" :lg="11">
            <el-table
              v-loading="loading"
              :data="prefixes"
              border
              size="small"
              highlight-current-row
              max-height="640"
              @row-click="selectPrefix"
            >
              <el-table-column label="网段" min-width="150">
                <template #default="{ row }">
                  <b>{{ row.prefix }}</b>
                  <div class="sub">{{ row.name }}<template v-if="row.parent"> · 属于 {{ row.parent }}</template></div>
                </template>
              </el-table-column>
              <el-table-column label="用途 / VLAN" width="110">
                <template #default="{ row }">
                  {{ PURPOSE_LABEL[row.purpose] || row.purpose }}
                  <div v-if="row.vlanId" class="sub">VLAN {{ row.vlanId }}</div>
                </template>
              </el-table-column>
              <el-table-column label="利用率" min-width="150">
                <template #default="{ row }">
                  <el-progress
                    v-if="row.usedPct != null"
                    :percentage="Math.min(100, row.usedPct)"
                    :stroke-width="10"
                    :color="pctColor(row.usedPct)"
                    :format="() => `${row.used}/${row.size}`"
                  />
                  <span v-else class="sub">{{ row.used }} 已用</span>
                </template>
              </el-table-column>
              <el-table-column v-if="canWrite" label="" width="90">
                <template #default="{ row }">
                  <el-button link type="primary" @click.stop="openPrefix(row)">编辑</el-button>
                  <el-button link type="danger" @click.stop="removePrefix(row)">删</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!loading && !prefixes.length" description="还没有登记网段：先新增网段，再点「同步资产 IP」导入现有地址" />
          </el-col>
          <el-col :xs="24" :lg="13">
            <el-empty v-if="!current" description="点击左侧网段查看地址明细" />
            <template v-else>
              <el-descriptions :column="3" border size="small">
                <el-descriptions-item label="网段">{{ current.prefix }}</el-descriptions-item>
                <el-descriptions-item label="网关">{{ current.gateway || '-' }}</el-descriptions-item>
                <el-descriptions-item label="VLAN">{{ current.vlanId ? `${current.vlanId} ${current.vlanName}` : '-' }}</el-descriptions-item>
                <el-descriptions-item label="已用">{{ current.used }}</el-descriptions-item>
                <el-descriptions-item label="保留">{{ current.reserved }}</el-descriptions-item>
                <el-descriptions-item label="可用">{{ current.size != null ? current.size - current.used : '-' }}</el-descriptions-item>
              </el-descriptions>
              <div v-if="nextFree.length" class="free">
                下一个可用：
                <el-tag v-for="ip in nextFree" :key="ip" size="small" class="free-tag" @click="canWrite && openAllocate(ip)">{{ ip }}</el-tag>
              </div>
              <div class="page-toolbar" style="margin-top: 8px">
                <el-radio-group v-model="addrQ.status" size="small" @change="(addrQ.page = 1), loadAddresses()">
                  <el-radio-button value="">全部</el-radio-button>
                  <el-radio-button value="USED">已用</el-radio-button>
                  <el-radio-button value="RESERVED">保留</el-radio-button>
                  <el-radio-button value="DHCP">DHCP</el-radio-button>
                </el-radio-group>
                <div style="flex: 1"></div>
                <el-button v-if="canWrite" type="primary" size="small" @click="openAllocate()">分配 / 保留地址</el-button>
              </div>
              <el-table :data="addresses" border size="small" max-height="480">
                <el-table-column prop="ip" label="IP" width="130" />
                <el-table-column label="状态" width="70">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'USED' ? 'primary' : row.status === 'RESERVED' ? 'warning' : 'info'">
                      {{ ADDR_STATUS_LABEL[row.status] }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="资产" min-width="200">
                  <template #default="{ row }">
                    <template v-if="row.assetNo">
                      <div class="nowrap">
                        {{ row.assetNo }} <span class="sub">{{ row.ipRole === 'biz' ? '业务' : row.ipRole === 'mgmt' ? '管理' : '' }}</span>
                      </div>
                      <div class="sub nowrap">{{ row.assetName }} {{ row.rackCode ? '· ' + row.rackCode : '' }}</div>
                    </template>
                    <span v-else class="sub">{{ row.remark || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="hostname" label="主机名" width="100" show-overflow-tooltip />
                <el-table-column label="来源" width="60">
                  <template #default="{ row }">{{ row.source === 'ASSET' ? '资产' : '手工' }}</template>
                </el-table-column>
                <el-table-column v-if="canWrite" label="" width="60">
                  <template #default="{ row }">
                    <el-button link type="danger" @click="release(row)">释放</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="page-pagination">
                <el-pagination v-model:current-page="addrQ.page" :page-size="addrQ.size" :total="addrTotal" layout="total, prev, pager, next" small @current-change="loadAddresses" />
              </div>
            </template>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="VLAN" name="vlans">
        <div class="page-toolbar">
          <div style="flex: 1"></div>
          <el-button v-if="canWrite" type="primary" :icon="'Plus'" @click="openVlan()">新增 VLAN</el-button>
        </div>
        <el-table :data="vlans" border size="small">
          <el-table-column prop="vlanId" label="VLAN ID" width="90" />
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column label="机房" width="120">
            <template #default="{ row }">{{ row.roomCode || '全局' }}</template>
          </el-table-column>
          <el-table-column label="用途" width="90">
            <template #default="{ row }">{{ PURPOSE_LABEL[row.purpose] || row.purpose || '-' }}</template>
          </el-table-column>
          <el-table-column prop="prefixes" label="关联网段" min-width="200" show-overflow-tooltip />
          <el-table-column prop="remark" label="说明" min-width="140" show-overflow-tooltip />
          <el-table-column v-if="canWrite" label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click="openVlan(row)">编辑</el-button>
              <el-button v-if="canDelete" link type="danger" @click="removeVlan(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="冲突检测" name="conflicts">
        <div class="page-toolbar">
          <span class="sub">基于资产台账的业务 IP / 管理 IP 实时检测</span>
          <div style="flex: 1"></div>
          <el-button :icon="'Refresh'" @click="loadConflicts">重新检测</el-button>
        </div>
        <template v-if="conflicts">
          <h4 class="sec">IP 重复（{{ conflicts.duplicates.length }}）</h4>
          <el-table :data="conflicts.duplicates" border size="small" max-height="300">
            <el-table-column prop="ip" label="IP" width="150" />
            <el-table-column prop="n" label="资产数" width="80" />
            <el-table-column prop="assets" label="使用该 IP 的资产" />
          </el-table>
          <h4 class="sec">IP 格式错误（{{ conflicts.invalid.length }}）</h4>
          <el-table :data="conflicts.invalid" border size="small" max-height="300">
            <el-table-column prop="assetNo" label="资产" width="200" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="bizIp" label="业务 IP" width="160" />
            <el-table-column prop="mgmtIp" label="管理 IP" width="160" />
          </el-table>
          <h4 class="sec">保留 / DHCP 地址被资产占用（{{ conflicts.reservedInUse.length }}）</h4>
          <el-table :data="conflicts.reservedInUse" border size="small" max-height="300">
            <el-table-column prop="ip" label="IP" width="150" />
            <el-table-column prop="status" label="登记状态" width="100" />
            <el-table-column prop="assets" label="占用资产" />
            <el-table-column prop="remark" label="保留说明" />
          </el-table>
          <h4 class="sec">
            不在任何已登记网段内（{{ conflicts.outsidePrefix.length }}）
            <span v-if="!conflicts.prefixConfigured" class="sub">— 尚未登记网段，跳过</span>
          </h4>
          <el-table :data="conflicts.outsidePrefix" border size="small" max-height="300">
            <el-table-column prop="ip" label="IP" width="150" />
            <el-table-column prop="assetNo" label="资产" />
            <el-table-column prop="ipRole" label="角色" width="80" />
          </el-table>
        </template>
      </el-tab-pane>

      <el-tab-pane label="IP 反查" name="lookup">
        <div class="page-toolbar">
          <el-input v-model="lookupText" placeholder="输入 IP，如 10.1.2.3" style="width: 260px" @keyup.enter="doLookup" />
          <el-button type="primary" @click="doLookup">查询</el-button>
        </div>
        <template v-if="lookupResult">
          <el-descriptions :column="1" border size="small" style="max-width: 760px">
            <el-descriptions-item label="IP">{{ lookupResult.ip }}</el-descriptions-item>
            <el-descriptions-item label="登记">
              <template v-if="lookupResult.record">
                {{ ADDR_STATUS_LABEL[lookupResult.record.status] }} · {{ lookupResult.record.source === 'ASSET' ? '资产同步' : '手工' }}
                {{ lookupResult.record.hostname || '' }} {{ lookupResult.record.remark || '' }}
              </template>
              <span v-else class="sub">未登记（可用）</span>
            </el-descriptions-item>
            <el-descriptions-item label="使用资产">
              <div v-for="a in lookupResult.assets" :key="a.assetNo">
                {{ a.assetNo }} {{ a.name }}（{{ a.ipRole === 'biz' ? '业务' : '管理' }} IP，{{ a.rackCode || '未上架' }}，{{ a.statusBiz }}）
              </div>
              <span v-if="!lookupResult.assets.length" class="sub">无</span>
            </el-descriptions-item>
            <el-descriptions-item label="所属网段">
              <span v-for="p in lookupResult.prefixes" :key="p.id" style="margin-right: 12px">{{ p.prefix }} {{ p.name }}</span>
              <span v-if="!lookupResult.prefixes.length" class="sub">不在任何已登记网段内</span>
            </el-descriptions-item>
          </el-descriptions>
        </template>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="pDialog" :title="pEditing ? '编辑网段' : '新增网段'" width="520px">
      <el-form label-width="80px">
        <el-form-item label="网段" required><el-input v-model="pForm.prefix" placeholder="10.10.1.0/24" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="pForm.name" placeholder="如 A 机房业务网" /></el-form-item>
        <el-form-item label="用途">
          <el-select v-model="pForm.purpose" style="width: 100%">
            <el-option v-for="(l, k) in PURPOSE_LABEL" :key="k" :label="l" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="VLAN">
          <el-select v-model="pForm.vlanRef" clearable filterable style="width: 100%">
            <el-option v-for="v in vlans" :key="v.id" :label="`${v.vlanId} ${v.name}${v.roomCode ? '（' + v.roomCode + '）' : ''}`" :value="v.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="网关"><el-input v-model="pForm.gateway" placeholder="10.10.1.254" /></el-form-item>
        <el-form-item label="机房"><el-input v-model="pForm.roomCode" placeholder="可选" /></el-form-item>
        <el-form-item label="DNS"><el-input v-model="pForm.dns" placeholder="可选，逗号分隔" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="pForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pDialog = false">取消</el-button>
        <el-button type="primary" @click="savePrefix">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="aDialog" :title="`分配地址（${current?.prefix}）`" width="520px">
      <el-form label-width="80px">
        <el-form-item label="方式">
          <el-radio-group v-model="aForm.mode">
            <el-radio value="asset">分配给资产</el-radio>
            <el-radio value="reserve">保留 / DHCP</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="IP"><el-input v-model="aForm.ip" placeholder="留空则自动取下一个可用地址" /></el-form-item>
        <template v-if="aForm.mode === 'asset'">
          <el-form-item label="资产" required><AssetPicker v-model="aForm.assetNo as any" style="width: 100%" /></el-form-item>
          <el-form-item label="写入">
            <el-radio-group v-model="aForm.ipRole">
              <el-radio value="biz">业务 IP</el-radio>
              <el-radio value="mgmt">管理 IP</el-radio>
            </el-radio-group>
          </el-form-item>
        </template>
        <el-form-item v-else label="状态">
          <el-radio-group v-model="aForm.status">
            <el-radio value="RESERVED">保留</el-radio>
            <el-radio value="DHCP">DHCP 池</el-radio>
            <el-radio value="USED">已用（非资产，如 VIP）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="主机名"><el-input v-model="aForm.hostname" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="aForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aDialog = false">取消</el-button>
        <el-button type="primary" :disabled="aForm.mode === 'asset' && !aForm.assetNo" @click="saveAllocate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="vDialog" :title="vEditing ? '编辑 VLAN' : '新增 VLAN'" width="460px">
      <el-form label-width="80px">
        <el-form-item label="VLAN ID" required><el-input-number v-model="vForm.vlanId" :min="1" :max="4094" /></el-form-item>
        <el-form-item label="名称" required><el-input v-model="vForm.name" /></el-form-item>
        <el-form-item label="机房"><el-input v-model="vForm.roomCode" placeholder="留空 = 全局" /></el-form-item>
        <el-form-item label="用途">
          <el-select v-model="vForm.purpose" clearable style="width: 100%">
            <el-option v-for="(l, k) in PURPOSE_LABEL" :key="k" :label="l" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="vForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="vDialog = false">取消</el-button>
        <el-button type="primary" @click="saveVlan">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.sec {
  margin: 16px 0 8px;
  font-size: 14px;
}
.nowrap {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.free {
  margin-top: 10px;
  font-size: 13px;
}
.free-tag {
  margin-right: 6px;
  cursor: pointer;
}
</style>
