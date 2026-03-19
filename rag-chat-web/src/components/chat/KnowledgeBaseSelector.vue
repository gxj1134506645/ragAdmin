<script setup lang="ts">
import { computed } from 'vue'
import type { KnowledgeBaseSummary } from '@/types/knowledge-base'

const props = defineProps<{
  modelValue: number[]
  options: KnowledgeBaseSummary[]
  loading?: boolean
  lockedKbId?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
}>()

const normalizedValue = computed(() => {
  const ordered = new Set<number>()
  if (typeof props.lockedKbId === 'number') {
    ordered.add(props.lockedKbId)
  }
  props.modelValue.forEach((item) => {
    ordered.add(item)
  })
  return Array.from(ordered)
})

function handleChange(values: number[]): void {
  const ordered = new Set<number>()
  if (typeof props.lockedKbId === 'number') {
    ordered.add(props.lockedKbId)
  }
  values.forEach((item) => {
    ordered.add(item)
  })
  emit('update:modelValue', Array.from(ordered))
}
</script>

<template>
  <div class="selector-card">
    <div class="selector-head">
      <span>检索知识库</span>
      <small>已选 {{ normalizedValue.length }} 个</small>
    </div>
    <el-select
      :model-value="normalizedValue"
      multiple
      filterable
      collapse-tags
      collapse-tags-tooltip
      :max-collapse-tags="2"
      :loading="loading"
      placeholder="可为空，表示纯模型问答"
      @change="handleChange"
    >
      <el-option
        v-for="item in options"
        :key="item.id"
        :label="item.id === lockedKbId ? `${item.kbName}（当前知识库）` : item.kbName"
        :value="item.id"
      />
    </el-select>
    <p v-if="typeof lockedKbId === 'number'" class="selector-tip">
      当前知识库已固定参与检索，不能从本会话中移除。
    </p>
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

.selector-head small,
.selector-tip {
  color: var(--text-muted);
}

.selector-tip {
  margin: 0;
  font-size: 12px;
}
</style>
