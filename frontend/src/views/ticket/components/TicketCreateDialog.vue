<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { createTicket } from '@/api/tickets'
import { isApiError } from '@/api/http'
import type { CreateTicketInput, Ticket, TicketPriority } from '@/types/ticket'

const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ created: [ticket: Ticket] }>()

const { t } = useI18n()

const PRIORITY_OPTIONS: TicketPriority[] = ['P1_CRITICAL', 'P2_HIGH', 'P3_MEDIUM', 'P4_LOW']

const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')

// Local form shape only: `priority` starts empty and requesterId stays a
// raw string while typing (an identifier, not a quantity - no auto
// correction), so they cannot match CreateTicketInput until validation
// passes.
const form = reactive({
  title: '',
  description: '',
  category: '',
  priority: '' as TicketPriority | '',
  requesterId: '',
})

// Rules mirror the backend constraints; the backend stays the final judge.
const rules = computed<FormRules>(() => ({
  title: [
    { required: true, whitespace: true, message: t('create.rules.required'), trigger: 'blur' },
    { max: 200, message: t('create.rules.titleMax'), trigger: 'blur' },
  ],
  description: [
    { required: true, whitespace: true, message: t('create.rules.required'), trigger: 'blur' },
  ],
  category: [
    { required: true, whitespace: true, message: t('create.rules.required'), trigger: 'blur' },
    { max: 64, message: t('create.rules.categoryMax'), trigger: 'blur' },
  ],
  priority: [{ required: true, message: t('create.rules.required'), trigger: 'change' }],
  requesterId: [
    { required: true, message: t('create.rules.required'), trigger: 'blur' },
    {
      // Positive integer only: 0 / -1 / 1.5 / abc fail; 999999 passes the
      // format check and is judged by the backend (404 RequesterNotFound).
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (!value) callback() // emptiness is reported by the required rule
        else if (/^\d+$/.test(value) && Number(value) > 0) callback()
        else callback(new Error(t('create.rules.requesterPositive')))
      },
      trigger: 'blur',
    },
  ],
}))

function resetForm() {
  formRef.value?.resetFields()
  errorMessage.value = ''
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  errorMessage.value = ''
  try {
    const input: CreateTicketInput = {
      title: form.title,
      description: form.description,
      category: form.category,
      priority: form.priority as TicketPriority,
      requesterId: Number(form.requesterId),
    }
    const ticket = await createTicket(input)
    ElMessage.success(t('feedback.created'))
    visible.value = false
    emit('created', ticket)
  } catch (error) {
    errorMessage.value = describeError(error)
    console.warn('ticket create failed', error)
  } finally {
    submitting.value = false
  }
}

function describeError(error: unknown): string {
  if (isApiError(error)) {
    switch (error.code) {
      case 'RequesterNotFound':
        return t('errors.requesterNotFound')
      case 'SlaPolicyNotConfigured':
        return t('errors.slaNotConfigured')
      case 'ValidationError':
      case 'InvalidParameter':
        return t('errors.validationError')
      case 'TicketValidation':
        return t('errors.ticketValidation')
      case 'NetworkError':
        return t('errors.networkError')
      default:
        return t('errors.unknownError')
    }
  }
  return t('errors.unknownError')
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('create.title')"
    width="560px"
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <el-alert
      v-if="errorMessage"
      class="create-error"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="right" label-width="auto">
      <el-form-item :label="t('detail.fields.title')" prop="title">
        <el-input v-model="form.title" />
      </el-form-item>
      <el-form-item :label="t('detail.fields.description')" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="4" />
      </el-form-item>
      <el-form-item :label="t('detail.fields.category')" prop="category">
        <el-input v-model="form.category" />
      </el-form-item>
      <el-form-item :label="t('detail.fields.priority')" prop="priority">
        <el-select v-model="form.priority" class="priority-select">
          <el-option v-for="p in PRIORITY_OPTIONS" :key="p" :value="p" :label="t(`priority.${p}`)" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('detail.fields.requesterId')" prop="requesterId">
        <el-input
          v-model="form.requesterId"
          inputmode="numeric"
          :placeholder="t('create.placeholder.requesterId')"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">{{
        t('common.save')
      }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.create-error {
  margin-bottom: 12px;
}

.priority-select {
  width: 100%;
}
</style>
