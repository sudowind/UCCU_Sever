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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executors;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;



interface Register {
    public boolean register(AioSession session, AioModule aio);
}

class SampleRegister implements Register {
    public boolean register(AioSession session, AioModule aio)
    {
        aio.addSession(session);
        return true;
    }
}

interface Reaper {
    public void reap(AioSession session);
}

class SampleReaper {
    public void reap(AioSession session)
    {
        //System.out.println("Session " + session.getRemoteSocketAddress() + " has disconnected!");
        UccuLogger.log("SampleReaper/Reap","Session"+session.getRemoteSocketAddress()+" has disconnected!");
    }
}

public class AioModule {
    
    private AsynchronousServerSocketChannel asyncServerSocketChannel;
    private AsynchronousChannelGroup asyncChannelGroup;
    
    private Register register;
    private Decoder decoder;
    private Reaper reaper;
    
    //private Future acceptFuture;
    private int threadPoolSize;
    private boolean started;
    private HashMap<SocketAddress, AioSession> sessionsMap;
    public AioModule(Register reg, Decoder dec, Reaper rpr) {
        threadPoolSize = 0;
        started = false;
        register = reg;
        decoder = dec;
        reaper = rpr;
        sessionsMap = new HashMap();
    }
    
    public void init(String hostName, int port, int threadpoolsize)
    {
        threadPoolSize = threadpoolsize;
        try {
            asyncChannelGroup = AsynchronousChannelGroup.withCachedThreadPool(Executors.newCachedThreadPool(), threadPoolSize);
            asyncServerSocketChannel = AsynchronousServerSocketChannel.open(asyncChannelGroup);
            asyncServerSocketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            asyncServerSocketChannel.setOption(StandardSocketOptions.SO_RCVBUF, 16 * 1024);
            if(port >= 0)
            {
                asyncServerSocketChannel.bind(new InetSocketAddress(hostName, port), 100);
                UccuLogger.kernel("AioModule/Init", "Bind at " + hostName + ": " + port+". Threadpool Size: "+threadpoolsize+".");
                //System.out.println("Bind at " + hostName + ": " + port);
            }
                
        } catch (Exception e) {
            //e.printStackTrace();
            UccuLogger.warn("AioModule/Init", "Failed to initialize at " + hostName + ": " + port+". Threadpool Size "+threadpoolsize+".");
        }
    }
    public AioSession connect(String hostName, int port, Decoder dec, Reaper rpr)
    {
        AioSession session = null;
        try {
            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open(asyncChannelGroup);
            Future done = socketChannel.connect(new InetSocketAddress(hostName, port));
            done.get();
            session = new AioSession(socketChannel, dec, rpr, new ReadCompletionHandler(), new WriteCompletionHandler());
            this.addSession(session);
        } catch (Exception e) {
            //e.printStackTrace();
            UccuLogger.warn("AioModule/Connect", "Failed to connect to "+hostName+":"+port+". "+e);
        }
        return session;
    }
    public boolean asyncAccept()
    {
        if(!this.started && this.asyncServerSocketChannel.isOpen())
        {
            
            //System.out.println("Start listening!");
            try {
                asyncServerSocketChannel.accept(this, new AcceptCompletionHandler());
            } catch (Exception e) {
                UccuLogger.warn("AioModule/AsyncAccept", "Failed to start listening! "+e);
                return false;
            }
            UccuLogger.debug("AioModule/AsyncAccept", "Start listening!");
            this.started = true;
            return true;
        }
        return false;
    }
    public void addSession(AioSession session)
    {
        synchronized(sessionsMap)
        {
            if(!sessionsMap.containsKey(session.getRemoteSocketAddress()))
                sessionsMap.put(session.getRemoteSocketAddress(), session);
        }
    }
    private final class AcceptCompletionHandler implements CompletionHandler<AsynchronousSocketChannel, AioModule>
    {
        public void completed(AsynchronousSocketChannel socketChannel, AioModule aio)
        {
            aio.started = false;
            try {
                UccuLogger.debug("AioModule/Accept/Completed", "Accept from "+ socketChannel.getRemoteAddress());
                //System.out.println("Accept from "+ socketChannel.getRemoteAddress());
                socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                AioSession session = new AioSession(socketChannel, decoder, reaper, new ReadCompletionHandler(),
                                        new WriteCompletionHandler());
                
                if(!register.register(session, aio))// Session denied!
                {
                    UccuLogger.log("AioModule/Accept/Completed", "Session"+ socketChannel.getRemoteAddress() + "is denied!");
                    //System.out.println("Session "+ socketChannel.getRemoteAddress() + "denied!");
                    socketChannel.close();
                }
                else
                    session.asyncRead();
            } 
            catch (Exception e) 
            {
                UccuLogger.warn("AioModule/Accept/Completed", e.toString());
            }
            finally
            {
                asyncAccept();
            }
        }
        public void failed(Throwable exc, AioModule aio)
        {
           aio.started = false;
           //exc.printStackTrace();
            try {
                UccuLogger.warn("AioModule/Accept/Failed", "Failed to listening at "+aio.asyncServerSocketChannel.getLocalAddress()+
                        ". "+exc);
            } catch (Exception e) {
                UccuLogger.warn("AioModule/Accept/Failed", e.toString());
            }
            asyncAccept();
        }
    }
    private final class ReadCompletionHandler implements CompletionHandler<Integer, AioSession>
    {
        public void completed(Integer res, AioSession session)
        {
            if(res < 0)
            {
                //session.reap();
                session.close();
                return;
            }
            try {
                
                session.getReadBuffer().flip();
                
                //记录缓存中数据信息，方便调试进行
                if(UccuLogger.isEnable(LogMode.DEBUG))
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Received ").append(res).append(" bytes from session").append(session.getRemoteSocketAddress());
                    UccuLogger.debug("AioModule/Read/Completed", sb.toString());

                    sb.delete(0, sb.length());
                    //sb.append("\r\n");
                    sb.append("Received contents(").append(session.getReadBuffer().remaining()).append("bytes): ");
                    while(session.getReadBuffer().hasRemaining())
                    {
                        String tmps = String.format("%02x ", session.getReadBuffer().get());
                        sb.append(tmps);
                    }
                    UccuLogger.debug("AioModule/Read/Completed", sb.toString());

                    session.getReadBuffer().rewind();
                }
                session.decode();

                session.getReadBuffer().compact();
            } catch (Exception e) {
                UccuLogger.warn("AioModule/Read/Completed", e.toString());
            }
            finally
            {
                session.asyncRead();
            }
        }
        public void failed(Throwable exc, AioSession session)
        {
            //System.out.println("Read Failed!");
            UccuLogger.warn("AioModule/Read/Failed", "Failed to read from session"+session.getRemoteSocketAddress()+
                    ". "+exc);
            //exc.printStackTrace();
            session.close();
        }
    }
    private final class WriteCompletionHandler implements CompletionHandler<Integer, AioSession>
    {
        public void completed(Integer res, AioSession session)
        {
            //System.out.println("Session " + session.getRemoteSocketAddress() + " has writen " + res + " bytes.");
            ByteBuffer msg;
            ByteBuffer last;
            Queue<ByteBuffer> writeQueue = session.getWriteQueue();
            synchronized(writeQueue)
            {
                last = writeQueue.peek();
                if(!last.hasRemaining())
                    writeQueue.remove();
                msg = writeQueue.peek();
            }
            
            if(last != null && UccuLogger.isEnable(LogMode.DEBUG)){
                UccuLogger.debug("AioModule/Write/Completed", "Sent " + res + " bytes to session" + session.getRemoteSocketAddress());
                StringBuilder sb = new StringBuilder();
                sb.append("Sent contents(").append(res).append("bytes): ");
                for(int i = last.position()-res; i != last.position(); ++i)
                {
                    String tmps = String.format("%02x ", last.get(i));
                    sb.append(tmps);
                }
                UccuLogger.debug("AioModule/Write/Completed", sb.toString());
            }
            
            if(msg != null)
            {
                try {
                    session.asyncWrite(msg);
                } catch (Exception e) {
                    //e.printStackTrace();
                    UccuLogger.warn("AioModule/Write/Completed", "Failed to start write to session"+session.getRemoteSocketAddress()+
                            ". "+e);
                    session.close();
                }
            }
        }
        public void failed(Throwable exc, AioSession session)
        {
            //exc.printStackTrace();
            UccuLogger.warn("AioModule/Write/Failed", "Failed to send to session"+session.getRemoteSocketAddress()+
                            ". "+exc);
            session.close();
        }
    }
}