import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaTable;

public class Net {
    public static Map<Integer, Integer> packetLength = new HashMap<Integer, Integer>();

    Socket sock;

    int packet = -1;
    int packet_bytes = 0;
    int length = -2;

    public Net() { }

    public void readCoordinatePair(LuaValue being) throws IOException {
        int b0 = readInt8();
        int b1 = readInt8();
        int b2 = readInt8();
        int b3 = readInt8();
        int b4 = readInt8();
        being.set("x", (b1 | (b0 << 8)) >> 6);
        being.set("y", (b2 | ((b1 & 0x3F) << 8)) >> 4);
        being.set("dstx", (b3 | ((b2 & 0x0F) << 8)) >> 2);
        being.set("dsty", b4 | (b3 & 0x03) << 8);
    }

    public void readCoordinates(LuaValue being) throws IOException {
        int b0 = readInt8();
        int b1 = readInt8();
        int b2 = readInt8();
        being.set("x", ((b1 & 0xC0) | ((b0 & 0xFF) << 8)) >> 6);
        being.set("y", ((b2 & 0xF0) | ((b1 & 0x3F) << 8)) >> 4);
        being.set("dstx", being.get("x"));
        being.set("dsty", being.get("y"));
        being.set("dir", b2 & 0x0F);
    }

    public void expectPacket(int p) throws IOException {
        readPacket();
        if(packet != p) {
            System.out.append("unexpected packet ");
            Utils.printHexInt16(packet);
            readHex();
        }
    }

    public int readPacket() throws IOException {
        length = -2;
        packet_bytes = 0;
        packet = readInt16();
        length = getPacketLength();
        if(length == -1) {
            length = readInt16();
        }
        return packet;
    }

    public int getPacketBytes() {
        return packet_bytes;
    }

    public int getPacketLength() throws IOException {
        if(length != -2) return length;
        if(packetLength.containsKey(packet)) {
            return packetLength.get(packet);
        }
        System.out.append("unknown packet ");
        Utils.printHexInt16(packet);
        System.out.println();
        readHex();
        System.exit(1);
        return -2;
    }

    public void checkExceedLength() throws IOException {
        if(packet != -1) {
            int len = getPacketLength();
            if(len > -1 && packet_bytes >= len) {
                Utils.printHexInt16(packet);
                System.out.println(" packet length exceeded!");
                System.exit(1);
            }
        }
    }

    public void checkPacketLength() throws IOException {
        if(packet != -1) {
            int len = getPacketLength();
            if(packet_bytes != len) {
                Utils.printHexInt16(packet);
                System.out.println(" packet lengths mismatch! pb="+packet_bytes+" len="+len);
                System.exit(1);
            }
        }
        packet = -1;
        length = -2;
    }

    public void skipPacket() throws IOException {
        int len = getPacketLength();
        if(packet_bytes == len) return;
        skip(len - packet_bytes);
        packet_bytes = len;
    }

    public void readHex() throws IOException {
        for(;;) {
            Utils.printHexInt8(readInt8());
        }
    }

    public void skip(int n) throws IOException {
        checkExceedLength();
        sock.getInputStream().skip(n);
        packet_bytes += n;
    }

    public int readInt8() throws IOException {
        checkExceedLength();
        int b = sock.getInputStream().read();
        ++packet_bytes;
        if(b == -1) throw new IOException("socket closed");
        return b;
    }

    public int readInt16() throws IOException {
        int b1 = readInt8();
        int b2 = readInt8();
        return b1 + (b2<<8);
    }

    public int readInt32() throws IOException {
        int b1 = readInt8();
        int b2 = readInt8();
        int b3 = readInt8();
        int b4 = readInt8();
        return b1 + (b2<<8) + (b3<<16) + (b4<<24);
    }

    public String readString(int len) throws IOException {
        checkExceedLength();
        StringBuilder sb = new StringBuilder();
        boolean append = true;
        packet_bytes += len;
        for(; len>0; --len) {
            char c = (char)sock.getInputStream().read();
            if(c == 0) append = false;
            if(append) sb.append(c);
        }
        return sb.toString();
    }

    public void writeInt8(int b) throws IOException {
        sock.getOutputStream().write(b);
    }

    public void writeInt16(int v) throws IOException {
        sock.getOutputStream().write( v & 255 );
        sock.getOutputStream().write( (v>>8) & 255 );
    }

    public void writeInt32(int v) throws IOException {
        sock.getOutputStream().write( v & 255 );
        sock.getOutputStream().write( (v>>8) & 255 );
        sock.getOutputStream().write( (v>>16) & 255 );
        sock.getOutputStream().write( (v>>24) & 255 );
    }

    public void writeString(int len, String str) throws IOException {
        byte[] b = str.getBytes();
        for(int i=0; i!=len; ++i) {
            if(i < b.length) sock.getOutputStream().write(b[i]);
            else sock.getOutputStream().write(0);
        }
    }

    public void writeCoordinates(int x, int y, int dir) throws IOException {
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
        writeInt8(b0);
        writeInt8(b1);
        writeInt8(b2);
    }

    public void connect(bot b) throws IOException {
        System.out.println("connecting to login server");

        sock = new Socket("server.themanaworld.org", 6901); // login server

        writeInt16(0x7530); // version request

        expectPacket(0x7531); // version response
        {
            int major = readInt8();
            int minor = readInt8();
            int patch = readInt8();
            int devel = readInt8();
            int flags = readInt8();
            int which = readInt8();
            int vendor = readInt16();
        }
        checkPacketLength();

        writeInt16(0x0064); // login request
        writeInt32(0);
        writeString(24, "chibot");
        writeString(24, "m1ghtyb0t");
        writeInt8(2);

        readPacket();
        if(packet == 0x0063) { // update host
            int len = getPacketLength() - 4;
            System.out.println(readString(len));

            checkPacketLength();
            readPacket();
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
            int len = getPacketLength();
            int worldCount = (len - 47) / 32;

            b.sid1 = readInt32();
            b.acid = readInt32();
            b.sid2 = readInt32();

            System.out.append("Session ID : ");
            Utils.printHexInt32(b.sid1);
            Utils.printHexInt32(b.sid2);
            System.out.append("\nAccount ID : ");
            Utils.printHexInt32(b.acid);
            System.out.println();

            skip(30);

            b.gender = readInt8();

            for(int i=0; i!=worldCount; ++i) {
                b.charServerIp = "" + readInt8() + "." + readInt8() + "." + readInt8() + "." + readInt8();
                b.charServerPort = readInt16() & 0x0000FFFF;
                String name = readString(20);
                int online = readInt32();

                System.out.println(b.charServerIp + ":" + b.charServerPort + " " + name + " (" + online + ")");
                skip(2);
            }
            checkPacketLength();
        } else {
            System.out.append("unexpected packet! ");
            Utils.printHexInt16(packet);
            System.exit(2);
        }

        sock.close(); // disconnect from login server

        System.out.println("connecting to char server");

        sock = new Socket(b.charServerIp, b.charServerPort); // char server

        writeInt16(0x0065); // character server connection request
        writeInt32(b.acid);
        writeInt32(b.sid1);
        writeInt32(b.sid2);
        writeInt16(1);
        writeInt8(b.gender);

        skip(4);

        expectPacket(0x006B); // update character list
        {
            int len = getPacketLength();
            skip(20);
            int charCount = (len - 24) / 106;

            b.character.set("id", readInt32());
            b.character.set("exp", readInt32());
            b.character.set("money", readInt32());
            b.character.set("job_exp", readInt32());
            int temp = readInt32();
            b.character.set("job_base", temp);
            b.character.set("job_mod", temp);
            b.character.set("eq_shoes", readInt16());
            b.character.set("eq_gloves", readInt16());
            b.character.set("eq_cape", readInt16());
            b.character.set("eq_misc1", readInt16());
            skip(14);
            b.character.set("hp", readInt16());
            b.character.set("max_hp", readInt16());
            b.character.set("mp", readInt16());
            b.character.set("max_mp", readInt16());
            skip(2);
            b.character.set("race", readInt16());
            b.character.set("hair_style", readInt16());
            b.character.set("eq_weapon", readInt16());
            b.character.set("level", readInt16());
            skip(2);
            b.character.set("eq_legs", readInt16());
            b.character.set("eq_shield", readInt16());
            b.character.set("eq_head", readInt16());
            b.character.set("eq_torso", readInt16());
            b.character.set("hair_color", readInt16());
            b.character.set("eq_misc2", readInt16());
            b.character.set("name", readString(24));
            b.character.set("st_str", readInt8());
            b.character.set("st_agi", readInt8());
            b.character.set("st_vit", readInt8());
            b.character.set("st_int", readInt8());
            b.character.set("st_dex", readInt8());
            b.character.set("st_luk", readInt8());
            skip(2);

            System.out.println(b.character.get("name") + " (" + b.character.get("level") + " lvl)");

            if(charCount > 1) skip(106 * (charCount - 1));
        }
        checkPacketLength();

        writeInt16(0x0066); // select character request
        writeInt8(0);

        expectPacket(0x0071); // SMSG_CHAR_MAP_INFO
        skip(4);
        b.mapName = readString(16);
        b.mapServerIp = "" + readInt8() + "." + readInt8() + "." + readInt8() + "." + readInt8();
        b.mapServerPort = readInt16() & 0x0000FFFF;
        System.out.println(b.mapServerIp + ":" + b.mapServerPort + " " + b.mapName);
        checkPacketLength();

        sock.close();

        System.out.println("connecting to map server");

        sock = new Socket(b.mapServerIp, b.mapServerPort); // map server

        writeInt16(0x0072); // CMSG_MAP_SERVER_CONNECT
        writeInt32(b.acid);
        writeInt32(b.character.get("id").toint());
        writeInt32(b.sid1);
        writeInt32(b.sid2);
        writeInt8(b.gender);

        skip(4);

        expectPacket(0x0073); // SMSG_MAP_LOGIN_SUCCESS
        skip(4); // server tick
        readCoordinates(b.character);
        skip(2);
        checkPacketLength();

    }

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
}
