// Development only, part of the client tour. Builds an overlay (bar, text, rectangle, picture, item, a pulsing warning), saves a frame
// of it, then opens a scripted screen with icon buttons and a screen overlay, saves a second frame and closes the game.
// It answers the saved world's backup/experimental confirmation screen. Frames: overlay_hud.png and overlay_screen.png.
(function () {
  const J = name => Java.loadClass(name);
  const note = (name, e) => console.log('OVERLAY-CHECK driver ' + name + ' ' + String(e).replace(/\s+/g, ' ').slice(0, 200));

  ClientEvents.screens(event => {
    event.create('overlay_demo', screen => {
      screen.text('Overlay demo screen', { y: 24 });
      screen.button('Item icon', 'close', { y: 60, width: 150, icon: 'minecraft:diamond_sword' });
      screen.button('Texture icon', 'close', { y: 90, width: 150, icon: 'minecraft:textures/item/apple.png', iconSide: 'right' });
      screen.button('Centered icon', 'close', { y: 120, width: 150, icon: 'minecraft:emerald', iconSide: 'center', iconSize: 12 });
    });
  });

  let hp = null;
  const build = () => {
    hp = Overlay.bar('hp', { x: 10, y: 10, width: 120, height: 10, value: 100, max: 100, color: '#cc2222', color2: '#ff9944',
      label: '{value} / {max}', smooth: 400, border: '#ffffff', borderSize: 1 });
    Overlay.bar('xp', { anchor: 'bottom', y: -40, width: 182, height: 5, value: 0, max: 100, color: '#66dd66', background: '#66000000', border: 0xFF000000 })
      .animate({ value: 70 }, 800, 'easeOut');
    Overlay.text('score', { x: 10, y: 28, text: 'Score: 1234', color: '#ffcc44', scale: 1.5, outline: '#000000', shadow: false });
    Overlay.text('big', { anchor: 'topright', x: -10, y: 10, text: 'Quest\nFind 3 rubies', align: 'right', background: '#aa101820', padding: 4 });
    Overlay.rect('panel', { anchor: 'left', x: 6, y: 20, width: 90, height: 28, color: '#cc3a5ca8', color2: '#cc101840', border: '#88ffffff' });
    Overlay.image('logo', { anchor: 'topright', x: -10, y: 50, width: 32, height: 32, texture: 'minecraft:textures/item/diamond.png' });
    Overlay.item('sword', { anchor: 'topright', x: -52, y: 54, item: 'minecraft:diamond_sword', scale: 2 });
    Overlay.text('warn', { anchor: 'top', y: 40, text: 'LOW HEALTH', color: '#ff4444', scale: 2 }).pulse(900, 0.25, 1.0);
    Overlay.text('screen_only', { surface: 'screen', anchor: 'bottom', y: -20, text: 'This text is a screen overlay', color: '#88ffaa' });
  };

  let ticks = 0, inWorld = 0, pressed = 0, started = false;
  ClientEvents.tick(() => {
    ticks++;
    const mc = J('net.minecraft.client.Minecraft').getInstance();
    let screen = null;
    try { const s = mc.gui.screen(); if (s !== undefined) screen = s; else screen = mc.screen; } catch (e) { screen = mc.screen; }
    if (pressed < 4 && screen != null && mc.level == null && ticks > 300 && ticks % 40 === 0) {
      pressed++;
      try { const kids = screen.children(); const b = []; for (let i = 0; i < kids.size(); i++) { const k = kids.get(i); if (('' + k).indexOf('Button') >= 0) b.push(k); } if (b.length > 1) { try { b[1].onPress(); } catch (e0) { b[1].onPress(null); } } } catch (e) { note('confirm', e); }
    }
    if (mc.level == null || mc.player == null) return;
    inWorld++;
    if (inWorld === 30) { try { if (mc.player.isDeadOrDying()) mc.player.respawn(); } catch (e) { note('respawn', e); } }
    if (inWorld === 60 && !started) { started = true; try { build(); console.log('OVERLAY-CHECK built'); } catch (e) { note('build', e); } }
    if (inWorld === 90) { try { hp.value = 35; } catch (e) { note('value', e); } }
    if (inWorld === 220) { try { Client.screenshot('overlay_hud.png'); console.log('OVERLAY-CHECK hud shot'); } catch (e) { note('hud shot', e); } }
    if (inWorld === 260) { try { Client.openScreen('overlay_demo'); } catch (e) { note('open screen', e); } }
    if (inWorld === 300) { try { Client.screenshot('overlay_screen.png'); console.log('OVERLAY-CHECK screen shot'); } catch (e) { note('screen shot', e); } }
    if (inWorld === 330) { try { Client.closeScreen(); mc.stop(); } catch (e) { note('stop', e); } }
  });
})();
