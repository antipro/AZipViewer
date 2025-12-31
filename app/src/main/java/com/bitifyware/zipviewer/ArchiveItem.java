package com.bitifyware.zipviewer;

import java.io.File;
import java.util.Date;

/**
 * Model class representing an archive file
 */
public class ArchiveItem {
    private File file;
    private String name;
    private long size;
    private Date date;
    private int viewCount;
    private String password;

    public ArchiveItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.size = file.length();
        this.date = new Date(file.lastModified());
        this.viewCount = 0;
        this.password = null;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    public Date getDate() {
        return date;
    }

    public String getFormattedDate() {
        return android.text.format.DateFormat.format("yyyy/MM/dd", date).toString();
    }

    public int getViewCount() {
        return viewCount;
    }

    public void incrementViewCount() {
        viewCount++;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }
}
