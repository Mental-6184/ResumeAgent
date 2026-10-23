/**
 * 共享 Markdown 渲染 composable
 * 统一 ChatPanel 和 TechGuidePanel 的 Markdown 渲染逻辑
 */
export function useMarkdown() {
  function renderMarkdown(text: string): string {
    if (!text) return ''

    let html = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')

    // Code blocks
    html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (_match, lang, code) => {
      const langLabel = lang ? `<div class="code-lang">${lang}</div>` : ''
      return `<div class="code-block-wrapper">${langLabel}<pre class="code-block"><code>${code}</code></pre></div>`
    })

    // Inline code
    html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')

    // Headings
    html = html.replace(/^### (.+)$/gm, '<h4 class="md-h4">$1</h4>')
    html = html.replace(/^## (.+)$/gm, '<h3 class="md-h3">$1</h3>')
    html = html.replace(/^# (.+)$/gm, '<h2 class="md-h2">$1</h2>')

    // Bold & italic
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')

    // Lists
    html = html.replace(/^- (.+)$/gm, '<div class="md-li">• $1</div>')
    html = html.replace(/^\d+\. (.+)$/gm, '<div class="md-oli">$1</div>')

    // ASCII diagrams
    html = html.replace(/((?:^.*[─│┌┐└┘┬┴├┤┼╔╗╚╝═║╠╣╦╩╬].*\n?){3,})/gm, '<pre class="ascii-diagram">$1</pre>')

    // Newlines
    html = html.replace(/\n/g, '<br>')

    return html
  }

  function renderSimple(text: string): string {
    if (!text) return ''
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>')
  }

  return { renderMarkdown, renderSimple }
}
