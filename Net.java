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
        recv_bytes = 0;
        packet = readInt16();
        System.out.append("readpacket ");
        Utils.printHexInt16(packet);
        System.out.println();
        length = getPacketLength();
        if(length == -1) {
            length = readInt16();
        }
        return packet;
    }

    public void sendPacket(int p) throws IOException {
        send_bytes = 0;
        send_packet = p;
        writeInt16(p);
    }

    public int getRecvBytes() {
        return recv_bytes;
    }

    public int getSendBytes() {
        return send_bytes;
    }

    public int getPacketLength(int p) throws IOException {
        if(p == 0x7530) return 2;
        if(p == 0x7531) return 10;
        if(p < packetLength.length && packetLength[p] != 0) {
            return packetLength[p];
        }
        System.out.append("unknown packet ");
        Utils.printHexInt16(p);
        System.out.println();
        readHex();
        System.exit(1);
        return -2;
    }

    public int getPacketLength() throws IOException {
        if(length != -2) return length;
        return getPacketLength(packet);
    }

    public void checkExceedLength() throws IOException {
        if(packet != -1) {
            int len = getPacketLength();
            if(len > -1 && recv_bytes >= len) {
                Utils.printHexInt16(packet);
                System.out.println(" packet length exceeded!");
                System.exit(1);
            }
        }
    }

    public void checkPacketLength() throws IOException {
        if(packet != -1) {
            int len = getPacketLength();
            if(recv_bytes != len) {
                Utils.printHexInt16(packet);
                System.out.println(" packet lengths mismatch! pb="+recv_bytes+" len="+len);
                System.exit(1);
            }
        }
        packet = -1;
        length = -2;
    }

    public void checkSentPacketLength() throws IOException {
        int len = getPacketLength(send_packet);
        if(len != -1 && send_bytes != len) {
            Utils.printHexInt16(send_packet);
            System.out.println(" packet lengths mismatch! (send) pb="+send_bytes+" len="+len);
            System.exit(1);
        }
    }

    public void skipPacket() throws IOException {
        int len = getPacketLength();
        if(recv_bytes == len) return;
        skip(len - recv_bytes);
        recv_bytes = len;
    }

    public void readHex() throws IOException {
        for(;;) {
            Utils.printHexInt8(readInt8());
        }
    }

    public void skip(int n) throws IOException {
        checkExceedLength();
        sock.getInputStream().skip(n);
        recv_bytes += n;
    }

    public int readInt8() throws IOException {
        checkExceedLength();
        int b = sock.getInputStream().read();
        ++recv_bytes;
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
        recv_bytes += len;
        for(; len>0; --len) {
            char c = (char)sock.getInputStream().read();
            if(c == 0) append = false;
            if(append) sb.append(c);
        }
        return sb.toString();
    }

    public void writeInt8(int b) throws IOException {
        sock.getOutputStream().write(b);
        ++send_bytes;
    }

    public void writeInt16(int v) throws IOException {
        sock.getOutputStream().write( v & 255 );
        sock.getOutputStream().write( (v>>8) & 255 );
        send_bytes += 2;
    }

    public void writeInt32(int v) throws IOException {
        sock.getOutputStream().write( v & 255 );
        sock.getOutputStream().write( (v>>8) & 255 );
        sock.getOutputStream().write( (v>>16) & 255 );
        sock.getOutputStream().write( (v>>24) & 255 );
        send_bytes += 4;
    }

    public void writeString(int len, String str) throws IOException {
        byte[] b = str.getBytes();
        for(int i=0; i!=len; ++i) {
            if(i < b.length) sock.getOutputStream().write(b[i]);
            else sock.getOutputStream().write(0);
        }
        send_bytes += len;
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

        File file = new File("config.txt");
        BufferedReader br = new BufferedReader(new FileReader(file));
        String username = br.readLine();
        String password = br.readLine();
        String charname = br.readLine();

        writeInt16(0x0064); // login request
        writeInt32(0);
        writeString(24, username);
        writeString(24, password);
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
            b.character.set("job_level", readInt32());
            b.character.set("boots", readInt16());
            b.character.set("gloves", readInt16());
            b.character.set("cape", readInt16());
            b.character.set("misc1", readInt16());
            skip(12); // (2) option, (2) unused, (4) karma, (4) manner
            b.character.set("char_points", readInt16());
            b.character.set("hp", readInt16());
            b.character.set("hp_max", readInt16());
            b.character.set("mp", readInt16());
            b.character.set("mp_max", readInt16());
            b.character.set("speed", readInt16());
            b.character.set("race", readInt16());
            b.character.set("hair_style", readInt16());
            b.character.set("weapon", readInt16());
            b.character.set("level", readInt16());
            b.character.set("skill_points", readInt16());
            b.character.set("legs", readInt16());
            b.character.set("shield", readInt16());
            b.character.set("helmet", readInt16());
            b.character.set("armor", readInt16());
            b.character.set("hair_color", readInt16());
            b.character.set("misc2", readInt16());
            b.character.set("name", readString(24));
            b.character.set("str_base", readInt8());
            b.character.set("agi_base", readInt8());
            b.character.set("vit_base", readInt8());
            b.character.set("int_base", readInt8());
            b.character.set("dex_base", readInt8());
            b.character.set("luk_base", readInt8());
            skip(1); // char num
            skip(1); // unused

            b.beings.set(b.character.get("id"), b.character);

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
        skip(4);
        readCoordinates(b.character);
        skip(2);
        checkPacketLength();

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
