/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import java.nio.ByteBuffer;
import org.w3c.dom.ls.LSException;

/**
 *
 * @author Xiaoshuang
 */
enum Target
{
    GS, 
    LS,
    CT
}

public class Datagram
{
    static char head = 0xFFFF;
    static byte toGS = 0x01;
    static byte toLS = 0x03;
    static byte toClient = 0x00;
    public static ByteBuffer wrap(ByteBuffer msg, Target tar, int sn)
    {
        int len = 10 + msg.remaining();
        ByteBuffer res = ByteBuffer.allocate(len+10);
        
        byte SN = (byte)sn;
        
        res.putChar(head);
        res.putInt(len);
        if(tar == Target.GS)
            res.put(toGS);
        else if(tar == Target.LS)
            res.put(toLS);
        else if(tar == Target.CT)
            res.put(toClient);
        res.put(SN);
        res.put(msg);
        char checksum = getChecksum(res.array(),res.position());
        res.putChar(checksum);
        
        res.flip();
        return res;
    }
    
    public static ByteBuffer getDatagram(ByteBuffer buffer)
    {
        if(buffer.remaining()<8){//包头+校验码不足
            buffer.compact();
            return null;
        }
        
        int len = buffer.getInt(2);
        if(buffer.remaining()<len){//包长不足
            buffer.compact();
            return null;
        }
            
        ByteBuffer tmp = ByteBuffer.allocate(len);
        while(tmp.hasRemaining())
            tmp.put(buffer.get());
        
        buffer.compact();
        tmp.flip();
        if(checked(tmp))
            return tmp;
        return null;
    }
    public static char trim(ByteBuffer datagram)
    {
        char res = datagram.getChar(6);
        ByteBuffer tmp = ByteBuffer.allocate(datagram.remaining() - 10);
        datagram.position(8);
        while(tmp.hasRemaining())
            tmp.put(datagram.get());
        
        tmp.flip();
        datagram = tmp;
        return res;
    }
    
    public static boolean checked(ByteBuffer dg)
    {
        byte[] tmp = dg.array();
        char newchecksum = getChecksum(tmp, tmp.length-2);
        char checksum = dg.getChar(dg.limit()-2);
        return newchecksum == checksum;
    }
    public static char getChecksum(byte[] content, int length)
    {
        char sum = 0;
        char seed = 49877; //Prime number
        for(int i = 0; i < length; ++i)
        {
            sum = (char)((sum * (i+1) + content[i]) % seed);
        }
        return sum;
    }
}
