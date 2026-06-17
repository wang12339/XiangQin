<template>
  <div class="min-h-screen bg-gray-50 pb-20">
    <div class="bg-white px-4 py-3 border-b sticky top-0 z-10">
      <h1 class="text-lg font-bold">位置</h1>
    </div>

    <div class="p-4 space-y-3">
      <div v-if="latest" class="bg-white rounded-xl p-4 shadow-sm">
        <div class="text-sm text-gray-400 mb-1">当前位置</div>
        <div class="font-mono text-sm">{{ latest.latitude.toFixed(6) }}, {{ latest.longitude.toFixed(6) }}</div>
        <div class="text-xs text-gray-400 mt-1">精度 {{ latest.accuracy }}米 · {{ new Date(latest.recordedTime).toLocaleString() }}</div>
      </div>

      <div class="bg-white rounded-xl overflow-hidden shadow-sm">
        <div ref="mapContainer" class="w-full h-[50vh] min-h-[300px] relative"></div>
        <div class="p-3 flex items-center justify-between text-xs text-gray-500 border-t">
          <span>{{ points.length }} 个轨迹点 · 最近 {{ days }} 天</span>
          <div class="flex gap-2">
            <button @click="changeDays(1)" :class="days === 1 ? 'text-blue-500 font-medium' : ''">1天</button>
            <button @click="changeDays(3)" :class="days === 3 ? 'text-blue-500 font-medium' : ''">3天</button>
            <button @click="changeDays(7)" :class="days === 7 ? 'text-blue-500 font-medium' : ''">7天</button>
            <button @click="centerOnLatest" class="text-blue-500">定位</button>
          </div>
        </div>
      </div>

      <div class="bg-white rounded-xl p-4 shadow-sm">
        <div class="font-medium mb-3">轨迹记录 ({{ points.length }})</div>
        <div v-if="!points.length" class="text-gray-400 text-sm text-center py-4">暂无轨迹</div>
        <div v-for="p in points" :key="p.time" class="flex items-center gap-3 py-2 border-b border-gray-50 last:border-0">
          <div class="w-2 h-2 rounded-full bg-blue-500 flex-shrink-0"></div>
          <div class="flex-1 min-w-0">
            <div class="text-sm font-mono">{{ p.lat.toFixed(4) }}, {{ p.lng.toFixed(4) }}</div>
            <div class="text-xs text-gray-400">{{ new Date(p.time).toLocaleString() }}</div>
          </div>
          <span class="text-xs text-gray-400">{{ p.acc }}m</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, onActivated } from 'vue'
import { api } from '../api/client'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const PI = Math.PI
const a = 6378245.0
const ee = 0.00669342162296594323

function transformLat(x: number, y: number): number {
  let ret = -100 + 2*x + 3*y + 0.2*y*y + 0.1*x*y + 0.2*Math.sqrt(Math.abs(x))
  ret += (20*Math.sin(6*x*PI) + 20*Math.sin(2*x*PI)) * 2 / 3
  ret += (20*Math.sin(y*PI) + 40*Math.sin(y/3*PI)) * 2 / 3
  ret += (160*Math.sin(y/12*PI) + 320*Math.sin(y*PI/30)) * 2 / 3
  return ret
}

function transformLng(x: number, y: number): number {
  let ret = 300 + x + 2*y + 0.1*x*x + 0.1*x*y + 0.1*Math.sqrt(Math.abs(x))
  ret += (20*Math.sin(6*x*PI) + 20*Math.sin(2*x*PI)) * 2 / 3
  ret += (20*Math.sin(x*PI) + 40*Math.sin(x/3*PI)) * 2 / 3
  ret += (150*Math.sin(x/12*PI) + 300*Math.sin(x/30*PI)) * 2 / 3
  return ret
}

function outOfChina(lat: number, lng: number): boolean {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function wgs84ToGcj02(lat: number, lng: number): [number, number] {
  if (outOfChina(lat, lng)) return [lat, lng]
  let dLat = transformLat(lng - 105, lat - 35)
  let dLng = transformLng(lng - 105, lat - 35)
  const radLat = lat / 180 * PI
  let magic = Math.sin(radLat)
  magic = 1 - ee * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180) / ((a * (1 - ee)) / (magic * sqrtMagic) * PI)
  dLng = (dLng * 180) / (a / sqrtMagic * Math.cos(radLat) * PI)
  return [lat + dLat, lng + dLng]
}

const latest = ref<any>(null)
const points = ref<any[]>([])
const days = ref(1)
const mapContainer = ref<HTMLElement>()
let map: L.Map | null = null
let markersLayer: L.LayerGroup | null = null
let homeLayer: L.LayerGroup | null = null

function initMap() {
  if (!mapContainer.value || map) return
  map = L.map(mapContainer.value, {
    zoomControl: false,
    attributionControl: false,
    center: [28.55, 121.46],
    zoom: 13
  })

  L.control.zoom({ position: 'topright' }).addTo(map)
  L.control.attribution({ position: 'bottomleft', prefix: '© 高德' }).addTo(map)

  const osmLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19, attribution: '© OpenStreetMap'
  })

  const amapLayer = L.tileLayer('https://webrd{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=7&x={x}&y={y}&z={z}', {
    subdomains: ['01', '02', '03', '04'], maxZoom: 18, attribution: '© 高德地图'
  })

  amapLayer.addTo(map)

  amapLayer.on('tileerror', () => {
    if (map && !map.hasLayer(osmLayer)) {
      map.removeLayer(amapLayer)
      osmLayer.addTo(map)
    }
  })

  markersLayer = L.layerGroup().addTo(map)
  homeLayer = L.layerGroup().addTo(map)
}

function renderTrack() {
  if (!map || !markersLayer) return
  const ml = markersLayer
  ml.clearLayers()
  homeLayer?.clearLayers()

  if (points.value.length === 0) return

  const latlngs: L.LatLngExpression[] = []

  points.value.forEach((p, i) => {
    const isLast = i === points.value.length - 1
    const isFist = i === 0
    const icon = L.divIcon({
      className: '',
      html: isLast
        ? '<div style="width:14px;height:14px;background:#22c55e;border:3px solid white;border-radius:50%;box-shadow:0 1px 4px rgba(0,0,0,0.3);"></div>'
        : isFist
          ? '<div style="width:10px;height:10px;background:#3b82f6;border:2px solid white;border-radius:50%;box-shadow:0 1px 3px rgba(0,0,0,0.2);"></div>'
          : '<div style="width:6px;height:6px;background:#93c5fd;border:1px solid white;border-radius:50%;"></div>',
      iconSize: [0, 0],
      iconAnchor: [isLast ? 7 : isFist ? 5 : 3, isLast ? 7 : isFist ? 5 : 3]
    })

    const marker = L.marker([p.lat, p.lng], { icon })
    const time = new Date(p.time).toLocaleString()
    marker.bindPopup(`<div style="font-size:13px;"><b>${isLast ? '当前位置' : isFist ? '最早位置' : '轨迹点'}</b><br/>${p.lat.toFixed(5)}, ${p.lng.toFixed(5)}<br/>${time}<br/>精度 ${p.acc}m</div>`)
    ml.addLayer(marker)
    latlngs.push([p.lat, p.lng])
  })

  if (latlngs.length > 1) {
    L.polyline(latlngs, { color: '#3b82f6', weight: 3, opacity: 0.7, dashArray: '6,4' }).addTo(ml)
  }

  if (latest.value) {
    map.setView([latest.value.latitude, latest.value.longitude], Math.max(map.getZoom(), 14))
  } else if (latlngs.length > 0) {
    map.fitBounds(L.latLngBounds(latlngs), { padding: [40, 40] })
  }
}

async function loadHomeZone() {
  if (!map || !homeLayer) return
  const hl = homeLayer
  try {
    const res = await api.get('/api/alerts/settings')
    const home = res.data.home
    if (home && map) {
      L.circle([home.latitude, home.longitude], {
        radius: home.radiusMeters,
        color: '#f59e0b',
        fillColor: '#fbbf24',
        fillOpacity: 0.15,
        weight: 2,
        dashArray: '5,5'
      }).addTo(hl).bindPopup(`<b>家的范围</b><br/>半径 ${home.radiusMeters}m${home.address ? '<br/>' + home.address : ''}`)
    }
  } catch {}
}

async function loadData() {
  try {
    const [latRes, locRes] = await Promise.all([
      api.get('/api/locations/latest'),
      api.get(`/api/locations?days=${days.value}`)
    ])
    const loc = latRes.data.location
    if (loc) {
      const [gLat, gLng] = wgs84ToGcj02(loc.latitude, loc.longitude)
      latest.value = { ...loc, latitude: gLat, longitude: gLng }
    } else {
      latest.value = null
    }
    points.value = (locRes.data.points || []).map((p: any) => {
      const [gLat, gLng] = wgs84ToGcj02(p.latitude, p.longitude)
      return { lat: gLat, lng: gLng, acc: p.accuracy, time: p.time }
    })
    await nextTick()
    renderTrack()
  } catch {}
}

function changeDays(d: number) {
  days.value = d
  loadData()
}

function centerOnLatest() {
  if (map && latest.value) {
    map.setView([latest.value.latitude, latest.value.longitude], 16)
  }
}

onMounted(async () => {
  await nextTick()
  initMap()
  await loadData()
  await loadHomeZone()
  setTimeout(() => {
    map?.invalidateSize()
    if (latest.value && map) {
      map.setView([latest.value.latitude, latest.value.longitude], 14)
    }
  }, 500)
})

onActivated(() => {
  setTimeout(() => {
    map?.invalidateSize()
    if (latest.value && map) {
      map.setView([latest.value.latitude, latest.value.longitude], 14)
    }
  }, 300)
})

onUnmounted(() => {
  map?.remove()
  map = null
})
</script>
