// Development only, client half of the namespace tour: checks that the language entries of both namespaces resolve, takes the frame
// and exits cleanly.
(function () {
  const mc = () => Java.loadClass('net.minecraft.client.Minecraft').getInstance();
  let ticks = -1;
  let waiting = 0;
  Client.skipExperimentalWarning();
  Client.hideToasts('all');
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) {
      if (++waiting === 700) Client.screenshot('ns_stall.png');
      if (waiting % 100 === 0) {
        let screen = null;
        try { screen = mc().gui.screen(); } catch (err) { screen = mc().screen; }
        if (screen != null) {
          console.log('NS-CHECK waiting screen ' + screen);
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
    if (ticks === 200) {
      const Component = Java.loadClass('net.minecraft.network.chat.Component');
      const lang = key => String(Component.translatable(key).getString());
      const checks = [['item.nsdemo.gem', 'Pack Gem'], ['item.modjs.gem', 'Modjs Gem'], ['block.nsdemo.crystal', 'Pack Crystal'],
        ['block.modjs.crystal', 'Modjs Crystal'], ['item.nsdemo.shard', 'Pack Shard']];
      checks.forEach(pair => console.log('NS-CHECK ' + (lang(pair[0]) === pair[1] ? 'PASS' : 'FAIL') + ' lang ' + pair[0] + ' = ' + lang(pair[0])));
      Client.screenshot('ns_world.png');
      console.log('NS-CHECK ok frame');
    }
    if (ticks === 280) {
      console.log('NS-TOUR clean client exit');
      mc().stop();
    }
  });
})();
