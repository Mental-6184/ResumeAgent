<template>
  <div class="panel">
    <div class="panel-header">项目查询</div>
    <div class="panel-body">
      <div class="form-section">
        <label class="form-label">查询关键词</label>
        <input
          class="form-input"
          v-model="form.query"
          placeholder="输入项目名称或关键词"
          @keydown.enter="handleQuery"
        />
      </div>
      <div class="form-section">
        <label class="form-label">技术栈筛选（可选）</label>
        <input
          class="form-input"
          v-model="form.techStack"
          placeholder="例如：Spring Boot, Vue3"
        />
      </div>

      <div class="action-bar">
        <button class="btn-primary" :disabled="loading" @click="handleQuery">
          <span v-if="loading" class="btn-loading-content">
            <ThinkingIndicator variant="inline" />
          </span>
          <span v-else>查询项目</span>
        </button>

        <div class="quick-filters">
          <span class="filter-label">快速筛选：</span>
          <button
            v-for="filter in quickFilters"
            :key="filter"
            class="chip-btn"
            @click="applyFilter(filter)"
          >
            {{ filter }}
          </button>
        </div>
      </div>

      <transition name="fade" mode="out-in">
        <div v-if="result" class="result-section">
          <div class="result-card summary-card">
            <div class="result-title">✦ 摘要</div>
            <div class="result-content">{{ result.summary }}</div>
            <div v-if="result.projects.length > 0" class="result-count">
              共找到 {{ result.projects.length }} 个项目
            </div>
          </div>

          <div v-if="result.projects.length > 0" class="projects-list">
            <div
              v-for="(project, index) in result.projects"
              :key="project.name"
              class="project-item"
              :style="{ animationDelay: `${index * 0.1}s` }"
            >
              <div class="project-header">
                <div class="project-name">{{ project.name }}</div>
                <div class="project-role">{{ project.role }}</div>
              </div>
              <div class="project-desc">{{ project.description }}</div>
              <div class="project-meta">
                <span class="meta-label">技术栈：</span>
                <span class="result-tags" style="display: inline-flex;">
                  <span v-for="tech in project.techStack" :key="tech" class="tag">{{ tech }}</span>
                </span>
              </div>
              <div class="project-highlights">
                <div class="meta-label">项目亮点：</div>
                <ul>
                  <li v-for="h in project.highlights" :key="h">{{ h }}</li>
                </ul>
              </div>
            </div>
          </div>

          <div v-else class="empty-state">
            未找到匹配的项目
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { queryProjects } from '../api/agent'
import type { ProjectQueryResponse } from '../types'
import ThinkingIndicator from './ThinkingIndicator.vue'

const loading = ref(false)
const result = ref<ProjectQueryResponse | null>(null)

const form = reactive({
  query: 'Spring Boot',
  techStack: ''
})

const quickFilters = ['Spring Boot', 'Vue', 'MySQL', 'Redis']

function applyFilter(filter: string) {
  form.query = filter
  handleQuery()
}

async function handleQuery() {
  if (!form.query.trim()) {
    ElMessage.warning('请输入查询关键词')
    return
  }

  loading.value = true
  result.value = null
  try {
    result.value = await queryProjects({ ...form })
  } catch {
    ElMessage.error('查询失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.action-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.quick-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-label {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
}

.chip-btn {
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid var(--border-strong);
  border-radius: 20px;
  font-family: var(--font-sans);
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-normal);
}

.chip-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
  transform: translateY(-1px);
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

.result-count {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--accent);
  margin-top: 8px;
}

.projects-list {
  margin-top: 16px;
}

.project-item {
  animation: cardIn 0.3s ease backwards;
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.project-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.project-role {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.btn-loading-content {
  display: inline-flex;
  align-items: center;
  gap: 6px;
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
