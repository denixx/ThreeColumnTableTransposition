package net.denixx.tctt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            GraphicInterface gui = new GraphicInterface();
            return;
        }

        String filename = args[0];
        if (filename == null || filename.trim().isEmpty()) {
            LOGGER.error("Give me a file or --gui !");
            return;
        }

        if ("--gui".equalsIgnoreCase(filename)) {
            GraphicInterface gui = new GraphicInterface();
            return;
        }

        File f = new File(filename);
        if (!f.exists()) {
            LOGGER.error("No file at {}!", f.getPath());
            return;
        }
        if (!f.isFile()) {
            LOGGER.error("This is not a file {}!", f.getPath());
            return;
        }
        if (!f.canRead()) {
            LOGGER.error("File at {} is not readable! (permissions?)", f.getPath());
            return;
        }

        try {
            Converter.transpondTable(f);
        } catch (Exception e) {
            LOGGER.error("Conversion error for file {}!", f.getPath(), e);
            return;
        }

        LOGGER.info("Done!");
    }
}
