<template>
  <transition name="modal">
    <div v-if="visible" class="guide-overlay" @click.self="close">
      <div class="guide-modal">
        <!-- 头部 -->
        <div class="guide-header">
          <div class="guide-title">
            <span class="guide-icon">{{ currentStep.icon }}</span>
            <span>{{ currentStep.title }}</span>
          </div>
          <button class="guide-close" @click="close" title="关闭">✕</button>
        </div>

        <!-- 内容区 -->
        <div class="guide-body">
          <transition name="slide" mode="out-in">
            <div :key="step" class="guide-content">
              <div class="guide-desc">{{ currentStep.description }}</div>
              <div class="guide-details">
                <div v-for="(item, i) in currentStep.items" :key="i" class="guide-item">
                  <span class="guide-item-icon">{{ item.icon }}</span>
                  <div class="guide-item-text">
                    <span class="guide-item-label">{{ item.label }}</span>
                    <span class="guide-item-desc">{{ item.desc }}</span>
                  </div>
                </div>
              </div>
              <div v-if="currentStep.tip" class="guide-tip">
                <span class="guide-tip-icon">💡</span>
                <span>{{ currentStep.tip }}</span>
              </div>
            </div>
          </transition>
        </div>

        <!-- 底部导航 -->
        <div class="guide-footer">
          <div class="guide-dots">
            <span
              v-for="(_, i) in steps"
              :key="i"
              class="guide-dot"
              :class="{ active: i === step }"
              @click="step = i"
            ></span>
          </div>
          <div class="guide-actions">
            <button
              v-if="step > 0"
              class="guide-btn guide-btn-secondary"
              @click="prev"
            >
              ← 上一步
            </button>
            <button
              v-if="step < steps.length - 1"
              class="guide-btn guide-btn-primary"
              @click="next"
            >
              下一步 →
            </button>
            <button
              v-else
              class="guide-btn guide-btn-primary"
              @click="close"
            >
              开始使用 ✓
            </button>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'navigate', panel: string): void
}>()

const step = ref(0)

interface GuideItem {
  icon: string
  label: string
  desc: string
}

interface GuideStep {
  icon: string
  title: string
  description: string
  items: GuideItem[]
  tip?: string
  panel?: string
}

const steps: GuideStep[] = [
  {
    icon: '✦',
    title: '欢迎使用 ResumeAgent',
    description: '这是一个基于 RAG 的模拟面试系统，AI 会以你的身份回答面试官的问题。以下是各模块的功能介绍。',
    items: [
      { icon: '◈', label: '模拟面试', desc: '以你的身份回答面试问题' },
      { icon: '◎', label: '岗位匹配', desc: '分析简历与目标岗位的匹配度' },
      { icon: '◇', label: '面试助手', desc: '生成个性化面试回答' },
      { icon: '◈', label: '技能分析', desc: '梳理技术栈与能力图谱' },
      { icon: '▣', label: '项目查询', desc: '深度检索项目经历细节' },
      { icon: '⊞', label: '项目详情', desc: '查看所有项目的详细信息' }
    ],
    tip: '点击左侧导航栏的图标可随时切换模块'
  },
  {
    icon: '▸',
    title: '模拟面试',
    description: '核心功能 — AI 以你的身份回答面试官的问题，全程第一人称，语气自然口语化。',
    items: [
      { icon: '▸', label: '简历问答模式', desc: '基于知识库回答，AI 就是你本人' },
      { icon: '◆', label: '通用对话模式', desc: '纯大模型对话，不限于简历场景' },
      { icon: '↩', label: '支持流式输出', desc: '打字机效果，实时感知生成过程' }
    ],
    tip: '面试前先上传简历和项目文档到知识库，效果最佳',
    panel: 'chat'
  },
  {
    icon: '◎',
    title: '岗位匹配分析',
    description: '输入目标岗位的名称和描述，系统会分析你与岗位的匹配度、优势、差距和提升建议。',
    items: [
      { icon: '◎', label: '匹配度评分', desc: '基于技术关键词重叠计算匹配分数' },
      { icon: '◈', label: '核心优势', desc: '从知识库中提取与岗位匹配的优势' },
      { icon: '◇', label: '能力差距', desc: '识别岗位要求但你尚未具备的技能' },
      { icon: '▣', label: '提升建议', desc: '针对差距给出具体的学习路径' }
    ],
    tip: '岗位描述越详细，分析结果越准确',
    panel: 'analyze'
  },
  {
    icon: '◇',
    title: '面试助手',
    description: '输入面试问题，系统基于你的真实经历生成个性化的回答，采用 STAR 法则组织。',
    items: [
      { icon: '◇', label: 'STAR 法则', desc: '情境 → 任务 → 行动 → 结果' },
      { icon: '◈', label: '核心要点', desc: '提炼面试官最关注的要点' },
      { icon: '◎', label: '回答技巧', desc: '提供实用的回答建议' }
    ],
    tip: '可以指定目标岗位，回答会更有针对性',
    panel: 'interview'
  },
  {
    icon: '◈',
    title: '技能分析',
    description: '自动从知识库中提取你的技术栈，生成能力图谱、技术优势和进阶建议。',
    items: [
      { icon: '◈', label: '核心技能', desc: '从项目中提取并评估熟练度' },
      { icon: '◎', label: '技术优势', desc: '结合具体项目案例的深度分析' },
      { icon: '◇', label: '覆盖面分析', desc: '按领域分类的技术能力评估' },
      { icon: '▣', label: '进阶建议', desc: '基于现有技能的成长路径' }
    ],
    panel: 'skill'
  },
  {
    icon: '▣',
    title: '项目查询 & 详情',
    description: '通过关键词或技术栈检索项目经历，查看项目的详细信息和技术实现。',
    items: [
      { icon: '▣', label: '项目查询', desc: '按名称或技术栈搜索项目' },
      { icon: '⊞', label: '项目详情', desc: '查看所有项目的完整信息' },
      { icon: '◈', label: '技术栈筛选', desc: '快速筛选特定技术栈的项目' }
    ],
    panel: 'project'
  },
  {
    icon: '✦',
    title: '开始使用',
    description: '现在你已经了解了所有模块的功能，可以开始使用了！',
    items: [
      { icon: '▸', label: '第一步', desc: '在「知识库」页面上传你的简历和项目文档' },
      { icon: '◈', label: '第二步', desc: '进入「模拟面试」体验 AI 以你的身份回答问题' },
      { icon: '◎', label: '第三步', desc: '尝试「岗位匹配」和「技能分析」深入了解自己' }
    ],
    tip: '侧边栏底部的绿点可以查看 LLM 配置状态'
  }
]

const currentStep = computed(() => steps[step.value])

function next() {
  if (step.value < steps.length - 1) {
    step.value++
  }
}

function prev() {
  if (step.value > 0) {
    step.value--
  }
}

function close() {
  step.value = 0
  emit('close')
}
</script>

<style scoped>
.guide-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.guide-modal {
  width: 520px;
  max-width: 90vw;
  max-height: 80vh;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12), 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

/* 头部 */
.guide-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--border);
}

.guide-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 600;
  color: var(--text);
}

.guide-icon {
  font-size: 20px;
  opacity: 0.7;
}

.guide-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.15s;
}

.guide-close:hover {
  background: var(--bg-hover);
  color: var(--text);
}

/* 内容区 */
.guide-body {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.guide-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.guide-desc {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.guide-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.guide-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 10px;
  transition: all 0.15s;
}

.guide-item:hover {
  border-color: var(--text-muted);
}

.guide-item-icon {
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 1px;
  opacity: 0.7;
}

.guide-item-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.guide-item-label {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
}

.guide-item-desc {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.guide-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 10px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.guide-tip-icon {
  flex-shrink: 0;
}

/* 底部 */
.guide-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-top: 1px solid var(--border);
}

.guide-dots {
  display: flex;
  gap: 6px;
}

.guide-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--border);
  cursor: pointer;
  transition: all 0.2s;
}

.guide-dot.active {
  background: var(--text);
  transform: scale(1.2);
}

.guide-dot:hover:not(.active) {
  background: var(--text-muted);
}

.guide-actions {
  display: flex;
  gap: 8px;
}

.guide-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  font-family: var(--font-mono);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.guide-btn-secondary {
  background: var(--bg-secondary);
  color: var(--text-secondary);
  border: 1px solid var(--border);
}

.guide-btn-secondary:hover {
  background: var(--bg-hover);
  color: var(--text);
}

.guide-btn-primary {
  background: var(--text);
  color: var(--bg);
}

.guide-btn-primary:hover {
  opacity: 0.9;
}

/* 动画 */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.25s ease;
}

.modal-enter-active .guide-modal,
.modal-leave-active .guide-modal {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .guide-modal,
.modal-leave-to .guide-modal {
  transform: scale(0.95) translateY(10px);
  opacity: 0;
}

.slide-enter-active,
.slide-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.slide-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.slide-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

/* 响应式 */
@media (max-width: 560px) {
  .guide-modal {
    width: 95vw;
    max-height: 90vh;
  }

  .guide-header {
    padding: 16px 18px 12px;
  }

  .guide-body {
    padding: 18px;
  }

  .guide-footer {
    padding: 12px 18px;
  }
}
</style>
