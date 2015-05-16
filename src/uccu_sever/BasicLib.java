/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

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
    static String DBAddress = "162.105.37.202";
    static int DBPort = 8898;
    static String LoginAddress = "162.105.37.202";
    static int LoginPort = 8798;
    
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
        System.out.println("Start Server Shell!");
        while(scr.hasNextLine())
        {
            String str = scr.nextLine();
        }
    }
}

public class BasicLib {
    
}
