import java.io.IOException;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import static org.luaj.vm2.LuaValue.*;
import org.luaj.vm2.LuaNumber;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
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
    public LuaTable buy_sell = new LuaTable();
    public LuaTable items = new LuaTable();
    public Globals globals;

    public LuaValue script;
    public LuaValue loopBody;
    public LuaValue packetHandler;

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

    public LuaValue equipType(int type) {
        if(type == 0) return NIL;
        if((type & 0x0001) != 0) return valueOf("legs");
        if((type & 0x0002) != 0) return valueOf("weapon");
        if((type & 0x0004) != 0) return valueOf("gloves");
        if((type & 0x0008) != 0) return valueOf("necklace");
        if((type & 0x0010) != 0) return valueOf("ring1");
        if((type & 0x0020) != 0) return valueOf("shield");
        if((type & 0x0040) != 0) return valueOf("shoes");
        if((type & 0x0080) != 0) return valueOf("ring2");
        if((type & 0x0100) != 0) return valueOf("head");
        if((type & 0x0200) != 0) return valueOf("torso");
        return valueOf(type);
    }

    public bot() throws Exception {
        net.connect(this);

        globals = JsePlatform.standardGlobals();

        globals.set("character", character);
        globals.set("inventory", inventory);
        globals.set("equipment", equipment);
        globals.set("storage", storage);
        globals.set("map_name", mapName);
        globals.set("beings", beings);
        globals.set("buy_sell", buy_sell);
        globals.set("items", items);

        script = globals.loadfile("bot.lua");
        script.call();
        packetHandler = globals.get("packet_handler");
        loopBody = globals.get("loop_body");

        LuaValue sendPacket = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                    String pak = args.arg(1).toString();
                    switch(pak) {
                        case "walk": {
                            int x = args.arg(2).toint();
                            int y = args.arg(3).toint();
                            int dir = character.get("dir").toint();
                            net.writeInt16(0x0085); // CMSG_PLAYER_CHANGE_DEST
                            net.writeCoordinates(x, y, dir);
                        } break;
                        case "talk": {
                            String msg = character.get("name") + " : " + args.arg(2).toString();
                            net.writeInt16(0x008C); // CMSG_CHAT_MESSAGE
                            net.writeInt16(4 + msg.length() + 1);
                            net.writeString(msg.length() + 1, msg);
                        } break;
                        case "whisper": {
                            String nick = args.arg(2).toString();
                            String msg = args.arg(3).toString();
                            net.writeInt16(0x0096); // CMSG_CHAT_WHISPER
                            net.writeInt16(msg.length() + 28);
                            net.writeString(24, nick);
                            net.writeString(msg.length(), msg);
                        } break;
                        case "storage_close": {
                            net.writeInt16(0x00F8); // CMSG_CLOSE_STORAGE
                        } break;
                        case "equip": {
                            int index = args.arg(2).toint();
                            net.writeInt16(0x00A9); // CMSG_PLAYER_EQUIP
                            net.writeInt16(index);
                            net.writeInt16(0);
                        } break;
                        case "unequip": {
                            int index = args.arg(2).toint();
                            net.writeInt16(0x00AB); // CMSG_PLAYER_UNEQUIP
                            net.writeInt16(index);
                        } break;
                        case "use": {
                            int index = args.arg(2).toint();
                            LuaValue item = inventory.get(index);
                            net.writeInt16(0x00A7); // CMSG_PLAYER_INVENTORY_USE
                            net.writeInt16(index);
                            net.writeInt32(item.get("id").toint());
                        } break;
                        case "drop": {
                            int index = args.arg(2).toint();
                            int amount = args.arg(3).toint();
                            net.writeInt16(0x00A2); // CMSG_PLAYER_INVENTORY_DROP
                            net.writeInt16(index);
                            net.writeInt16(amount);
                        } break;
                        case "to_storage": {
                            int index = args.arg(2).toint();
                            int amount = args.arg(3).toint();
                            net.writeInt16(0x00F3); // CMSG_MOVE_TO_STORAGE
                            net.writeInt16(index);
                            net.writeInt16(amount);
                        } break;
                        case "from_storage": {
                            int index = args.arg(2).toint();
                            int amount = args.arg(3).toint();
                            net.writeInt16(0x00F5); // CMSG_MOVE_FROM_STORAGE
                            net.writeInt16(index);
                            net.writeInt16(amount);
                        } break;
                        case "npc_talk": {
                            int npcId = args.arg(2).toint();
                            net.writeInt16(0x0090); // CMSG_NPC_TALK
                            net.writeInt32(npcId);
                            net.writeInt8(0);
                        } break;
                        case "npc_buy_sell": {
                            int npcId = args.arg(2).toint();
                            boolean buy_sell = args.arg(3).toboolean(); // true for selling
                            net.writeInt16(0x00C5); // CMSG_NPC_BUY_SELL_REQUEST
                            net.writeInt32(npcId);
                            net.writeInt8( buy_sell ? 1 : 0 );
                        } break;
                        case "npc_buy_item": {
                            int npcId = args.arg(2).toint();
                            int itemId = args.arg(3).toint();
                            int amount = args.arg(4).toint();
                            net.writeInt16(0x00C8); // CMSG_NPC_BUY_REQUEST
                            net.writeInt16(8); // one item (length of packet)
                            net.writeInt16(amount);
                            net.writeInt16(itemId);
                        } break;
                        case "npc_sell_item": {
                            int npcId = args.arg(2).toint();
                            int itemId = args.arg(3).toint();
                            int amount = args.arg(4).toint();
                            net.writeInt16(0x00C9); // CMSG_NPC_SELL_REQUEST
                            net.writeInt16(8);
                            net.writeInt16(itemId);
                            net.writeInt16(amount);
                        } break;
                        case "npc_next": {
                            int npcId = args.arg(2).toint();
                            net.writeInt16(0x00B9); // CMSG_NPC_NEXT_REQUEST
                            net.writeInt32(npcId);
                        } break;
                        case "npc_close": {
                            int npcId = args.arg(2).toint();
                            net.writeInt16(0x0146); // CMSG_NPC_CLOSE
                            net.writeInt32(npcId);
                        } break;
                        case "npc_choise": {
                            int npcId = args.arg(2).toint();
                            int choise = args.arg(3).toint();
                            net.writeInt16(0x00B8); // CMSG_LIST_CHOISE
                            net.writeInt32(npcId);
                            net.writeInt8(choise);
                        } break;
                        case "npc_int_input": {
                            int npcId = args.arg(2).toint();
                            int value = args.arg(3).toint();
                            net.writeInt16(0x0143); // CMSG_NPC_INT_RESPONSE
                            net.writeInt32(npcId);
                            net.writeInt32(value);
                        } break;
                        case "npc_str_input": {
                            int npcId = args.arg(2).toint();
                            String value = args.arg(3).toString();
                            net.writeInt16(0x01D5); // CMSG_NPC_STR_RESPONSE
                            net.writeInt16(value.length() + 9);
                            net.writeInt32(npcId);
                            net.writeString(value.length() + 1, value);
                        } break;
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                return NIL;
            }
        };

        globals.set("send_packet", sendPacket);

        Thread reader = new Thread(new Runnable() {
            @Override
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
                                if(being == NIL) {
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
                                packetHandler.call(valueOf("being_update"), valueOf(id));
                            } break;
                            case 0x0095: { // SMSG_BEING_NAME_RESPONSE (30)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being != NIL) {
                                    String name = net.readString(24);
                                    being.set("name", name);
                                    packetHandler.call(valueOf("being_name"), valueOf(id));
                                } else {
                                    net.skipPacket();
                                }
                            } break;
                            case 0x007C: { // SMSG_BEING_SPAWN
                                int id = net.readInt32();
                                net.skip(14);
                                int job = net.readInt16();
                                net.skip(14);
                                LuaTable being = new LuaTable();
                                net.readCoordinates(being);
                                System.out.println("SMSG_BEING_SPAWN id="+id+" job="+job+" at "+being.get("x")+", "+being.get("y"));
                                net.skipPacket();
                            } break;
                            case 0x0086: { // SMSG_BEING_MOVE_2
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being != NIL) {
                                    net.readCoordinatePair(being);
                                    net.skip(2); //server tick
                                    packetHandler.call(valueOf("being_update"), valueOf(id));
                                } else {
                                    net.skip(7);
                                }
                                net.skip(3); // possibly coordinates
                            } break;
                            case 0x0080: { // SMSG_BEING_REMOVE
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being != NIL) {
                                    if(net.readInt8() == 1) {
                                        being.set("action", "dead");
                                    } else {
                                        beings.set(id, NIL);
                                    }
                                    packetHandler.call(valueOf("being_remove"), valueOf(id));
                                } else {
                                    net.skip(1);
                                }
                            } break;
                            case 0x0148: { // SMSG_BEING_RESURRECT (8)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being != NIL) {
                                    if(net.readInt8() == 1) {
                                        being.set("action", "stand");
                                    }
                                    packetHandler.call(valueOf("being_update"), valueOf(id));
                                } else {
                                    net.skip(1);
                                }
                                net.skip(1);
                            } break;
                            case 0x01DE: { // SMSG_SKILL_DAMAGE
                                net.skip(2); // skill id
                                int srcId = net.readInt32();
                                int dstId = net.readInt32();
                                LuaValue srcBeing = beings.get(srcId);
                                LuaValue dstBeing = beings.get(dstId);
                                net.skip(4); // server tick
                                int attackSpeed = net.readInt32();
                                net.skip(4); // dst speed
                                int dmg = net.readInt32();
                                net.skip(2); // skill level
                                net.skip(2); // div
                                net.skip(1); // skill hit/type (?)
                                if(attackSpeed != 0 && srcBeing != NIL && srcId != character.get("id").toint()); {
                                    srcBeing.set("attack_speed", attackSpeed);
                                }
                                if(srcBeing != NIL && dstBeing != NIL) {
                                    packetHandler.invoke(varargsOf(new LuaValue[] {
                                        valueOf("being_attack"),
                                        valueOf(srcId),
                                        valueOf(dstId),
                                        valueOf(dmg)
                                    }));
                                }
                            } break;
                            case 0x008A: { // SMSG_BEING_ACTION (29)
                                int srcId = net.readInt32();
                                int dstId = net.readInt32();
                                LuaValue srcBeing = beings.get(srcId);
                                LuaValue dstBeing = beings.get(dstId);
                                net.skip(4); // server tick
                                net.skip(8); // src speed, dst speed
                                int param1 = net.readInt16();
                                net.skip(2); // param2
                                int type = net.readInt8();
                                net.skip(2); // param3
                                String typeStr = "";

                                switch(type) {
                                    case 0x00: // hit
                                        typeStr = "hit";
                                    break;
                                    case 0x0A: // critical hit
                                        typeStr = "critical";
                                    break;
                                    case 0x08: // multi hit
                                        typeStr = "multi";
                                    break;
                                    case 0x0B: // flee
                                        typeStr = "flee";
                                    break;
                                    case 0x02: // sit
                                        typeStr = "sit";
                                    break;
                                    case 0x03: // stand up
                                        typeStr = "stand";
                                    break;
                                }
                                packetHandler.invoke(varargsOf(new LuaValue[] {
                                    valueOf("being_action"),
                                    valueOf(srcId),
                                    valueOf(dstId),
                                    valueOf(typeStr),
                                    valueOf(param1)
                                }));
                            } break;
                            case 0x019B: { // SMSG_BEING_SELFEFFECT (10)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                int effectType = net.readInt32();
                                packetHandler.call(
                                    valueOf("being_selfeffect"),
                                    valueOf(id),
                                    valueOf(effectType)
                                );
                            } break;
                            case 0x00C0: { // SMSG_BEING_EMOTION (7)
                                int dstId = net.readInt32();
                                int emote = net.readInt8();
                                packetHandler.call(
                                    valueOf("being_emote"),
                                    valueOf(dstId),
                                    valueOf(emote)
                                );
                            } break;
                            case 0x00C3: // SMSG_BEING_CHANGE_LOOKS (8)
                            case 0x01D7: { // SMSG_BEING_CHANGE_LOOKS2 (11)
                                int dstId = net.readInt32();
                                LuaValue being = beings.get(dstId);
                                if(being == NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                int type = net.readInt8();
                                int id = 0;
                                int id2 = 0;
                                String typeStr = "";
                                if(packet == 0x00C3) {
                                    id = net.readInt8();
                                } else {
                                    id = net.readInt16();
                                    id2 = net.readInt16();
                                }
                                switch(type) {
                                    case 1:
                                        typeStr = "hair_type";
                                        being.set("hair_type", id);
                                    break;
                                    case 2:
                                        typeStr = "eq_weapon+eq_shield";
                                        being.set("eq_weapon", id);
                                        being.set("eq_shield", id2);
                                    break;
                                    case 3:
                                        typeStr = "eq_legs";
                                        being.set("eq_legs", id);
                                    break;
                                    case 4:
                                        typeStr = "eq_head";
                                        being.set("eq_head", id);
                                    break;
                                    case 5:
                                        typeStr = "eq_torso";
                                        being.set("eq_torso", id);
                                    break;
                                    case 6:
                                        typeStr = "hair_color";
                                        being.set("hair_color", id);
                                    break;
                                    case 8:
                                        typeStr = "eq_shield";
                                        being.set("eq_shield", id);
                                    break;
                                    case 9:
                                        typeStr = "eq_shoes";
                                        being.set("eq_shoes", id);
                                    break;
                                    case 10:
                                        typeStr = "eq_gloves";
                                        being.set("eq_gloves", id);
                                    break;
                                    case 11:
                                        typeStr = "eq_cape";
                                        being.set("eq_cape", id);
                                    break;
                                    case 12:
                                        typeStr = "eq_misc1";
                                        being.set("eq_misc1", id);
                                    break;
                                    case 13:
                                        typeStr = "eq_misc2";
                                        being.set("eq_misc2", id);
                                    break;
                                }
                                packetHandler.call(
                                    valueOf("being_change_looks"),
                                    valueOf(dstId),
                                    valueOf(typeStr)
                                );
                            } break;
                            case 0x0195: { // SMSG_PLAYER_GUILD_PARTY_INFO (102)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                being.set("party_name", net.readString(24));
                                being.set("guild_name", net.readString(24));
                                being.set("guild_pos", net.readString(24));
                                net.skip(24);
                                packetHandler.call(
                                    valueOf("being_guild_info"),
                                    valueOf(id)
                                );
                            } break;
                            case 0x009C: { // SMSG_BEING_CHANGE_DIRECTION (9)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                net.skip(2);
                                being.set("dir", net.readInt8());
                                packetHandler.call(
                                    valueOf("being_update"),
                                    valueOf(id)
                                );
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
                                if(being == NIL) {
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
                                packetHandler.call(
                                    valueOf("player_update"),
                                    valueOf(id)
                                );
                            } break;
                            case 0x0088: { // SMSG_PLAYER_STOP (10)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                int x = net.readInt16();
                                int y = net.readInt16();
                                packetHandler.invoke(varargsOf(new LuaValue[] {
                                    valueOf("player_stop"),
                                    valueOf(id),
                                    valueOf(x),
                                    valueOf(y)
                                }));
                            } break;
                            case 0x0139: { // SMSG_PLAYER_MOVE_TO_ATTACK (16)
                                net.skipPacket();
                            } break;
                            case 0x0119: { // SMSG_PLAYER_STATUS_CHANGE (13)
                                int id = net.readInt32();
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    net.skipPacket();
                                    break;
                                }
                                being.set("stun_mode", net.readInt16());
                                int statusEffects = net.readInt16();
                                statusEffects |= net.readInt16() << 16;
                                being.set("status_effects", statusEffects);
                                net.skip(1);
                                packetHandler.call(
                                    valueOf("player_update"),
                                    valueOf(id)
                                );
                            } break;
                            case 0x0196: { // SMSG_BEING_STATUS_CHANGE (9)
                                int status = net.readInt16();
                                int id = net.readInt32();
                                int flag = net.readInt8();
                            } break;
                            case 0x0098: { // SMSG_WHISPER_RESPONSE (3)
                                String type = "";
                                switch(net.readInt8()) {
                                    case 0x00:
                                        type = "success";
                                    break;
                                    case 0x01:
                                        type = "offline";
                                        // recipient offline
                                    break;
                                    case 0x02:
                                        type = "ignored";
                                        // ignored
                                    break;
                                }
                                packetHandler.call(
                                    valueOf("whisper_response"),
                                    valueOf(type)
                                );
                            } break;
                            case 0x0097: { // SMSG_WHISPER
                                int msglen = net.getPacketLength() - 28;
                                if(msglen <= 0) {
                                    net.skipPacket();
                                    break;
                                }
                                String nick = net.readString(24);
                                String msg = net.readString(msglen);
                                packetHandler.call(
                                    valueOf("whisper"),
                                    valueOf(nick),
                                    valueOf(msg)
                                );
                            } break;
                            case 0x008D: { // SMSG_BEING_CHAT
                                int id = net.readInt16();
                                int msglen = net.getPacketLength() - 8;
                                if(beings.get(id) == NIL || msglen <= 0) {
                                    net.skipPacket();
                                    break;
                                }
                                String msg = net.readString(msglen);
                                packetHandler.call(
                                    valueOf("being_chat"),
                                    valueOf(id),
                                    valueOf(msg)
                                );
                            } break;
                            case 0x008E: // SMSG_PLAYER_CHAT
                            case 0x009A: { // SMSG_GM_CHAT
                                int msglen = net.getPacketLength() - 4;
                                if(msglen <= 0) {
                                    net.skipPacket();
                                    break;
                                }
                                String msg = net.readString(msglen);
                                LuaValue gm_flag = (packet == 0x009A) ? TRUE : FALSE;
                                packetHandler.call(
                                    valueOf("player_chat"),
                                    gm_flag,
                                    valueOf(msg)
                                );
                            } break;
                            case 0x010C: { // SMSG_MVP (6)
                                int id = net.readInt32();
                            } break;
                            case 0x01EE: // SMSG_PLAYER_INVENTORY
                            case 0x01F0: { // SMSG_PLAYER_STORAGE_ITEMS
                                if(packet == 0x01EE) {
                                    inventory = new LuaTable();
                                    globals.set("inventory", inventory);
                                    equipment = new LuaTable();
                                    globals.set("equipment", equipment);
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
                                packetHandler.call(
                                    (packet == 0x01EE) ? valueOf("inventory_update") : valueOf("storage_update")
                                );
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
                                packetHandler.call(valueOf("storage_update"));
                            } break;
                            case 0x00A0: { // SMSG_PLAYER_INVENTORY_ADD
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
                                net.skip(2); // equip type
                                net.skip(1); // item type
                                if(net.readInt8() == 0) {
                                    LuaValue item1 = inventory.get(index);
                                    if(item1 != NIL && item1.get("id") == item.get("id")) {
                                        item.set("amount", amount + item1.get("amount").toint());
                                    }
                                    inventory.set(index, item);
                                }
                                packetHandler.call(valueOf("inventory_update"));
                            } break;
                            case 0x00AF: { // SMSG_PLAYER_INVENTORY_REMOVE
                                int index = net.readInt16();
                                int amount = net.readInt16();
                                LuaValue item = inventory.get(index);
                                if(item != NIL) {
                                    item.set("amount", item.get("amount").toint() - amount);
                                    if(item.get("amount").toint() <= 0) {
                                        inventory.set(index, NIL);
                                    }
                                }
                                packetHandler.call(valueOf("inventory_update"));
                            } break;
                            case 0x01C8: { // SMSG_PLAYER_INVENTORY_USE
                                int index = net.readInt16();
                                net.skip(2); // item id
                                net.skip(4); // id
                                int amount = net.readInt16();
                                net.skip(1); // type
                                LuaValue item = inventory.get(index);
                                if(item != NIL) {
                                    if(amount > 0) {
                                        item.set("amount", amount);
                                    } else {
                                        inventory.set(index, NIL);
                                    }
                                }
                                packetHandler.call(valueOf("inventory_update"));
                            } break;
                            case 0x00A8: { // SMSG_ITEM_USE_RESPONSE
                                int index = net.readInt16();
                                int amount = net.readInt16();
                                LuaValue result = TRUE;
                                if(net.readInt8() == 0) {
                                    // failed to use item
                                    result = FALSE;
                                } else {
                                    LuaValue item = inventory.get(index);
                                    if(item != NIL) {
                                        if(amount != 0) {
                                            item.set("amount", amount);
                                        } else {
                                            inventory.set(index, NIL);
                                        }
                                    }
                                }
                                packetHandler.call(
                                    valueOf("item_use_response"),
                                    result
                                );
                            } break;
                            case 0x00F2: { // SMSG_PLAYER_STORAGE_STATUS
                                net.skip(2); // used count (?)
                                int size = net.readInt16(); // max size
                                globals.set("storage_size", size);
                                packetHandler.call(valueOf("storage_status"));
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
                                if(item1 != NIL) {
                                    item1.set("id", item.get("id"));
                                    item1.set("amount", item1.get("amount").toint() + amount);
                                } else {
                                    storage.set(index, item);
                                }
                                packetHandler.call(valueOf("storage_update"));
                            } break;
                            case 0x00F6: { // SMSG_PLAYER_STORAGE_REMOVE
                                int index = net.readInt16();
                                int amount = net.readInt16();
                                LuaValue item = storage.get(index);
                                if(item != NIL) {
                                    item.set("amount", item.get("amount").toint() - amount);
                                    if(item.get("amount").toint() <= 0) {
                                        storage.set(index, NIL);
                                    }
                                }
                                packetHandler.call(valueOf("storage_update"));
                            } break;
                            case 0x00F8: { // SMSG_PLAYER_STORAGE_CLOSE
                                packetHandler.call(valueOf("storage_close"));
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
                                    if(type != 0) {
                                        item.set("equip", equipType(type));
                                    }
                                    net.skip(1); // attribute
                                    net.skip(1); // refine
                                    net.skip(8); // cards
                                    if(type == 0) {
                                        inventory.set(index, item);
                                    } else {
                                        equipment.set(index, item);
                                    }
                                }
                                packetHandler.call(valueOf("inventory_update"));
                            } break;
                            case 0x00AA: { // SMSG_PLAYER_EQUIP
                                int index = net.readInt16();
                                int type = net.readInt16();
                                LuaValue result = TRUE;
                                if(net.readInt8() == 0) {
                                    // unable to equip
                                    result = FALSE;
                                } else {
                                    LuaValue item = inventory.get(index);
                                    if(item != NIL) {
                                        item.set("equip", equipType(type));
                                        equipment.set(index, item);
                                        inventory.set(index, NIL);
                                    }
                                }
                                packetHandler.call(
                                    valueOf("equip"),
                                    result,
                                    valueOf(index)
                                );
                            } break;
                            case 0x00AC: { // SMSG_PLAYER_UNEQUIP
                                int index = net.readInt16();
                                int type = net.readInt16();
                                LuaValue result = TRUE;
                                if(net.readInt8() == 0) {
                                    // unable to unequip
                                    result = FALSE;
                                } else {
                                    LuaValue item = equipment.get(index);
                                    if(item != NIL) {
                                        item.set("equip", NIL);
                                        equipment.set(index, NIL);
                                        inventory.set(index, item);
                                        character.set("attack_range", -1);
                                    }
                                }
                                packetHandler.call(
                                    valueOf("unequip"),
                                    result,
                                    valueOf(index)
                                );
                            } break;
                            case 0x013A: { // SMSG_PLAYER_ATTACK_RANGE
                                character.set("attack_range", net.readInt16());
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x013C: { // SMSG_PLAYER_ARROW_EQUIP
                                int index = net.readInt16();
                                if(index <= 1) break;
                                LuaValue item = equipment.get(index);
                                if(item != NIL) {
                                    item.set("equip", "arrow");
                                    equipment.set(index, item);
                                    inventory.set(index, NIL);
                                }
                                packetHandler.call(valueOf("inventory_update"));
                            } break;
                            case 0x00C4: { // SMSG_NPC_BUY_SELL_CHOISE
                                int npcId = net.readInt32();
                                packetHandler.call(
                                    valueOf("buy_sell_choise"),
                                    valueOf(npcId)
                                );
                            } break;
                            case 0x00C6: { // SMSG_NPC_BUY
                                int count = (net.getPacketLength() - 4) / 11;
                                buy_sell = new LuaTable();
                                globals.set("buy_sell", buy_sell);
                                for(int i=0; i!=count; ++i) {
                                    int value = net.readInt32();
                                    net.skip(4); // dcvalue (?)
                                    net.skip(1); // type
                                    int itemId = net.readInt16();
                                    LuaTable item = new LuaTable();
                                    item.set("id", itemId);
                                    item.set("value", value);
                                    buy_sell.set(i+1, item);
                                }
                                packetHandler.call(valueOf("buy_items"));
                            } break;
                            case 0x00C7: { // SMSG_NPC_SELL
                                int count = (net.getPacketLength() - 4) / 10;
                                buy_sell = new LuaTable();
                                globals.set("buy_sell", buy_sell);
                                for(int i=0; i!=count; ++i) {
                                    int index = net.readInt16();
                                    int value = net.readInt32();
                                    net.skip(4); // ocvalue
                                    LuaValue item = inventory.get(index);
                                    if(item != NIL) {
                                        buy_sell.set(i+1, item);
                                    }
                                }
                                packetHandler.call(valueOf("sell_items"));
                            } break;
                            case 0x00CA: { // SMSG_NPC_BUY_RESPONSE
                                packetHandler.call(
                                    valueOf("buy_response"),
                                    (net.readInt8() == 0) ? TRUE : FALSE
                                );
                            } break;
                            case 0x00CB: { // SMSG_NPC_SELL_RESPONSE
                                packetHandler.call(
                                    valueOf("sell_response"),
                                    (net.readInt8() == 0) ? TRUE : FALSE
                                );
                            } break;
                            case 0x009D: // SMSG_ITEM_VISIBLE
                            case 0x009E: { // SMSG_ITEM_DROPPED
                                int id = net.readInt32();
                                LuaTable item = new LuaTable();
                                item.set("id", id);
                                item.set("item_id", net.readInt32());
                                net.skip(1); // identified
                                item.set("x", net.readInt16());
                                item.set("y", net.readInt16());
                                net.skip(4); // amount,subX,subY / subX,subY,amount
                                items.set(id, item);
                                packetHandler.call(valueOf("item_update"), valueOf(id));
                            } break;
                            case 0x00A1: { // SMSG_ITEM_REMOVE
                                int id = net.readInt32();
                                items.set(id, NIL);
                                packetHandler.call(valueOf("item_remove"), valueOf(id));
                            } break;
                            case 0x00B7: { // SMSG_NPC_CHOISE
                                int npcId = net.readInt32();
                                String msg = net.readString(net.getPacketLength() - 8);
                                String[] choises = msg.split(":");
                                LuaTable t_choises = new LuaTable();
                                for(int i=0; i!=choises.length; ++i) {
                                    t_choises.set(i+1, valueOf(choises[i]));
                                }
                                packetHandler.call(
                                    valueOf("npc_choise"),
                                    valueOf(npcId),
                                    t_choises
                                );
                            } break;
                            case 0x00B4: { // SMSG_NPC_MESSAGE
                                int npcId = net.readInt32();
                                String msg = net.readString(net.getPacketLength() - 8);
                                packetHandler.call(
                                    valueOf("npc_message"),
                                    valueOf(msg)
                                );
                            } break;
                            case 0x00B6: { // SMSG_NPC_CLOSE
                                int npcId = net.readInt32();
                                packetHandler.call(valueOf("npc_close"), valueOf(npcId));
                            } break;
                            case 0x00B5: { // SMSG_NPC_NEXT
                                int npcId = net.readInt32();
                                packetHandler.call(valueOf("npc_next"), valueOf(npcId));
                            } break;
                            case 0x0142: { // SMSG_NPC_INT_INPUT
                                int npcId = net.readInt32();
                                packetHandler.call(valueOf("npc_int_input"), valueOf(npcId));
                            } break;
                            case 0x01D4: { // SMSG_NPC_STR_INPUT
                                int npcId = net.readInt32();
                                packetHandler.call(valueOf("npc_str_input"), valueOf(npcId));
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

        while(!quit) {
            LuaValue ret = loopBody.call();
            if(ret == FALSE) {
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
