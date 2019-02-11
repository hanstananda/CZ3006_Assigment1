public class PFrame {
    public static final int DATA = 0;
    public static final int ACK = 1;
    public static final int NAK = 2;
    public static final String[] KIND = new String[]{"DATA", "ACK", "NAK"};
    public int kind = 0;
    public int ack = 0;
    public Packet info = new Packet();
    public int seq = 0;

    public PFrame() {
    }
}
