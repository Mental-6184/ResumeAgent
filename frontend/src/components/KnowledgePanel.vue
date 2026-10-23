<template>
  <div class="knowledge-panel">
    <header class="panel-header">
      <div class="header-left">
        <h1 class="header-title">知识库</h1>
        <p class="header-subtitle">上传简历、项目文档、技术博客，AI 的回答将基于这里的内容。上传的文档会持久化保存，重启不丢失。</p>
      </div>
      <div class="header-right">
        <span class="doc-count">{{ documents.length }} 个文档</span>
      </div>
    </header>

    <div class="panel-body">
      <!-- 上传卡片 -->
      <div class="form-card">
        <div class="card-head">
          <h3>上传文档</h3>
          <span class="card-hint">支持 PDF / TXT / MD，单个文件不超过 10MB</span>
        </div>
        <div class="upload-row">
          <button class="btn btn-upload" :disabled="uploading" @click="triggerUpload">
            {{ uploading ? '解析中…' : '⬆ 选择文件上传' }}
          </button>
          <select v-model="uploadType" class="input type-select">
            <option value="project">项目文档</option>
            <option value="blog">技术博客</option>
            <option value="resume">简历/经历</option>
            <option value="readme">项目说明</option>
          </select>
          <input ref="fileInputRef" type="file" accept=".pdf,.txt,.md,.text" class="hidden-input" @change="onFileChange" />
        </div>
      </div>

      <!-- 粘贴文本 -->
      <div class="form-card">
        <div class="card-head">
          <h3>粘贴文本</h3>
          <span class="card-hint">没有文件时可直接粘贴内容保存为文档</span>
        </div>
        <div class="form-grid">
          <label class="field"><span>文档名称</span><input v-model="pasteName" class="input" placeholder="如：FundPilot 项目文档" /></label>
          <label class="field">
            <span>文档类型</span>
            <select v-model="pasteType" class="input">
              <option value="project">项目文档</option>
              <option value="blog">技术博客</option>
              <option value="resume">简历/经历</option>
              <option value="readme">项目说明</option>
            </select>
          </label>
        </div>
        <label class="field field-block">
          <span>文档内容</span>
          <textarea v-model="pasteContent" class="textarea" rows="5" placeholder="粘贴 Markdown / 文本内容…"></textarea>
        </label>
        <div class="paste-actions">
          <button class="btn btn-primary" :disabled="saving || !pasteName.trim() || !pasteContent.trim()" @click="savePaste">
            {{ saving ? '保存中…' : '保存到知识库' }}
          </button>
        </div>
      </div>

      <!-- 文档列表 -->
      <div class="form-card">
        <div class="card-head">
          <h3>文档列表</h3>
          <span class="card-hint">内置文档随项目启动自动加载，不可删除</span>
        </div>
        <div v-if="documents.length === 0" class="list-empty">知识库为空，上传或粘贴文档开始使用</div>
        <div v-for="doc in documents" :key="doc.sourceName" class="doc-row">
          <div class="doc-info">
            <div class="doc-name">
              {{ doc.sourceName }}
              <span v-if="doc.builtin" class="doc-tag builtin">内置</span>
              <span v-else class="doc-tag manual">上传</span>
            </div>
            <div class="doc-meta">{{ typeLabel(doc.sourceType) }} · {{ doc.chunks }} 个片段<span v-if="doc.uploadedAt"> · {{ formatTime(doc.uploadedAt) }}</span></div>
          </div>
          <button
            v-if="!doc.builtin"
            class="btn-mini btn-mini-danger"
            title="删除文档"
            @click="removeDoc(doc)"
          >删除</button>
        </div>
      </div>
    </div>

    <!-- 轻提示 -->
    <transition name="toast">
      <div v-if="toast" class="toast" :class="toastType">{{ toast }}</div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { deleteDocument, listDocuments, saveDocument, uploadDocument } from '../api/rag'
import type { KnowledgeDocumentInfo } from '../types'

const documents = ref<KnowledgeDocumentInfo[]>([])
const uploading = ref(false)
const saving = ref(false)
const uploadType = ref('project')
const pasteName = ref('')
const pasteType = ref('project')
const pasteContent = ref('')
const toast = ref('')
const toastType = ref<'success' | 'error'>('success')
const fileInputRef = ref<HTMLInputElement | null>(null)

function showToast(message: string, type: 'success' | 'error' = 'success') {
  toast.value = message
  toastType.value = type
  setTimeout(() => { if (toast.value === message) toast.value = '' }, 2600)
}

function typeLabel(type: string): string {
  const map: Record<string, string> = {
    project: '项目文档', blog: '技术博客', resume: '简历/经历', readme: '项目说明', builtin: '内置资料',
  }
  return map[type] || type
}

function formatTime(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function load() {
  try {
    documents.value = await listDocuments()
  } catch {
    showToast('加载文档列表失败，请确认后端已启动', 'error')
  }
}

function triggerUpload() {
  fileInputRef.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    const res = await uploadDocument(file, uploadType.value)
    showToast(`${res.message}（${res.chunks} 个片段）`)
    await load()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    showToast(msg || '上传失败，请检查文件格式', 'error')
  } finally {
    uploading.value = false
  }
}

async function savePaste() {
  saving.value = true
  try {
    const res = await saveDocument({
      sourceName: pasteName.value.trim(),
      sourceType: pasteType.value,
      content: pasteContent.value,
    })
    showToast(`${res.message}（${res.chunks} 个片段）`)
    pasteName.value = ''
    pasteContent.value = ''
    await load()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    showToast(msg || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function removeDoc(doc: KnowledgeDocumentInfo) {
  if (!window.confirm(`确定删除文档「${doc.sourceName}」吗？删除后 AI 将不再引用它的内容。`)) return
  try {
    await deleteDocument(doc.sourceName)
    showToast('已删除')
    await load()
  } catch {
    showToast('删除失败', 'error')
  }
}

onMounted(load)
</script>

<style scoped>
.knowledge-panel {
  position: relative;
  height: 100%;
  overflow-y: auto;
  background: #f7f8fa;
  color: #1f2329;
}

.panel-header {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
  padding: 28px 32px 20px; background: #fff; border-bottom: 1px solid #e5e6eb;
}
.header-title { margin: 0; font-size: 22px; font-weight: 600; }
.header-subtitle { margin: 6px 0 0; font-size: 13px; color: #646a73; max-width: 620px; line-height: 1.6; }
.header-right { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.doc-count {
  padding: 6px 12px; border-radius: 999px; background: #eef2ff; color: #2456e6;
  font-size: 12px; font-weight: 500;
}

.panel-body {
  display: flex; flex-direction: column; gap: 16px;
  padding: 20px 32px 40px; max-width: 960px;
}

.form-card {
  background: #fff; border: 1px solid #e5e6eb; border-radius: 12px; padding: 20px 22px;
}
.card-head {
  display: flex; align-items: baseline; gap: 10px; margin-bottom: 16px;
  padding-bottom: 12px; border-bottom: 1px solid #f2f3f5;
}
.card-head h3 { margin: 0; font-size: 14px; font-weight: 600; }
.card-hint { font-size: 11px; color: #8f959e; }

.upload-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.type-select { width: 160px; }
.hidden-input { display: none; }

.form-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; margin-bottom: 12px;
}
.field { display: flex; flex-direction: column; gap: 6px; }
.field > span { font-size: 12px; color: #646a73; font-weight: 500; }
.field-block { margin-bottom: 12px; }

.input {
  border: 1px solid #d0d3d9; border-radius: 8px; padding: 9px 12px;
  font-size: 13px; color: #1f2329; background: #fff; outline: none; width: 100%;
  transition: border-color .18s, box-shadow .18s;
}
.input:focus { border-color: #2456e6; box-shadow: 0 0 0 3px rgba(36, 86, 230, .12); }
.textarea {
  border: 1px solid #d0d3d9; border-radius: 8px; padding: 10px 12px;
  font-size: 13px; color: #1f2329; background: #fff; outline: none;
  resize: vertical; width: 100%; line-height: 1.7; font-family: inherit;
  transition: border-color .18s, box-shadow .18s; box-sizing: border-box;
}
.textarea:focus { border-color: #2456e6; box-shadow: 0 0 0 3px rgba(36, 86, 230, .12); }

.paste-actions { display: flex; justify-content: flex-end; }

.list-empty { font-size: 12px; color: #8f959e; padding: 8px 4px; }
.doc-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 12px 4px; border-bottom: 1px solid #f2f3f5;
}
.doc-row:last-child { border-bottom: none; }
.doc-name { font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.doc-tag { font-size: 10px; padding: 1px 7px; border-radius: 999px; font-weight: 500; }
.doc-tag.builtin { background: #f2f3f5; color: #646a73; }
.doc-tag.manual { background: #eef2ff; color: #2456e6; }
.doc-meta { font-size: 11px; color: #8f959e; margin-top: 3px; }

.btn {
  border: none; border-radius: 8px; padding: 10px 18px;
  font-size: 13px; font-weight: 500; cursor: pointer; transition: all .18s ease; white-space: nowrap;
}
.btn:disabled { opacity: .55; cursor: not-allowed; }
.btn-primary { background: #2456e6; color: #fff; }
.btn-primary:hover:not(:disabled) { background: #1d47c4; box-shadow: 0 4px 12px rgba(36, 86, 230, .28); }
.btn-upload { background: #eef2ff; color: #2456e6; border: 1px dashed #2456e6; }
.btn-upload:hover:not(:disabled) { background: #dfe7ff; }
.btn-mini {
  border: 1px solid #d0d3d9; background: #fff; color: #646a73;
  font-size: 11px; padding: 3px 10px; border-radius: 6px; cursor: pointer; transition: all .15s;
}
.btn-mini:hover { border-color: #2456e6; color: #2456e6; }
.btn-mini-danger:hover { border-color: #d83931; color: #d83931; }

.toast {
  position: fixed; top: 24px; left: 50%; transform: translateX(-50%);
  padding: 10px 20px; border-radius: 8px; font-size: 13px; z-index: 100;
  box-shadow: 0 6px 20px rgba(31, 35, 41, .16);
}
.toast.success { background: #1f2329; color: #fff; }
.toast.error { background: #d83931; color: #fff; }
.toast-enter-active, .toast-leave-active { transition: opacity .25s, transform .25s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, -8px); }
</style>
