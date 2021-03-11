package Util;

import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class DataHandler {

    public static void writeToFile(String fileName, String content) throws IOException {
        FileWriter fw = new FileWriter(fileName, true);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(content);
        bw.newLine();
        bw.close();
    }

    public static void DeleteFile(String file) throws IOException {
        File fileToDelete = new File(file);
        if (fileToDelete.exists()) {
            FileUtils.forceDelete(fileToDelete);
        }
    }
}