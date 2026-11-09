// Development only, part of the client tour. Calls Minecraft's own classes and methods by their usual names, which a real
// Minecraft 1.20.1 game only knows under other names (the script engine translates them). Logs "NAMES ..." lines.
ServerEvents.loaded(e => {
  try {
    const Items = Java.loadClass('net.minecraft.world.item.Items');
    const Commands = Java.loadClass('net.minecraft.commands.Commands');
    console.log('NAMES Items.DIAMOND=' + Items.DIAMOND);
    console.log('NAMES recipes=' + e.server.getRecipeManager().getRecipes().size());
    console.log('NAMES Commands.literal=' + Commands.literal('hello').getLiteral());
    console.log('NAMES overworld=' + e.server.overworld().dimension().location());
    console.log('NAMES stack=' + Items.ENDER_PEARL.getDefaultInstance().getMaxStackSize());
  } catch (err) { console.log('NAMES FAILED ' + err); }
});
PlayerEvents.loggedIn(e => {
  try { console.log('NAMES player=' + e.player.getName().getString() + ' health=' + e.player.health + ' food=' + e.player.getFoodData().getFoodLevel() + ' pos=' + Math.round(e.player.getY())); }
  catch (err) { console.log('NAMES FAILED player ' + err); }
});
