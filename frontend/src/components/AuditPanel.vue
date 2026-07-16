<template>
  <div class="panel">
    <div class="panel-header">安全审计</div>
    <div class="panel-body">
      <div style="display: flex; gap: 10px; align-items: center; margin-bottom: 20px;">
        <button class="btn-primary" :disabled="loading" @click="loadLogs">
          {{ loading ? '加载中...' : '刷新日志' }}
        </button>
        <input
          class="form-input"
          type="number"
          v-model.number="limit"
          min="5"
          max="100"
          style="width: 80px;"
        />
        <span style="font-family: var(--font-mono); font-size: 12px; color: var(--text-muted);">条</span>
      </div>

      <div v-if="logs.length === 0" class="empty-state">
        暂无审计记录
      </div>

      <div v-else>
        <div v-for="item in logs" :key="`${item.timestamp}-${item.category}`" class="audit-item">
          <div class="audit-meta">
            <span>{{ item.timestamp }}</span>
            <span>{{ item.category }}</span>
            <span v-if="item.conversationId">{{ item.conversationId }}</span>
          </div>
          <div class="audit-block">
            <div class="audit-label">输入</div>
            <div class="audit-text">{{ item.input }}</div>
          </div>
          <div class="audit-block">
            <div class="audit-label">输出</div>
            <div class="audit-text">{{ item.output }}</div>
          </div>
          <div v-if="item.notes" class="audit-block">
            <div class="audit-label">备注</div>
            <div class="audit-text">{{ item.notes }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAuditLogs } from '../api/audit'
import type { AuditEntryResponse } from '../types'

const loading = ref(false)
const limit = ref(20)
const logs = ref<AuditEntryResponse[]>([])

async function loadLogs() {
  loading.value = true
  try {
    logs.value = await getAuditLogs(limit.value)
  } catch {
    ElMessage.error('审计日志加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadLogs()
})
</script>
