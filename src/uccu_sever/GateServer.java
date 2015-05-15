/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.sql.DriverManager;
import java.util.HashMap;



/**
 *
 * @author Wind
 */

public class GateServer implements Register, Decoder{
    private AioSession gameSession;
    private AioSession loginSession;
    private HashMap<Integer, AioSession> sessions = new HashMap<Integer, AioSession>();
    private int clientNum = 0;
    private ByteBuffer tmpBuffer;
    public GateServer(){
        
    }
    
    public void init(String gip, int gport, String lip, int lport, AioModule aio){
        gameSession = aio.connect(gip, gport, new GameDecoder());
        loginSession = aio.connect(lip, lport, new LoginDecoder());
    }
    
    public boolean register(AioSession session, AioModule aio){////////////////////////////////////////////////////
        sessions.put(clientNum, session);
        clientNum++;
        aio.addSession(session);
        return true;
    }
    
    public void decode(ByteBuffer buffer, AioSession session){
        
    }
    
    private class GameDecoder implements Decoder{
        public void decode(ByteBuffer buffer, AioSession session){
            
        }
    }

    private class LoginDecoder implements Decoder{
        public void decode(ByteBuffer buffer, AioSession session){
        
        }
    }
}
