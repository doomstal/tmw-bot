import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

public class Utils {
    static char[] hexDigits = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    public static String int8toHex(int v) {
        return "" + hexDigits[(v>>4)&0x0F] + hexDigits[v&0x0F];
    }

    public static String int16toHex(int v) {
        return int8toHex((v>>8)&0xFF)+int8toHex(v&0xFF);
    }

    public static void clearTable(LuaValue table) {
        LuaValue k = LuaValue.NIL;
        while(true) {
            Varargs n = table.next(k);
            if( (k = n.arg1()).isnil() ) break;
            table.set(k, LuaValue.NIL);
        }
    }

    public static void copyTable(LuaValue src, LuaValue dst) {
        LuaValue k = LuaValue.NIL;
        while(true) {
            Varargs n = src.next(k);
            if( (k = n.arg1()).isnil() ) break;
            dst.set(k, n.arg(2));
        }
    }
}
