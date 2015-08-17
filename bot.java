import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

public class bot {
    static Map<Integer, Integer> packetLength = new HashMap<Integer, Integer>();

    static {
        packetLength.put(0x0062, 3);
        packetLength.put(0x0063, -1);
        packetLength.put(0x0069, -1);
        packetLength.put(0x006A, 23);
        packetLength.put(0x006B, -1);
        packetLength.put(0x006C, 3);
        packetLength.put(0x006D, 108);
        packetLength.put(0x006E, 3);
        packetLength.put(0x006F, 2);
        packetLength.put(0x0070, 3);
        packetLength.put(0x0071, 28);
        packetLength.put(0x0073, 11);
        packetLength.put(0x0078, 54);
        packetLength.put(0x007B, 60);
        packetLength.put(0x007C, 41);
        packetLength.put(0x007F, 6);
        packetLength.put(0x0080, 7);
        packetLength.put(0x0081, 3);
        packetLength.put(0x0086, 16);
        packetLength.put(0x0087, 12);
        packetLength.put(0x0088, 10);
        packetLength.put(0x008A, 29);
        packetLength.put(0x008D, -1);
        packetLength.put(0x008E, -1);
        packetLength.put(0x0091, 22);
        packetLength.put(0x0092, 28);
        packetLength.put(0x0095, 30);
        packetLength.put(0x0097, -1);
        packetLength.put(0x0098, 3);
        packetLength.put(0x009A, -1);
        packetLength.put(0x009C, 9);
        packetLength.put(0x009D, 17);
        packetLength.put(0x009E, 17);
        packetLength.put(0x00A0, 23);
        packetLength.put(0x00A1, 6);
        packetLength.put(0x00A4, -1);
        packetLength.put(0x00A6, -1);
        packetLength.put(0x00A8, 7);
        packetLength.put(0x00AA, 7);
        packetLength.put(0x00AC, 7);
        packetLength.put(0x00AF, 6);
        packetLength.put(0x00B0, 8);
        packetLength.put(0x00B1, 8);
        packetLength.put(0x00B3, 3);
        packetLength.put(0x00B4, -1);
        packetLength.put(0x00B5, 6);
        packetLength.put(0x00B6, 6);
        packetLength.put(0x00B7, -1);
        packetLength.put(0x00BC, 6);
        packetLength.put(0x00BD, 44);
        packetLength.put(0x00BE, 5);
        packetLength.put(0x00C0, 7);
        packetLength.put(0x00C2, 6);
        packetLength.put(0x00C3, 8);
        packetLength.put(0x00C4, 6);
        packetLength.put(0x00C6, -1);
        packetLength.put(0x00C7, -1);
        packetLength.put(0x00CA, 3);
        packetLength.put(0x00CB, 3);
        packetLength.put(0x00CD, 6);
        packetLength.put(0x00E5, 26);
        packetLength.put(0x00E7, 3);
        packetLength.put(0x00E9, 19);
        packetLength.put(0x00EC, 3);
        packetLength.put(0x00EE, 2);
        packetLength.put(0x00F0, 3);
        packetLength.put(0x00F2, 6);
        packetLength.put(0x00F4, 21);
        packetLength.put(0x00F6, 8);
        packetLength.put(0x00F8, 2);
        packetLength.put(0x00FA, 3);
        packetLength.put(0x00FB, -1);
        packetLength.put(0x00FD, 27);
        packetLength.put(0x00FE, 30);
        packetLength.put(0x0101, 6);
        packetLength.put(0x0104, 79);
        packetLength.put(0x0105, 31);
        packetLength.put(0x0106, 10);
        packetLength.put(0x0107, 10);
        packetLength.put(0x0109, -1);
        packetLength.put(0x010C, 6);
        packetLength.put(0x010E, 11);
        packetLength.put(0x010F, -1);
        packetLength.put(0x0110, 10);
        packetLength.put(0x0119, 13);
        packetLength.put(0x0139, 16);
        packetLength.put(0x013A, 4);
        packetLength.put(0x013B, 4);
        packetLength.put(0x013C, 4);
        packetLength.put(0x0141, 14);
        packetLength.put(0x0142, 6);
        packetLength.put(0x0148, 8);
        packetLength.put(0x014C, -1);
        packetLength.put(0x014E, 6);
        packetLength.put(0x014E, 6);
        packetLength.put(0x0152, -1);
        packetLength.put(0x0154, -1);
        packetLength.put(0x0156, -1);
        packetLength.put(0x015A, 66);
        packetLength.put(0x015C, 90);
        packetLength.put(0x015E, 6);
        packetLength.put(0x0160, -1);
        packetLength.put(0x0162, -1);
        packetLength.put(0x0163, -1);
        packetLength.put(0x0166, -1);
        packetLength.put(0x0167, 3);
        packetLength.put(0x0169, 3);
        packetLength.put(0x016A, 30);
        packetLength.put(0x016C, 43);
        packetLength.put(0x016D, 14);
        packetLength.put(0x016F, 182);
        packetLength.put(0x0171, 30);
        packetLength.put(0x0173, 3);
        packetLength.put(0x0174, -1);
        packetLength.put(0x017F, -1);
        packetLength.put(0x0181, 3);
        packetLength.put(0x0184, 10);
        packetLength.put(0x018B, 4);
        packetLength.put(0x0195, 102);
        packetLength.put(0x0196, 9);
        packetLength.put(0x019B, 10);
        packetLength.put(0x01B1, 7);
        packetLength.put(0x01B6, 114);
        packetLength.put(0x01C8, 13);
        packetLength.put(0x01D4, 6);
        packetLength.put(0x01D7, 11);
        packetLength.put(0x01D8, 54);
        packetLength.put(0x01D9, 53);
        packetLength.put(0x01DA, 60);
        packetLength.put(0x01DE, 33);
        packetLength.put(0x01EE, -1);
        packetLength.put(0x01F0, -1);
        packetLength.put(0x020C, 10);
        packetLength.put(0x7531, 10);

        packetLength.put(0x0000, 10);
        packetLength.put(0x0074, 3);
        packetLength.put(0x0075, -1);
        packetLength.put(0x0076, 9);
        packetLength.put(0x0077, 5);
        packetLength.put(0x0079, 53);
        packetLength.put(0x007A, 58);
        packetLength.put(0x0082, 2);
        packetLength.put(0x0083, 2);
        packetLength.put(0x0084, 2);
        packetLength.put(0x008B, 23);
        packetLength.put(0x0093, 2);
        packetLength.put(0x00A3, -1);
        packetLength.put(0x00A5, -1);
        packetLength.put(0x00AE, -1);
        packetLength.put(0x00BA, 2);
        packetLength.put(0x00CE, 2);
        packetLength.put(0x00CF, 27);
        packetLength.put(0x00D0, 3);
        packetLength.put(0x00D1, 4);
        packetLength.put(0x00D2, 4);
        packetLength.put(0x00D3, 2);
        packetLength.put(0x00D4, -1);
        packetLength.put(0x00D5, -1);
        packetLength.put(0x00D6, 3);
        packetLength.put(0x00D7, -1);
        packetLength.put(0x00D8, 6);
        packetLength.put(0x00D9, 14);
        packetLength.put(0x00DA, 3);
        packetLength.put(0x00DB, -1);
        packetLength.put(0x00DC, 28);
        packetLength.put(0x00DD, 29);
        packetLength.put(0x00DE, -1);
        packetLength.put(0x00DF, -1);
        packetLength.put(0x00E0, 30);
        packetLength.put(0x00E1, 30);
        packetLength.put(0x00E2, 26);
        packetLength.put(0x00E3, 2);
        packetLength.put(0x00EA, 5);
        packetLength.put(0x00F1, 2);
        packetLength.put(0x010A, 4);
        packetLength.put(0x010B, 6);
        packetLength.put(0x010D, 2);
        packetLength.put(0x0111, 39);
        packetLength.put(0x0114, 31);
        packetLength.put(0x0115, 35);
        packetLength.put(0x0117, 18);
        packetLength.put(0x0118, 2);
        packetLength.put(0x011A, 15);
        packetLength.put(0x011C, 68);
        packetLength.put(0x011D, 2);
        packetLength.put(0x011E, 3);
        packetLength.put(0x011F, 16);
        packetLength.put(0x0120, 6);
        packetLength.put(0x0121, 14);
        packetLength.put(0x0122, -1);
        packetLength.put(0x0123, -1);
        packetLength.put(0x0124, 21);
        packetLength.put(0x0125, 8);
        packetLength.put(0x0126, 8);
        packetLength.put(0x0127, 8);
        packetLength.put(0x0128, 8);
        packetLength.put(0x0129, 8);
        packetLength.put(0x012A, 2);
        packetLength.put(0x012B, 2);
        packetLength.put(0x012C, 3);
        packetLength.put(0x012D, 4);
        packetLength.put(0x012E, 2);
        packetLength.put(0x012F, -1);
        packetLength.put(0x0130, 6);
        packetLength.put(0x0131, 86);
        packetLength.put(0x0132, 6);
        packetLength.put(0x0133, -1);
        packetLength.put(0x0134, -1);
        packetLength.put(0x0135, 7);
        packetLength.put(0x0136, -1);
        packetLength.put(0x0137, 6);
        packetLength.put(0x0138, 3);
        packetLength.put(0x013D, 6);
        packetLength.put(0x013E, 24);
        packetLength.put(0x013F, 26);
        packetLength.put(0x0140, 22);
        packetLength.put(0x0144, 23);
        packetLength.put(0x0145, 19);
        packetLength.put(0x0147, 39);
        packetLength.put(0x014A, 6);
        packetLength.put(0x014B, 27);
        packetLength.put(0x0150, 110);
        packetLength.put(0x0157, 6);
        packetLength.put(0x0158, -1);
        packetLength.put(0x015F, 42);
        packetLength.put(0x0164, -1);
        packetLength.put(0x0175, 6);
        packetLength.put(0x0176, 106);
        packetLength.put(0x0177, -1);
        packetLength.put(0x0178, 4);
        packetLength.put(0x0179, 5);
        packetLength.put(0x017A, 4);
        packetLength.put(0x017B, -1);
        packetLength.put(0x017C, 6);
        packetLength.put(0x017D, 7);
        packetLength.put(0x0182, 106);
        packetLength.put(0x0185, 34);
        packetLength.put(0x0187, 6);
        packetLength.put(0x0188, 8);
        packetLength.put(0x0189, 4);
        packetLength.put(0x018C, 29);
        packetLength.put(0x018D, -1);
        packetLength.put(0x018E, 10);
        packetLength.put(0x018F, 6);
        packetLength.put(0x0191, 86);
        packetLength.put(0x0192, 24);
        packetLength.put(0x0193, 6);
        packetLength.put(0x0194, 30);
        packetLength.put(0x0197, 4);
        packetLength.put(0x0198, 8);
        packetLength.put(0x0199, 4);
        packetLength.put(0x019A, 14);
        packetLength.put(0x019E, 2);
        packetLength.put(0x019F, 6);
        packetLength.put(0x01A0, 3);
        packetLength.put(0x01A1, 3);
        packetLength.put(0x01A2, 35);
        packetLength.put(0x01A3, 5);
        packetLength.put(0x01A4, 11);
        packetLength.put(0x01A5, 26);
        packetLength.put(0x01A6, -1);
        packetLength.put(0x01A7, 4);
        packetLength.put(0x01A8, 4);
        packetLength.put(0x01A9, 6);
        packetLength.put(0x01AA, 10);
        packetLength.put(0x01AB, 12);
        packetLength.put(0x01AC, 6);
        packetLength.put(0x01AD, -1);
        packetLength.put(0x01AE, 4);
        packetLength.put(0x01AF, 4);
        packetLength.put(0x01B0, 11);
        packetLength.put(0x01B2, -1);
        packetLength.put(0x01B3, 67);
        packetLength.put(0x01B4, 12);
        packetLength.put(0x01B5, 18);
        packetLength.put(0x01B7, 6);
        packetLength.put(0x01B8, 3);
        packetLength.put(0x01B9, 6);
        packetLength.put(0x01BA, 26);
        packetLength.put(0x01BB, 26);
        packetLength.put(0x01BC, 26);
        packetLength.put(0x01BD, 26);
        packetLength.put(0x01BE, 2);
        packetLength.put(0x01BF, 3);
        packetLength.put(0x01C0, 2);
        packetLength.put(0x01C1, 14);
        packetLength.put(0x01C2, 10);
        packetLength.put(0x01C3, -1);
        packetLength.put(0x01C4, 22);
        packetLength.put(0x01C5, 22);
        packetLength.put(0x01C6, 4);
        packetLength.put(0x01C7, 2);
        packetLength.put(0x01C9, 97);
        packetLength.put(0x01CB, 9);
        packetLength.put(0x01CC, 9);
        packetLength.put(0x01CD, 29);
        packetLength.put(0x01CE, 6);
        packetLength.put(0x01CF, 28);
        packetLength.put(0x01D0, 8);
        packetLength.put(0x01D1, 14);
        packetLength.put(0x01D2, 10);
        packetLength.put(0x01D3, 35);
        packetLength.put(0x01D6, 4);
        packetLength.put(0x01DB, 2);
        packetLength.put(0x01DC, -1);
        packetLength.put(0x01DD, 47);
        packetLength.put(0x01DF, 6);
        packetLength.put(0x01E0, 30);
        packetLength.put(0x01E1, 8);
        packetLength.put(0x01E2, 34);
        packetLength.put(0x01E3, 14);
        packetLength.put(0x01E4, 2);
        packetLength.put(0x01E5, 6);
        packetLength.put(0x01E6, 26);
        packetLength.put(0x01E7, 2);
        packetLength.put(0x01E8, 28);
        packetLength.put(0x01E9, 81);
        packetLength.put(0x01EA, 6);
        packetLength.put(0x01EB, 10);
        packetLength.put(0x01EC, 26);
        packetLength.put(0x01ED, 2);
        packetLength.put(0x01EF, -1);
        packetLength.put(0x01F1, -1);
        packetLength.put(0x01F2, 20);
        packetLength.put(0x01F3, 10);
        packetLength.put(0x01F4, 32);
        packetLength.put(0x01F5, 9);
        packetLength.put(0x01F6, 34);
        packetLength.put(0x01F7, 14);
        packetLength.put(0x01F8, 2);
        packetLength.put(0x01F9, 6);
        packetLength.put(0x01FA, 48);
        packetLength.put(0x01FB, 56);
        packetLength.put(0x01FC, -1);
        packetLength.put(0x01FD, 4);
        packetLength.put(0x01FE, 5);
        packetLength.put(0x01FF, 10);
        packetLength.put(0x0200, 26);
        packetLength.put(0x0204, 18);
        packetLength.put(0x020B, 19);
    }

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

    LuaTable character = new LuaTable();
    LuaTable beings = new LuaTable();

    String mapServerIp;
    int mapServerPort;

    boolean mapLoaded = true;
    boolean quit = false;

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

    public void readHex() throws IOException {
        for(;;) {
            printHexByte(readByte());
        }
    }

    public void readCoordinatePair(LuaValue being) throws IOException {
        int b0 = readByte();
        int b1 = readByte();
        int b2 = readByte();
        int b3 = readByte();
        int b4 = readByte();
        being.set("dstx", (b3 | ((b2 & 0x0F) << 8)) >> 2);
        being.set("dsty", b4 | (b3 & 0x03) << 8);
        being.set("srcx", (b1 | (b0 << 8)) >> 6);
        being.set("srcy", (b2 | ((b1 & 0x3F) << 8)) >> 4);
    }

    public void readCoordinates(LuaValue being) throws IOException {
        int b0 = readByte();
        int b1 = readByte();
        int b2 = readByte();
        being.set("x", ((b1 & 0xC0) | ((b0 & 0xFF) << 8)) >> 6);
        being.set("y", ((b2 & 0xF0) | ((b1 & 0x3F) << 8)) >> 4);
        being.set("dir", b2 & 0x0F);
    }

    public void expectPacket(int p) throws IOException {
        int packet = readShort();
        if(packet != p) {
            System.out.append("unexpected packet ");
            printHexShort(packet);
            readHex();
        }
    }

    public LuaValue createBeing(int id, int job) {
        LuaTable being = new LuaTable();
        String type = "";
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
            writeShort(0x0094); // being name request
            writeInt(id);
        }

        return being;
    }

    public bot() throws Exception {
        System.out.println("connecting to login server");

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
        writeString(24, "chibot");
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

        System.out.println("connecting to char server");

        sock = new Socket(charServerIp, charServerPort); // char server
        out = sock.getOutputStream();
        in = sock.getInputStream();

        writeShort(0x0065); // character server connection request
        writeInt(acid);
        writeInt(sid1);
        writeInt(sid2);
        writeShort(1);
        writeByte(gender);

        in.skip(4);

        expectPacket(0x006B); // update character list
        {
            int len = readShort();
            in.skip(20);
            int charCount = (len - 24) / 106;

            character.set("id", readInt());
            character.set("exp", readInt());
            character.set("money", readInt());
            character.set("job_exp", readInt());
            int temp = readInt();
            character.set("job_base", temp);
            character.set("job_mod", temp);
            character.set("eq_shoes", readShort());
            character.set("eq_gloves", readShort());
            character.set("eq_cape", readShort());
            character.set("eq_misc1", readShort());
            in.skip(14);
            character.set("hp", readShort());
            character.set("max_hp", readShort());
            character.set("mp", readShort());
            character.set("max_mp", readShort());
            in.skip(2);
            character.set("race", readShort());
            character.set("hair_style", readShort());
            character.set("weapon", readShort());
            character.set("level", readShort());
            in.skip(2);
            character.set("eq_bottom", readShort());
            character.set("eq_shield", readShort());
            character.set("eq_head", readShort());
            character.set("eq_top", readShort());
            character.set("hair_color", readShort());
            character.set("eq_misc2", readShort());
            character.set("name", readString(24));
            character.set("st_str", readByte());
            character.set("st_agi", readByte());
            character.set("st_vit", readByte());
            character.set("st_int", readByte());
            character.set("st_dex", readByte());
            character.set("st_luk", readByte());
            in.skip(2);

            System.out.println(character.get("name") + " (" + character.get("level") + " lvl)");

            if(charCount > 1) in.skip(106 * (charCount - 1));
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

        System.out.println("connecting to map server");

        sock = new Socket(mapServerIp, mapServerPort); // map server
        out = sock.getOutputStream();
        in = sock.getInputStream();

        writeShort(0x0072); // CMSG_MAP_SERVER_CONNECT
        writeInt(acid);
        writeInt(character.get("id").toint());
        writeInt(sid1);
        writeInt(sid2);
        writeByte(gender);

        in.skip(4);

        expectPacket(0x0073); // SMSG_MAP_LOGIN_SUCCESS
        in.skip(4); // server tick
        readCoordinates(character);
        in.skip(2);

        Globals globals = JsePlatform.standardGlobals();

        globals.set("character", character);
        globals.set("map_name", mapName);
        globals.set("beings", beings);

        Thread reader = new Thread(new Runnable() {
            public void run() {
                try {
                    while(!quit) {
                        if(mapLoaded) {
                            writeShort(0x007D); // CMSG_MAP_LOADED
                            mapLoaded = false;
                        }

                        int packet = readShort();
                        switch(packet) {
                            case 0x0078: // SMSG_BEING_VISIBLE
                            case 0x007B: { // SMSG_BEING_MOVE
                                int id = readInt();
                                int speed = readShort();
                                int stunMode = readShort();
                                int statusEffects = readShort();
                                int statusEffects |= readShort() << 16;
                                int job = readShort();

                                LuaValue being = beings.get(id);
                                if(being != LuaValue.NIL) {
                                    if(job == 0 && id >= 110000000) {
                                        
                                    } else {
                                        being = createBeing(id, job);
                                    }
                                }
                                being.set("id", id);
                                being.set("speed", speed);
                                being.set("stunmode", stunmode);
                                being.set("status_effects", statusEffects);
                                being.set("hair_style", readShort());
                                being.set("eq_weapon", readShort());
                                being.set("eq_legs", readShort()); //headbottom

                                if(packet == 0x007B) {
                                    in.skip(4);
                                }

                                being.set("eq_shield", readShort());
                                being.set("eq_head", readShort()); //headtop
                                being.set("eq_torso", readShort()); //headmid
                                being.set("hair_color", readShort());
                                being.set("eq_shoes", readShort());
                                being.set("eq_gloves", readShort());
                                being.set("guild", readInt());
                                in.skip(4);
                                being.set("status_effect_block", readShort());
                                in.skip(1);
                                being.set("gender", readByte());
                                if(packet == 0x007B) {
                                    readCoordinatePair(being);
                                } else {
                                    readCoordinates(being);
                                }
                                in.skip(3);

                            } break;
                            case 0x0086: { // SMSG_BEING_MOVE_2
                                int id = readInt();
                                LuaValue being = beings.get(id);
                                if(being != LuaValue.NIL) {
                                    readCoordinatePair(being);
                                    in.skip(2); //server tick
                                } else {
                                    in.skip(7);
                                }
                                in.skip(3); // possibly coordinates
                            } break;
                            case 0x0080: { // SMSG_BEING_REMOVE
                                int id = readInt();
                                LuaValue being = beings.get(id);
                                if(being != LuaValue.NIL) {
                                    if(readByte() == 1) {
                                        being.set("dead", LuaValue.TRUE);
                                    } else {
                                        beings.set(id, LuaValue.NIL);
                                    }
                                } else {
                                    in.skip(1);
                                }
                            } break;
                            case 0x0148: { // SMSG_BEING_RESURRECT (8)
                                int id = readInt();
                                LuaValue being = beings.get(id);
                                if(being != LuaValue.NIL) {
                                    if(readByte() == 1) {
                                        being.set("dead", LuaValue.FALSE);
                                    }
                                } else {
                                    in.skip(1);
                                }
                                in.skip(1);
                            } break;
                            case 0x0095: { // SMSG_BEING_NAME_RESPONSE (30)
                                int id = readInt();
                                LuaValue being = beings.get(id);
                                if(being != LuaValue.NIL) {
                                    being.set("name", readString(24));
                                } else {
                                    in.skip(24);
                                }
                            } break;
                            default:
                                if(packetLength.containsKey(packet)) {
                                    int len = packetLength.get(packet);
                                    if(len == -1) {
                                        len = readShort() - 2;
                                    }
                                    in.skip(len - 2);
                                } else {
                                    System.out.append("unknown packet ");
                                    printHexShort(packet);
                                    readHex();
                                    quit = true;
                                }
                        }
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

        sock.close();
    }

    public static void main(String[] args) throws Exception {
        bot instance = new bot();
    }
}
