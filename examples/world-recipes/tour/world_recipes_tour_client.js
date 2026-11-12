// Development only, client half of the in-world crafting tour: checks that the recipes reach the recipe viewer information pages,
// takes the frame, and exits cleanly.
(function () {
  const mc = () => Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  let ticks = -1;
  let waiting = 0;
  Client.skipExperimentalWarning();
  Client.hideToasts('all');
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) {
      if (++waiting === 700) Client.screenshot('wr_stall.png');
      if (waiting % 100 === 0) {
        let screen = null;
        try { screen = mc().gui.screen(); } catch (err) { screen = mc().screen; }
        if (screen != null) {
          console.log('WR-CHECK waiting screen ' + screen);
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
    if (ticks === 330) {
      Client.screenshot('wr_world.png');
      console.log('WR-CHECK ok frame');
    }
    if (ticks === 350) {
      const AddInformation = Java.loadClass('com.itmecasey.modjs.client.RecipeViewerEventJS$AddInformation');
      const info = new AddInformation().info();
      const stone = info.get('minecraft:stone');
      const flint = info.get('minecraft:flint');
      const diamond = info.get('minecraft:diamond');
      console.log('WR-CHECK ' + (stone != null && String(stone).indexOf('right-click Stone with Flint') >= 0 ? 'PASS' : 'FAIL') + ' viewer page on stone: ' + stone);
      console.log('WR-CHECK ' + (flint != null && diamond != null ? 'PASS' : 'FAIL') + ' viewer pages on flint and diamond');
    }
    if (ticks === 420) {
      console.log('WR-TOUR clean client exit');
      mc().stop();
    }
  });
})();
