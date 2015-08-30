import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaTable;

public class Net {
    public static int packetLength[];

    Socket sock;

    int packet = -1;
    int recv_bytes = 0;
    int send_packet = -1;
    int send_bytes = 0;
    int length = -2;

    public Net() { }

    public class PacketIn {
        byte[] buffer = new byte[2048];
        int[] tokens = new int[1024];

        int id;
        int length;
        int pos;
        int token;

        PacketIn() throws IOException {
            InputStream in = sock.getInputStream();
            pos = 0;
            in.read(buffer, 0, 2);
            length = 2;
            this.id = readInt16();
            length = getPacketLength(id);
            if(length == -1) {
                in.read(buffer, 2, 2);
                length = 4;
                length = readInt16();
            }
            in.read(buffer, pos, length - pos);
        }

        public int getId() { return id; }

        public int getLength() { return length; }

        public void skip(int n) {
            tokens[token++] = 0;
            tokens[token++] = n;
            checkOverflow(n);

            pos += n;
        }

        public void skip() {
            skip(length - pos);
        }

        public int readInt8() {
            tokens[token++] = 1;
            checkOverflow(1);

            int value = buffer[pos]&0xFF;
            pos += 1;
            return value;
        }

        public int readInt16() {
            tokens[token++] = 2;
            checkOverflow(2);

            int value = buffer[pos]&0xFF | ((buffer[pos+1]&0xFF)<<8);
            pos += 2;
            return value;
        }

        public int readInt32() {
            tokens[token++] = 3;
            checkOverflow(4);

            int value = buffer[pos]&0xFF | ((buffer[pos+1]&0xFF)<<8) | ((buffer[pos+2]&0xFF)<<16) | ((buffer[pos+3]&0xFF)<<24);
            pos += 4;
            return value;
        }

        public String readString(int len) {
            tokens[token++] = 4;
            tokens[token++] = len;
            checkOverflow(len);

            StringBuilder sb = new StringBuilder();
            boolean append = true;
            for(int i=0; i!=len; ++i) {
                char c = (char)buffer[pos+i];
                if(c == 0) append = false;
                if(append) sb.append(c);
            }
            pos += len;
            return sb.toString();
        }

        public void readCoordinates(LuaValue being) {
            tokens[token++] = 5;
            checkOverflow(3);

            int b0 = buffer[pos]&0xFF;
            int b1 = buffer[pos+1]&0xFF;
            int b2 = buffer[pos+2]&0xFF;
            being.set("x", ((b1 & 0xC0) | ((b0 & 0xFF) << 8)) >> 6);
            being.set("y", ((b2 & 0xF0) | ((b1 & 0x3F) << 8)) >> 4);
            being.set("dst_x", being.get("x"));
            being.set("dst_y", being.get("y"));
            int dir = 1;
            switch(b2&0x0F) {
                case 0: dir = 1; break;
                case 1: dir = 1; break;
                case 2: dir = 2; break;
                case 3: dir = 4; break;
                case 4: dir = 4; break;
                case 5: dir = 4; break;
                case 6: dir = 8; break;
                case 7: dir = 1; break;
                default:
                    System.out.println("unknown dir="+(b2&0x0F));
            }
            being.set("dir", dir);
            pos += 3;
        }

        public void readCoordinatePair(LuaValue being) {
            tokens[token++] = 6;
            checkOverflow(5);

            int b0 = buffer[pos]&0xFF;
            int b1 = buffer[pos+1]&0xFF;
            int b2 = buffer[pos+2]&0xFF;
            int b3 = buffer[pos+3]&0xFF;
            int b4 = buffer[pos+4]&0xFF;
            being.set("x", (b1 | (b0 << 8)) >> 6);
            being.set("y", (b2 | ((b1 & 0x3F) << 8)) >> 4);
            being.set("dst_x", (b3 | ((b2 & 0x0F) << 8)) >> 2);
            being.set("dst_y", b4 | (b3 & 0x03) << 8);
            pos += 5;
        }

        public void checkOverflow(int n) {
            if(pos + n > length) {
                System.out.println("packet read overflow!!!");
                System.out.println(this.toString());
                System.exit(1);
            }
        }

        public void close() {
            if(pos < length) {
                System.out.println("packet read unfinished!!!");
                System.out.println(this.toString());
                System.exit(1);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("PacketIn ");
            sb.append(Utils.int16toHex(id));
            sb.append(' ');
            sb.append(pos);
            sb.append('/');
            sb.append(length);
            for(int i=0; i!=token; ++i) {
                sb.append(' ');
                switch(tokens[i]) {
                    case 0: sb.append("skip("+tokens[++i]+')'); break;
                    case 1: sb.append("int8"); break;
                    case 2: sb.append("int16"); break;
                    case 3: sb.append("int32"); break;
                    case 4: sb.append("string("+tokens[++i]+')'); break;
                    case 5: sb.append("pos3"); break;
                    case 6: sb.append("pos5"); break;
                }
            }
            return sb.toString();
        }
    }

    public class PacketOut {
        byte[] buffer = new byte[2048];
        int[] tokens = new int[1024];

        int id;
        int length;
        int pos;
        int token;

        PacketOut(int id) {
            this.id = id;
            length = getPacketLength(id);
            writeInt16(id);
            if(length == -1) writeInt16(0); // placeholder
        }

        public void writeInt8(int b) {
            tokens[token++] = 1;
            checkOverflow(1);

            buffer[pos] = (byte)(b & 0xFF);
            pos += 1;
        }

        public void writeInt16(int v) {
            tokens[token++] = 2;
            checkOverflow(2);

            buffer[pos] = (byte)(v & 0xFF);
            buffer[pos+1] = (byte)( (v>>8) & 0xFF );
            pos += 2;
        }

        public void writeInt32(int v) {
            tokens[token++] = 3;
            checkOverflow(4);

            buffer[pos] = (byte)(v & 0xFF);
            buffer[pos+1] = (byte)( (v>>8) & 0xFF );
            buffer[pos+2] = (byte)( (v>>16) & 0xFF );
            buffer[pos+3] = (byte)( (v>>24) & 0xFF );
            pos += 4;
        }

        public void writeString(int len, String str) {
            tokens[token++] = 4;
            tokens[token++] = len;
            checkOverflow(len);

            byte[] bytes = str.getBytes();
            for(int i=0; i!=len; ++i) {
                if(i < bytes.length) buffer[pos+i] = bytes[i];
                else buffer[pos+i] = 0;
            }
            pos += len;
        }

        public void writeCoordinates(int x, int y, int dir) {
            tokens[token++] = 5;
            checkOverflow(3);

            int b0;
            int b1;
            int b2;
            int tmp = x << 6;
            b0 = (tmp >> 8) & 0xFF;
            b1 = tmp & 0xC0;
            tmp = y << 4;
            b1 |= (tmp >> 8) & 0x3F;
            b2 = tmp & 0xF0;
            b2 |= dir & 0x0F;
            buffer[pos] = (byte)b0;
            buffer[pos+1] = (byte)b1;
            buffer[pos+2] = (byte)b2;
            pos += 3;
        }

        public void checkOverflow(int n) {
            if(length == -1) return;
            if(pos + n > length) {
                System.out.println("packet read overflow!!!");
                System.out.println(this.toString());
                System.exit(1);
            }
        }

        public void send() throws IOException {
            if(length > -1 && pos < length) {
                System.out.println("packet write unfinished!!!");
                System.out.println(this.toString());
                System.exit(1);
            }
            if(length == -1) {
                length = pos;
                pos = 2;
                writeInt16(length);
            }
            sock.getOutputStream().write(buffer, 0, length);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("PacketOut ");
            sb.append(Utils.int16toHex(id));
            sb.append(' ');
            sb.append(pos);
            sb.append('/');
            sb.append(length);
            for(int i=0; i!=token; ++i) {
                sb.append(' ');
                switch(tokens[i]) {
                    case 0: sb.append("skip("+tokens[++i]+')'); break;
                    case 1: sb.append("int8"); break;
                    case 2: sb.append("int16"); break;
                    case 3: sb.append("int32"); break;
                    case 4: sb.append("string("+tokens[++i]+')'); break;
                    case 5: sb.append("pos3"); break;
                    case 6: sb.append("pos5"); break;
                }
            }
            return sb.toString();
        }
    }

    public PacketIn readPacket() throws IOException {
        return new PacketIn();
    }

    public PacketOut newPacket(int id) {
        return new PacketOut(id);
    }

    public int getPacketLength(int p) {
        if(p == 0x7530) return 2;
        if(p == 0x7531) return 10;
        if(p < packetLength.length && packetLength[p] != 0) {
            return packetLength[p];
        }
        System.out.println("unknown packet "+Utils.int16toHex(p));
        System.exit(1);
        return -2;
    }

    public void connect(bot b) throws IOException {
        File file = new File("config.txt");
        BufferedReader br = new BufferedReader(new FileReader(file));
        String username = br.readLine();
        String password = br.readLine();
        String charname = br.readLine();

        System.out.println("connecting to login server");

        sock = new Socket("server.themanaworld.org", 6901); // login server

        PacketOut po = newPacket(0x0064); // login request
        po.writeInt32(0);
        po.writeString(24, username);
        po.writeString(24, password);
        po.writeInt8(2);
        po.send();

        PacketIn pi = null;

        while(true) {
            pi = readPacket();
            if(pi.getId() == 0x0069) break; // login data
            switch(pi.getId()) {
                case 0x006A:
                    System.out.println("login error");
                    System.exit(1);
                case 0x0081:
                    System.out.println("connection problem");
                    System.exit(1);
            }
        }
        int worldCount = (pi.getLength() - 47) / 32;
        b.sid1 = pi.readInt32();
        b.acid = pi.readInt32();
        b.sid2 = pi.readInt32();
        pi.skip(30);
        b.gender = pi.readInt8();
        b.charServerIp = ""+pi.readInt8()+"."+pi.readInt8()+"."+pi.readInt8()+"."+pi.readInt8();
        b.charServerPort = pi.readInt16();
        String name = pi.readString(20);
        int online = pi.readInt16();
        pi.skip();
        pi.close();

        sock.close(); // disconnect from login server

        System.out.println(b.charServerIp+":"+b.charServerPort+" "+name+" ("+online+')');
        System.out.println("connecting to char server");

        sock = new Socket(b.charServerIp, b.charServerPort); // char server

        po = newPacket(0x0065); // character server connection request
        po.writeInt32(b.acid);
        po.writeInt32(b.sid1);
        po.writeInt32(b.sid2);
        po.writeInt16(1);
        po.writeInt8(b.gender);
        po.send();

        sock.getInputStream().skip(4);

        while(true) {
            pi = readPacket();
            if(pi.getId() == 0x0071) break; // SMSG_CHAR_MAP_INFO
            switch(pi.getId()) {
                case 0x006A:
                    System.out.println("login error");
                    System.exit(1);
                case 0x006C:
                    System.out.println("connection refused: "+pi.readInt8());
                    System.exit(1);
                case 0x006B: { // update character list
                    pi.skip(20);
                    int charCount = (pi.getLength() - 24) / 106;

                    boolean char_found = false;
                    int char_num = 0;
                    for(int i=0; i!=charCount; ++i) {
                        LuaTable character = new LuaTable();

                        character.set("id", pi.readInt32());
                        character.set("exp", pi.readInt32());
                        character.set("money", pi.readInt32());
                        character.set("job_exp", pi.readInt32());
                        character.set("job_level", pi.readInt32());
                        character.set("boots", pi.readInt16());
                        character.set("gloves", pi.readInt16());
                        character.set("cape", pi.readInt16());
                        character.set("misc1", pi.readInt16());
                        pi.skip(12); // (2) option, (2) unused, (4) karma, (4) manner
                        character.set("char_points", pi.readInt16());
                        character.set("hp", pi.readInt16());
                        character.set("hp_max", pi.readInt16());
                        character.set("mp", pi.readInt16());
                        character.set("mp_max", pi.readInt16());
                        character.set("speed", pi.readInt16());
                        character.set("race", pi.readInt16());
                        character.set("hair_style", pi.readInt16());
                        character.set("weapon", pi.readInt16());
                        int level = pi.readInt16();
                        character.set("level", level);
                        character.set("skill_points", pi.readInt16());
                        character.set("legs", pi.readInt16());
                        character.set("shield", pi.readInt16());
                        character.set("helmet", pi.readInt16());
                        character.set("armor", pi.readInt16());
                        character.set("hair_color", pi.readInt16());
                        character.set("misc2", pi.readInt16());
                        name = pi.readString(24);
                        character.set("name", name);
                        character.set("str_base", pi.readInt8());
                        character.set("agi_base", pi.readInt8());
                        character.set("vit_base", pi.readInt8());
                        character.set("int_base", pi.readInt8());
                        character.set("dex_base", pi.readInt8());
                        character.set("luk_base", pi.readInt8());
                        char_num = pi.readInt8();
                        pi.skip(1); // unused
                        System.out.println(name+" ("+level+" lvl)");

                        if(name.equals(charname)) {
                            Utils.copyTable(character, b.character);
                            pi.skip();
                            char_found = true;
                            break;
                        }
                    }
                    if(!char_found) {
                        System.out.println("character '"+charname+"' not found!");
                        System.exit(1);
                    }
                    pi.close();

                    po = newPacket(0x0066); // select character request
                    po.writeInt8(char_num);
                    po.send();
                } break;
            }
        }
        pi.skip(4);
        b.mapName = pi.readString(16);
        b.mapServerIp = ""+pi.readInt8()+"."+pi.readInt8()+"."+pi.readInt8()+"."+pi.readInt8();
        b.mapServerPort = pi.readInt16();
        pi.close();

        sock.close();

        System.out.println(b.mapServerIp + ":" + b.mapServerPort + " " + b.mapName);
        System.out.println("connecting to map server");

        sock = new Socket(b.mapServerIp, b.mapServerPort); // map server

        po = newPacket(0x0072); // CMSG_MAP_SERVER_CONNECT
        po.writeInt32(b.acid);
        po.writeInt32(b.character.get("id").toint());
        po.writeInt32(b.sid1);
        po.writeInt32(b.sid2);
        po.writeInt8(b.gender);
        po.send();

        sock.getInputStream().skip(4);

        pi = readPacket();
        if(pi.getId() != 0x0073) {
            System.out.println("unexpected packet");
            System.out.println(pi);
            System.exit(1);
        }
        pi.skip(4);
        pi.readCoordinates(b.character);
        pi.skip(2);
        pi.close();
    }

    static {
        packetLength = new int[] {
           10,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
//0x0040
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0, 50,  3, -1, 55, 17,  3, 37, 46, -1, 23, -1,  3,108,  3,  2,
            3, 28, 19, 11,  3, -1,  9,  5, 54, 53, 58, 60, 41,  2,  6,  6,
//0x0080
            7,  3,  2,  2,  2,  5, 16, 12, 10,  7, 29, 23, -1, -1, -1,  0,
            7, 22, 28,  2,  6, 30, -1, -1,  3, -1, -1,  5,  9, 17, 17,  6,
           23,  6,  6, -1, -1, -1, -1,  8,  7,  6,  7,  4,  7,  0, -1,  6,
            8,  8,  3,  3, -1,  6,  6, -1,  7,  6,  2,  5,  6, 44,  5,  3,
//0x00C0
            7,  2,  6,  8,  6,  7, -1, -1, -1, -1,  3,  3,  6,  6,  2, 27,
            3,  4,  4,  2, -1, -1,  3, -1,  6, 14,  3, -1, 28, 29, -1, -1,
           30, 30, 26,  2,  6, 26,  3,  3,  8, 19,  5,  2,  3,  2,  2,  2,
            3,  2,  6,  8, 21,  8,  8,  2,  2, 26,  3, -1,  6, 27, 30, 10,
//0x0100
            2,  6,  6, 30, 79, 31, 10, 10, -1, -1,  4,  6,  6,  2, 11, -1,
           10, 39,  4, 10, 31, 35, 10, 18,  2, 13, 15, 20, 68,  2,  3, 16,
            6, 14, -1, -1, 21,  8,  8,  8,  8,  8,  2,  2,  3,  4,  2, -1,
            6, 86,  6, -1, -1,  7, -1,  6,  3, 16,  4,  4,  4,  6, 24, 26,
//0x0140
           22, 14,  6, 10, 23, 19,  6, 39,  8,  9,  6, 27, -1,  2,  6,  6,
          110,  6, -1, -1, -1, -1, -1,  6, -1, 54, 66, 54, 90, 42,  6, 42,
           -1, -1, -1, -1, -1, 30, -1,  3, 14,  3, 30, 10, 43, 14,186,182,
           14, 30, 10,  3, -1,  6,106, -1,  4,  5,  4, -1,  6,  7, -1, -1,
//0x0180
            6,  3,106, 10, 10, 34,  0,  6,  8,  4,  4,  4, 29, -1, 10,  6,
           90, 86, 24,  6, 30,102,  9,  4,  8,  4, 14, 10,  4,  6,  2,  6,
            3,  3, 35,  5, 11, 26, -1,  4,  4,  6, 10, 12,  6, -1,  4,  4,
           11,  7, -1, 67, 12, 18,114,  6,  3,  6, 26, 26, 26, 26,  2,  3,
//0x01C0
            2, 14, 10, -1, 22, 22,  4,  2, 13, 97,  0,  9,  9, 29,  6, 28,
            8, 14, 10, 35,  6,  8,  4, 11, 54, 53, 60,  2, -1, 47, 33,  6,
           30,  8, 34, 14,  2,  6, 26,  2, 28, 81,  6, 10, 26,  2, -1, -1,
           -1, -1, 20, 10, 32,  9, 34, 14,  2,  6, 48, 56, -1,  4,  5, 10,
//0x0200
           26,  0,  0,  0, 18,  0,  0,  0,  0,  0,  0, 19, 10,  0,  0,  0,
            0,  0, 16,  0,  8, -1,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
            0,  0,  0,  0,  0, 14, 10,  4, 10,  0,  0,  0,  0,  0,  0,  0
        };
    }
}
