<script setup lang="ts">
import ModelFormDialog from '@/components/model/ModelFormDialog.vue'
import ModelProviderDrawer from '@/components/model/ModelProviderDrawer.vue'
import ModelProviderFormDialog from '@/components/model/ModelProviderFormDialog.vue'
import ModelTable from '@/components/model/ModelTable.vue'
import ModelToolbar from '@/components/model/ModelToolbar.vue'
import { useModelManagement } from '@/composables/useModelManagement'

const {
  providerLoading,
  providerSubmitting,
  providerDrawerVisible,
  providerFormDialogVisible,
  providerCheckingIds,
  providers,
  providerHealthResult,
  activeProviderHealthId,
  modelLoading,
  modelSubmitting,
  batchDeleteSubmitting,
  modelDialogVisible,
  modelDialogMode,
  modelCheckingIds,
  defaultChatModelLoadingId,
  models,
  modelHealthResult,
  activeModelHealthId,
  modelLoadError,
  modelDialogDraft,
  pagination,
  modelQuery,
  selectedModelCount,
  shouldShowDefaultChatModelAlert,
  currentPageEmbeddingCount,
  currentPageUnsupportedEmbeddingCount,
  loadProviders,
  loadPageData,
  openProviderDrawer,
  openCreateProviderDialog,
  handleProviderHealthCheck,
  handleCreateProvider,
  openCreateModelDialog,
  openEditModelDialog,
  handleModelHealthCheck,
  handleSaveModel,
  handleDeleteModel,
  handleSelectionChange,
  handleBatchDeleteModels,
  handleSetDefaultChatModel,
  handleSearchModels,
  handleResetModels,
  handleCurrentChange,
  handleSizeChange,
} = useModelManagement()
</script>

<template>
  <section class="model-page">
    <ModelToolbar
      v-model:provider-code="modelQuery.providerCode"
      v-model:capability-type="modelQuery.capabilityType"
      v-model:status="modelQuery.status"
      :providers="providers"
      :total="pagination.total"
      :current-page-count="models.length"
      :current-page-embedding-count="currentPageEmbeddingCount"
      :current-page-unsupported-embedding-count="currentPageUnsupportedEmbeddingCount"
      :selected-count="selectedModelCount"
      :loading="modelLoading"
      :batch-delete-submitting="batchDeleteSubmitting"
      :should-show-default-chat-model-alert="shouldShowDefaultChatModelAlert"
      @search="handleSearchModels"
      @reset="handleResetModels"
      @refresh="loadPageData"
      @open-provider-drawer="openProviderDrawer"
      @open-create-model="openCreateModelDialog"
      @batch-delete="handleBatchDeleteModels"
    />

    <ModelTable
      :models="models"
      :loading="modelLoading"
      :checking-ids="modelCheckingIds"
      :default-chat-model-loading-id="defaultChatModelLoadingId"
      :health-result="modelHealthResult"
      :active-health-id="activeModelHealthId"
      :pagination="pagination"
      :model-load-error="modelLoadError"
      @edit="openEditModelDialog"
      @health-check="handleModelHealthCheck"
      @set-default-chat-model="handleSetDefaultChatModel"
      @delete="handleDeleteModel"
      @selection-change="handleSelectionChange"
      @current-change="handleCurrentChange"
      @size-change="handleSizeChange"
    />

    <ModelProviderDrawer
      v-model="providerDrawerVisible"
      :providers="providers"
      :loading="providerLoading"
      :checking-ids="providerCheckingIds"
      :health-result="providerHealthResult"
      :active-health-id="activeProviderHealthId"
      @refresh="loadProviders"
      @open-create="openCreateProviderDialog"
      @health-check="handleProviderHealthCheck"
    />

    <ModelProviderFormDialog
      v-model="providerFormDialogVisible"
      :submitting="providerSubmitting"
      @submit="handleCreateProvider"
    />

    <ModelFormDialog
      v-model="modelDialogVisible"
      :mode="modelDialogMode"
      :submitting="modelSubmitting"
      :providers="providers"
      :initial-form="modelDialogDraft"
      @submit="handleSaveModel"
    />
  </section>
</template>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
</style>
