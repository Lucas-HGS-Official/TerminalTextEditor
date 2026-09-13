// java -cp jna-5.19.1.jar --enable-native-access=ALL-UNNAMED Viewer.java

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import java.io.IOException;
import java.lang.classfile.Interfaces;


public class Viewer {

    private static LibC.Termios OGAttr;

    public static void main(String[] args) throws IOException {
        EnableRawmode();

        while (true) {
            int key = System.in.read();
            if (key == 'q') {
                LibC.INSTANCE.tcsetattr(LibC.SYSTEM_OUT_FD, LibC.TCSAFLUSH, OGAttr);
                System.exit(0);
            }
            System.out.print((char) key + " (" + key + ")\r\n");
        }
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
}


interface LibC extends Library {

    int SYSTEM_OUT_FD = 0;
    int ISIG = 1, ICANON = 2, ECHO = 10, TCSAFLUSH = 2,
            IXON = 2000, ICRNL = 400, IEXTEN = 100000, OPOST = 1, VMIN = 6, VTIME = 5, TIOCGWINSZ = 0x5413;

    LibC INSTANCE = Native.load("c", LibC.class);

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
}