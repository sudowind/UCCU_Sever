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
        System.out.println("Session " + session.getRemoteSocketAddress() + " has disconnected!");
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
                System.out.println("Bind at " + hostName + ": " + port);
            }
                
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }
        return session;
    }
    public void asyncAccept()
    {
        if(!this.started && this.asyncServerSocketChannel.isOpen())
        {
            System.out.println("Start listening!");
            asyncServerSocketChannel.accept(this, new AcceptCompletionHandler());
            this.started = true;
        }
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
        public void cancelled(Object attachment)
        {
            System.out.println("Accept Operation cancelled!");
        }
        public void completed(AsynchronousSocketChannel socketChannel, AioModule aio)
        {
            aio.started = false;
            try {
                System.out.println("Accept from "+ socketChannel.getRemoteAddress());
                socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                AioSession session = new AioSession(socketChannel, decoder, reaper, new ReadCompletionHandler(),
                                        new WriteCompletionHandler());
                
                if(!register.register(session, aio))// Session denied!
                {
                    System.out.println("Session "+ socketChannel.getRemoteAddress() + "denied!");
                    socketChannel.close();
                }
                else
                    session.asyncRead();
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
            finally
            {
                asyncAccept();
            }
        }
        public void failed(Throwable exc, AioModule aio)
        {
           aio.started = false;
           exc.printStackTrace();
           asyncAccept();
        }
    }
    private final class ReadCompletionHandler implements CompletionHandler<Integer, AioSession>
    {
        public void cancelled(AioSession session)
        {
            try {
                System.out.println("Session " + session.getSocketChannel().getRemoteAddress() + " has cancelled!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        public void completed(Integer res, AioSession session)
        {
            if(res < 0)
            {
                session.reap();
                session.close();
                return;
            }
            try {
                
                session.getReadBuffer().flip();
                
                
                while(session.getReadBuffer().hasRemaining())
                {
                    System.out.print(session.getReadBuffer().get() + " ");
                }
                System.out.println("");
                
                session.getReadBuffer().rewind();
                
                session.decode();

                session.getReadBuffer().compact();
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally
            {
                session.asyncRead();
            }
        }
        public void failed(Throwable exc, AioSession session)
        {
            System.out.println("Read Failed!");
            exc.printStackTrace();
            session.close();
        }
    }
    private final class WriteCompletionHandler implements CompletionHandler<Integer, AioSession>
    {
        public void cancelled(AioSession session)
        {
            try {
                System.out.println("Session " + session.getSocketChannel().getRemoteAddress() + " has cancelled!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        public void completed(Integer res, AioSession session)
        {
            System.out.println("Session " + session.getRemoteSocketAddress() + " has writen " + res + " bytes.");
            ByteBuffer msg;
            Queue<ByteBuffer> writeQueue = session.getWriteQueue();
            synchronized(writeQueue)
            {
                msg = writeQueue.peek();
                if(!msg.hasRemaining())
                    writeQueue.remove();
                msg = writeQueue.peek();
            }
            if(msg != null)
            {
                try {
                    session.asyncWrite(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    session.close();
                }
            }
        }
        public void failed(Throwable exc, AioSession session)
        {
            exc.printStackTrace();
            session.close();
        }
    }
}
