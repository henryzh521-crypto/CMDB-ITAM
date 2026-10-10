import { toCanvas, toDataURL } from 'qrcode'
import { escapeHtml } from '@/utils/escape'

export interface QrcodeItem {
  assetNo: string
  name: string
}

const QR_WIDTH = 220

/** 将文本（资产编号纯文本）渲染到 canvas，移动端扫码即可精确查询资产 */
export async function renderToCanvas(el: HTMLCanvasElement, text: string): Promise<void> {
  await toCanvas(el, text, { width: QR_WIDTH, margin: 2 })
}

/** 生成二维码 PNG Data URL */
export async function toDataUrl(text: string): Promise<string> {
  return toDataURL(text, { width: QR_WIDTH, margin: 2 })
}

/** 下载单枚二维码 PNG */
export async function downloadQrcode(assetNo: string): Promise<void> {
  const url = await toDataUrl(assetNo)
  const a = document.createElement('a')
  a.href = url
  a.download = `资产二维码_${assetNo}.png`
  document.body.appendChild(a)
  a.click()
  a.remove()
}

/** 打开批量打印页：每枚资产一张卡片，A4 四列网格，自动触发打印 */
export async function openPrintPage(items: QrcodeItem[]): Promise<void> {
  if (!items.length) return
  const cards = await Promise.all(
    items.map(async (it) => ({
      assetNo: it.assetNo,
      name: it.name,
      url: await toDataUrl(it.assetNo),
    })),
  )
  const win = window.open('', '_blank')
  if (!win) {
    throw new Error('打印窗口被浏览器拦截，请允许弹窗后重试')
  }
  const cardsHtml = cards
    .map(
      (c) => `<div class="card">
        <img class="qr" src="${c.url}" alt="${escapeHtml(c.assetNo)}" />
        <div class="no">${escapeHtml(c.assetNo)}</div>
        <div class="name">${escapeHtml(c.name)}</div>
      </div>`,
    )
    .join('')
  win.document.write(`<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8" />
<title>资产二维码打印</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif; color: #111; }
  .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6mm; padding: 10mm; max-width: 210mm; margin: 0 auto; }
  .card { text-align: center; border: 1px dashed #bbb; border-radius: 4px; padding: 3mm 2mm 4mm; break-inside: avoid; page-break-inside: avoid; }
  .qr { width: 40mm; height: 40mm; display: block; margin: 0 auto; }
  .no { font-size: 15px; font-weight: 700; margin-top: 2mm; word-break: break-all; }
  .name { font-size: 12px; color: #444; margin-top: 1mm; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  @page { size: A4; margin: 8mm; }
  @media print { .grid { padding: 0; } .card { border-color: #ddd; } }
</style>
</head>
<body>
<div class="grid">${cardsHtml}</div>
</body>
</html>`)
  win.document.close()
  win.focus()
  win.print()
}
