// Two-script config scenario, part A (startup): registers configs that the server script in selftest_config_b.js reads and changes.
const sharedA = Config.register('selftest_shared', { value: Config.number(1), tags: Config.list('string', ['a']) });
global.cfgInitial = sharedA.value;
global.cfgA = sharedA;
global.cfgChanges = [];
sharedA.onChange((cfg, keys) => { global.cfgChanges.push(keys.join('+')); });
Config.register('selftest_collide', { x: Config.number(1) });
