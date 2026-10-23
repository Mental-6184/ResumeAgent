<template>
  <div class="profile-panel">
    <!-- 页头 -->
    <header class="panel-header">
      <div class="header-left">
        <h1 class="header-title">我的档案</h1>
        <p class="header-subtitle">上传简历、填写网申信息，保存为模板并随时切换。AI 的所有回答都将以当前激活的档案为准。</p>
      </div>
      <div class="header-right">
        <span v-if="activeTemplate" class="active-badge">
          <span class="badge-dot"></span>
          当前使用：{{ activeTemplate.name }}
        </span>
        <button class="btn btn-primary" @click="startCreate">＋ 新建模板</button>
      </div>
    </header>

    <div class="panel-body">
      <!-- 左侧：模板列表 -->
      <aside class="template-list">
        <h2 class="list-title">模板列表 <span class="list-count">{{ templates.length }}</span></h2>
        <div v-if="templates.length === 0" class="list-empty">
          还没有模板，点击右上角「新建模板」开始创建
        </div>
        <div
          v-for="tpl in templates"
          :key="tpl.id"
          class="template-card"
          :class="{ editing: tpl.id === editingId, active: tpl.id === activeId }"
        >
          <div class="tpl-main" @click="startEdit(tpl)">
            <div class="tpl-name">
              {{ tpl.name }}
              <span v-if="tpl.id === activeId" class="tpl-active-tag">使用中</span>
            </div>
            <div class="tpl-meta">
              {{ tpl.form?.name || '未填写姓名' }} · 更新于 {{ formatTime(tpl.updatedAt) }}
            </div>
          </div>
          <div class="tpl-actions">
            <button
              v-if="tpl.id !== activeId"
              class="btn-mini"
              title="设为当前档案"
              @click="setActive(tpl)"
            >启用</button>
            <button class="btn-mini btn-mini-danger" title="删除模板" @click="removeTemplate(tpl)">删除</button>
          </div>
        </div>
      </aside>

      <!-- 右侧：编辑器 -->
      <section class="editor">
        <div v-if="!editing" class="editor-empty">
          <div class="empty-icon">📄</div>
          <h3>选择或创建一个模板</h3>
          <p>你可以为不同方向（如「后端开发」「算法岗」）维护多份档案，随时切换。</p>
          <button class="btn btn-primary" @click="startCreate">＋ 新建模板</button>
        </div>

        <template v-else>
          <!-- 模板名 + 操作 -->
          <div class="editor-toolbar">
            <input
              v-model="templateName"
              class="input input-title"
              placeholder="模板名称，如：互联网后端岗"
              maxlength="30"
            />
            <div class="toolbar-actions">
              <button v-if="editingId && !isNew" class="btn btn-ghost" @click="openVersions">🕘 版本历史</button>
              <button class="btn btn-ghost" :disabled="saving" @click="save(false)">保存</button>
              <button class="btn btn-primary" :disabled="saving" @click="save(true)">
                {{ saving ? '保存中…' : '保存并启用' }}
              </button>
            </div>
          </div>

          <!-- 简历文件 -->
          <div class="form-card">
            <div class="card-head">
              <h3>简历文件</h3>
              <span class="card-hint">支持 PDF / TXT / MD，上传后自动解析为文本</span>
            </div>
            <div class="upload-row">
              <button class="btn btn-upload" :disabled="uploading" @click="triggerUpload">
                {{ uploading ? '解析中…' : '⬆ 上传简历文件' }}
              </button>
              <span v-if="resumeFileName" class="upload-name">{{ resumeFileName }}</span>
              <input
                ref="fileInputRef"
                type="file"
                accept=".pdf,.txt,.md,.text"
                class="hidden-input"
                @change="onFileChange"
              />
            </div>
            <textarea
              v-model="resumeText"
              class="textarea resume-textarea"
              placeholder="上传 PDF 自动解析，也可以直接粘贴/编辑简历文本。这里的内容是 AI 回答问题的第一事实来源。"
              rows="10"
            ></textarea>
            <div class="char-count">{{ resumeText.length }} 字</div>
          </div>

          <!-- 基本信息 -->
          <div class="form-card">
            <div class="card-head"><h3>基本信息</h3><span class="card-hint">类似网申必填项</span></div>
            <div class="form-grid">
              <label class="field"><span>姓名</span><input v-model="form.name" class="input" placeholder="张三" /></label>
              <label class="field"><span>手机</span><input v-model="form.phone" class="input" placeholder="138xxxx0000" /></label>
              <label class="field"><span>邮箱</span><input v-model="form.email" class="input" placeholder="you@example.com" /></label>
              <label class="field"><span>城市</span><input v-model="form.city" class="input" placeholder="北京" /></label>
            </div>
          </div>

          <!-- 教育背景 -->
          <div class="form-card">
            <div class="card-head"><h3>教育背景</h3></div>
            <div class="form-grid">
              <label class="field"><span>学校</span><input v-model="form.school" class="input" placeholder="某某大学" /></label>
              <label class="field"><span>专业</span><input v-model="form.major" class="input" placeholder="软件工程" /></label>
              <label class="field">
                <span>学历</span>
                <select v-model="form.degree" class="input">
                  <option value="">请选择</option>
                  <option>专科</option><option>本科</option><option>硕士</option><option>博士</option>
                </select>
              </label>
              <label class="field"><span>毕业年份</span><input v-model="form.graduationYear" class="input" placeholder="2027" /></label>
            </div>
          </div>

          <!-- 求职意向 -->
          <div class="form-card">
            <div class="card-head"><h3>求职意向</h3></div>
            <div class="form-grid">
              <label class="field"><span>目标岗位</span><input v-model="form.targetPosition" class="input" placeholder="Java 后端开发" /></label>
              <label class="field"><span>期望薪资</span><input v-model="form.expectedSalary" class="input" placeholder="25k（选填）" /></label>
            </div>
          </div>

          <!-- 技能与经历 -->
          <div class="form-card">
            <div class="card-head"><h3>技能与经历</h3><span class="card-hint">写得越具体，AI 回答越贴合你</span></div>
            <label class="field field-block">
              <span>技能特长</span>
              <textarea v-model="form.skills" class="textarea" rows="3" placeholder="如：熟练掌握 Java、Spring Boot、MySQL、Redis；了解 RAG 与 Agent 开发"></textarea>
            </label>
            <label class="field field-block">
              <span>项目经历</span>
              <textarea v-model="form.projectExperience" class="textarea" rows="4" placeholder="项目名称、你的角色、技术方案、成果"></textarea>
            </label>
            <label class="field field-block">
              <span>实习经历</span>
              <textarea v-model="form.internshipExperience" class="textarea" rows="3" placeholder="公司、岗位、职责与成果"></textarea>
            </label>
            <label class="field field-block">
              <span>荣誉奖项</span>
              <textarea v-model="form.awards" class="textarea" rows="2" placeholder="奖学金、竞赛奖项等（选填）"></textarea>
            </label>
          </div>

          <!-- 自我评价 -->
          <div class="form-card">
            <div class="card-head"><h3>自我评价</h3></div>
            <label class="field field-block">
              <textarea v-model="form.selfEvaluation" class="textarea" rows="3" placeholder="个人特质、工作风格、职业规划"></textarea>
            </label>
          </div>

          <div class="editor-footer">
            <button class="btn btn-ghost" :disabled="saving" @click="save(false)">保存</button>
            <button class="btn btn-primary" :disabled="saving" @click="save(true)">保存并启用</button>
          </div>
        </template>
      </section>
    </div>

    <!-- 版本历史弹层 -->
    <div v-if="showVersions" class="modal-mask" @click.self="showVersions = false">
      <div class="modal">
        <div class="modal-head">
          <h3>版本历史</h3>
          <button class="modal-close" @click="showVersions = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="versions.length === 0" class="list-empty">暂无历史版本（每次保存都会自动留一份快照）</div>
          <div v-for="v in versions" :key="v.timestamp" class="version-row">
            <div class="version-info">
              <div class="version-time">{{ formatTime(v.timestamp) }}</div>
              <div class="version-summary">{{ v.summary || '—' }}</div>
            </div>
            <div class="version-actions">
              <button class="btn-mini" title="载入查看（不直接生效）" @click="previewVersion(v)">预览</button>
              <button class="btn-mini" title="恢复到该版本" @click="rollbackTo(v)">回滚</button>
            </div>
          </div>
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
import { computed, onMounted, reactive, ref } from 'vue'
import {
  activateTemplate,
  createTemplate,
  deleteTemplate,
  getVersion,
  listTemplates,
  listVersions,
  rollbackVersion,
  updateTemplate,
  uploadResume,
} from '../api/profile'
import type { ProfileForm, ProfileTemplate, ProfileVersionItem } from '../types'

const templates = ref<ProfileTemplate[]>([])
const activeId = ref<string | null>(null)
const editingId = ref<string | null>(null)
const isNew = ref(false)
const templateName = ref('')
const resumeText = ref('')
const resumeFileName = ref('')
const saving = ref(false)
const uploading = ref(false)
const toast = ref('')
const toastType = ref<'success' | 'error'>('success')
const fileInputRef = ref<HTMLInputElement | null>(null)

const form = reactive<ProfileForm>(emptyForm())

const editing = computed(() => isNew.value || editingId.value !== null)
const activeTemplate = computed(() => templates.value.find(t => t.id === activeId.value) || null)

function emptyForm(): ProfileForm {
  return {
    name: '', phone: '', email: '', city: '',
    school: '', major: '', degree: '', graduationYear: '',
    targetPosition: '', expectedSalary: '',
    skills: '', projectExperience: '', internshipExperience: '',
    selfEvaluation: '', awards: '',
  }
}

function formatTime(ts: number): string {
  if (!ts) return '—'
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function showToast(message: string, type: 'success' | 'error' = 'success') {
  toast.value = message
  toastType.value = type
  setTimeout(() => { if (toast.value === message) toast.value = '' }, 2600)
}

async function load() {
  try {
    const res = await listTemplates()
    templates.value = res.templates
    activeId.value = res.activeId
  } catch {
    showToast('加载模板失败，请确认后端已启动', 'error')
  }
}

function startCreate() {
  isNew.value = true
  editingId.value = null
  templateName.value = ''
  resumeText.value = ''
  resumeFileName.value = ''
  Object.assign(form, emptyForm())
}

function startEdit(tpl: ProfileTemplate) {
  isNew.value = false
  editingId.value = tpl.id
  templateName.value = tpl.name
  resumeText.value = tpl.resumeText || ''
  resumeFileName.value = tpl.resumeFileName || ''
  Object.assign(form, emptyForm(), tpl.form || {})
}

function payload() {
  return {
    name: templateName.value.trim(),
    form: { ...form },
    resumeText: resumeText.value,
    resumeFileName: resumeFileName.value,
  }
}

async function save(activateAfter: boolean) {
  if (!templateName.value.trim()) {
    showToast('请先填写模板名称', 'error')
    return
  }
  saving.value = true
  try {
    let saved: ProfileTemplate
    if (isNew.value || !editingId.value) {
      saved = await createTemplate(payload())
      isNew.value = false
      editingId.value = saved.id
    } else {
      saved = await updateTemplate(editingId.value, payload())
    }
    if (activateAfter) {
      const res = await activateTemplate(saved.id)
      templates.value = res.templates
      activeId.value = res.activeId
      showToast('已保存并启用该档案 ✓')
    } else {
      await load()
      showToast('已保存 ✓')
    }
  } catch {
    showToast('保存失败，请重试', 'error')
  } finally {
    saving.value = false
  }
}

async function setActive(tpl: ProfileTemplate) {
  try {
    const res = await activateTemplate(tpl.id)
    templates.value = res.templates
    activeId.value = res.activeId
    showToast(`已切换到「${tpl.name}」`)
  } catch {
    showToast('切换失败', 'error')
  }
}

async function removeTemplate(tpl: ProfileTemplate) {
  if (!window.confirm(`确定删除模板「${tpl.name}」吗？`)) return
  try {
    await deleteTemplate(tpl.id)
    if (editingId.value === tpl.id) startCreate()
    await load()
    showToast('已删除')
  } catch {
    showToast('删除失败', 'error')
  }
}

function triggerUpload() {
  fileInputRef.value?.click()
}

// ===== 版本历史 =====

const showVersions = ref(false)
const versions = ref<ProfileVersionItem[]>([])

async function openVersions() {
  if (!editingId.value) return
  showVersions.value = true
  try {
    versions.value = await listVersions(editingId.value)
  } catch {
    versions.value = []
    showToast('加载版本历史失败', 'error')
  }
}

async function previewVersion(v: ProfileVersionItem) {
  if (!editingId.value) return
  try {
    const tpl = await getVersion(editingId.value, v.timestamp)
    startEdit(tpl)
    showVersions.value = false
    showToast('已载入该版本内容（未保存，可编辑后再保存）')
  } catch {
    showToast('载入版本失败', 'error')
  }
}

async function rollbackTo(v: ProfileVersionItem) {
  if (!editingId.value) return
  if (!window.confirm(`确定回滚到 ${formatTime(v.timestamp)} 的版本吗？当前内容会先存入历史，可再回滚回来。`)) return
  try {
    const tpl = await rollbackVersion(editingId.value, v.timestamp)
    startEdit(tpl)
    showVersions.value = false
    await load()
    showToast('已回滚 ✓ 当前内容已存入历史，可再次回滚')
  } catch {
    showToast('回滚失败', 'error')
  }
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    const res = await uploadResume(file)
    resumeText.value = res.extractedText
    resumeFileName.value = res.fileName
    showToast(`已解析「${res.fileName}」（${res.charCount} 字）`)
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    showToast(msg || '文件解析失败，请检查文件格式', 'error')
  } finally {
    uploading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.profile-panel {
  position: relative;
  height: 100%;
  overflow-y: auto;
  background: #f7f8fa;
  color: #1f2329;
}

/* ===== 页头 ===== */
.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 28px 32px 20px;
  background: #ffffff;
  border-bottom: 1px solid #e5e6eb;
}
.header-title { margin: 0; font-size: 22px; font-weight: 600; letter-spacing: .3px; }
.header-subtitle { margin: 6px 0 0; font-size: 13px; color: #646a73; max-width: 560px; line-height: 1.6; }
.header-right { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.active-badge {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px; border-radius: 999px;
  background: #eef2ff; color: #2456e6; font-size: 12px; font-weight: 500;
}
.badge-dot { width: 6px; height: 6px; border-radius: 50%; background: #2456e6; }

/* ===== 布局 ===== */
.panel-body {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
  padding: 20px 32px 40px;
  align-items: start;
}

/* ===== 模板列表 ===== */
.template-list {
  background: #fff; border: 1px solid #e5e6eb; border-radius: 12px;
  padding: 16px; position: sticky; top: 20px;
}
.list-title { margin: 0 0 12px; font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-count {
  font-size: 11px; background: #f2f3f5; color: #646a73;
  padding: 1px 8px; border-radius: 999px; font-weight: 500;
}
.list-empty { font-size: 12px; color: #8f959e; line-height: 1.7; padding: 8px 4px; }
.template-card {
  border: 1px solid #e5e6eb; border-radius: 10px; padding: 12px;
  margin-bottom: 10px; cursor: pointer; transition: all .18s ease;
}
.template-card:hover { border-color: #c0c6cf; box-shadow: 0 2px 8px rgba(31, 35, 41, .06); }
.template-card.active { border-color: #2456e6; background: #f5f8ff; }
.template-card.editing { outline: 2px solid rgba(36, 86, 230, .25); }
.tpl-name { font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
.tpl-active-tag {
  font-size: 10px; background: #2456e6; color: #fff;
  padding: 1px 6px; border-radius: 999px; font-weight: 500;
}
.tpl-meta { font-size: 11px; color: #8f959e; margin-top: 4px; }
.tpl-actions { display: flex; gap: 6px; margin-top: 8px; }

/* ===== 编辑器 ===== */
.editor { min-width: 0; }
.editor-empty {
  background: #fff; border: 1px dashed #d0d3d9; border-radius: 12px;
  padding: 64px 24px; text-align: center; color: #646a73;
}
.editor-empty .empty-icon { font-size: 40px; margin-bottom: 12px; }
.editor-empty h3 { margin: 0 0 8px; font-size: 16px; color: #1f2329; }
.editor-empty p { margin: 0 0 20px; font-size: 13px; }

.editor-toolbar {
  display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
}
.input-title {
  flex: 1; font-size: 16px; font-weight: 600; padding: 12px 14px;
}
.toolbar-actions { display: flex; gap: 8px; }

.form-card {
  background: #fff; border: 1px solid #e5e6eb; border-radius: 12px;
  padding: 20px 22px; margin-bottom: 16px;
}
.card-head {
  display: flex; align-items: baseline; gap: 10px; margin-bottom: 16px;
  padding-bottom: 12px; border-bottom: 1px solid #f2f3f5;
}
.card-head h3 { margin: 0; font-size: 14px; font-weight: 600; }
.card-hint { font-size: 11px; color: #8f959e; }

.form-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px;
}
.field { display: flex; flex-direction: column; gap: 6px; }
.field > span { font-size: 12px; color: #646a73; font-weight: 500; }
.field-block { margin-bottom: 12px; }
.field-block:last-child { margin-bottom: 0; }

.input {
  border: 1px solid #d0d3d9; border-radius: 8px; padding: 9px 12px;
  font-size: 13px; color: #1f2329; background: #fff; outline: none;
  transition: border-color .18s, box-shadow .18s; width: 100%;
}
.input:focus { border-color: #2456e6; box-shadow: 0 0 0 3px rgba(36, 86, 230, .12); }
.textarea {
  border: 1px solid #d0d3d9; border-radius: 8px; padding: 10px 12px;
  font-size: 13px; color: #1f2329; background: #fff; outline: none;
  resize: vertical; width: 100%; line-height: 1.7; font-family: inherit;
  transition: border-color .18s, box-shadow .18s; box-sizing: border-box;
}
.textarea:focus { border-color: #2456e6; box-shadow: 0 0 0 3px rgba(36, 86, 230, .12); }
.resume-textarea { min-height: 160px; }
.char-count { text-align: right; font-size: 11px; color: #8f959e; margin-top: 6px; }

/* ===== 上传 ===== */
.upload-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.upload-name { font-size: 12px; color: #2456e6; background: #eef2ff; padding: 4px 10px; border-radius: 6px; }
.hidden-input { display: none; }

/* ===== 按钮 ===== */
.btn {
  border: none; border-radius: 8px; padding: 10px 18px;
  font-size: 13px; font-weight: 500; cursor: pointer;
  transition: all .18s ease; white-space: nowrap;
}
.btn:disabled { opacity: .55; cursor: not-allowed; }
.btn-primary { background: #2456e6; color: #fff; }
.btn-primary:hover:not(:disabled) { background: #1d47c4; box-shadow: 0 4px 12px rgba(36, 86, 230, .28); }
.btn-ghost { background: #f2f3f5; color: #1f2329; }
.btn-ghost:hover:not(:disabled) { background: #e5e6eb; }
.btn-upload { background: #eef2ff; color: #2456e6; border: 1px dashed #2456e6; }
.btn-upload:hover:not(:disabled) { background: #dfe7ff; }
.btn-mini {
  border: 1px solid #d0d3d9; background: #fff; color: #646a73;
  font-size: 11px; padding: 3px 10px; border-radius: 6px; cursor: pointer;
  transition: all .15s;
}
.btn-mini:hover { border-color: #2456e6; color: #2456e6; }
.btn-mini-danger:hover { border-color: #d83931; color: #d83931; }

.editor-footer {
  display: flex; justify-content: flex-end; gap: 10px; padding: 8px 0 24px;
}

/* ===== 版本历史弹层 ===== */
.modal-mask {
  position: fixed; inset: 0; background: rgba(31, 35, 41, 0.35);
  display: flex; align-items: center; justify-content: center; z-index: 60;
}
.modal {
  width: 520px; max-width: calc(100vw - 48px); max-height: 70vh;
  background: #fff; border-radius: 12px; box-shadow: 0 12px 40px rgba(31, 35, 41, .18);
  display: flex; flex-direction: column; overflow: hidden;
}
.modal-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid #e5e6eb;
}
.modal-head h3 { margin: 0; font-size: 15px; font-weight: 600; }
.modal-close {
  border: none; background: none; font-size: 20px; line-height: 1;
  color: #8f959e; cursor: pointer; padding: 2px 6px; border-radius: 6px;
}
.modal-close:hover { background: #f2f3f5; color: #1f2329; }
.modal-body { padding: 12px 20px 20px; overflow-y: auto; }
.version-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 12px 4px; border-bottom: 1px solid #f2f3f5;
}
.version-row:last-child { border-bottom: none; }
.version-time { font-size: 13px; font-weight: 600; color: #1f2329; }
.version-summary { font-size: 12px; color: #8f959e; margin-top: 2px; }
.version-actions { display: flex; gap: 6px; flex-shrink: 0; }

/* ===== 轻提示 ===== */
.toast {
  position: fixed; top: 24px; left: 50%; transform: translateX(-50%);
  padding: 10px 20px; border-radius: 8px; font-size: 13px; z-index: 100;
  box-shadow: 0 6px 20px rgba(31, 35, 41, .16);
}
.toast.success { background: #1f2329; color: #fff; }
.toast.error { background: #d83931; color: #fff; }
.toast-enter-active, .toast-leave-active { transition: opacity .25s, transform .25s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translate(-50%, -8px); }

@media (max-width: 960px) {
  .panel-body { grid-template-columns: 1fr; }
  .template-list { position: static; }
}
</style>
