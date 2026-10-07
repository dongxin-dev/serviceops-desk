<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { listTickets } from '@/api/tickets'
import { isApiError } from '@/api/http'
import { formatDateTime } from '@/locales/format'
import TicketDetailDrawer from './components/TicketDetailDrawer.vue'
import TicketCreateDialog from './components/TicketCreateDialog.vue'
import TicketPriorityTag from './components/TicketPriorityTag.vue'
import TicketStatusTag from './components/TicketStatusTag.vue'
import type { TicketPriority, TicketStatus, TicketSummary } from '@/types/ticket'

const { t, locale } = useI18n()

const STATUS_OPTIONS: TicketStatus[] = [
  'OPEN',
  'ASSIGNED',
  'IN_PROGRESS',
  'RESOLVED',
  'CLOSED',
  'REOPENED',
]
const PRIORITY_OPTIONS: TicketPriority[] = ['P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW']

const rows = ref<TicketSummary[]>([])
const totalElements = ref(0)
const loading = ref(false)
const errorMessage = ref('')

const statusFilter = ref<TicketStatus | ''>('')
const priorityFilter = ref<TicketPriority | ''>('')

// Element Plus pagination is 1-based; the backend API is 0-based.
// The translation happens exactly once, in load().
const uiPage = ref(1)
const uiSize = ref(20)

const drawerOpen = ref(false)
const selectedId = ref<number | null>(null)
const createVisible = ref(false)

// Race guard: only the newest request may write UI state; late responses
// from superseded loads (fast filter/page switches) are dropped silently.
let requestSeq = 0

async function load() {
  const seq = ++requestSeq
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listTickets({
      page: uiPage.value - 1,
      size: uiSize.value,
      status: statusFilter.value === '' ? undefined : statusFilter.value,
      priority: priorityFilter.value === '' ? undefined : priorityFilter.value,
    })
    if (seq !== requestSeq) return
    rows.value = result.content
    totalElements.value = result.totalElements
  } catch (error) {
    if (seq !== requestSeq) return
    rows.value = []
    totalElements.value = 0
    errorMessage.value = describeError(error)
    console.warn('ticket list load failed', error)
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

/** Filter / page-size changes always restart from the first page. */
function reloadFromFirstPage() {
  if (uiPage.value !== 1) {
    uiPage.value = 1 // the uiPage watcher below triggers the reload
  } else {
    void load()
  }
}

watch(uiPage, load)
watch([statusFilter, priorityFilter], reloadFromFirstPage)
watch(uiSize, reloadFromFirstPage)

onMounted(load)

function openDetail(row: TicketSummary) {
  selectedId.value = row.id
  drawerOpen.value = true
}

function describeError(error: unknown): string {
  if (isApiError(error)) {
    switch (error.code) {
      case 'NetworkError':
        return t('errors.networkError')
      case 'ValidationError':
      case 'InvalidParameter':
      case 'MalformedRequest':
        return t('errors.validationError')
      default:
        return t('ticket.loadFailed')
    }
  }
  return t('ticket.loadFailed')
}

function displayDate(value: string): string {
  return formatDateTime(value, locale.value, t('common.none'))
}
</script>

<template>
  <div class="list-toolbar">
    <el-select
      v-model="statusFilter"
      class="filter-select"
      :placeholder="t('ticket.filters.statusAll')"
      clearable
    >
      <el-option :value="''" :label="t('ticket.filters.statusAll')" />
      <el-option v-for="s in STATUS_OPTIONS" :key="s" :value="s" :label="t(`status.${s}`)" />
    </el-select>
    <el-select
      v-model="priorityFilter"
      class="filter-select"
      :placeholder="t('ticket.filters.priorityAll')"
      clearable
    >
      <el-option :value="''" :label="t('ticket.filters.priorityAll')" />
      <el-option v-for="p in PRIORITY_OPTIONS" :key="p" :value="p" :label="t(`priority.${p}`)" />
    </el-select>
    <el-button class="create-button" type="primary" @click="createVisible = true">{{
      t('ticket.actions.create')
    }}</el-button>
  </div>

  <div class="list-card">
    <el-alert
      v-if="errorMessage"
      class="list-error"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button size="small" @click="load">{{ t('ticket.retry') }}</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column :label="t('ticket.columns.ticketNo')" width="220">
        <template #default="{ row }: { row: TicketSummary }">
          <el-link type="primary" @click="openDetail(row)">{{ row.ticketNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ticket.columns.title')"
        prop="title"
        min-width="220"
        show-overflow-tooltip
      />
      <el-table-column :label="t('ticket.columns.priority')" width="120">
        <template #default="{ row }: { row: TicketSummary }">
          <TicketPriorityTag :priority="row.priority" />
        </template>
      </el-table-column>
      <el-table-column :label="t('ticket.columns.status')" width="120">
        <template #default="{ row }: { row: TicketSummary }">
          <TicketStatusTag :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column :label="t('ticket.columns.assignee')" width="140">
        <template #default="{ row }: { row: TicketSummary }">
          {{ row.currentAssigneeId ?? t('common.none') }}
        </template>
      </el-table-column>
      <el-table-column :label="t('ticket.columns.createdAt')" width="200">
        <template #default="{ row }: { row: TicketSummary }">{{
          displayDate(row.createdAt)
        }}</template>
      </el-table-column>
      <el-table-column :label="t('ticket.columns.actions')" width="100" fixed="right">
        <template #default="{ row }: { row: TicketSummary }">
          <el-button link type="primary" @click="openDetail(row)">{{
            t('ticket.actions.view')
          }}</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span>{{ t('ticket.empty') }}</span>
      </template>
    </el-table>

    <div class="list-pagination">
      <el-pagination
        v-model:current-page="uiPage"
        v-model:page-size="uiSize"
        :total="totalElements"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
      />
    </div>
  </div>

  <TicketDetailDrawer v-model="drawerOpen" :ticket-id="selectedId" @updated="load" />

  <TicketCreateDialog v-model="createVisible" @created="load" />
</template>

<style scoped>
.filter-select {
  width: 180px;
}

.create-button {
  margin-left: auto;
}

.list-error {
  margin-bottom: 12px;
}
</style>
