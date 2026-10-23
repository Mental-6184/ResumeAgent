<template>
  <div class="job-workspace">
    <header class="workspace-header">
      <div>
        <h1>岗位工作区</h1>
        <p>集中管理岗位、匹配报告、定制简历建议和面试进度。</p>
      </div>
      <button class="btn-primary" @click="startCreate">＋ 新建岗位</button>
    </header>

    <div class="workspace-toolbar">
      <input v-model="query" class="form-input" placeholder="搜索公司、岗位或 JD" />
      <select v-model="statusFilter" class="form-input filter-select">
        <option value="">全部状态</option>
        <option v-for="status in statuses" :key="status" :value="status">{{ statusLabels[status] }}</option>
      </select>
      <div class="view-toggle">
        <button :class="{ active: viewMode === 'board' }" @click="viewMode = 'board'">看板</button>
        <button :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'">列表</button>
      </div>
    </div>

    <div class="workspace-body">
      <section class="job-list-area">
        <div v-if="loading" class="empty-state">正在加载岗位…</div>
        <div v-else-if="filteredJobs.length === 0" class="empty-state">
          <div class="empty-icon">🎯</div>
          <h3>还没有岗位记录</h3>
          <p>先保存一个目标岗位，再生成匹配报告和定制建议。</p>
        </div>

        <div v-else-if="viewMode === 'board'" class="board">
          <div v-for="status in statuses" :key="status" class="board-column" @dragover.prevent @drop="dropJob(status)">
            <div class="column-title">
              <span>{{ statusLabels[status] }}</span><b>{{ jobsByStatus(status).length }}</b>
            </div>
            <div v-for="job in jobsByStatus(status)" :key="job.id" class="job-card" draggable="true" @dragstart="dragJobId = job.id"
                 :class="{ selected: selected?.id === job.id }" @click="selectJob(job)">
              <strong>{{ job.title }}</strong><span>{{ job.company }}</span>
              <small>{{ job.location || '地点未填写' }} · {{ job.salaryRange || '薪资未填写' }}</small>
              <div class="card-meta"><span>{{ job.matchReport ? `${job.matchReport.overallScore}% 匹配` : '未分析' }}</span><span>{{ formatDate(job.updatedAt) }}</span></div>
              <select :value="job.status" @click.stop @change="changeStatus(job, ($event.target as HTMLSelectElement).value as JobStatus)">
                <option v-for="item in statuses" :key="item" :value="item">{{ statusLabels[item] }}</option>
              </select>
            </div>
          </div>
        </div>

        <div v-else class="list-view">
          <div v-for="job in filteredJobs" :key="job.id" class="list-row" @click="selectJob(job)">
            <div><strong>{{ job.company }} · {{ job.title }}</strong><small>{{ job.location || '地点未填写' }}</small></div>
            <span class="status-pill">{{ statusLabels[job.status] }}</span>
            <span>{{ job.matchReport ? `${job.matchReport.overallScore}%` : '—' }}</span>
            <span>{{ formatDate(job.updatedAt) }}</span>
          </div>
        </div>
      </section>

      <aside class="job-detail" v-if="selected || creating">
        <div class="detail-header"><h2>{{ creating ? '新建岗位' : '岗位详情' }}</h2><button class="icon-button" @click="closeDetail">×</button></div>
        <div class="form-grid">
          <label>公司名称<input v-model="form.company" class="form-input" /></label>
          <label>岗位名称<input v-model="form.title" class="form-input" /></label>
          <label>地点<input v-model="form.location" class="form-input" /></label>
          <label>薪资范围<input v-model="form.salaryRange" class="form-input" /></label>
          <label>岗位链接<input v-model="form.url" class="form-input" /></label>
          <label>截止日期<input v-model="deadlineText" type="date" class="form-input" /></label>
        </div>
        <label>岗位描述<textarea v-model="form.jobDescription" class="form-textarea" rows="7" /></label>
        <label>备注<textarea v-model="form.notes" class="form-textarea" rows="3" /></label>
        <div class="detail-actions">
          <button class="btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存岗位' }}</button>
          <button v-if="!creating" class="btn-danger" @click="remove">删除岗位</button>
        </div>

        <template v-if="selected && !creating">
          <section class="tool-section">
            <div class="section-title">投递状态</div>
            <select :value="selected.status" class="form-input" @change="changeStatus(selected, ($event.target as HTMLSelectElement).value as JobStatus)">
              <option v-for="status in statuses" :key="status" :value="status">{{ statusLabels[status] }}</option>
            </select>
          </section>
          <section class="tool-section">
            <div class="section-title">匹配报告</div>
            <button class="btn-secondary" :disabled="reportLoading" @click="generateReport">{{ reportLoading ? '分析中…' : selected.matchReport ? '重新生成报告' : '生成匹配报告' }}</button>
            <div v-if="selected.matchReport" class="report-summary"><b>{{ selected.matchReport.overallScore }}% 匹配</b><p>{{ selected.matchReport.summary }}</p></div>
            <small v-if="selected.matchReport && selected.profileTemplateId" class="muted">基于档案：{{ selected.profileTemplateId }}</small>
          </section>
          <section class="tool-section">
            <div class="section-title">定制简历建议</div>
            <button class="btn-secondary" :disabled="draftLoading || !selected.matchReport" @click="generateDraft">{{ draftLoading ? '生成中…' : selected.resumeDraft ? '重新生成建议' : '生成简历建议' }}</button>
            <div v-if="draft" class="draft-editor">
              <label>个人简介<textarea v-model="draft.summary" class="form-textarea" rows="4" /></label>
              <label>技能顺序<input v-model="skillsText" class="form-input" placeholder="用逗号分隔" /></label>
              <div v-for="(item, index) in draft.projectSuggestions" :key="index" class="suggestion-editor">
                <b>{{ item.project }}</b><textarea v-model="item.suggestion" class="form-textarea" rows="3" /><small>{{ item.reason }}</small>
              </div>
              <button class="btn-secondary" @click="saveDraft">保存简历建议</button>
              <button class="btn-ghost" @click="exportDraft">导出建议</button>
            </div>
          </section>
          <section class="tool-section">
            <div class="section-title">岗位专属面试</div>
            <button class="btn-secondary" @click="$emit('start-interview', { jobId: selected.id, jobTitle: `${selected.company} - ${selected.title}` })">开始岗位面试</button>
            <div v-if="reviews.length" class="review-links">
              <button v-for="review in reviews" :key="review.id" class="review-link" @click="$emit('open-review', review.id)">{{ review.title }} · {{ review.overallScore }}分</button>
            </div>
            <p v-else class="muted">暂无关联复盘</p>
          </section>
        </template>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createJob, deleteJob, generateJobMatchReport, generateJobResumeDraft, listJobReviews, listJobs, saveJobResumeDraft, updateJob, updateJobStatus } from '../api/jobs'
import { exportResume } from '../api/export'
import type { JobCreateRequest, JobRecord, JobResumeDraft, JobReviewSummary, JobStatus } from '../types'

const emit = defineEmits<{ (e: 'start-interview', context: { jobId: string; jobTitle: string }): void; (e: 'open-review', id: string): void }>()
const statuses: JobStatus[] = ['SAVED', 'PREPARING', 'APPLIED', 'WRITTEN_TEST', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN']
const statusLabels: Record<JobStatus, string> = { SAVED: '已收藏', PREPARING: '准备中', APPLIED: '已投递', WRITTEN_TEST: '笔试', INTERVIEW: '面试中', OFFER: 'Offer', REJECTED: '已拒绝', WITHDRAWN: '已放弃' }
const jobs = ref<JobRecord[]>([])
const selected = ref<JobRecord | null>(null)
const creating = ref(false)
const loading = ref(false)
const saving = ref(false)
const reportLoading = ref(false)
const draftLoading = ref(false)
const query = ref('')
const statusFilter = ref<JobStatus | ''>('')
const viewMode = ref<'board' | 'list'>('board')
const dragJobId = ref('')
const reviews = ref<JobReviewSummary[]>([])
const draft = ref<JobResumeDraft | null>(null)
const skillsText = ref('')
const deadlineText = ref('')
const form = reactive<JobCreateRequest>({ company: '', title: '', location: '', salaryRange: '', url: '', jobDescription: '', deadline: null, status: 'SAVED', notes: '' })

const filteredJobs = computed(() => jobs.value.filter(job => (!statusFilter.value || job.status === statusFilter.value) && (!query.value.trim() || `${job.company} ${job.title} ${job.jobDescription}`.toLowerCase().includes(query.value.trim().toLowerCase()))))
function jobsByStatus(status: JobStatus) { return filteredJobs.value.filter(job => job.status === status) }
function formatDate(value: number) { return new Date(value).toLocaleDateString('zh-CN') }
function resetForm() { Object.assign(form, { company: '', title: '', location: '', salaryRange: '', url: '', jobDescription: '', deadline: null, status: 'SAVED', notes: '' }); deadlineText.value = '' }
function fillForm(job: JobRecord) { Object.assign(form, { company: job.company, title: job.title, location: job.location, salaryRange: job.salaryRange, url: job.url, jobDescription: job.jobDescription, deadline: job.deadline, status: job.status, notes: job.notes }); deadlineText.value = job.deadline ? new Date(job.deadline).toISOString().slice(0, 10) : '' }
async function load() { loading.value = true; try { jobs.value = await listJobs() } catch { ElMessage.error('岗位列表加载失败') } finally { loading.value = false } }
function startCreate() { selected.value = null; creating.value = true; resetForm(); draft.value = null; reviews.value = [] }
function closeDetail() { selected.value = null; creating.value = false; draft.value = null; reviews.value = [] }
function selectJob(job: JobRecord) { selected.value = job; creating.value = false; fillForm(job); draft.value = job.resumeDraft ? structuredClone(job.resumeDraft) : null; skillsText.value = draft.value?.skills.join(', ') || ''; void loadReviews(job.id) }
async function loadReviews(id: string) { try { reviews.value = await listJobReviews(id) } catch { reviews.value = [] } }
async function save() { if (!form.company?.trim() || !form.title?.trim() || !form.jobDescription?.trim()) { ElMessage.warning('请填写公司、岗位和 JD'); return }; saving.value = true; try { form.deadline = deadlineText.value ? new Date(`${deadlineText.value}T23:59:59`).getTime() : null; const saved = creating.value ? await createJob(form) : await updateJob(selected.value!.id, form); jobs.value = creating.value ? [saved, ...jobs.value] : jobs.value.map(item => item.id === saved.id ? saved : item); selected.value = saved; creating.value = false; fillForm(saved); ElMessage.success('岗位已保存') } catch { ElMessage.error('岗位保存失败') } finally { saving.value = false } }
async function remove() { if (!selected.value || !window.confirm('确定删除这个岗位吗？')) return; try { await deleteJob(selected.value.id); jobs.value = jobs.value.filter(item => item.id !== selected.value!.id); closeDetail(); ElMessage.success('岗位已删除') } catch { ElMessage.error('岗位删除失败') } }
async function changeStatus(job: JobRecord, status: JobStatus) { try { const updated = await updateJobStatus(job.id, status); jobs.value = jobs.value.map(item => item.id === updated.id ? updated : item); if (selected.value?.id === updated.id) { selected.value = updated; fillForm(updated) } } catch { ElMessage.error('状态更新失败') } }
async function dropJob(status: JobStatus) { if (!dragJobId.value) return; const job = jobs.value.find(item => item.id === dragJobId.value); dragJobId.value = ''; if (job && job.status !== status) await changeStatus(job, status) }
async function generateReport() { if (!selected.value) return; reportLoading.value = true; try { const updated = await generateJobMatchReport(selected.value.id); jobs.value = jobs.value.map(item => item.id === updated.id ? updated : item); selected.value = updated; ElMessage.success('匹配报告已生成') } catch (error: any) { ElMessage.error(error?.response?.data?.message || '匹配报告生成失败') } finally { reportLoading.value = false } }
async function generateDraft() { if (!selected.value) return; draftLoading.value = true; try { draft.value = await generateJobResumeDraft(selected.value.id); skillsText.value = draft.value.skills.join(', '); selected.value = { ...selected.value, resumeDraft: draft.value }; jobs.value = jobs.value.map(item => item.id === selected.value!.id ? selected.value! : item); ElMessage.success('简历建议已生成') } catch { ElMessage.error('请先生成匹配报告或检查档案') } finally { draftLoading.value = false } }
async function saveDraft() { if (!selected.value || !draft.value) return; draft.value.skills = skillsText.value.split(/[,，、]/).map(item => item.trim()).filter(Boolean); try { const saved = await saveJobResumeDraft(selected.value.id, draft.value); selected.value = { ...selected.value, resumeDraft: saved }; jobs.value = jobs.value.map(item => item.id === selected.value!.id ? selected.value! : item); draft.value = saved; ElMessage.success('简历建议已保存') } catch { ElMessage.error('简历建议保存失败') } }
async function exportDraft() { if (!selected.value?.resumeDraft) return; try { await exportResume({ format: 'md', templateId: selected.value.profileTemplateId, matchReport: selected.value.matchReport, resumeDraft: selected.value.resumeDraft }, `岗位简历建议_${selected.value.title}`); ElMessage.success('已导出') } catch { ElMessage.error('导出失败') } }
watch(selected, job => { draft.value = job?.resumeDraft ? structuredClone(job.resumeDraft) : null; skillsText.value = draft.value?.skills.join(', ') || '' })
onMounted(load)
</script>

<style scoped>
.job-workspace { height: 100%; overflow: auto; background: #f7f8fa; color: #1f2329; padding: 28px 32px 48px; box-sizing: border-box; }
.workspace-header, .workspace-toolbar, .workspace-body { max-width: 1500px; margin: 0 auto; }
.workspace-header { display:flex; justify-content:space-between; align-items:center; gap:20px; margin-bottom:20px; }
.workspace-header h1 { margin:0; font-size:24px; }.workspace-header p { color:#8f959e; margin:8px 0 0; font-size:13px; }
.workspace-toolbar { display:flex; gap:10px; margin-bottom:16px; }.workspace-toolbar .form-input { max-width:300px; }.filter-select { width:140px; }.view-toggle { margin-left:auto; display:flex; }.view-toggle button { border:1px solid #d9dce2; background:#fff; padding:8px 14px; cursor:pointer; }.view-toggle button:first-child { border-radius:8px 0 0 8px; }.view-toggle button:last-child { border-radius:0 8px 8px 0; }.view-toggle .active { background:#2456e6; color:#fff; border-color:#2456e6; }
.workspace-body { display:grid; grid-template-columns:minmax(0,1fr) 440px; gap:18px; align-items:start; }.board { display:grid; grid-template-columns:repeat(4,minmax(190px,1fr)); gap:12px; overflow-x:auto; padding-bottom:8px; }.board-column { min-width:190px; }.column-title { display:flex; justify-content:space-between; color:#646a73; font-size:13px; margin:0 0 8px; }.column-title b { background:#e5e6eb; border-radius:10px; padding:1px 7px; font-size:11px; }
.job-card, .job-detail, .list-view { background:#fff; border:1px solid #e5e6eb; border-radius:12px; }.job-card { padding:13px; margin-bottom:10px; cursor:pointer; }.job-card.selected { border-color:#2456e6; box-shadow:0 0 0 2px #e7edff; }.job-card strong, .job-card span, .job-card small { display:block; }.job-card strong { font-size:14px; }.job-card span { color:#646a73; font-size:12px; margin-top:5px; }.job-card small { color:#8f959e; margin-top:8px; }.card-meta { display:flex; justify-content:space-between; margin:10px 0; }.card-meta span { color:#2456e6; font-size:11px; }.job-card select { width:100%; border:1px solid #e5e6eb; border-radius:6px; padding:5px; font-size:11px; }
.list-view { overflow:hidden; }.list-row { display:grid; grid-template-columns:2fr 120px 80px 100px; gap:12px; align-items:center; padding:14px 16px; border-bottom:1px solid #f0f1f3; cursor:pointer; }.list-row:last-child { border-bottom:0; }.list-row strong, .list-row small { display:block; }.list-row small { color:#8f959e; margin-top:4px; }.status-pill { color:#2456e6; font-size:12px; }
.job-detail { padding:18px; position:sticky; top:0; max-height:calc(100vh - 160px); overflow:auto; }.detail-header { display:flex; justify-content:space-between; align-items:center; }.detail-header h2 { font-size:18px; margin:0 0 16px; }.icon-button { border:0; background:transparent; font-size:24px; color:#8f959e; cursor:pointer; }.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; }.job-detail label { display:block; color:#646a73; font-size:12px; margin-bottom:11px; }.job-detail label .form-input, .job-detail label .form-textarea { margin-top:5px; width:100%; box-sizing:border-box; }.detail-actions { display:flex; gap:8px; margin:4px 0 18px; }.btn-danger { border:1px solid #f0b4b4; color:#c43d3d; background:#fff; border-radius:8px; padding:8px 13px; cursor:pointer; }.tool-section { border-top:1px solid #eef0f3; padding-top:15px; margin-top:15px; }.section-title { font-weight:600; margin-bottom:9px; }.btn-secondary, .btn-ghost { border:1px solid #d9dce2; background:#fff; color:#2456e6; border-radius:8px; padding:8px 12px; cursor:pointer; }.report-summary { background:#f5f8ff; padding:10px; border-radius:8px; margin-top:10px; }.report-summary p { color:#646a73; font-size:12px; line-height:1.6; }.draft-editor { margin-top:10px; }.suggestion-editor { border:1px solid #eef0f3; border-radius:8px; padding:9px; margin:8px 0; }.suggestion-editor small, .muted { color:#8f959e; font-size:11px; }.review-links { display:flex; flex-direction:column; gap:6px; margin-top:10px; }.review-link { text-align:left; border:0; background:#f5f8ff; color:#2456e6; padding:8px; border-radius:6px; cursor:pointer; }.empty-state { background:#fff; border:1px solid #e5e6eb; border-radius:12px; padding:70px 20px; text-align:center; color:#8f959e; }.empty-icon { font-size:32px; }.empty-state h3 { color:#1f2329; }
@media (max-width: 1100px) { .workspace-body { grid-template-columns:1fr; }.job-detail { position:relative; max-height:none; }.board { grid-template-columns:repeat(4,220px); } }
@media (max-width: 650px) { .job-workspace { padding:20px 14px 36px; }.workspace-header { align-items:flex-start; flex-direction:column; }.workspace-toolbar { flex-wrap:wrap; }.workspace-toolbar .form-input { max-width:none; flex:1; min-width:180px; }.view-toggle { margin-left:0; }.form-grid { grid-template-columns:1fr; }.list-row { grid-template-columns:1fr 90px; }.list-row > span:nth-child(3), .list-row > span:nth-child(4) { display:none; } }
</style>
