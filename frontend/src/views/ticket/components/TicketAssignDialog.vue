<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { assignTicket } from '@/api/tickets'
import { isApiError } from '@/api/http'
import type { AssignTicketInput, Ticket } from '@/types/ticket'

const props = defineProps<{ ticket: Ticket }>()
const visible = defineModel<boolean>({ required: true })
// assigned: full ticket from the 200 response; reload: server conflict
// already explained, the drawer must re-GET the latest state.
const emit = defineEmits<{ assigned: [ticket: Ticket]; reload: [] }>()

const { t } = useI18n()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const errorMessage = ref('')

// Identifier inputs (not quantities): raw strings, ElInput only, no
// steppers and no auto-correction - same pattern as the F2 requester ID.
const form = reactive({ assigneeId: '', actorId: '' })

// OPEN assigns, REOPENED reassigns; both hit the SAME /assign endpoint.
// The ASSIGN vs REASSIGN decision belongs to the backend domain: no
// assignmentType ever leaves the client.
const dialogTitle = computed(() =>
  props.ticket.status === 'REOPENED' ? t('assign.title.reassign') : t('assign.title.assign'),
)

function positiveIntRule() {
  return {
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      if (!value) callback() // emptiness is reported by the required rule
      else if (/^\d+$/.test(value) && Number(value) > 0) callback()
      else callback(new Error(t('assign.rules.positive')))
    },
    trigger: 'blur',
  }
}

const rules = computed<FormRules>(() => ({
  assigneeId: [
    { required: true, message: t('create.rules.required'), trigger: 'blur' },
    positiveIntRule(),
  ],
  actorId: [
    { required: true, message: t('create.rules.required'), trigger: 'blur' },
    positiveIntRule(),
  ],
}))

watch(visible, (open) => {
  if (open) {
    form.assigneeId = ''
    form.actorId = ''
    errorMessage.value = ''
    formRef.value?.clearValidate()
  }
})

async function submit() {
  if (!formRef.value || submitting.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  errorMessage.value = ''
  // version is carried from the current ticket snapshot, never user input.
  const input: AssignTicketInput = {
    assigneeId: Number(form.assigneeId),
    actorId: Number(form.actorId),
    version: props.ticket.version,
  }
  try {
    const updated = await assignTicket(props.ticket.id, input)
    ElMessage.success(t('feedback.assigned'))
    visible.value = false
    emit('assigned', updated)
  } catch (error) {
    if (
      isApiError(error) &&
      (error.code === 'TicketStaleRevision' || error.code === 'IllegalTicketStateTransition')
    ) {
      // Same conflict UX as F2 edit: warn, close, let the drawer reload.
      ElMessage.warning(
        error.code === 'TicketStaleRevision'
          ? t('errors.staleRevision')
          : t('errors.illegalStateTransition'),
      )
      visible.value = false
      emit('reload')
    } else {
      errorMessage.value = describeError(error)
      console.warn('ticket assign failed', error)
    }
  } finally {
    submitting.value = false
  }
}

function describeError(error: unknown): string {
  if (isApiError(error)) {
    switch (error.code) {
      case 'AssigneeNotFound':
        return t('errors.assigneeNotFound')
      case 'ActorNotFound':
        return t('errors.actorNotFound')
      case 'TicketNotFound':
        return t('errors.ticketNotFound')
      case 'ValidationError':
      case 'InvalidParameter':
      case 'MalformedRequest':
        return t('errors.validationError')
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
  <el-dialog v-model="visible" :title="dialogTitle" width="480px" :close-on-click-modal="false">
    <el-alert
      v-if="errorMessage"
      class="assign-error"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="right" label-width="auto">
      <el-form-item :label="t('assign.fields.assigneeId')" prop="assigneeId">
        <el-input
          v-model="form.assigneeId"
          inputmode="numeric"
          :placeholder="t('assign.placeholder.assigneeId')"
        />
      </el-form-item>
      <el-form-item :label="t('assign.fields.actorId')" prop="actorId">
        <el-input
          v-model="form.actorId"
          inputmode="numeric"
          :placeholder="t('assign.placeholder.actorId')"
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
.assign-error {
  margin-bottom: 12px;
}
</style>
