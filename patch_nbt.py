import gzip
import struct

filename = "d:/Minecraft/allmod/TC_world_reborn_1.21.1_1.7.3/src/main/resources/data/tc_reborn/structure/magikarp_dragon_evolution_lake.nbt"

with gzip.open(filename, "rb") as f:
    data = f.read()

def patch_nbt(data):
    search_bytes = b"tcpoke_reborn:"
    replace_bytes = b"tc_reborn:"
    
    idx = 0
    while True:
        idx = data.find(search_bytes, idx)
        if idx == -1:
            break
            
        length_bytes = data[idx-2:idx]
        length = struct.unpack(">H", length_bytes)[0]
        
        if length >= len(search_bytes) and length < 256:
            new_length = length - 4
            new_length_bytes = struct.pack(">H", new_length)
            
            data = data[:idx-2] + new_length_bytes + replace_bytes + data[idx+len(search_bytes):]
            idx += len(replace_bytes)
        else:
            idx += 1
            
    return data

patched_data = patch_nbt(data)

with gzip.open(filename, "wb") as f:
    f.write(patched_data)

print("NBT file successfully patched!")
