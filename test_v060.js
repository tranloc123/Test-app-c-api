const assert = require('assert');
const {resolvePick, parsePickComment, allCharacters} = require('./character_resolver_v059b');

assert.strictEqual(allCharacters().length, 28);
assert.strictEqual(resolvePick(1).character, 'YOSHIMITSU');
assert.strictEqual(resolvePick(14).character, 'KRATOS');
assert.strictEqual(resolvePick(18).character, 'RAPHAEL');
assert.strictEqual(resolvePick(28).character, 'ZASALAMEL');
assert.strictEqual(resolvePick(17).sourceSlot, 17);
assert.strictEqual(resolvePick(18).sourceSlot, 19);
assert.strictEqual(resolvePick(28).sourceSlot, 29);
assert.strictEqual(parsePickComment('/pick 14').pick, 14);
assert.strictEqual(parsePickComment('pick 14'), null);
assert.strictEqual(parsePickComment('/pick 29'), null);

for (let p = 1; p <= 28; p++) {
  const r = resolvePick(p);
  assert(r);
  assert.strictEqual(r.pick, p);
  assert(r.sourceSlot >= 1 && r.sourceSlot <= 29 && r.sourceSlot !== 18);
}

console.log('SCBD V0.6.0R1 OFFLINE TEST: PASS');
console.log('Mapping: 1=YOSHIMITSU | 14=KRATOS | 18=RAPHAEL | 28=ZASALAMEL');
console.log('Exact source slots: 17->17 | 18->19 | 28->29 | timeout->30 Random');
