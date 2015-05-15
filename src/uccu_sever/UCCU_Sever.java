/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

/**
 *
 * @author Xiaoshuang
 */

import java.net.InetAddress;

public class UCCU_Sever {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        AioModule aio = new AioModule(new SampleRegister(), new SampleDecoder());
        try {
            aio.init(InetAddress.getLocalHost().getHostAddress(), 8998, 4);
            aio.asyncAccept();
            Thread.sleep(1000000);
        }
        catch (Exception e) {
        }
    }
    
}
