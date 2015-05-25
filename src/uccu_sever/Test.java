/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import java.util.Scanner;
import org.python.core.PyException;
import org.python.core.PyInteger;
import org.python.util.PythonInterpreter;

/**
 *
 * @author Wind
 */
public class Test {
    public static void main(String[] args) throws PyException{
        PythonInterpreter interp = new PythonInterpreter();
        interp.exec("import sys");
        interp.set("a", new PyInteger(42));
        interp.exec("print sys");
        Scanner in = new Scanner(System.in);
        while(in.hasNextLine()){
            try{
                interp.exec(in.nextLine());
            }catch(Exception e){
                System.out.println(e);
            }
        }
    }
}
