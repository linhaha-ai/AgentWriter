/** 统一前端时间展示格式，避免受浏览器区域设置影响。 */
export function formatDateTime(value: string | Date | null | undefined): string {
  if (!value) return '-'

  const date = value instanceof Date
    ? value
    : new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value)
  if (Number.isNaN(date.getTime())) return '-'

  const pad = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
