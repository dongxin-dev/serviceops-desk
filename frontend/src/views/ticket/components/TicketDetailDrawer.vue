<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'

import {
  closeTicket,
  getTicket,
  reopenTicket,
  resolveTicket,
  startTicket,
} from '@/api/tickets'
import { isApiError } from '@/api/http'
import { formatDateTime } from '@/locales/format'
import TicketPriorityTag from './TicketPriorityTag.vue'
import TicketStatusTag from './TicketStatusTag.vue'
import TicketEditDialog from './TicketEditDialog.vue'
import TicketAssignDialog from './TicketAssignDialog.vue'
import type { Ticket, TicketActionInput } from '@/types/ticket'

/** Lifecycle actions executed from the drawer footer (no CLAIM endpoint exists). */
type TicketLifecycleAction = 'start' | 'resolve' | 'close' | 'reopen'

const props = defineProps<{ modelValue: boolean; ticketId: number | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  updated: [ticket: Ticket]
}>()

const { t, locale } = useI18n()

const ticket = ref<Ticket | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const editVisible = ref(false)
const assignVisible = ref(false)
// At most one lifecycle mutation runs at a time: the executing button shows
// loading while every other mutation entry point stays disabled.
const pendingAction = ref<TicketLifecycleAction | null>(null)
const busy = computed(() => loading.value || pendingAction.value !== null)

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
  emit('updated', updated)
}

function onAssigned(updated: Ticket) {
  // Same flow as edit: the response carries the full ticket, freshly
  // incremented version included.
  ticket.value = updated
  emit('updated', updated)
}

/**
 * Shared reload for edit-stale, action-stale and illegal-transition: always
 * goes through the requestSeq-guarded load(), never a second mechanism.
 */
function reloadDetail() {
  if (props.ticketId !== null) void load(props.ticketId)
}

const ACTION_API: Record<
  TicketLifecycleAction,
  (id: number, input: TicketActionInput) => Promise<Ticket>
> = {
  start: startTicket,
  resolve: resolveTicket,
  close: closeTicket,
  reopen: reopenTicket,
}

const ACTION_FEEDBACK: Record<TicketLifecycleAction, string> = {
  start: 'feedback.started',
  resolve: 'feedback.resolved',
  close: 'feedback.closed',
  reopen: 'feedback.reopened',
}

async function runAction(action: TicketLifecycleAction) {
  const current = ticket.value
  if (!current || pendingAction.value !== null) return

  if (action === 'close') {
    // CLOSED is the terminal state - Close is the only action with a
    // confirmation. Cancelling sends no request at all.
    try {
      await ElMessageBox.confirm(t('closeConfirm.message'), t('closeConfirm.title'), {
        confirmButtonText: t('closeConfirm.confirm'),
        cancelButtonText: t('closeConfirm.cancel'),
        type: 'warning',
      })
    } catch {
      return
    }
  }

  pendingAction.value = action
  try {
    const updated = await ACTION_API[action](current.id, { version: current.version })
    ticket.value = updated
    emit('updated', updated)
    ElMessage.success(t(ACTION_FEEDBACK[action]))
  } catch (error) {
    handleMutationError(error)
  } finally {
    pendingAction.value = null
  }
}

function handleMutationError(error: unknown): void {
  if (isApiError(error)) {
    if (error.code === 'TicketStaleRevision') {
      ElMessage.warning(t('errors.staleRevision'))
      reloadDetail()
      return
    }
    if (error.code === 'IllegalTicketStateTransition') {
      // Distinct copy, same reload helper: a rejected transition means the
      // visible matrix came from a stale state.
      ElMessage.warning(t('errors.illegalStateTransition'))
      reloadDetail()
      return
    }
    if (error.code === 'TicketNotFound') {
      ElMessage.error(t('errors.ticketNotFound'))
      return
    }
    if (error.code === 'NetworkError') {
      ElMessage.error(t('errors.networkError'))
      return
    }
  }
  ElMessage.error(t('errors.unknownError'))
  console.warn('ticket action failed', error)
}

function onClosed() {
  ticket.value = null
  errorMessage.value = ''
  editVisible.value = false
  assignVisible.value = false
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
      <div class="drawer-actions">
        <div class="drawer-actions__group">
          <!-- Action visibility only: the backend domain stays the final
               judge and answers any illegal call with 409. -->
          <el-button
            v-if="ticket && ticket.status === 'OPEN'"
            type="primary"
            :disabled="busy"
            @click="assignVisible = true"
          >
            {{ t('ticket.actions.assign') }}
          </el-button>
          <el-button
            v-if="ticket && ticket.status === 'REOPENED'"
            type="primary"
            :disabled="busy"
            @click="assignVisible = true"
          >
            {{ t('ticket.actions.reassign') }}
          </el-button>
          <el-button
            v-if="ticket && ticket.status === 'ASSIGNED'"
            type="primary"
            :loading="pendingAction === 'start'"
            :disabled="busy"
            @click="runAction('start')"
          >
            {{ t('ticket.actions.start') }}
          </el-button>
          <el-button
            v-if="ticket && ticket.status === 'IN_PROGRESS'"
            type="primary"
            :loading="pendingAction === 'resolve'"
            :disabled="busy"
            @click="runAction('resolve')"
          >
            {{ t('ticket.actions.resolve') }}
          </el-button>
          <template v-if="ticket && ticket.status === 'RESOLVED'">
            <el-button
              type="warning"
              :loading="pendingAction === 'close'"
              :disabled="busy"
              @click="runAction('close')"
            >
              {{ t('ticket.actions.close') }}
            </el-button>
            <el-button
              :loading="pendingAction === 'reopen'"
              :disabled="busy"
              @click="runAction('reopen')"
            >
              {{ t('ticket.actions.reopen') }}
            </el-button>
          </template>
        </div>
        <el-button :disabled="busy || !ticket" @click="editVisible = true">{{
          t('ticket.actions.edit')
        }}</el-button>
      </div>
    </template>

    <TicketEditDialog
      v-if="ticket"
      v-model="editVisible"
      :ticket="ticket"
      @updated="onUpdated"
      @stale="reloadDetail"
    />

    <TicketAssignDialog
      v-if="ticket"
      v-model="assignVisible"
      :ticket="ticket"
      @assigned="onAssigned"
      @reload="reloadDetail"
    />
  </el-drawer>
</template>

<style scoped>
.detail-block {
  margin-bottom: 16px;
}

.drawer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.drawer-actions__group {
  display: flex;
  gap: 12px;
}
</style>
