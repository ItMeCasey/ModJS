// Development only, client half of the world overlay tour: gives a plain stone block an overlay at the position the server
// tour uses, takes the frames, and exits cleanly.
(function () {
  const mc = () => Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  let ticks = -1;
  let waiting = 0;
  Client.skipExperimentalWarning();
  Client.hideToasts('all');
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) {
      if (++waiting === 700) Client.screenshot('wo_stall.png');
      if (waiting % 100 === 0) {
        // a copied test world can wait on a confirmation screen (missing content, backup); answer it like the restart tour does
        let screen = null;
        try { screen = mc().gui.screen(); } catch (err) { screen = mc().screen; }
        if (screen != null) {
          console.log('WO-CHECK waiting screen ' + screen);
          Java.loadClass('com.itmecasey.modjs.client.ClientTweaks').autoConfirmExperimental(screen);
          const children = screen.children();
          const buttons = [];
          for (let i = 0; i < children.size(); i++) {
            const button = children.get(i);
            if (String(button).indexOf('Button') >= 0) buttons.push(button);
            try {
              const text = String(button.getMessage().getString());
              if (text === 'Proceed' || text === "I know what I'm doing!") {
                try { button.onPress(); } catch (err) { button.onPress(null); }
                break;
              }
            } catch (err) {}
          }
          if (String(screen).indexOf('BackupConfirmScreen') >= 0 && buttons.length > 1) {
            try { buttons[1].onPress(); } catch (err) { buttons[1].onPress(null); }
          }
        }
      }
      return;
    }
    ticks++;
    if (ticks === 30) {
      const p = mc().player;
      const bx = Math.floor(p.getX());
      const by = Math.round(p.getY());
      const bz = Math.floor(p.getZ()) + 3;
      // a block without a block entity: world overlays attach to any position
      const plain = Overlay.at(bx + 2, by, bz);
      plain.text('title', { text: 'Plain stone', y: 0.45, color: '#9ad7ff', background: '#80000000' });
      plain.bar('hp', { value: 3, max: 4, width: 0.9, height: 0.12, y: 0.2, color: '#3fdc5a' });
      plain.item('icon', { item: 'minecraft:diamond', y: 0.95 });
      plain.texture('badge', { texture: 'modjs:textures/gui/overlay_badge.png', face: 'north', width: 0.5, height: 0.5, y: 0 });
      console.log('WO-CHECK overlay positions ' + Overlay.world.positions);
    }
    if (ticks === 300) {
      Client.screenshot('wo_world.png');
      console.log('WO-CHECK ok world frame, positions=' + Overlay.world.positions);
    }
    if (ticks === 480) {
      let screen = null;
      try { screen = mc().gui.screen(); } catch (err) { screen = mc().screen; }
      const name = String(screen);
      console.log('WO-CHECK screen is ' + name);
      if (name.indexOf('ScriptContainerScreen') >= 0) Client.screenshot('wo_screen.png');
    }
    if (ticks === 540) {
      console.log('WO-CHECK ok screen frame');
    }
    if (ticks === 640) {
      console.log('WO-TOUR clean client exit');
      mc().stop();
    }
  });
})();
