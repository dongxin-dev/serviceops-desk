import { createI18n } from 'vue-i18n'

import enUS from './en-US'
import zhCN from './zh-CN'

export type AppLocale = 'zh-CN' | 'en-US'

export const LOCALE_STORAGE_KEY = 'serviceops.locale'

export const AVAILABLE_LOCALES: AppLocale[] = ['zh-CN', 'en-US']

function storedLocale(): AppLocale {
  const raw = localStorage.getItem(LOCALE_STORAGE_KEY)
  return raw === 'en-US' ? 'en-US' : 'zh-CN'
}

/**
 * Composition-API mode only (legacy: false). Default is zh-CN; the choice
 * survives reloads via localStorage and never triggers backend re-fetches.
 */
export const i18n = createI18n({
  legacy: false,
  locale: storedLocale(),
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export function persistLocale(locale: AppLocale): void {
  localStorage.setItem(LOCALE_STORAGE_KEY, locale)
}
