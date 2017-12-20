package com.willisp.simplevpn;

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadPoolExecutor;

public class TCPOutput implements Runnable{
    private final String TAG = this.getClass().getSimpleName();

    private Selector selector;
    private VpnService service;
    private ConcurrentLinkedDeque<Packet> OutputTCPQueue;
    private ConcurrentLinkedDeque<ByteBuffer> InputQueue;

    private LinkedHashMap<String, SocketChannel> ipPort_Channel;

    public TCPOutput(Selector selector, VpnService service,
                     ConcurrentLinkedDeque<Packet> OutputTCPQueue,
                     ConcurrentLinkedDeque<ByteBuffer> InputQueue) {
        this.selector = selector;
        this.service = service;
        this.OutputTCPQueue = OutputTCPQueue;
        this.InputQueue = InputQueue;

        this.ipPort_Channel = new LinkedHashMap<String, SocketChannel>();
    }

    @Override
    public void run() {

    }

    private void initConnection(String ipPort,
                                InetAddress dstAddr,
                                int dstPort,
                                Packet pkt,
                                Packet.TCPHeader tcpHeader,
                                ByteBuffer response)
        throws IOException
    {

    }
}
