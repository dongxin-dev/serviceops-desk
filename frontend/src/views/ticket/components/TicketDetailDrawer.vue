<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { getTicket } from '@/api/tickets'
import { isApiError } from '@/api/http'
import { formatDateTime } from '@/locales/format'
import TicketPriorityTag from './TicketPriorityTag.vue'
import TicketStatusTag from './TicketStatusTag.vue'
import TicketEditDialog from './TicketEditDialog.vue'
import type { Ticket } from '@/types/ticket'

const props = defineProps<{ modelValue: boolean; ticketId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; updated: [] }>()

const { t, locale } = useI18n()

const ticket = ref<Ticket | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const editVisible = ref(false)

// Race guard: only the newest request may write UI state; late responses
// from superseded opens are dropped silently. No library, no AbortController.
let requestSeq = 0

// Every open re-GETs the full ticket: list summaries must never masquerade
// as detail data.
watch(
  () => [props.modelValue, props.ticketId] as const,
  ([open, id]) => {
    if (open && id !== null) void load(id)
  },
  { immediate: true },
)

async function load(id: number) {
  const seq = ++requestSeq
  loading.value = true
  errorMessage.value = ''
  ticket.value = null
  try {
    const result = await getTicket(id)
    if (seq !== requestSeq) return
    ticket.value = result
  } catch (error) {
    if (seq !== requestSeq) return
    errorMessage.value = describeError(error)
    console.warn('ticket detail load failed', error)
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function onUpdated(updated: Ticket) {
  // The PATCH response is authoritative; no follow-up GET is needed.
  ticket.value = updated
  emit('updated')
}

function onStale() {
  // 409: reload the server's latest state; the user re-edits from there.
  if (props.ticketId !== null) void load(props.ticketId)
}

function onClosed() {
  ticket.value = null
  errorMessage.value = ''
  editVisible.value = false
}

function describeError(error: unknown): string {
  if (isApiError(error)) {
    switch (error.code) {
      case 'TicketNotFound':
        return t('errors.ticketNotFound')
      case 'NetworkError':
        return t('errors.networkError')
      case 'ValidationError':
      case 'InvalidParameter':
        return t('errors.validationError')
      default:
        return t('detail.loadFailed')
    }
  }
  return t('detail.loadFailed')
}

function dt(value: string | null): string {
  return formatDateTime(value, locale.value, t('common.none'))
}

function orNone(value: number | null | undefined): string {
  return value === null || value === undefined ? t('common.none') : String(value)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    direction="rtl"
    size="60%"
    :title="t('detail.title')"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="onClosed"
  >
    <el-skeleton v-if="loading" :rows="8" animated />
    <!-- never leave a blank drawer: failures render in place -->
    <el-alert
      v-else-if="errorMessage"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
    />
    <template v-else-if="ticket">
      <el-descriptions :column="2" border :title="t('detail.sections.basic')" class="detail-block">
        <el-descriptions-item :label="t('detail.fields.ticketNo')">{{
          ticket.ticketNo
        }}</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.category')">{{
          ticket.category
        }}</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.title')" :span="2">{{
          ticket.title
        }}</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.description')" :span="2">{{
          ticket.description
        }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions
        :column="2"
        border
        :title="t('detail.sections.assignment')"
        class="detail-block"
      >
        <el-descriptions-item :label="t('detail.fields.requesterId')">{{
          ticket.requesterId
        }}</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.currentAssigneeId')">{{
          orNone(ticket.currentAssigneeId)
        }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions :column="2" border :title="t('detail.sections.sla')" class="detail-block">
        <el-descriptions-item :label="t('detail.fields.slaPolicyId')">{{
          ticket.slaPolicyId
        }}</el-descriptions-item>
        <el-descriptions-item>&nbsp;</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.responseDueAt')">{{
          dt(ticket.responseDueAt)
        }}</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.resolutionDueAt')">{{
          dt(ticket.resolutionDueAt)
        }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions
        :column="2"
        border
        :title="t('detail.sections.lifecycle')"
        class="detail-block"
      >
        <el-descriptions-item :label="t('detail.fields.status')">
          <TicketStatusTag :status="ticket.status" />
        </el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.priority')">
          <TicketPriorityTag :priority="ticket.priority" />
        </el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.firstRespondedAt')">{{
          dt(ticket.firstRespondedAt)
        }}</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.resolvedAt')">{{
          dt(ticket.resolvedAt)
        }}</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.closedAt')">{{
          dt(ticket.closedAt)
        }}</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.escalationLevel')">{{
          ticket.escalationLevel
        }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions
        :column="2"
        border
        :title="t('detail.sections.system')"
        class="detail-block"
      >
        <el-descriptions-item :label="t('detail.fields.version')">{{
          ticket.version
        }}</el-descriptions-item>
        <el-descriptions-item>&nbsp;</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.createdAt')">{{
          dt(ticket.createdAt)
        }}</el-descriptions-item>
        <el-descriptions-item :label="t('detail.fields.updatedAt')">{{
          dt(ticket.updatedAt)
        }}</el-descriptions-item>
      </el-descriptions>
    </template>

    <template #footer>
      <el-button type="primary" :disabled="loading || !ticket" @click="editVisible = true">{{
        t('ticket.actions.edit')
      }}</el-button>
    </template>

    <TicketEditDialog
      v-if="ticket"
      v-model="editVisible"
      :ticket="ticket"
      @updated="onUpdated"
      @stale="onStale"
    />
  </el-drawer>
</template>

<style scoped>
.detail-block {
  margin-bottom: 16px;
}
</style>
