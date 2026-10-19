using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

// Physical USB (ADB reverse) and paired, encrypted Bluetooth RFCOMM adapters.
// Both terminate at the receiver's existing loopback HTTP/WebSocket protocol.
internal sealed class DirectConnections : IDisposable {
    internal static readonly Guid BluetoothService = new Guid("873d421b-2c01-4b50-9cde-0836ab901a51");
    private readonly int port;
    private readonly string adb;
    private readonly HashSet<string> usb = new HashSet<string>();
    private readonly HashSet<IDisposable> clients = new HashSet<IDisposable>();
    private Socket listener;
    private ServiceRegistration registration;
    private bool disposed;
    internal bool BluetoothEnabled { get { return listener != null; } }
    internal DirectConnections(int port, string adbPath = null) { this.port = port; adb = adbPath ?? Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "runtime", "platform-tools", "adb.exe"); }
    private Task<string> Adb(string args) {
        return Task.Run(() => {
            if (!File.Exists(adb)) throw new IOException("USB 组件缺失，请完整解压新版 Windows 接收端。");
            using (var process = new Process { StartInfo = new ProcessStartInfo(adb, args) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true } }) {
                process.Start(); var output = process.StandardOutput.ReadToEndAsync(); var error = process.StandardError.ReadToEndAsync();
                if (!process.WaitForExit(15000)) { try { process.Kill(); } catch { } throw new IOException("USB 连接超时，请检查数据线与手机授权。"); }
                Task.WaitAll(output, error);
                if (process.ExitCode != 0) throw new IOException(error.Result.Trim());
                return output.Result;
            }
        });
    }
    internal async Task<string[]> UsbDevices() {
        string result = await Adb("devices");
        return result.Split('\n').Select(line => line.Trim().Split((char[])null, StringSplitOptions.RemoveEmptyEntries))
            .Where(parts => parts.Length == 2 && parts[1] == "device" && !parts[0].StartsWith("emulator-") && !parts[0].Contains(":"))
            .Select(parts => parts[0]).ToArray();
    }
    internal async Task<string> ConnectUsb(string serial) {
        if (disposed) throw new ObjectDisposedException("DirectConnections");
        if (!Regex.IsMatch(serial, "^[A-Za-z0-9._-]+$")) throw new ArgumentException("USB 设备编号无效");
        string mappings = await Adb("-s " + serial + " reverse --list");
        string existing = mappings.Split('\n').FirstOrDefault(line => Regex.IsMatch(line, "\\stcp:3210\\s"));
        if (existing != null) {
            if (!existing.Trim().EndsWith("tcp:" + port)) throw new IOException("手机 USB 通道被其他接收端占用，请断开旧连接后重连数据线。");
        } else {
            await Adb("-s " + serial + " reverse --no-rebind tcp:3210 tcp:" + port); usb.Add(serial);
        }
        return "USB 已就绪 · 请在手机连接页选择 USB 直连";
    }
    internal Task<string> StartBluetooth() {
        return Task.Run(() => {
            lock (clients) {
                if (disposed) throw new ObjectDisposedException("DirectConnections");
                if (listener != null) return "蓝牙服务已开启";
                var socket = new Socket((AddressFamily)32, SocketType.Stream, (ProtocolType)3);
                try {
                    socket.SetSocketOption((SocketOptionLevel)3, (SocketOptionName)unchecked((int)0x80000001), 1);
                    socket.SetSocketOption((SocketOptionLevel)3, (SocketOptionName)2, 1);
                    socket.Bind(new BluetoothEndPoint()); socket.Listen(4);
                    registration = new ServiceRegistration(((BluetoothEndPoint)socket.LocalEndPoint).Bytes());
                    listener = socket; Task.Run(() => Accept(socket));
                    return "蓝牙已开启 · 在手机系统中配对本机，再选择蓝牙直连";
                } catch { socket.Dispose(); if (registration != null) { registration.Dispose(); registration = null; } throw; }
            }
        });
    }
    internal void StopBluetooth() {
        lock (clients) {
            if (registration != null) { registration.Dispose(); registration = null; }
            if (listener != null) { listener.Dispose(); listener = null; }
            foreach (var client in clients) try { client.Dispose(); } catch { } clients.Clear();
        }
    }
    private void Accept(Socket socket) {
        while (!disposed) {
            try { var remote = socket.Accept(); lock (clients) { if (disposed || clients.Count >= 16) { remote.Dispose(); continue; } clients.Add(remote); } Task.Run(() => Bridge(remote)); }
            catch { break; }
        }
    }
    private async Task Bridge(Socket remote) {
        using (remote) using (var tcp = new TcpClient()) {
            lock (clients) clients.Add(tcp);
            try {
                await tcp.ConnectAsync(IPAddress.Loopback, port); tcp.NoDelay = true;
                using (var bluetooth = new NetworkStream(remote, false)) {
                    var stream = tcp.GetStream(); var up = bluetooth.CopyToAsync(stream); var down = stream.CopyToAsync(bluetooth);
                    await Task.WhenAny(up, down); remote.Dispose(); tcp.Close();
                    try { await Task.WhenAll(up, down); } catch { }
                }
            } catch { }
            finally { lock (clients) { clients.Remove(remote); clients.Remove(tcp); } }
        }
    }
    public void Dispose() {
        lock (clients) {
            if (disposed) return; disposed = true;
            if (registration != null) { registration.Dispose(); registration = null; }
            if (listener != null) { listener.Dispose(); listener = null; }
            foreach (var client in clients) try { client.Dispose(); } catch { } clients.Clear();
        }
        foreach (string serial in usb.ToArray()) {
            try {
                string mappings = Adb("-s " + serial + " reverse --list").GetAwaiter().GetResult();
                if (mappings.Split('\n').Any(line => Regex.IsMatch(line, "\\stcp:3210\\stcp:" + port + "\\s*$")))
                    Adb("-s " + serial + " reverse --remove tcp:3210").GetAwaiter().GetResult();
            } catch { }
        }
        usb.Clear();
    }
    private sealed class BluetoothEndPoint : EndPoint {
        private readonly byte[] value;
        public BluetoothEndPoint() { value = new byte[30]; value[0] = 32; for (int n = 26; n < 30; n++) value[n] = 255; }
        private BluetoothEndPoint(SocketAddress address) { value = new byte[30]; for (int n = 0; n < value.Length; n++) value[n] = address[n]; }
        public override AddressFamily AddressFamily { get { return (AddressFamily)32; } }
        public override SocketAddress Serialize() { var address = new SocketAddress((AddressFamily)32, 30); for (int n = 0; n < value.Length; n++) address[n] = value[n]; return address; }
        public override EndPoint Create(SocketAddress address) { return new BluetoothEndPoint(address); }
        internal byte[] Bytes() { return (byte[])value.Clone(); }
    }
    // The Win32 provider creates the RFCOMM SDP record from this WSAQUERYSET.
    private sealed class ServiceRegistration : IDisposable {
        [StructLayout(LayoutKind.Sequential)] private struct SockAddress { internal IntPtr address; internal int length; }
        [StructLayout(LayoutKind.Sequential)] private struct CsAddress { internal SockAddress local, remote; internal int type, protocol; }
        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)] private struct Query {
            internal uint size; [MarshalAs(UnmanagedType.LPWStr)] internal string name;
            internal IntPtr service, version, comment; internal uint nameSpace; internal IntPtr provider, context;
            internal uint protocols; internal IntPtr protocolArray, query; internal uint addresses; internal IntPtr addressArray;
            internal uint flags; internal IntPtr blob;
        }
        [DllImport("ws2_32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern int WSASetServiceW(ref Query query, int operation, uint flags);
        [DllImport("ws2_32.dll")] private static extern int WSAGetLastError();
        private Query query;
        private IntPtr address, service, info;
        private bool registered;
        internal ServiceRegistration(byte[] endpoint) {
            try {
                address = Marshal.AllocHGlobal(endpoint.Length); Marshal.Copy(endpoint, 0, address, endpoint.Length);
                service = Marshal.AllocHGlobal(16); Marshal.Copy(BluetoothService.ToByteArray(), 0, service, 16);
                var cs = new CsAddress { local = new SockAddress { address = address, length = endpoint.Length }, type = 1, protocol = 3 };
                info = Marshal.AllocHGlobal(Marshal.SizeOf(cs)); Marshal.StructureToPtr(cs, info, false);
                query = new Query { size = (uint)Marshal.SizeOf(typeof(Query)), name = "Orbit Pad", service = service, nameSpace = 16, addresses = 1, addressArray = info };
                if (WSASetServiceW(ref query, 0, 0) != 0) throw new SocketException(WSAGetLastError());
                registered = true;
            } catch { Dispose(); throw; }
        }
        public void Dispose() { if (registered) { WSASetServiceW(ref query, 2, 0); registered = false; } if (info != IntPtr.Zero) { Marshal.FreeHGlobal(info); info = IntPtr.Zero; } if (address != IntPtr.Zero) { Marshal.FreeHGlobal(address); address = IntPtr.Zero; } if (service != IntPtr.Zero) { Marshal.FreeHGlobal(service); service = IntPtr.Zero; } }
    }
}
