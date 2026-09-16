// java -cp jna-5.19.1.jar --enable-native-access=ALL-UNNAMED Viewer.java

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import java.io.IOException;
import java.lang.classfile.Interfaces;


public class Viewer {

    private static LibC.Termios OGAttr;
    private static int rows = 10;
    private static int cols = 10;

    public static void main(String[] args) throws IOException {
        EnableRawmode();
        initEditor();

        while (true) {
            refreshScreen();
            int key = System.in.read();
            handleKey(key);
        }
    }

    private static void refreshScreen() {
        StringBuilder builder = new StringBuilder();

        builder.append("\033[2J");
        builder.append("\033[H");
        for (int i = 0; i < rows-1; i++) {
            builder.append("~\r\n");
        }
        java.lang.String statusMessage = "Code Editor - v0.0.1 ALPHA";
        builder.append(new StringBuilder()
                .append("\033[7m")
                .append(statusMessage)
                .append(" ".repeat(Math.max(0, cols - statusMessage.length())))
                .append("\033[0m\n").toString());
        builder.append("\033[H");

        System.out.print(builder);
    }

    private static void handleKey(int key) {
        if (key == 'q') {
            System.out.print("\033[2J");
            System.out.print("\033[H");
            LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, OGAttr);
            System.exit(0);
        }
        System.out.print((char) key + " (" + key + ")\r\n");
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
        rows = windowSize.ws_row;
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