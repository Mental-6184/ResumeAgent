<template>
  <div class="panel">
    <div class="panel-header">面试回答生成</div>
    <div class="panel-body">
      <div class="form-section">
        <label class="form-label">面试问题</label>
        <textarea
          class="form-textarea"
          v-model="form.question"
          rows="3"
          placeholder="输入面试问题，例如：介绍一下你的项目经历"
          @keydown.ctrl.enter="handleGenerate"
        ></textarea>
        <div class="form-hint">Ctrl + Enter 快速生成</div>
      </div>

      <div class="form-section">
        <label class="form-label">目标岗位（可选）</label>
        <input
          class="form-input"
          v-model="form.jobTitle"
          placeholder="例如：Java 安全开发工程师"
        />
      </div>

      <div class="action-bar">
        <button class="btn-primary" :disabled="loading" @click="handleGenerate">
          <span v-if="loading" class="btn-loading-content">
            <ThinkingIndicator variant="inline" />
          </span>
          <span v-else>生成回答</span>
        </button>

        <div class="example-chips">
          <span class="chips-label">示例：</span>
          <button
            v-for="example in examples"
            :key="example.label"
            class="chip-btn"
            @click="useExample(example)"
          >
            {{ example.label }}
          </button>
        </div>
      </div>

      <transition name="fade" mode="out-in">
        <div v-if="result" class="result-section">
          <div class="result-card answer-card">
            <div class="result-title">✦ 参考回答</div>
            <div class="result-content answer-content" v-html="formatText(result.answer)"></div>
          </div>

          <div class="result-columns">
            <div class="result-card">
              <div class="result-title">◈ 回答要点</div>
              <div class="result-content">
                <ul>
                  <li v-for="point in result.keyPoints" :key="point">{{ point }}</li>
                </ul>
              </div>
            </div>

            <div class="result-card">
              <div class="result-title">◇ 回答技巧</div>
              <div class="result-content">
                <ul>
                  <li v-for="tip in result.tips" :key="tip">{{ tip }}</li>
                </ul>
              </div>
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
import { generateInterviewAnswer } from '../api/agent'
import type { InterviewAnswerResponse } from '../types'
import ThinkingIndicator from './ThinkingIndicator.vue'

interface Example {
  label: string
  question: string
}

const loading = ref(false)
const result = ref<InterviewAnswerResponse | null>(null)

const form = reactive({
  question: '',
  jobTitle: 'Java 后端开发工程师'
})

const examples: Example[] = [
  { label: '项目难点', question: '介绍一下你项目中遇到的主要难点和挑战' },
  { label: '技术优势', question: '你的技术优势是什么，和同龄人相比有什么不同' },
  { label: '职业规划', question: '你的职业规划是什么' },
  { label: '实习经历', question: '介绍一下你在昆明逐远浪商贸的实习经历和收获' }
]

const formatText = (value: string) => value.replace(/\n/g, '<br>')

function useExample(example: Example) {
  form.question = example.question
}

async function handleGenerate() {
  if (!form.question.trim()) {
    ElMessage.warning('请输入面试问题')
    return
  }

  loading.value = true
  result.value = null
  try {
    result.value = await generateInterviewAnswer({ ...form })
  } catch {
    ElMessage.error('生成失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.form-hint {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 6px;
}

.action-bar {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;
}

.example-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.chips-label {
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

.answer-card {
  padding: 20px 18px;
}

.answer-content {
  font-size: 15px;
  line-height: 1.8;
}

.result-columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 12px;
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

@media (max-width: 640px) {
  .result-columns {
    grid-template-columns: 1fr;
  }
}
</style>
