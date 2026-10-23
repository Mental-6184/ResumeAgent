<template>
  <div class="review-panel">
    <header class="panel-header">
      <div class="header-left">
        <h1 class="header-title">面试复盘</h1>
        <p class="header-subtitle">每场模拟面试生成结构化复盘报告，历史成绩并排对比，看清进步轨迹。</p>
      </div>
      <div class="header-right">
        <button v-if="current" class="btn btn-ghost" :disabled="exporting" @click="doExport('pdf')">⬇ 导出 PDF</button>
        <button v-if="current" class="btn btn-ghost" :disabled="exporting" @click="doExport('md')">⬇ Markdown</button>
      </div>
    </header>

    <div class="panel-body">
      <!-- 左：历史对比 -->
      <aside class="review-list">
        <h2 class="list-title">历史复盘 <span class="list-count">{{ summaries.length }}</span></h2>
        <div v-if="summaries.length === 0" class="list-empty">
          暂无复盘记录。到「模拟面试」完成一场面试后，点击「生成复盘」即可。
        </div>
        <div
          v-for="item in summaries"
          :key="item.id"
          class="review-card"
          :class="{ active: current?.id === item.id }"
          @click="selectReview(item.id)"
        >
          <div class="review-title">{{ item.title }}</div>
          <div class="review-time">{{ formatTime(item.createdAt) }}</div>
          <div class="mini-bar">
            <div class="mini-fill" :style="{ width: item.overallScore + '%' }"></div>
          </div>
          <div class="review-score">{{ item.overallScore }} 分</div>
          <button class="btn-mini btn-mini-danger" title="删除" @click.stop="removeReview(item.id)">删除</button>
        </div>
      </aside>

      <!-- 右：详情 -->
      <section class="review-detail">
        <div v-if="!current" class="detail-empty">
          <div class="empty-icon">📈</div>
          <h3>选择一场复盘查看详情</h3>
          <p>报告包含维度评分、表现亮点、改进方向与逐题点评。</p>
        </div>

        <template v-else>
          <div class="detail-card score-card">
            <div class="detail-score">{{ current.overallScore }}</div>
            <div class="detail-label">
              总体表现
              <span v-if="current.degraded" class="tag-inline">统计复盘</span>
              <span v-else class="tag-inline llm">AI 复盘</span>
            </div>
            <div class="detail-summary">{{ current.summary }}</div>
          </div>

          <div class="detail-card">
            <h3 class="card-title">维度评分</h3>
            <div class="dim-row" v-for="dim in current.dimensions" :key="dim.name">
              <div class="dim-head">
                <span class="dim-name">{{ dim.name }}</span>
                <span class="dim-score">{{ dim.score }}</span>
              </div>
              <div class="score-bar"><div class="score-fill" :style="{ width: dim.score + '%' }"></div></div>
              <div class="dim-comment">{{ dim.comment }}</div>
            </div>
          </div>

          <div class="detail-card" v-if="current.highlights.length">
            <h3 class="card-title">✦ 表现亮点</h3>
            <ul class="bullet-list"><li v-for="(item, i) in current.highlights" :key="i">{{ item }}</li></ul>
          </div>

          <div class="detail-card" v-if="current.improvements.length">
            <h3 class="card-title">◈ 改进方向</h3>
            <ul class="bullet-list"><li v-for="(item, i) in current.improvements" :key="i">{{ item }}</li></ul>
          </div>

          <div class="detail-card" v-if="current.perQuestion.length">
            <h3 class="card-title">◇ 逐题点评</h3>
            <div class="q-row" v-for="(q, i) in current.perQuestion" :key="i">
              <div class="q-head">
                <span class="q-index">Q{{ i + 1 }}</span>
                <span class="q-text">{{ q.question }}</span>
                <span class="q-score">{{ q.score }} 分</span>
              </div>
              <div class="q-comment">{{ q.comment }}</div>
              <div class="q-improve">↳ {{ q.improvement }}</div>
            </div>
          </div>
        </template>
      </section>
    </div>

    <!-- 轻提示 -->
    <transition name="toast">
      <div v-if="toast" class="toast" :class="toastType">{{ toast }}</div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { deleteReview, getReview, listReviews } from '../api/agent'
import { exportInterview } from '../api/export'
import type { InterviewReviewResponse, InterviewReviewSummary } from '../types'

const summaries = ref<InterviewReviewSummary[]>([])
const current = ref<InterviewReviewResponse | null>(null)
const exporting = ref(false)
const toast = ref('')
const toastType = ref<'success' | 'error'>('success')

function showToast(message: string, type: 'success' | 'error' = 'success') {
  toast.value = message
  toastType.value = type
  setTimeout(() => { if (toast.value === message) toast.value = '' }, 2600)
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function load() {
  try {
    summaries.value = await listReviews()
    // 默认选中最新一场
    const requestedId = sessionStorage.getItem('resumeagent_open_review')
    sessionStorage.removeItem('resumeagent_open_review')
    const targetId = requestedId && summaries.value.some(item => item.id === requestedId)
      ? requestedId : summaries.value[0]?.id
    if (targetId && !current.value) {
      await selectReview(targetId)
    }
  } catch {
    showToast('加载复盘列表失败，请确认后端已启动', 'error')
  }
}

async function selectReview(id: string) {
  try {
    current.value = await getReview(id)
  } catch {
    showToast('加载复盘详情失败', 'error')
  }
}

async function removeReview(id: string) {
  if (!window.confirm('确定删除该复盘记录吗？')) return
  try {
    await deleteReview(id)
    if (current.value?.id === id) current.value = null
    await load()
    showToast('已删除')
  } catch {
    showToast('删除失败', 'error')
  }
}

async function doExport(format: 'pdf' | 'md') {
  if (!current.value) return
  exporting.value = true
  try {
    await exportInterview(
      { format, reviewId: current.value.id, title: current.value.title },
      `面试复盘_${current.value.title}`
    )
    showToast('已导出 ✓')
  } catch {
    showToast('导出失败', 'error')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.review-panel {
  position: relative; height: 100%; overflow-y: auto;
  background: #f7f8fa; color: #1f2329;
}

.panel-header {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
  padding: 28px 32px 20px; background: #fff; border-bottom: 1px solid #e5e6eb;
}
.header-title { margin: 0; font-size: 22px; font-weight: 600; }
.header-subtitle { margin: 6px 0 0; font-size: 13px; color: #646a73; max-width: 560px; line-height: 1.6; }
.header-right { display: flex; gap: 8px; flex-shrink: 0; }

.panel-body {
  display: grid; grid-template-columns: 280px 1fr; gap: 20px;
  padding: 20px 32px 40px; align-items: start;
}

/* ===== 历史列表 ===== */
.review-list {
  background: #fff; border: 1px solid #e5e6eb; border-radius: 12px;
  padding: 16px; position: sticky; top: 20px;
}
.list-title { margin: 0 0 12px; font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.list-count {
  font-size: 11px; background: #f2f3f5; color: #646a73; padding: 1px 8px; border-radius: 999px; font-weight: 500;
}
.list-empty { font-size: 12px; color: #8f959e; line-height: 1.7; padding: 8px 4px; }

.review-card {
  border: 1px solid #e5e6eb; border-radius: 10px; padding: 12px;
  margin-bottom: 10px; cursor: pointer; transition: all .18s ease;
}
.review-card:hover { border-color: #c0c6cf; box-shadow: 0 2px 8px rgba(31, 35, 41, .06); }
.review-card.active { border-color: #2456e6; background: #f5f8ff; }
.review-title { font-size: 13px; font-weight: 600; }
.review-time { font-size: 11px; color: #8f959e; margin-top: 3px; }
.mini-bar {
  height: 4px; background: #eef0f3; border-radius: 2px; margin: 8px 0 4px; overflow: hidden;
}
.mini-fill { height: 100%; background: #2456e6; border-radius: 2px; transition: width .6s ease; }
.review-score { font-size: 11px; color: #2456e6; font-weight: 600; }
.review-card .btn-mini { margin-top: 8px; }

/* ===== 详情 ===== */
.review-detail { min-width: 0; }
.detail-empty {
  background: #fff; border: 1px dashed #d0d3d9; border-radius: 12px;
  padding: 64px 24px; text-align: center; color: #646a73;
}
.detail-empty .empty-icon { font-size: 40px; margin-bottom: 12px; }
.detail-empty h3 { margin: 0 0 8px; font-size: 16px; color: #1f2329; }
.detail-empty p { margin: 0; font-size: 13px; }

.detail-card {
  background: #fff; border: 1px solid #e5e6eb; border-radius: 12px;
  padding: 20px 22px; margin-bottom: 16px;
}
.card-title {
  margin: 0 0 14px; padding-bottom: 10px; border-bottom: 1px solid #f2f3f5;
  font-size: 14px; font-weight: 600;
}

.score-card { text-align: center; }
.detail-score { font-size: 40px; font-weight: 700; color: #2456e6; }
.detail-label { font-size: 13px; color: #646a73; margin-top: 2px; }
.tag-inline {
  font-size: 10px; background: #f2f3f5; color: #646a73; padding: 2px 8px;
  border-radius: 999px; margin-left: 8px; font-weight: 500;
}
.tag-inline.llm { background: #eef2ff; color: #2456e6; }
.detail-summary { font-size: 13px; color: #1f2329; margin-top: 12px; line-height: 1.8; text-align: left; }

.dim-row { margin-bottom: 14px; }
.dim-row:last-child { margin-bottom: 0; }
.dim-head { display: flex; justify-content: space-between; align-items: baseline; }
.dim-name { font-size: 13px; font-weight: 600; }
.dim-score { font-size: 13px; font-weight: 600; color: #2456e6; }
.score-bar {
  height: 5px; background: #eef0f3; border-radius: 3px; margin-top: 6px; overflow: hidden;
}
.score-fill { height: 100%; background: #2456e6; border-radius: 3px; transition: width .7s ease; }
.dim-comment { font-size: 11px; color: #8f959e; margin-top: 5px; }

.bullet-list { margin: 0; padding-left: 18px; }
.bullet-list li { font-size: 13px; color: #1f2329; line-height: 1.8; }

.q-row { padding: 12px 0; border-bottom: 1px solid #f2f3f5; }
.q-row:last-child { border-bottom: none; }
.q-head { display: flex; align-items: flex-start; gap: 8px; }
.q-index {
  font-size: 11px; background: #eef2ff; color: #2456e6; padding: 1px 7px;
  border-radius: 6px; font-weight: 600; flex-shrink: 0;
}
.q-text { flex: 1; font-size: 13px; line-height: 1.6; }
.q-score { font-size: 12px; color: #2456e6; font-weight: 600; flex-shrink: 0; }
.q-comment { font-size: 12px; color: #646a73; margin: 6px 0 2px; padding-left: 30px; }
.q-improve { font-size: 12px; color: #2456e6; padding-left: 30px; }

/* ===== 按钮/提示 ===== */
.btn {
  border: none; border-radius: 8px; padding: 10px 18px;
  font-size: 13px; font-weight: 500; cursor: pointer; transition: all .18s ease; white-space: nowrap;
}
.btn:disabled { opacity: .55; cursor: not-allowed; }
.btn-ghost { background: #f2f3f5; color: #1f2329; }
.btn-ghost:hover:not(:disabled) { background: #e5e6eb; }
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

@media (max-width: 960px) {
  .panel-body { grid-template-columns: 1fr; }
  .review-list { position: static; }
}
</style>
