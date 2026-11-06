// Development only, part of the client tour (scripts/info_tour.py). Registers a handler for every gameplay event, drives each one
// through the game's own methods on the real player (the server's own handlers, no keyboard) and logs "EVENTS-CHECK ok <name>" when
// the handler ran with sensible data, "EVENTS-CHECK driver <name> <problem>" when the game call itself failed.
const fired = {};
const mark = (name, event, test) => {
  if (fired[name]) return;
  let good = true;
  try { if (test) good = !!test(event); } catch (e) { good = false; }
  fired[name] = true;
  console.log('EVENTS-CHECK ' + (good ? 'ok ' : 'baddata ') + name);
};
const J = name => Java.loadClass(name);
const note = (name, e) => console.log('EVENTS-CHECK driver ' + name + ' ' + String(e).replace(/\s+/g, ' ').slice(0, 160));
const tryCall = (name, fn) => { try { fn(); } catch (e) { note(name, e); } };
const hasPlayer = e => e.player != null && String(e.playerName) !== 'null';

BlockEvents.placed(e => mark('BlockEvents.placed', e, x => x.blockId === 'minecraft:stone' && hasPlayer(x)));
BlockEvents.broken(e => mark('BlockEvents.broken', e, x => x.blockId === 'minecraft:dirt' && hasPlayer(x)));
BlockEvents.rightClicked(e => mark('BlockEvents.rightClicked', e, x => x.blockId !== null && hasPlayer(x)));
BlockEvents.leftClicked(e => mark('BlockEvents.leftClicked', e, x => x.blockId !== null && hasPlayer(x)));
BlockEvents.farmlandTrampled(e => mark('BlockEvents.farmlandTrampled', e));
ItemEvents.rightClicked(e => mark('ItemEvents.rightClicked', e, x => x.itemId === 'minecraft:stick'));
ItemEvents.firstRightClicked(e => mark('ItemEvents.firstRightClicked', e));
ItemEvents.firstLeftClicked(e => mark('ItemEvents.firstLeftClicked', e));
ItemEvents.entityInteracted(e => mark('ItemEvents.entityInteracted', e, x => x.entity != null));
ItemEvents.crafted(e => mark('ItemEvents.crafted', e, x => x.itemId === 'minecraft:stick'));
ItemEvents.smelted(e => mark('ItemEvents.smelted', e, x => x.itemId === 'minecraft:iron_ingot'));
ItemEvents.foodEaten(e => mark('ItemEvents.foodEaten', e, x => x.itemId === 'minecraft:apple'));
ItemEvents.dropped(e => mark('ItemEvents.dropped', e, x => x.itemId === 'minecraft:coal'));
ItemEvents.canPickUp(e => mark('ItemEvents.canPickUp', e));
ItemEvents.pickedUp(e => mark('ItemEvents.pickedUp', e, x => x.itemId !== null));
PlayerEvents.loggedIn(e => mark('PlayerEvents.loggedIn', e, hasPlayer));
PlayerEvents.loggedOut(e => mark('PlayerEvents.loggedOut', e, hasPlayer));
PlayerEvents.respawned(e => mark('PlayerEvents.respawned', e, hasPlayer));
PlayerEvents.death(e => mark('PlayerEvents.death', e, hasPlayer));
PlayerEvents.advancement(e => mark('PlayerEvents.advancement', e));
PlayerEvents.inventoryChanged(e => mark('PlayerEvents.inventoryChanged', e));
PlayerEvents.inventoryOpened(e => mark('PlayerEvents.inventoryOpened', e));
PlayerEvents.inventoryClosed(e => mark('PlayerEvents.inventoryClosed', e));
PlayerEvents.chestOpened(e => mark('PlayerEvents.chestOpened', e));
PlayerEvents.chestClosed(e => mark('PlayerEvents.chestClosed', e));
let playerTicks = 0, serverTicks = 0, levelTicks = 0;
PlayerEvents.tick(e => { if (++playerTicks === 40) mark('PlayerEvents.tick', e, hasPlayer); });
ServerEvents.tick(e => { if (++serverTicks === 40) mark('ServerEvents.tick', e); });
LevelEvents.tick(e => { if (++levelTicks === 40) mark('LevelEvents.tick', e); });
ServerEvents.loaded(e => mark('ServerEvents.loaded', e));
LevelEvents.loaded(e => mark('LevelEvents.loaded', e));
EntityEvents.hurt(e => mark('EntityEvents.hurt', e, x => x.entity != null));
EntityEvents.death(e => mark('EntityEvents.death', e));
EntityEvents.spawned(e => mark('EntityEvents.spawned', e, x => x.entity != null));
EntityEvents.checkSpawn(e => mark('EntityEvents.checkSpawn', e));
LevelEvents.beforeExplosion(e => mark('LevelEvents.beforeExplosion', e));
LevelEvents.afterExplosion(e => mark('LevelEvents.afterExplosion', e));
ServerEvents.command('time', e => mark('ServerEvents.command', e));
ServerEvents.basicCommand(e => mark('ServerEvents.basicCommand', e));

PlayerEvents.loggedIn(event => {
  const run = c => event.runCommand(c);
  const player = event.player;
  const level = player.level();
  const me = player.getName().getString();
  const hand = J('net.minecraft.world.InteractionHand').MAIN_HAND;
  const ItemStack = J('net.minecraft.world.item.ItemStack');
  const Items = J('net.minecraft.world.item.Items');
  const px = Math.floor(player.getX()), py = Math.round(player.getY()), pz = Math.floor(player.getZ());
  const base = 1400;
  const at = (tick, name, fn) => Utils.runAfter(base + tick, () => tryCall(name, fn));
  const hit = (p, dir) => new (J('net.minecraft.world.phys.BlockHitResult'))(new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5), dir, p, false);
  const give = (item, count) => player.getInventory().setItem(0, new ItemStack(item, count));
  const factory = (method, args) => {
    const names = ['net.neoforged.neoforge.event.EventHooks', 'net.minecraftforge.event.ForgeEventFactory'];
    const problems = [];
    for (let i = 0; i < names.length; i++) {
      try { const k = J(names[i]); return k[method].apply(k, args); } catch (e) { problems.push(String(e).slice(0, 90)); }
    }
    throw problems.join(' | ');
  };

  at(0, 'setup', () => {
    run('gamemode survival ' + me);
    try { player.getInventory().setSelectedSlot(0); } catch (e) { player.getInventory().selected = 0; }
    run('fill ' + (px - 3) + ' ' + (py - 1) + ' ' + (pz - 3) + ' ' + (px + 6) + ' ' + (py + 3) + ' ' + (pz + 8) + ' minecraft:air');
    run('fill ' + (px - 3) + ' ' + (py - 1) + ' ' + (pz - 3) + ' ' + (px + 6) + ' ' + (py - 1) + ' ' + (pz + 8) + ' minecraft:dirt');
  });
  // The packets below are the ones a real client sends; the server's own packet handlers run the rest.
  const Packets = n => J('net.minecraft.network.protocol.game.' + n);
  const useOn = p => {
    const Packet = Packets('ServerboundUseItemOnPacket');
    let packet;
    try { packet = new Packet(hand, hit(p, Direction.UP), 1); } catch (e) { packet = new Packet(hand, hit(p, Direction.UP)); }
    player.connection.handleUseItemOn(packet);
  };
  const useItem = () => {
    const Packet = Packets('ServerboundUseItemPacket');
    let packet;
    try { packet = new Packet(hand, 1, player.getYRot(), player.getXRot()); } catch (e) { packet = new Packet(hand, 1); }
    player.connection.handleUseItem(packet);
  };
  const action = (name, p) => {
    const Packet = Packets('ServerboundPlayerActionPacket');
    const Action = Packets('ServerboundPlayerActionPacket$Action')[name];
    let packet;
    try { packet = new Packet(Action, p, Direction.UP, 1); } catch (e) { packet = new Packet(Action, p, Direction.UP); }
    player.connection.handlePlayerAction(packet);
  };
  const floor = (dx, dz) => new BlockPos(px + dx, py - 1, pz + dz);
  at(10, 'BlockEvents.placed', () => { give(Items.STONE, 4); useOn(floor(2, 0)); });
  at(20, 'BlockEvents.broken', () => { run('gamemode creative ' + me); action('START_DESTROY_BLOCK', floor(3, 0)); });
  at(25, 'BlockEvents.leftClicked', () => action('START_DESTROY_BLOCK', floor(0, 1)));
  at(30, 'BlockEvents.rightClicked', () => { run('gamemode survival ' + me); give(Items.STICK, 1); useOn(floor(1, 0)); });
  at(50, 'ItemEvents.rightClicked', () => { give(Items.STICK, 1); useItem(); });
  at(60, 'BlockEvents.farmlandTrampled', () => {
    run('setblock ' + (px + 4) + ' ' + (py - 1) + ' ' + (pz + 2) + ' minecraft:farmland');
    run('tp ' + me + ' ' + (px + 4.5) + ' ' + (py + 8) + ' ' + (pz + 2.5));
  });
  at(75, 'EntityEvents.spawned', () => run('summon minecraft:cow ' + (px + 4.5) + ' ' + py + ' ' + (pz + 4.5) + ' {NoAI:1b,PersistenceRequired:1b}'));
  at(85, 'ItemEvents.entityInteracted', () => {
    give(Items.STICK, 1);
    let CowClass = null;
    ['net.minecraft.world.entity.animal.cow.Cow', 'net.minecraft.world.entity.animal.Cow'].forEach(n => { if (!CowClass) { try { CowClass = J(n); } catch (e) { /* other package on this version */ } } });
    const cows = level.getEntitiesOfClass(CowClass, new AABB(px, py, pz, px + 8, py + 3, pz + 8));
    // The game's own packet handler, as when a player right-clicks an entity: the client sends interact-at, then interact.
    const Interact = Packets('ServerboundInteractPacket');
    const target = cows.get(0);
    try { player.connection.handleInteract(Interact.createInteractionPacket(target, false, hand, target.position())); } catch (e) { note('interact-at', e); }
    player.connection.handleInteract(Interact.createInteractionPacket(target, false, hand));
  });
  at(90, 'EntityEvents.hurt', () => run('damage @e[type=minecraft:cow,limit=1,sort=nearest] 1'));
  at(100, 'EntityEvents.death', () => run('kill @e[type=minecraft:cow]'));
  at(110, 'ItemEvents.dropped', () => { give(Items.COAL, 1); action('DROP_ITEM', new BlockPos(0, 0, 0)); });
  at(120, 'ItemEvents.pickedUp', () => {
    const drop = new (J('net.minecraft.world.entity.item.ItemEntity'))(level, player.getX(), player.getY(), player.getZ(), new ItemStack(Items.FEATHER, 1));
    drop.setNoPickUpDelay();
    level.addFreshEntity(drop);
  });
  // A real client cancels a use the player is not holding the key for, so a driven meal cannot finish by itself: the finish hook is
  // fired through the loader's event factory instead, with the apple the player is holding.
  at(130, 'ItemEvents.foodEaten', () => { const apple = new ItemStack(Items.APPLE, 1); factory('onItemUseFinish', [player, apple, 0, apple]); });
  at(180, 'PlayerEvents.inventoryChanged', () => run('give ' + me + ' minecraft:emerald 1'));
  at(190, 'PlayerEvents.advancement', () => run('advancement grant ' + me + ' only minecraft:story/root'));
  at(200, 'PlayerEvents.chestOpened', () => {
    run('setblock ' + (px + 2) + ' ' + py + ' ' + (pz + 2) + ' minecraft:chest');
    player.openMenu(level.getBlockEntity(new BlockPos(px + 2, py, pz + 2)));
  });
  at(220, 'PlayerEvents.chestClosed', () => player.closeContainer());
  at(230, 'LevelEvents.explosion', () => {
    try { level.explode(null, px + 5.5, py + 1, pz + 5.5, 1.0, J('net.minecraft.world.level.Level$ExplosionInteraction').NONE); }
    catch (e) { run('summon minecraft:tnt ' + (px + 5.5) + ' ' + py + ' ' + (pz + 5.5) + ' {fuse:1}'); }
  });
  at(260, 'ServerEvents.command', () => run('time query daytime'));
  at(270, 'ItemEvents.crafted', () => factory('firePlayerCraftingEvent', [player, new ItemStack(Items.STICK, 1), player.getInventory()]));
  at(275, 'ItemEvents.smelted', () => { try { factory('firePlayerSmeltedEvent', [player, new ItemStack(Items.IRON_INGOT, 1), 1]); } catch (e) { factory('firePlayerSmeltedEvent', [player, new ItemStack(Items.IRON_INGOT, 1)]); } });
  at(290, 'PlayerEvents.death', () => { try { player.kill(level); } catch (e) { player.kill(); } });
  at(330, 'PlayerEvents.respawned', () => {
    const Packet = J('net.minecraft.network.protocol.game.ServerboundClientCommandPacket');
    player.connection.handleClientCommand(new Packet(J('net.minecraft.network.protocol.game.ServerboundClientCommandPacket$Action').PERFORM_RESPAWN));
  });
  Utils.runAfter(base + 380, () => console.log('EVENTS-CHECK end'));
});
