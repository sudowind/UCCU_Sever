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
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;
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
    
    public void printInfo(){
        System.out.println("------------------------");
        System.out.print("id: " + id);
        System.out.print("sessionID: " + sessionID);
        System.out.print("name: " + name);
        System.out.print("gender: " + gender);
        System.out.print("posX: " + posX);
        System.out.print("posY: " + posY);
        System.out.println("------------------------");
    }
}

public class GateServer implements Register, Decoder, Reaper{
    private AioModule localAio;
    private AioSession gameSession;
    private AioSession loginSession;
    //in the following two lines, Integer represents sessionID
    private HashMap<Integer, AioSession> sessions = new HashMap<Integer, AioSession>();
    private HashMap<Integer, PlayerInfo> players = new HashMap<Integer, PlayerInfo>();
    //0--not login, 1--has connected
    private HashMap<Integer, Byte> stats = new HashMap<Integer, Byte>();
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
    private static Object lock0 = 0;
    private static Object lock1 = 1;
    public GateServer(){
        
    }
    
    public void init(String gip, int gport, String lip, int lport, AioModule aio){
        localAio = aio;
        lIp = lip;
        lPort = lport;
        gameSession = aio.connect(gip, gport, new GameDecoder(), new SampleReaper());
        ByteBuffer msg = ByteBuffer.allocate(32);
        msg.putInt(12345);
        msg.flip();
        synchronized(lock0){
            gameSession.write(Datagram.wrap(msg, Target.GS, 0x00));
            gameSession.asyncRead();
            try{
                lock0.wait();
                System.out.println("successfully connect to GameServer");
            }catch(Exception e){
            }
        }
    }
    
    public boolean register(AioSession session, AioModule aio){
        System.out.println("client connected!");
        synchronized(sessions){
            sessions.put(clientNum, session);
            stats.put(clientNum, (byte)0);
        }
        session.setAttachment(clientNum);
        clientNum++;
        aio.addSession(session);
        return true;
    }
    
    public void reap(AioSession session)
    {
        int sessionID;
        System.out.println("Client " + session.getRemoteSocketAddress() + " has disconnected!");
        sessionID = (int)session.getAttachment();
        synchronized(sessions){
            sessions.remove(sessionID);
        }
        synchronized(players){
            if(players.containsKey(sessionID)){
                players.remove(sessionID);
                currChar = players.size();
            }
        }
    }
    
    private class SampleReaper implements Reaper{
        public void reap(AioSession session){
            System.out.println("Session " + session.getRemoteSocketAddress() + " has disconnected!");
        }
    } 
    
    //client and GateServer
    public void decode(ByteBuffer buffer, AioSession session){
        int id = (int)session.getAttachment();
        
        char form;
        ByteBuffer tmp = Datagram.getDatagram(buffer);
        ByteBuffer nbuf = ByteBuffer.allocate(64);
        if(tmp == null){
            if(!buffer.hasRemaining()){
                synchronized(sessions){
                    sessions.remove(session.getAttachment());
                }
                synchronized(players){
                    if(players.containsKey(session.getAttachment())){
                        players.remove(session.getAttachment());
                    }
                }
                session.close();
                buffer.clear();
            }
            return;
        }
        form = Datagram.trim(tmp);
        switch(form){
            case 0x0000://first attach
                System.out.println("client " + session.getRemoteSocketAddress() + " has connected.");
                if(1.125 * currChar < maxChar){
                    nbuf.put((byte)1);
                }
                else{
                    nbuf.put((byte)2);
                }
                nbuf.flip();
                session.write(Datagram.wrap(nbuf, Target.CT, 0x01));//0x0001
                break;
            case 0x0002://login info
                System.out.println("client " + session.getRemoteSocketAddress() + " send name and pwd.");
                nbuf.putInt(id);
                nbuf.put(tmp);
                nbuf.flip();
                loginSession.write(Datagram.wrap(nbuf, Target.LS, 0x02));//0x0202
                break;
            case 0x0004://register new player
                System.out.println("client " + session.getRemoteSocketAddress() + " register a new name.");
                nbuf.putInt(id);
                nbuf.put(tmp);
                nbuf.flip();
                loginSession.write(Datagram.wrap(nbuf, Target.LS, 0x04));//0x0204
                break;
            case 0x0007://res of character chosen
                if(stats.get((int)session.getAttachment()) != 1){
                    synchronized(sessions){
                        sessions.remove(session.getAttachment());
                    }
                    session.close();
                    break;
                }
                System.out.println("client " + session.getRemoteSocketAddress() + " has chosen his character.");
                synchronized(currChar){
                    nbuf.putInt(id);
                    nbuf.put(tmp);
                    nbuf.flip();
                    ByteBuffer tbuf = ByteBuffer.allocate(64);
                    if(currChar < maxChar){
                        tbuf = nbuf.duplicate();
                        gameSession.write(Datagram.wrap(tbuf, Target.GS, 0x09));//0x0109
                    }
                    else{//增加一个包，告诉client人数已达到上限0x0013
                        loginSession.write(Datagram.wrap(tbuf, Target.CT, 0x13));//0x0013
                    }
                    loginSession.write(Datagram.wrap(nbuf, Target.LS, 0x07));//0x0207
                }
                break;
            case 0x0008:
                if(stats.get((int)session.getAttachment()) != 1){
                    synchronized(sessions){
                        sessions.remove(session.getAttachment());
                    }
                    session.close();
                    break;
                }
                System.out.println("client " + session.getRemoteSocketAddress() + " creat a new character.");
                nbuf.putInt(id);
                nbuf.put(tmp);
                nbuf.flip();
                loginSession.write(Datagram.wrap(nbuf, Target.LS, 0x08));//0x0208
                break;
            case 0x000b://try to move
                if(stats.get((int)session.getAttachment()) != 1){
                    synchronized(sessions){
                        sessions.remove(session.getAttachment());
                    }
                    session.close();
                    break;
                }
                System.out.println("client " + session.getRemoteSocketAddress() + " try to move.");
                nbuf.putInt(id);
                nbuf.put(tmp);
                nbuf.flip();
                gameSession.write(Datagram.wrap(nbuf, Target.GS, 0x0b));//0x010b
                break;
            case 0x000d://Globle message
                if(stats.get((int)session.getAttachment()) != 1){
                    synchronized(sessions){
                        sessions.remove(session.getAttachment());
                    }
                    session.close();
                    break;
                }
                System.out.println("client " + session.getRemoteSocketAddress() + " try to send globle msg.");
                nbuf.putInt(id);
                nbuf.put(tmp);
                nbuf.flip();
                gameSession.write(Datagram.wrap(nbuf, Target.GS, 0x0d));//0x010d
                break;
            case 0x0010:
                if(stats.get((int)session.getAttachment()) != 1){
                    synchronized(sessions){
                        sessions.remove(session.getAttachment());
                    }
                    session.close();
                    break;
                }
                System.out.println("client " + session.getRemoteSocketAddress() + " try to start a private chat.");
                nbuf.putInt(id);
                nbuf.put(tmp);
                nbuf.flip();
                gameSession.write(Datagram.wrap(nbuf, Target.GS, 0x10));//0x0110
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
                    loginSession = localAio.connect(lIp, lPort, new LoginDecoder(), new SampleReaper());
                    maxChar = tmp.getInt(2);
                    tmp.limit(2);
                    synchronized(lock1){
                        loginSession.write(Datagram.wrap(tmp, Target.LS, 0x00));//0x0200
                        loginSession.asyncRead();
                        try{
                            lock1.wait();
                            System.out.println("successfully connect to LoginServer");
                        }catch(Exception e){

                        }
                    }
                    localAio.asyncAccept();
                    System.out.println("aio response!");
                    synchronized(lock0){
                        lock0.notify();
                    }
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
                        for(Map.Entry<Integer, PlayerInfo>  entry: players.entrySet()){////////////////////chu le wen ti xian kan zhe li
                            player = entry.getValue();
                            sessions.get(player.getSession()).write(Datagram.wrap(tmp, Target.CT, 0x0a));//0x000a
                            sessions.get(sessionID).write(Datagram.wrap(player.pack(), Target.CT, 0x0a));//0x000a
                        }
                        players.put(sessionID, new PlayerInfo(id, name, level, 
                                gender, posX, posY, sessionID));
                        currChar = players.size();
                        System.out.println("new character comes in!");
                        players.get(sessionID).printInfo();
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
            Byte res;
            ByteBuffer tmp = Datagram.getDatagram(buffer);
            ByteBuffer nbuf = ByteBuffer.allocate(64);
            if(tmp == null)
                return;
            form = Datagram.trim(tmp);
            switch(form){
                case 0x0201://Successfully Login
                    synchronized(lock1){
                        lock1.notify();
                        System.out.println("login response!");
                    }
                    break;
                case 0x0203://return login info to Client
                    sessionID = tmp.getInt();
                    tmp.compact();
                    res = tmp.get(0);
                    if(res == 1){
                        stats.put(sessionID, (byte)1);
                        System.out.println("Client " + sessions.get(sessionID).
                                getRemoteSocketAddress() + " login successfully!");
                    }
                    else{
                        System.out.println("Client " + sessions.get(sessionID).
                                getRemoteSocketAddress() + " login failed!");
                    }
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x03));//0x0003
                    break;
                case 0x0205://return register info to client
                    System.out.println("LoginServer returns register result");
                    sessionID = tmp.getInt();
                    tmp.compact();
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x05));//0x0005
                    break;
                case 0x0206:
                    System.out.println("LoginServer returns pre-load result");
                    sessionID = tmp.getInt();
                    tmp.compact();
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x06));//0x0006
                    break;
                case 0x0209:
                    System.out.println("LoginServer returns char-creation result");
                    sessionID = tmp.getInt();
                    tmp.compact();
                    sessions.get(sessionID).write(Datagram.wrap(tmp, Target.CT, 0x09));//0x0009
                    break;
            }
        }
    }
}
