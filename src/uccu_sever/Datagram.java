

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 *
 * @author xiaoshuang
 */
enum Target
{
    GS, 
    LS,
    CT
}

class Datagram
{
    static char head = 0xFFFF;
    static byte toGS = 0x01;
    static byte toLS = 0x02;
    static byte toClient = 0x00;
    static Charset charset = Charset.forName("GBK");
    
    public static ByteBuffer wrap(ByteBuffer msg, Target tar, int sn)
    {
        if(msg.position() != 0)
            msg.flip();
        
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
        if(buffer.remaining()<8)//包头+校验码不足
        {
            return null;
        }
            
        
        int len = buffer.getInt(2);
        if(buffer.remaining()<len)//包长不足
        {
            return null;
        }
        
        ByteBuffer tmp = ByteBuffer.allocate(len);
        while(tmp.hasRemaining())
            tmp.put(buffer.get());
        
        tmp.flip();
        if(checked(tmp))
            return tmp;
        return null;
    }
    public static char trim(ByteBuffer datagram)
    {
        char res = datagram.getChar(6);
        datagram.limit(datagram.remaining()-2);
        datagram.position(8);
        datagram.compact();
        datagram.flip();
        return res;
    }
    
    //调用这个函数提取字符串不会改变buffer中任何值，你无法直接知道这个字符串结束的位置，找不到返回null
    public static String extractString(ByteBuffer datagram, int index)
    {
        ByteBuffer btmp = ByteBuffer.allocate(datagram.remaining()) ;
        while(index < datagram.limit())
        {
            byte tmp = datagram.get(index);
            if(tmp == '\0')
            {
                break;
            }
            btmp.put(tmp);
            index++;
            if(index == datagram.limit())
                return null;
        }
        btmp.flip();
        
        CharBuffer charbuf = null;
        try {
            charbuf = charset.decode(btmp);
            return charbuf.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    //调用此函数会从buffer的当前位置开始找一个字符串，并更新当前位置为字符串后面第一个位置，找不到则返回null
    public static String extractString(ByteBuffer datagram)
    {
        ByteBuffer btmp = ByteBuffer.allocate(datagram.remaining()) ;
        int index = datagram.position();
        while(index < datagram.limit())
        {
            byte tmp = datagram.get(index);
            if(tmp == '\0')
            {
                datagram.position(index + 1);
                break;
            }
            btmp.put(tmp);
            index++;
            if(index == datagram.limit())
                return null;
        }
        btmp.flip();
        
        CharBuffer charbuf = null;
        try {
            charbuf = charset.decode(btmp);
            return charbuf.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static void restoreString(ByteBuffer msg, String str)//确保空间足够，否则不会写入buffer
    {
        str = str + '\0';
        if(msg.remaining()<str.length())
            return;
        
        msg.put(charset.encode(str));
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

