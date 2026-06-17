const CACHE_PREFIX = 'xiangqin_cache_'
const CACHE_DURATION = 5 * 60 * 1000

export function getCached<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(CACHE_PREFIX + key)
    if (!raw) return null
    const { data, timestamp } = JSON.parse(raw)
    if (Date.now() - timestamp > CACHE_DURATION) {
      localStorage.removeItem(CACHE_PREFIX + key)
      return null
    }
    return data as T
  } catch { return null }
}

export function setCache(key: string, data: any): void {
  try {
    localStorage.setItem(CACHE_PREFIX + key, JSON.stringify({ data, timestamp: Date.now() }))
  } catch {}
}

export function clearCache(key?: string): void {
  if (key) {
    localStorage.removeItem(CACHE_PREFIX + key)
  } else {
    const keys = Object.keys(localStorage).filter(k => k.startsWith(CACHE_PREFIX))
    keys.forEach(k => localStorage.removeItem(k))
  }
}
