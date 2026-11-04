BlockEvents.broken('minecraft:stone', e => { console.log('SELFTEST script: broken ' + e.blockId + ' by ' + e.playerName); e.cancel(); });
PlayerEvents.chat(e => { console.log('SELFTEST script: chat "' + e.message + '"'); e.message = e.message.toUpperCase(); });
PlayerEvents.loggedIn(e => console.log('SELFTEST script: loggedIn ' + e.playerName));
ItemEvents.crafted(e => console.log('SELFTEST script: crafted ' + e.itemId));
EntityEvents.hurt(e => { console.log('SELFTEST script: hurt amount ' + e.amount); e.setAmount(e.amount * 2); });
console.log("SELFTEST script: globals " + new Vec3(1, 2, 3).x + " " + new BlockPos(4, 5, 6).y + " " + (UUID.randomUUID().toString().length) + " " + Direction.UP);
