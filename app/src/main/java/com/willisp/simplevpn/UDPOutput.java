package com.willisp.simplevpn;

/*
    读取输出的UDP数据包队列
    解析出目标主机的IP和端口
    通过SocketChannel直接与远程主机连接
    向Selector注册此Channel
 */

import android.icu.util.Output;
import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class UDPOutput implements Runnable{
    private final String TAG = this.getClass().getSimpleName();

    private Selector selector;
    private VpnService service;
    private ConcurrentLinkedDeque<Packet> OutputUDPQueue;

    // NOTICE: 记录本地PORT和外出IP-PORT对，用于修改包内容
    private LinkedHashMap<String, DatagramChannel> ipPort_Channel;

    public UDPOutput(Selector selector,
                     ConcurrentLinkedDeque<Packet> OutputUDPQueue,
                     VpnService service) {
        this.selector = selector;
        this.OutputUDPQueue = OutputUDPQueue;
        this.service = service;

        this.ipPort_Channel = new LinkedHashMap<String, DatagramChannel>();
    }

    public void run() {
        try {
            while (true) {
                Packet pkt;
                do {
                    pkt = OutputUDPQueue.poll();
                    if (pkt != null) break;
                    Thread.sleep(10);
                } while (!Thread.currentThread().isInterrupted());

                InetAddress dst_addr = pkt.ip4Header.dstAddr;
                int dstPort = pkt.udpHeader.dstPort;
                int srcPort = pkt.udpHeader.srcPort;

                String ipPort = dst_addr.getHostAddress() + ":" + dstPort + ":" + srcPort;
                DatagramChannel channel = ipPort_Channel.get(ipPort);

                if (channel == null) {
                    try {
                        channel = DatagramChannel.open();
                        channel.connect(new InetSocketAddress(dst_addr, dstPort));
                        channel.configureBlocking(false);
                        pkt.swapSrcAndDst();
                        channel.register(selector, SelectionKey.OP_READ, pkt);
                    } catch (IOException e) {
                        Log.i(TAG, "error UDP connect to remote host");
                        continue;
                    }

                    service.protect(channel.socket());
                    ipPort_Channel.put(ipPort, channel);
                }

                try {
                    ByteBuffer buffer = pkt.dataBuffer;
                    while (buffer.hasRemaining())
                        channel.write(buffer);
                } catch (IOException e) {
                    Log.i(TAG, "error UDP write to remote host");
                    ipPort_Channel.remove(ipPort);

                    try { channel.close();}
                    catch (IOException e1) {
                        Log.i(TAG, "error close UDP channel");
                    }
                }
            }
        }
        catch (InterruptedException ie) { Log.i(TAG, "thread interrupted");}
    }
}