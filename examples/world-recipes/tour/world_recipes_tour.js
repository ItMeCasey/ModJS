// Development only: the screenshot tour for in-world crafting (scripts/world_recipes_tour.py). It defines two recipes, puts a stone block
// and a one-block pool of water in front of the player, right-clicks the stone with flint through the server's packet handler, drops coal and
// sand into the cauldron, and logs "WR-CHECK ..." results after a few seconds.
(function () {
  const J = name => Java.loadClass(name);
  DimensionEvents.add(event => {
    event.addType('modjs:bright', {base: 'minecraft:overworld', set: {ambient_light: 0.5}});
    event.add('modjs:flat_test', {generator: 'flat', layers: [{block: 'minecraft:stone', height: 3}], type: 'modjs:bright'});
  });
  ServerEvents.worldRecipes(event => {
    event.itemOnBlock('minecraft:stone', 'minecraft:flint').turnInto('minecraft:gold_block').drop('minecraft:gravel', 2);
    event.itemsInBlock('minecraft:water', ['minecraft:coal x2', 'minecraft:sand']).drop('minecraft:diamond');
  });
  PlayerEvents.loggedIn(event => {
    const run = command => event.runCommand(command);
    const me = event.player.getName().getString();
    const bx = Math.floor(event.player.getX());
    const by = Math.round(event.player.getY());
    const bz = Math.floor(event.player.getZ()) + 3;
    const px = Math.floor(event.player.getX());
    const pz = Math.floor(event.player.getZ());
    let version = '';
    try { version = String(J('net.minecraft.SharedConstants').getCurrentVersion().getName()); } catch (e) {}
    if (version.indexOf('1.') === 0) {
      run('gamerule sendCommandFeedback false');
      run('gamerule logAdminCommands false');
    }
    run('kill @e[type=minecraft:item_frame]');
    run('kill @e[type=minecraft:item]');
    run('fill ' + (px - 5) + ' ' + by + ' ' + (pz - 4) + ' ' + (px + 5) + ' ' + (by + 5) + ' ' + (pz + 7) + ' minecraft:air');
    run('fill ' + (px - 5) + ' ' + (by - 1) + ' ' + (pz - 4) + ' ' + (px + 5) + ' ' + (by - 1) + ' ' + (pz + 7) + ' minecraft:stone');
    run('gamemode survival ' + me);
    run('time set noon');
    run('weather clear');
    const level = () => event.player.level();
    const pos = (x, y, z) => new (J('net.minecraft.core.BlockPos'))(x, y, z);
    const stateText = (x, y, z) => String(level().getBlockState(pos(x, y, z)));
    const nearbyItems = (x, y, z) => {
      const AABB = J('net.minecraft.world.phys.AABB');
      const list = level().getEntitiesOfClass(J('net.minecraft.world.entity.item.ItemEntity'), new AABB(x - 2, y - 1, z - 2, x + 3, y + 3, z + 3));
      const out = [];
      for (let i = 0; i < list.size(); i++) { const e = list.get(i); out.push(String(e.getItem())); console.log('WR-TOUR item ' + e.getItem() + ' at ' + e.getX().toFixed(2) + ' ' + e.getY().toFixed(2) + ' ' + e.getZ().toFixed(2)); }
      return out;
    };
    Utils.runAfter(100, () => {
      run('setblock ' + (bx - 1) + ' ' + by + ' ' + bz + ' minecraft:stone');
      run('setblock ' + (bx + 1) + ' ' + (by - 1) + ' ' + bz + ' minecraft:water');   // a one-block pool in the floor
      run('item replace entity ' + me + ' hotbar.0 with minecraft:flint 3');
      run('tp ' + me + ' ' + bx + ' ' + by + ' ' + (bz - 3.5) + ' 0 14');
      console.log('WR-TOUR READY world');
    });
    // right-click the stone with flint, as a client sends it
    Utils.runAfter(160, () => {
      const player = event.player;
      const Packet = J('net.minecraft.network.protocol.game.ServerboundUseItemOnPacket');
      const Vec3 = J('net.minecraft.world.phys.Vec3');
      const hit = new (J('net.minecraft.world.phys.BlockHitResult'))(new Vec3(bx - 0.5, by + 1.0, bz + 0.5), J('net.minecraft.core.Direction').UP, pos(bx - 1, by, bz), false);
      const hand = J('net.minecraft.world.InteractionHand').MAIN_HAND;
      let packet;
      try { packet = new Packet(hand, hit, 1); } catch (e) { packet = new Packet(hand, hit); }
      player.connection.handleUseItemOn(packet);
      console.log('WR-TOUR CLICKED stone');
    });
    // drop coal and sand into the pool
    Utils.runAfter(200, () => {
      const ItemEntity = J('net.minecraft.world.entity.item.ItemEntity');
      const ItemStack = J('net.minecraft.world.item.ItemStack');
      const Items = J('net.minecraft.world.item.Items');
      [[Items.COAL, 2], [Items.SAND, 1]].forEach(pair => {
        const entity = new ItemEntity(level(), bx + 1.5, by + 0.4, bz + 0.5, new ItemStack(pair[0], pair[1]));
        entity.setDeltaMovement(0, 0, 0);
        entity.setPickUpDelay(400);   // the player stands close by and must not pick them up
        level().addFreshEntity(entity);
      });
      console.log('WR-TOUR DROPPED coal and sand');
    });
    Utils.runAfter(230, () => {
      const AABB = J('net.minecraft.world.phys.AABB');
      const list = level().getEntitiesOfClass(J('net.minecraft.world.entity.item.ItemEntity'), new AABB(bx - 10, by - 3, bz - 10, bx + 10, by + 6, bz + 10));
      for (let i = 0; i < list.size(); i++) { const e = list.get(i); console.log('WR-TOUR at230 ' + e.getItem() + ' at ' + e.getX().toFixed(2) + ' ' + e.getY().toFixed(2) + ' ' + e.getZ().toFixed(2) + ' inWater=' + e.isInWater()); }
      console.log('WR-TOUR recipes=' + J('com.itmecasey.modjs.worldcraft.WorldRecipes').all().size() + ' bx=' + bx + ' by=' + by + ' bz=' + bz);
    });
    Utils.runAfter(360, () => {
      const stone = stateText(bx - 1, by, bz);
      const gravel = nearbyItems(bx - 1, by, bz).join(',');
      const diamond = nearbyItems(bx + 1, by - 1, bz).join(',');
      console.log('WR-TOUR engine spawned ' + J('com.itmecasey.modjs.worldcraft.WorldCraftAdapters').recentSpawns());
      console.log('WR-TOUR pool block is ' + stateText(bx + 1, by - 1, bz));
      console.log('WR-CHECK ' + (stone.indexOf('gold_block') >= 0 ? 'PASS' : 'FAIL') + ' item on block: ' + stone);
      console.log('WR-CHECK ' + (gravel.indexOf('gravel') >= 0 ? 'PASS' : 'FAIL') + ' drops: ' + gravel);
      console.log('WR-CHECK ' + (String(J('com.itmecasey.modjs.worldcraft.WorldCraftAdapters').recentSpawns()).indexOf('minecraft:diamond x1') >= 0 && diamond.indexOf('coal') < 0 && diamond.indexOf('sand') < 0 ? 'PASS' : 'FAIL') + ' items in water: ' + diamond);
    });
  });
})();
