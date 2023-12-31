package marcel.hardware;

public class Machine implements Runnable {

    private volatile Thread runner;
    // Emulation Objects
    private final Memory mem;
    private final M6809 micro;
    private final Screen screen;
    private final Keyboard keyboard;

    // application mode constructor
    public Machine(Screen screen) {
        mem = new Memory();
        Sound sound = new Sound();
        this.screen = screen;
        micro = new M6809(mem, sound);
        keyboard = new Keyboard(screen, mem);
        this.screen.init(mem);
    }

    public void start() {
        if (runner == null) {
            runner = new Thread(this);
            runner.setPriority(Thread.MAX_PRIORITY);
            runner.start();
        }
    }

    public void stop() {
        if (runner != null) {
            runner = null;
        }
    }

    @Override
    public void run() {
        Thread thisThread = Thread.currentThread();
        while (runner == thisThread) {
            fullSpeed();
            synchronize();
        }
    }

    private boolean IRQ;

    // the emulator main loop
    private void fullSpeed() {
        int cl;

        screen.repaint(); // Mise a jour de l'affichage

        // Mise a jour du crayon optique a partir des donnée de la souris souris
        mem.LightPenClic = screen.isMouse_clic();
        mem.LightPenX = screen.getMouse_X();
        mem.LightPenY = screen.getMouse_Y();

        mem.set(0xA7E7, 0x00);
        mem.GA3 = 0x00;
        /* 3.9 ms haut �cran (+0.3 irq)*/
        if (IRQ) {
            IRQ = false;
            micro.FetchUntil(3800);
        } else {
            micro.FetchUntil(4100);
        }

        /* 13ms fenetre */
        mem.set(0xA7E7, 0x80);
        mem.GA3 = 0x80;
        micro.FetchUntil(13100);

        mem.set(0xA7E7, 0x00);
        mem.GA3 = 0x00;
        micro.FetchUntil(2800);

        if ((mem.CRB & 0x01) == 0x01) {
            IRQ = true;
            /* Positionne le bit 7 de CRB */
            mem.CRB |= 0x80;
            mem.set(0xA7C3, mem.CRB);
            int CC = micro.readCC();
            if ((CC & 0x10) == 0) {
                micro.IRQ();
            }
            /* 300 cycles sous interrupt */
            micro.FetchUntil(300);
            mem.CRB &= 0x7F;
            mem.set(0xA7C3, mem.CRB);
        }
    }

    private long lastTime = System.currentTimeMillis();
    private int[] keys;

    private void AutoType(String input) {
        input = input.replace("\"", "zxz");
        keys = new int[input.length()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = (int) input.charAt(i);
            System.out.println(keys[i]);
        }
        keytimer = 1;
    }

    private int keytimer;
    private int keypos;
    private String typetext;

    public void setBoot(String input) {
        if (input != null) {
            testtimer = 1;
            input = input.replace("m", "\r\n");
        }
        typetext = input;
    }

    private static int testtimer;

    public static void setTesttimer(int testtimer) {
        Machine.testtimer = testtimer;
    }

    private void synchronize() {
        if (testtimer != 0 && typetext != null) {
            testtimer++;
            if (testtimer == 100) {
                AutoType(typetext);
                testtimer = 0;
            }
        }
        if (keytimer != 0) {
            keytimer++;
            if (keytimer == 2) {
                keyboard.press(keys[keypos]);
            }
            if (keytimer == 3) {
                keyboard.release(keys[keypos++]);
                keytimer = 1;
                if (keypos >= keys.length) {
                    keypos = 0;
                    keytimer = 0;
                    keys = null;
                }
            }
        }
        int realTimeMillis = (int) (System.currentTimeMillis() - lastTime);

        int sleepMillis = 20 - realTimeMillis - 1;
        if (sleepMillis < 0) {
            lastTime = System.currentTimeMillis();
            return;
        }
        try {
            Thread.sleep(sleepMillis);
        } catch (Exception e) {
            System.out.println(e);
        }
        lastTime = System.currentTimeMillis();
    }

    public boolean setK7FileFromUrl(String K7) {
        return mem.setK7FileFromUrl(K7);
    }

    public boolean setK7File(String K7) {
        return mem.setK7File(K7);
    }

    // soft reset method ("reinit prog" button on original MO5) 
    public void resetSoft() {
        micro.reset();
    }

    // hard reset (switch off and on)
    public void resetHard() {
        for (int i = 0x2000; i < 0x3000; i++) {
            mem.set(i, 0);
        }
        micro.reset();
    }

    // Debug Methods
    public String dumpRegisters() {
        return micro.printState();
    }

    public String unassembleFromPC(int nblines) {
        return micro.unassemble(micro.getPC(), nblines);
    }

    public String dumpSystemStack(int nblines) {
        return "00";
    }
} // of class

