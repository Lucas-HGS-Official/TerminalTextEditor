// java -cp lib/jna-5.19.1.jar --enable-native-access=ALL-UNNAMED Viewer.java LOREM_IPSUM.txt


import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;


public class Viewer {

    private static final int ARROW_UP = 1000,
            ARROW_DOWN = 1001,
            ARROW_RIGHT = 1002,
            ARROW_LEFT = 1003,
            HOME = 1004,
            DEL = 1005,
            END = 1006,
            PAGE_UP = 1007,
            PAGE_DOWN = 1008;

    private static LibC.Termios OGAttr;

    private static int rows = 10, cols = 10;
    private static int cursorx = 0, offsetx = 0, cursory = 0, offsety = 0;

    private static List<String> content = List.of();


    public static void main(String[] args) throws IOException {

        openFile(args);
        EnableRawmode();
        initEditor();

        while (true) {
            scrolling();
            refreshScreen();
            int key = readKey();
            handleKey(key);
        }
    }
    private static void openFile(String[] args) {
        if (args.length == 1) {
            String filename = args[0];
            Path path = Path.of(filename);

            if (!Files.exists(path)) { return; }

            try (Stream<String> stream = Files.lines(path)) {
                content = stream.toList();
            } catch (IOException e) {
                // throw new RuntimeErrorException(e);
            }
        }
    }

    private static void scrolling() {
        if (cursory >= rows + offsety) {
            offsety = cursory - rows + 1;
        } else if (cursory < offsety) {
            offsety = cursory;
        }

        if (cursorx >= cols + offsetx) {
            offsetx = cursorx - cols + 1;
        } else if (cursorx < offsetx) {
            offsetx = cursorx;
        }
    }

    private static int readKey() throws IOException {
        int key = System.in.read();
        if (key != '\033') { return key; }
        int secondKey = System.in.read();
        if (secondKey != '[' && secondKey != 'O') { return secondKey; }
        int thirdKey = System.in.read();
        if (secondKey == '[') {
            return switch (thirdKey) {
                case 'A' -> ARROW_UP;
                case 'B' -> ARROW_DOWN;
                case 'C' -> ARROW_RIGHT;
                case 'D' -> ARROW_LEFT;
                case 'H' -> HOME;
                case 'F' -> END;
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    int fourthKey = System.in.read();
                    if (fourthKey != '~') {
                        yield fourthKey;
                    }
                    switch (thirdKey) {
                        case '1':
                        case '7':
                            yield HOME;
                        case '3':
                            yield DEL;
                        case '4':
                        case '8':
                            yield END;
                        case '5':
                            yield PAGE_UP;
                        case '6':
                            yield PAGE_DOWN;
                        default:
                            yield thirdKey;
                    }
                }
                default -> thirdKey;
            };
        } else {
            return switch (thirdKey) {
                case 'H' -> HOME;
                case 'F' -> END;
                default -> thirdKey;
            };
        }
    }

    private static void refreshScreen() {
        StringBuilder builder = new StringBuilder();

        builder.append("\033[H"); // moves cursor to top left
        drawContent(builder);
        drawStatus(builder);
        builder.append(String.format("\033[%d;%dH", cursory - offsety, cursorx - offsetx + 1)); // draws cursor to the correct position

        System.out.print(builder);
    }

    private static void drawContent(StringBuilder builder) {
        for (int i = 0; i < rows-1; i++) {
            int fileI = offsety + i;
            if (fileI >= content.size()) {
                builder.append("~");
            } else {
                String line = content.get(fileI);
                int lenToDraw = line.length() - offsetx;

                if (lenToDraw < 0) {
                    lenToDraw=0;
                }
                if (lenToDraw > cols) {
                    lenToDraw = cols;
                }
                if (lenToDraw > 0) {
                    builder.append(line, offsetx, offsetx + lenToDraw);
                }

                builder.append(line);
            }
            builder.append("\033[K\r\n");
        }
    }
    private static void drawStatus(StringBuilder builder) {
        java.lang.String statusMessage = "Rows: " + (rows-1) + " X: " + cursorx + " Y: " + cursory;
        builder.append(new StringBuilder()
                .append("\033[7m")
                .append(statusMessage)
                .append(" ".repeat(Math.max(0, cols - statusMessage.length())))
                .append("\033[0m\n").toString());
    }

    private static void handleKey(int key) {
        if (key == 'q' || key == 'Q') {
            System.out.print("\033[2J");
            System.out.print("\033[H");
            LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, OGAttr);
            System.exit(0);
        } else if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, END, HOME, PAGE_DOWN, PAGE_UP).contains(key)) {
            moveCursor(key);
        }
    }

    private static void moveCursor(int key) {
        switch (key) {
            case ARROW_UP -> {
                if (cursory > 0) { cursory--; }
            }
            case ARROW_DOWN -> {
                if (cursory < content.size()) { cursory++; }
            }
            case ARROW_LEFT -> {
                if (cursorx > 0) { cursorx--; }
            }
            case ARROW_RIGHT -> {
                if (cursorx < content.get(cursory).length() - 1) { cursorx++; }
            }
            case PAGE_DOWN, PAGE_UP -> {
                if (key == PAGE_DOWN) {
                    moveCursorToBottom();
                } else {
                    moveCursorToTop();
                }
                for (int i=0; i<rows-1; i++) {
                    moveCursor(key == PAGE_DOWN? ARROW_DOWN : ARROW_UP);
                }
            }
            case HOME -> cursorx = 0;
            case END -> cursorx = cols-1;
        }
    }
    private static void moveCursorToBottom() {
        cursory = offsety + rows-1;
    }
    private static void moveCursorToTop() {
        cursory = offsety;
    }

    private static void EnableRawmode() {
        LibC.Termios termios = new LibC.Termios();
        int rc = LibC.INSTANCE.tcgetattr(LibC.SYSTEM_OUT_FD, termios);
        if (rc != 0) {
            System.err.println("Error calling tcgetattr");
            System.exit(rc);
        }

        OGAttr = LibC.Termios.of(termios);

        termios.c_lflag &= ~(LibC.ECHO | LibC.ICANON | LibC.IEXTEN | LibC.ISIG);
        termios.c_iflag &= ~(LibC.IXON | LibC.ICRNL);
        termios.c_oflag &= ~(LibC.OPOST);

        termios.c_cc[LibC.VMIN] = 0;
        termios.c_cc[LibC.VTIME] = 1;

        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, termios);
    }

    private static LibC.Winsize getWindowSize() {
        final LibC.Winsize winsize = new LibC.Winsize();

        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.TIOCGWINSZ, winsize);
        if (rc != 0) {
            System.err.println("ioctl failed with return code[={}]" + rc);
            System.exit(1);
        }

        return winsize;
    }

    private static void initEditor() {
        LibC.Winsize windowSize = getWindowSize();
        rows = windowSize.ws_row-1;
        cols = windowSize.ws_col;
    }
}



interface LibC extends Library {

    int SYSTEM_OUT_FD = 0;
    int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2,
            IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6, VTIME = 5, TIOCGWINSZ = 0x5413;

    LibC INSTANCE = Native.load("c", LibC.class);

    @Structure.FieldOrder(value = {"ws_row", "ws_col", "ws_xpixel", "ws_ypixel"})
    class Winsize extends Structure {
        public short ws_row, ws_col, ws_xpixel, ws_ypixel;
    }

    @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc"})
    class Termios extends Structure {
        public int c_iflag, c_oflag, c_cflag, c_lflag;
        public byte[]  c_cc = new byte[19];

        public Termios() {
        }

        public static Termios of (Termios t) {
            Termios copy = new Termios();
            copy.c_cc = t.c_cc.clone();
            copy.c_oflag = t.c_oflag;
            copy.c_iflag = t.c_iflag;
            copy.c_cflag = t.c_cflag;
            copy.c_lflag = t.c_lflag;

            return copy;
        }
    }

    int tcgetattr(int fd, Termios termios);

    int tcsetattr(int fd, int optional_actions, Termios termios);

    int ioctl(int fd, int opt, Winsize winsize);
}
