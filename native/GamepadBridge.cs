using System;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

// A separate process owns the virtual device. EOF, timeout and shutdown return it
// to neutral; Windows removes it when this process loses its bus handle.
internal static class GamepadBridge {
    private const uint Success = 0x20000000;
    [StructLayout(LayoutKind.Sequential)]
    internal struct Report {
        public ushort Buttons;
        public byte LeftTrigger, RightTrigger;
        public short LeftX, LeftY, RightX, RightY;
    }
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr LoadLibraryEx(string path, IntPtr file, uint flags);
    [DllImport("ViGEmClient.dll", CallingConvention = CallingConvention.Cdecl)] private static extern IntPtr vigem_alloc();
    [DllImport("ViGEmClient.dll", CallingConvention = CallingConvention.Cdecl)] private static extern void vigem_free(IntPtr client);
    [DllImport("ViGEmClient.dll", CallingConvention = CallingConvention.Cdecl)] private static extern uint vigem_connect(IntPtr client);
    [DllImport("ViGEmClient.dll", CallingConvention = CallingConvention.Cdecl)] private static extern void vigem_disconnect(IntPtr client);
    [DllImport("ViGEmClient.dll", CallingConvention = CallingConvention.Cdecl)] private static extern IntPtr vigem_target_x360_alloc();
    [DllImport("ViGEmClient.dll", CallingConvention = CallingConvention.Cdecl)] private static extern void vigem_target_free(IntPtr target);
    [DllImport("ViGEmClient.dll", CallingConvention = CallingConvention.Cdecl)] private static extern uint vigem_target_add(IntPtr client, IntPtr target);
    [DllImport("ViGEmClient.dll", CallingConvention = CallingConvention.Cdecl)] private static extern uint vigem_target_remove(IntPtr client, IntPtr target);
    [DllImport("ViGEmClient.dll", CallingConvention = CallingConvention.Cdecl)] private static extern uint vigem_target_x360_update(IntPtr client, IntPtr target, Report report);
    private static readonly object Gate = new object();
    private static IntPtr client, target;
    private static bool added, connected, failed, xboxHeld;
    [StructLayout(LayoutKind.Sequential)] private struct KeyboardInput {
        public ushort Key, Scan; public uint Flags, Time; public UIntPtr Extra;
    }
    [StructLayout(LayoutKind.Explicit)] private struct InputUnion {
        [FieldOffset(0)] public KeyboardInput Keyboard;
        // INPUT union size is 32 bytes on x64 (MOUSEINPUT).
        [FieldOffset(0)] public long Mouse0;
        [FieldOffset(8)] public long Mouse1;
        [FieldOffset(16)] public long Mouse2;
        [FieldOffset(24)] public long Mouse3;
    }
    [StructLayout(LayoutKind.Sequential)] private struct SystemInput { public uint Type; public InputUnion Data; }
    [DllImport("user32.dll", SetLastError=true)] private static extern uint SendInput(uint count, SystemInput[] inputs, int size);
    private static SystemInput Key(ushort key, bool up) {
        return new SystemInput { Type=1, Data=new InputUnion { Keyboard=new KeyboardInput { Key=key, Flags=up ? 2u : 0u } } };
    }
    private static void GameBar() {
        var keys = new[] { Key(0x5B,false), Key(0x47,false), Key(0x47,true), Key(0x5B,true) };
        uint sent = SendInput(4, keys, Marshal.SizeOf(typeof(SystemInput)));
        if (sent != 4) {
            // Never leave injected modifiers down if Windows rejects part of the batch.
            SendInput(2, new[] { Key(0x47,true), Key(0x5B,true) }, Marshal.SizeOf(typeof(SystemInput)));
            Console.WriteLine("NOTICE:无法发送 Game Bar 快捷键，请检查游戏与接收端的权限是否一致。");
        }
    }
    private static DateTime lastMessage = DateTime.UtcNow;

    private static void Check(uint result, string operation) {
        if (result != Success) throw new InvalidOperationException(operation + " (0x" + result.ToString("X8") + ")");
    }
    private static void Apply(Report report) {
        bool xbox = (report.Buttons & 0x0400) != 0;
        // Consume the app's Xbox action so Windows cannot also toggle via Guide.
        report.Buttons = (ushort)(report.Buttons & ~0x0400);
        if (xbox && !xboxHeld) GameBar();
        xboxHeld = xbox;
        Check(vigem_target_x360_update(client, target, report), "更新 Xbox 360 手柄失败，请检查 ViGEmBus 驱动并重启接收端");
    }
    private static bool Parse(string line, out Report report) {
        report = new Report();
        if (!line.StartsWith("pad:")) return false;
        string[] parts = line.Substring(4).Split(',');
        int[] values = new int[7];
        if (parts.Length != values.Length) return false;
        for (int i = 0; i < values.Length; i++) {
            if (!Int32.TryParse(parts[i], NumberStyles.Integer, CultureInfo.InvariantCulture, out values[i])) return false;
            if (i == 0 ? values[i] < 0 || values[i] > 65535 : i < 3 ? values[i] < 0 || values[i] > 255 : values[i] < -32768 || values[i] > 32767) return false;
        }
        report.Buttons = (ushort)values[0]; report.LeftTrigger = (byte)values[1]; report.RightTrigger = (byte)values[2];
        report.LeftX = (short)values[3]; report.LeftY = (short)values[4]; report.RightX = (short)values[5]; report.RightY = (short)values[6];
        return true;
    }
    private static int Main() {
        Console.OutputEncoding = new UTF8Encoding(false);
        try {
            string dll = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "ViGEmClient.dll");
            if (LoadLibraryEx(dll, IntPtr.Zero, 0x00001100) == IntPtr.Zero)
                throw new InvalidOperationException("无法加载 ViGEmClient.dll，请重新完整解压接收端或运行 npm run setup:gamepad");
            client = vigem_alloc();
            if (client == IntPtr.Zero) throw new OutOfMemoryException();
            Check(vigem_connect(client), "ViGEmBus 驱动未就绪，请运行安装手柄驱动.cmd，完成安装后重启接收端");
            connected = true;
            target = vigem_target_x360_alloc();
            if (target == IntPtr.Zero) throw new OutOfMemoryException();
            Check(vigem_target_add(client, target), "创建 Xbox 360 手柄失败");
            added = true;
            Apply(new Report());
            using (var watchdog = new Timer(delegate(object unused) {
                lock (Gate) {
                    if (failed || (DateTime.UtcNow - lastMessage).TotalMilliseconds <= 1600) return;
                    try { Apply(new Report()); }
                    catch (Exception error) { failed = true; Console.WriteLine("ERROR:" + error.Message); }
                }
            }, null, 200, 200)) {
                Console.WriteLine("READY");
                string line;
                while ((line = Console.ReadLine()) != null && line != "quit") {
                    lock (Gate) {
                        if (failed) break;
                        Report report;
                        if (!Parse(line, out report)) {
                            Apply(new Report());
                            throw new InvalidDataException("无效的原生手柄报告");
                        }
                        Apply(report); lastMessage = DateTime.UtcNow;
                    }
                }
            }
            return failed ? 1 : 0;
        } catch (Exception error) {
            Console.WriteLine("ERROR:" + error.Message); return 1;
        } finally {
            lock (Gate) {
                if (added) {
                    try { Apply(new Report()); } catch { }
                    vigem_target_remove(client, target);
                }
                if (target != IntPtr.Zero) vigem_target_free(target);
                if (connected) vigem_disconnect(client);
                if (client != IntPtr.Zero) vigem_free(client);
            }
        }
    }
}
