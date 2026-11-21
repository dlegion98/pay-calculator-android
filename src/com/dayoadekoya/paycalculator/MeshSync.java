package com.dayoadekoya.paycalculator;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import com.dayoadekoya.paycalculator.Model.PayEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Nearby Device Sync — wire-compatible with the iOS MeshSyncManager:
 * Bonjour service "_paycalc-mesh._tcp", plain TCP, each message = 4-byte big-endian length
 * + SyncData JSON ({"entries":[...], "syncCode":"..."}). Only devices with the same pairing
 * code accept each other's data. Both sides advertise and browse; the device with the
 * higher instance name dials out. Entries merge by id; deletions are not propagated.
 */
public final class MeshSync {
    private MeshSync() {}

    private static final String SERVICE_TYPE = "_paycalc-mesh._tcp.";

    private static Context app;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService io = Executors.newCachedThreadPool();

    private static boolean running;
    private static String instanceName;
    private static ServerSocket server;
    private static NsdManager nsd;
    private static NsdManager.RegistrationListener regListener;
    private static NsdManager.DiscoveryListener discListener;

    private static final List<Peer> peers = new ArrayList<Peer>();
    private static final Set<String> outbound = new HashSet<String>();
    private static final LinkedList<NsdServiceInfo> resolveQueue = new LinkedList<NsdServiceInfo>();
    private static boolean resolving;

    public static boolean syncing;
    public static int lastReceived;
    public static String status = "";
    public static Runnable onChange;

    private static final class Peer {
        Socket socket;
        OutputStream out;
        String name;
    }

    // ------------------------------------------------------------------ settings

    public static boolean enabled() { return Store.bool("meshEnabled", false); }
    public static String code() { return Store.str("meshSyncCode", ""); }
    public static int connectedCount() { synchronized (peers) { return peers.size(); } }

    public static void setEnabled(Context c, boolean on) {
        Store.put("meshEnabled", on);
        if (on) start(c); else stop();
        notifyUi();
    }

    public static void setCode(Context c, String code) {
        Store.put("meshSyncCode", code == null ? "" : code.trim());
        if (running) stop();
        if (enabled()) start(c);
        notifyUi();
    }

    private static String instanceName(Context c) {
        String suffix = Store.str("meshInstanceSuffix", "");
        if (suffix.isEmpty()) {
            suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
            Store.put("meshInstanceSuffix", suffix);
        }
        String dev = null;
        try { dev = Settings.Global.getString(c.getContentResolver(), "device_name"); } catch (Throwable ignored) {}
        if (dev == null || dev.trim().isEmpty()) dev = android.os.Build.MODEL;
        return dev + "#" + suffix;
    }

    // ------------------------------------------------------------------ lifecycle

    public static synchronized void start(Context c) {
        app = c.getApplicationContext();
        if (running || !enabled() || code().isEmpty()) return;
        running = true;
        instanceName = instanceName(app);
        status = "Looking for devices on this Wi-Fi…";
        Store.addListener(CHANGE_LISTENER);
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    server = new ServerSocket(0);
                    final int port = server.getLocalPort();
                    main.post(new Runnable() { @Override public void run() { register(port); discover(); } });
                    while (running && server != null && !server.isClosed()) {
                        Socket s = server.accept();
                        adopt(s, null);
                    }
                } catch (Throwable t) {
                    if (running) setStatus("Couldn't start sync: " + t.getMessage());
                }
            }
        });
    }

    public static synchronized void stop() {
        running = false;
        try { if (nsd != null && regListener != null) nsd.unregisterService(regListener); } catch (Throwable ignored) {}
        try { if (nsd != null && discListener != null) nsd.stopServiceDiscovery(discListener); } catch (Throwable ignored) {}
        regListener = null;
        discListener = null;
        try { if (server != null) server.close(); } catch (Throwable ignored) {}
        server = null;
        synchronized (peers) {
            for (Peer p : peers) try { p.socket.close(); } catch (Throwable ignored) {}
            peers.clear();
        }
        outbound.clear();
        resolveQueue.clear();
        resolving = false;
        status = "";
        notifyUi();
    }

    private static void register(int port) {
        try {
            nsd = (NsdManager) app.getSystemService(Context.NSD_SERVICE);
            NsdServiceInfo info = new NsdServiceInfo();
            info.setServiceName(instanceName);
            info.setServiceType(SERVICE_TYPE);
            info.setPort(port);
            regListener = new NsdManager.RegistrationListener() {
                @Override public void onServiceRegistered(NsdServiceInfo i) {
                    if (i.getServiceName() != null) instanceName = i.getServiceName();
                }
                @Override public void onRegistrationFailed(NsdServiceInfo i, int e) { setStatus("Couldn't advertise this phone (error " + e + ")"); }
                @Override public void onServiceUnregistered(NsdServiceInfo i) {}
                @Override public void onUnregistrationFailed(NsdServiceInfo i, int e) {}
            };
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, regListener);
        } catch (Throwable t) {
            setStatus("Couldn't advertise this phone");
        }
    }

    private static void discover() {
        try {
            discListener = new NsdManager.DiscoveryListener() {
                @Override public void onDiscoveryStarted(String t) {}
                @Override public void onDiscoveryStopped(String t) {}
                @Override public void onStartDiscoveryFailed(String t, int e) { setStatus("Couldn't search for devices (error " + e + ")"); }
                @Override public void onStopDiscoveryFailed(String t, int e) {}
                @Override public void onServiceLost(NsdServiceInfo i) {}
                @Override public void onServiceFound(final NsdServiceInfo i) {
                    main.post(new Runnable() { @Override public void run() { found(i); } });
                }
            };
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener);
        } catch (Throwable t) {
            setStatus("Couldn't search for devices");
        }
    }

    private static void found(NsdServiceInfo i) {
        String name = i.getServiceName();
        if (!running || name == null || name.equals(instanceName)) return;
        // Same tie-break as iOS: only the device with the higher name dials out.
        if (instanceName.compareTo(name) <= 0) return;
        if (outbound.contains(name)) return;
        outbound.add(name);
        resolveQueue.add(i);
        resolveNext();
    }

    @SuppressWarnings("deprecation")
    private static void resolveNext() {
        if (resolving || resolveQueue.isEmpty() || !running) return;
        resolving = true;
        final NsdServiceInfo i = resolveQueue.removeFirst();
        try {
            nsd.resolveService(i, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo s, int e) {
                    main.post(new Runnable() { @Override public void run() {
                        outbound.remove(i.getServiceName());
                        resolving = false;
                        resolveNext();
                    } });
                }
                @Override public void onServiceResolved(final NsdServiceInfo s) {
                    main.post(new Runnable() { @Override public void run() { resolving = false; resolveNext(); } });
                    final InetAddress host = s.getHost();
                    final int port = s.getPort();
                    final String name = i.getServiceName();
                    io.execute(new Runnable() {
                        @Override public void run() {
                            try {
                                Socket sock = new Socket();
                                sock.connect(new java.net.InetSocketAddress(host, port), 8000);
                                adopt(sock, name);
                            } catch (Throwable t) {
                                main.post(new Runnable() { @Override public void run() { outbound.remove(name); } });
                            }
                        }
                    });
                }
            });
        } catch (Throwable t) {
            resolving = false;
            outbound.remove(i.getServiceName());
        }
    }

    // ------------------------------------------------------------------ connections

    private static void adopt(final Socket s, String peerName) {
        try {
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
            final Peer p = new Peer();
            p.socket = s;
            p.out = s.getOutputStream();
            p.name = peerName != null ? peerName : String.valueOf(s.getInetAddress());
            synchronized (peers) { peers.add(p); }
            setStatus("");
            notifyUi();
            // Build the payload on the main thread (Store isn't thread-safe), send on I/O.
            main.post(new Runnable() { @Override public void run() { sendTo(p); } });
            io.execute(new Runnable() { @Override public void run() { readLoop(p); } });
        } catch (Throwable t) {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    private static void readLoop(final Peer p) {
        try {
            DataInputStream in = new DataInputStream(p.socket.getInputStream());
            while (running) {
                int len = in.readInt();
                if (len <= 0 || len > 50_000_000) break;
                byte[] buf = new byte[len];
                in.readFully(buf);
                final String json = new String(buf, StandardCharsets.UTF_8);
                main.post(new Runnable() { @Override public void run() { applyIncoming(json); } });
            }
        } catch (Throwable ignored) {
        } finally {
            drop(p);
        }
    }

    private static void drop(final Peer p) {
        try { p.socket.close(); } catch (Throwable ignored) {}
        synchronized (peers) { peers.remove(p); }
        main.post(new Runnable() { @Override public void run() { outbound.remove(p.name); notifyUi(); } });
    }

    // ------------------------------------------------------------------ sending

    private static byte[] payload() throws Exception {
        JSONObject root = new JSONObject();
        JSONArray es = new JSONArray();
        for (PayEntry e : new ArrayList<PayEntry>(Store.entries)) es.put(Store.entryJson(e, true));
        root.put("entries", es);
        root.put("syncCode", code());
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void sendTo(final Peer p) {
        final byte[] body;
        try { body = payload(); } catch (Throwable t) { return; }
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    synchronized (p) {
                        byte[] head = {(byte) (body.length >>> 24), (byte) (body.length >>> 16), (byte) (body.length >>> 8), (byte) body.length};
                        p.out.write(head);
                        p.out.write(body);
                        p.out.flush();
                    }
                } catch (Throwable t) {
                    drop(p);
                }
            }
        });
    }

    /** "Sync Now" — push our database to every connected device. */
    public static void broadcast() {
        List<Peer> list;
        synchronized (peers) { list = new ArrayList<Peer>(peers); }
        if (list.isEmpty()) return;
        syncing = true;
        notifyUi();
        for (Peer p : list) sendTo(p);
        main.postDelayed(new Runnable() { @Override public void run() { syncing = false; notifyUi(); } }, 1000);
    }

    private static final Runnable BROADCAST = new Runnable() { @Override public void run() { broadcast(); } };

    /** Local edits push automatically (coalesced), like iOS. */
    private static final Runnable CHANGE_LISTENER = new Runnable() {
        @Override public void run() {
            if (!running || connectedCount() == 0) return;
            main.removeCallbacks(BROADCAST);
            main.postDelayed(BROADCAST, 400);
        }
    };

    // ------------------------------------------------------------------ receiving

    private static void applyIncoming(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String theirCode = root.optString("syncCode", "");
            if (root.isNull("syncCode")) theirCode = "";
            if (!theirCode.equals(code())) return; // different pairing code: ignore
            JSONArray es = root.optJSONArray("entries");
            if (es == null) return;
            Map<String, Integer> byId = new HashMap<String, Integer>();
            for (int i = 0; i < Store.entries.size(); i++) byId.put(Store.entries.get(i).id.toUpperCase(Locale.ROOT), i);
            int applied = 0;
            for (int i = 0; i < es.length(); i++) {
                JSONObject o = es.getJSONObject(i);
                PayEntry in = Store.entryFrom(o, !o.has("dateMillis"));
                in.id = in.id.toUpperCase(Locale.ROOT);
                Integer idx = byId.get(in.id);
                if (idx == null) {
                    Store.entries.add(in);
                    byId.put(in.id, Store.entries.size() - 1);
                    applied++;
                } else {
                    PayEntry local = Store.entries.get(idx);
                    in.sortIndex = local.sortIndex;
                    if (!same(local, in)) { Store.entries.set(idx, in); applied++; }
                }
            }
            if (applied > 0) {
                lastReceived = applied;
                Store.changed();
                if (MainActivity.current != null) {
                    MainActivity.current.snack("Synced " + applied + (applied == 1 ? " pay period" : " pay periods") + " from a nearby device");
                }
            }
            notifyUi();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static boolean same(PayEntry a, PayEntry b) {
        try {
            PayEntry x = a.copy(false), y = b.copy(false);
            sortDays(x); sortDays(y);
            return Store.entryJson(x, false).toString().equals(Store.entryJson(y, false).toString());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void sortDays(PayEntry e) {
        java.util.Collections.sort(e.days, new java.util.Comparator<Model.DailyPay>() {
            @Override public int compare(Model.DailyPay a, Model.DailyPay b) { return Integer.compare(a.dayIndex, b.dayIndex); }
        });
    }

    // ------------------------------------------------------------------ ui

    private static void setStatus(final String s) {
        main.post(new Runnable() { @Override public void run() { status = s; notifyUi(); } });
    }

    private static void notifyUi() {
        main.post(new Runnable() {
            @Override public void run() { if (onChange != null) try { onChange.run(); } catch (Throwable ignored) {} }
        });
    }
}
