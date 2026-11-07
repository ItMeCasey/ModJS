// Development only, part of the client tour. Checks the client events in a real client, using the game's own methods (no desktop input):
// init, highPriorityAssets, loggedIn, loggedOut, tick, keyPressed and the item tooltip events, and sends one chat message for the
// server's chat event. It answers the world's backup/experimental confirmation screen, then closes the game on its own.
// Results are logged as "CLIENT-CHECK ok <name>" or "CLIENT-CHECK driver <name> <problem>".
(function () {
  const J = name => Java.loadClass(name);
  const note = (name, e) => console.log('CLIENT-CHECK driver ' + name + ' ' + String(e).replace(/\s+/g, ' ').slice(0, 200));
  const ok = name => console.log('CLIENT-CHECK ok ' + name);
  const seen = {};
  const once = (name, extra) => { if (!seen[name]) { seen[name] = true; ok(name + (extra ? ' ' + extra : '')); } };
  const screenOf = mc => { try { const s = mc.gui.screen(); if (s !== undefined) return s; } catch (e) {} return mc.screen; };

  ClientEvents.init(() => once('init'));
  ClientEvents.highPriorityAssets(() => once('highPriorityAssets'));
  ClientEvents.loggedIn(() => once('loggedIn'));
  ClientEvents.loggedOut(() => once('loggedOut'));
  ClientEvents.keyPressed(e => once('keyPressed', 'key=' + e.key + ' action=' + e.action));
  ItemEvents.tooltip(e => e.add('minecraft:diamond', 'CHECK-TIP static'));
  ItemEvents.dynamicTooltips('minecraft:diamond', e => e.add('minecraft:diamond', 'CHECK-TIP dynamic'));

  let ticks = 0, inWorld = 0, pressed = 0;
  const lines = stack => {
    const mc = J('net.minecraft.client.Minecraft').getInstance();
    const flagClass = J('net.minecraft.world.item.TooltipFlag');
    let flag = null;
    try { flag = flagClass.NORMAL; } catch (e) {}
    if (flag == null) { try { flag = J('net.minecraft.world.item.TooltipFlag$Default').NORMAL; } catch (e) {} }
    const attempts = [
      () => stack.getTooltipLines(J('net.minecraft.world.item.Item$TooltipContext').of(mc.level), mc.player, flag),
      () => stack.getTooltipLines(mc.player, flag),
      () => stack.getTooltipLines(J('net.minecraft.world.item.Item$TooltipContext').of(mc.level), mc.player, flag, null)
    ];
    for (let i = 0; i < attempts.length; i++) { try { const r = attempts[i](); if (r != null) return r; } catch (e) { note('tooltip-attempt', e); } }
    return null;
  };
  const text = list => { const out = []; for (let i = 0; i < list.size(); i++) out.push('' + list.get(i).getString()); return out.join(' | '); };

  ClientEvents.tick(() => {
    ticks++;
    const mc = J('net.minecraft.client.Minecraft').getInstance();
    const screen = screenOf(mc);
    if (pressed < 4 && screen != null && mc.level == null && ticks > 300 && ticks % 40 === 0) {
      pressed++;
      try {
        const kids = screen.children(); const buttons = [];
        for (let i = 0; i < kids.size(); i++) { const k = kids.get(i); if (('' + k).indexOf('Button') >= 0) buttons.push(k); }
        if (buttons.length > 1) { try { buttons[1].onPress(); } catch (e0) { buttons[1].onPress(null); } }
      } catch (e) { note('confirm-screen', e); }
    }
    if (mc.level == null || mc.player == null) return;
    inWorld++;
    once('tick');
    if (inWorld === 30) { try { if (mc.player.isDeadOrDying()) { mc.player.respawn(); console.log('CLIENT-CHECK respawned a dead test player'); } } catch (e) { note('respawn', e); } }
    if (inWorld === 80) {
      try { const l = lines(new (J('net.minecraft.world.item.ItemStack'))(J('net.minecraft.world.item.Items').DIAMOND)); if (l == null) note('tooltip', 'no getTooltipLines overload worked'); else { const t = text(l); console.log('CLIENT-CHECK tooltip lines: ' + t); if (t.indexOf('CHECK-TIP static') >= 0) ok('ItemEvents.tooltip'); if (t.indexOf('CHECK-TIP dynamic') >= 0) ok('ItemEvents.dynamicTooltips'); } } catch (e) { note('tooltip', e); }
    }
    if (inWorld === 120) {
      const kh = mc.keyboardHandler; const win = mc.getWindow();
      const handle = (() => { try { return win.handle(); } catch (e) { try { return win.getWindow(); } catch (e2) { return 0; } } })();
      const tries = [
        () => kh.keyPress(handle, 65, 30, 1, 0),
        () => kh.keyPress(handle, 1, new (J('net.minecraft.client.input.KeyEvent'))(65, 30, 0)),
        () => kh.keyPress(handle, 65, 30, 1, 0, 0)
      ];
      let done = false;
      for (let i = 0; i < tries.length; i++) { try { tries[i](); done = true; break; } catch (e) { note('keyPress-attempt', e); } }
      if (!done) note('keyPressed', 'no keyPress overload worked');
    }
    if (inWorld === 160) { try { mc.player.connection.sendChat('chat check from the client'); ok('chat-sent'); } catch (e) { note('chat', e); } }
    if (inWorld === 400) { try { console.log('CLIENT-CHECK dimension ' + mc.level.dimension()); Client.screenshot('dim_check.png'); } catch (e) { note('dimension', e); } }
    if (inWorld === 700) { console.log('CLIENT-CHECK stopping'); try { mc.stop(); } catch (e) { note('stop', e); } }
  });
})();
