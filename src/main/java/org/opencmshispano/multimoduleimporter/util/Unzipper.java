package org.opencmshispano.multimoduleimporter.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.opencms.main.CmsSystemInfo;
import org.opencms.main.OpenCms;

/**
 * Utility class to unzip files.
 * 
 */
public final class Unzipper {

    public static void unzip(final String filepath) {
        Enumeration entries;
        ZipFile zipFile;

        try {
            zipFile = new ZipFile(filepath);

            entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();

                if (entry.isDirectory()) {
                    // Assume directories are stored parents first then children.
                    // This is not robust, just for demonstration purposes.
                    (new File(entry.getName())).mkdir();
                    continue;
                }

                copyInputStream(zipFile.getInputStream(entry), new BufferedOutputStream(new FileOutputStream(OpenCms.getSystemInfo().getPackagesRfsPath()
                        + File.separator + CmsSystemInfo.FOLDER_MODULES + File.separator + entry.getName())));
            }

            zipFile.close();
        } catch (IOException ioe) {
            System.err.println("Unhandled exception:");
            ioe.printStackTrace();
            return;
        }
    }

    public static void copyInputStream(final InputStream in, final OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }

        in.close();
        out.close();
    }

    public static Enumeration getZipEntries(final String filepath) {
        try {
            return (new ZipFile(filepath)).entries();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Unzipper() {
    }

}
