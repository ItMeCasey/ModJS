// Config example: settings with limits, a nested group, and a callback when the file changes.
// Put this in server_scripts. The file is created as modjs/config/example.json5 on first run.
const cfg = Config.register('example', {
  speed: Config.number(1.5, { min: 0, max: 10, comment: 'Movement speed' }),
  enabled: Config.bool(true),
  mode: Config.choice('fast', ['fast', 'slow']),
  greeting: Config.string('Welcome', { maxLength: 40 }),
  blocked: Config.list('string', ['minecraft:bedrock']),
  limits: {
    perPlayer: Config.integer(8, { min: 1, max: 64 }),
    radius: Config.number(16, { min: 1 })
  }
});

PlayerEvents.loggedIn(event => {
  if (cfg.enabled) {
    event.tell(cfg.greeting + ' (' + cfg.mode + ', speed ' + cfg.speed + ')');
  }
});

BlockEvents.broken(event => {
  if (cfg.blocked.indexOf(event.blockId) >= 0) {
    event.cancel();
  }
});

cfg.onChange((config, keys) => {
  console.log('example config changed: ' + keys.join(', '));
});
