/** HTML 特殊字符转义（& < > " '）：
 *  用于 ECharts tooltip formatter（renderMode=html，经 innerHTML 插入）等
 *  会拼 HTML 字符串的场景，防止用户可控字段造成存储型 XSS。 */
export function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
