import java.util.Timer;
import java.util.TimerTask;


public class SWP {
    /*========================================================================*
         the following are provided, do not change them!!
         *========================================================================*/
    //the following are protocol constants.
    public static final int MAX_SEQ = 7;
    public static final int NR_BUFS = (MAX_SEQ + 1)/2;

    // the following are protocol variables
    private int oldest_frame = 0;
    private PEvent event = new PEvent();
    private Packet out_buf[] = new Packet[NR_BUFS];

    //the following are used for simulation purpose only
    private SWE swe = null;
    private String sid = null;

    //Constructor
    public SWP(SWE sw, String s){
        swe = sw;
        sid = s;
    }

    //the following methods are all protocol related
    private void init(){
        for (int i = 0; i < NR_BUFS; i++){
            out_buf[i] = new Packet();
        }
    }

    private void wait_for_event(PEvent e){
        swe.wait_for_event(e); //may be blocked
        oldest_frame = e.seq;  //set timeout frame seq
    }

    private void enable_network_layer(int nr_of_bufs) {
        //network layer is permitted to send if credit is available
        swe.grant_credit(nr_of_bufs);
    }

    private void from_network_layer(Packet p) {
        swe.from_network_layer(p);
    }

    private void to_network_layer(Packet packet) {
        swe.to_network_layer(packet);
    }

    private void to_physical_layer(PFrame fm)  {
        System.out.println("SWP: Sending frame: seq = " + fm.seq +
                " ack = " + fm.ack + " kind = " +
                PFrame.KIND[fm.kind] + " info = " + fm.info.data );
        System.out.flush();
        swe.to_physical_layer(fm);
    }

    private void from_physical_layer(PFrame fm) {
        PFrame fm1 = swe.from_physical_layer();
        fm.kind = fm1.kind;
        fm.seq = fm1.seq;
        fm.ack = fm1.ack;
        fm.info = fm1.info;
    }


    /*===========================================================================*
		 implement your Protocol Variables and Methods below:
	 *==========================================================================*/
    private boolean no_nak = true;
    private Packet in_buf[] = new Packet[NR_BUFS];
    private Timer[] timers = new Timer[NR_BUFS];
    private Timer ack_timer = new Timer();
    private static final int TIMEOUT_INTERVAL = 500;
    private static final int ACK_TIMEOUT_INTERVAL = 300;

    // This method increments an integer while making sure it falls within the range of the max sequence number
    public static int increment(int n) {
        return (n+1)%(MAX_SEQ+1);
    }

    static boolean between(int seq_nr_a, int seq_nr_b, int seq_nr_c)
    {
		/*
		Return true if a <=b < c circularly; false otherwise.
		*/
        if (((seq_nr_a <= seq_nr_b) && (seq_nr_b < seq_nr_c)) || ((seq_nr_c < seq_nr_a) && (seq_nr_a <= seq_nr_b)) || ((seq_nr_b < seq_nr_c) && (seq_nr_c < seq_nr_a)))
            return(true);
        else
            return(false);
    }

    private void send_frame(int frame_type, int frame_nr, int frame_expected_nr, Packet buffer[])
    {
        /* Scratch variable */
        PFrame s = new PFrame();
        s.kind = frame_type;
        if (frame_type == PFrame.DATA)
        {
            s.info = buffer[frame_nr % NR_BUFS];
        }
        s.seq = frame_nr;
        s.ack = (frame_expected_nr+ MAX_SEQ) %(MAX_SEQ+1);
        if(frame_type == PFrame.NAK ) {
            no_nak = false;
        }

        to_physical_layer(s);

        if( frame_type == PFrame.DATA)
        {
            start_timer(frame_nr);
        }

        stop_ack_timer();
    }



    public void protocol6() {
        init();

        // Parameter declaration
        int seq_nr_ack_expected; /* lower edge of sender's window */
        int seq_nr_next_frame_to_send; /* upper edge of sender's windo+1 */
        int seq_nr_frame_expected; /* lower edge of receiver's window */
        int seq_nr_too_far; /* upper edge of receier's window +1 */
        PFrame r = new PFrame(); /* scratch variable */
        boolean arrived[] = new boolean[NR_BUFS]; /* inbound bit map */
        int seq_nr_buffered; /* how many output buffers currently used */

        // Initialize Network Layer
        enable_network_layer(NR_BUFS);

        // Parameter value initialization
        seq_nr_ack_expected = 0; /* next ack expected on the inbound stream */
        seq_nr_next_frame_to_send =0; /* number of next outgoing frame */
        seq_nr_frame_expected =0;
        seq_nr_too_far = NR_BUFS;
        seq_nr_buffered = 0;

        for(int i =0;i<NR_BUFS;i++) {
            arrived[i] = false;
        }

        while(true) {
            wait_for_event(event);
            switch(event.type) {
                case (PEvent.NETWORK_LAYER_READY): /* accept, save, and transmit a new frame */
                    // expand the window
                    seq_nr_buffered ++;

                    //fetch network package from network layer
                    from_network_layer(out_buf[seq_nr_next_frame_to_send % NR_BUFS]);

                    //transmit the frame
                    send_frame(PFrame.DATA, seq_nr_next_frame_to_send, seq_nr_frame_expected, out_buf);

                    seq_nr_next_frame_to_send = increment(seq_nr_next_frame_to_send);
                    break;
                case (PEvent.FRAME_ARRIVAL ): // A data or control frame has arrived
                    // fetch incoming frame from physical layer
                    from_physical_layer(r);
                    if(r.kind==PFrame.DATA) {

                        // An undamaged frame has arrived
                        if ((r.seq != seq_nr_frame_expected) && no_nak) {
                            send_frame(PFrame.NAK, 0, seq_nr_frame_expected, out_buf);
                        } else {
                            // Start ACK timer to retransmit ACK to sender when timer timeout
                            start_ack_timer();
                        }

                        // Check if frame received within sliding window range & whether it has been received before

                        if (between(seq_nr_frame_expected, r.seq, seq_nr_too_far) && arrived[r.seq % NR_BUFS] == false) {
                            //Frames may be accepted in any order
                            arrived[r.seq % NR_BUFS] = true; // mark buffer as full
                            in_buf[r.seq % NR_BUFS] = r.info; // insert data to buffer
                            while (arrived[seq_nr_frame_expected % NR_BUFS]) {
                                //Pass frames and advance window
                                to_network_layer(in_buf[seq_nr_frame_expected % NR_BUFS]);
                                no_nak = true;
                                arrived[seq_nr_frame_expected % NR_BUFS] = false;

                                // advance lower edge of receiver's window
                                seq_nr_frame_expected = increment(seq_nr_frame_expected);

                                // advance upper edge of receiver's window
                                seq_nr_too_far = increment(seq_nr_too_far);

                                // to see if a separate ack is needed
                                start_ack_timer();

                            }
                        }
                    }
                    if(r.kind==PFrame.NAK && between(seq_nr_ack_expected, increment(r.ack), seq_nr_next_frame_to_send))
                    {
                        // If NAK frame arrives, and it is between sliding window range
                        send_frame(PFrame.DATA, increment(r.ack), seq_nr_frame_expected, out_buf);
                        // Note: increment() does not actually increase the value! it essentially just return the value
                    }
                    while(between(seq_nr_ack_expected,r.ack,seq_nr_next_frame_to_send))
                    {
                        stop_timer(seq_nr_ack_expected % NR_BUFS);
                        seq_nr_ack_expected = increment(seq_nr_ack_expected); // advance lower edge of sender's window
                        // Free up 1 buffer slot(?)
                        enable_network_layer(1);
                    }
                    break;
                case (PEvent.CKSUM_ERR):  // Damaged frame arrived
                    if(no_nak)
                    {
                        // Send NAK if it is not sent yet
                        send_frame(PFrame.NAK,0,seq_nr_frame_expected, out_buf);
                    }
                    break;
                case (PEvent.TIMEOUT): // we timed out
                    send_frame(PFrame.DATA, oldest_frame, seq_nr_frame_expected, out_buf);
                    break;
                case (PEvent.ACK_TIMEOUT): // ack timer expired, send ack instead of wait to be piggybacked
                    send_frame(PFrame.ACK, 0, seq_nr_frame_expected, out_buf);
                    break;
                default:
                    System.out.println("SWP: undefined event type = "
                            + event.type);
                    System.out.flush();
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

    private void start_timer(int seq) {
        stop_timer(seq);
        timers[seq % NR_BUFS] = new Timer();
        timers[seq % NR_BUFS].schedule(new ProtocolTimerTask(seq), TIMEOUT_INTERVAL);
    }

    private void stop_ack_timer() {
        try {
            ack_timer.cancel();
        } catch (Exception e) {
        }
    }

    private void stop_timer(int seq) {
        try {
            timers[seq % NR_BUFS].cancel();
        } catch (Exception e) {
        }
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
//End of class

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/
