// java -cp lib/jna-5.19.1.jar --enable-native-access=ALL-UNNAMED Viewer.java big.txt


import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;


public class Viewer {

    private static final int ARROW_UP = 1000,
            ARROW_DOWN = 1001,
            ARROW_RIGHT = 1002,
            ARROW_LEFT = 1003,
            HOME = 1004,
            DEL = 1005,
            BACKSPACE = 127,
            END = 1006,
            PAGE_UP = 1007,
            PAGE_DOWN = 1008;


    private static int rows = 10, cols = 10;
    private static int cursorx = 0, offsetx = 0, cursory = 0, offsety = 0;

    private static List<String> content = List.of();

    static String statusMessage;

    private static Terminal terminal =
    Platform.isWindows() ?
        new WindowsTerminal() :
        Platform.isMac() ?
            new MacOsTerminal() :
            new UnixTerminal();


    public static void main(String[] args) throws IOException {

        openFile(args);
        terminal.enableRawmode();
        initEditor();

        while (true) {
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
        scrolling();

        StringBuilder builder = new StringBuilder();

        builder.append("\033[H"); // moves cursor to top left
        drawContent(builder);
        drawStatus(builder);
        builder.append(String.format("\033[%d;%dH", cursory - offsety + 1, cursorx - offsetx + 1)); // draws cursor to the correct position

        System.out.print(builder);
    }

    private static void drawContent(StringBuilder builder) {
        for (int i = 0; i < rows; i++) {
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
            }
            builder.append("\033[K\r\n");
        }
    }
    private static void drawStatus(StringBuilder builder) {
        String message = statusMessage != null ? statusMessage : "Rows: " + (rows) + " X: " + cursorx + " Y: " + cursory;
        builder.append(new StringBuilder()
                .append("\033[7m")
                .append(message)
                .append(" ".repeat(Math.max(0, cols - message.length())))
                .append("\033[0m").toString());
    }

    public static void setStatusMessage(String statusMessage) {
        Viewer.statusMessage = statusMessage;
    }

    private static void handleKey(int key) {
        if (key == ctrl('q')) {
            System.out.print("\033[2J");
            System.out.print("\033[H");
            terminal.disableRawMode();
            System.exit(0);
        } else if (key == ctrl('f')) {
            editorSearch();
        } else if (List.of(ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT, END, HOME, PAGE_DOWN, PAGE_UP).contains(key)) {
            moveCursor(key);
        }
    }

    enum SearchDir {
        FORWARD, BAKCWARD,
    }

    static SearchDir searchDir = SearchDir.FORWARD;

    static int lastMatch = -1;

    private static void editorSearch() {
        promptUser("Search %s (ESC/Arrows/Enter)", (query, keyPressed) -> {
            if (query == null || query.isBlank()) {
                searchDir = SearchDir.FORWARD;
                lastMatch = -1;
                return;
            }
            if (keyPressed == ARROW_LEFT || keyPressed == ARROW_UP) {
                searchDir = SearchDir.BAKCWARD;
            } else if (keyPressed == ARROW_RIGHT || keyPressed == ARROW_DOWN) {
                searchDir = SearchDir.FORWARD;
            } else {
                searchDir = SearchDir.FORWARD;
                lastMatch = -1;
            }

            int currentI = lastMatch;
            for (int i=0; i<content.size(); i++) {
                currentI += searchDir == SearchDir.FORWARD? 1 : -1;

                if (currentI == content.size()) {
                    currentI = 0;
                } else if (currentI == -1) {
                    currentI = content.size()-1;
                }

                String line = content.get(currentI);
                int match = line.indexOf(query);
                if (match != -1) {
                    lastMatch = currentI;
                    cursory = currentI;
                    cursorx = match;
                    offsety = content.size();
                    break;
                }
            }
        });
    }

    private static void promptUser(String message, BiConsumer<String, Integer> consumer) {
        StringBuilder input = new StringBuilder();

        while (true) {
            try {
                setStatusMessage(!input.isEmpty() ? input.toString() : message);
                refreshScreen();
                int key = readKey();
                if (key == '\033' || key == '\r') {
                    setStatusMessage(null);
                    return;
                } else if (key == DEL || key == BACKSPACE || key == ctrl('h')) {
                    if (!input.isEmpty()) {
                        input.deleteCharAt(input.length() -1);
                    }
                } else if (!Character.isISOControl(key) && key < 128) {
                    input.append((char) key);
                }
                consumer.accept(input.toString(), key);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static int ctrl(char key) {
        return key & 0x1f;
    }

    private static void moveCursor(int key) {
        String line = getCurrentLine();
        if (line == null) {
            line = "";
        }
        switch (key) {
            case ARROW_UP -> {
                if (cursory > 0) { cursory--; }
            }
            case ARROW_DOWN -> {
                if (cursory < content.size()-1) { cursory++; }
            }
            case ARROW_LEFT -> {
                if (cursorx > 0) { cursorx--; }
            }
            case ARROW_RIGHT -> {
                if (cursorx < line.length()) { cursorx++; }
            }
            case PAGE_DOWN, PAGE_UP -> {
                if (key == PAGE_DOWN) {
                    moveCursorToBottom();
                } else {
                    moveCursorToTop();
                }
                for (int i=0; i<rows; i++) {
                    moveCursor(key == PAGE_DOWN? ARROW_DOWN : ARROW_UP);
                }
            }
            case HOME -> cursorx = 0;
            case END -> cursorx = line.length();
        }

        line = getCurrentLine();
        if (cursorx > line.length()) {
            cursorx = line.length();
        }
    }

    private static String getCurrentLine() {
        return cursory < content.size() ? content.get(cursory) : null;
    }

    private static void moveCursorToBottom() {
        cursory = offsety + rows-1;
        if (cursory > content.size()) { cursory = content.size(); }
    }
    private static void moveCursorToTop() {
        cursory = offsety;
    }

    private static void initEditor() {
        WindowSize windowSize = terminal.getWindowSize();
        rows = windowSize.rows()-1;
        cols = windowSize.cols();
    }
}

interface Terminal {
    void enableRawmode();
    void disableRawMode();
    WindowSize getWindowSize();
}


record WindowSize(int rows, int cols) {

}

class UnixTerminal implements Terminal {
    private static LibC.Termios OGAttr;

    @Override
    public void enableRawmode() {
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

    @Override
    public void disableRawMode() {
        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, OGAttr);
    }

    @Override
    public WindowSize getWindowSize() {
        final LibC.Winsize winsize = new LibC.Winsize();

        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.TIOCGWINSZ, winsize);
        if (rc != 0) {
            System.err.println("ioctl failed with return code[={}]" + rc);
            System.exit(1);
        }

        return new WindowSize(winsize.ws_row, winsize.ws_col);
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
}

class MacOsTerminal implements Terminal {
    private static LibC.Termios OGAttr;

    @Override
    public void enableRawmode() {
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

    @Override
    public void disableRawMode() {
        LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, OGAttr);
    }

    @Override
    public WindowSize getWindowSize() {
        final LibC.Winsize winsize = new LibC.Winsize();

        final int rc = LibC.INSTANCE.ioctl(LibC.SYSTEM_OUT_FD, LibC.TIOCGWINSZ, winsize);
        if (rc != 0) {
            System.err.println("ioctl failed with return code[={}]" + rc);
            System.exit(1);
        }

        return new WindowSize(winsize.ws_row, winsize.ws_col);
    }

    interface LibC extends Library {

        int SYSTEM_OUT_FD = 0;
        int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2,
                IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6, VTIME = 5, TIOCGWINSZ = 0x40087468;

        LibC INSTANCE = Native.load("c", LibC.class);

        @Structure.FieldOrder(value = {"ws_row", "ws_col", "ws_xpixel", "ws_ypixel"})
        class Winsize extends Structure {
            public short ws_row, ws_col, ws_xpixel, ws_ypixel;
        }

        @Structure.FieldOrder(value = {"c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_cc"})
        class Termios extends Structure {
            public long c_iflag, c_oflag, c_cflag, c_lflag;
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
}

class WindowsTerminal implements Terminal {

    private IntByReference inMode;
    private IntByReference outMode;

    @Override
    public void enableRawmode() {
        Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);

        inMode = new IntByReference();
        Kernel32.INSTANCE.GetConsoleMode(inHandle, inMode);

        int inMode;
        inMode = this.inMode.getValue() & ~(
                Kernel32.ENABLE_ECHO_INPUT
                        | Kernel32.ENABLE_LINE_INPUT
                        | Kernel32.ENABLE_MOUSE_INPUT
                        | Kernel32.ENABLE_WINDOW_INPUT
                        | Kernel32.ENABLE_PROCESSED_INPUT
        );

        inMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_INPUT;


        Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode);

        Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        outMode = new IntByReference();
        Kernel32.INSTANCE.GetConsoleMode(outHandle, outMode);

        int outMode = this.outMode.getValue();
        outMode |= Kernel32.ENABLE_VIRTUAL_TERMINAL_PROCESSING;
        outMode |= Kernel32.ENABLE_PROCESSED_OUTPUT;
        Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode);

    }


    @Override
    public void disableRawMode() {
        Pointer inHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
        Kernel32.INSTANCE.SetConsoleMode(inHandle, inMode.getValue());

        Pointer outHandle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        Kernel32.INSTANCE.SetConsoleMode(outHandle, outMode.getValue());
    }


    @Override
    public WindowSize getWindowSize() {
        final Kernel32.CONSOLE_SCREEN_BUFFER_INFO info = new Kernel32.CONSOLE_SCREEN_BUFFER_INFO();
        final Kernel32 instance = Kernel32.INSTANCE;
        final Pointer handle = Kernel32.INSTANCE.GetStdHandle(Kernel32.STD_OUTPUT_HANDLE);
        instance.GetConsoleScreenBufferInfo(handle, info);
        return new WindowSize(info.windowHeight(), info.windowWidth());
    }

    interface Kernel32 extends StdCallLibrary {

        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        public static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004, ENABLE_PROCESSED_OUTPUT = 0x0001;

        int ENABLE_LINE_INPUT = 0x0002;
        int ENABLE_PROCESSED_INPUT = 0x0001;
        int ENABLE_ECHO_INPUT = 0x0004;
        int ENABLE_MOUSE_INPUT = 0x0010;
        int ENABLE_WINDOW_INPUT = 0x0008;
        int ENABLE_QUICK_EDIT_MODE = 0x0040;
        int ENABLE_INSERT_MODE = 0x0020;

        int ENABLE_EXTENDED_FLAGS = 0x0080;

        int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;


        int STD_OUTPUT_HANDLE = -11;
        int STD_INPUT_HANDLE = -10;
        int DISABLE_NEWLINE_AUTO_RETURN = 0x0008;

        void GetConsoleScreenBufferInfo(
                Pointer in_hConsoleOutput,
                CONSOLE_SCREEN_BUFFER_INFO out_lpConsoleScreenBufferInfo)
                throws LastErrorException;

        void GetConsoleMode(
                Pointer in_hConsoleOutput,
                IntByReference out_lpMode)
                throws LastErrorException;

        void SetConsoleMode(
                Pointer in_hConsoleOutput,
                int in_dwMode) throws LastErrorException;

        Pointer GetStdHandle(int nStdHandle);

        class CONSOLE_SCREEN_BUFFER_INFO extends Structure {


            public COORD dwSize;
            public COORD dwCursorPosition;
            public short wAttributes;
            public SMALL_RECT srWindow;
            public COORD dwMaximumWindowSize;

            private static String[] fieldOrder = {"dwSize", "dwCursorPosition", "wAttributes", "srWindow", "dwMaximumWindowSize"};

            @Override
            protected java.util.List<String> getFieldOrder() {
                return java.util.Arrays.asList(fieldOrder);
            }

            public int windowWidth() {
                return this.srWindow.width() + 1;
            }

            public int windowHeight() {
                return this.srWindow.height() + 1;
            }
        }

        class COORD extends Structure implements Structure.ByValue {
            public COORD() {
            }

            public COORD(short X, short Y) {
                this.X = X;
                this.Y = Y;
            }

            public short X;
            public short Y;

            private static String[] fieldOrder = {"X", "Y"};

            @Override
            protected java.util.List<String> getFieldOrder() {
                return java.util.Arrays.asList(fieldOrder);
            }
        }

        // typedef struct _SMALL_RECT {
        //    SHORT Left;
        //    SHORT Top;
        //    SHORT Right;
        //    SHORT Bottom;
        //  } SMALL_RECT;
        class SMALL_RECT extends Structure {
            public SMALL_RECT() {
            }

            public SMALL_RECT(SMALL_RECT org) {
                this(org.Top, org.Left, org.Bottom, org.Right);
            }

            public SMALL_RECT(short Top, short Left, short Bottom, short Right) {
                this.Top = Top;
                this.Left = Left;
                this.Bottom = Bottom;
                this.Right = Right;
            }

            public short Left;
            public short Top;
            public short Right;
            public short Bottom;

            private static String[] fieldOrder = {"Left", "Top", "Right", "Bottom"};

            @Override
            protected java.util.List<String> getFieldOrder() {
                return java.util.Arrays.asList(fieldOrder);
            }

            public short width() {
                return (short) (this.Right - this.Left);
            }

            public short height() {
                return (short) (this.Bottom - this.Top);
            }
        }
    }
}
