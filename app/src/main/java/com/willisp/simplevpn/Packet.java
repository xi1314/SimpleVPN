package com.willisp.simplevpn;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class Packet {
    public static final int IP4_HEADER_SIZE = 20;
    public static final int TCP_HEADER_SIZE = 20;
    public static final int UDP_HEADER_SIZE = 8;
    public static final int BUFFER_SIZE = 16384;

    private boolean isTCP;
    private boolean isUDP;

    public IP4Header ip4Header;
    public TCPHeader tcpHeader;
    public UDPHeader udpHeader;
    public ByteBuffer dataBuffer;

    public Packet(ByteBuffer buffer) throws UnknownHostException{
        this.ip4Header = new IP4Header(buffer);
        if (this.ip4Header.protoNum == 6) {
            this.tcpHeader = new TCPHeader(buffer);
            this.isTCP = true;
        }
        else if (ip4Header.protoNum == 17) {
            this.udpHeader = new UDPHeader(buffer);
            this.isUDP = true;
        }
        this.dataBuffer = buffer;
    }

    public boolean isTCP() { return isTCP;}
    public boolean isUDP() { return isUDP;}

    public void swapSrcAndDst() {
        InetAddress tmpAddr = ip4Header.dstAddr;
        ip4Header.dstAddr = ip4Header.srcAddr;
        ip4Header.srcAddr = tmpAddr;

        int tmpPort;
        if (isUDP) {
            tmpPort = udpHeader.dstPort;
            udpHeader.dstPort = udpHeader.srcPort;
            udpHeader.srcPort = tmpPort;
        }
        else if (isTCP) {
            tmpPort = tcpHeader.dstPort;
            tcpHeader.dstPort = tcpHeader.srcPort;
            tcpHeader.seqNum = tmpPort;
        }
    }

    private void fillHeader(ByteBuffer buffer) {
        ip4Header.fillHeader(buffer);
        if (isTCP)
            tcpHeader.fillHeader(buffer);
        else if (isUDP)
            udpHeader.fillHeader(buffer);
    }

    private void updateIP4Checksum() {
        ByteBuffer buffer = dataBuffer.duplicate();
        buffer.position(0);

        // clear previous checksum
        buffer.putShort(10, (short) 0);

        int ipLength = ip4Header.headerLength;
        int sum = 0;
        while (ipLength > 0) {
            sum += getUnsignedShort(buffer.getShort());
            ipLength -= 2;
        }
        while (sum >> 16 > 0)
            sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        ip4Header.headerChecksum = sum;
        buffer.putShort(10, (short) sum);
    }

    public void updateTCPBuffer(ByteBuffer buffer, byte flags,
                                long seqNum, long ackNum, int payloadSize) {
        buffer.position(0);
        fillHeader(buffer);
        dataBuffer = buffer;

        tcpHeader.flags = flags;
        buffer.put(IP4_HEADER_SIZE + 13, flags);

        tcpHeader.seqNum = seqNum;
        buffer.putInt(IP4_HEADER_SIZE + 4, (int) seqNum);

        tcpHeader.ackNum = ackNum;
        buffer.putInt(IP4_HEADER_SIZE + 8, (int) ackNum);

        // options are not needed
        byte dataOffset = (byte) (TCP_HEADER_SIZE << 2);
        tcpHeader.dataOffset_Reserved = dataOffset;
        dataBuffer.put(IP4_HEADER_SIZE + 12, dataOffset);

        updateTCPChecksum(payloadSize);

        int ip4TotalLength = IP4_HEADER_SIZE + TCP_HEADER_SIZE + payloadSize;
        dataBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
    }

    private void updateTCPChecksum(int payloadSize) {
        int sum = 0;
        int tcpLength = TCP_HEADER_SIZE + payloadSize;

        ByteBuffer buffer = ByteBuffer.wrap(ip4Header.srcAddr.getAddress());
        sum = getUnsignedShort(buffer.getShort()) + getUnsignedShort(buffer.getShort());

        buffer = ByteBuffer.wrap(ip4Header.dstAddr.getAddress());
        sum = getUnsignedShort(buffer.getShort()) + getUnsignedShort(buffer.getShort());

        sum += TransProto.TCP.getProtoNumber() + tcpLength;

        buffer = dataBuffer.duplicate();

        // clear previous checksum
        buffer.putShort(IP4_HEADER_SIZE + 16, (short) 0);

        buffer.position(IP4_HEADER_SIZE);
        while (tcpLength > 1) {
            sum += getUnsignedShort(buffer.getShort());
            tcpLength -= 2;
        }
        if (tcpLength > 0)
            sum += getUnsignedByte(buffer.get()) << 8;

        while (sum >> 16 > 0)
            sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        tcpHeader.checksum = sum;
        dataBuffer.putShort(IP4_HEADER_SIZE+16, (short) sum);
    }

    public void updateUDPBuffer(ByteBuffer buffer, int payloadSize) {
        buffer.position(0);
        fillHeader(buffer);
        dataBuffer = buffer;

        int udpTotalLength = UDP_HEADER_SIZE + payloadSize;
        dataBuffer.putShort(IP4_HEADER_SIZE + 4, (short) udpTotalLength);
        udpHeader.length = udpTotalLength;

        // disable UDP checksum validation
        dataBuffer.putShort(IP4_HEADER_SIZE + 6, (short) 0);
        udpHeader.checksum = 0;

        int ip4TotalLength = IP4_HEADER_SIZE + udpTotalLength;
        dataBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
    }

    private enum TransProto{
        TCP(6), UDP(17), Other(0xFF);

        private int protoNumber;

        TransProto(int protoNumber) {this.protoNumber = protoNumber;}

        private static TransProto num2enum (int protoNumber) {
            if (protoNumber == 6)
                return TCP;
            else if (protoNumber == 17)
                return UDP;
            else
                return Other;
        }

        public int getProtoNumber() {return this.protoNumber;}
    }

    public class IP4Header {
        public byte version;
        public byte IHL;            // IP Header Length;
        public int headerLength;
        public short tos;           // type of service
        public int totalLength;

        public int id_flags_offset;

        public short TTL;
        private short protoNum;
        public TransProto proto;
        public int headerChecksum;

        public InetAddress srcAddr;
        public InetAddress dstAddr;

        public int optionsAndPadding;

        private IP4Header(ByteBuffer buffer) throws UnknownHostException {
            byte version_IHL = buffer.get();
            // BIG ENDIAN
            this.version = (byte) (version_IHL >> 4);
            this.IHL = (byte) (version_IHL & 0X0F);
            this.headerLength = this.IHL << 2;

            this.tos = getUnsignedByte(buffer.get());
            this.totalLength = getUnsignedShort(buffer.getShort());
            this.id_flags_offset = buffer.getInt();

            this.TTL = getUnsignedByte(buffer.get());
            this.protoNum = getUnsignedByte(buffer.get());
            this.proto = TransProto.num2enum(this.protoNum);
            this.headerChecksum = getUnsignedShort(buffer.getShort());

            byte[] addr = new byte[4];
            buffer.get(addr, 0, 4);
            this.srcAddr = InetAddress.getByAddress(addr);

            buffer.get(addr, 0, 4);
            this.dstAddr = InetAddress.getByAddress(addr);
        }

        public void fillHeader(ByteBuffer buffer) {
            buffer.put((byte) (this.version << 4 | this.IHL));
            buffer.put((byte) this.tos);
            buffer.putShort((short) this.totalLength);

            buffer.putInt(this.id_flags_offset);

            buffer.put((byte) this.TTL);
            buffer.put((byte) this.protoNum);
            buffer.putShort((short) this.headerChecksum);
            buffer.put(this.srcAddr.getAddress());
            buffer.put(this.dstAddr.getAddress());
        }
    }

    public class TCPHeader {
        public static final int FIN = 0x01;
        public static final int SYN = 0x02;
        public static final int RST = 0x04;
        public static final int PSH = 0x08;
        public static final int ACK = 0x10;
        public static final int URG = 0x20;

        public int srcPort;
        public int dstPort;
        public long seqNum;
        public long ackNum;

        public byte dataOffset_Reserved;
        public byte flags;
        public int window;
        public int checksum;
        public int urgentPointer;
        public byte[] options_padding;

        public int headerLength;

        private TCPHeader(ByteBuffer buffer) {
            this.srcPort = getUnsignedShort(buffer.getShort());
            this.dstPort = getUnsignedShort(buffer.getShort());
            this.seqNum = getUnsignedInt(buffer.getInt());
            this.ackNum = getUnsignedInt(buffer.getInt());
            this.dataOffset_Reserved = buffer.get();
            this.headerLength = (this.dataOffset_Reserved & 0xF0) >> 2;
            this.flags = buffer.get();
            this.window = getUnsignedShort(buffer.getShort());
            this.checksum = getUnsignedShort(buffer.getShort());
            this.urgentPointer = getUnsignedShort(buffer.getShort());

            int optionsLength = this.headerLength - TCP_HEADER_SIZE;
            if (optionsLength > 0) {
                options_padding = new byte[optionsLength];
                buffer.get(options_padding, 0, optionsLength);
            }
        }

        public boolean isFIN() {return (flags & FIN) == FIN;}
        public boolean isSYN() {return (flags & SYN) == SYN;}
        public boolean isRST() {return (flags & RST) == RST;}
        public boolean isPSH() {return (flags & PSH) == PSH;}
        public boolean isACK() {return (flags & ACK) == ACK;}
        public boolean isURG() {return (flags & URG) == URG;}

        private void fillHeader(ByteBuffer buffer) {
            buffer.putShort((short) srcPort);
            buffer.putShort((short) dstPort);

            buffer.putInt((int) seqNum);
            buffer.putInt((int) ackNum);

            buffer.put(dataOffset_Reserved);
            buffer.put(flags);
            buffer.putShort((short) window);

            buffer.putShort((short) checksum);
            buffer.putShort((short) urgentPointer);
            buffer.put(options_padding);
        }
    }

    public class UDPHeader {
        public int srcPort;
        public int dstPort;
        public int length;
        public int checksum;

        private UDPHeader(ByteBuffer buffer) {
            this.srcPort = getUnsignedShort(buffer.getShort());
            this.dstPort = getUnsignedShort(buffer.getShort());
            this.length = getUnsignedShort(buffer.getShort());
            this.checksum = getUnsignedShort(buffer.getShort());
        }

        private void fillHeader(ByteBuffer buffer) {
            buffer.putShort((short) this.srcPort);
            buffer.putShort((short) this.dstPort);
            buffer.putShort((short) this.length);
            buffer.putShort((short) this.checksum);
        }
    }

    private static short getUnsignedByte(byte value) {return (short)(value & 0XFF);}
    private static int getUnsignedShort(short value) {return value & 0XFFFF;}
    private static long getUnsignedInt(int value) {return value & 0XFFFFFFFFL;}
}
