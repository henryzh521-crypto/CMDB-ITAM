<script setup lang="ts">
/**
 * 资产远程选择器（按编号 / 名称 / SN 模糊检索，支持多选）。
 * v-model 为资产编号（单选 string / 多选 string[]）。
 */
import { ref, watch } from 'vue'
import { fetchAssets } from '@/api/asset'
import type { AssetItem } from '@/api/asset'

const props = withDefaults(
  defineProps<{
    modelValue: string | string[] | undefined
    multiple?: boolean
    placeholder?: string
    disabled?: boolean
  }>(),
  { multiple: false, placeholder: '输入资产编号 / 名称 / SN 检索', disabled: false },
)
const emit = defineEmits<{ (e: 'update:modelValue', v: string | string[] | undefined): void }>()

const options = ref<AssetItem[]>([])
const loading = ref(false)
const inner = ref<string | string[] | undefined>(props.modelValue)

watch(
  () => props.modelValue,
  (v) => {
    inner.value = v
  },
)

async function remote(q: string) {
  loading.value = true
  try {
    const res = await fetchAssets({ page: 1, size: 20, keyword: q || undefined })
    options.value = res.records ?? []
  } catch {
    options.value = []
  } finally {
    loading.value = false
  }
}

function onChange(v: string | string[] | undefined) {
  emit('update:modelValue', v)
}
</script>

<template>
  <el-select
    v-model="inner"
    :multiple="multiple"
    filterable
    remote
    clearable
    reserve-keyword
    :remote-method="remote"
    :loading="loading"
    :placeholder="placeholder"
    :disabled="disabled"
    style="width: 100%"
    @focus="options.length === 0 && remote('')"
    @change="onChange"
  >
    <el-option v-for="a in options" :key="a.assetNo" :label="`${a.assetNo} ${a.name}`" :value="a.assetNo">
      <span>{{ a.assetNo }}</span>
      <span style="color: var(--el-text-color-secondary); margin-left: 8px">{{ a.name }}</span>
      <span v-if="a.statusBiz" style="float: right; color: var(--el-text-color-placeholder); font-size: 12px">
        {{ a.statusBiz }}
      </span>
    </el-option>
  </el-select>
</template>
