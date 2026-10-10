<script setup lang="ts">
/**
 * 线缆管理：端口到端口的物理连接。数据线自动生成 CI「网络连接」关系，电源线生成「供电来自」关系，
 * 影响分析因此能看到真实物理拓扑；拆除最后一根线时关系自动结束。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AssetPicker from '@/components/AssetPicker.vue'
import { CABLE_STATUS_LABEL, createCable, deleteCable, fetchCableMeta, fetchCables, removeCable, updateCable } from '@/api/network'
import type { Cable, CablePayload } from '@/api/network'

const auth = useAuthStore()
/** v3.4：删除需角色显式授予（后端同样校验） */
const canDelete = computed(() => auth.canDelete('network'))
const route = useRoute()
const canWrite = computed(() => auth.canWrite('network'))
const types = ref<Record<string, string>>({})

const q = reactive({
  assetNo: (typeof route.query.assetNo === 'string' ? route.query.assetNo : undefined) as string | undefined,
  rackCode: '',
  type: '',
  status: '',
  keyword: '',
  page: 1,
  size: 20,
})
const rows = ref<Cable[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await fetchCables({
      assetNo: q.assetNo || undefined,
      rackCode: q.rackCode || undefined,
      type: q.type || undefined,
      status: q.status || undefined,
      keyword: q.keyword || undefined,
      page: q.page,
      size: q.size,
    })
    rows.value = r.records
    total.value = r.total
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}
function search() {
  q.page = 1
  void load()
}

const dialog = ref(false)
const editing = ref<number | null>(null)
const empty = (): CablePayload => ({
  cableNo: '',
  cableType: 'COPPER',
  aAssetNo: '',
  aPort: '',
  bAssetNo: '',
  bPort: '',
  lengthM: undefined,
  color: '',
  speed: '',
  status: 'CONNECTED',
  remark: '',
})
const form = reactive<CablePayload>(empty())
const keepOpen = ref(false)

function open(c?: Cable) {
  editing.value = c?.id ?? null
  Object.assign(form, empty(), c ? { ...c } : { aAssetNo: q.assetNo || '' })
  dialog.value = true
}

async function save() {
  if (!form.aAssetNo || !form.bAssetNo || !form.aPort || !form.bPort) {
    ElMessage.warning('请填写两端设备与端口')
    return
  }
  try {
    const body = { ...form, cableNo: form.cableNo || '', lengthM: form.lengthM || undefined }
    if (editing.value) await updateCable(editing.value, body)
    else await createCable(body)
    ElMessage.success('已保存')
    if (keepOpen.value && !editing.value) {
      // 连续登记：保留两端设备与线型，端口清空
      form.aPort = ''
      form.bPort = ''
      form.cableNo = ''
    } else {
      dialog.value = false
    }
    void load()
  } catch {
    // ignore
  }
}

async function doRemove(c: Cable) {
  try {
    await ElMessageBox.confirm(`拆除线缆 ${c.cableNo}？记录将保留为「已拆除」，端口释放`, '拆除', { type: 'warning' })
    await removeCable(c.id)
    void load()
  } catch {
    // 取消
  }
}
async function doDelete(c: Cable) {
  try {
    await ElMessageBox.confirm(`删除线缆记录 ${c.cableNo}？（误录时使用；正常下线请用「拆除」）`, '删除', { type: 'warning' })
    await deleteCable(c.id)
    void load()
  } catch {
    // 取消
  }
}

function end(name?: string, rack?: string, unit?: number) {
  return [name, rack ? `${rack}${unit ? ' U' + unit : ''}` : ''].filter(Boolean).join(' · ')
}

onMounted(async () => {
  try {
    types.value = (await fetchCableMeta()).types
  } catch {
    // ignore
  }
  void load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">线缆管理</div>
    <div class="page-toolbar">
      <AssetPicker v-model="q.assetNo as any" placeholder="按设备筛选" style="width: 240px" @update:model-value="search" />
      <el-input v-model="q.rackCode" placeholder="机柜编码" clearable style="width: 140px" @keyup.enter="search" @clear="search" />
      <el-select v-model="q.type" placeholder="类型" clearable style="width: 140px" @change="search">
        <el-option v-for="(l, k) in types" :key="k" :label="l" :value="k" />
      </el-select>
      <el-select v-model="q.status" placeholder="在用（不含已拆除）" clearable style="width: 160px" @change="search">
        <el-option v-for="(l, k) in CABLE_STATUS_LABEL" :key="k" :label="l" :value="k" />
      </el-select>
      <el-input v-model="q.keyword" placeholder="线缆编号 / 端口 / 备注" clearable style="width: 180px" @keyup.enter="search" @clear="search" />
      <el-button type="primary" plain @click="search">查询</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite" type="primary" :icon="'Plus'" @click="open()">登记线缆</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe size="small">
      <el-table-column label="编号 / 类型" width="170">
        <template #default="{ row }">
          {{ row.cableNo }}
          <div class="sub">{{ types[row.cableType] || row.cableType }} {{ row.speed || '' }} {{ row.lengthM ? row.lengthM + 'm' : '' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="A 端" min-width="220">
        <template #default="{ row }">
          {{ row.aAssetNo }} <b class="port">{{ row.aPort }}</b>
          <div class="sub">{{ end(row.aAssetName, row.aRack, row.aUnit) }}</div>
        </template>
      </el-table-column>
      <el-table-column width="40" align="center">
        <template #default="{ row }">{{ row.cableType === 'POWER' ? '⚡' : '⇄' }}</template>
      </el-table-column>
      <el-table-column label="B 端" min-width="220">
        <template #default="{ row }">
          {{ row.bAssetNo }} <b class="port">{{ row.bPort }}</b>
          <div class="sub">{{ end(row.bAssetName, row.bRack, row.bUnit) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'CONNECTED' ? 'success' : row.status === 'PLANNED' ? 'warning' : 'info'">
            {{ CABLE_STATUS_LABEL[row.status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      <el-table-column v-if="canWrite" label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">编辑</el-button>
          <el-button v-if="row.status !== 'REMOVED'" link type="warning" @click="doRemove(row)">拆除</el-button>
          <el-button v-if="canDelete" link type="danger" @click="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="page-pagination">
      <el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑线缆' : '登记线缆'" width="640px">
      <el-form label-width="80px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="form.cableType" style="width: 100%">
                <el-option v-for="(l, k) in types" :key="k" :label="l" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="编号"><el-input v-model="form.cableNo" :disabled="!!editing" placeholder="留空自动生成" /></el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="A 端设备" required><AssetPicker v-model="form.aAssetNo as any" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="端口" label-width="50px" required><el-input v-model="form.aPort" placeholder="eth0 / PSU1" /></el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="form.cableType === 'POWER' ? 'B 端 PDU' : 'B 端设备'" required>
              <AssetPicker v-model="form.bAssetNo as any" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="端口" label-width="50px" required><el-input v-model="form.bPort" placeholder="Ge1/0/1 / C13-08" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="长度(m)"><el-input-number v-model="form.lengthM" :min="0" :controls="false" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="速率" label-width="50px"><el-input v-model="form.speed" placeholder="25G / 400G" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="颜色" label-width="50px"><el-input v-model="form.color" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio value="PLANNED">规划</el-radio>
                <el-radio value="CONNECTED">已连接</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
          </el-col>
        </el-row>
        <div v-if="form.cableType === 'POWER'" class="sub" style="margin-left: 80px">电源线：A 端为用电设备，B 端为 PDU / 电源，自动生成「A 供电来自 B」关系</div>
      </el-form>
      <template #footer>
        <el-checkbox v-if="!editing" v-model="keepOpen" style="float: left">保存后继续登记</el-checkbox>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.port {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-weight: 600;
  margin-left: 4px;
}
</style>
