public class VMach {
    public VMach() {
    }

    public static void main(String[] var0) {
        String var1 = "";
        boolean var2 = true;

        try {
            var1 = var0[0];
        } catch (Exception var5) {
            System.err.println("\nERROR: VMach site-id required!");
            System.err.println("\nUsage: java NetSim [site-id] where site-id = 1 or 2");
            System.exit(0);
        }

        int var6 = Integer.parseInt(var1);
        if (var6 != 1 && var6 != 2) {
            System.err.println("\nERROR: Invalid site-id: " + var1);
            System.err.println("\nUsage: java NetSim [site-id] where site-id = 1 or 2");
            System.exit(0);
        }

        SWE var3 = new SWE(var1);
        var3.init();
        SWP var4 = new SWP(var3, var1);
        var4.protocol6();
    }
}

