"""One real-client BEI/ModJS compatibility spot test. Pass the matching BEI jar."""
import sys,time,shutil
from pathlib import Path
import restart_tour as runner
ROOT=Path(__file__).resolve().parent.parent
SCRIPT=r'''
(function(){
 const J=n=>Java.loadClass(n);
 const check=(name,ok)=>console.log('BEI-CHECK '+(ok?'PASS ':'FAIL ')+name);
 RecipeViewerEvents.hideItems(e=>e.hide('minecraft:stone'));
 RecipeViewerEvents.hideCategories(e=>e.hide('minecraft:smelting'));
 RecipeViewerEvents.addInformation(e=>e.add('minecraft:diamond','BEI-CHECK diamond information'));
 RecipeViewerEvents.groupItems(e=>e.group('Spot gems',['minecraft:diamond','minecraft:emerald'],{icon:'minecraft:diamond'}));
 let ticks=-1;
 ClientEvents.loggedIn(()=>{ticks=0;});
 ClientEvents.tick(()=>{
  if(ticks<0)return;
  ticks++;
  if(ticks===180){try{
   const compat=J('com.itmecasey.bei.compat.ModJsCompat');
   const items=J('net.minecraft.world.item.Items');
   const entries=J('com.itmecasey.bei.index.ItemIndex').entries();
   const groups=J('com.itmecasey.bei.client.Groups').resolve(entries);
   check('stone hidden',groups.hidden().contains(items.STONE));
   check('diamond visible',!groups.hidden().contains(items.DIAMOND));
   const group=groups.groupOf().get(items.DIAMOND);
   check('grouping',group!=null&&String(group.name)==='Spot gems'&&group.members.size()===2);
   const info=compat.infoRecipes();
   check('information registered',info.size()===1&&info.get(0).outputs().get(0).getItem()===items.DIAMOND);
   const recipes=J('com.itmecasey.bei.index.RecipeIndex').producing(items.IRON_INGOT);
   let smelting=false;
   for(let i=0;i<recipes.size();i++){let r=recipes.get(i);if(String(r.recipeType())==='minecraft:smelting')smelting=true;}
   check('smelting category hidden',recipes.size()>0&&!smelting&&compat.hiddenCategory(recipes.get(0).withRecipeType('minecraft:smelting')));
   J('com.itmecasey.bei.client.RecipePopup').open(items.DIAMOND,false);
  }catch(err){console.log('BEI-CHECK FAIL driver '+err);}}
  if(ticks===210)Client.screenshot('bei_check.png');
 });
})();
'''
if __name__=='__main__':
 jar=Path(sys.argv[1]).resolve()
 assert jar.is_file()
 module='forge-26.1.2'
 init=ROOT/'release/bei-spot.gradle'
 init.write_text("allprojects { plugins.withId('java') { dependencies.add('runtimeOnly', files('"+jar.as_posix()+"')) } }\n",encoding='utf-8')
 runner.WORLD='modjs_bei_'+str(time.time_ns())
 runner.EVIDENCE='client-bei'
 runner.tour(module, stages=(1,),extra_client=SCRIPT,flags='-I "'+str(init)+'"')
 log=ROOT/'docs/testing/client-bei-forge-26.1.2-1.log'
 # The common runner retains its own markers; capture BEI assertions from the actual game log too.
 text=(ROOT/module/'run/logs/latest.log').read_text(errors='replace')
 lines=[line.rstrip() for line in text.splitlines() if 'BEI-CHECK' in line]
 (ROOT/'docs/testing/client-bei-forge-26.1.2.log').write_text('\n'.join(lines)+'\n',encoding='utf-8')
 for name in ('stone hidden','diamond visible','grouping','information registered','smelting category hidden'):
  assert 'BEI-CHECK PASS '+name in text,name
 shutil.copy2(ROOT/module/'run/screenshots/bei_check.png',ROOT/'docs/frames/bei-forge-26.1.2.png')
 print('PASS BEI spot test: all four script events, including grouping',flush=True)
