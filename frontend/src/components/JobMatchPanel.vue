<template>
  <div class="panel">
    <div class="panel-header">岗位匹配分析</div>
    <div class="panel-body">
      <div class="form-section">
        <label class="form-label">岗位名称</label>
        <input
          class="form-input"
          v-model="form.jobTitle"
          placeholder="例如：Java 安全开发工程师"
          @keydown.enter="handleAnalyze"
        />
      </div>
      <div class="form-section">
        <label class="form-label">岗位描述</label>
        <textarea
          class="form-textarea"
          v-model="form.jobDescription"
          rows="4"
          placeholder="粘贴岗位描述..."
        ></textarea>
      </div>

      <div style="display: flex; gap: 8px; align-items: center; margin-bottom: 20px;">
        <button class="btn-primary" :disabled="loading" @click="handleAnalyze">
          <span v-if="loading" class="btn-loading-content">
            <span class="loading-spinner-sm"></span>
            分析中...
          </span>
          <span v-else>开始分析</span>
        </button>
        <button class="btn-ghost" @click="clearForm">清空</button>
      </div>

      <transition name="fade" mode="out-in">
        <div v-if="result" class="result-section">
          <div class="result-card score-card">
            <div class="result-score">{{ result.matchScore }}%</div>
            <div class="result-title">匹配度</div>
            <div class="score-bar">
              <div class="score-fill" :style="{ width: result.matchScore + '%' }"></div>
            </div>
          </div>

          <div class="result-card">
            <div class="result-title">优势</div>
            <div class="result-content">
              <ul>
                <li v-for="item in result.strengths" :key="item">{{ item }}</li>
              </ul>
            </div>
          </div>

          <div class="result-card">
            <div class="result-title">短板</div>
            <div class="result-content">
              <ul>
                <li v-for="item in result.gaps" :key="item">{{ item }}</li>
              </ul>
            </div>
          </div>

          <div class="result-card">
            <div class="result-title">建议</div>
            <div class="result-content">
              <ul>
                <li v-for="item in result.suggestions" :key="item">{{ item }}</li>
              </ul>
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
import { analyzeJob } from '../api/agent'
import type { JobAnalyzeResponse } from '../types'

const loading = ref(false)
const result = ref<JobAnalyzeResponse | null>(null)

const form = reactive({
  jobTitle: 'Java 后端开发工程师',
  jobDescription: '负责 Java 后端服务开发、系统架构设计、数据库优化和安全能力建设。要求熟悉 Spring Boot、MySQL、Redis，具备微服务和分布式系统开发经验。'
})

function clearForm() {
  form.jobTitle = ''
  form.jobDescription = ''
  result.value = null
}

async function handleAnalyze() {
  if (!form.jobTitle.trim() || !form.jobDescription.trim()) {
    ElMessage.warning('请填写岗位名称和描述')
    return
  }

  loading.value = true
  result.value = null
  try {
    result.value = await analyzeJob({ ...form })
  } catch {
    ElMessage.error('分析失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.result-section {
  animation: slideInUp 0.3s ease;
}

@keyframes slideInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.score-card {
  text-align: center;
  padding: 24px 18px;
}

.score-bar {
  width: 100%;
  height: 6px;
  background: var(--bg-secondary);
  border-radius: 3px;
  margin-top: 12px;
  overflow: hidden;
}

.score-fill {
  height: 100%;
  background: var(--text);
  border-radius: 3px;
  transition: width 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.btn-loading-content {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.loading-spinner-sm {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
