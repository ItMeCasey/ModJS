// Two-script config scenario, part B (server): reads the config of the startup script, changes it, saves it, and registers a name twice.
const sharedB = Config.get('selftest_shared');
const start = global.cfgInitial;
// server scripts can run more than once in a test run (a reload); the first run is the one that sees the untouched config
const previous = global.cfgCross;
const result = {};
result.read = previous ? previous.read === true : (sharedB !== null && sharedB.value === start && sharedB.tags[0] === 'a');
sharedB.value = start + 1;
sharedB.set('tags', ['x', 'y']);
result.aSeesIt = global.cfgA.value === start + 1 && global.cfgA.tags.length === 2;
sharedB.save();
result.notRegistered = Config.get('selftest_nothing') === null && !Config.has('selftest_nothing');
Config.register('selftest_collide', { x: Config.number(2), y: Config.bool(true) });
result.collisionUsesLatest = Config.get('selftest_collide').y === true && global.cfgA !== null;
global.cfgCross = result;
