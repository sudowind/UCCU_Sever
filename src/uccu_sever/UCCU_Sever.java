/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import java.net.InetAddress;

/**
 *
 * @author Wind
 */
public class UCCU_Sever {
    
    /**
     * @param args the command line arguments
     */
    
    static String ip;
    
    
    public static void main(String[] args) {
        // TODO code application logic here
        GateServer gate = new GateServer();
        AioModule aio = new AioModule(gate, gate);
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
        }
        aio.init(ip, 8438, 4);
        String gip = new String();////////////////////////////////
        String lip = new String();
        int gport = 0; 
        int lport = 0;
        gate.init(gip, gport, lip, lport, aio);
        aio.asyncAccept();
    }
    
}
