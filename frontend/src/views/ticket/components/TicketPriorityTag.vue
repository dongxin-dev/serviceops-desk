<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import type { TicketPriority } from '@/types/ticket'

const props = defineProps<{ priority: TicketPriority }>()

const { t } = useI18n()

const TAG_TYPES = {
  P1_CRITICAL: 'danger',
  P2_HIGH: 'warning',
  P3_MEDIUM: 'primary',
  P4_LOW: 'info',
} as const

const tagType = computed(() => TAG_TYPES[props.priority])
</script>

<template>
  <el-tag :type="tagType" effect="plain" disable-transitions>{{
    t(`priority.${priority}`)
  }}</el-tag>
</template>
