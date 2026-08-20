import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SseParser } from '../src/main/resources/static/sse.js';

test('handles delimiters, UTF-8 decoded fragments and multiple events',()=>{
  const events=[];const parser=new SseParser((name,data)=>events.push([name,data]));
  parser.push('event: delta\r\ndata: {"text":"你');
  parser.push('好"}\r\n\r');
  parser.push('\nevent: done\ndata: {}\n\n');
  assert.deepEqual(events,[['delta',{text:'你好'}],['done',{}]]);
});
test('ignores heartbeat and accepts multiline data',()=>{
  const events=[];const parser=new SseParser((name,data)=>events.push([name,data]));
  parser.push(': heartbeat\n\nevent: sources\ndata: [\ndata: {"id":"1"}]\n\n');
  assert.deepEqual(events,[['sources',[{id:'1'}]]]);
});
