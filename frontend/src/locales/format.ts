/**
 * Minimal display formatter: browser-standard Intl only, no date library.
 * Backend sends ISO-8601 offset strings (TIMESTAMPTZ); we render them in
 * the viewer's timezone with a locale-aware pattern.
 */
const FORMATTERS = new Map<string, Intl.DateTimeFormat>()

function formatterFor(locale: string): Intl.DateTimeFormat {
  let fmt = FORMATTERS.get(locale)
  if (!fmt) {
    fmt = new Intl.DateTimeFormat(locale, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
    FORMATTERS.set(locale, fmt)
  }
  return fmt
}

/** null / blank -> placeholder (i18n common.none is passed in by callers). */
export function formatDateTime(value: string | null, locale: string, placeholder: string): string {
  if (!value) return placeholder
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return formatterFor(locale).format(date)
}
