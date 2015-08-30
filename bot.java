import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

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
    public LuaTable skills = new LuaTable();
    public LuaTable trade_buy = new LuaTable();
    public LuaTable trade_sell = new LuaTable();
    public LuaTable warps = new LuaTable();
    public int clientTime = 0;

    public LuaTable itemDB = new LuaTable();

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
    Semaphore writeLock = new Semaphore(1, true);
    Semaphore dataLock = new Semaphore(1, true);

    HashMap<String, Map> maps = new HashMap<String, Map>();
    Map map;

    public LuaValue createBeing(int id, int job) throws IOException {
        LuaTable being = new LuaTable();
        if(id == character.get("id").toint()) being = character;
        String type = null;
        being.set("race", job);
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

        being.set("dir", 1);

        beings.set(id, being);

        if(type.equals("player") || type.equals("npc")) {
            try {
            writeLock.acquire();

            Net.PacketOut po = net.newPacket(0x0094); // being name request
            po.writeInt32(id);
            po.send();

            } catch(InterruptedException e) {
                e.printStackTrace();
            }
            writeLock.release();
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

    public void being_update_path(LuaValue being) {
        int x = being.get("x").toint();
        int y = being.get("y").toint();
        int dst_x = being.get("dst_x").toint();
        int dst_y = being.get("dst_y").toint();
        if(x!=dst_x || y!=dst_y) {
            LuaValue path = being.get("path");
            int path_index = being.get("path_index").toint();
            if(path == NIL) {
                being.set("path", map.find_path(x, y, dst_x, dst_y));
                being.set("path_index", 1);
                return;
            }

            int length = path.length();
            if(path_index > length
            || path.get(path_index).get("x").toint() !=x || path.get(path_index).get("y").toint() != y
            || path.get(length).get("x").toint() != dst_x || path.get(length).get("y").toint() != dst_y) {
                being.set("path", map.find_path(x, y, dst_x, dst_y));
                being.set("path_index", 1);
            }

        } else {
            being.set("path", NIL);
        }
    }

    public void load_maps() {
        File[] files = new File("server-data/world/map/data").listFiles();
        for(File file: files) {
            if(!file.isDirectory()) {
                String name = file.getName();
                if(name.matches("\\d\\d\\d-\\d\\.wlk")) {
                    String map_name = name.substring(0, 5);
                    maps.put(map_name, new Map(map_name));
                }
            }
        }
    }

    public void load_warps() {
        Utils.clearTable(warps);
        File[] files = new File("server-data/world/map/npc").listFiles();
        for(File file: files) {
            if(file.isDirectory()) {
                String name = file.getName();
                if(name.matches("\\d\\d\\d-\\d")) {
                    warps.set(name, load_warps(name));
                }
            }
        }
    }

    public LuaValue load_warps(String map_name) {
        try {
            File file = new File("server-data/world/map/npc/" + map_name + "/_warps.txt");
            if(!file.exists()) return NIL;

            LuaTable map_warps = new LuaTable();
            Scanner s = new Scanner(new FileInputStream(file));

            int i=1;
            while(s.hasNextLine()) {
                String line = s.nextLine();
                if(line.length() > 0 && !line.startsWith("//")) {
                    Scanner s2 = new Scanner(line);
                    s2.useDelimiter(",|\\|");
                    LuaTable warp = new LuaTable();
                    warp.set("map", s2.next());
                    warp.set("x", s2.nextInt());
                    warp.set("y", s2.nextInt());
                    s2.next();
                    s2.next();
                    s2.next();
                    s2.next();
                    warp.set("dst_map", s2.next());
                    warp.set("dst_x", s2.nextInt());
                    warp.set("dst_y", s2.nextInt());
                    map_warps.set(i++, warp);
                }
            }
            return map_warps;
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        return NIL;
    }

    public void fill_itemDB() {
        try {
            final String[] files = {
                "item_db_chest", "item_db_foot",
                "item_db_generic", "item_db_hand",
                "item_db_head", "item_db_leg",
                "item_db_offhand", "item_db_trinket",
                "item_db_use", "item_db_weapon"
            };
            for(String name: files) {
                Scanner s = new Scanner(new FileInputStream(new File("server-data/world/map/db/"+name+".txt")));

                while(s.hasNextLine()) {
                    String line = s.nextLine();
                    if(line.length() > 0 && !line.startsWith("//")) {
                        Scanner s2 = new Scanner(line);
                        s2.useDelimiter(",\\s*");
                        LuaTable item = new LuaTable();
                        int id = s2.nextInt();
                        item.set("id", id);
                        item.set("name", s2.next());
                        s2.next(); // type
                        item.set("price", s2.nextInt());
                        item.set("sell", s2.nextInt());
                        item.set("weight", s2.nextInt());
                        int attack = s2.nextInt();
                        if(attack > 0) item.set("attack", attack);
                        int defence = s2.nextInt();
                        if(defence > 0) item.set("defence", defence);
                        int range = s2.nextInt();
                        if(range > 0) item.set("range", range);
                        int mattack = s2.nextInt();
                        if(mattack > 0) item.set("mattack", mattack);
                        // ignore the rest
                        itemDB.set(id, item);
                    }
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void update_character_stats() {
        character.set("str", character.get("str_base").toint() + character.get("str_mod").toint());
        character.set("agi", character.get("agi_base").toint() + character.get("agi_mod").toint());
        character.set("vit", character.get("vit_base").toint() + character.get("vit_mod").toint());
        character.set("int", character.get("int_base").toint() + character.get("int_mod").toint());
        character.set("dex", character.get("dex_base").toint() + character.get("dex_mod").toint());
        character.set("luk", character.get("luk_base").toint() + character.get("luk_mod").toint());
        character.set("attack", character.get("attack_base").toint() + character.get("attack_mod").toint());
        character.set("defence", character.get("defence_base").toint() + character.get("defence_mod").toint());
        character.set("mattack", character.get("mattack_base").toint() + character.get("mattack_mod").toint());
        character.set("mdefence", character.get("mdefence_base").toint() + character.get("mdefence_mod").toint());
        character.set("evasion", character.get("evasion_base").toint() + character.get("evasion_mod").toint());
    }

    public bot() throws Exception {
        globals = JsePlatform.standardGlobals();

        fill_itemDB();
        load_maps();
        load_warps();

        net.connect(this);

        globals.set("character", character);
        globals.set("inventory", inventory);
        globals.set("equipment", equipment);
        globals.set("storage", storage);
        globals.set("map_name", mapName);
        globals.set("beings", beings);
        globals.set("buy_sell", buy_sell);
        globals.set("items", items);
        globals.set("skills", skills);
        globals.set("trade_buy", trade_buy);
        globals.set("trade_sell", trade_sell);
        globals.set("client_time", clientTime);
        globals.set("warps", warps);
        globals.set("itemDB", itemDB);

        map = maps.get(mapName);
        if(map == null) throw new RuntimeException("map "+mapName+" doesn't exist!");

        beings.set(character.get("id"), character);

        globals.set("map_accessible", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int x1 = args.arg(1).toint();
                int y1 = args.arg(2).toint();
                int x2 = args.arg(3).toint();
                int y2 = args.arg(4).toint();

                LuaValue ret = valueOf(map.is_accesible(x1, y1, x2, y2));

                return varargsOf(new LuaValue[] {ret});
            }
        });

        globals.set("map_region", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String map_name = args.arg(1).toString();
                int x = args.arg(2).toint();
                int y = args.arg(3).toint();

                LuaValue ret = NIL;
                if(maps.containsKey(map_name)) {
                    Map map = maps.get(map_name);
                    ret = valueOf(map.get_region(x, y));
                }

                return varargsOf(new LuaValue[] {ret});
            }
        });

        globals.set("map_find_path", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int x1 = args.arg(1).toint();
                int y1 = args.arg(2).toint();
                int x2 = args.arg(3).toint();
                int y2 = args.arg(4).toint();

                LuaValue ret = map.find_path(x1, y1, x2, y2);

                return varargsOf(new LuaValue[] {ret});
            }
        });

        script = globals.loadfile("bot.lua");
        script.call();
        packetHandler = globals.get("packet_handler");
        loopBody = globals.get("loop_body");

        globals.set("send_packet", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                try {
                writeLock.acquire();

                try {
                    String pak = args.arg(1).toString();
//                    System.out.println("send_packet "+args);
                    Net.PacketOut po = null;
                    switch(pak) {
                        case "reload": {
                            script = globals.loadfile("bot.lua");
                            script.call();
                            packetHandler = globals.get("packet_handler");
                            loopBody = globals.get("loop_body");
                            System.out.println("reloaded");
                        } break;
                        case "walk": {
                            int x = args.arg(2).toint();
                            int y = args.arg(3).toint();
                            int dir = args.arg(4).toint();
                            po = net.newPacket(0x0085); // CMSG_PLAYER_CHANGE_DEST
                            po.writeCoordinates(x, y, dir);
                        } break;
                        case "talk": {
                            String msg = character.get("name") + " : " + args.arg(2).toString();
                            po = net.newPacket(0x008C); // CMSG_CHAT_MESSAGE
                            po.writeInt16(4 + msg.length() + 1);
                            po.writeString(msg.length() + 1, msg);
                        } break;
                        case "whisper": {
                            String nick = args.arg(2).toString();
                            String msg = args.arg(3).toString();
                            po = net.newPacket(0x0096); // CMSG_CHAT_WHISPER
                            po.writeInt16(msg.length() + 28);
                            po.writeString(24, nick);
                            po.writeString(msg.length(), msg);
                        } break;
                        case "storage_close": {
                            po = net.newPacket(0x00F7); // CMSG_CLOSE_STORAGE
                        } break;
                        case "equip": {
                            int index = args.arg(2).toint();
                            po = net.newPacket(0x00A9); // CMSG_PLAYER_EQUIP
                            po.writeInt16(index);
                            po.writeInt16(0);
                        } break;
                        case "unequip": {
                            int index = args.arg(2).toint();
                            po = net.newPacket(0x00AB); // CMSG_PLAYER_UNEQUIP
                            po.writeInt16(index);
                        } break;
                        case "use": {
                            int index = args.arg(2).toint();
                            LuaValue item = inventory.get(index);
                            po = net.newPacket(0x00A7); // CMSG_PLAYER_INVENTORY_USE
                            po.writeInt16(index);
                            po.writeInt32(item.get("id").toint());
                        } break;
                        case "drop": {
                            int index = args.arg(2).toint();
                            int amount = args.arg(3).toint();
                            if(amount < 1) amount = 1;
                            po = net.newPacket(0x00A2); // CMSG_PLAYER_INVENTORY_DROP
                            po.writeInt16(index);
                            po.writeInt16(amount);
                        } break;
                        case "to_storage": {
                            int index = args.arg(2).toint();
                            int amount = args.arg(3).toint();
                            if(amount < 1) amount = 1;
                            po = net.newPacket(0x00F3); // CMSG_MOVE_TO_STORAGE
                            po.writeInt16(index);
                            po.writeInt32(amount);
                        } break;
                        case "from_storage": {
                            int index = args.arg(2).toint();
                            int amount = args.arg(3).toint();
                            if(amount < 1) amount = 1;
                            po = net.newPacket(0x00F5); // CMSG_MOVE_FROM_STORAGE
                            po.writeInt16(index);
                            po.writeInt32(amount);
                        } break;
                        case "npc_talk": {
                            int npcId = args.arg(2).toint();
                            po = net.newPacket(0x0090); // CMSG_NPC_TALK
                            po.writeInt32(npcId);
                            po.writeInt8(0);
                        } break;
                        case "npc_buy_sell": {
                            int npcId = args.arg(2).toint();
                            boolean buy_sell = args.arg(3).toboolean(); // true for selling
                            po = net.newPacket(0x00C5); // CMSG_NPC_BUY_SELL_REQUEST
                            po.writeInt32(npcId);
                            po.writeInt8( buy_sell ? 1 : 0 );
                        } break;
                        case "npc_buy_item": {
                            int npcId = args.arg(2).toint();
                            int itemId = args.arg(3).toint();
                            int amount = args.arg(4).toint();
                            if(amount < 1) amount = 1;
                            po = net.newPacket(0x00C8); // CMSG_NPC_BUY_REQUEST
                            po.writeInt16(8); // one item (length of packet)
                            po.writeInt16(amount);
                            po.writeInt16(itemId);
                        } break;
                        case "npc_sell_item": {
                            int npcId = args.arg(2).toint();
                            int index = args.arg(3).toint();
                            int amount = args.arg(4).toint();
                            if(amount < 1) amount = 1;
                            po = net.newPacket(0x00C9); // CMSG_NPC_SELL_REQUEST
                            po.writeInt16(8);
                            po.writeInt16(index);
                            po.writeInt16(amount);
                        } break;
                        case "npc_next": {
                            int npcId = args.arg(2).toint();
                            po = net.newPacket(0x00B9); // CMSG_NPC_NEXT_REQUEST
                            po.writeInt32(npcId);
                        } break;
                        case "npc_close": {
                            int npcId = args.arg(2).toint();
                            po = net.newPacket(0x0146); // CMSG_NPC_CLOSE
                            po.writeInt32(npcId);
                        } break;
                        case "npc_choise": {
                            int npcId = args.arg(2).toint();
                            int choise = args.arg(3).toint();
                            po = net.newPacket(0x00B8); // CMSG_LIST_CHOISE
                            po.writeInt32(npcId);
                            po.writeInt8(choise);
                        } break;
                        case "npc_int_input": {
                            int npcId = args.arg(2).toint();
                            int value = args.arg(3).toint();
                            if(args.arg(3).isnil()) value = 0;
                            po = net.newPacket(0x0143); // CMSG_NPC_INT_RESPONSE
                            po.writeInt32(npcId);
                            po.writeInt32(value);
                        } break;
                        case "npc_str_input": {
                            int npcId = args.arg(2).toint();
                            String value = args.arg(3).toString();
                            if(args.arg(3).isnil()) value = "";
                            po = net.newPacket(0x01D5); // CMSG_NPC_STR_RESPONSE
                            po.writeInt16(value.length() + 9);
                            po.writeInt32(npcId);
                            po.writeString(value.length() + 1, value);
                        } break;
                        case "attack": {
                            int id = args.arg(2).toint();
                            po = net.newPacket(0x0089); // CMSG_PLAYER_ATTACK
                            po.writeInt32(id);
                            po.writeInt8(0);
                        } break;
                        case "emote": {
                            int emoteId = args.arg(2).toint();
                            po = net.newPacket(0x00BF); // CMSG_PLAYER_EMOTE
                            po.writeInt8(emoteId);
                        } break;
                        case "increase_attribute": {
                            String attr_name = args.arg(2).toString();
                            int attr = -1;
                            switch(attr_name) {
                                case "str": attr = 0; break;
                                case "agi": attr = 1; break;
                                case "vit": attr = 2; break;
                                case "int": attr = 3; break;
                                case "dex": attr = 4; break;
                                case "luk": attr = 5; break;
                            }
                            if(attr == -1) break;
                            po = net.newPacket(0x00BB); // CMSG_STAT_UPDATE_REQUEST
                            po.writeInt16(attr);
                            po.writeInt8(1);
                        } break;
                        case "increase_skill": {
                            int skillId = args.arg(2).toint();
                            po = net.newPacket(0x0112); // CMSG_SKILL_LEVELUP_REQUEST
                            po.writeInt16(skillId);
                        } break;
                        case "pickup": {
                            int id = args.arg(2).toint();
                            po = net.newPacket(0x009F); // CMSG_ITEM_PICKUP
                            po.writeInt32(id);
                        } break;
                        case "turn": {
                            int dir = args.arg(2).toint();
                            po = net.newPacket(0x009B); // CMSG_PLAYER_CHANGE_DIR
                            po.writeInt16(0);
                            po.writeInt8(dir);
                        } break;
                        case "action": {
                            String act = args.arg(2).toString();
                            int type;
                            if(act.equals("sit")) type = 2;
                            else if(act.equals("stand")) type = 3;
                            else break;
                            po = net.newPacket(0x0089); // CMSG_PLAYER_CHANGE_ACT
                            po.writeInt32(0);
                            po.writeInt8(type);
                        } break;
                        case "respawn": {
                            po = net.newPacket(0x00B2); // CMSG_PLAYER_RESTART
                            po.writeInt8(0);
                        } break;
                        case "trade_request": {
                            int id = args.arg(2).toint();
                            po = net.newPacket(0x00E4); // CMSG_TRADE_REQUEST
                            po.writeInt32(id);
                        } break;
                        case "trade_response": {
                            boolean accept = args.arg(2).toboolean();
                            po = net.newPacket(0x00E6); // CMSG_TRADE_RESPONSE
                            po.writeInt8(accept ? 3 : 4);
                        } break;
                        case "trade_add": {
                            int index = args.arg(2).toint();
                            int amount = args.arg(3).toint();
                            po = net.newPacket(0x00E8); // CMSG_TRADE_ITEM_ADD_REQUEST
                            po.writeInt16(index);
                            po.writeInt32(amount);
                        } break;
                        case "trade_set_money": {
                            int amount = args.arg(3).toint();
                            po = net.newPacket(0x00E8); // CMSG_TRADE_ITEM_ADD_REQUEST
                            po.writeInt16(0);
                            po.writeInt32(amount);
                        } break;
                        case "trade_confirm": {
                            po = net.newPacket(0x00EB); // CMSG_TRADE_ADD_COMPLETE
                        } break;
                        case "trade_finish": {
                            po = net.newPacket(0x00EF); // CMSG_TRADE_OK
                        } break;
                        case "trade_cancel": {
                            po = net.newPacket(0x00ED); // CMSG_TRADE_CANCEL
                        } break;
                        default:
                            writeLock.release();
                            return NIL;
                    }
                    if(po != null) {
                        po.send();
//                        System.out.println(po);
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }

                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
                writeLock.release();

                return NIL;
            }
        });

        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while(!quit) {
                        Net.PacketIn pi = net.readPacket();
                        int packet = pi.getId();
                        dataLock.acquire();
                        switch(packet) {
                            case 0x0078: // SMSG_BEING_VISIBLE
                            case 0x007B: { // SMSG_BEING_MOVE
                                int id = pi.readInt32();
                                if(id == character.get("id").toint()) System.out.println("character being_move");
                                int speed = pi.readInt16();
                                int stunMode = pi.readInt16();
                                int statusEffects = pi.readInt16();
                                statusEffects |= pi.readInt16() << 16;
                                int job = pi.readInt16();

                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    if(job == 0 && id >= 110000000) {
                                        pi.skip();
                                        break;
                                    } else {
                                        being = createBeing(id, job);
                                    }
                                }

                                being.set("id", id);
                                being.set("speed", speed);
                                being.set("stun_mode", stunMode);
                                being.set("status_effects", statusEffects);
                                being.set("hair_style", pi.readInt16());
                                being.set("weapon", pi.readInt16());
                                being.set("legs", pi.readInt16()); //headbottom

                                if(packet == 0x007B) {
                                    pi.skip(4);
                                }

                                being.set("shield", pi.readInt16());
                                being.set("helmet", pi.readInt16()); //headtop
                                being.set("armor", pi.readInt16()); //headmid
                                being.set("hair_color", pi.readInt16());
                                being.set("boots", pi.readInt16());
                                being.set("gloves", pi.readInt16());
                                being.set("guild", pi.readInt32());
                                pi.skip(4);
                                being.set("status_effect_block", pi.readInt16());
                                pi.skip(1);
                                being.set("gender", pi.readInt8());
                                if(packet == 0x007B) {
                                    pi.readCoordinatePair(being);
                                } else {
                                    pi.readCoordinates(being);
                                }
                                pi.skip(5);
                                if(!being.get("action").toString().equals("dead")) being.set("action", "stand");
                                being_update_path(being);
//                                System.out.println("id="+id+" stun_mode="+stunMode+" status_effects="+statusEffects+" status_effect_block="+being.get("status_effect_block"));
                                packetHandler.call(valueOf("being_update"), valueOf(id));
                            } break;
                            case 0x0095: { // SMSG_BEING_NAME_RESPONSE (30)
                                int id = pi.readInt32();
                                if(id == character.get("id").toint()) System.out.println("character being_name_response");
                                LuaValue being = beings.get(id);
                                if(being != NIL) {
                                    String name = pi.readString(24);
                                    being.set("name", name);
                                    packetHandler.call(valueOf("being_name"), valueOf(id));
                                } else {
                                    pi.skip();
                                }
                            } break;
                            case 0x007C: { // SMSG_BEING_SPAWN
                                int id = pi.readInt32();
                                if(id == character.get("id").toint()) System.out.println("character being_spawn");
                                pi.skip(14);
                                int job = pi.readInt16();
                                pi.skip(14);
                                LuaValue being = beings.get(id);
                                if(being == NIL) being = createBeing(id, job);
                                pi.readCoordinates(being);
                                if(!being.get("action").toString().equals("dead")) being.set("action", "stand");
                                being_update_path(being);
                                pi.skip(2);
                            } break;
                            case 0x0086: { // SMSG_BEING_MOVE_2
                                int id = pi.readInt32();
                                if(id == character.get("id").toint()) System.out.println("character being_move_2");
                                LuaValue being = beings.get(id);
                                if(being != NIL) {
                                    pi.readCoordinatePair(being);
                                    pi.skip(4);
                                    if(!being.get("action").toString().equals("dead")) being.set("action", "stand");
                                    being_update_path(being);
                                    packetHandler.call(valueOf("being_update"), valueOf(id));
                                } else {
                                    pi.skip(7);
                                }
                                pi.skip(3); // possibly coordinates
                            } break;
                            case 0x0080: { // SMSG_BEING_REMOVE
                                int id = pi.readInt32();
                                if(id == character.get("id").toint()) System.out.println("character being_remove");
                                LuaValue being = beings.get(id);
                                if(being != NIL) {
                                    if(pi.readInt8() == 1) {
                                        being.set("action", "dead");
                                    } else {
                                        beings.set(id, NIL);
                                    }
                                    packetHandler.call(valueOf("being_remove"), valueOf(id));
                                } else {
                                    pi.skip(1);
                                }
                            } break;
                            case 0x0148: { // SMSG_BEING_RESURRECT (8)
                                int id = pi.readInt32();
                                if(id == character.get("id").toint()) System.out.println("character being_resurrect");
                                LuaValue being = beings.get(id);
                                if(being != NIL) {
                                    if(pi.readInt8() == 1) {
                                        being.set("action", "stand");
                                    }
                                    packetHandler.call(valueOf("being_update"), valueOf(id));
                                } else {
                                    pi.skip(1);
                                }
                                pi.skip(1);
                            } break;
                            case 0x01DE: { // SMSG_SKILL_DAMAGE
                                pi.skip(2); // skill id
                                int srcId = pi.readInt32();
                                int dstId = pi.readInt32();
                                LuaValue srcBeing = beings.get(srcId);
                                LuaValue dstBeing = beings.get(dstId);
                                pi.skip(4);
                                int attackSpeed = pi.readInt32();
                                pi.skip(4); // dst speed
                                int dmg = pi.readInt32();
                                pi.skip(2); // skill level
                                pi.skip(2); // div
                                pi.skip(1); // skill hit/type (?)
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
                                int srcId = pi.readInt32();
                                if(srcId == character.get("id").toint()) System.out.println("character being_action src");
                                int dstId = pi.readInt32();
                                LuaValue srcBeing = beings.get(srcId);
                                LuaValue dstBeing = beings.get(dstId);
                                pi.skip(12); // 3 zero ints (?)
                                int param1 = pi.readInt16();
                                pi.skip(2); // param2
                                int type = pi.readInt8();
                                pi.skip(2); // param3
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
                                if(typeStr.equals("sit") || typeStr.equals("stand")) {
                                    if(srcBeing != NIL) srcBeing.set("action", typeStr);
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
                                int id = pi.readInt32();
                                if(id == character.get("id").toint()) System.out.println("character being_selfeffect");
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    pi.skip();
                                    break;
                                }
                                int effectType = pi.readInt32();
                                packetHandler.call(
                                    valueOf("being_selfeffect"),
                                    valueOf(id),
                                    valueOf(effectType)
                                );
                            } break;
                            case 0x00C0: { // SMSG_BEING_EMOTION (7)
                                int dstId = pi.readInt32();
                                int emote = pi.readInt8();
                                packetHandler.call(
                                    valueOf("being_emote"),
                                    valueOf(dstId),
                                    valueOf(emote)
                                );
                            } break;
                            case 0x00C3: // SMSG_BEING_CHANGE_LOOKS (8)
                            case 0x01D7: { // SMSG_BEING_CHANGE_LOOKS2 (11)
                                int dstId = pi.readInt32();
                                LuaValue being = beings.get(dstId);
                                if(being == NIL) {
                                    pi.skip();
                                    break;
                                }
                                int type = pi.readInt8();
                                int id = 0;
                                int id2 = 0;
                                String typeStr = "";
                                if(packet == 0x00C3) {
                                    id = pi.readInt8();
                                } else {
                                    id = pi.readInt16();
                                    id2 = pi.readInt16();
                                }
                                switch(type) {
                                    case 1:
                                        typeStr = "hair_type";
                                        being.set("hair_type", id);
                                    break;
                                    case 2:
                                        typeStr = "weapon+shield";
                                        being.set("weapon", id);
                                        being.set("shield", id2);
                                    break;
                                    case 3:
                                        typeStr = "legs";
                                        being.set("legs", id);
                                    break;
                                    case 4:
                                        typeStr = "helmet";
                                        being.set("helmet", id);
                                    break;
                                    case 5:
                                        typeStr = "armor";
                                        being.set("armor", id);
                                    break;
                                    case 6:
                                        typeStr = "hair_color";
                                        being.set("hair_color", id);
                                    break;
                                    case 8:
                                        typeStr = "shield";
                                        being.set("shield", id);
                                    break;
                                    case 9:
                                        typeStr = "boots";
                                        being.set("boots", id);
                                    break;
                                    case 10:
                                        typeStr = "gloves";
                                        being.set("gloves", id);
                                    break;
                                    case 11:
                                        typeStr = "cape";
                                        being.set("cape", id);
                                    break;
                                    case 12:
                                        typeStr = "misc1";
                                        being.set("misc1", id);
                                    break;
                                    case 13:
                                        typeStr = "misc2";
                                        being.set("misc2", id);
                                    break;
                                }
                                packetHandler.call(
                                    valueOf("being_change_looks"),
                                    valueOf(dstId),
                                    valueOf(typeStr)
                                );
                            } break;
                            case 0x0195: { // SMSG_PLAYER_GUILD_PARTY_INFO (102)
                                int id = pi.readInt32();
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    pi.skip();
                                    break;
                                }
                                being.set("party_name", pi.readString(24));
                                being.set("guild_name", pi.readString(24));
                                being.set("guild_pos", pi.readString(24));
                                pi.skip(24);
                                packetHandler.call(
                                    valueOf("being_guild_info"),
                                    valueOf(id)
                                );
                            } break;
                            case 0x009C: { // SMSG_BEING_CHANGE_DIRECTION (9)
                                int id = pi.readInt32();
                                if(id == character.get("id").toint()) System.out.println("character being_change_direction");
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    pi.skip();
                                    break;
                                }
                                pi.skip(2);
                                being.set("dir", pi.readInt8());
                                packetHandler.call(
                                    valueOf("being_update"),
                                    valueOf(id)
                                );
                            } break;
                            case 0x01D8: // SMSG_PLAYER_UPDATE_1 (54)
                            case 0x01D9: // SMSG_PLAYER_UPDATE_2 (53)
                            case 0x01DA: { // SMSG_PLAYER_MOVE (60)
                                int id = pi.readInt32();
                                if(id == character.get("id").toint()) System.out.println("character player_move");
                                int speed = pi.readInt16();
                                int stunMode = pi.readInt16();
                                int statusEffects = pi.readInt16();
                                statusEffects |= pi.readInt16() << 16;
                                int job = pi.readInt16();

                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    being = createBeing(id, job);
                                }

                                being.set("id", id);
                                being.set("speed", speed);
                                being.set("stun_mode", stunMode);
                                being.set("status_effects", statusEffects);
//                                System.out.println("id="+id+" stun_mode="+stunMode+" status_effects="+statusEffects);
                                being.set("hair_style", pi.readInt16());
                                being.set("weapon", pi.readInt16());
                                being.set("shield", pi.readInt16());
                                being.set("legs", pi.readInt16());

                                if(packet == 0x01DA) {
                                    pi.skip(4);
                                }

                                being.set("helmet", pi.readInt16());
                                being.set("armor", pi.readInt16());
                                being.set("hair_color", pi.readInt16());
                                being.set("boots", pi.readInt16());
                                being.set("gloves", pi.readInt16());
                                pi.skip(8);
                                being.set("status_effect_block", pi.readInt16());
                                pi.skip(1);
                                being.set("gender", pi.readInt8());
                                if(packet == 0x01DA) {
                                    pi.readCoordinatePair(being);
                                } else {
                                    pi.readCoordinates(being);
                                }
                                pi.skip(2); // gm status
                                if(packet == 0x01D8) {
                                    switch(pi.readInt8()) {
                                        case 1:
                                            being.set("action", "dead");
                                        break;
                                        case 2:
                                            being.set("action", "sit");
                                        break;
                                    }
                                } else if(packet == 0x01DA) {
                                    pi.skip(1);
                                }
                                if(packet != 0x01D8) {
                                    if(!being.get("action").toString().equals("dead")) being.set("action", "stand");
                                }
                                pi.skip(2);
                                being_update_path(being);
                                packetHandler.call(
                                    valueOf("player_update"),
                                    valueOf(id)
                                );
                            } break;
                            case 0x0088: { // SMSG_PLAYER_STOP (10)
                                int id = pi.readInt32();
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    pi.skip();
                                    break;
                                }
                                int x = pi.readInt16();
                                int y = pi.readInt16();
                                packetHandler.invoke(varargsOf(new LuaValue[] {
                                    valueOf("player_stop"),
                                    valueOf(id),
                                    valueOf(x),
                                    valueOf(y)
                                }));
                            } break;
                            case 0x0139: { // SMSG_PLAYER_MOVE_TO_ATTACK (16)
                                pi.skip();
                            } break;
                            case 0x0119: { // SMSG_PLAYER_STATUS_CHANGE (13)
                                int id = pi.readInt32();
                                LuaValue being = beings.get(id);
                                if(being == NIL) {
                                    pi.skip();
                                    break;
                                }
                                being.set("stun_mode", pi.readInt16());
                                int statusEffects = pi.readInt16();
                                statusEffects |= pi.readInt16() << 16;
                                being.set("status_effects", statusEffects);
                                pi.skip(1);
//                                System.out.println("id="+id+" stun_mode="+being.get("stun_mode")+" status_effects="+statusEffects);
                                packetHandler.call(
                                    valueOf("player_update"),
                                    valueOf(id)
                                );
                            } break;
                            case 0x0196: { // SMSG_BEING_STATUS_CHANGE (9)
                                int status = pi.readInt16();
                                int id = pi.readInt32();
                                int flag = pi.readInt8();
                            } break;
                            case 0x0098: { // SMSG_WHISPER_RESPONSE (3)
                                String type = "";
                                switch(pi.readInt8()) {
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
                                int msglen = pi.getLength() - 28;
                                if(msglen <= 0) {
                                    pi.skip();
                                    break;
                                }
                                String nick = pi.readString(24);
                                String msg = pi.readString(msglen);
                                packetHandler.call(
                                    valueOf("whisper"),
                                    valueOf(nick),
                                    valueOf(msg)
                                );
                            } break;
                            case 0x008D: { // SMSG_BEING_CHAT
                                int id = pi.readInt32();
                                int msglen = pi.getLength() - 8;
                                if(beings.get(id) == NIL || msglen <= 0) {
                                    pi.skip();
                                    break;
                                }
                                String msg = pi.readString(msglen);
                                packetHandler.call(
                                    valueOf("being_chat"),
                                    valueOf(id),
                                    valueOf(msg)
                                );
                            } break;
                            case 0x008E: // SMSG_PLAYER_CHAT
                            case 0x009A: { // SMSG_GM_CHAT
                                int msglen = pi.getLength() - 4;
                                if(msglen <= 0) {
                                    pi.skip();
                                    break;
                                }
                                String msg = pi.readString(msglen);
                                LuaValue gm_flag = (packet == 0x009A) ? TRUE : FALSE;
                                packetHandler.call(
                                    valueOf("player_chat"),
                                    gm_flag,
                                    valueOf(msg)
                                );
                            } break;
                            case 0x010C: { // SMSG_MVP (6)
                                int id = pi.readInt32();
                            } break;
                            case 0x01EE: // SMSG_PLAYER_INVENTORY
                            case 0x01F0: { // SMSG_PLAYER_STORAGE_ITEMS
                                if(packet == 0x01EE) {
                                    Utils.clearTable(inventory);
                                    Utils.clearTable(equipment);
                                } else {
                                    Utils.clearTable(storage);
                                }
                                int number = (pi.getLength() - 4) / 18;
                                for(int i=0; i!=number; ++i) {
                                    LuaTable item = new LuaTable();
                                    int index = pi.readInt16();
                                    item.set("index", index);
                                    int id = pi.readInt16();
                                    item.set("id", id);
                                    LuaValue item_db = itemDB.get(id);
                                    if(item_db != NIL) item.set("name", item_db.get("name"));
                                    pi.skip(1); // item type
                                    pi.skip(1); // identified
                                    item.set("amount", pi.readInt16());
                                    pi.skip(2); // arrow (?)
                                    pi.skip(8); // cards
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
                                int number = (pi.getLength() - 4) / 20;
                                for(int i=0; i!=number; ++i) {
                                    int index = pi.readInt16();
                                    LuaTable item = new LuaTable();
                                    item.set("index", index);
                                    int id = pi.readInt16();
                                    item.set("id", id);
                                    LuaValue item_db = itemDB.get(id);
                                    if(item_db != NIL) item.set("name", item_db.get("name"));
                                    pi.skip(1); // item type
                                    pi.skip(1); // identified
                                    pi.skip(2); // equip point (?)
                                    pi.skip(2); // another equip point (?)
                                    pi.skip(1); // attribute (broken)
                                    pi.skip(1); // refine level
                                    pi.skip(8); // cards
                                    storage.set(index, item);
                                }
                                packetHandler.call(valueOf("storage_update"));
                            } break;
                            case 0x00A0: { // SMSG_PLAYER_INVENTORY_ADD
                                int index = pi.readInt16();
                                int amount = pi.readInt16();
                                int id = pi.readInt16();
                                LuaTable item = new LuaTable();
                                item.set("index", index);
                                item.set("amount", amount);
                                item.set("id", id);
                                LuaValue item_db = itemDB.get(id);
                                if(item_db != NIL) item.set("name", item_db.get("name"));
                                pi.skip(1); // identified
                                pi.skip(1); // attribute
                                pi.skip(1); // refine
                                pi.skip(8); // cards
                                pi.skip(2); // equip type
                                pi.skip(1); // item type
                                int status = pi.readInt8();
//                                System.out.println("inventory_add ["+index+"] "+id+" "+amount+" "+status);
                                if(status == 0) {
                                    LuaValue item1 = inventory.get(index);
                                    if(item1 != NIL) {
                                        item1.set("amount", item1.get("amount").toint() + amount);
                                    } else {
                                        inventory.set(index, item);
                                    }
                                }
                                packetHandler.call(valueOf("inventory_update"));
                            } break;
                            case 0x00AF: { // SMSG_PLAYER_INVENTORY_REMOVE
                                int index = pi.readInt16();
                                int amount = pi.readInt16();
//                                System.out.println("inventory_remove ["+index+"] "+amount);
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
                                int index = pi.readInt16();
                                pi.skip(2); // item id
                                pi.skip(4); // id
                                int amount = pi.readInt16();
                                pi.skip(1); // type
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
                                int index = pi.readInt16();
                                int amount = pi.readInt16();
                                LuaValue result = TRUE;
                                if(pi.readInt8() == 0) {
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
                                pi.skip(2); // used count (?)
                                int size = pi.readInt16(); // max size
                                globals.set("storage_size", size);
                                packetHandler.call(valueOf("storage_status"));
                            } break;
                            case 0x00F4: { // SMSG_PLAYER_STORAGE_ADD
                                int index = pi.readInt16();
                                int amount = pi.readInt32();
                                LuaTable item = new LuaTable();
                                item.set("index", index);
                                item.set("amount", amount);
                                int id = pi.readInt16();
                                item.set("id", id);
                                LuaValue item_db = itemDB.get(id);
                                if(item_db != NIL) item.set("name", item_db.get("name"));
                                pi.skip(1); // identified
                                pi.skip(1); // attribute
                                pi.skip(1); // refine
                                pi.skip(8); // cards
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
                                int index = pi.readInt16();
                                int amount = pi.readInt32();
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
                                Utils.clearTable(equipment);
                                int number = (pi.getLength() - 4) / 20;
                                for(int i=0; i!=number; ++i) {
                                    int index = pi.readInt16();
                                    LuaTable item = new LuaTable();
                                    item.set("index", index);
                                    int id = pi.readInt16();
                                    item.set("id", id);
                                    LuaValue item_db = itemDB.get(id);
                                    if(item_db != NIL) item.set("name", item_db.get("name"));
                                    pi.skip(1); // item type
                                    pi.skip(1); // identified
                                    pi.skip(2); // equip type
                                    int type = pi.readInt16();
                                    if(type != 0) {
                                        item.set("equip", equipType(type));
                                    }
                                    pi.skip(1); // attribute
                                    pi.skip(1); // refine
                                    pi.skip(8); // cards
                                    if(type == 0) {
                                        inventory.set(index, item);
                                    } else {
                                        equipment.set(index, item);
                                    }
                                }
                                packetHandler.call(valueOf("inventory_update"));
                            } break;
                            case 0x00AA: { // SMSG_PLAYER_EQUIP
                                int index = pi.readInt16();
                                int type = pi.readInt16();
                                LuaValue result = TRUE;
                                if(pi.readInt8() == 0) {
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
                                int index = pi.readInt16();
                                int type = pi.readInt16();
                                LuaValue result = TRUE;
                                if(pi.readInt8() == 0) {
                                    // unable to unequip
                                    result = FALSE;
                                } else {
                                    LuaValue item = equipment.get(index);
                                    if(item != NIL) {
                                        item.set("equip", NIL);
                                        equipment.set(index, NIL);
                                        inventory.set(index, item);
                                        character.set("range", NIL);
                                    }
                                }
                                packetHandler.call(
                                    valueOf("unequip"),
                                    result,
                                    valueOf(index)
                                );
                            } break;
                            case 0x013A: { // SMSG_PLAYER_ATTACK_RANGE
                                character.set("range", pi.readInt16());
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x013C: { // SMSG_PLAYER_ARROW_EQUIP
                                int index = pi.readInt16();
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
                                int npcId = pi.readInt32();
                                packetHandler.call(
                                    valueOf("buy_sell_choise"),
                                    valueOf(npcId)
                                );
                            } break;
                            case 0x00C6: { // SMSG_NPC_BUY
                                int count = (pi.getLength() - 4) / 11;
                                Utils.clearTable(buy_sell);
                                for(int i=0; i!=count; ++i) {
                                    int value = pi.readInt32();
                                    pi.skip(4); // dcvalue (?)
                                    pi.skip(1); // type
                                    int itemId = pi.readInt16();
                                    LuaTable item = new LuaTable();
                                    item.set("id", itemId);
                                    LuaValue item_db = itemDB.get(itemId);
                                    if(item_db != NIL) item.set("name", item_db.get("name"));
                                    item.set("value", value);
                                    buy_sell.set(i+1, item);
                                }
                                packetHandler.call(valueOf("buy_items"));
                            } break;
                            case 0x00C7: { // SMSG_NPC_SELL
                                int count = (pi.getLength() - 4) / 10;
                                Utils.clearTable(buy_sell);
                                for(int i=0; i!=count; ++i) {
                                    int index = pi.readInt16();
                                    int value = pi.readInt32();
                                    pi.skip(4); // ocvalue
                                    LuaValue item = inventory.get(index);
                                    if(item != NIL) {
                                        LuaTable sitem = new LuaTable();
                                        sitem.set("id", item.get("id"));
                                        sitem.set("name", item.get("name"));
                                        sitem.set("index", item.get("index"));
                                        sitem.set("amount", item.get("amount"));
                                        sitem.set("value", value);
                                        buy_sell.set(index, sitem);
                                    }
                                }
                                packetHandler.call(valueOf("sell_items"));
                            } break;
                            case 0x00CA: { // SMSG_NPC_BUY_RESPONSE
                                packetHandler.call(
                                    valueOf("buy_response"),
                                    (pi.readInt8() == 0) ? TRUE : FALSE
                                );
                            } break;
                            case 0x00CB: { // SMSG_NPC_SELL_RESPONSE
                                packetHandler.call(
                                    valueOf("sell_response"),
                                    (pi.readInt8() == 0) ? TRUE : FALSE
                                );
                            } break;
                            case 0x009D: // SMSG_ITEM_VISIBLE
                            case 0x009E: { // SMSG_ITEM_DROPPED
                                int id = pi.readInt32();
                                LuaTable item = new LuaTable();
                                item.set("id", id);
                                int itemId = pi.readInt16();
                                item.set("item_id", itemId);
                                LuaValue item_db = itemDB.get(itemId);
                                if(item_db != NIL) item.set("name", item_db.get("name"));
                                pi.skip(1); // identified
                                item.set("x", pi.readInt16());
                                item.set("y", pi.readInt16());
                                pi.skip(4); // amount,subX,subY / subX,subY,amount
                                items.set(id, item);
                                packetHandler.call(valueOf("item_update"), valueOf(id));
                            } break;
                            case 0x00A1: { // SMSG_ITEM_REMOVE
                                int id = pi.readInt32();
                                items.set(id, NIL);
                                packetHandler.call(valueOf("item_remove"), valueOf(id));
                            } break;
                            case 0x00B7: { // SMSG_NPC_CHOISE
                                int npcId = pi.readInt32();
                                String msg = pi.readString(pi.getLength() - 8);
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
                                int npcId = pi.readInt32();
                                String msg = pi.readString(pi.getLength() - 8);
                                packetHandler.call(
                                    valueOf("npc_message"),
                                    valueOf(msg)
                                );
                            } break;
                            case 0x00B6: { // SMSG_NPC_CLOSE
                                int npcId = pi.readInt32();
                                packetHandler.call(valueOf("npc_close"), valueOf(npcId));
                            } break;
                            case 0x00B5: { // SMSG_NPC_NEXT
                                int npcId = pi.readInt32();
                                packetHandler.call(valueOf("npc_next"), valueOf(npcId));
                            } break;
                            case 0x0142: { // SMSG_NPC_INT_INPUT
                                int npcId = pi.readInt32();
                                packetHandler.call(valueOf("npc_int_input"), valueOf(npcId));
                            } break;
                            case 0x01D4: { // SMSG_NPC_STR_INPUT
                                int npcId = pi.readInt32();
                                packetHandler.call(valueOf("npc_str_input"), valueOf(npcId));
                            } break;
                            case 0x0087: { // SMSG_WALK_RESPONSE
                                pi.skip(4);
                                pi.readCoordinatePair(character);
                                being_update_path(character);
                                packetHandler.call(valueOf("walk_response"));
                                pi.skip(1);
                            } break;
                            case 0x0091: { // SMSG_PLAYER_WARP
                                Utils.clearTable(beings);
                                beings.set(character.get("id"), character);
                                Utils.clearTable(items);

                                String dstMap = pi.readString(16);
                                int x = pi.readInt16();
                                int y = pi.readInt16();
                                if(!dstMap.equals(mapName)) {
                                    map = maps.get(dstMap);
                                    if(map == null) throw new RuntimeException("map "+dstMap+" doesn't exist!");
                                }
                                mapLoaded = true;
                                mapName = dstMap;
                                System.out.println("map = "+mapName);
                                globals.set("map_name", mapName);
                                character.set("x", x);
                                character.set("y", y);
                                character.set("dst_x", x);
                                character.set("dst_y", y);
                                being_update_path(character);
                                packetHandler.call(valueOf("player_warp"));
                            } break;
                            case 0x00B0: { // SMSG_PLAYER_STAT_UPDATE_1
                                int type = pi.readInt16();
                                int value = pi.readInt32();
                                switch(type) {
                                    case 0x0000: character.set("speed", value); break;
                                    case 0x0004: break; // manner
                                    case 0x0005: character.set("hp", value); break;
                                    case 0x0006: character.set("hp_max", value); break;
                                    case 0x0007: character.set("mp", value); break;
                                    case 0x0008: character.set("mp_max", value); break;
                                    case 0x0009: character.set("char_points", value); break;
                                    case 0x000B: character.set("level", value); break;
                                    case 0x000C: character.set("skill_points", value); break;
                                    case 0x0018: character.set("weight", value); break;
                                    case 0x0019: character.set("weight_max", value); break;
                                    case 0x0029: character.set("attack_base", value); break;
                                    case 0x002A: character.set("attack_mod", value); break;
                                    case 0x002B: character.set("mattack_base", value); break;
                                    case 0x002C: character.set("mattack_mod", value); break;
                                    case 0x002D: character.set("defence_base", value); break;
                                    case 0x002E: character.set("defence_mod", value); break;
                                    case 0x002F: character.set("mdefence_base", value); break;
                                    case 0x0030: character.set("mdefence_mod", value); break;
                                    case 0x0031: character.set("hit", value); break;
                                    case 0x0032: character.set("evasion_base", value); break;
                                    case 0x0033: character.set("evasion_mod", value); break;
                                    case 0x0034: character.set("critical", value); break;
                                    case 0x0035: character.set("attack_speed", value); break;
                                    case 0x0037: character.set("job_base", value); break;
                                    case 500: character.set("gm_level", value); break;
                                }
                                update_character_stats();
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x00B1: { // SMSG_PLAYER_STAT_UPDATE_2
                                int type = pi.readInt16();
                                int value = pi.readInt32();
                                switch(type) {
                                    case 0x0001: character.set("exp", value); break;
                                    case 0x0002: character.set("job_exp", value); break;
                                    case 0x0014: character.set("money", value); break;
                                    case 0x0016: character.set("exp_need", value); break;
                                    case 0x0017: character.set("job_need", value); break;
                                }
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x0141: { // SMSG_PLAYER_STAT_UPDATE_3
                                int type = pi.readInt32();
                                int base = pi.readInt32();
                                int bonus = pi.readInt32();
                                switch(type) {
                                    case 0:
                                        character.set("str_base", base);
                                        character.set("str_mod", bonus);
                                    break;
                                    case 1:
                                        character.set("agi_base", base);
                                        character.set("agi_mod", bonus);
                                    break;
                                    case 2:
                                        character.set("vit_base", base);
                                        character.set("vit_mod", bonus);
                                    break;
                                    case 3:
                                        character.set("int_base", base);
                                        character.set("int_mod", bonus);
                                    break;
                                    case 4:
                                        character.set("dex_base", base);
                                        character.set("dex_mod", bonus);
                                    break;
                                    case 5:
                                        character.set("luk_base", base);
                                        character.set("luk_mod", bonus);
                                    break;
                                }
                                update_character_stats();
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x00BC: { // SMSG_PLAYER_STAT_UPDATE_4
                                int type = pi.readInt16();
                                int ok = pi.readInt8();
                                int value = pi.readInt8();
                                if(ok == 1) {
                                    switch(type) {
                                        case 0: character.set("str_base", value); break;
                                        case 1: character.set("agi_base", value); break;
                                        case 2: character.set("vit_base", value); break;
                                        case 3: character.set("int_base", value); break;
                                        case 4: character.set("dex_base", value); break;
                                        case 5: character.set("luk_base", value); break;
                                    }
                                    update_character_stats();
                                }
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x00BD: { // SMSG_PLAYER_STAT_UPDATE_5
                                character.set("char_points", pi.readInt16());
                                character.set("str_base", pi.readInt8());
                                character.set("str_need", pi.readInt8());
                                character.set("agi_base", pi.readInt8());
                                character.set("agi_need", pi.readInt8());
                                character.set("vit_base", pi.readInt8());
                                character.set("vit_need", pi.readInt8());
                                character.set("int_base", pi.readInt8());
                                character.set("int_need", pi.readInt8());
                                character.set("dex_base", pi.readInt8());
                                character.set("dex_need", pi.readInt8());
                                character.set("luk_base", pi.readInt8());
                                character.set("luk_need", pi.readInt8());
                                character.set("attack_base", pi.readInt16());
                                character.set("attack_mod", pi.readInt16());
                                character.set("mattack_base", pi.readInt16());
                                character.set("mattack_mod", pi.readInt16());
                                character.set("defence_base", pi.readInt16());
                                character.set("defence_mod", pi.readInt16());
                                character.set("mdefence_base", pi.readInt16());
                                character.set("mdefence_mod", pi.readInt16());
                                character.set("hit", pi.readInt16());
                                character.set("evasion_base", pi.readInt16());
                                character.set("evasion_mod", pi.readInt16());
                                character.set("critical", pi.readInt16());
                                pi.skip(4); // karma, manner
                                update_character_stats();
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x00BE: { // SMSG_PLAYER_STAT_UPDATE_6
                                int type = pi.readInt16();
                                int value = pi.readInt8();
                                switch(type) {
                                    case 0x0020: character.set("str_need", value); break;
                                    case 0x0021: character.set("agi_need", value); break;
                                    case 0x0022: character.set("vit_need", value); break;
                                    case 0x0023: character.set("int_need", value); break;
                                    case 0x0024: character.set("dex_need", value); break;
                                    case 0x0025: character.set("luk_need", value); break;
                                }
                                update_character_stats();
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x013B: { // SMSG_PLAYER_ARROW_MESSAGE
                                int type = pi.readInt16();
                            } break;
                            case 0x010F: { // SMSG_PLAYER_SKILLS
                                Utils.clearTable(skills);
                                int count = (pi.getLength() - 4) / 37;
                                for(int i=0; i!=count; ++i) {
                                    int id = pi.readInt16();
                                    LuaTable skill = new LuaTable();
                                    skill.set("id", id);
                                    pi.skip(2); // target type
                                    pi.skip(2); // unused
                                    skill.set("level", pi.readInt16());
                                    pi.skip(2); // sp
                                    pi.skip(2); // range
                                    pi.skip(24); // unused
                                    skill.set("up", pi.readInt8());
                                    skills.set(id, skill);
                                }
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x010E: { // SMSG_PLAYER_SKILL_UP
                                int id = pi.readInt16();
                                LuaTable skill = new LuaTable();
                                skill.set("id", id);
                                skill.set("level", pi.readInt16());
                                pi.skip(2); // sp
                                pi.skip(2); // range
                                skill.set("up", pi.readInt8());
                                skills.set(id, skill);
                                packetHandler.call(valueOf("char_update"));
                            } break;
                            case 0x0110: { // SMSG_SKILL_FAILED
                                pi.skip();
                            } break;
                            case 0x00E5: { // SMSG_TRADE_REQUEST
                                String name = pi.readString(24);
                                packetHandler.call(valueOf("trade_request"), valueOf(name));
                            } break;
                            case 0x00E7: { // SMSG_TRADE_RESPONSE
                                String result = "";
                                switch(pi.readInt8()) {
                                    case 0: result = "far_away"; break;
                                    case 1: result = "not_exist"; break;
                                    case 2: result = "fail"; break;
                                    case 3: result = "ok"; break;
                                    case 4: result = "cancel"; break;
                                }
                                Utils.clearTable(trade_buy);
                                Utils.clearTable(trade_sell);
                                packetHandler.call(valueOf("trade_response"), valueOf(result));
                            } break;
                            case 0x00E9: { // SMSG_TRADE_ITEM_ADD
                                int amount = pi.readInt32();
                                int id = pi.readInt16();
//                                System.out.println("trade_item_add "+id+" "+amount);
                                pi.skip(1); // identified
                                pi.skip(1); // attribute
                                pi.skip(1); // refine
                                pi.skip(8); // cards
                                if(id == 0) {
                                    trade_buy.set("money", amount);
                                } else {
                                    LuaTable item = new LuaTable();
                                    item.set("id", id);
                                    item.set("amount", amount);
                                    trade_buy.set(id, item);
                                }
                                packetHandler.call(valueOf("trade_update"));
                            } break;
                            case 0x01B1: { // SMSG_TRADE_ITEM_ADD_RESPONSE
                                int index = pi.readInt16();
                                int amount = pi.readInt16();
                                int status = pi.readInt8();
//                                System.out.println("trade_add_response "+index+" "+amount+" "+status);
                                String result;
                                if(inventory.get(index) == NIL && equipment.get(index) == NIL) {
                                    result = "ok";
                                } else {
                                    if(status == 0 && amount > 0) {
                                        LuaValue item = inventory.get(index);
                                        if(item != NIL) {
                                            LuaTable sitem = new LuaTable();
                                            sitem.set("index", item.get("index"));
                                            sitem.set("id", item.get("id"));
                                            sitem.set("amount", item.get("amount"));
                                            if(sitem.get("amount") != NIL) {
                                                sitem.set("amount", amount);
                                            }
                                            trade_sell.set(index, sitem);
                                        }
                                    }
                                    switch(status) {
                                        case 0: // success
                                            result = "ok";
                                        break;
                                        case 1:
                                            result = "overweighted";
                                        break;
                                        case 2:
                                            result = "no_slot";
                                        break;
                                        default:
                                            result = "fail";
                                    }
                                }
                                packetHandler.call(valueOf("trade_add_response"), valueOf(result));
                            } break;
                            case 0x00EC: { // SMSG_TRADE_OK
                                packetHandler.call(valueOf("trade_confirm"), valueOf(pi.readInt8()));
                            } break;
                            case 0x00EE: { // SMSG_TRADE_CANCEL
                                Utils.clearTable(trade_buy);
                                Utils.clearTable(trade_sell);
                                packetHandler.call(valueOf("trade_cancel"));
                            } break;
                            case 0x00F0: { // SMSG_TRADE_COMPLETE
                                int status = pi.readInt8();
                                String result = status==0 ? "ok" : "fail";
                                if(status == 0) {
                                    LuaValue k = NIL;
                                    while(true) {
                                        Varargs n = trade_sell.next(k);
                                        if( (k=n.arg1()).isnil() ) break;
                                        int index = k.toint();
                                        if(index > 0) {
                                            boolean remove = false;
                                            LuaValue sitem = n.arg(2);
                                            LuaValue item = inventory.get(index);
                                            if(item != NIL) {
                                                int amount = item.get("amount").toint();
                                                if(amount > 0) amount -= sitem.get("amount").toint();
                                                item.set("amount", amount);
                                                if(amount < 1) inventory.set(index, NIL);
                                            }
                                        } else if(k.toString().equals("money")) {
                                            character.set("money", character.get("money").toint() - n.arg(2).toint());
                                        }
                                    }
                                }
                                Utils.clearTable(trade_buy);
                                Utils.clearTable(trade_sell);
                                packetHandler.call(valueOf("trade_complete"), valueOf(result));
                            } break;
                            default:
                                pi.skip();
                        }
                        pi.close();
//                        System.out.println(pi);
                        dataLock.release();
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
            dataLock.acquire();

            if(mapLoaded) {
                writeLock.acquire();

                Net.PacketOut po = net.newPacket(0x007D); // CMSG_MAP_LOADED
                po.send();

                writeLock.release();
                mapLoaded = false;
            }

            clientTime = (int)(System.nanoTime() / 1000000);
            globals.set("client_time", clientTime);

            LuaValue k = NIL;
            while(true) {
                Varargs n = beings.next(k);
                if( (k = n.arg1()).isnil() )
                    break;
                LuaValue being = n.arg(2);
                int x = being.get("x").toint();
                int y = being.get("y").toint();
                int dst_x = being.get("dst_x").toint();
                int dst_y = being.get("dst_y").toint();
                if(x!=dst_x || y!=dst_y) {
                    LuaValue path = being.get("path");
                    int path_index = being.get("path_index").toint();
                    int walk_time = being.get("walk_time").toint();
                    int dir = being.get("dir").toint();

                    if(path == NIL) {
                        path = map.find_path(x, y, dst_x, dst_y);
                        path_index = 1;
                    }
                    if(path != NIL) {
                        if(walk_time == 0) walk_time = clientTime + being.get("speed").toint();

                        int length = path.length();
                        while(walk_time < clientTime && path_index <= length) {
                            walk_time = clientTime + being.get("speed").toint();
                            LuaValue p = path.get(path_index);
                            if(p.get("x").toint() != x || p.get("y").toint() != y) {
                                System.out.println("path position mismatch!");
                                System.out.println("id="+being.get("id"));
                                if(being.get("name")!=NIL) System.out.println("name="+being.get("name"));
                                System.out.println("x=" + x + " y=" + y);
                                System.out.println("dst_x="+dst_x+" dst_y="+dst_y);
                                System.out.println("path_index="+path_index);
                                map.x1 = x;
                                map.y1 = y;
                                map.x2 = dst_x;
                                map.y2 = dst_y;
                                map.printMap(Math.min(x,dst_x)-5, Math.min(y,dst_y)-5, Math.max(x,dst_x)+5, Math.max(y,dst_y)+5);
                                for(int i=1; i<=length; ++i) {
                                    p = path.get(i);
                                    System.out.println(p.get("x") + ", "+p.get("y"));
                                }
                                System.exit(1);
                            }
                            ++path_index;
                            if(path_index > length) break;
                            p = path.get(path_index);
                            int px = p.get("x").toint();
                            int py = p.get("y").toint();
                            if(py < y) {
                                dir = 4; // up
                            } else if(py > y) {
                                dir = 1; // down
                            } else {
                                if(px < x) {
                                    dir = 8; // left
                                } else if(px > x) {
                                    dir = 2; // right
                                }
                            }
                            x = px;
                            y = py;
                        }
                    }

                    being.set("path", path);
                    being.set("path_index", path_index);
                    being.set("walk_time", walk_time);
                    being.set("x", x);
                    being.set("y", y);
                    being.set("dir", dir);
                } else {
                    being.set("path", NIL);
                }
            }

            LuaValue ret = loopBody.call();
            if(ret == FALSE) {
                quit = true;
                break;
            }
            dataLock.release();

            Thread.sleep(10);
        }

    }

    public static void main(String[] args) throws Exception {
        bot instance = new bot();
    }
}
