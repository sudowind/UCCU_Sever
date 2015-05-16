/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import com.sun.corba.se.impl.encoding.CodeSetConversion;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.TreeSet;
import oracle.jrockit.jfr.events.ContentTypeImpl;
import org.w3c.dom.ls.LSException;

/**
 *
 * @author Wind
 */

class playerInfo{
    private int id;
    private String name;
    private byte level;
    private byte gender;
    private int posX;
    private int posY;

    public playerInfo(int i, String nm, byte lv, byte gd, int x, int y) {
        id = i;
        name = nm;
        level = lv;
        gender = gd;
        posX = x;
        posY = y;
    }
    
    public void changePos(int x, int y){
        posX = x;
        posY = y;
    }
}

public class GateServer implements Register, Decoder{
    private AioModule localAio;
    private AioSession gameSession;
    private AioSession loginSession;
    //in the following two lines, Integer represents sessionID
    private HashMap<Integer, AioSession> sessions = new HashMap<Integer, AioSession>();
    private HashMap<Integer, playerInfo> players = new HashMap<Integer, playerInfo>();
    private int clientNum = 0;
    private ByteBuffer tmpBuffer;
    static char head = 0xffff;
    static byte toClient = 0x00;
    static byte toGS = 0x01;
    static byte toLS = 0x02;
    private String lIp;
    private int lPort;
    private int maxChar;
    public GateServer(){
        
    }
    
    public void init(String gip, int gport, String lip, int lport, AioModule aio){
        localAio = aio;
        lIp = lip;
        lPort = lport;
        gameSession = aio.connect(gip, gport, new GameDecoder());
        ByteBuffer msg = ByteBuffer.allocate(32);
        msg.putInt(12345);
        msg.flip();
        gameSession.write(Datagram.wrap(msg, Target.GS, 0x00));
    }
    
    public boolean register(AioSession session, AioModule aio){////////////////////////////////////////////////////
        sessions.put(clientNum, session);
        session.setAttachment(clientNum);
        clientNum++;
        aio.addSession(session);
        return true;
    }
    
    public static char getChecksum(byte[] content, int length){
        char sum = 0;
        char seed = 49877; //Prime number
        for(int i = 0; i < length; ++i){
            sum = (char)((sum * (i + 1) + content[i]) % seed);
        }
        return sum;
    }
    
    public static ByteBuffer wrap(ByteBuffer msg, Target tar, int sn)
    {
        int len = 8+msg.remaining();
        ByteBuffer res = ByteBuffer.allocate(len+8);
        
        byte SN = (byte)sn;
        
        res.putChar(head);//head
        res.putInt(len);//length
        if(tar == Target.GS)//
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
    
    //client and GateServer
    public void decode(ByteBuffer buffer, AioSession session){
        int id = (int)session.getAttachment();
        
        char form;
        ByteBuffer tmp = Datagram.getDatagram(buffer);
        ByteBuffer nbuf = ByteBuffer.allocate(64);
        if(tmp == null)
            return;
        form = Datagram.trim(tmp);
        switch(form){
            case 0x0000://first attach
                int hello = 12345;
                nbuf.put((byte)hello);
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.GS, 0x00));//0x0100
                break;
            case 0x0002://login info
                nbuf.put((byte)session.getAttachment());
                nbuf.put(tmp);
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.LS, 0x02));//0x0202
                break;
            case 0x0004://register new player
                nbuf.put((byte)session.getAttachment());
                nbuf.put(tmp);
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.LS, 0x04));//0x0204
                break;
            case 0x0007://res of character chosen
                nbuf.put((byte)session.getAttachment());
                nbuf.put(tmp);
                nbuf.flip();
                ByteBuffer tbuf = nbuf.duplicate();
                session.write(Datagram.wrap(nbuf, Target.LS, 0x07));//0x0207
                session.write(Datagram.wrap(tbuf, Target.GS, 0x09));//0x0109
                break;
            case 0x0008:
                nbuf.put((byte)session.getAttachment());
                nbuf.put(tmp);
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.LS, 0x08));//0x0208
                break;
            case 0x000b:
                nbuf.put((byte)session.getAttachment());
                nbuf.put(tmp);
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.GS, 0x0b));//0x010b
                break;
            case 0x000d:
                break;
            case 0x0010:
                break;
        }
    }
    
    //GameServer and GateServer
    private class GameDecoder implements Decoder{
        public void decode(ByteBuffer buffer, AioSession session){
            char form;
            ByteBuffer tmp = Datagram.getDatagram(buffer);
            ByteBuffer nbuf = ByteBuffer.allocate(64);
            if(tmp == null)
                return;
            form = Datagram.trim(tmp);
            switch(form){
                case 0x0101://connect LoginServer
                    loginSession = localAio.connect(lIp, lPort, new LoginDecoder());
                    maxChar = tmp.getInt(2);
                    tmp.limit(2);
                    loginSession.write(wrap(tmp, Target.LS, 0x00));//0x0200
                    break;
                case 0x010a:
                    int sessionID = tmp.getInt();
                    int id = tmp.getInt();
                    
                    String name;
                    break;
                case 0x010c:
                    
                    break;
                case 0x010e:
                    break;
                case 0x010f:
                    break;
                case 0x0111:
                    break;
                case 0x0112:
                    break;
            }
        }
    }
    
    //GateServer and LoginServer
    private class LoginDecoder implements Decoder{
        public void decode(ByteBuffer buffer, AioSession session){
            int sessionID;
            char form;
            ByteBuffer tmp = Datagram.getDatagram(buffer);
            ByteBuffer nbuf = ByteBuffer.allocate(64);
            if(tmp == null)
                return;
            form = Datagram.trim(tmp);
            switch(form){
                case 0x0201://Successfully Login
                    break;
                case 0x0203://return login info to Client
                    sessionID = tmp.getInt();
                    tmp.compact();
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x03));//0x0003
                    break;
                case 0x0205://return register info to client
                    sessionID = tmp.getInt();
                    tmp.compact();
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x05));//0x0005
                    break;
                case 0x0206:
                    sessionID = tmp.getInt();
                    tmp.compact();
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x06));//0x0006
                    break;
                case 0x0209:
                    sessionID = tmp.getInt();
                    tmp.compact();
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x09));//0x0009
                    break;
            }
        }
    }
}
