// ModJS type declarations. This file is regenerated on every launch; do not edit it.
// Add a jsconfig.json next to your scripts (ModJS creates one) and your editor will offer completions.

type Ingredient = string | string[] | ItemRef | { item: string } | { tag: string };
type ItemLike = string | ItemRef | { item?: string; id?: string; count?: number };
type Handler<E> = (event: E) => void;

interface ItemRef {
  id(): string;
  count(): number;
}

interface EventBase {
  cancel(): void;
  isCancelled(): boolean;
}

interface RecipeBuilder {
  id(id: string): RecipeBuilder;
  group(group: string): RecipeBuilder;
  category(category: string): RecipeBuilder;
  xp(experience: number): RecipeBuilder;
  cookingTime(ticks: number): RecipeBuilder;
}

interface RecipeFilter {
  id?: string;
  mod?: string;
  type?: string;
  group?: string;
  output?: ItemLike;
  input?: Ingredient;
  not?: RecipeFilter | string;
  or?: RecipeFilter | RecipeFilter[] | string;
}

interface RecipesEvent extends EventBase {
  shaped(result: ItemLike, pattern: string[], key: Record<string, Ingredient>): RecipeBuilder;
  shapeless(result: ItemLike, ingredients: Ingredient[]): RecipeBuilder;
  smelting(result: ItemLike, ingredient: Ingredient): RecipeBuilder;
  blasting(result: ItemLike, ingredient: Ingredient): RecipeBuilder;
  smoking(result: ItemLike, ingredient: Ingredient): RecipeBuilder;
  campfireCooking(result: ItemLike, ingredient: Ingredient): RecipeBuilder;
  stonecutting(result: ItemLike, ingredient: Ingredient): RecipeBuilder;
  smithing(result: ItemLike, template: Ingredient, base: Ingredient, addition: Ingredient): RecipeBuilder;
  custom(json: object): RecipeBuilder;
  remove(filter: RecipeFilter | RecipeFilter[] | string): void;
  replaceInput(filter: RecipeFilter | RecipeFilter[] | string, from: string, to: string): void;
  replaceOutput(filter: RecipeFilter | RecipeFilter[] | string, from: ItemLike, to: ItemLike): void;
}

interface TagsEvent extends EventBase {
  registry: string;
  add(tag: string, entries: string | string[]): TagsEvent;
  remove(tag: string, entries: string | string[]): TagsEvent;
  removeAll(tag: string): TagsEvent;
}

interface DataEvent extends EventBase {
  addJson(id: string, json: object): void;
  addText(id: string, text: string): void;
  exists(id: string): boolean;
}

interface LangEvent extends EventBase {
  language: string;
  add(key: string, value: string): void;
}

interface LootPool {
  rolls(value: number | [number, number]): LootPool;
  addItem(item: ItemLike, count?: number | [number, number], weight?: number): LootPool;
  addEntry(json: object): LootPool;
  addCondition(json: object): LootPool;
  survivesExplosion(): LootPool;
  killedByPlayer(): LootPool;
  randomChance(chance: number): LootPool;
}

interface LootTable {
  addPool(configure: (pool: LootPool) => void): LootTable;
  addFunction(json: object): LootTable;
}

interface LootEvent extends EventBase {
  add(id: string, configure: (table: LootTable) => void): void;
  addSimple(id: string, drop: ItemLike): void;
  addSimpleBlock(block: string, drop: ItemLike): void;
  clear(id: string): void;
  addJson(id: string, json: object): void;
}

interface OreOptions {
  id: string;
  block: string;
  size?: number;
  count?: number;
  minY?: number;
  maxY?: number;
  replaces?: string;
  biomes?: string | string[];
  step?: string;
}

interface WorldgenEvent extends EventBase {
  addOre(options: OreOptions): void;
  addFeature(options: { id: string; features: string | string[]; biomes?: string | string[]; step?: string }): void;
  removeFeature(options: { id: string; features: string | string[]; biomes?: string | string[] }): void;
  addSpawn(options: { id: string; entity: string; weight?: number; minCount?: number; maxCount?: number; biomes?: string | string[] }): void;
  removeSpawn(options: { id: string; entities: string | string[]; biomes?: string | string[] }): void;
}

interface ItemBuilder {
  displayName(name: string): ItemBuilder;
  maxStackSize(size: number): ItemBuilder;
  unstackable(): ItemBuilder;
  texture(texture: string): ItemBuilder;
  parentModel(model: string): ItemBuilder;
  tab(tab: string): ItemBuilder;
  rarity(rarity: 'common' | 'uncommon' | 'rare' | 'epic'): ItemBuilder;
  glow(glow: boolean): ItemBuilder;
  food(nutrition: number, saturation: number): ItemBuilder;
  alwaysEdible(): ItemBuilder;
  tooltip(line: string): ItemBuilder;
}

interface BlockBuilder {
  displayName(name: string): BlockBuilder;
  hardness(hardness: number): BlockBuilder;
  resistance(resistance: number): BlockBuilder;
  lightLevel(level: number): BlockBuilder;
  requiresTool(requiresTool: boolean): BlockBuilder;
  texture(texture: string): BlockBuilder;
  tab(tab: string): BlockBuilder;
  noItem(): BlockBuilder;
  dropsNothing(): BlockBuilder;
  /** A texture per face; the most specific key wins (north over side over all). front is north, end is top and bottom. */
  textures(faces: { all?: string; top?: string; bottom?: string; side?: string; north?: string; south?: string; east?: string; west?: string; front?: string; end?: string }): BlockBuilder;
  /** cube_all, cube, cube_column, cube_bottom_top, orientable (turns to face the player), or a model id such as 'mymod:block/thing'. */
  model(model: string): BlockBuilder;
  /** A block entity with slots (a count, or positions from the top left of the screen), a stack limit and named synced numbers. */
  inventory(options: { slots: number | { x: number; y: number }[]; stackLimit?: number; values?: string[] }): BlockBuilder;
  /** A block entity with a data map and no slots. */
  entity(): BlockBuilder;
  /** Runs on the server every interval ticks (default 1). */
  onTick(interval: number, handler: (be: ScriptBlockEntity) => void): BlockBuilder;
  onTick(handler: (be: ScriptBlockEntity) => void): BlockBuilder;
  /** Runs on the server when a screen button with an id is pressed. */
  onButton(handler: (id: string, be: ScriptBlockEntity, player: MinecraftObject) => void): BlockBuilder;
  /** Describes the screen: text, bar, button (with an id), background, size. Positions count from the panel's top left. */
  screen(define: (screen: BlockScreen) => void): BlockBuilder;
}

/** The block entity a handler of a scripted block receives. */
interface ScriptBlockEntity {
  readonly x: number;
  readonly y: number;
  readonly z: number;
  readonly blockId: string;
  readonly size: number;
  readonly clientSide: boolean;
  getItemId(slot: number): string;
  getCount(slot: number): number;
  setItem(slot: number, itemId: string, count: number): void;
  clearSlot(slot: number): void;
  /** Takes up to amount from a slot and returns how many were taken. */
  shrink(slot: number, amount: number): number;
  /** Puts items into the first slots that take them and returns how many did not fit. */
  insert(itemId: string, count: number): number;
  getData(key: string): number | string | boolean | null;
  setData(key: string, value: number | string | boolean | null): void;
  getValue(name: string): number;
  setValue(name: string, value: number): void;
  markChanged(): void;
  sync(): void;
}

interface BlockScreen {
  size(width: number, height: number): void;
  background(colorOrTexture: number | string): void;
  text(text: string, options?: { x?: number; y?: number; color?: number; left?: boolean }): void;
  bar(options: { value: string | number; max?: string | number; x: number; y: number; width?: number; height?: number; color?: number; background?: number }): void;
  button(label: string, options: { id?: string; x?: number; y?: number; width?: number; height?: number }): void;
}

/** One element of a world overlay: text, item, bar or texture. Sizes are in blocks. */
interface WorldOverlayElement {
  [option: string]: any;
  animate(properties: Record<string, number>, milliseconds: number, easing?: string): WorldOverlayElement;
  fadeIn(milliseconds: number): WorldOverlayElement;
  fadeOut(milliseconds: number): WorldOverlayElement;
  pulse(periodMs: number, minAlpha?: number, maxAlpha?: number): WorldOverlayElement;
}

interface WorldOverlayCommon {
  x?: number;
  y?: number;
  z?: number;
  /** billboard (default), up, down, north, south, east or west. */
  face?: string;
  scale?: number;
  distance?: number;
  layer?: number;
  alpha?: number;
  visible?: boolean;
}

/** The overlay of one block position. */
interface WorldOverlayAt {
  readonly x: number;
  readonly y: number;
  readonly z: number;
  readonly size: number;
  /** The synced data of the scripted block here. */
  readonly data: Record<string, any>;
  text(id: string, options?: WorldOverlayCommon & { text?: string; color?: number | string; shadow?: boolean; background?: number | string }): WorldOverlayElement;
  item(id: string, options?: WorldOverlayCommon & { item?: string }): WorldOverlayElement;
  bar(id: string, options?: WorldOverlayCommon & { value?: number; max?: number; width?: number; height?: number; color?: number | string; backgroundColor?: number | string }): WorldOverlayElement;
  texture(id: string, options?: WorldOverlayCommon & { texture?: string; width?: number; height?: number; color?: number | string }): WorldOverlayElement;
  get(id: string): WorldOverlayElement | null;
  has(id: string): boolean;
  remove(id: string): void;
  clear(): void;
}

declare const Overlay: {
  /** The overlay of a block position. */
  at(x: number, y: number, z: number): WorldOverlayAt;
  at(pos: { x: number; y: number; z: number }): WorldOverlayAt;
  /** The synced data of the scripted block at a position. */
  data(x: number, y: number, z: number): Record<string, any>;
  /** Runs when a block of this id comes near and again when its synced data changes; at is cleared before each run. */
  forBlock(blockId: string, template: (at: WorldOverlayAt, data: Record<string, any>, pos: { x: number; y: number; z: number }) => void): void;
  /** Limits for world overlays. */
  world: { maxDistance: number; maxElements: number; readonly positions: number };
  /** The screen overlay (bar, text, rect, image, item, get, has, remove, clear). */
  [screenOverlay: string]: any;
};

interface RegistryEvent<B> extends EventBase {
  /** 'ruby' registers modjs:ruby; 'mypack:ruby' registers mypack:ruby (lowercase letters, digits, _ . - in the namespace; not 'minecraft'). */
  create(id: string): B;
  /** Plain ids in this handler go to this namespace from now on. */
  namespace(namespace: string): RegistryEvent<B>;
}

/** An in-world recipe from ServerEvents.worldRecipes; the methods chain. Blocks and items are ids, '#tags', and blocks may carry state values: 'minecraft:cauldron[level=3]'. */
interface WorldRecipe {
  id(id: string): WorldRecipe;
  /** true: the player must sneak; false: must not. */
  sneaking(sneaking: boolean): WorldRecipe;
  /** Which hand holds the item for a click: main (default), off or any. */
  hand(hand: 'main' | 'off' | 'any'): WorldRecipe;
  /** How many of the held item (or the dropped items) are used up; 0 keeps them. */
  consume(count: number): WorldRecipe;
  /** Wears the held tool down. */
  damage(amount: number): WorldRecipe;
  /** Chance 0 to 1 that the results happen; the items are used up either way. */
  chance(chance: number): WorldRecipe;
  turnInto(block: string): WorldRecipe;
  breakBlock(): WorldRecipe;
  drop(itemId: string, count?: number): WorldRecipe;
  give(itemId: string, count?: number): WorldRecipe;
  command(command: string): WorldRecipe;
  /** blockBreak: the tool the player must hold. */
  tool(matcher: string): WorldRecipe;
  /** blockBreak: only this recipe's drops. */
  replaceDrops(): WorldRecipe;
  /** lightning: dropped items needed within three blocks. */
  item(matcher: string, count?: number): WorldRecipe;
}

type WorldRecipeItem = string | { item: string; count?: number };

interface WorldRecipesEvent extends EventBase {
  itemOnBlock(block: string, item?: WorldRecipeItem): WorldRecipe;
  leftClickBlock(block: string, item?: WorldRecipeItem): WorldRecipe;
  itemsInBlock(block: string, items: WorldRecipeItem[]): WorldRecipe;
  itemsOnBlock(block: string, items: WorldRecipeItem[]): WorldRecipe;
  anvil(block: string): WorldRecipe;
  lightning(block: string): WorldRecipe;
  randomTick(block: string): WorldRecipe;
  blockBreak(block: string): WorldRecipe;
}

/** Minecraft objects are passed through as-is; see the Minecraft source for their methods. */
type MinecraftObject = any;

interface GameEvent extends EventBase {
  player: MinecraftObject;
  playerName: string;
  level: MinecraftObject;
  server: MinecraftObject;
  entity: MinecraftObject;
  entityId: string;
  item: MinecraftObject;
  itemId: string;
  blockState: MinecraftObject;
  blockId: string;
  pos: MinecraftObject;
  x: number;
  y: number;
  z: number;
  hand: string;
  face: string;
  damageType: string;
  message: string;
  amount: number;
  tell(text: string): void;
  runCommand(command: string): void;
}

interface TooltipEvent extends EventBase {
  item: MinecraftObject;
  itemId: string;
  add(items: string | string[], text: string | MinecraftObject | (string | MinecraftObject)[]): void;
}

interface ClientEvent extends EventBase {
  key: number;
  action: number;
}

interface CommandRegistryEvent extends EventBase {
  register(command: MinecraftObject): void;
  dispatcher: MinecraftObject;
}

interface ScheduledTask {
  cancel(): void;
  isCancelled(): boolean;
}

interface CreativeTabBuilder {
  displayName(name: string): CreativeTabBuilder;
  icon(itemId: string): CreativeTabBuilder;
}

declare const StartupEvents: {
  init(handler: Handler<EventBase>): void;
  postInit(handler: Handler<EventBase>): void;
  registry(type: 'creative_tab', handler: Handler<RegistryEvent<CreativeTabBuilder>>): void;
  registry(type: 'item', handler: Handler<RegistryEvent<ItemBuilder>>): void;
  registry(type: 'block', handler: Handler<RegistryEvent<BlockBuilder>>): void;
};

declare const ServerEvents: {
  recipes(handler: Handler<RecipesEvent>): void;
  /** In-world crafting: item on block, items in water, anvil, lightning, random tick, block break. */
  worldRecipes(handler: Handler<WorldRecipesEvent>): void;
  tags(registry: string, handler: Handler<TagsEvent>): void;
  highPriorityData(handler: Handler<DataEvent>): void;
  generateData(handler: Handler<DataEvent>): void;
  blockLootTables(handler: Handler<LootEvent>): void;
  entityLootTables(handler: Handler<LootEvent>): void;
  chestLootTables(handler: Handler<LootEvent>): void;
  genericLootTables(handler: Handler<LootEvent>): void;
  commandRegistry(handler: Handler<CommandRegistryEvent>): void;
  afterRecipes(handler: Handler<EventBase>): void;
  loaded(handler: Handler<GameEvent>): void;
  unloaded(handler: Handler<GameEvent>): void;
  tick(handler: Handler<GameEvent>): void;
};

declare const WorldgenEvents: {
  add(handler: Handler<WorldgenEvent>): void;
};

declare const BlockEvents: {
  broken(handler: Handler<GameEvent>): void;
  broken(blockId: string, handler: Handler<GameEvent>): void;
  placed(handler: Handler<GameEvent>): void;
  placed(blockId: string, handler: Handler<GameEvent>): void;
  rightClicked(handler: Handler<GameEvent>): void;
  rightClicked(blockId: string, handler: Handler<GameEvent>): void;
  leftClicked(handler: Handler<GameEvent>): void;
  leftClicked(blockId: string, handler: Handler<GameEvent>): void;
};

interface ItemChanges {
  maxStackSize: number;
  maxDamage: number;
  rarity: 'common' | 'uncommon' | 'rare' | 'epic';
}

interface ItemModificationEvent extends EventBase {
  modify(id: string | string[], change: (item: ItemChanges) => void): void;
}

declare const ItemEvents: {
  /** Startup scripts only: change properties of existing items. */
  modification(handler: Handler<ItemModificationEvent>): void;
  rightClicked(handler: Handler<GameEvent>): void;
  rightClicked(itemId: string, handler: Handler<GameEvent>): void;
  entityInteracted(handler: Handler<GameEvent>): void;
  entityInteracted(itemId: string, handler: Handler<GameEvent>): void;
  crafted(handler: Handler<GameEvent>): void;
  crafted(itemId: string, handler: Handler<GameEvent>): void;
  smelted(handler: Handler<GameEvent>): void;
  smelted(itemId: string, handler: Handler<GameEvent>): void;
  foodEaten(handler: Handler<GameEvent>): void;
  foodEaten(itemId: string, handler: Handler<GameEvent>): void;
  dropped(handler: Handler<GameEvent>): void;
  dropped(itemId: string, handler: Handler<GameEvent>): void;
  pickedUp(handler: Handler<GameEvent>): void;
  pickedUp(itemId: string, handler: Handler<GameEvent>): void;
  /** Client scripts only. */
  tooltip(handler: Handler<TooltipEvent>): void;
};

declare const PlayerEvents: {
  loggedIn(handler: Handler<GameEvent>): void;
  loggedOut(handler: Handler<GameEvent>): void;
  respawned(handler: Handler<GameEvent>): void;
  tick(handler: Handler<GameEvent>): void;
  chat(handler: Handler<GameEvent>): void;
  death(handler: Handler<GameEvent>): void;
  advancement(handler: Handler<GameEvent>): void;
  advancement(advancementId: string, handler: Handler<GameEvent>): void;
  inventoryOpened(handler: Handler<GameEvent>): void;
  inventoryClosed(handler: Handler<GameEvent>): void;
};

declare const EntityEvents: {
  spawned(handler: Handler<GameEvent>): void;
  spawned(entityId: string, handler: Handler<GameEvent>): void;
  death(handler: Handler<GameEvent>): void;
  death(entityId: string, handler: Handler<GameEvent>): void;
  hurt(handler: Handler<GameEvent>): void;
  hurt(entityId: string, handler: Handler<GameEvent>): void;
};

declare const LevelEvents: {
  tick(handler: Handler<GameEvent>): void;
  tick(dimensionId: string, handler: Handler<GameEvent>): void;
  loaded(handler: Handler<GameEvent>): void;
  loaded(dimensionId: string, handler: Handler<GameEvent>): void;
  unloaded(handler: Handler<GameEvent>): void;
  unloaded(dimensionId: string, handler: Handler<GameEvent>): void;
  beforeExplosion(handler: Handler<GameEvent>): void;
  afterExplosion(handler: Handler<GameEvent>): void;
};

/** One HUD frame; positions and sizes are scaled GUI pixels, colors are ARGB. */
interface HudEvent extends EventBase {
  readonly width: number;
  readonly height: number;
  text(text: string, x: number, y: number, color?: number): void;
  fill(x: number, y: number, width: number, height: number, color: number): void;
}

interface KeyBindingEvent extends EventBase {
  register(id: string, defaultKey: number, name?: string): void;
}
interface KeyBindingPressedEvent extends EventBase { readonly id: string; }

declare const ClientEvents: {
  keyBindings(handler: Handler<KeyBindingEvent>): void;
  keyBindingPressed(handler: Handler<KeyBindingPressedEvent>): void;
  keyBindingPressed(id: string, handler: Handler<KeyBindingPressedEvent>): void;
  hud(handler: Handler<HudEvent>): void;
  lang(language: string, handler: Handler<LangEvent>): void;
  highPriorityAssets(handler: Handler<DataEvent>): void;
  generateAssets(handler: Handler<DataEvent>): void;
  tick(handler: Handler<ClientEvent>): void;
  loggedIn(handler: Handler<ClientEvent>): void;
  loggedOut(handler: Handler<ClientEvent>): void;
  keyPressed(handler: Handler<ClientEvent>): void;
  keyPressed(keyCode: string, handler: Handler<ClientEvent>): void;
};

declare const console: {
  log(...args: any[]): void;
  info(...args: any[]): void;
  debug(...args: any[]): void;
  warn(...args: any[]): void;
  error(...args: any[]): void;
};

/** Data shared between startup, server and client scripts for the current game session. */
declare const global: Record<string, any>;

declare const Java: {
  loadClass(name: string): any;
  exists(name: string): boolean;
};

declare const Item: {
  of(item: ItemLike, count?: number): ItemRef;
};

declare const Ingredient: {
  of(ingredient: Ingredient): any;
  tag(tag: string): string;
};

declare const Utils: {
  server: MinecraftObject;
  runAfter(ticks: number, callback: () => void): ScheduledTask;
  runEvery(ticks: number, callback: () => void): ScheduledTask;
  randomInt(min: number, max: number): number;
  random(): number;
  rollChance(chance: number): boolean;
  runCommand(command: string): boolean;
  currentTimeMillis(): number;
};

declare const Text: {
  of(text: string): MinecraftObject;
  translate(key: string): MinecraftObject;
  color(color: string, text: string): MinecraftObject;
  red(text: string): MinecraftObject;
  green(text: string): MinecraftObject;
  yellow(text: string): MinecraftObject;
  gold(text: string): MinecraftObject;
  aqua(text: string): MinecraftObject;
  gray(text: string): MinecraftObject;
  white(text: string): MinecraftObject;
};

interface PersistentStore {
  get(key: string, fallback?: any): any;
  set(key: string, value: any): void;
  has(key: string): boolean;
  remove(key: string): void;
  keys(): string[];
}

declare const Persistent: {
  get(key: string, fallback?: any): any;
  set(key: string, value: any): void;
  has(key: string): boolean;
  remove(key: string): void;
  store(name: string): PersistentStore;
  player(uuid: string): PersistentStore;
  entity(uuid: string): PersistentStore;
};

interface NetworkEvent extends EventBase {
  channel: string;
  /** The JSON payload as a script value. */
  data: any;
  /** The sender when received on the server; null on the client. */
  player: MinecraftObject;
}

declare const NetworkEvents: {
  dataReceived(channel: string, handler: Handler<NetworkEvent>): void;
};

declare const Network: {
  /** Client scripts: send data to the server. */
  sendToServer(channel: string, data: any): void;
  /** Server scripts: send data to one player. */
  sendToPlayer(player: MinecraftObject, channel: string, data: any): void;
  /** Server scripts: send data to every connected player. */
  sendToAll(channel: string, data: any): void;
};

declare const JsonIO: {
  /** Reads a JSON file relative to the modjs folder; null when missing. */
  read(path: string): any;
  /** Writes a JSON file relative to the modjs folder. */
  write(path: string, value: any): void;
  exists(path: string): boolean;
};

declare const Platform: {
  mcVersion: string;
  loader: string;
  isClient(): boolean;
  isLoaded(modId: string): boolean;
};

declare const SECOND: 1000;
declare const MINUTE: 60000;
declare const HOUR: 3600000;
