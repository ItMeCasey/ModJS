"""Prepare copied test worlds before Minecraft opens them."""
import gzip
import struct


def _edit(path, changes, vanilla_rules_only=False):
    raw = gzip.decompress(path.read_bytes())
    offset = 0
    def take(size):
        nonlocal offset
        value = raw[offset:offset + size]
        offset += size
        return value
    def number(fmt):
        return struct.unpack('>' + fmt, take(struct.calcsize('>' + fmt)))[0]
    def string():
        return take(number('H')).decode('utf-8')
    def payload(kind, parents):
        start = offset
        if kind == 10:
            result = bytearray()
            remaining = dict(changes.get(parents, {}))
            while True:
                child = number('B')
                if child == 0:
                    break
                header = offset - 1
                name = string()
                value_start = offset
                value = payload(child, parents + (name,))
                if vanilla_rules_only and parents == ('data',) and not name.startswith('minecraft:'):
                    continue
                if name in remaining:
                    replacement = remaining.pop(name)
                    if replacement is None:
                        continue
                    child, value = replacement
                    encoded = name.encode('utf-8')
                    result.extend(bytes([child]) + struct.pack('>H', len(encoded)) + encoded + value)
                else:
                    result.extend(raw[header:value_start] + value)
            for name, replacement in remaining.items():
                if replacement is not None:
                    child, value = replacement
                    encoded = name.encode('utf-8')
                    result.extend(bytes([child]) + struct.pack('>H', len(encoded)) + encoded + value)
            return bytes(result) + b'\0'
        if kind in (1, 2, 3, 4, 5, 6):
            take({1:1, 2:2, 3:4, 4:8, 5:4, 6:8}[kind])
        elif kind == 8:
            string()
        elif kind == 9:
            child, count = number('B'), number('i')
            for _ in range(count):
                payload(child, parents + ('[]',))
        elif kind in (7, 11, 12):
            take(number('i') * {7:1, 11:4, 12:8}[kind])
        else:
            raise ValueError(f'Unknown NBT tag {kind}')
        return raw[start:offset]
    kind = number('B')
    string()
    header = raw[:offset]
    result = header + payload(kind, ())
    if offset != len(raw):
        raise ValueError('Unexpected trailing NBT data')
    path.write_bytes(gzip.compress(result, mtime=0))


def peaceful_world(world, reset_player=False):
    """Set Peaceful and disable natural and special mob spawns before launch."""
    def text(value):
        encoded = value.encode('utf-8')
        return 8, struct.pack('>H', len(encoded)) + encoded
    modern = world / 'data/minecraft/game_rules.dat'
    if modern.exists():
        _edit(world / 'level.dat', {
            ('Data',): {'Player': None} if reset_player else {},
            ('Data', 'difficulty_settings'): {'difficulty': text('peaceful')},
        })
        _edit(modern, {('data',): {f'minecraft:{name}': (1, b'\0') for name in (
            'spawn_mobs', 'spawn_monsters', 'spawn_patrols', 'spawn_phantoms',
            'spawn_wandering_traders', 'spawn_wardens', 'spawner_blocks_work', 'raids')}}, vanilla_rules_only=True)
    else:
        data = {'Difficulty': (1, b'\0')}
        if reset_player:
            data['Player'] = None
        _edit(world / 'level.dat', {
            ('Data',): data,
            ('Data', 'GameRules'): {name: text('false') for name in (
                'doMobSpawning', 'doPatrolSpawning', 'doTraderSpawning',
                'doInsomnia', 'doWardenSpawning')} | {'disableRaids': text('true')},
        })
