package com.willisp.simplevpn;

/*
    读取与外部主机相连的SocketChanel写的数据
    修改本地端口并发送到协议栈
 */

import android.support.annotation.IntRange;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

public class UDPInput implements Runnable{
    private static final String TAG = UDPInput.class.getSimpleName();
    private static final int HEADER_SIZE = Packet.IP4_HEADER_SIZE + Packet.UDP_HEADER_SIZE;

    private Selector selector;
    private ConcurrentLinkedDeque<ByteBuffer> inputQueue;

    public UDPInput(Selector selector,
                    ConcurrentLinkedDeque<ByteBuffer> inputQueue) {
        this.selector = selector;
        this.inputQueue = inputQueue;
    }

    public void run() {
        try {
            Log.i(TAG, "UDPInput started");
            while (!Thread.interrupted()) {
                int channelCount = selector.select();
                if (channelCount == 0) {
                    Thread.sleep(10);
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();

                while (it.hasNext() && !Thread.interrupted()) {
                    SelectionKey key = it.next();
                    if (key.isValid() && key.isReadable()) {
                        it.remove();

                        DatagramChannel channel = (DatagramChannel) key.channel();
                        ByteBuffer rcvBuffer = ByteBuffer.allocate(Packet.BUFFER_SIZE);
                        int readBytes = channel.read(rcvBuffer);

                        Packet referencePacket = (Packet) key.attachment();
                        referencePacket.updateUDPBuffer(rcvBuffer, readBytes);
                        rcvBuffer.position(HEADER_SIZE + readBytes);

                        inputQueue.offer(rcvBuffer);
                    }
                }
            }
        }
        catch (InterruptedException ie) {
            Log.i(TAG, "thread interrupted");
        }
        catch (IOException ioe) {
            Log.i(TAG, "error IO execution");
        }
    }
}