package com.willisp.simplevpn;

import android.net.VpnService;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Set;

public class TCPInput implements Runnable{
    private final String TAG = this.getClass().getSimpleName();

    private Selector selector;
    private ConcurrentLinkedDeque<ByteBuffer> N2DQueue;

    public TCPInput(Selector selector,
                    ConcurrentLinkedDeque<ByteBuffer> N2DQueue) {
        this.selector = selector;
        this.N2DQueue = N2DQueue;
    }

    public void run() {

    }
}
