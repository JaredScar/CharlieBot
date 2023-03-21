package com.jaredscarito.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Logger {
    private static String getCurrentDatetimeString() {
        Date date = new Date();

        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        formatter.setTimeZone(TimeZone.getTimeZone("EST"));

        return (formatter.format(date));
    }
    public static void log(String action, String performedBy, String performedOn, String reasoning) {}
    public static void log(Exception ex) {
        File errors = new File("logs/error.txt");
        if (!errors.exists()) {
            try {
                errors.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (errors.exists()) {
            try {
                FileWriter writer = new FileWriter(errors, true);
                writer.write("[" + getCurrentDatetimeString() + "] Error Encountered:");
                writer.write(ex.getMessage());
                writer.write("\n");
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
