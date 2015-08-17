public class Utils {
    static char[] hexDigits = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    public static void printHexDigit(int d) {
        if(d >= 0 && d < 16) System.out.append(hexDigits[d]);
    }

    public static void printHexInt8(int b) {
        printHexDigit((b >> 4) & 15);
        printHexDigit(b & 15);
    }

    public static void printHexInt16(int s) {
        printHexInt8( (s >> 8) & 255 );
        printHexInt8( s & 255 );
    }

    public static void printHexInt32(int s) {
        printHexInt8( (s >> 24) & 255 );
        printHexInt8( (s >> 16) & 255 );
        printHexInt8( (s >> 8) & 255 );
        printHexInt8( s & 255 );
    }
}
