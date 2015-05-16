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
import java.util.Map;
import java.util.TreeSet;
import oracle.jrockit.jfr.events.ContentTypeImpl;
import org.w3c.dom.ls.LSException;

/**
 *
 * @author Wind
 */

class PlayerInfo{
    private int id;
    private int sessionID;
    private String name;
    private byte level;
    private byte gender;
    private int posX;
    private int posY;

    public PlayerInfo(int i, String nm, byte lv, byte gd, int x, int y, int session) {
        id = i;
        name = nm;
        level = lv;
        gender = gd;
        posX = x;
        posY = y;
        sessionID = session;
    }
    
    public void changePos(int x, int y){
        posX = x;
        posY = y;
    }
    
    public int getPlayerID(){
        return id;
    }
    
    public int getSession(){
        return sessionID;
    }
    
    public ByteBuffer pack(){
        ByteBuffer tmp = ByteBuffer.allocate(128);
        tmp.putInt(id);
        Datagram.restoreString(tmp, name);
        tmp.put(level);
        tmp.put(gender);
        tmp.putInt(posX);
        tmp.putInt(posY);
        tmp.flip();
        return tmp;
    }
}

public class GateServer implements Register, Decoder{
    private AioModule localAio;
    private AioSession gameSession;
    private AioSession loginSession;
    //in the following two lines, Integer represents sessionID
    private HashMap<Integer, AioSession> sessions = new HashMap<Integer, AioSession>();
    private HashMap<Integer, PlayerInfo> players = new HashMap<Integer, PlayerInfo>();
    private int clientNum = 0;
    private ByteBuffer tmpBuffer;
    static char head = 0xffff;
    static byte toClient = 0x00;
    static byte toGS = 0x01;
    static byte toLS = 0x02;
    private String lIp;
    private int lPort;
    private int maxChar;
    private Integer currChar = 0;
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
                synchronized(currChar){
                    nbuf.put((byte)session.getAttachment());
                    nbuf.put(tmp);
                    nbuf.flip();
                    ByteBuffer tbuf = ByteBuffer.allocate(64);
                    if(currChar < maxChar){
                        tbuf = nbuf.duplicate();
                        currChar++;
                        session.write(Datagram.wrap(tbuf, Target.GS, 0x09));//0x0109
                    }
                    else{//增加一个包，告诉client人数已达到上限0x0013
                        session.write(Datagram.wrap(tbuf, Target.CT, 0x13));//0x0013
                    }
                    session.write(Datagram.wrap(nbuf, Target.LS, 0x07));//0x0207
                }
                break;
            case 0x0008:
                nbuf.put((byte)session.getAttachment());
                nbuf.put(tmp);
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.LS, 0x08));//0x0208
                break;
            case 0x000b://try to move
                nbuf.put((byte)session.getAttachment());
                nbuf.put(tmp);
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.GS, 0x0b));//0x010b
                break;
            case 0x000d://Globle message
                nbuf.put((byte)session.getAttachment());
                nbuf.put(tmp);
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.GS, 0x0d));//0x010d
                break;
            case 0x0010:
                nbuf.put((byte)session.getAttachment());
                nbuf.put(tmp);
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.GS, 0x10));//0x0110
                break;
        }
    }
    
    //GameServer and GateServer
    private class GameDecoder implements Decoder{
        public void decode(ByteBuffer buffer, AioSession session){
            int sessionID;
            char form;
            int id;
            String name;
            byte level;
            byte gender;
            int posX;
            int posY;
            String msg;
            PlayerInfo player;
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
                    loginSession.write(Datagram.wrap(tmp, Target.LS, 0x00));//0x0200
                    break;
                case 0x010a:
                    sessionID = tmp.getInt();
                    tmp.compact();
                    id = tmp.getInt();
                    name = Datagram.extractString(tmp);
                    level = tmp.get();
                    gender = tmp.get();
                    posX = tmp.getInt();
                    posY = tmp.getInt();
                    tmp.rewind();
                    synchronized(players){
                        players.put(sessionID, new PlayerInfo(id, name, level, 
                                gender, posX, posY, sessionID));
                        for(Map.Entry<Integer, PlayerInfo>  entry: players.entrySet()){////////////////////chu le wen ti xian kan zhe li
                            player = entry.getValue();
                            sessions.get(player.getSession()).write(Datagram.wrap(tmp, Target.CT, 0x0a));//0x000a
                            sessions.get(sessionID).write(Datagram.wrap(player.pack(), Target.CT, 0x0a));//0x000a
                        }
                    }
                    break;
                case 0x010c://allow this movement
                    sessionID = tmp.getInt();
                    tmp.compact();
                    id = tmp.getInt();
                    posX = tmp.getInt();
                    posY = tmp.getInt();
                    nbuf.putInt(id);
                    nbuf.putInt(posX);
                    nbuf.putInt(posY);
                    synchronized(players){
                        players.get(sessionID).changePos(posX, posY);
                        for(Map.Entry<Integer, PlayerInfo> entry: players.entrySet()){
                            sessions.get(entry.getValue().getSession()).write(
                                    Datagram.wrap(nbuf, Target.CT, 0x0c));//0x000c
                        }
                    }
                    break;
                case 0x010e://Globel msg rejected
                    sessionID = tmp.getInt();
                    tmp.compact();
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x0e));//0x000e
                    break;
                case 0x010f://send globel msg to every player
                    sessionID = tmp.getInt();
                    tmp.compact();
                    synchronized(players){
                        for(Map.Entry<Integer, PlayerInfo> entry: players.entrySet()){
                            sessions.get(entry.getValue().getSession()).write(
                                    Datagram.wrap(tmp, Target.CT, 0x0f));//0x000f
                        }
                    }
                    break;
                case 0x0111://Private chat rejected
                    sessionID = tmp.getInt();
                    tmp.compact();
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x11));//0x0011
                    break;
                case 0x0112://Private chat
                    sessionID = tmp.getInt();
                    tmp.compact();
                    id = tmp.getInt(4);
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x11));//0x0011
                    synchronized(players){
                        for(Map.Entry<Integer, PlayerInfo> entry: players.entrySet()){
                            player = entry.getValue();
                            if(player.getPlayerID() == id){
                                sessions.get(player.getSession()).write(
                                        Datagram.wrap(tmp, Target.CT, 0x0f));//0x000f
                                break;
                            }
                        }
                    }
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
