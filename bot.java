import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;

class Character {
    public int x;
    public int y;
    public int dir;

    public int id;
    public int exp;
    public int money;
    public int job_exp;
    public int job_level;
    public int shoes;
    public int gloves;
    public int cape;
    public int misc1;
    public int hp;
    public int max_hp;
    public int mp;
    public int max_mp;
    public int species;
    public int hair;
    public int weapon;
    public int level;
    public int legs;
    public int shield;
    public int head;
    public int torso;
    public int hair_color;
    public int misc2;
    public String name;
    public int st_str;
    public int st_agi;
    public int st_vit;
    public int st_int;
    public int st_dex;
    public int st_luk;
    public int char_slot;

    public int char_points;
    public int skill_points;
    public int total_weight;
    public int max_weight;
    public int atk_base;
    public int atk_mod;
    public int matk_base;
    public int matk_mod;
    public int def_base;
    public int def_mod;
    public int mdef_base;
    public int mdef_mod;
    public int hit_base;
    public int flee_base;
    public int flee_mod;
    public int crit_base;
    public int attack_speed;
    public int job_base;

    public Character() {}

    public void print() {
        System.out.println("[" + char_slot + "] " + name + " (" + level + " lvl)");
    }
}

public class bot {
    static char[] hexDigits = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    static void printHexDigit(int d) {
        if(d >= 0 && d < 16) System.out.append(hexDigits[d]);
    }

    static void printHexByte(int b) {
        printHexDigit((b >> 4) & 15);
        printHexDigit(b & 15);
    }

    static void printHexShort(int s) {
        printHexByte( (s >> 8) & 255 );
        printHexByte( s & 255 );
    }

    static void printHexInt(int s) {
        printHexByte( (s >> 24) & 255 );
        printHexByte( (s >> 16) & 255 );
        printHexByte( (s >> 8) & 255 );
        printHexByte( s & 255 );
    }

    Socket sock;
    OutputStream out;
    InputStream in;

    String charServerIp;
    int charServerPort;

    int acid;
    int sid1;
    int sid2;
    int gender;

    Character character;

    String mapServerIp;
    int mapServerPort;

    public void writeByte(int b) throws IOException {
        out.write(b);
    }

    public void writeShort(int v) throws IOException {
        out.write( v & 255 );
        out.write( (v>>8) & 255 );
    }

    public void writeInt(int v) throws IOException {
        out.write( v & 255 );
        out.write( (v>>8) & 255 );
        out.write( (v>>16) & 255 );
        out.write( (v>>24) & 255 );
    }

    public void writeString(int len, String str) throws IOException {
        byte[] b = str.getBytes();
        for(int i=0; i!=len; ++i) {
            if(i < b.length) out.write(b[i]);
            else out.write(0);
        }
    }

    public int readByte() throws IOException {
        int b = in.read();
        if(b == -1) throw new IOException("socket closed");
        return b;
    }

    public int readShort() throws IOException {
        int b1 = readByte();
        int b2 = readByte();
        return b1 + (b2<<8);
    }

    public int readInt() throws IOException {
        int b1 = readByte();
        int b2 = readByte();
        int b3 = readByte();
        int b4 = readByte();
        return b1 + (b2<<8) + (b3<<16) + (b4<<24);
    }

    public void readHex() throws IOException {
        for(;;) {
            printHexByte(readByte());
        }
    }

    public String readString(int len) throws IOException {
        StringBuilder sb = new StringBuilder();
        boolean append = true;
        for(; len>0; --len) {
            char c = (char)in.read();
            if(c == 0) append = false;
            if(append) sb.append(c);
        }
        return sb.toString();
    }

    public Character readCharacter() throws IOException {
        Character c = new Character();
        c.id = readInt();
        c.exp = readInt();
        c.money = readInt();
        c.job_exp = readInt();
        c.job_level = readInt();
        c.shoes = readShort();
        c.gloves = readShort();
        c.cape = readShort();
        c.misc1 = readShort();
        in.skip(14);
        c.hp = readShort();
        c.max_hp = readShort();
        c.mp = readShort();
        c.max_mp = readShort();
        in.skip(2);
        c.species = readShort();
        c.hair = readShort();
        c.weapon = readShort();
        c.level = readShort();
        in.skip(2);
        c.legs = readShort();
        c.shield = readShort();
        c.head = readShort();
        c.torso = readShort();
        c.hair_color = readShort();
        c.misc2 = readShort();
        c.name = readString(24);
        c.st_str = readByte();
        c.st_agi = readByte();
        c.st_vit = readByte();
        c.st_int = readByte();
        c.st_dex = readByte();
        c.st_luk = readByte();
        c.char_slot = readByte();
        in.skip(1);

        return c;
    }

    public Character readPosition(Character c) throws IOException {
        int b0 = readByte();
        int b1 = readByte();
        int b2 = readByte();
        c.x = ((b1 & 0xC0) + ((b0 & 0xFF) << 8)) >> 6;
        c.y = ((b2 & 0xF0) + ((b1 & 0x3F) << 8)) >> 4;
        c.dir = b2 & 0x0F;

        return c;
    }

    public void expectPacket(int p) throws IOException {
        int packet = readShort();
        if(packet != p) {
            System.out.append("unexpected packet ");
            printHexShort(packet);
        }
    }

    public bot() throws Exception {
        sock = new Socket("server.themanaworld.org", 6901); // login server
        out = sock.getOutputStream();
        in = sock.getInputStream();

        writeShort(0x7530); // version request

        expectPacket(0x7531); // version response
        {
            int major = readByte();
            int minor = readByte();
            int patch = readByte();
            int devel = readByte();
            int flags = readByte();
            int which = readByte();
            int vendor = readShort();
        }

        writeShort(0x0064); // login request
        writeInt(0);
        writeString(24, "tmwbot");
        writeString(24, "m1ghtyb0t");
        writeByte(2);

        int packet = readShort();
        if(packet == 0x0063) { // update host
            int len = readShort() - 4;
            System.out.println(readString(len));

            packet = readShort();
        }
        if(packet == 0x006A) {
            System.out.println("login error");
            readHex();
            System.exit(1);
        }
        if(packet == 0x0081) {
            System.out.println("connection problem");
            readHex();
            System.exit(1);
        }
        if(packet == 0x0069) { // login data
            int len = readShort();
            int worldCount = (len - 47) / 32;

            sid1 = readInt();
            acid = readInt();
            sid2 = readInt();

            System.out.append("Session ID : ");
            printHexInt(sid1);
            printHexInt(sid2);
            System.out.append("\nAccount ID : ");
            printHexInt(acid);
            System.out.println();

            in.skip(30);

            gender = readByte();

            for(int i=0; i!=worldCount; ++i) {
                charServerIp = "" + readByte() + "." + readByte() + "." + readByte() + "." + readByte();
                charServerPort = readShort() & 0x0000FFFF;
                String name = readString(20);
                int online = readInt();

                System.out.println(charServerIp + ":" + charServerPort + " " + name + " (" + online + ")");
                in.skip(2);
            }
        }

        sock.close(); // disconnect from login server

        sock = new Socket(charServerIp, charServerPort); // char server
        out = sock.getOutputStream();
        in = sock.getInputStream();

        writeShort(0x0065); // character server connection request
        writeInt(acid);
        writeInt(sid1);
        writeInt(sid2);
        writeShort(1);
        writeByte(1);

        in.skip(4);

        expectPacket(0x006B); // update character list
        {
            int len = readShort();
            in.skip(20);
            int charCount = (len - 24) / 106;
            character = readCharacter();
            character.print();
        }

        writeShort(0x0066); // select character request
        writeByte(0);

        expectPacket(0x0071); // SMSG_CHAR_MAP_INFO
        in.skip(4);
        String mapName = readString(16);
        mapServerIp = "" + readByte() + "." + readByte() + "." + readByte() + "." + readByte();
        mapServerPort = readShort() & 0x0000FFFF;
        System.out.println(mapServerIp + ":" + mapServerPort + " " + mapName);

        sock.close();

        sock = new Socket(mapServerIp, mapServerPort); // char server
        out = sock.getOutputStream();
        in = sock.getInputStream();

        writeShort(0x0072); // CMSG_MAP_SERVER_CONNECT
        writeInt(acid);
        writeInt(character.id);
        writeInt(sid1);
        writeInt(sid2);
        writeByte(gender);

        in.skip(4);

        expectPacket(0x0073); // SMSG_MAP_LOGIN_SUCCESS
        in.skip(4); // server tick
        readPosition(character);
        in.skip(2);
        System.out.println(character.x + ", " + character.y + ", " + character.dir);

        boolean mapLoaded = true;

        for(;;) {
            if(mapLoaded) {
                writeShort(0x007D); // CMSG_MAP_LOADED
                mapLoaded = false;
            }

            packet = readShort();
            switch(packet) {
                case 0x0004: break; // unknown
                case 0x0078: { // SMSG_BEING_VISIBLE
                    in.skip(52);
                } break;
                case 0x007B: { // SMSG_BEING_MOVE
                    in.skip(58);
                } break;
                case 0x007C: { // SMSG_BEING_SPAWN
                    in.skip(39);
                } break;
                case 0x007F: { // SMSG_SERVER_PING
                    in.skip(4);
                } break;
                case 0x0080: { // SMSG_BEING_REMOVE
                    in.skip(5);
                } break;
                case 0x0086: { // SMSG_BEING_MOVE2
                    in.skip(14);
                } break;
                case 0x0087: { // SMSG_WALK_RESPONSE
                    in.skip(10);
                } break;
                case 0x0088: { // SMSG_PLAYER_STOP
                    in.skip(8);
                } break;
                case 0x008A: { // SMSG_BEING_ACTION
                    in.skip(27);
                } break;
                case 0x008D: { // SMSG_BEING_CHAT
                 // TODO
                } break;
                case 0x008E: { // SMSG_PLAYER_CHAT
                 // TODO
                } break;
                case 0x0091: { // SMSG_PLAYER_WARP
                    in.skip(20);
                } break;
                case 0x0092: { // SMSG_CHANGE_MAP_SERVER
                    in.skip(26);
                } break;
                case 0x0094: { // hard-coded
                    in.skip(4);
                } break;
                case 0x0095: { // SMSG_BEING_NAME_RESPONSE
                    in.skip(28);
                } break;
                case 0x0097: { // SMSG_WHISPER
                    // TODO
                } break;
                case 0x0098: { // SMSG_WHISPER_RESPONSE
                    in.skip(1);
                } break;
                case 0x009A: { // SMSG_GM_CHAT
                 // TODO
                } break;
                case 0x009C: { // SMSG_BEING_CHANGE_DIRECTION
                    in.skip(7);
                } break;
                case 0x009D: { // SMSG_ITEM_VISIBLE
                    in.skip(15);
                } break;
                case 0x009E: { // SMSG_ITEM_DROPPED
                    in.skip(15);
                } break;
                case 0x00A0: { // SMSG_PLAYER_INVENTORY_ADD
                    in.skip(21);
                } break;
                case 0x00A1: { // SMSG_ITEM_REMOVE
                    in.skip(4);
                } break;
                case 0x00A4: { // SMSG_PLAYER_EQUIPMENT
                    // TODO
                } break;
                case 0x00A6: { // SMSG_PLAYER_STORAGE_EQUIP
                    // TODO
                } break;
                case 0x00A8: { // SMSG_ITEM_USE_RESPONSE
                    in.skip(5);
                } break;
                case 0x00AA: { // SMSG_PLAYER_EQUIP
                    in.skip(5);
                } break;
                case 0x00AC: { // SMSG_PLAYER_UNEQIUP
                    in.skip(5);
                } break;
                case 0x00AF: { // SMSG_PLAYER_INVENTORY_REMOVE
                    in.skip(4);
                } break;
                case 0x00B0: { // SMSG_PLAYER_STAT_UPDATE_1 
                    int type = readShort();
                    int value = readInt();

                    switch(type) {
                        case 0x0000:
                        // set move speed (value/10, value/10, 0)
                        break;
                        case 0x0004: break;
                        case 0x0005: character.hp = value; break;
                        case 0x0006: character.max_hp = value; break;
                        case 0x0007: character.mp = value; break;
                        case 0x0008: character.max_mp = value; break;
                        case 0x0009: character.char_points = value; break;
                        case 0x000B: character.level = value; break;
                        case 0x000C: character.skill_points = value; break;
                        case 0x0018: character.total_weight = value; break;
                        case 0x0019: character.max_weight = value; break;
                        case 0x0029: character.atk_base = value; break;
                        case 0x002A: character.atk_mod = value; break;
                        case 0x002B: character.matk_base = value; break;
                        case 0x002C: character.matk_mod = value; break;
                        case 0x002D: character.def_base = value; break;
                        case 0x002E: character.def_mod = value; break;
                        case 0x002F: character.mdef_base = value; break;
                        case 0x0030: character.mdef_mod = value; break;
                        case 0x0031: character.hit_base = value; break;
                        case 0x0032: character.flee_base = value; break;
                        case 0x0033: character.flee_mod = value; break;
                        case 0x0034: character.crit_base = value; break;
                        case 0x0035: character.attack_speed = value; break;
                        case 0x0037: character.job_base = value; break;
                        case 500: // gm level
                        break;
                    }
                } break;
                case 0x00B1: { // SMSG_PLAYER_STAT_UPDATE_2
                    in.skip(6);
                } break;
                case 0x00B3: { // SMSG_CHAR_SWITCH_RESPONSE
                    in.skip(1);
                } break;
                case 0x00B4: { // SMSG_NPC_MESSAGE
                    // TODO
                } break;
                case 0x00B5: { // SMSG_NPC_NEXT
                    in.skip(4);
                } break;
                case 0x00B6: { // SMSG_NPC_CLOSE
                    in.skip(4);
                } break;
                case 0x00B7: { // SMSG_NPC_CHOISE
                    // TODO
                } break;
                case 0x00BC: { // SMSG_PLAYER_STAT_UPDATE_4
                    in.skip(4);
                } break;
                case 0x00BD: { // SMSG_PLAYER_STAT_UPDATE_5
                    in.skip(42);
                } break;
                case 0x00BE: { // SMSG_PLAYER_STAT_UPDATE_6
                    in.skip(3);
                } break;
                case 0x00C0: { // SMSG_BEING_EMOTION
                    in.skip(5);
                } break;
                case 0x00C2: { // SMSG_WHO_ANSWER
                    in.skip(4);
                } break;
                case 0x00C3: { // SMSG_BEING_CHANGE_LOOKS
                    in.skip(6);
                } break;
                case 0x00C4: { // SMSG_NPC_BUY_SELL_CHOISE
                    in.skip(4);
                } break;
                case 0x00C6: { // SMSG_NPC_BUY
                    //TODO
                } break;
                case 0x00C7: { // SMSG_NPC_SELL
                    //TODO
                } break;
                case 0x00CA: { // SMSG_NPC_BUY_RESPONSE
                    in.skip(1);
                } break;
                case 0x00E5: { // SMSG_TRADE_REQUEST
                    in.skip(4);
                } break;
                case 0x00E7: { // SMSG_TRADE_RESPONSE
                    in.skip(1);
                } break;
                case 0x00E9: { // SMSG_TRADE_ITEM_ADD
                    in.skip(17);
                } break;
                case 0x00EC: { // SMSG_TRADE_OK
                    in.skip(1);
                } break;
                case 0x00EE: break; // SMSG_TRADE_CANCEL
                case 0x00F0: { // SMSG_TRADE_COMPLETE
                    in.skip(1);
                } break;
                case 0x00F2: { // SMSG_PLAYER_STORAGE_STATUS
                    in.skip(4);
                } break;
                case 0x00F4: { // SMSG_PLAYER_STORAGE_ADD
                    in.skip(19);
                } break;
                case 0x00F6: { // SMSG_PLAYER_STORAGE_REMOVE
                    in.skip(6);
                } break;
                default:
                    readHex();
            }
        }

//        sock.close();
    }

    public static void main(String[] args) throws Exception {
        bot instance = new bot();

/*
        Thread reader = new Thread(new Runnable() {
            public void run() {
                while(sock.isConnected()) {
                    try {
                        byte b = in.readByte();
                        printHex(b);
                        System.out.append(' ');
                        if(b == '\n') System.out.append('\n');
                    } catch(Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        });
        reader.start();

        out.writeBytes("GET / HTTP/1.1\n");
        out.writeBytes("Host: doomstal.com\n");
        out.writeBytes("\n");

        Thread.sleep(1000);

        */
    }
}
