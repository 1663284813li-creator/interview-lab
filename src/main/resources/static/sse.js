export class SseParser {
  constructor(onEvent) { this.buffer = ''; this.onEvent = onEvent; }
  push(text) {
    this.buffer += text;
    // Accept LF and CRLF, including delimiters split across network chunks.
    let match;
    while ((match = /\r?\n\r?\n/.exec(this.buffer))) {
      const block = this.buffer.slice(0, match.index);
      this.buffer = this.buffer.slice(match.index + match[0].length);
      let event = 'message'; const data = [];
      for (const line of block.split(/\r?\n/)) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
      }
      if (data.length) this.onEvent(event, JSON.parse(data.join('\n')));
    }
  }
}
