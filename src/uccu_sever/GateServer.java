/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.sql.DriverManager;



/**
 *
 * @author Wind
 */

class GateRegister implements Register{
    public boolean register(AioSession session, AioModule aio){
        aio.addSession(session);
        return true;
    }
}

class ClientDecoder implements Decoder{
    private ByteBuffer currBuf;
    private char func;   //which kind of data package
    private int length; //length of data package
    private char head;
    public void decode(ByteBuffer buffer, AioSession session){
        currBuf = buffer.duplicate();
        head = buffer.getChar();
        length = buffer.getInt();
        func = buffer.getChar();
    }    
}

class GameDecoder implements Decoder{
    public void decode(ByteBuffer buffer, AioSession session){
        
    }
}

class LoginDecoder implements Decoder{
    public void decode(ByteBuffer buffer, AioSession session){
        
    }
}

public class GateServer {
    AioModule aio;
    private String ip;
    private AioSession gameSession;
    private AioSession loginSession;
    public GateServer(){
        aio = new AioModule(new GateRegister(), new ClientDecoder());
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
        }
        aio.init(ip, 8438, 4);
    }
            
    public void request(){
        
    }
    
    public void login(){
        
    }
    
    public void init(String gip, int gport, String lip, int lport){
        gameSession = aio.connect(gip, gport, new GameDecoder());
        loginSession = aio.connect(lip, lport, new LoginDecoder());
    }
    
    public String getIp(){
        return ip;
    }
}
