import java.io.IOException;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

public class bot {
    public String charServerIp;
    public int charServerPort;

    public int acid;
    public int sid1;
    public int sid2;
    public int gender;

    public LuaTable character = new LuaTable();
    public LuaTable inventory = new LuaTable();
    public LuaTable equipment = new LuaTable();
    public LuaTable storage = new LuaTable();
    public LuaTable beings = new LuaTable();
    public LuaValue leader = LuaValue.NIL;

    public String mapServerIp;
    public int mapServerPort;

    public String mapName;

    boolean mapLoaded = true;
    boolean quit = false;

    Net net = new Net();

    public LuaValue createBeing(int id, int job) throws IOException {
        LuaTable being = new LuaTable();
        String type = null;
        being.set("job", job);
        if(job <=25 || (job >= 4001 && job <= 4049)) {
            type = "player";
        } else if(job >= 46 && job <= 1000) {
            type = "npc";
        } else if(job > 1000 && job <= 2000) {
            type = "monster";
        } else if(job == 45) {
            type = "portal";
        }
        being.set("type", type);

        beings.set(id, being);

        if(type.equals("player") || type.equals("npc")) {
            net.writeInt16(0x0094); // being name request
            net.writeInt32(id);
        }

        return being;
    }

    public bot() throws Exception {
        net.connect(this);

        Globals globals = JsePlatform.standardGlobals();

        globals.set("character", character);
        globals.set("inventory", inventory);
        globals.set("equipment", equipment);
        globals.set("storage", storage);
        globals.set("map_name", mapName);
        globals.set("beings", beings);
        globals.set("leader_id", LuaValue.NIL);

        Thread reader = new Thread(new Runnable() {
            public void run() {
                try {
                    while(!quit) {
                        if(mapLoaded) {
                            net.writeInt16(0x007D); // CMSG_MAP_LOADED
                            mapLoaded = false;
                        }

                        int packet = net.readPacket();
//                        System.out.append("recv packet = ");
//                        Utils.printHexInt16(packet);
//                        System.out.println();
                        switch(packet) {
                            case 0x0078: // SMSG_BEING_VISIBLE
                            case 0x007B: { // SMSG_BEING_MOVE
                                int id = net.readInt32();
                                int speed = net.readInt16();
                                int stunMode = net.readInt16();
                                int statusEffects = net.readInt16();
                                statusEffects |= net.readInt16() << 16;
                                int job = net.readInt16();

                                LuaValue being = beings.get(id);
                                if(being == LuaValue.NIL) {
                                    if(job == 0 && id >= 110000000) {
                                        net.skipPacket();
                                        break;
                                    } else {
                                        being = createBeing(id, job);
                                    }
                                }

                                being.set("id", id);
                                being.set("speed", speed);
                                being.set("stun_mode", stunMode);
                                being.set("status_effects", statusEffects);
                                being.set("hair_style", net.readInt16());
                                being.set("eq_weapon", net.readInt16());
                                being.set("eq_legs", net.readInt16()); //headbottom

                                if(packet == 0x007B) {
                                    net.skip(4); // server tick
                                }

                                being.set("eq_shield", net.readInt16());
                                being.set("eq_head", net.readInt16()); //headtop
                                being.set("eq_torso", net.readInt16()); //headmid
                                being.set("hair_color", net.readInt16());
                                being.set("eq_shoes", net.readInt16());
                                being.set("eq_gloves", net.readInt16());
                                being.set("guild", net.readInt32());
                                net.skip(4);
                                being.set("status_effect_block", net.readInt16());
                                net.skip(1);
                                being.set("gender", net.readInt8());
                                if(packet == 0x007B) {
                                    net.readCoordinatePair(being);
                                } else {
                                    net.readCoordinates(being);
                                }
                                net.skip(5);
                            } break;
                            case 0x0095: { // SMSG_BEING_NAME_RESPONSE (30)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being != LuaValue.NIL) {
                                    String name = net.readString(24);
                                    being.set("name", name);
                                    if(name.equals("doomstal")) {
                                        globals.set("leader_id", id);
                                    }
                                } else {
                                    net.skipPacket();
                                }
                            } break;
                            case 0x007C: { // SMSG_BEING_SPAWN
                                net.skipPacket();
                            } break;
                            case 0x0086: { // SMSG_BEING_MOVE_2
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being != LuaValue.NIL) {
                                    net.readCoordinatePair(being);
                                    net.skip(2); //server tick
                                } else {
                                    net.skip(7);
                                }
                                net.skip(3); // possibly coordinates
                            } break;
                            case 0x0080: { // SMSG_BEING_REMOVE
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being != LuaValue.NIL) {
                                    if(net.readInt8() == 1) {
                                        being.set("action", "dead");
                                    } else {
                                        beings.set(id, LuaValue.NIL);
                                    }
                                } else {
                                    net.skip(1);
                                }
                            } break;
                            case 0x0148: { // SMSG_BEING_RESURRECT (8)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being != LuaValue.NIL) {
                                    if(net.readInt8() == 1) {
                                        being.set("action", "stand");
                                    }
                                } else {
                                    net.skip(1);
                                }
                                net.skip(1);
                            } break;
                            case 0x01DE: { // SMSG_SKILL_DAMAGE
                                net.skipPacket();
                            } break;
                            case 0x008A: { // SMSG_BEING_ACTION (29)
                                LuaValue srcBeing = beings.get(net.readInt32());
                                LuaValue dstBeing = beings.get(net.readInt32());
                                net.skip(4); // server tick
                                net.skip(8); // src speed, dst speed
                                int param1 = net.readInt16();
                                net.skip(2); // param2
                                int type = net.readInt8();
                                net.skip(2); // param3

                                switch(type) {
                                    case 0x00: // hit
                                    case 0x0A: // critical hit
                                    case 0x08: // multi hit
                                    case 0x0B: // flee
                                    break;
                                    case 0x02: // sit
                                    break;
                                    case 0x03: // stand up
                                    break;
                                }
                            } break;
                            case 0x019B: { // SMSG_BEING_SELFEFFECT (10)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being == LuaValue.NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                int effectType = net.readInt32();
                            } break;
                            case 0x00C0: { // SMSG_BEING_EMOTION (7)
                                net.skipPacket();
                            } break;
                            case 0x00C3: // SMSG_BEING_CHANGE_LOOKS (8)
                            case 0x01D7: { // SMSG_BEING_CHANGE_LOOKS2 (11)
                                LuaValue being = beings.get(net.readInt32());
                                if(being == LuaValue.NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                int type = net.readInt8();
                                int id = 0;
                                int id2 = 0;
                                if(packet == 0x00C3) {
                                    id = net.readInt8();
                                } else {
                                    id = net.readInt16();
                                    id2 = net.readInt16();
                                }
                                switch(type) {
                                    case 1:
                                        being.set("hair_type", id);
                                    break;
                                    case 2:
                                        being.set("eq_weapon", id);
                                        being.set("eq_shield", id2);
                                    break;
                                    case 3:
                                        being.set("eq_legs", id);
                                    break;
                                    case 4:
                                        being.set("eq_head", id);
                                    break;
                                    case 5:
                                        being.set("eq_torso", id);
                                    break;
                                    case 6:
                                        being.set("hair_color", id);
                                    break;
                                    case 8:
                                        being.set("eq_shield", id);
                                    break;
                                    case 9:
                                        being.set("eq_shoes", id);
                                    break;
                                    case 10:
                                        being.set("eq_gloves", id);
                                    break;
                                    case 11:
                                        being.set("eq_cape", id);
                                    break;
                                    case 12:
                                        being.set("eq_misc1", id);
                                    break;
                                    case 13:
                                        being.set("eq_misc2", id);
                                    break;
                                }
                            } break;
                            case 0x0195: { // SMSG_PLAYER_GUILD_PARTY_INFO (102)
                                LuaValue being = beings.get(net.readInt32());
                                if(being == LuaValue.NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                being.set("party_name", net.readString(24));
                                being.set("guild_name", net.readString(24));
                                being.set("guild_pos", net.readString(24));
                                net.skip(24);
                            } break;
                            case 0x009C: { // SMSG_BEING_CHANGE_DIRECTION (9)
                                LuaValue being = beings.get(net.readInt32());
                                if(being == LuaValue.NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                net.skip(2);
                                being.set("dir", net.readInt8());
                            } break;
                            case 0x01D8: // SMSG_PLAYER_UPDATE_1 (54)
                            case 0x01D9: // SMSG_PLAYER_UPDATE_2 (53)
                            case 0x01DA: { // SMSG_PLAYER_MOVE (60)
                                int id = net.readInt32();
                                int speed = net.readInt16();
                                int stunMode = net.readInt16();
                                int statusEffects = net.readInt16();
                                statusEffects |= net.readInt16() << 16;
                                int job = net.readInt16();

                                LuaValue being = beings.get(id);
                                if(being == LuaValue.NIL) {
                                    being = createBeing(id, job);
                                }

                                being.set("id", id);
                                being.set("speed", speed);
                                being.set("stun_mode", stunMode);
                                being.set("status_effects", statusEffects);
                                being.set("hair_style", net.readInt16());
                                being.set("eq_weapon", net.readInt16());
                                being.set("eq_shield", net.readInt16());
                                being.set("eq_legs", net.readInt16());

                                if(packet == 0x01DA) {
                                    net.skip(4); // server tick
                                }

                                being.set("eq_head", net.readInt16());
                                being.set("eq_torso", net.readInt16());
                                being.set("hair_color", net.readInt16());
                                being.set("eq_shoes", net.readInt16());
                                being.set("eq_gloves", net.readInt16());
                                net.skip(8);
                                being.set("status_effect_block", net.readInt16());
                                net.skip(1);
                                being.set("gender", net.readInt8());
                                if(packet == 0x01DA) {
                                    net.readCoordinatePair(being);
                                } else {
                                    net.readCoordinates(being);
                                }
                                net.skip(2); // gm status
                                if(packet == 0x01D8) {
                                    switch(net.readInt8()) {
                                        case 1:
                                            being.set("action", "dead");
                                        break;
                                        case 2:
                                            being.set("action", "sit");
                                        break;
                                    }
                                } else if(packet == 0x01DA) {
                                    net.skip(1);
                                }
                                net.skip(2);
                            } break;
                            case 0x0088: { // SMSG_PLAYER_STOP (10)
                                LuaValue being = beings.get(net.readInt32());
                                if(being == LuaValue.NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                being.set("x", net.readInt16());
                                being.set("y", net.readInt16());
                            } break;
                            case 0x0139: { // SMSG_PLAYER_MOVE_TO_ATTACK (16)
                                net.skipPacket();
                            } break;
                            case 0x0119: { // SMSG_PLAYER_STATUS_CHANGE (13)
                                LuaValue being = beings.get(net.readInt32());
                                if(being == LuaValue.NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                being.set("stun_mode", net.readInt16());
                                int statusEffects = net.readInt16();
                                statusEffects |= net.readInt16() << 16;
                                being.set("status_effects", statusEffects);
                                net.skip(1);
                            } break;
                            case 0x0196: { // SMSG_BEING_STATUS_CHANGE (9)
                                int status = net.readInt16();
                                int id = net.readInt32();
                                int flag = net.readInt8();
                            } break;
                            case 0x0098: { // SMSG_WHISPER_RESPONSE (3)
                                switch(net.readInt8()) {
                                    case 0x00:
                                        // success
                                    break;
                                    case 0x01:
                                        // recipient offline
                                    break;
                                    case 0x02:
                                        // ignored
                                    break;
                                }
                            } break;
                            case 0x0097: { // SMSG_WHISPER
                                int msglen = net.getPacketLength() - 28;
                                if(msglen <= 0) {
                                    net.skipPacket();
                                    break;
                                }
                                String nick = net.readString(24);
                                String msg = net.readString(msglen);
                                System.out.println(nick + " > " + msg);
                            } break;
                            case 0x008D: { // SMSG_BEING_CHAT
                                int id = net.readInt16();
                                int msglen = net.getPacketLength() - 8;
                                if(beings.get(id) == LuaValue.NIL || msglen <= 0) {
                                    net.skipPacket();
                                    break;
                                }
                                String msg = net.readString(msglen);
                                System.out.println("["+id+"] "+msg);
                            } break;
                            case 0x008E: // SMSG_PLAYER_CHAT
                            case 0x009A: { // SMSG_GM_CHAT
                                int msglen = net.getPacketLength() - 4;
                                if(msglen <= 0) {
                                    net.skipPacket();
                                    break;
                                }
                                String msg = net.readString(msglen);
                                if(packet == 0x008E) {
                                    System.out.println(msg);
                                } else {
                                    System.out.println("[announcement] "+msg);
                                }
                            } break;
                            case 0x010C: { // SMSG_MVP (6)
                                int id = net.readInt32();
                            } break;
                            case 0x01EE: // SMSG_PLAYER_INVENTORY
                            case 0x01F0: { // SMSG_PLAYER_STORAGE_ITEMS
                                if(packet == 0x01EE) {
                                    //equipment = new LuaTable();
                                    inventory = new LuaTable();
                                    //globals.set("equipment", equipment);
                                    globals.set("inventory", inventory);
                                } else {
                                    storage = new LuaTable();
                                    globals.set("storage", storage);
                                }
                                int number = (net.getPacketLength() - 4) / 18;
                                for(int i=0; i!=number; ++i) {
                                    LuaTable item = new LuaTable();
                                    int index = net.readInt16();
                                    item.set("index", index);
                                    item.set("id", net.readInt16());
                                    net.skip(1); // item type
                                    net.skip(1); // identified
                                    item.set("amount", net.readInt16());
                                    net.skip(2); // arrow (?)
                                    net.skip(8); // cards
                                    if(packet == 0x01EE) {
                                        inventory.set(index, item);
                                    } else {
                                        storage.set(index, item);
                                    }
                                }
                            } break;
                            case 0x00A6: { // SMSG_PLAYER_STORAGE_EQUIP
                                int number = (net.getPacketLength() - 4) / 20;
                                for(int i=0; i!=number; ++i) {
                                    int index = net.readInt16();
                                    LuaTable item = new LuaTable();
                                    item.set("index", index);
                                    item.set("id", net.readInt16());
                                    net.skip(1); // item type
                                    net.skip(1); // identified
                                    net.skip(2); // equip point (?)
                                    net.skip(2); // another equip point (?)
                                    net.skip(1); // attribute (broken)
                                    net.skip(1); // refine level
                                    net.skip(8); // cards
                                    storage.set(index, item);
                                }
                            } break;
                            case 0x00A0: { // SMSG_PLAYER_INVENTORY_ADD
                                int index = net.readInt16();
                                LuaTable item = new LuaTable();
                                item.set("index", index);
                                item.set("amount", net.readInt16());
                                item.set("id", net.readInt16());
                                net.skip(1); // identified
                                net.skip(1); // attribute
                                net.skip(1); // refine
                                net.skip(8); // cards
                                net.skip(2); // equip type
                                net.skip(1); // item type
                                if(net.readInt8() == 0) {
                                    LuaValue item1 = inventory.get(index);
                                    if(item1 != LuaValue.NIL && item1.get("id") == item.get("id")) {
                                        item.set("amount", item.get("amount").toint() + item1.get("amount").toint());
                                    }
                                    inventory.set(index, item);
                                }
                            } break;
                            case 0x00AF: { // SMSG_PLAYER_INVENTORY_REMOVE
                                int index = net.readInt16();
                                int amount = net.readInt16();
                                LuaValue item = inventory.get(index);
                                if(item != LuaValue.NIL) {
                                    item.set("amount", item.get("amount").toint() - amount);
                                    if(item.get("amount").toint() <= 0) {
                                        inventory.set(index, LuaValue.NIL);
                                    }
                                }
                            } break;
                            case 0x00A8: { // SMSG_ITEM_USE_RESPONSE
                                int index = net.readInt16();
                                int amount = net.readInt16();
                                if(net.readInt8() == 0) {
                                    // failed to use item
                                } else {
                                    LuaValue item = inventory.get(index);
                                    if(item != LuaValue.NIL) {
                                        if(amount != 0) {
                                            item.set("amount", amount);
                                        } else {
                                            inventory.set(index, LuaValue.NIL);
                                        }
                                    }
                                }
                            } break;
                            case 0x00F2: { // SMSG_PLAYER_STORAGE_STATUS
                                net.skip(2); // used count (?)
                                int size = net.readInt16(); // max size
                                storage = new LuaTable();
                                storage.set("size", size);
                                globals.set("storage", storage);
                            } break;
                            case 0x00F4: { // SMSG_PLAYER_STORAGE_ADD
                                int index = net.readInt16();
                                int amount = net.readInt16();
                                LuaTable item = new LuaTable();
                                item.set("index", index);
                                item.set("amount", amount);
                                item.set("id", net.readInt16());
                                net.skip(1); // identified
                                net.skip(1); // attribute
                                net.skip(1); // refine
                                net.skip(8); // cards
                                LuaValue item1 = storage.get(index);
                                if(item1 != LuaValue.NIL) {
                                    item1.set("id", item.get("id"));
                                    item1.set("amount", item1.get("amount").toint() + amount);
                                } else {
                                    storage.set(index, item);
                                }
                            } break;
                            case 0x00F6: { // SMSG_PLAYER_STORAGE_REMOVE
                                int index = net.readInt16();
                                int amount = net.readInt16();
                                LuaValue item = storage.get(index);
                                if(item != LuaValue.NIL) {
                                    item.set("amount", item.get("amount").toint() - amount);
                                    if(item.get("amount").toint() <= 0) {
                                        storage.set(index, LuaValue.NIL);
                                    }
                                }
                            } break;
                            case 0x00F8: { // SMSG_PLAYER_STORAGE_CLOSE
                            } break;
                            case 0x00A4: { // SMSG_PLAYER_EQUIPMENT
                                equipment = new LuaTable();
                                globals.set("equipment", equipment);
                                int number = (net.getPacketLength() - 4) / 20;
                                for(int i=0; i!=number; ++i) {
                                    int index = net.readInt16();
                                    LuaTable item = new LuaTable();
                                    item.set("index", index);
                                    item.set("id", net.readInt16());
                                    net.skip(1); // item type
                                    net.skip(1); // identified
                                    net.skip(2); // equip type
                                    int type = net.readInt16();
                                    net.skip(1); // attribute
                                    net.skip(1); // refine
                                    net.skip(8); // cards
                                    inventory.set(index, item);
                                    if(type != 0) {
                                        equipment.set(type, LuaValue.valueOf(index));
                                    }
                                }
                            } break;
                            case 0x00AA: { // SMSG_PLAYER_EQUIP
                                int index = net.readInt16();
                                int type = net.readInt16();
                                if(net.readInt8() == 0) {
                                    // unable to equip
                                } else {
                                    equipment.set(type, LuaValue.valueOf(index));
                                }
                            } break;
                            case 0x00AC: { // SMSG_PLAYER_UNEQUIP
                                int index = net.readInt16();
                                int type = net.readInt16();
                                if(net.readInt8() == 0) {
                                    // unable to unequip
                                } else {
                                    equipment.set(type, LuaValue.NIL);
                                    character.set("attack_range", -1);
                                }
                            } break;
                            case 0x013A: { // SMSG_PLAYER_ATTACK_RANGE
                                character.set("attack_range", net.readInt16());
                            } break;
                            case 0x013C: { // SMSG_PLAYER_ARROW_EQUIP
                                int index = net.readInt16();
                                if(index <= 1) break;
                                equipment.set(10, LuaValue.valueOf(index)); // projectile slot
                            } break;
                            default:
                                net.skipPacket();
                        }
                        net.checkPacketLength();
//                        System.out.append("done packet = ");
//                        Utils.printHexInt16(packet);
//                        System.out.println();
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                    quit = true;
                }
            }
        });
        reader.start();

        LuaValue script = globals.loadfile("bot.lua");
        script.call();
        LuaValue loopBody = globals.get("loop_body");

        while(!quit) {
            LuaValue ret = loopBody.call();
            if(ret == LuaValue.FALSE) {
                quit = true;
                break;
            }
            Thread.sleep(10);
        }

    }

    public static void main(String[] args) throws Exception {
        bot instance = new bot();
    }
}
