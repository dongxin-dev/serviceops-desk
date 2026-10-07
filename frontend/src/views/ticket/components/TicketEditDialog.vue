<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { updateTicket } from '@/api/tickets'
import { isApiError } from '@/api/http'
import type { Ticket, UpdateTicketInput } from '@/types/ticket'

const props = defineProps<{ ticket: Ticket }>()
const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ updated: [ticket: Ticket]; stale: [] }>()

const { t } = useI18n()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({ title: '', description: '', category: '' })
// Snapshot taken when the dialog opens: the PATCH body carries ONLY the
// fields that differ from it, plus the optimistic-lock version.
const snapshot = reactive({ title: '', description: '', category: '', version: 0 })

function startEdit() {
  snapshot.title = props.ticket.title
  snapshot.description = props.ticket.description
  snapshot.category = props.ticket.category
  snapshot.version = props.ticket.version
  form.title = props.ticket.title
  form.description = props.ticket.description
  form.category = props.ticket.category
  errorMessage.value = ''
  formRef.value?.clearValidate()
}

watch(visible, (open) => {
  if (open) startEdit()
})

// Save stays disabled while nothing differs from the snapshot: an empty
// PATCH is a guaranteed 400, and we must not call the backend for it.
const dirty = computed(
  () =>
    form.title !== snapshot.title ||
    form.description !== snapshot.description ||
    form.category !== snapshot.category,
)

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
}))

async function submit() {
  // Zero-diff edits must never reach the backend (empty PATCH is a 400).
  if (!dirty.value || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const body: UpdateTicketInput = { version: snapshot.version }
  if (form.title !== snapshot.title) body.title = form.title
  if (form.description !== snapshot.description) body.description = form.description
  if (form.category !== snapshot.category) body.category = form.category

  submitting.value = true
  errorMessage.value = ''
  try {
    const updated = await updateTicket(props.ticket.id, body)
    ElMessage.success(t('feedback.updated'))
    visible.value = false
    emit('updated', updated)
  } catch (error) {
    if (isApiError(error) && error.code === 'TicketStaleRevision') {
      // Never auto-retry or overwrite: warn, close, and ask the drawer to
      // re-GET the server's latest state.
      ElMessage.warning(t('errors.staleRevision'))
      visible.value = false
      emit('stale')
    } else {
      errorMessage.value = describeError(error)
      console.warn('ticket edit failed', error)
    }
  } finally {
    submitting.value = false
  }
}

function describeError(error: unknown): string {
  if (isApiError(error)) {
    switch (error.code) {
      case 'TicketNotFound':
        return t('errors.ticketNotFound')
      case 'ValidationError':
      case 'InvalidParameter':
        return t('errors.validationError')
      case 'TicketValidation':
        return t('errors.ticketValidation')
      case 'MalformedRequest':
      case 'InvalidPatchRequest':
        return t('errors.invalidPatch')
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
    :title="t('edit.title')"
    width="560px"
    :close-on-click-modal="false"
  >
    <el-alert
      v-if="errorMessage"
      class="edit-error"
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
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :disabled="!dirty" :loading="submitting" @click="submit">{{
        t('common.save')
      }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.edit-error {
  margin-bottom: 12px;
}
</style>
