package app.orbitpad.controller;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Android RFCOMM adapter. Rust still owns pairing, WebSocket and controller state.
 * Each loopback TCP connection gets one authenticated/encrypted RFCOMM stream. */
final class BluetoothProxy implements Closeable {
    static final UUID SERVICE=UUID.fromString("873d421b-2c01-4b50-9cde-0836ab901a51");
    private final BluetoothDevice device;
    private final ServerSocket server;
    private final ExecutorService workers=Executors.newCachedThreadPool();
    private final ScheduledExecutorService timeouts=Executors.newSingleThreadScheduledExecutor();
    private final Set<Closeable> sockets=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    BluetoothProxy(BluetoothDevice device) throws Exception {
        this.device=device;server=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
        workers.execute(()->{while(!closed){try{Socket local=server.accept();local.setTcpNoDelay(true);sockets.add(local);workers.execute(()->bridge(local));}catch(Exception e){if(!closed)close();}}});
    }
    int port(){return server.getLocalPort();}
    private void bridge(Socket local){
        BluetoothSocket remote=null;
        try{
            remote=device.createRfcommSocketToServiceRecord(SERVICE);sockets.add(remote);final BluetoothSocket connecting=remote;
            java.util.concurrent.ScheduledFuture<?> deadline=timeouts.schedule(()->quiet(connecting),12,TimeUnit.SECONDS);
            try{remote.connect();}finally{deadline.cancel(false);}
            final BluetoothSocket bluetooth=remote;
            workers.execute(()->copy(local,bluetooth,()->local.getInputStream(),()->bluetooth.getOutputStream()));
            copy(local,bluetooth,()->bluetooth.getInputStream(),()->local.getOutputStream());
        }catch(Exception ignored){quiet(local);quiet(remote);}
    }
    interface Source {InputStream get() throws Exception;}
    interface Sink {OutputStream get() throws Exception;}
    private void copy(Socket local,BluetoothSocket remote,Source from,Sink to){
        try{InputStream in=from.get();OutputStream out=to.get();byte[] buffer=new byte[8192];int count;while(!closed&&(count=in.read(buffer))!=-1){out.write(buffer,0,count);out.flush();}}
        catch(Exception ignored){}finally{quiet(local);quiet(remote);sockets.remove(local);sockets.remove(remote);}
    }
    private static void quiet(Closeable socket){if(socket!=null)try{socket.close();}catch(Exception ignored){}}
    @Override public void close(){if(closed)return;closed=true;quiet(server);for(Closeable socket:sockets)quiet(socket);sockets.clear();workers.shutdownNow();timeouts.shutdownNow();}
}
