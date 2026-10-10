/**
 * 受限 Markdown 渲染（知识库正文）：先整体 HTML 转义，再识别有限语法生成标签，
 * 因此正文中的任何原始 HTML / 脚本都只会作为文本显示，杜绝存储型 XSS。
 *
 * 支持：# 标题（1-4 级）、段落、无序/有序列表、> 引用、``` 代码块、`行内代码`、**粗体**、*斜体*、
 * [链接](https://…)（仅 http/https/站内相对路径）、--- 分隔线、| 表格 |。
 */

function esc(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

function safeUrl(u: string): string | null {
  const url = u.trim()
  if (/^https?:\/\//i.test(url) || (url.startsWith('/') && !url.startsWith('//'))) return url
  return null
}

/** 行内语法（输入已转义） */
function inline(s: string): string {
  // 行内代码先占位，避免其中内容再被解析
  const codes: string[] = []
  let out = s.replace(/`([^`]+)`/g, (_, c: string) => {
    codes.push(`<code>${c}</code>`)
    return `\u0000${codes.length - 1}\u0000`
  })
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  out = out.replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>')
  out = out.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (m, text: string, href: string) => {
    // href 已被转义（&amp; 等），还原后校验协议再重新转义
    const raw = href.replace(/&amp;/g, '&')
    const u = safeUrl(raw)
    if (!u) return text
    const ext = /^https?:/i.test(u)
    return `<a href="${esc(u)}"${ext ? ' target="_blank" rel="noopener noreferrer"' : ''}>${text}</a>`
  })
  // eslint-disable-next-line no-control-regex
  return out.replace(/\u0000(\d+)\u0000/g, (_, i: string) => codes[Number(i)])
}

export function renderMarkdown(src: string | null | undefined): string {
  const lines = esc(src || '').replace(/\r\n?/g, '\n').split('\n')
  const html: string[] = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    // 代码块
    if (/^```/.test(line)) {
      const buf: string[] = []
      i++
      while (i < lines.length && !/^```/.test(lines[i])) buf.push(lines[i++])
      i++
      html.push(`<pre><code>${buf.join('\n')}</code></pre>`)
      continue
    }
    if (/^\s*$/.test(line)) {
      i++
      continue
    }
    const h = /^(#{1,4})\s+(.*)$/.exec(line)
    if (h) {
      const lv = h[1].length + 1 // # → h2，避免与页面标题冲突
      html.push(`<h${lv}>${inline(h[2])}</h${lv}>`)
      i++
      continue
    }
    if (/^(-{3,}|\*{3,})\s*$/.test(line)) {
      html.push('<hr>')
      i++
      continue
    }
    if (/^&gt;\s?/.test(line)) {
      const buf: string[] = []
      while (i < lines.length && /^&gt;\s?/.test(lines[i])) buf.push(lines[i++].replace(/^&gt;\s?/, ''))
      html.push(`<blockquote>${buf.map(inline).join('<br>')}</blockquote>`)
      continue
    }
    if (/^\s*[-*+]\s+/.test(line)) {
      const buf: string[] = []
      while (i < lines.length && /^\s*[-*+]\s+/.test(lines[i])) buf.push(lines[i++].replace(/^\s*[-*+]\s+/, ''))
      html.push(`<ul>${buf.map((b) => `<li>${inline(b)}</li>`).join('')}</ul>`)
      continue
    }
    if (/^\s*\d+[.)]\s+/.test(line)) {
      const buf: string[] = []
      while (i < lines.length && /^\s*\d+[.)]\s+/.test(lines[i])) buf.push(lines[i++].replace(/^\s*\d+[.)]\s+/, ''))
      html.push(`<ol>${buf.map((b) => `<li>${inline(b)}</li>`).join('')}</ol>`)
      continue
    }
    if (/^\|.*\|\s*$/.test(line)) {
      const rows: string[][] = []
      while (i < lines.length && /^\|.*\|\s*$/.test(lines[i])) {
        rows.push(lines[i++].trim().slice(1, -1).split('|').map((c) => c.trim()))
      }
      const isSep = (r: string[]) => r.every((c) => /^:?-{2,}:?$/.test(c))
      const head = rows.length > 1 && isSep(rows[1]) ? rows[0] : null
      const body = head ? rows.slice(2) : rows
      html.push(
        '<table>' +
          (head ? `<thead><tr>${head.map((c) => `<th>${inline(c)}</th>`).join('')}</tr></thead>` : '') +
          `<tbody>${body.map((r) => `<tr>${r.map((c) => `<td>${inline(c)}</td>`).join('')}</tr>`).join('')}</tbody></table>`,
      )
      continue
    }
    // 段落：连续非空、非特殊行
    const buf: string[] = []
    while (
      i < lines.length &&
      !/^\s*$/.test(lines[i]) &&
      !/^(```|#{1,4}\s|&gt;|\s*[-*+]\s+|\s*\d+[.)]\s+|\|)/.test(lines[i])
    ) {
      buf.push(lines[i++])
    }
    if (buf.length) html.push(`<p>${buf.map(inline).join('<br>')}</p>`)
    else i++
  }
  return html.join('\n')
}
