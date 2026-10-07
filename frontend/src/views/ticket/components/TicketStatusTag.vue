<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import type { TicketStatus } from '@/types/ticket'

const props = defineProps<{ status: TicketStatus }>()

const { t } = useI18n()

const TAG_TYPES = {
  OPEN: 'info',
  ASSIGNED: 'primary',
  IN_PROGRESS: 'warning',
  RESOLVED: 'success',
  CLOSED: 'info',
  REOPENED: 'danger',
} as const

const tagType = computed(() => TAG_TYPES[props.status])
</script>

<template>
  <el-tag :type="tagType" effect="light" disable-transitions>{{ t(`status.${status}`) }}</el-tag>
</template>
