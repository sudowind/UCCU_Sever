/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uccu_sever;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.LinkedList;
import java.util.Queue;

/**
 *
 * @author Xiaoshuang
 */


interface Decoder {
    public void decode(ByteBuffer buffer, AioSession session);
}

class SampleDecoder implements Decoder{
    public void decode(ByteBuffer buffer, AioSession session)
    {
        ByteBuffer msg = ByteBuffer.allocate(128);
        msg.put(buffer);
        msg.flip();
        session.write(msg);
    }
}

public class AioSession {
    private ByteBuffer readBuffer;
    private Queue<ByteBuffer> writeQueue;
    private AsynchronousSocketChannel socketChannel;
    private Decoder decoder;
    private CompletionHandler<Integer, AioSession> readCompletionHandler;
    private CompletionHandler<Integer, AioSession> writeCompletionHandler;
    private Object attachment;
    
    public AioSession(AsynchronousSocketChannel sockChannel, Decoder dec, CompletionHandler readHandler, 
                        CompletionHandler writeHandler) {
        readBuffer = ByteBuffer.allocate(128);
        socketChannel = sockChannel;
        readCompletionHandler = readHandler;
        writeCompletionHandler = writeHandler;
        decoder = dec;
        writeQueue = new LinkedList<ByteBuffer>();
    }
    public void setAttachment(Object att)
    {
        attachment = att;
    }
    public Object getAttachment()
    {
        return attachment;
    }
    public ByteBuffer getReadBuffer()
    {
        return readBuffer;
    }
    public AsynchronousSocketChannel getSocketChannel()
    {
        return socketChannel;
    }
    public Queue<ByteBuffer> getWriteQueue()
    {
        return writeQueue;
    }
    public void decode()
    {
        this.decoder.decode(readBuffer, this);
    }
    public void asyncRead()
    {
        socketChannel.read(readBuffer, this, this.readCompletionHandler);
    }
    public void asyncWrite(ByteBuffer msg)
    {
        socketChannel.write(msg, this, writeCompletionHandler);
    }
    public void write(ByteBuffer msg)
    {
        boolean canwrite = false;
        synchronized(writeQueue)
        {
            canwrite = writeQueue.isEmpty();
            writeQueue.offer(msg);
        }
        if(canwrite)
            asyncWrite(msg);
    }
    public void write(String str)
    {
        ByteBuffer msg = ByteBuffer.allocate(str.length());
        msg.put(str.getBytes());
        msg.flip();
        write(msg);
    }
    public void close()
    {
        try {
            socketChannel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public SocketAddress getRemoteSocketAddress()
    {
        try {
            return socketChannel.getRemoteAddress();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
