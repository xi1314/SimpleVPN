package com.willisp.simplevpn;


import android.app.PendingIntent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Selector;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class SimpleVPNService extends VpnService{
    private static final String VPN_ADDR = "10.0.0.2";
    private static final String VPN_ROUTE = "0.0.0.0";
    private static final String TAG = "SimpleVPNService";
    private ParcelFileDescriptor vpnInterface;
    private PendingIntent intent;

    // OutputQueue指从用户程序产生，向网口输出的数据包队列
    private ConcurrentLinkedDeque<Packet> OutputTCPQueue;
    private ConcurrentLinkedDeque<Packet> OutputUDPQueue;

    // InputQueue指外部主机发送到本机的数据包队列，数据包指向用户程序
    private ConcurrentLinkedDeque<ByteBuffer> InputQueue;

    // Selector作用于和外部主机有实际通信的SocketChannel
    private Selector tcpSelector;
    private Selector udpSelector;

    @Override
    public void onCreate() {
        if (vpnInterface == null) {
            Builder builder = new Builder();
            builder.addAddress(VPN_ADDR, 32);
            builder.addRoute(VPN_ROUTE, 0);
            builder.setSession("SimpleVPNService");
            builder.setConfigureIntent(intent);
            vpnInterface = builder.establish();
        }
        Log.i(TAG, "preparing finished");

        OutputTCPQueue = new ConcurrentLinkedDeque<Packet>();
        OutputUDPQueue = new ConcurrentLinkedDeque<Packet>();
        InputQueue = new ConcurrentLinkedDeque<ByteBuffer>();

        try {
            tcpSelector = Selector.open();
            udpSelector = Selector.open();
        }
        catch(IOException e) {Log.i(TAG, "error opening selectors");}


        Executor executor = Executors.newCachedThreadPool();
        executor.execute(new PacketsTransfer());
        executor.execute(new TCPOutput(tcpSelector, this, OutputTCPQueue, InputQueue));
//        executor.execute(new TCPInput());
        executor.execute(new UDPOutput(udpSelector, OutputUDPQueue, this));
        executor.execute(new UDPInput(udpSelector, InputQueue));

        Log.i(TAG, "service all ready");
    }

    // 从虚拟网卡中读取数据包，根据数据包类型加入不同的输出队列
    // 同时从输入队列中读取包，并向虚拟网卡写入数据包
    public class PacketsTransfer implements Runnable {
        public void run() {
            FileChannel input = new FileInputStream(vpnInterface.getFileDescriptor()).getChannel();
            FileChannel output = new FileOutputStream(vpnInterface.getFileDescriptor()).getChannel();

            try{
                ByteBuffer buffer = null;
                boolean dataSent = true;
                while (!Thread.interrupted()) {
                    if (dataSent)
                        buffer = ByteBuffer.allocate(Packet.BUFFER_SIZE);
                    else
                        buffer.clear();

                    int readBytes = input.read(buffer);
                    if (readBytes > 0) {
                        dataSent = true;
                        Packet pkt = new Packet(buffer);
                        if (pkt.isTCP())
                            OutputTCPQueue.offer(pkt);
                        else if (pkt.isUDP())
                            OutputUDPQueue.offer(pkt);
                        else {
                            Log.w(TAG, "unknown packet type");
                            dataSent = false;
                        }
                    }
                    else
                        dataSent = false;

                    ByteBuffer toUserBuffer = InputQueue.poll();
                    if (toUserBuffer != null) {
                        toUserBuffer.flip();
                        while (toUserBuffer.hasRemaining())
                            output.write(toUserBuffer);

                        toUserBuffer.clear();
                    }
                }
            }
            catch (IOException e) {Log.i(TAG, "error handling with TUN device");}
            finally {
                try { input.close(); output.close();}
                catch (Exception e) { Log.i(TAG, "VPN service stopped");}
            }
        }
    }
}