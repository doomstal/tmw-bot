import java.util.Comparator;
import java.util.TreeSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.LinkedList;
import java.util.Queue;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import static org.luaj.vm2.LuaValue.*;

public class Map {
    public int width;
    public int height;
    int[][] map;

    static int costsWidth = 500;
    static int costsHeight = 500;
    static int[][] threat = null;
    static int[][] costs = null;
    static Direction[][] directions = null;

    static void initArrays() {
        threat = new int[costsHeight][costsWidth];
        costs = new int[costsHeight][costsWidth];
        directions = new Direction[costsHeight][costsWidth];
    }

    static {
        initArrays();
    }

    public Map(String map_name) {
        loadMap(map_name);
    }

    void newRegion(int x, int y, int region) {
        LinkedList<Integer> marked = new LinkedList<Integer>();

        map[y][x] = region;
        marked.add(y*width + x);
        while(!marked.isEmpty()) {
            int xy = marked.remove();
            x = xy % width;
            y = xy / width;
            if(x<=0 || x>=width-1 || y<=0 || y>=height-1) continue;
            if(map[y][x+1] == -1) {
                map[y][x+1] = region;
                marked.add(y*width + x+1);
            }
            if(map[y][x-1] == -1) {
                map[y][x-1] = region;
                marked.add(y*width + x-1);
            }
            if(map[y-1][x] == -1) {
                map[y-1][x] = region;
                marked.add((y-1)*width + x);
            }
            if(map[y+1][x] == -1) {
                map[y+1][x] = region;
                marked.add((y+1)*width + x);
            }
        }
    }

    public void loadMap(String map_name) {
        try {
            boolean cached = true;
            File file = new File("cache/maps/" + map_name + ".map");
            if(!file.exists()) {
                file = new File("server-data/world/map/data/" + map_name + ".wlk");
                cached = false;
            }

            FileInputStream in = new FileInputStream(file);
            width = (in.read() + (in.read() << 8));
            height = (in.read() + (in.read() << 8));
            map = new int[height][width];
            if(width > costsWidth || height > costsHeight) {
                costsWidth = width;
                costsHeight = height;
                initArrays();
            }
            byte[] buff = new byte[width];
            for(int j=0; j!=height; ++j) {
                if(in.read(buff) != width) throw new RuntimeException("bad map file: "+file.getName());
                for(int i=0; i!=width; ++i) {
                    if(cached) {
                        map[j][i] = buff[i];
                    } else {
                        if((buff[i] & 0x01) != 0) map[j][i] = 0;
                        else map[j][i] = -1;
                    }
                }
            }
            in.close();

            if(!cached) {
                int region = 1;
                for(int j=0; j!=height; ++j) {
                    for(int i=0; i!=width; ++i) {
                        if(map[j][i] == -1) newRegion(i, j, region++);
                    }
                }
                if(region > 255) {
                    System.out.println("too many regions!");
                    System.exit(1);
                }

                file = new File("cache/maps/" + map_name + ".map");
                if(!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
                FileOutputStream out = new FileOutputStream(file);
                out.write((byte)(width & 0xFF));
                out.write((byte)(width >> 8));
                out.write((byte)(height & 0xFF));
                out.write((byte)(height >> 8));
                for(int j=0; j!=height; ++j) {
                    for(int i=0; i!=width; ++i) {
                        out.write((byte)map[j][i]);
                    }
                }
                out.close();
                System.out.println("saved map to cache "+map_name+" "+width+","+height);
            } else {
                System.out.println("loaded cached map "+map_name+" "+width+","+height);
            }
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static Queue<Position> walk = new LinkedList<Position>();
    public int x1;
    public int x2;
    public int y1;
    public int y2;

    public static enum Direction {
        NONE, U, D, L, R, UL, UR, DL, DR
    }

    public static class Position {
        public int x;
        public int y;
        public int cost;
        public int length;
        public Direction dir;
        Position(int x, int y, int cost, int length, Direction dir) {
            this.x = x;
            this.y = y;
            this.cost = cost;
            this.length = length;
            this.dir = dir;
        }
    }

    public void printMap(int i1, int j1, int i2, int j2) {
        if(i1 < 0) i1 = 0;
        if(i2 > width) i2 = width;
        if(j1 < 0) j1 = 0;
        if(j2 > height) j2 = height;
        for(int j=j1; j!=j2; ++j) {
            for(int i=i1; i!=i2; ++i) {
                char c = '.';
                if(costs[j][i] > -1) c = ',';
                if(i==x1 && j==y1) c = 'A';
                if(i==x2 && j==y2) c = 'B';
                if(map[j][i] == 0) c = ' ';
                System.out.append(c);
            }
            System.out.println();
        }
    }

    public boolean walkable(int x, int y, int dx, int dy) {
        if(x+dx < 0 || x+dx >= width || y+dy < 0 || y+dy >= height) return false;
        return map[y+dy][x+dx]!=0 && map[y][x+dx]!=0 && map[y+dy][x]!=0;
    }

    static LuaTable makeLuaPosition(int x, int y) {
        LuaTable t = new LuaTable();
        t.set("x", x);
        t.set("y", y);
        return t;
    }

    public int getRegion(int x, int y) {
        if(x<0 || x>=width || y<0 || y>=height) return 0;
        return map[y][x];
    }

    public boolean isAccesible(int x1, int y1, int x2, int y2) {
        if(x1<0 || x1>=width || y1<0 || y1>=height
        || x2<0 || x2>=width || y2<0 || y2>=height) return false;
        return map[y1][x1]!=0 && map[y2][x2]!=0 && map[y1][x1]==map[y2][x2];
    }

    public LuaValue findPath(int lx1, int ly1, int lx2, int ly2) {
        this.x1 = lx1;
        this.y1 = ly1;
        this.x2 = lx2;
        this.y2 = ly2;

        if(map[y1][x1]==0 || map[y2][x2]==0 || map[y1][x1] != map[y2][x2]) return NIL;

        int x = x1;
        int y = y1;
        int dx = (x2>x1) ? 1 : -1;
        int dy = (y2>y1) ? 1 : -1;

        LuaTable tmpPath = new LuaTable();
        int i = 1;
        while(true) {
            tmpPath.set(i++, makeLuaPosition(x, y));
            if(x==x2 && y==y2) break;
            if(x==x2) dx=0;
            if(y==y2) dy=0;
            if(!walkable(x,y,dx,dy)) {
                tmpPath = null;
                break;
            }
            x += dx;
            y += dy;
        }
        if(tmpPath != null) return tmpPath;

        int path_cost = -1;
        int path_length = 0;

        for(int j=0; j!=height; ++j) {
            for(i=0; i!=width; ++i) costs[j][i] = -1;
        }

        walk.clear();
        walk.add(new Position(x1, y1, 0, 1, Direction.NONE));

        while(!walk.isEmpty()) {
            Position p = (Position)walk.remove();
            if(costs[p.y][p.x]>-1 && p.cost >= costs[p.y][p.x]) continue;
            if(path_cost>-1 && p.cost >= path_cost) continue;

            costs[p.y][p.x] = p.cost;
            directions[p.y][p.x] = p.dir;
            if(p.x == x2 && p.y == y2) {
                if(path_cost == -1 || path_cost > p.cost) {
                    path_cost = p.cost;
                    path_length = p.length;
                }
            } else {
                checkWalkableAdd(p, 1, -1, Direction.UR);
                checkWalkableAdd(p, 1, 1, Direction.DR);
                checkWalkableAdd(p, -1, 1, Direction.DL);
                checkWalkableAdd(p, -1, -1, Direction.UL);
                checkWalkableAdd(p, 1, 0, Direction.R);
                checkWalkableAdd(p, 0, 1, Direction.D);
                checkWalkableAdd(p, -1, 0, Direction.L);
                checkWalkableAdd(p, 0, -1, Direction.U);
            }
        }

        if(path_cost == -1) {
            System.out.println("path_cost == -1  lx1="+lx1+" ly1="+ly1+" lx2="+lx2+" ly2="+ly2);
            System.out.println("r1="+map[y1][x1]+" r2="+map[y2][x2]);
            printMap(x1-5, y1-5, x2+5, y2+5);
            return NIL;
        }

        LuaTable path = new LuaTable();
        i=path_length;
        x = x2;
        y = y2;
        while(i>0) {
            path.set(i--, makeLuaPosition(x, y));
            if(x==x1 && y==y1) break;
            switch(directions[y][x]) {
                case U: ++y; break;
                case D: --y; break;
                case L: ++x; break;
                case R: --x; break;
                case UL: ++x; ++y; break;
                case UR: --x; ++y; break;
                case DL: ++x; --y; break;
                case DR: --x; --y; break;
                default:
                    System.out.println("unknown direction");
                    printMap(x1-5, y1-5, x2+5, y2+5);
                    return NIL;
            }
        }
        if(x!=x1 || y!=y1) {
            System.out.println("x1!=x1 || y!=y1");
            return NIL;
        }
        return path;
    }

    void checkWalkableAdd(Position p, int dx, int dy, Direction dir) {
        int cost = 10;
        if(dx!=0 && dy!=0) cost = 14;
        if(walkable(p.x, p.y, dx, dy)) walk.add(new Position(p.x + dx, p.y + dy, p.cost + cost, p.length + 1, dir));
    }
}
