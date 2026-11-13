/** Finds string literals and the call they sit in, with a small scanner that needs no parser. */

export interface StringLiteral {
  /** Text between the quotes. */
  value: string;
  /** Offset of the opening quote. */
  quoteStart: number;
  /** Offset of the first character inside the quotes. */
  start: number;
  /** Offset of the closing quote, or where the line ends when the string is not closed. */
  end: number;
  quote: string;
}

export interface Frame {
  open: '(' | '[' | '{';
  callee: string;
  argIndex: number;
  key: string;
}

/** Every string literal in the text, skipping comments. */
export function stringLiterals(text: string): StringLiteral[] {
  const found: StringLiteral[] = [];
  const n = text.length;
  let i = 0;
  while (i < n) {
    const c = text[i];
    if (c === '/' && text[i + 1] === '/') {
      while (i < n && text[i] !== '\n') {
        i++;
      }
    } else if (c === '/' && text[i + 1] === '*') {
      const close = text.indexOf('*/', i + 2);
      i = close < 0 ? n : close + 2;
    } else if (c === '"' || c === "'" || c === '`') {
      const quoteStart = i;
      i++;
      while (i < n && text[i] !== c && (c === '`' || text[i] !== '\n')) {
        if (text[i] === '\\') {
          i++;
        }
        i++;
      }
      found.push({ value: text.slice(quoteStart + 1, Math.min(i, n)), quoteStart, start: quoteStart + 1, end: Math.min(i, n), quote: c });
      i++;
    } else {
      i++;
    }
  }
  return found;
}

/** The string literal that contains the offset (the cursor may sit right before the closing quote). */
export function stringAt(text: string, offset: number): StringLiteral | undefined {
  return stringLiterals(text).find((s) => offset >= s.start && offset <= s.end);
}

/** The bracket stack at the given offset, innermost last. */
export function frameAt(text: string, target: number): Frame[] {
  const stack: Frame[] = [];
  const n = text.length;
  let i = 0;
  let chain = '';
  let lastToken = '';
  let afterDot = false;
  const snapshot = () => stack.map((f) => ({ ...f }));
  while (i < n) {
    if (i === target) {
      return snapshot();
    }
    const c = text[i];
    if (c === '/' && text[i + 1] === '/') {
      while (i < n && text[i] !== '\n') {
        i++;
      }
      continue;
    }
    if (c === '/' && text[i + 1] === '*') {
      const close = text.indexOf('*/', i + 2);
      i = close < 0 ? n : close + 2;
      continue;
    }
    if (c === '"' || c === "'" || c === '`') {
      const start = i;
      i++;
      while (i < n && text[i] !== c && (c === '`' || text[i] !== '\n')) {
        if (text[i] === '\\') {
          i++;
        }
        i++;
      }
      lastToken = text.slice(start + 1, Math.min(i, n));
      chain = '';
      afterDot = false;
      i++;
      continue;
    }
    if (/[A-Za-z_$]/.test(c)) {
      let j = i + 1;
      while (j < n && /[\w$]/.test(text[j])) {
        j++;
      }
      const ident = text.slice(i, j);
      chain = afterDot && chain ? `${chain}.${ident}` : ident;
      lastToken = ident;
      afterDot = false;
      i = j;
      continue;
    }
    if (/\s/.test(c)) {
      i++;
      continue;
    }
    if (c === '.') {
      afterDot = chain !== '';
      i++;
      continue;
    }
    if (c === '?' && text[i + 1] === '.') {
      afterDot = chain !== '';
      i += 2;
      continue;
    }
    afterDot = false;
    if (c === '(') {
      stack.push({ open: '(', callee: chain, argIndex: 0, key: '' });
    } else if (c === '[' || c === '{') {
      stack.push({ open: c, callee: '', argIndex: 0, key: '' });
    } else if (c === ')' || c === ']' || c === '}') {
      stack.pop();
    } else if (c === ',') {
      const top = stack[stack.length - 1];
      if (top) {
        top.argIndex++;
        top.key = '';
      }
    } else if (c === ':') {
      const top = stack[stack.length - 1];
      if (top && top.open === '{') {
        top.key = lastToken;
      }
    }
    chain = '';
    i++;
  }
  return snapshot();
}

export interface CallContext {
  /** The full called name, for example `event.shaped`. */
  callee: string;
  /** The last part of it, for example `shaped`. */
  method: string;
  /** Everything before the last part, for example `event`. */
  owner: string;
  argIndex: number;
  /** The key of the object literal property the string is the value of, when it is one. */
  property: string;
  inArray: boolean;
}

/** The innermost call the offset is an argument of, or undefined. */
export function callContext(text: string, quoteStart: number): CallContext | undefined {
  const frames = frameAt(text, quoteStart);
  let call = -1;
  for (let i = frames.length - 1; i >= 0; i--) {
    if (frames[i].open === '(') {
      call = i;
      break;
    }
  }
  if (call < 0) {
    return undefined;
  }
  const inner = frames.slice(call + 1);
  const innermost = inner[inner.length - 1];
  const callee = frames[call].callee;
  const dot = callee.lastIndexOf('.');
  return {
    callee,
    method: dot < 0 ? callee : callee.slice(dot + 1),
    owner: dot < 0 ? '' : callee.slice(0, dot),
    argIndex: frames[call].argIndex,
    property: innermost && innermost.open === '{' ? innermost.key : '',
    inArray: innermost?.open === '[' || inner.some((f) => f.open === '['),
  };
}
