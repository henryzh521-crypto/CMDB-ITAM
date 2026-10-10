<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Camera, Tools } from '@element-plus/icons-vue'
import { lookupAssets } from '@/api/wecom'
import type { AssetLookupItem } from '@/api/wecom'
import { createWorkorder } from '@/api/workorder'

const keyword = ref('')
const loading = ref(false)
const searched = ref(false)
const list = ref<AssetLookupItem[]>([])
const expandedNo = ref('')
const repairingNo = ref('')

function bizTagType(statusBiz?: string): 'success' | 'warning' | 'danger' | 'info' {
  if (statusBiz === '在用') return 'success'
  if (statusBiz === '维修') return 'warning'
  if (statusBiz === '报废') return 'danger'
  return 'info'
}

function locationText(item: AssetLookupItem): string {
  const parts = [item.roomCode, item.rackCode, item.unitNo].filter((v) => !!v)
  return parts.length ? parts.join(' / ') : '未上架'
}

async function doSearch() {
  const q = keyword.value.trim()
  if (!q) {
    ElMessage.warning('请输入资产编号 / SN / 名称')
    return
  }
  loading.value = true
  try {
    const res = await lookupAssets(q)
    list.value = res || []
    expandedNo.value = ''
    searched.value = true
    if (list.value.length === 0) ElMessage.info('未找到匹配资产')
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/** 企微扫码（未接入 jweixin sdk，仅宽松探测企微环境） */
function onScan() {
  const wx = (window as any).wx
  if (wx && typeof wx.scanQRCode === 'function') {
    wx.scanQRCode({
      desc: '扫码查资产',
      needResult: 1,
      scanType: ['qrCode', 'barCode'],
      success: (res: { resultStr?: string }) => {
        const text = (res.resultStr || '').trim()
        if (text) {
          keyword.value = text
          void doSearch()
        }
      },
    })
  } else {
    ElMessage.warning('当前环境不支持扫码，请手动输入')
  }
}

function toggleExpand(item: AssetLookupItem) {
  expandedNo.value = expandedNo.value === item.assetNo ? '' : item.assetNo
}

/** 故障报修：复用现有创建工单 API（类型=故障维修，关联该资产） */
async function onRepair(item: AssetLookupItem) {
  try {
    await ElMessageBox.confirm(`确认为「${item.name}」创建故障报修工单？`, '故障报修', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  repairingNo.value = item.assetNo
  try {
    await createWorkorder({
      woType: '故障维修',
      title: `${item.name} 故障报修`,
      priority: '中',
      relatedAssets: [item.assetNo],
    })
    ElMessage.success('报修工单已创建')
  } catch {
    // 拦截器已提示
  } finally {
    repairingNo.value = ''
  }
}
</script>

<template>
  <div class="m-lookup">
    <div class="m-search-bar">
      <el-input
        v-model="keyword"
        size="large"
        clearable
        placeholder="扫码或输入资产编号 / SN / 名称"
        :disabled="loading"
        @keyup.enter="doSearch"
      />
      <div class="m-search-actions">
        <el-button type="primary" size="large" :loading="loading" @click="doSearch">查询</el-button>
        <el-button size="large" plain :icon="Camera" @click="onScan">扫一扫</el-button>
      </div>
    </div>

    <div v-loading="loading" class="m-result">
      <el-empty
        v-if="searched && !loading && list.length === 0"
        description="未找到匹配资产"
      />

      <div
        v-for="item in list"
        :key="item.assetNo"
        class="m-card"
        @click="toggleExpand(item)"
      >
        <div class="m-card-head">
          <span class="m-card-name">{{ item.name }}</span>
          <el-tag size="small" :type="bizTagType(item.statusBiz)">
            {{ item.statusBiz || '未知' }}
          </el-tag>
        </div>
        <div class="m-card-row">
          <span class="m-card-label">编号</span>
          <span class="m-card-value m-mono">{{ item.assetNo }}</span>
        </div>
        <div class="m-card-row">
          <span class="m-card-label">位置</span>
          <span class="m-card-value">{{ locationText(item) }}</span>
        </div>
        <div class="m-card-row">
          <span class="m-card-label">责任人</span>
          <span class="m-card-value">{{ item.owner || '-' }}</span>
        </div>

        <div v-if="expandedNo === item.assetNo" class="m-card-detail" @click.stop>
          <div class="m-detail-title">资产详情</div>
          <div class="m-card-row">
            <span class="m-card-label">SN</span>
            <span class="m-card-value m-mono">{{ item.sn || '-' }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">财务状态</span>
            <span class="m-card-value">{{ item.statusFin || '-' }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">分类</span>
            <span class="m-card-value">{{ item.categoryCode || '-' }}</span>
          </div>
          <div class="m-card-row">
            <span class="m-card-label">型号</span>
            <span class="m-card-value m-mono">{{ item.modelCode || '-' }}</span>
          </div>
          <el-button
            type="warning"
            size="large"
            class="m-repair-btn"
            :icon="Tools"
            :loading="repairingNo === item.assetNo"
            @click="onRepair(item)"
          >
            报修
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.m-lookup {
  padding: 12px;
}

.m-search-bar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.m-search-actions {
  display: flex;
  gap: 10px;
}

.m-search-actions .el-button {
  flex: 1;
}

.m-result {
  min-height: 200px;
}

.m-card {
  background: var(--el-bg-color);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
  cursor: pointer;
}

.m-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.m-card-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.m-card-row {
  display: flex;
  font-size: 13px;
  line-height: 22px;
}

.m-card-label {
  width: 64px;
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}

.m-card-value {
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.m-mono {
  font-family: Consolas, Menlo, monospace;
}

.m-card-detail {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.m-detail-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  margin-bottom: 6px;
}

.m-repair-btn {
  width: 100%;
  margin-top: 12px;
}
</style>
