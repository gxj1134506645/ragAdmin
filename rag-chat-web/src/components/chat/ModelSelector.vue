<script setup lang="ts">
import type { ModelSummary } from '@/types/model'

defineProps<{
  modelValue?: number | null
  options: ModelSummary[]
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
}>()

function handleChange(value: number | string | boolean | undefined): void {
  if (typeof value === 'number') {
    emit('update:modelValue', value)
    return
  }
  emit('update:modelValue', undefined)
}
</script>

<template>
  <div class="selector-card">
    <div class="selector-head">
      <span>聊天模型</span>
      <small>{{ modelValue ? '已显式指定' : '跟随系统默认' }}</small>
    </div>
    <el-select
      :model-value="modelValue ?? undefined"
      filterable
      clearable
      :loading="loading"
      placeholder="选择当前会话使用的模型"
      @change="handleChange"
    >
      <el-option
        v-for="item in options"
        :key="item.id"
        :label="`${item.modelName} · ${item.providerName}`"
        :value="item.id"
      />
    </el-select>
  </div>
</template>

<style scoped>
.selector-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.selector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.selector-head span {
  font-weight: 600;
}

.selector-head small {
  color: var(--text-muted);
}
</style>
