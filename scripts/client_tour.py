"""Run real client-event checks and optional JEI, EMI or REI checks using the restart tour's isolation and restoration."""
import shutil
import sys
import time
from pathlib import Path
import restart_tour as runner

ROOT = Path(__file__).resolve().parent.parent
HUD = r'''
(function () {
  let seen = false, ticks = -1;
  ClientEvents.hud(e => {
    e.fill(8, 8, 200, 30, 0xCC182432);
    e.text('ModJS scripted HUD', 14, 14, 0xFFFFCC44);
    e.text('Width: ' + e.width + ' Height: ' + e.height, 14, 26);
    if (!seen) { seen = true; console.log('CLIENT-CHECK ok hud'); }
  });
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => { if (ticks >= 0 && ++ticks === 100) Client.screenshot('hud_check.png'); });
})();
'''
KEYS = r'''
(function () {
  const J = name => Java.loadClass(name);
  const key = code => {
    try { return J('com.mojang.blaze3d.platform.InputConstants$Type').KEYSYM.getOrCreate(code); }
    catch (err) { return J('com.mojang.blaze3d.platform.InputConstants$Type').KEYBOARD.getOrCreate(code); }
  };
  let ticks = -1, presses = 0;
  ClientEvents.keyBindings(e => e.register('modjs:tour_key', 71, 'ModJS test key'));
  ClientEvents.keyBindingPressed('modjs:tour_key', e => { if (e.id === 'modjs:tour_key') presses++; });
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) return;
    ticks++;
    try {
      if (ticks === 140) {
        const binding = J('com.itmecasey.modjs.client.ScriptedKeys').get('modjs:tour_key');
        if (binding == null) { console.log('CLIENT-CHECK driver keyBindings not registered'); return; }
        console.log('CLIENT-CHECK ok keyBindings');
        binding.setKey(key(72));
        J('net.minecraft.client.KeyMapping').resetMapping();
        J('net.minecraft.client.KeyMapping').click(key(72));
      }
      if (ticks === 150 && presses === 1) {
        console.log('CLIENT-CHECK ok keyBindingRebound');
        const reboundBinding = J('com.itmecasey.modjs.client.ScriptedKeys').get('modjs:tour_key');
        reboundBinding.setKey(key(71));
        J('net.minecraft.client.KeyMapping').resetMapping();
        J('net.minecraft.client.KeyMapping').click(key(71));
      }
      if (ticks === 165 && presses === 2) console.log('CLIENT-CHECK ok keyBindingPressed');
    } catch (err) { console.log('CLIENT-CHECK driver keyBindings ' + err); }
  });
})();
'''
JEI = r'''
(function () {
  const J = name => Java.loadClass(name);
  const check = (name, ok) => console.log('JEI-CHECK ' + (ok ? 'PASS ' : 'FAIL ') + name);
  RecipeViewerEvents.hideItems(e => e.hide('minecraft:stone'));
  RecipeViewerEvents.addInformation(e => e.add('minecraft:diamond', 'JEI-CHECK scripted diamond information'));
  RecipeViewerEvents.hideCategories(e => e.hide('minecraft:smelting'));
  let ticks = -1;
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) return;
    ticks++;
    if (ticks === 180) {
      try {
        let runtime;
        try { runtime = J('mezz.jei.common.Internal').getJeiRuntime(); }
        catch (err) { runtime = J('com.itmecasey.modjs.compat.jei.ModJSJeiPlugin').runtime(); }
        const types = J('mezz.jei.api.constants.VanillaTypes');
        const items = J('net.minecraft.world.item.Items');
        const Stack = J('net.minecraft.world.item.ItemStack');
        const registered = runtime.getIngredientManager().getAllIngredients(types.ITEM_STACK);
        const helper = runtime.getIngredientManager().getIngredientHelper(types.ITEM_STACK);
        console.log('JEI-CHECK registered item count ' + registered.size());
        let stone = false, diamond = false;
        const iterator = registered.iterator();
        while (iterator.hasNext()) {
          let stack = iterator.next();
          let id = String(helper.getResourceLocation(stack));
          if (id === 'minecraft:stone') stone = true;
          if (id === 'minecraft:diamond') diamond = true;
        }
        check('stone hidden', !stone);
        check('diamond visible', diamond);
        const manager = runtime.getRecipeManager();
        const visible = manager.createRecipeCategoryLookup().get().toList();
        const all = manager.createRecipeCategoryLookup().includeHidden().get().toList();
        const has = (list, name) => { for (let i = 0; i < list.size(); i++) { let id = String(list.get(i).getRecipeType().getUid()); if (id === name || (name === 'minecraft:smelting' && id === 'minecraft:furnace')) return true; } return false; };
        check('smelting category hidden', !has(visible, 'minecraft:smelting') && has(all, 'minecraft:smelting'));
        const infoType = J('mezz.jei.api.constants.RecipeTypes').INFORMATION;
        const infos = manager.createRecipeLookup(infoType).get().toList();
        let found = null;
        for (let i = 0; i < infos.size(); i++) {
          let descriptions = infos.get(i).getDescription();
          let description = '';
          for (let n = 0; n < descriptions.size(); n++) description += String(descriptions.get(n).getString()) + ' ';
          if (description.replace(/\s+/g, ' ').indexOf('JEI-CHECK scripted diamond information') >= 0) found = infos.get(i);
        }
        check('information registered', found != null);
        if (found != null) runtime.getRecipesGui().showRecipes(manager.getRecipeCategory(infoType), J('java.util.List').of(found), J('java.util.List').of());
      } catch (err) { console.log('JEI-CHECK FAIL driver ' + err); }
    }
    if (ticks === 210) Client.screenshot('jei_check.png');
  });
})();
'''

EMI = r'''
(function () {
  const J = name => Java.loadClass(name);
  const check = (name, ok) => console.log('EMI-CHECK ' + (ok ? 'PASS ' : 'FAIL ') + name);
  RecipeViewerEvents.hideItems(e => e.hide('minecraft:stone'));
  RecipeViewerEvents.addInformation(e => e.add('minecraft:diamond', 'EMI-CHECK scripted diamond information'));
  RecipeViewerEvents.hideCategories(e => e.hide('minecraft:smelting'));
  let ticks = -1;
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) return;
    ticks++;
    if (ticks === 300) {
      try {
        const api = J('dev.emi.emi.api.EmiApi');
        const stacks = api.getIndexStacks();
        let stone = false, diamond = false;
        for (let i = 0; i < stacks.size(); i++) {
          let id = String(stacks.get(i).getId());
          if (id === 'minecraft:stone') stone = true;
          if (id === 'minecraft:diamond') diamond = true;
        }
        check('stone hidden', !stone);
        check('diamond visible', diamond);
        const recipes = api.getRecipeManager().getRecipes();
        let smelting = false, info = null;
        for (let i = 0; i < recipes.size(); i++) {
          let recipe = recipes.get(i);
          if (String(recipe.getCategory().getId()) === 'minecraft:smelting') smelting = true;
          if (String(recipe.getId()) === 'modjs:information/0') info = recipe;
        }
        check('smelting category hidden', !smelting);
        check('information registered', info != null);
        if (info != null) api.displayRecipe(info);
      } catch (err) { console.log('EMI-CHECK FAIL driver ' + err); }
    }
    if (ticks === 340) Client.screenshot('emi_check.png');
  });
})();
'''

REI = r'''
(function () {
  const J = name => Java.loadClass(name);
  const check = (name, ok) => console.log('REI-CHECK ' + (ok ? 'PASS ' : 'FAIL ') + name);
  RecipeViewerEvents.hideItems(e => e.hide('minecraft:stone'));
  RecipeViewerEvents.addInformation(e => e.add('minecraft:diamond', 'REI-CHECK scripted diamond information'));
  RecipeViewerEvents.hideCategories(e => e.hide('minecraft:smelting'));
  let ticks = -1;
  ClientEvents.loggedIn(() => { ticks = 0; });
  ClientEvents.tick(() => {
    if (ticks < 0) return;
    ticks++;
    if (ticks === 300) {
      try {
        const entries = J('me.shedaniel.rei.api.client.registry.entry.EntryRegistry').getInstance().getEntryStacks().toList();
        let stone = false, diamond = false;
        for (let i = 0; i < entries.size(); i++) {
          let id = String(entries.get(i).getIdentifier());
          if (id === 'minecraft:stone') stone = true;
          if (id === 'minecraft:diamond') diamond = true;
        }
        check('stone hidden', !stone);
        check('diamond visible', diamond);
        const categories = J('me.shedaniel.rei.api.client.registry.category.CategoryRegistry').getInstance();
        const configs = categories.stream().toList();
        let hidden = false;
        for (let i = 0; i < configs.size(); i++) {
          let category = configs.get(i).getCategory();
          let id = String(category.getCategoryIdentifier().getIdentifier());
          if (id === 'minecraft:smelting' || id === 'minecraft:plugins/smelting') hidden = !categories.isCategoryVisible(category);
        }
        check('smelting category hidden', hidden);
        const all = J('me.shedaniel.rei.api.client.registry.display.DisplayRegistry').getInstance().getAll();
        const lists = all.values().iterator();
        let info = null;
        while (lists.hasNext()) {
          let displays = lists.next();
          for (let i = 0; i < displays.size(); i++) {
            let display = displays.get(i);
            try {
              let lines = display.getTexts();
              for (let n = 0; n < lines.size(); n++) {
                if (String(lines.get(n).getString()).indexOf('REI-CHECK scripted diamond information') >= 0) info = display;
              }
            } catch (err) {}
          }
        }
        check('information registered', info != null);
        if (info != null) J('me.shedaniel.rei.api.client.view.ViewSearchBuilder').builder().addCategory(info.getCategoryIdentifier()).open();
      } catch (err) { console.log('REI-CHECK FAIL driver ' + err); }
    }
    if (ticks === 340) Client.screenshot('rei_check.png');
  });
})();
'''

def main(module, jei=False, restart=False, viewer=None):
    viewer = viewer or ('jei' if jei else None)
    runner.WORLD = 'modjs_client_' + str(time.time_ns())
    runner.EVIDENCE = 'restart-client' if restart else 'client'
    if viewer:
        runner.EVIDENCE += '-' + viewer
    extra = (ROOT / 'examples/client/tour/client_events_check.js').read_text(encoding='utf-8')
    options = ROOT / module / 'run/options.txt'
    options.write_text(options.read_text(encoding='utf-8').replace('chatVisibility:2', 'chatVisibility:0'), encoding='utf-8')
    if viewer:
        runner.CLIENT = runner.CLIENT.replace('ticks === 240', 'ticks === 420')
    viewer_script = {'jei': JEI, 'emi': EMI, 'rei': REI}.get(viewer, '')
    runner.tour(module, stages=(1, 2) if restart else (1,), extra_client=extra + HUD + KEYS + viewer_script, flags=f'-P{viewer}Tour' if viewer else '')
    log = '\n'.join((ROOT / 'docs/testing' / f'{runner.EVIDENCE}-{module}-{stage}.log').read_text(encoding='utf-8') for stage in ((1, 2) if restart else (1,)))
    dest = ROOT / 'docs/testing' / f'{runner.EVIDENCE}-{module}.log'
    dest.write_text(log, encoding='utf-8')
    required = ['init', 'highPriorityAssets', 'loggedIn', 'loggedOut', 'tick', 'keyPressed', 'ItemEvents.tooltip', 'ItemEvents.dynamicTooltips', 'chat-sent', 'hud', 'keyBindings', 'keyBindingRebound', 'keyBindingPressed']
    missing = [token for token in required if f'CLIENT-CHECK ok {token}' not in log]
    if 'RESTART-CHECK EVENT chat ' not in log:
        missing.append('server chat receipt')
    hud_shot = ROOT / module / 'run/screenshots/hud_check.png'
    if hud_shot.exists():
        shutil.copy2(hud_shot, ROOT / 'docs/frames' / f'hud-{module}.png')
    else:
        missing.append('HUD screenshot')
    if viewer:
        for token in ('stone hidden', 'diamond visible', 'smelting category hidden', 'information registered'):
            if f'{viewer.upper()}-CHECK PASS {token}' not in log:
                missing.append(token)
        shot = ROOT / module / f'run/screenshots/{viewer}_check.png'
        if shot.exists():
            shutil.copy2(shot, ROOT / 'docs/frames' / f'{viewer}-{module}.png')
        else:
            missing.append(f'{viewer} screenshot')
    if missing:
        raise RuntimeError(f'{module}: missing checks {missing}; see {dest}')
    print(f'PASS {module} client events' + (f' and {viewer.upper()}' if viewer else ''), flush=True)

if __name__ == '__main__':
    selected_viewer = next((name for name in ('jei', 'emi', 'rei') if f'--{name}' in sys.argv[2:]), None)
    main(sys.argv[1], '--jei' in sys.argv[2:], '--restart' in sys.argv[2:], selected_viewer)
