<template>
  <div class="panel">
    <div class="panel-header">岗位匹配分析</div>
    <div class="panel-body">
      <!-- 模式切换 -->
      <div class="mode-tabs">
        <button class="mode-tab" :class="{ active: mode === 'quick' }" @click="mode = 'quick'">快速匹配</button>
        <button class="mode-tab" :class="{ active: mode === 'deep' }" @click="mode = 'deep'">深度报告</button>
        <span class="mode-hint">{{ mode === 'quick' ? '一眼看匹配度与优劣势' : '逐条 JD 要求 + 简历修改建议' }}</span>
      </div>

      <div class="form-section">
        <label class="form-label">岗位名称</label>
        <input
          class="form-input"
          v-model="form.jobTitle"
          placeholder="例如：Java 后端开发工程师"
          @keydown.enter="handleAnalyze"
        />
      </div>
      <div class="form-section">
        <label class="form-label">岗位描述</label>
        <textarea
          class="form-textarea"
          v-model="form.jobDescription"
          rows="4"
          placeholder="粘贴岗位描述（JD）..."
        ></textarea>
      </div>

      <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 20px;">
        <button class="btn-primary" :disabled="loading" @click="handleAnalyze">
          <span v-if="loading" class="btn-loading-content">
            <ThinkingIndicator variant="inline" />
          </span>
          <span v-else>{{ mode === 'quick' ? '开始分析' : '生成深度报告' }}</span>
        </button>
        <button class="btn-ghost" @click="clearForm">清空</button>
        <template v-if="mode === 'deep' && report">
          <button class="btn-ghost" :disabled="exporting" @click="doExport('pdf')">⬇ 导出 PDF</button>
          <button class="btn-ghost" :disabled="exporting" @click="doExport('md')">⬇ 导出 Markdown</button>
        </template>
      </div>

      <!-- ===== 快速匹配结果 ===== -->
      <transition name="fade" mode="out-in">
        <div v-if="mode === 'quick' && result" class="result-section">
          <div class="result-card score-card">
            <div class="result-score">{{ result.matchScore }}%</div>
            <div class="result-title">匹配度</div>
            <div class="score-bar">
              <div class="score-fill" :style="{ width: result.matchScore + '%' }"></div>
            </div>
          </div>

          <div class="result-card">
            <div class="result-title">✦ 优势</div>
            <div class="result-content">
              <ul><li v-for="item in result.strengths" :key="item">{{ item }}</li></ul>
            </div>
          </div>

          <div class="result-card">
            <div class="result-title">◈ 短板</div>
            <div class="result-content">
              <ul><li v-for="item in result.gaps" :key="item">{{ item }}</li></ul>
            </div>
          </div>

          <div class="result-card">
            <div class="result-title">◇ 建议</div>
            <div class="result-content">
              <ul><li v-for="item in result.suggestions" :key="item">{{ item }}</li></ul>
            </div>
          </div>
        </div>

        <!-- ===== 深度报告结果 ===== -->
        <div v-else-if="mode === 'deep' && report" class="result-section">
          <div class="result-card score-card">
            <div class="result-score">{{ report.overallScore }}%</div>
            <div class="result-title">
              综合匹配度
              <span v-if="report.degraded" class="degraded-tag">关键词分析</span>
              <span v-else class="degraded-tag llm">AI 深度分析</span>
            </div>
            <div class="score-bar">
              <div class="score-fill" :style="{ width: report.overallScore + '%' }"></div>
            </div>
            <div class="report-summary">{{ report.summary }}</div>
          </div>

          <!-- 维度分 -->
          <div class="result-card">
            <div class="result-title">◈ 维度评分</div>
            <div class="dim-row" v-for="dim in report.dimensions" :key="dim.name">
              <div class="dim-head">
                <span class="dim-name">{{ dim.name }}</span>
                <span class="dim-score">{{ dim.score }}</span>
              </div>
              <div class="score-bar slim">
                <div class="score-fill" :style="{ width: dim.score + '%' }"></div>
              </div>
              <div class="dim-comment">{{ dim.comment }}</div>
            </div>
          </div>

          <!-- 逐条 JD 要求 -->
          <div class="result-card">
            <div class="result-title">◇ 逐条 JD 要求匹配</div>
            <div class="req-row" v-for="(req, index) in report.requirementMatches" :key="index">
              <div class="req-head">
                <span class="status-dot" :class="statusClass(req.status)"></span>
                <span class="req-text">{{ req.requirement }}</span>
                <span class="status-label" :class="statusClass(req.status)">{{ req.status }}</span>
              </div>
              <div class="req-evidence">{{ req.evidence }}</div>
              <div class="req-advice">↳ {{ req.advice }}</div>
            </div>
          </div>

          <!-- 简历修改建议 -->
          <div class="result-card">
            <div class="result-title">✦ 简历修改建议</div>
            <div class="sug-row" v-for="(sug, index) in report.resumeSuggestions" :key="index">
              <div class="sug-original">原文：{{ sug.original }}</div>
              <div class="sug-new">建议：{{ sug.suggestion }}</div>
              <div class="sug-reason">{{ sug.reason }}</div>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { analyzeJob, getMatchReport } from '../api/agent'
import { exportResume } from '../api/export'
import type { JobAnalyzeResponse, MatchReportResponse } from '../types'
import ThinkingIndicator from './ThinkingIndicator.vue'

const mode = ref<'quick' | 'deep'>('quick')
const loading = ref(false)
const exporting = ref(false)
const result = ref<JobAnalyzeResponse | null>(null)
const report = ref<MatchReportResponse | null>(null)

const form = reactive({
  jobTitle: 'Java 后端开发工程师',
  jobDescription: '负责 Java 后端服务开发、系统架构设计、数据库优化和安全能力建设。要求熟悉 Spring Boot、MySQL、Redis，具备微服务和分布式系统开发经验。'
})

function clearForm() {
  form.jobTitle = ''
  form.jobDescription = ''
  result.value = null
  report.value = null
}

function statusClass(status: string): string {
  if (status === '满足') return 'ok'
  if (status === '缺失') return 'bad'
  return 'mid'
}

async function handleAnalyze() {
  if (!form.jobTitle.trim() || !form.jobDescription.trim()) {
    ElMessage.warning('请填写岗位名称和描述')
    return
  }

  loading.value = true
  try {
    if (mode.value === 'quick') {
      result.value = null
      result.value = await analyzeJob({ ...form })
    } else {
      report.value = null
      report.value = await getMatchReport({ ...form })
    }
  } catch {
    ElMessage.error('分析失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function doExport(format: 'pdf' | 'md') {
  if (!report.value) return
  exporting.value = true
  try {
    await exportResume(
      { format, templateId: null, matchReport: report.value },
      `简历优化_${report.value.jobTitle}`
    )
    ElMessage.success('已导出')
  } catch {
    ElMessage.error('导出失败，请确认已启用档案模板')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.mode-tabs {
  display: flex; align-items: center; gap: 8px; margin-bottom: 18px;
}
.mode-tab {
  border: 1px solid var(--border-strong); background: #fff; color: var(--text-secondary);
  padding: 8px 16px; border-radius: 8px; font-size: 13px; cursor: pointer; transition: all .18s;
}
.mode-tab.active { background: var(--accent); border-color: var(--accent); color: #fff; }
.mode-hint { font-size: 12px; color: var(--text-muted); margin-left: 4px; }

.result-section { animation: slideInUp 0.3s ease; }

@keyframes slideInUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.score-card { text-align: center; padding: 24px 18px; }

.score-bar {
  width: 100%; height: 6px; background: var(--bg-secondary);
  border-radius: 3px; margin-top: 12px; overflow: hidden;
}
.score-bar.slim { height: 4px; margin-top: 6px; }

.score-fill {
  height: 100%; background: var(--accent); border-radius: 3px;
  transition: width 0.8s cubic-bezier(0.16, 1, 0.3, 1);
}

.degraded-tag {
  font-size: 10px; background: var(--bg-secondary); color: var(--text-secondary);
  padding: 2px 8px; border-radius: 999px; margin-left: 8px; font-weight: 500; vertical-align: middle;
}
.degraded-tag.llm { background: var(--accent-soft); color: var(--accent); }

.report-summary {
  margin-top: 12px; font-size: 12px; color: var(--text-secondary); line-height: 1.7; text-align: left;
}

.dim-row { margin-bottom: 14px; }
.dim-row:last-child { margin-bottom: 0; }
.dim-head { display: flex; justify-content: space-between; align-items: baseline; }
.dim-name { font-size: 13px; font-weight: 600; color: var(--text); }
.dim-score { font-size: 13px; font-weight: 600; color: var(--accent); }
.dim-comment { font-size: 11px; color: var(--text-muted); margin-top: 4px; }

.req-row {
  padding: 12px 0; border-bottom: 1px solid var(--border-light);
}
.req-row:last-child { border-bottom: none; }
.req-head { display: flex; align-items: flex-start; gap: 8px; }
.req-text { flex: 1; font-size: 13px; color: var(--text); line-height: 1.6; }
.status-dot {
  width: 8px; height: 8px; border-radius: 50%; margin-top: 6px; flex-shrink: 0;
}
.status-dot.ok { background: var(--success); }
.status-dot.mid { background: var(--warning); }
.status-dot.bad { background: var(--error); }
.status-label {
  font-size: 11px; padding: 1px 8px; border-radius: 999px; flex-shrink: 0; font-weight: 500;
}
.status-label.ok { background: rgba(46, 164, 79, .1); color: var(--success); }
.status-label.mid { background: rgba(255, 136, 0, .1); color: var(--warning); }
.status-label.bad { background: var(--error-soft); color: var(--error); }
.req-evidence { font-size: 12px; color: var(--text-secondary); margin-top: 6px; padding-left: 16px; }
.req-advice { font-size: 12px; color: var(--accent); margin-top: 4px; padding-left: 16px; }

.sug-row {
  padding: 12px 14px; background: var(--bg); border-radius: 8px; margin-bottom: 10px;
}
.sug-row:last-child { margin-bottom: 0; }
.sug-original { font-size: 12px; color: var(--text-muted); text-decoration: line-through; }
.sug-new { font-size: 13px; color: var(--text); margin-top: 4px; font-weight: 500; }
.sug-reason { font-size: 11px; color: var(--text-secondary); margin-top: 4px; }

.btn-loading-content {
  display: inline-flex; align-items: center; gap: 6px;
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.fade-enter-from { opacity: 0; transform: translateY(10px); }
.fade-leave-to { opacity: 0; transform: translateY(-10px); }
</style>
