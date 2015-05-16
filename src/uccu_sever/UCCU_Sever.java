/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import java.net.InetAddress;
import java.util.Scanner;

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
        //get ips and ports
        Scanner in = new Scanner(System.in);
        System.out.println("Please type in GameServer's ip and port number");
        String gip = in.next();////////////////////////////////
        int gport = in.nextInt();
        System.out.println("Please type in LoginServer's ip and port number");
        in = new Scanner(System.in);
        String lip = in.next();
        int lport = in.nextInt();
        System.out.println("GameServer is " + gip + ":" + gport);
        System.out.println("LoginServer is " + lip + ":" + lport);
        gate.init(gip, gport, lip, lport, aio);
        
        
        
        aio.asyncAccept();
    }
    
}
