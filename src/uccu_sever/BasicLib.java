/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.Scanner;

/**
 *
 * @author xiaoshuang
 */

class Const
{
    static String[] gateAddress;
    static int[] gatePort;
    static String gameServerAddress;
    static int gameServerPort;
    static String DBAddress = "162.105.37.13";
    static int DBPort = 8898;
    static String LoginAddress = "162.105.37.13";
    static int LoginPort = 8798;
    
    
    
    static String blank = "           ";
    static long MIN_CHAT_INTERVAL = 1000;//最小聊天间隔
    
}


class UCCUTimer
{
    private long startTime;
    private long initTime;
    public UCCUTimer()
    {
        startTime = 0;
        initTime = 0;
    }
    public void reset(long start)//单位NanoSec
    {
        startTime = start;
        initTime = System.nanoTime();
    }
    public long getMS()
    {
        return (System.nanoTime() - initTime + startTime)/1000_000L;
    }
    public long getS()
    {
        return getMS()/1000L;
    }
    public long getMin()
    {
        return getMS()/60_000L;
    }
    public long getHour()
    {
        return getMS()/3_600_000L;
    }
    public String getString()
    {
        long total = getMS();
        total /= 1000L;
        long sec = total % 60;
        long min = (total/60) % 60;
        long hour = (total/3600) % 24;
        long day = (total/86400) % 365;
        long year = (total/31_536_000L);
        
        StringBuilder sb = new StringBuilder();
        sb.append(year).append("years, ").append(day).append("days, ");
        sb.append(hour).append("hours, ").append(min).append("minutes, ");
        sb.append(sec).append("seconds.");
        return sb.toString();
    }
    public String getTimestamp()
    {
        long total = getMS();
        total /= 1000L;
        long sec = total % 60;
        long min = (total/60) % 60;
        long hour = (total/3600) % 24;
        long day = (total/86400) % 365;
        long year = (total/31_536_000L);
        
        return String.format("[%02d:%02d:%02d:%02d:%02d]", year, day, hour, min, sec);
    }
}



class Shell
{
    Scanner scr;
    public Shell()
    {
        scr = new Scanner(System.in);
    }
    public void startShell()
    {
        //System.out.println("Start Server Shell!");
        UccuLogger.log("Shell/Start", "Shell mode is on!");
        while(scr.hasNextLine())
        {
            String str = scr.nextLine();
        }
    }
    public String getLine()
    {
        scr.hasNextLine();
        return scr.nextLine();
    }
}


class LogMode
{
    static int DEBUG = 0;//调试模式
    static int NORMAL = 1;//正常模式
    static int CHIEF = 2;//简要模式
    static int NONE = 3;//无日志
}

class UccuLogger
{
    static Integer mode = LogMode.NORMAL;
    static String dir = "logs/";
    static FileChannel logfile;
    static String filename = "[date].log";
    static int fileno = 1;
    
    private String name;
    
    
    
    private UccuLogger(String n)
    {
        name = n;
    }
    public static void setOptions(String directory, int m)
    {
        synchronized(mode)
        {
            mode = m;
        }
        synchronized(dir)
        {
            dir = directory;
            File d = new File(dir);
            if(!d.exists())
                d.mkdirs();
        }
        checkFile();
    }

    public static void setMode(Integer m) {
        synchronized(mode)
        {
            mode = m;
        }
    }
    
    public static void debug(String name, String str)
    {
        getLogger(name).debug(str);
    }
    //推荐使用，可以直接得到一个临时对象，方便使用
    public static void log(String name, String str)
    {
        getLogger(name).log(str);
    }
    //推荐使用，可以直接得到一个临时对象，方便使用
    public static void warn(String name, String str)
    {
        getLogger(name).warn(str);
    }
    
    public static void kernel(String name, String str)
    {
        getLogger(name).kernel(str);
    }
    
    public static void note(String name, String str)
    {
        getLogger(name).note(str);
    }
    
    public void debug(String str)
    {
        this.log0("DEBUG", str, LogMode.DEBUG);
    }
    
    public void log(String str)
    {
        this.log0("INFO", str, LogMode.NORMAL);
    }
    
    public void warn(String str)
    {
        this.log0("WARNING", str, LogMode.CHIEF);
    }
    
    public void kernel(String str)
    {
        this.log0("KERNEL", str, LogMode.CHIEF);
    }
    
    public void note(String str)
    {
        this.log0("NOTE", str, LogMode.NONE);
    }
    
    public void log0(String type, String str, int m)
    {
        checkFile();
        
        if(!isEnable(m))
            return;
        
        str = this.decorate(type, str);
        synchronized(System.out)
        {
            System.out.println(str);
        }
        if(m == LogMode.NONE)
            return;
        try {
            byte[] array = str.getBytes("GBK");
            ByteBuffer tmp = ByteBuffer.allocate(array.length+2);
            tmp.put(array);
            tmp.put((byte)'\r');
            tmp.put((byte)'\n');
            tmp.flip();
            synchronized(filename)
            {
                logfile.write(tmp);
                logfile.force(true);
            }
            //logfile.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
        
    public String decorate(String type, String str)
    {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int min = c.get(Calendar.MINUTE);
        int sec = c.get(Calendar.SECOND);
        return String.format("[%02d:%02d:%02d] [%s/%s]: %s", hour, min, sec, name, type, str);
    }
    
    
    public static boolean isEnable(int m)
    {
        synchronized(mode)
        {
            return m >= mode;
        }
    }
    
    public static UccuLogger getLogger(String name)
    {
        File d = new File(dir);
        if(!d.exists())
            d.mkdirs();
        
        checkFile();
        return new UccuLogger(name);
    }
    public static String getDate()
    {
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DATE);
        return String.format("%d-%02d-%02d", year, month+1, day);
    }
    
    public static String getFilename()
    {
        String date = getDate();
        fileno = 1;
        File f = new File(dir + date+"-"+fileno+".log");
        try {
            while(!f.createNewFile())
            {
                ++fileno;
                f = new File(dir + date+"-"+fileno+".log");
            }
            return date+"-"+fileno+".log";
            
        } catch (Exception e) {
            System.err.println("WARNING! Can't get new logfile's name!");
            return null;
        }
    }
    
    public static void checkFile()
    {
        synchronized(filename)
        {
            if(filename.equals(getDate()+"-"+fileno+".log"))
                return;

            filename = getFilename();
            Path path = null;
            synchronized(dir)
            {
                path = Paths.get(dir+filename);
            }
            try {
                if(logfile != null && logfile.isOpen())
                    logfile.close();

                logfile = FileChannel.open(path, EnumSet.of(StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,StandardOpenOption.WRITE));

            } catch (IOException ex) {
                System.err.println("Can't open logfile: "+ filename);
                synchronized(mode)
                {
                    mode = LogMode.NONE;
                }
            }
        }
    }
    
}


public class BasicLib {
    public static String md5(String str) {
        StringBuffer sb = new StringBuffer(32);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] tmp = md.digest(str.getBytes("GBK"));
            for(int i = 0; i < tmp.length; ++i)
            {
                sb.append(Integer.toHexString((tmp[i]&0xFF)|0x100).toUpperCase().substring(1, 3));
            }
        } catch (Exception e) {
            //System.out.println("Can't get MD5.");
            UccuLogger.warn("BasicLib/MD5", "Can't calculate the MD5! "+e);
            //e.printStackTrace();
            return null;
        }
        return sb.toString();
    }
}