<template>
  <div class="thinking-indicator" :class="variant">
    <span class="thinking-icon">{{ currentIcon }}</span>
    <span class="thinking-text">{{ currentText }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'

type Variant = 'default' | 'inline' | 'dots'

const props = withDefaults(defineProps<{
  variant?: Variant
}>(), {
  variant: 'default'
})

// 有趣的提示文案列表
const messages = [
  '大学生没钱买好模型',
  '用爱发电中',
  '白嫖算力中',
  '模型在吃泡面',
  'GPU在哭穷',
  '服务器在省钱',
  '模型在搬砖赚算力',
  '穷但努力中',
  '廉价算力加载中',
  '免费额度消耗中',
  '学生优惠已用完',
  '模型在兼职中',
  '算力靠众筹',
  '服务器在吃土',
  '努力但没钱',
]

// 对应的图标
const icons = [
  '😭', '🍜', '💸', '🥲', '💀', '🤡', '🫠', '💔', '📉', '🥺', '😤', '🎓', '💻', '🔥', '✨'
]

const currentIndex = ref(0)
const currentText = ref(messages[0])
const currentIcon = ref(icons[0])
let timer: number | null = null

onMounted(() => {
  // 随机起始位置
  currentIndex.value = Math.floor(Math.random() * messages.length)
  currentText.value = messages[currentIndex.value]
  currentIcon.value = icons[currentIndex.value % icons.length]

  // 每2秒切换一次文案
  timer = window.setInterval(() => {
    currentIndex.value = (currentIndex.value + 1) % messages.length
    currentText.value = messages[currentIndex.value]
    currentIcon.value = icons[currentIndex.value % icons.length]
  }, 2000)
})

onBeforeUnmount(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.thinking-indicator {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
  color: var(--text-muted);
  animation: fadeInOut 2s ease-in-out infinite;
  white-space: nowrap;
}

.thinking-indicator.inline {
  font-size: 14px;
  gap: 6px;
}

.thinking-indicator.dots {
  gap: 4px;
}

.thinking-icon {
  font-size: 18px;
  animation: bounce 1s ease-in-out infinite;
}

.thinking-indicator.inline .thinking-icon {
  font-size: 16px;
}

.thinking-text {
  font-size: 14px;
  opacity: 0.9;
}

.thinking-indicator.inline .thinking-text {
  font-size: 14px;
  opacity: 0.85;
  font-weight: 500;
}

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

@keyframes fadeInOut {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}
</style>
