<template>
  <div class="panel">
    <div class="panel-header">技能分析</div>
    <div class="panel-body">
      <div class="intro-section">
        <p class="intro-text">
          基于知识库中的简历和项目数据，自动分析技能画像
        </p>
        <button class="btn-primary" :disabled="loading" @click="handleAnalyze">
          <span v-if="loading" class="btn-loading-content">
            <span class="loading-spinner-sm"></span>
            分析中...
          </span>
          <span v-else>一键分析技能</span>
        </button>
      </div>

      <transition name="fade" mode="out-in">
        <div v-if="result" class="result-section">
          <div class="result-card summary-card">
            <div class="result-title">摘要</div>
            <div class="result-content">{{ result.summary }}</div>
          </div>

          <div class="result-card">
            <div class="result-title">核心技能</div>
            <div class="result-tags">
              <span v-for="skill in result.coreSkills" :key="skill" class="tag skill-tag">
                {{ skill }}
              </span>
            </div>
          </div>

          <div class="result-card">
            <div class="result-title">技术优势</div>
            <div class="result-content">
              <ul>
                <li v-for="s in result.technicalStrengths" :key="s">{{ s }}</li>
              </ul>
            </div>
          </div>

          <div class="result-card">
            <div class="result-title">能力覆盖面</div>
            <div class="result-tags" v-if="result.coverageAreas.length > 0">
              <span v-for="area in result.coverageAreas" :key="area" class="tag area-tag">
                {{ area }}
              </span>
            </div>
            <div v-else class="empty-hint">
              暂未识别到明确的能力覆盖领域
            </div>
          </div>

          <div class="result-card">
            <div class="result-title">改进建议</div>
            <div class="result-content">
              <ul>
                <li v-for="s in result.improvementSuggestions" :key="s">{{ s }}</li>
              </ul>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { analyzeSkills } from '../api/agent'
import type { SkillAnalysisResponse } from '../types'

const loading = ref(false)
const result = ref<SkillAnalysisResponse | null>(null)

async function handleAnalyze() {
  loading.value = true
  result.value = null
  try {
    result.value = await analyzeSkills()
  } catch {
    ElMessage.error('分析失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.intro-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.intro-text {
  color: var(--text-muted);
  font-size: 13px;
  margin: 0;
  flex: 1;
  min-width: 200px;
}

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

.summary-card {
  padding: 20px 18px;
}

.skill-tag {
  transition: all var(--transition-fast);
}

.skill-tag:hover {
  background: var(--text);
  color: var(--bg);
  border-color: var(--text);
}

.area-tag {
  background: var(--bg);
  border-style: dashed;
}

.area-tag:hover {
  border-style: solid;
  background: var(--bg-secondary);
}

.empty-hint {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 6px;
  font-style: italic;
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
