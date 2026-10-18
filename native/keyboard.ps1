$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Threading;

public static class OrbitKeyboard {
    [StructLayout(LayoutKind.Sequential)]
    struct INPUT { public uint type; public InputUnion data; }
    [StructLayout(LayoutKind.Explicit)]
    struct InputUnion {
        [FieldOffset(0)] public MOUSEINPUT mouse;
        [FieldOffset(0)] public KEYBDINPUT keyboard;
    }
    [StructLayout(LayoutKind.Sequential)]
    struct MOUSEINPUT { public int dx, dy; public uint mouseData, flags, time; public UIntPtr extra; }
    [StructLayout(LayoutKind.Sequential)]
    struct KEYBDINPUT { public ushort vk, scan; public uint flags, time; public UIntPtr extra; }
    [DllImport("user32.dll", SetLastError = true)]
    static extern uint SendInput(uint count, INPUT[] inputs, int size);
    [DllImport("user32.dll")]
    static extern uint MapVirtualKey(uint code, uint mapType);

    static readonly object gate = new object();
    static readonly HashSet<int> held = new HashSet<int>();
    static DateTime lastMessage = DateTime.UtcNow;

    static bool Key(int vk, bool down) {
        uint flags = 0x0008u | (down ? 0u : 0x0002u);
        if (vk >= 0x21 && vk <= 0x2E) flags |= 0x0001u;
        INPUT item = new INPUT();
        item.type = 1;
        item.data.keyboard.scan = (ushort)MapVirtualKey((uint)vk, 0);
        item.data.keyboard.flags = flags;
        return SendInput(1, new INPUT[] { item }, Marshal.SizeOf(typeof(INPUT))) == 1;
    }

    static void Apply(HashSet<int> next) {
        foreach (int vk in new List<int>(held)) {
            if (!next.Contains(vk) && Key(vk, false)) held.Remove(vk);
        }
        foreach (int vk in next) {
            if (!held.Contains(vk)) {
                if (Key(vk, true)) held.Add(vk);
                else Console.WriteLine("ERROR:Windows refused keyboard input; check foreground app privileges.");
            }
        }
    }

    public static void Run() {
        using (Timer watchdog = new Timer(delegate(object unused) {
            lock (gate) {
                if ((DateTime.UtcNow - lastMessage).TotalMilliseconds > 1600) Apply(new HashSet<int>());
            }
        }, null, 300, 300)) {
            Console.WriteLine("READY");
            try {
                string line;
                while ((line = Console.ReadLine()) != null) {
                    if (line == "quit") break;
                    if (!line.StartsWith("keys:")) continue;
                    HashSet<int> next = new HashSet<int>();
                    foreach (string raw in line.Substring(5).Split(',')) {
                        int vk;
                        if (Int32.TryParse(raw, out vk) && vk > 0 && vk < 256) next.Add(vk);
                    }
                    lock (gate) { lastMessage = DateTime.UtcNow; Apply(next); }
                }
            } finally { lock (gate) { Apply(new HashSet<int>()); } }
        }
    }
}
'@
[OrbitKeyboard]::Run()
