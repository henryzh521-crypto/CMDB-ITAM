<script setup lang="ts">
/**
 * 知识库：检索 / 阅读 / 评价；有写权限可新建、编辑（Markdown，所见即所得预览）、发布、归档。
 * 支持 ?kb=KB-000001 直达文章（告警、工单、问题中的推荐链接）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { renderMarkdown } from '@/utils/markdown'
import {
  KB_CATEGORY_LABEL,
  KB_STATUS_LABEL,
  createKb,
  deleteKb,
  feedbackKb,
  fetchKb,
  fetchKbArticle,
  updateKb,
} from '@/api/itsm'
import type { KbArticle, KbPayload } from '@/api/itsm'

const auth = useAuthStore()
/** v3.4：删除需角色显式授予（后端同样校验） */
const canDelete = computed(() => auth.canDelete('knowledge'))
const route = useRoute()
const router = useRouter()
const canWrite = computed(() => auth.canWrite('knowledge'))

const q = reactive({ keyword: '', category: '', status: '', tag: '', page: 1, size: 20 })
const rows = ref<KbArticle[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const r = await fetchKb({
      keyword: q.keyword || undefined,
      category: q.category || undefined,
      status: q.status || undefined,
      tag: q.tag || undefined,
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
function byTag(t: string) {
  q.tag = t
  search()
}

/* 阅读 */
const drawer = ref(false)
const article = ref<KbArticle | null>(null)
const html = computed(() => renderMarkdown(article.value?.content))
const voted = ref<boolean | null>(null)
async function open(idOrNo: string | number) {
  try {
    article.value = await fetchKbArticle(idOrNo)
    voted.value = null
    drawer.value = true
  } catch {
    // ignore
  }
}
async function vote(helpful: boolean) {
  if (!article.value) return
  try {
    const r = await feedbackKb(article.value.id, helpful)
    article.value.helpful = r.helpful
    article.value.unhelpful = r.unhelpful
    voted.value = helpful
    ElMessage.success('感谢反馈')
  } catch {
    // ignore
  }
}

/* 编辑 */
const editor = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<KbPayload>({
  title: '',
  category: 'TROUBLESHOOT',
  content: '',
  status: 'DRAFT',
  tags: [],
  alertnames: [],
  modelCodes: [],
  categoryCodes: [],
})
const preview = computed(() => renderMarkdown(form.content))
const TEMPLATE = `## 现象

（告警 / 报错 / 用户反馈）

## 影响范围

## 排查步骤

1.
2.

## 处理方法

## 注意事项
`
function openEditor(a?: KbArticle) {
  editingId.value = a?.id ?? null
  Object.assign(form, a
    ? {
        title: a.title,
        category: a.category,
        content: a.content || '',
        status: a.status,
        tags: [...(a.tags || [])],
        alertnames: [...(a.alertnames || [])],
        modelCodes: [...(a.modelCodes || [])],
        categoryCodes: [...(a.categoryCodes || [])],
      }
    : { title: '', category: 'TROUBLESHOOT', content: TEMPLATE, status: 'DRAFT', tags: [], alertnames: [], modelCodes: [], categoryCodes: [] })
  editor.value = true
}
async function save(status?: string) {
  saving.value = true
  try {
    const body = { ...form, status: status || form.status }
    const r = editingId.value ? await updateKb(editingId.value, body) : await createKb(body)
    ElMessage.success(body.status === 'PUBLISHED' ? '已发布' : '已保存')
    editor.value = false
    void load()
    if (drawer.value && article.value?.id === r.id) article.value = { ...r }
  } catch {
    // ignore
  } finally {
    saving.value = false
  }
}
async function remove(a: KbArticle) {
  try {
    await ElMessageBox.confirm(`删除文章「${a.title}」？`, '删除', { type: 'warning' })
    await deleteKb(a.id)
    drawer.value = false
    void load()
  } catch {
    // 取消
  }
}

onMounted(() => {
  void load()
  const kb = route.query.kb
  if (typeof kb === 'string' && kb) void open(kb)
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">知识库</div>
    <div class="page-toolbar">
      <el-input v-model="q.keyword" placeholder="搜索标题、正文、标签、告警名、型号" clearable style="width: 320px" @keyup.enter="search" @clear="search">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="q.category" placeholder="分类" clearable style="width: 130px" @change="search">
        <el-option v-for="(l, k) in KB_CATEGORY_LABEL" :key="k" :label="l" :value="k" />
      </el-select>
      <el-select v-if="canWrite" v-model="q.status" placeholder="状态（不含归档）" clearable style="width: 160px" @change="search">
        <el-option v-for="(l, k) in KB_STATUS_LABEL" :key="k" :label="l" :value="k" />
      </el-select>
      <el-tag v-if="q.tag" closable @close="(q.tag = ''), search()"># {{ q.tag }}</el-tag>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite" type="primary" :icon="'Plus'" @click="openEditor()">写文章</el-button>
    </div>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && !rows.length" description="没有找到相关文章" />
      <div v-for="a in rows" :key="a.id" class="item" @click="open(a.id)">
        <div class="head">
          <el-tag size="small" :type="a.category === 'KNOWN_ERROR' ? 'danger' : a.category === 'SOP' ? 'success' : 'primary'" effect="plain">
            {{ KB_CATEGORY_LABEL[a.category] }}
          </el-tag>
          <span class="title">{{ a.title }}</span>
          <el-tag v-if="a.status !== 'PUBLISHED'" size="small" type="info">{{ KB_STATUS_LABEL[a.status] }}</el-tag>
        </div>
        <div class="excerpt">{{ a.excerpt }}</div>
        <div class="meta">
          <span>{{ a.kbNo }}</span>
          <span>{{ a.updatedBy || a.createdBy }} · {{ (a.updatedAt || '').slice(0, 10) }}</span>
          <span>阅读 {{ a.views }}</span>
          <span>👍 {{ a.helpful }}</span>
          <span v-for="t in a.tags" :key="t" class="tag" @click.stop="byTag(t)">#{{ t }}</span>
        </div>
      </div>
    </div>
    <div class="page-pagination">
      <el-pagination v-model:current-page="q.page" :page-size="q.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-drawer v-model="drawer" size="760px" :title="article?.title">
      <template v-if="article">
        <div class="meta" style="margin-bottom: 12px">
          <el-tag size="small" effect="plain">{{ KB_CATEGORY_LABEL[article.category] }}</el-tag>
          <span>{{ article.kbNo }}</span>
          <span>{{ article.updatedBy || article.createdBy }} 更新于 {{ (article.updatedAt || '').replace('T', ' ').slice(0, 16) }}</span>
          <span>阅读 {{ article.views }}</span>
          <el-link v-if="article.sourceProblem" type="primary" @click="router.push({ path: '/problems', query: { prb: article.sourceProblem } })">
            来源问题 {{ article.sourceProblem }}
          </el-link>
        </div>
        <div v-if="article.alertnames?.length || article.modelCodes?.length" class="scope">
          适用：
          <el-tag v-for="x in article.alertnames" :key="'a' + x" size="small" type="warning">告警 {{ x }}</el-tag>
          <el-tag v-for="x in article.modelCodes" :key="'m' + x" size="small">型号 {{ x }}</el-tag>
        </div>
        <!-- renderMarkdown 先整体转义再生成受限标签，正文内的原始 HTML 不会被执行 -->
        <!-- eslint-disable-next-line vue/no-v-html -->
        <div class="md" v-html="html"></div>
        <el-divider />
        <div class="feedback">
          这篇文章对你有帮助吗？
          <el-button size="small" :type="voted === true ? 'success' : ''" @click="vote(true)">👍 有用（{{ article.helpful }}）</el-button>
          <el-button size="small" :type="voted === false ? 'warning' : ''" @click="vote(false)">👎 没用（{{ article.unhelpful }}）</el-button>
          <div style="flex: 1"></div>
          <template v-if="canWrite">
            <el-button size="small" type="primary" plain @click="openEditor(article)">编辑</el-button>
            <el-button v-if="canDelete" size="small" type="danger" plain @click="remove(article)">删除</el-button>
          </template>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="editor" :title="editingId ? '编辑文章' : '写文章'" width="1100px" top="4vh">
      <el-form label-width="70px">
        <el-row :gutter="12">
          <el-col :span="16"><el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" show-word-limit /></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item label="分类">
              <el-select v-model="form.category" style="width: 100%">
                <el-option v-for="(l, k) in KB_CATEGORY_LABEL" :key="k" :label="l" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="标签">
              <el-select v-model="form.tags" multiple filterable allow-create default-first-option placeholder="回车添加" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="适用告警">
              <el-select v-model="form.alertnames" multiple filterable allow-create default-first-option placeholder="如 HostDown" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="适用型号">
              <el-select v-model="form.modelCodes" multiple filterable allow-create default-first-option placeholder="型号编码" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="editor">
          <el-input v-model="form.content" type="textarea" :rows="22" placeholder="支持 Markdown：## 标题、- 列表、1. 步骤、`代码`、```代码块```、**粗体**、[链接](https://…)、| 表格 |" />
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div class="md preview" v-html="preview"></div>
        </div>
      </el-form>
      <template #footer>
        <span class="sub" style="float: left">适用告警 / 型号用于在告警详情、问题单中自动推荐本文</span>
        <el-button @click="editor = false">取消</el-button>
        <el-button :loading="saving" @click="save('DRAFT')">存草稿</el-button>
        <el-button v-if="form.status === 'PUBLISHED'" :loading="saving" @click="save('ARCHIVED')">归档</el-button>
        <el-button type="primary" :loading="saving" @click="save('PUBLISHED')">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.list {
  min-height: 120px;
}
.item {
  padding: 14px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}
.item:hover {
  background: var(--el-fill-color-light);
}
.head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.title {
  font-size: 15px;
  font-weight: 600;
}
.excerpt {
  margin: 6px 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.tag {
  color: var(--el-color-primary);
}
.scope {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
  font-size: 12px;
}
.feedback {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.editor {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.preview {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px 14px;
  height: 474px;
  overflow: auto;
}
.md :deep(h2),
.md :deep(h3),
.md :deep(h4),
.md :deep(h5) {
  margin: 16px 0 8px;
}
.md :deep(p) {
  line-height: 1.75;
  margin: 8px 0;
}
.md :deep(li) {
  line-height: 1.75;
}
.md :deep(pre) {
  background: #1e1e1e;
  color: #e6e6e6;
  padding: 10px 12px;
  border-radius: 4px;
  overflow: auto;
  font-size: 12px;
}
.md :deep(code) {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  background: var(--el-fill-color);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
.md :deep(pre code) {
  background: none;
  padding: 0;
}
.md :deep(blockquote) {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 4px solid var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-regular);
}
.md :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
}
.md :deep(th),
.md :deep(td) {
  border: 1px solid var(--el-border-color);
  padding: 4px 10px;
}
</style>
