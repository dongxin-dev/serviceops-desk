<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import enUs from 'element-plus/es/locale/lang/en'

import { persistLocale, type AppLocale } from '@/locales'

const { t, locale } = useI18n()
const route = useRoute()

// Element Plus built-in widget text (pagination, empty slots...) follows the
// app locale; switching never re-fetches backend data.
const elLocale = computed(() => (locale.value === 'zh-CN' ? zhCn : enUs))

const pageTitle = computed(() =>
  typeof route.meta.titleKey === 'string' ? t(route.meta.titleKey) : '',
)

const currentLanguageLabel = computed(() =>
  locale.value === 'zh-CN' ? t('language.zh') : t('language.en'),
)

function switchLocale(next: AppLocale) {
  locale.value = next
  persistLocale(next)
}
</script>

<template>
  <el-config-provider :locale="elLocale">
    <el-container class="app-shell">
      <el-aside width="220px" class="app-aside">
        <div class="app-brand">{{ t('app.brand') }}</div>
        <el-menu :default-active="route.path" router class="app-menu">
          <el-menu-item index="/tickets">
            <span>{{ t('nav.tickets') }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header class="app-header" height="56px">
          <div class="app-page-title">{{ pageTitle }}</div>
          <el-dropdown trigger="click" @command="switchLocale($event as AppLocale)">
            <span class="app-lang">{{ currentLanguageLabel }}</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="zh-CN">{{ t('language.zh') }}</el-dropdown-item>
                <el-dropdown-item command="en-US">{{ t('language.en') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </el-header>
        <el-main class="app-main">
          <RouterView />
        </el-main>
      </el-container>
    </el-container>
  </el-config-provider>
</template>
