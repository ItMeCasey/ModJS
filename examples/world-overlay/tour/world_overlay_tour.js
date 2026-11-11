// Development only: the screenshot tour for world overlays and scripted blocks (scripts/world_overlay_tour.py). On login it
// builds a stone floor, puts a Demo Smelter and a plain stone block in front of the player, fills the smelter, and later
// right-clicks the smelter through the server's own packet handler so its screen opens. It logs "WO-TOUR ..." markers.
(function () {
  const J = name => Java.loadClass(name);
  // The copied tour world holds a run-time dimension made by the restart tour; without its definition the world does not load.
  DimensionEvents.add(event => {
    event.addType('modjs:bright', {base: 'minecraft:overworld', set: {ambient_light: 0.5}});
    event.add('modjs:flat_test', {generator: 'flat', layers: [{block: 'minecraft:stone', height: 3}], type: 'modjs:bright'});
  });
  PlayerEvents.loggedIn(event => {
    const run = command => event.runCommand(command);
    const me = event.player.getName().getString();
    const bx = Math.floor(event.player.getX());
    const by = Math.round(event.player.getY());
    const bz = Math.floor(event.player.getZ()) + 3;
    const px = Math.floor(event.player.getX());
    const pz = Math.floor(event.player.getZ());
    // command feedback would print into the frames; the gamerule command is only accepted this way up to 1.21.x
    let version = '';
    try { version = String(J('net.minecraft.SharedConstants').getCurrentVersion().getName()); } catch (e) {}
    if (version.indexOf('1.') === 0) {
      run('gamerule sendCommandFeedback false');
      run('gamerule logAdminCommands false');
      run('gamerule announceAdvancements false');
    }
    run('kill @e[type=minecraft:item_frame]');
    run('kill @e[type=minecraft:item]');
    run('fill ' + (px - 5) + ' ' + by + ' ' + (pz - 4) + ' ' + (px + 5) + ' ' + (by + 5) + ' ' + (pz + 7) + ' minecraft:air');
    run('fill ' + (px - 5) + ' ' + (by - 1) + ' ' + (pz - 4) + ' ' + (px + 5) + ' ' + (by - 1) + ' ' + (pz + 7) + ' minecraft:stone');
    run('gamemode creative ' + me);
    run('time set noon');
    run('weather clear');
    Utils.runAfter(100, () => {
      run('setblock ' + bx + ' ' + by + ' ' + bz + ' modjs:demo_smelter[facing=north]');
      run('setblock ' + (bx + 2) + ' ' + by + ' ' + bz + ' minecraft:stone');
      run('item replace block ' + bx + ' ' + by + ' ' + bz + ' container.0 with minecraft:raw_iron 20');
      // stand between the two blocks, a few blocks back, and look slightly down at them
      run('tp ' + me + ' ' + (bx + 1.0) + ' ' + by + ' ' + (bz - 3.5) + ' 0 14');
      console.log('WO-TOUR READY world');
    });
    // a right click on the smelter, as the client sends it; the block opens its menu and the client opens the screen
    Utils.runAfter(420, () => {
      const player = event.player;
      const Packet = J('net.minecraft.network.protocol.game.ServerboundUseItemOnPacket');
      const Vec3 = J('net.minecraft.world.phys.Vec3');
      const pos = new (J('net.minecraft.core.BlockPos'))(bx, by, bz);
      const hit = new (J('net.minecraft.world.phys.BlockHitResult'))(new Vec3(bx + 0.5, by + 0.5, bz + 0.05), J('net.minecraft.core.Direction').NORTH, pos, false);
      const hand = J('net.minecraft.world.InteractionHand').MAIN_HAND;
      let packet;
      try { packet = new Packet(hand, hit, 1); } catch (e) { packet = new Packet(hand, hit); }
      player.connection.handleUseItemOn(packet);
      console.log('WO-TOUR OPENED screen');
    });
    // let the smelter work for a while, then check that it made iron ingots from the raw iron
    Utils.runAfter(560, () => {
      const be = Utils.blockEntity('minecraft:overworld', bx, by, bz);
      const made = be == null ? -1 : be.getCount(1);
      console.log('WO-CHECK ' + (be != null && made > 0 && be.getItemId(1) === 'minecraft:iron_ingot' ? 'PASS' : 'FAIL') + ' smelter made ingots: ' + made);
    });
  });
})();
