<script setup lang="ts">
/**
 * 机房 3D 平面图（three.js，按路由懒加载）：
 * 浅色场景 + PBR/阴影写实渲染；机柜盒体按行列摆位 + 容量热力着色，
 * 前门/提手/底座/悬空标牌细节，通道地面与冷通道划线，
 * OrbitControls + raycast 拾取，搜索淡出、悬停/选中高亮、按距离淡入的柜号标牌。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { FloorRack, RoomItem } from '@/api/room'
import type { FloorLayout } from '../floor-utils'
import { rackHeatColor, shortRackCode, type HeatMode } from '../floor-utils'

const props = defineProps<{
  layout: FloorLayout
  room?: RoomItem | null
  heatMode: HeatMode
  search?: string
  selectedRackCode?: string
}>()

const emit = defineEmits<{ select: [rack: FloorRack] }>()

const hostRef = ref<HTMLDivElement>()
const tooltip = ref<{ show: boolean; x: number; y: number; text: string }>({
  show: false,
  x: 0,
  y: 0,
  text: '',
})
const loadError = ref('')

/* 尺寸（米级近似）：柜 0.62 宽 × 1.2 深 × 2.2 高 + 0.12 底座；排间通道 2.6 */
const RACK_W = 0.62
const RACK_D = 1.2
const RACK_H = 2.2
const PLINTH_H = 0.12
const GAP_X = 0.18
const AISLE_Z = 2.6
const ROW_PITCH = RACK_D + AISLE_Z

const BG = 0xeef2f6
const EDGE = 0x64748b
const SEL = 0x2563eb
const STEEL = '#4b5563'
const DARK = '#2f3742'

/** 标牌淡入区间：相机距离 26→14 之间由近及远淡入 */
const LABEL_FAR = 26
const LABEL_NEAR = 14

interface SceneCtx {
  THREE: typeof import('three')
  renderer: import('three').WebGLRenderer
  scene: import('three').Scene
  camera: import('three').PerspectiveCamera
  controls: { update: () => void; dispose: () => void; target: import('three').Vector3 }
  key: import('three').DirectionalLight
  envTex?: import('three').Texture
  raycaster: import('three').Raycaster
  pointer: import('three').Vector2
  rackGroup: import('three').Group
  meshes: import('three').Object3D[]
  sprites: import('three').Sprite[]
  raf: number
  ro?: ResizeObserver
  disposed: boolean
}

let ctx: SceneCtx | null = null
let hoverCode = ''

/* ---------------- 资源回收 ---------------- */

function disposeMaterial(m: import('three').Material | undefined) {
  if (!m) return
  const anyM = m as import('three').Material & { map?: { dispose: () => void } }
  anyM.map?.dispose()
  m.dispose()
}

function disposeGroup(sceneCtx: SceneCtx) {
  const { rackGroup } = sceneCtx
  rackGroup.traverse((obj) => {
    // Sprite 复用内置共享几何，不能随组件释放
    const mesh = obj as import('three').Mesh & { isSprite?: boolean }
    if (mesh.geometry && !mesh.isSprite) mesh.geometry.dispose()
    const m = mesh.material as import('three').Material | import('three').Material[] | undefined
    if (Array.isArray(m)) m.forEach(disposeMaterial)
    else if (m) disposeMaterial(m)
  })
  rackGroup.clear()
  sceneCtx.meshes = []
  sceneCtx.sprites = []
}

/* ---------------- 场景重建 ---------------- */

function rowSpan(): { spanX: number; spanZ: number; cx: number; cz: number; rows: number } {
  const rows = props.layout.rowLabels.length
  const spanX = Math.max(props.layout.maxCol, 1) * (RACK_W + GAP_X)
  const spanZ = rows ? (rows - 1) * ROW_PITCH + RACK_D : RACK_D
  return { spanX, spanZ, cx: 0, cz: spanZ / 2, rows }
}

function makeLabelTexture(THREE: typeof import('three'), code: string, sub: string) {
  const cv = document.createElement('canvas')
  cv.width = 320
  cv.height = 120
  const g = cv.getContext('2d')
  if (g) {
    const rr = g as CanvasRenderingContext2D & {
      roundRect?: (x: number, y: number, w: number, h: number, r: number) => void
    }
    g.fillStyle = 'rgba(255,255,255,0.9)'
    g.strokeStyle = 'rgba(100,116,139,0.55)'
    g.lineWidth = 3
    if (rr.roundRect) {
      g.beginPath()
      rr.roundRect(2, 2, 316, 116, 14)
      g.fill()
      g.stroke()
    } else {
      g.fillRect(2, 2, 316, 116)
      g.strokeRect(2, 2, 316, 116)
    }
    g.textAlign = 'center'
    g.fillStyle = '#1f2937'
    g.font = 'bold 46px "Segoe UI", system-ui, -apple-system, sans-serif'
    g.fillText(code, 160, 56)
    g.fillStyle = '#4b5569'
    g.font = '34px "Segoe UI", system-ui, -apple-system, sans-serif'
    g.fillText(sub, 160, 100)
  }
  const tex = new THREE.CanvasTexture(cv)
  tex.colorSpace = THREE.SRGBColorSpace
  tex.anisotropy = 4
  return tex
}

/** 依据悬停/选中状态重刷单柜材质（材质按柜独立，可安全改 emissive） */
function paintRack(code: string) {
  const c = ctx
  if (!c) return
  const mesh = c.meshes.find((m) => m.userData.rackCode === code)
  if (!mesh) return
  const u = mesh.userData
  const selected = code === props.selectedRackCode
  const hover = hoverCode === code
  const heat = String(u.base || '#67c23a')

  const bm = u.bodyMat as import('three').MeshStandardMaterial
  bm.emissive.set(selected ? SEL : heat)
  bm.emissiveIntensity = selected ? 0.5 : hover ? 0.34 : 0.14

  const dm = u.doorMat as import('three').MeshStandardMaterial
  dm.emissive.set(selected ? SEL : hover ? 0xffffff : 0x000000)
  dm.emissiveIntensity = selected ? 0.4 : hover ? 0.12 : 0

  const em = u.edges.material as import('three').LineBasicMaterial
  em.color.set(selected ? SEL : hover ? 0x1f2937 : EDGE)
  em.opacity = u.dim ? 0.18 : selected || hover ? 0.95 : 0.75
}

function setHover(code: string) {
  if (hoverCode === code) return
  const prev = hoverCode
  hoverCode = code
  if (prev) paintRack(prev)
  if (code) paintRack(code)
}

function rebuild() {
  const c = ctx
  if (!c) return
  const { THREE, rackGroup } = c
  disposeGroup(c)
  hoverCode = ''

  const dimmedSet = new Set<string>()
  const kw = (props.search || '').trim().toLowerCase()
  if (kw) {
    for (const cell of props.layout.cells) {
      const r = cell.rack
      const hit =
        r.rackCode.toLowerCase().includes(kw) || (r.locationName || '').toLowerCase().includes(kw)
      if (!hit) dimmedSet.add(r.rackCode)
    }
  }

  const { spanX, spanZ, cx, cz } = rowSpan()
  const geo = new THREE.BoxGeometry(RACK_W, RACK_H, RACK_D)
  const doorGeo = new THREE.BoxGeometry(RACK_W - 0.07, RACK_H - 0.2, 0.03)
  const plinthGeo = new THREE.BoxGeometry(RACK_W - 0.04, PLINTH_H, RACK_D - 0.04)
  const handleGeo = new THREE.BoxGeometry(0.03, 0.3, 0.03)
  const edgeGeo = new THREE.EdgesGeometry(geo)
  const steel = new THREE.Color(STEEL)
  const dark = new THREE.Color(DARK)

  for (const cell of props.layout.cells) {
    const rack = cell.rack
    const heat = rackHeatColor(rack, props.heatMode)
    const dim = dimmedSet.has(rack.rackCode)
    const selected = rack.rackCode === props.selectedRackCode
    const opacity = dim ? 0.16 : 1

    const holder = new THREE.Group()
    holder.position.set(
      (cell.gridCol - (props.layout.maxCol - 1) / 2) * (RACK_W + GAP_X),
      0,
      cell.gridRow * ROW_PITCH,
    )

    const bodyMat = new THREE.MeshStandardMaterial({
      color: new THREE.Color(heat),
      roughness: 0.6,
      metalness: 0.12,
      emissive: new THREE.Color(selected ? SEL : heat),
      emissiveIntensity: selected ? 0.5 : 0.14,
      transparent: dim,
      opacity,
      depthWrite: !dim,
    })
    const body = new THREE.Mesh(geo, bodyMat)
    body.position.y = PLINTH_H + RACK_H / 2
    body.castShadow = !dim
    body.receiveShadow = !dim
    body.userData = {
      rackCode: rack.rackCode,
      base: heat,
      dim,
      bodyMat,
      doorMat: null,
      edges: null,
    }

    const doorMat = new THREE.MeshStandardMaterial({
      color: steel.clone().lerp(new THREE.Color(heat), 0.12),
      roughness: 0.5,
      metalness: 0.4,
      emissive: new THREE.Color(selected ? SEL : 0x000000),
      emissiveIntensity: selected ? 0.4 : 0,
      transparent: dim,
      opacity,
      depthWrite: !dim,
    })
    const door = new THREE.Mesh(doorGeo, doorMat)
    door.position.set(0, PLINTH_H + RACK_H / 2, RACK_D / 2 - 0.005)
    door.castShadow = !dim
    door.receiveShadow = !dim
    body.userData.doorMat = doorMat

    const darkMat = new THREE.MeshStandardMaterial({
      color: dark,
      roughness: 0.6,
      metalness: 0.55,
      transparent: dim,
      opacity,
      depthWrite: !dim,
    })
    const plinth = new THREE.Mesh(plinthGeo, darkMat)
    plinth.position.y = PLINTH_H / 2
    plinth.castShadow = !dim
    plinth.receiveShadow = !dim

    const handles: import('three').Mesh[] = []
    for (const hx of [-0.17, 0.17]) {
      const h = new THREE.Mesh(handleGeo, darkMat)
      h.position.set(hx, PLINTH_H + RACK_H * 0.6, RACK_D / 2 + 0.035)
      h.castShadow = !dim
      handles.push(h)
    }

    const edges = new THREE.LineSegments(
      edgeGeo,
      new THREE.LineBasicMaterial({
        color: selected ? SEL : EDGE,
        transparent: true,
        opacity: dim ? 0.18 : selected ? 0.95 : 0.75,
      }),
    )
    edges.position.copy(body.position)
    body.userData.edges = edges

    const spr = new THREE.Sprite(
      new THREE.SpriteMaterial({
        map: makeLabelTexture(
          THREE,
          shortRackCode(rack.rackCode),
          `${rack.usedUnits}/${rack.uCapacity}U`,
        ),
        transparent: true,
        depthWrite: false,
        opacity: dim ? 0.1 : 1,
      }),
    )
    spr.scale.set(0.95, 0.356, 1)
    spr.position.set(0, PLINTH_H + RACK_H + 0.4, 0)
    spr.userData.dim = dim

    holder.userData.rackCode = rack.rackCode
    holder.add(body, door, plinth, ...handles, edges, spr)
    rackGroup.add(holder)
    c.meshes.push(body)
    c.sprites.push(spr)
  }

  // 机房地面（浅色地砖）+ 外轮廓
  if (props.layout.cells.length) {
    const padGeo = new THREE.PlaneGeometry(spanX + 1.8, spanZ + 1.4)
    const pad = new THREE.Mesh(
      padGeo,
      new THREE.MeshStandardMaterial({ color: 0xe7ecf3, roughness: 0.95, metalness: 0 }),
    )
    pad.rotation.x = -Math.PI / 2
    pad.position.set(cx, 0.003, cz)
    pad.receiveShadow = true
    rackGroup.add(pad)

    const outPts = [
      new THREE.Vector3(-spanX / 2 - 0.9, 0.012, cz - spanZ / 2 - 0.7),
      new THREE.Vector3(spanX / 2 + 0.9, 0.012, cz - spanZ / 2 - 0.7),
      new THREE.Vector3(spanX / 2 + 0.9, 0.012, cz + spanZ / 2 + 0.7),
      new THREE.Vector3(-spanX / 2 - 0.9, 0.012, cz + spanZ / 2 + 0.7),
    ]
    const outGeo = new THREE.BufferGeometry().setFromPoints([...outPts, outPts[0]])
    rackGroup.add(
      new THREE.Line(
        outGeo,
        new THREE.LineBasicMaterial({ color: 0x9aa5b1, transparent: true, opacity: 0.9 }),
      ),
    )

    // 冷通道：浅色步道 + 两侧黄色虚线（安全通道划线）
    const rows = props.layout.rowLabels.length
    for (let r = 0; r < rows - 1; r++) {
      const zc = r * ROW_PITCH + RACK_D + AISLE_Z / 2
      const walkGeo = new THREE.PlaneGeometry(spanX - 0.5, AISLE_Z - 0.6)
      const walk = new THREE.Mesh(
        walkGeo,
        new THREE.MeshStandardMaterial({ color: 0xeef2f7, roughness: 0.9, metalness: 0 }),
      )
      walk.rotation.x = -Math.PI / 2
      walk.position.set(cx, 0.006, zc)
      walk.receiveShadow = true
      rackGroup.add(walk)

      for (const off of [-1, 1]) {
        const tz = zc + off * (AISLE_Z / 2 - 0.42)
        const x0 = cx - spanX / 2 + 0.3
        const x1 = cx + spanX / 2 - 0.3
        const tapeGeo = new THREE.BufferGeometry().setFromPoints([
          new THREE.Vector3(x0, 0.009, tz),
          new THREE.Vector3(x1, 0.009, tz),
        ])
        const tape = new THREE.Line(
          tapeGeo,
          new THREE.LineDashedMaterial({
            color: 0xe6a23c,
            dashSize: 0.35,
            gapSize: 0.25,
            transparent: true,
            opacity: 0.85,
          }),
        )
        tape.computeLineDistances()
        rackGroup.add(tape)
      }
    }
  }

  fitCamera()
}

function fitCamera() {
  const c = ctx
  if (!c) return
  const { THREE } = c
  const { spanX, spanZ, cx, cz } = rowSpan()
  const has = props.layout.cells.length > 0

  if (has) {
    const dist = Math.max(spanX, spanZ, 4) * 1.15 + 4
    c.controls.target.set(cx, 0.9, cz)
    c.camera.position.set(cx + dist * 0.45, dist * 0.75, cz + dist * 0.85)
    c.camera.lookAt(cx, 0.9, cz)
  } else {
    c.controls.target.set(cx, 0.8, cz)
    c.camera.position.set(cx + 5, 7, cz + 9)
    c.camera.lookAt(cx, 0.8, cz)
  }

  // 主光（投影）随房间尺寸裁剪阴影相机，避免小房间阴影精度浪费/大房间漏影
  const s = Math.max(spanX, spanZ) * 0.8 + 3
  const sc = c.key.shadow.camera
  sc.left = -s
  sc.right = s
  sc.top = s
  sc.bottom = -s
  sc.near = 1
  sc.far = s * 4 + 24
  sc.updateProjectionMatrix()
  const dir = new THREE.Vector3(0.5, 1, 0.4).normalize()
  c.key.position.set(cx, 0, cz).addScaledVector(dir, s * 2 + 6)
  c.key.target.position.set(cx, 0, cz)
  c.key.target.updateMatrixWorld()
}

/* ---------------- 初始化 ---------------- */

async function initScene() {
  const host = hostRef.value
  if (!host) return
  loadError.value = ''
  try {
    const THREE = await import('three')
    const { OrbitControls } = await import('three/examples/jsm/controls/OrbitControls.js')

    const renderer = new THREE.WebGLRenderer({ antialias: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.setSize(host.clientWidth || 600, host.clientHeight || 400)
    renderer.outputColorSpace = THREE.SRGBColorSpace
    // Neutral：保持热力色相（ACES 会把绿/黄压成橄榄色，热力图要的是可读性）
    renderer.toneMapping = THREE.NeutralToneMapping
    renderer.toneMappingExposure = 1
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFSoftShadowMap
    renderer.setClearColor(BG, 1)
    host.appendChild(renderer.domElement)

    const scene = new THREE.Scene()
    scene.background = new THREE.Color(BG)
    scene.fog = new THREE.Fog(BG, 70, 190)

    // 环境贴图：金属（门/底座/提手）靠反射成像，无环境时金属面会发黑
    const { RoomEnvironment } = await import('three/examples/jsm/environments/RoomEnvironment.js')
    const pmrem = new THREE.PMREMGenerator(renderer)
    const envTex = pmrem.fromScene(new RoomEnvironment(), 0.04).texture
    scene.environment = envTex
    pmrem.dispose()

    const camera = new THREE.PerspectiveCamera(
      48,
      (host.clientWidth || 600) / (host.clientHeight || 400),
      0.1,
      500,
    )
    camera.position.set(6, 8, 12)

    scene.add(new THREE.AmbientLight(0xffffff, 0.3))
    scene.add(new THREE.HemisphereLight(0xffffff, 0xcdd5de, 0.45))

    const key = new THREE.DirectionalLight(0xffffff, 1.65)
    key.castShadow = true
    key.shadow.mapSize.set(2048, 2048)
    key.shadow.bias = -0.0008
    key.shadow.normalBias = 0.03
    scene.add(key)
    scene.add(key.target)

    const fill = new THREE.DirectionalLight(0xffffff, 0.45)
    fill.position.set(-9, 8, -7)
    scene.add(fill)

    // 地面（接受机柜投影）+ 浅色网格
    const floorMat = new THREE.MeshStandardMaterial({ color: 0xdfe4ea, roughness: 0.95, metalness: 0 })
    const floor = new THREE.Mesh(new THREE.PlaneGeometry(240, 240), floorMat)
    floor.rotation.x = -Math.PI / 2
    floor.position.y = -0.012
    floor.receiveShadow = true
    scene.add(floor)

    const grid = new THREE.GridHelper(240, 120, 0xc3cbd4, 0xd7dde4)
    const gm = grid.material as import('three').Material
    gm.transparent = true
    gm.opacity = 0.6
    scene.add(grid)

    const rackGroup = new THREE.Group()
    scene.add(rackGroup)

    const controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.08
    controls.maxPolarAngle = Math.PI / 2.05
    controls.minDistance = 3
    controls.maxDistance = 90

    ctx = {
      THREE,
      renderer,
      scene,
      camera,
      controls,
      key,
      envTex,
      raycaster: new THREE.Raycaster(),
      pointer: new THREE.Vector2(),
      rackGroup,
      meshes: [],
      sprites: [],
      raf: 0,
      disposed: false,
    }

    rebuild()

    const tick = () => {
      if (!ctx || ctx.disposed) return
      ctx.controls.update()
      // 标牌按相机距离淡入：拉近才看清柜号，拉远不糊成一片
      const d = ctx.camera.position.distanceTo(ctx.controls.target)
      const k = Math.max(0, Math.min(1, (LABEL_FAR - d) / (LABEL_FAR - LABEL_NEAR)))
      for (const s of ctx.sprites) {
        const m = s.material as import('three').SpriteMaterial
        m.opacity = (s.userData.dim ? 0.12 : 1) * (0.08 + 0.92 * k)
      }
      ctx.renderer.render(ctx.scene, ctx.camera)
      ctx.raf = requestAnimationFrame(tick)
    }
    tick()

    const ro = new ResizeObserver(() => {
      if (!ctx || !hostRef.value) return
      const w = hostRef.value.clientWidth
      const h = hostRef.value.clientHeight
      ctx.renderer.setSize(w, h)
      ctx.camera.aspect = w / (h || 1)
      ctx.camera.updateProjectionMatrix()
    })
    ro.observe(host)
    ctx.ro = ro

    renderer.domElement.addEventListener('pointermove', onPointerMove)
    renderer.domElement.addEventListener('pointerleave', onPointerLeave)
    renderer.domElement.addEventListener('click', onClick)
  } catch (e) {
    loadError.value = '3D 组件加载失败，请刷新重试'
    console.error(e)
  }
}

/* ---------------- 拾取 ---------------- */

function rackCodeOf(obj: import('three').Object3D | null): string {
  let o: import('three').Object3D | null = obj
  while (o) {
    if (o.userData?.rackCode) return String(o.userData.rackCode)
    o = o.parent
  }
  return ''
}

function pickRack(e: MouseEvent): { code: string; x: number; y: number } | null {
  const c = ctx
  if (!c) return null
  const el = c.renderer.domElement
  const rect = el.getBoundingClientRect()
  c.pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
  c.pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
  c.raycaster.setFromCamera(c.pointer, c.camera)
  const hits = c.raycaster.intersectObjects(c.rackGroup.children, true)
  for (const h of hits) {
    const code = rackCodeOf(h.object)
    if (code) {
      return { code, x: e.clientX - rect.left, y: e.clientY - rect.top }
    }
  }
  return null
}

function onPointerMove(e: PointerEvent) {
  const pick = pickRack(e)
  if (!pick) {
    tooltip.value.show = false
    setHover('')
    if (ctx) ctx.renderer.domElement.style.cursor = 'grab'
    return
  }
  if (ctx) ctx.renderer.domElement.style.cursor = 'pointer'
  setHover(pick.code)
  const rack = props.layout.cells.find((c) => c.rack.rackCode === pick.code)?.rack
  if (!rack) return
  tooltip.value = {
    show: true,
    x: pick.x + 14,
    y: pick.y + 10,
    text: `${rack.rackCode} · ${rack.usedUnits}/${rack.uCapacity}U（${Math.round((rack.usageRate || 0) * 100)}%）`,
  }
}

function onPointerLeave() {
  tooltip.value.show = false
  setHover('')
  if (ctx) ctx.renderer.domElement.style.cursor = 'grab'
}

function onClick(e: MouseEvent) {
  const pick = pickRack(e)
  if (!pick) return
  const rack = props.layout.cells.find((c) => c.rack.rackCode === pick.code)?.rack
  if (rack) emit('select', rack)
}

watch(
  // 按引用比对即可：layout 来自父级 computed(() => buildFloorLayout(...))，
  // 数据刷新时整对象换新引用；heatMode/search/selectedRackCode 是原始值。
  // 刻意不要 deep —— deep 会每次触发都遍历整张 cells×racks 图去挂嵌套依赖，
  // 机房越大开销越大，而本组件的重建只需要知道「layout 换没换」。
  [() => props.layout, () => props.heatMode, () => props.search, () => props.selectedRackCode],
  () => rebuild(),
)

onMounted(() => {
  void initScene()
})

onBeforeUnmount(() => {
  const c = ctx
  if (!c) return
  c.disposed = true
  cancelAnimationFrame(c.raf)
  c.ro?.disconnect()
  const el = c.renderer.domElement
  el.removeEventListener('pointermove', onPointerMove)
  el.removeEventListener('pointerleave', onPointerLeave)
  el.removeEventListener('click', onClick)
  disposeGroup(c)
  c.controls.dispose()
  c.scene.traverse((obj) => {
    const mesh = obj as import('three').Mesh & { isSprite?: boolean }
    if (mesh.geometry && !mesh.isSprite) mesh.geometry.dispose()
    const m = mesh.material as import('three').Material | import('three').Material[] | undefined
    if (Array.isArray(m)) m.forEach(disposeMaterial)
    else if (m) disposeMaterial(m)
  })
  c.renderer.dispose()
  c.envTex?.dispose()
  el.remove()
  ctx = null
})
</script>

<template>
  <div class="floor3d">
    <div ref="hostRef" class="floor3d-host"></div>
    <div v-if="loadError" class="floor3d-err">{{ loadError }}</div>
    <div
      v-if="tooltip.show"
      class="floor3d-tip"
      :style="{ left: tooltip.x + 'px', top: tooltip.y + 'px' }"
    >
      {{ tooltip.text }}
    </div>
    <div class="floor3d-hint">左键旋转 · 右键平移 · 滚轮缩放 · 点击机柜查看详情</div>
    <!-- 占位/加载态 -->
    <div v-if="!loadError && !layout.cells.length" class="floor3d-empty">该机房暂无机柜</div>
  </div>
</template>

<style scoped>
.floor3d {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 420px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
  background: #eef2f6;
}

.floor3d-host {
  width: 100%;
  height: 100%;
}

.floor3d-host :deep(canvas) {
  display: block;
}

.floor3d-tip {
  position: absolute;
  z-index: 3;
  pointer-events: none;
  background: rgba(255, 255, 255, 0.96);
  color: var(--el-text-color-primary);
  font-size: 12px;
  padding: 5px 9px;
  border-radius: 4px;
  white-space: nowrap;
  border: 1px solid #d5dbe3;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.14);
}

.floor3d-hint {
  position: absolute;
  left: 10px;
  bottom: 8px;
  font-size: 11px;
  color: #64748b;
  pointer-events: none;
}

.floor3d-err,
.floor3d-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  background: rgba(238, 242, 246, 0.86);
}

.floor3d-err {
  color: #dc2626;
}

.floor3d-empty {
  color: #94a3b8;
}
</style>
