// One-time migration for duke-engine 0.3.0: a block list separates its entries with a comma.
//
//   Modules = [          Modules = [
//     MoveUpdate           MoveUpdate
//     End           ->     End,
//     Turret               Turret
//     End                  End
//   ]                    ]
//
// Textual on purpose: DukeText already rejects the old form, so the new parser cannot be used to read
// what is being migrated. The rule needs only indentation, which these files are consistent about — an
// entry opens with a word at some depth and closes with End at the same depth.
const fs = require('fs');

function indentOf(line) {
  const m = line.match(/^[ \t]*/)[0];
  return m.replace(/\t/g, '    ').length;
}

function code(line) {
  // a ; outside quotes starts a comment
  let out = '', quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') quoted = !quoted;
    if (c === ';' && !quoted) break;
    out += c;
  }
  return out.trimEnd();
}

/** Index of the next line with code on it, or -1. */
function nextCode(lines, from) {
  for (let i = from; i < lines.length; i++) {
    if (code(lines[i]).trim() !== '') return i;
  }
  return -1;
}

function migrate(text) {
  const lines = text.split(/\r?\n/);
  const lists = [];           // entry indent for each open [ … ]
  let added = 0;
  for (let i = 0; i < lines.length; i++) {
    const bare = code(lines[i]).trim();
    if (bare === '') continue;

    if (bare === ']') { lists.pop(); continue; }

    // `Key = [` opens a list; its entries sit at whatever the next line is indented to
    if (/=\s*\[$/.test(bare)) {
      const first = nextCode(lines, i + 1);
      lists.push(first < 0 ? -1 : indentOf(lines[first]));
      continue;
    }

    if (lists.length === 0) continue;
    const entryIndent = lists[lists.length - 1];
    if (!/^End$/i.test(bare) || indentOf(lines[i]) !== entryIndent) continue;

    // an End that closes an entry: comma unless the list ends here
    const after = nextCode(lines, i + 1);
    if (after < 0 || code(lines[after]).trim() === ']') continue;
    lines[i] = lines[i].replace(/(End)(\s*)$/i, '$1,');
    added++;
  }
  return { text: lines.join('\n'), added };
}

const files = process.argv.slice(2);
let total = 0, touched = 0;
for (const file of files) {
  const before = fs.readFileSync(file, 'utf8');
  const { text, added } = migrate(before);
  if (added > 0) {
    fs.writeFileSync(file, text);
    touched++;
    total += added;
    console.log(String(added).padStart(4) + '  ' + file);
  }
}
console.log('--- ' + total + ' commas in ' + touched + ' files ---');
