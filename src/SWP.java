import java.util.Timer;
import java.util.TimerTask;


public class SWP {
    public static final int MAX_SEQ = 7;
    public static final int NR_BUFS = (MAX_SEQ + 1) / 2;
    private int oldest_frame = 0;
    private PEvent event = new PEvent();
    private Packet[] out_buf = new Packet[NR_BUFS];
    private SWE swe = null;
    private String sid = null;

    // protocol related variables
    private boolean no_nak = true;
    // timer related variables and constants
    private Timer[] timers = new Timer[NR_BUFS];
    private Timer ack_timer = new Timer();
    private static final int TIMEOUT_INTERVAL = 500;
    private static final int ACK_TIMEOUT_INTERVAL = 300;

    public SWP(SWE var1, String var2) {
        this.swe = var1;
        this.sid = var2;
    }

    private void enable_network_layer(int var1) {
        this.swe.grant_credit(var1);
    }

    private void from_network_layer(Packet var1) {
        this.swe.from_network_layer(var1);
    }

    private void from_physical_layer(PFrame var1) {
        PFrame var2 = this.swe.from_physical_layer();
        var1.kind = var2.kind;
        var1.seq = var2.seq;
        var1.ack = var2.ack;
        var1.info = var2.info;
    }

    private void init() {
        for(int var1 = 0; var1 < 4; ++var1) {
            this.out_buf[var1] = new Packet();
        }

    }


    public void protocol6() {
        this.init();

        while(true) {
            while(true) {
                this.wait_for_event(this.event);
                switch(this.event.type) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                        break;
                    default:
                        System.out.println("SWP: undefined event type = " + this.event.type);
                        System.out.flush();
                }
            }
        }
    }

    private void start_ack_timer() {
        stop_ack_timer();
        ack_timer = new Timer();
        ack_timer.schedule(new TimerTask() {
            @Override
            public void run() {
                swe.generate_acktimeout_event();
            }
        }, ACK_TIMEOUT_INTERVAL);
    }

    private void start_timer(int var1) {
        stop_timer(var1);
        timers[var1 % NR_BUFS] = new Timer();
        timers[var1 % NR_BUFS].schedule(new ProtocolTimerTask(var1), TIMEOUT_INTERVAL);
    }

    private void stop_ack_timer() {
        try {
            ack_timer.cancel();
        } catch (Exception e) {
            System.out.println("Failed to stop the acknowledge timer");
        }
    }

    private void stop_timer(int var1) {
        try {
            timers[var1 % NR_BUFS].cancel();
        } catch (Exception e) {
            System.out.println("Failed to stop timer");
        }
    }

    private void to_network_layer(Packet var1) {
        this.swe.to_network_layer(var1);
    }

    private void to_physical_layer(PFrame var1) {
        System.out.println("SWP: Sending frame: seq = " + var1.seq + " ack = " + var1.ack + " kind = " + PFrame.KIND[var1.kind] + " info = " + var1.info.data);
        System.out.flush();
        this.swe.to_physical_layer(var1);
    }

    private void wait_for_event(PEvent var1) {
        this.swe.wait_for_event(var1);
        this.oldest_frame = var1.seq;
    }

    // Helper class for implementing timer
    private class ProtocolTimerTask extends TimerTask {
        public int seq; // add an attribute seq to record the sequence number

        public ProtocolTimerTask(int seq) {
            super();
            this.seq = seq;
        }

        // generate a timeout event of the recorded sequence number when the task is executed
        @Override
        public void run() {
            swe.generate_timeout_event(this.seq);
        }
    }
}
